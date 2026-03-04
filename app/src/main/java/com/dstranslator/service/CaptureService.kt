package com.dstranslator.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.Display
import android.view.WindowManager
import com.dstranslator.R
import com.dstranslator.data.capture.OcrPreprocessor
import com.dstranslator.data.capture.ScreenCaptureManager
import com.dstranslator.data.db.ProfileDao
import com.dstranslator.data.dictionary.JMdictRepository
import com.dstranslator.data.segmentation.FuriganaResolver
import com.dstranslator.data.segmentation.SudachiSegmenter
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.translation.TranslationManager
import com.dstranslator.data.tts.TtsManager
import com.dstranslator.domain.engine.OcrEngine
import com.dstranslator.domain.model.CaptureRegion
import com.dstranslator.domain.model.OcrTextBlock
import com.dstranslator.domain.model.PipelineState
import com.dstranslator.domain.model.TranslationEntry
import com.dstranslator.ui.presentation.TranslationPresentation
import dagger.hilt.android.AndroidEntryPoint
import com.dstranslator.data.db.TranslationHistoryDao
import com.dstranslator.data.db.TranslationHistoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Foreground service that orchestrates the capture-to-translate pipeline.
 *
 * Manages: MediaProjection session, screen capture, OCR preprocessing,
 * text recognition, translation, and secondary display presentation.
 *
 * Communicates via intent actions:
 * - ACTION_START: Initialize capture session with MediaProjection consent data
 * - ACTION_CAPTURE: Trigger a single capture-and-translate cycle
 * - ACTION_STOP: Release all resources and stop the service
 */
@AndroidEntryPoint
class CaptureService : Service() {

    @Inject lateinit var ocrEngine: OcrEngine
    @Inject lateinit var translationManager: TranslationManager
    @Inject lateinit var ttsManager: TtsManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var screenCaptureManager: ScreenCaptureManager
    @Inject lateinit var ocrPreprocessor: OcrPreprocessor
    @Inject lateinit var translationHistoryDao: TranslationHistoryDao
    @Inject lateinit var profileDao: ProfileDao
    @Inject lateinit var sudachiSegmenter: SudachiSegmenter
    @Inject lateinit var furiganaResolver: FuriganaResolver
    @Inject lateinit var jmdictRepository: JMdictRepository

    private var mediaProjection: MediaProjection? = null
    private var presentation: TranslationPresentation? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var continuousCaptureJob: Job? = null
    private var previousOcrText: String = ""
    private var currentSessionId: String = UUID.randomUUID().toString()
    private var historyCollectionJob: Job? = null

    /** Per-region text tracking for auto-read (replaces global previousOcrText for auto-read decisions) */
    private val previousRegionTexts = mutableMapOf<String, String>()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped by system")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_CAPTURE -> handleCapture()
            ACTION_STOP -> handleStop()
            ACTION_START_CONTINUOUS -> startContinuousCapture()
            ACTION_STOP_CONTINUOUS -> stopContinuousCapture()
        }
        return START_NOT_STICKY
    }

    private fun handleStart(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val data = intent.getParcelableExtra<Intent>(EXTRA_DATA) ?: run {
            Log.e(TAG, "Missing projection data intent")
            stopSelf()
            return
        }

        // Start as foreground service with media projection type
        startForeground(
            NOTIFICATION_ID,
            createNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )

        // Get MediaProjection from consent result
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(projectionCallback, null)

        // Set up screen capture
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenCaptureManager.setup(mediaProjection!!, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)

        // Expose capture manager for region setup screenshots
        screenCaptureManagerRef = screenCaptureManager

        // Initialize TTS
        ttsManager.initialize()

        // Ensure default profile exists on first launch
        serviceScope.launch {
            settingsRepository.ensureDefaultProfile(profileDao)
        }

        // Initialize Sudachi segmenter (async, non-blocking)
        serviceScope.launch {
            try {
                sudachiSegmenter.initialize()
                Log.d(TAG, "Sudachi segmenter initialized")
            } catch (e: Exception) {
                Log.w(TAG, "Sudachi initialization failed; segmentation disabled", e)
            }
        }

        // Set up Presentation on secondary display
        setupPresentation()

        // Generate a new session ID for this capture session
        currentSessionId = UUID.randomUUID().toString()
        _currentSessionId.value = currentSessionId

        // Load existing history for this session (if service was recreated)
        historyCollectionJob?.cancel()
        historyCollectionJob = serviceScope.launch {
            translationHistoryDao.getBySession(currentSessionId).collect { historyEntries ->
                val entries = historyEntries.map { entity ->
                    TranslationEntry(
                        japanese = entity.sourceText,
                        english = entity.translatedText,
                        timestamp = entity.timestamp,
                        sessionId = entity.sessionId
                    )
                }
                _translations.value = entries
            }
        }

        // Start floating button service
        val buttonIntent = Intent(this, FloatingButtonService::class.java)
        startService(buttonIntent)

        _pipelineState.value = PipelineState.Idle
    }

    private fun handleCapture() {
        serviceScope.launch {
            captureAndTranslate()
        }
    }

    private fun handleStop() {
        // Stop continuous capture if running
        stopContinuousCapture()
        historyCollectionJob?.cancel()

        // Stop floating button service
        stopService(Intent(this, FloatingButtonService::class.java))

        // Release resources
        screenCaptureManager.release()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        // Dismiss presentation
        presentation?.dismiss()
        presentation = null

        // Shutdown TTS
        ttsManager.shutdown()

        screenCaptureManagerRef = null
        _pipelineState.value = PipelineState.Idle

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "DS Translator Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active screen capture session"
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        // Create stop action intent
        val stopIntent = Intent(this, CaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("DS Translator")
            .setContentText("Translating game text...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(
                Notification.Action.Builder(
                    null, "Stop", stopPendingIntent
                ).build()
            )
            .setOngoing(true)
            .build()
    }

    /**
     * Find and set up the Presentation on the secondary display.
     * Uses DISPLAY_CATEGORY_PRESENTATION first, then falls back to any non-default display.
     */
    private fun setupPresentation() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

        // Try to find presentation display
        var display: Display? = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .firstOrNull()

        // Fallback: try any non-default display
        if (display == null) {
            display = displayManager.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        }

        if (display != null) {
            presentation = TranslationPresentation(
                this,
                display,
                _translations.asStateFlow(),
                ::onPlayAudio,
                onWordLookup = { word ->
                    jmdictRepository.lookupWord(word.dictionaryForm)
                }
            )
            presentation?.show()
            Log.d(TAG, "Presentation shown on display: ${display.name}")
        } else {
            Log.w(TAG, "No secondary display found for Presentation")
        }

        // Register listener for display changes (disconnect handling)
        displayManager.registerDisplayListener(displayListener, null)
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            // If we don't have a presentation yet, try to set one up
            if (presentation == null) {
                setupPresentation()
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            // If our presentation display was removed, dismiss it
            if (presentation != null) {
                presentation?.dismiss()
                presentation = null
                Log.d(TAG, "Presentation display removed")
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            // No action needed
        }
    }

    /**
     * Execute the full capture-to-translate pipeline:
     * 1. Capture screenshot
     * 2. For each configured capture region (or full screen if none):
     *    a. Preprocess bitmap (crop, upscale, grayscale)
     *    b. Run OCR to extract text blocks
     * 3. Change detection: skip if identical to previous OCR text
     * 4. Translate each text block (cache-first via TranslationManager)
     * 5. Persist to Room history (Flow collection updates translations StateFlow)
     */
    private suspend fun captureAndTranslate() {
        try {
            _pipelineState.value = PipelineState.Capturing

            val bitmap = screenCaptureManager.acquireScreenshot()
            if (bitmap == null) {
                _pipelineState.value = if (_isContinuousActive.value) PipelineState.ContinuousActive else PipelineState.Error("Failed to capture screenshot")
                return
            }

            _pipelineState.value = PipelineState.Processing

            // Get all capture regions; fall back to single region or full screen
            val multiRegions = settingsRepository.getCaptureRegions()
            val regions: List<CaptureRegion?> = if (multiRegions.isNotEmpty()) {
                multiRegions
            } else {
                val single = settingsRepository.getCaptureRegion()
                if (single != null) listOf(single) else listOf(null)
            }

            // Collect text blocks from all regions, tracking per-region text for auto-read
            val allTextBlocks = mutableListOf<OcrTextBlock>()
            val preprocessedBitmaps = mutableListOf<android.graphics.Bitmap>()
            val regionTextMap = mutableMapOf<String, String>()

            for (region in regions) {
                val preprocessed = ocrPreprocessor.preprocess(bitmap, region)
                preprocessedBitmaps.add(preprocessed)
                val textBlocks = ocrEngine.recognize(preprocessed)
                allTextBlocks.addAll(textBlocks)
                // Track per-region OCR text for auto-read
                if (region != null) {
                    regionTextMap[region.id] = textBlocks.joinToString("") { it.text }.trim()
                }
            }

            val currentText = allTextBlocks.joinToString("\n") { it.text }.trim()

            // Change detection: skip if text is identical to previous (consecutive identical = always skip)
            if (currentText == previousOcrText || currentText.isBlank()) {
                _pipelineState.value = if (_isContinuousActive.value) PipelineState.ContinuousActive else PipelineState.Done
                // Recycle bitmaps
                preprocessedBitmaps.forEach { pp ->
                    if (pp !== bitmap) pp.recycle()
                }
                bitmap.recycle()
                return
            }
            previousOcrText = currentText

            // Translate each text block, segment, and persist to Room history
            val newEntries = allTextBlocks.mapNotNull { block ->
                if (block.text.isBlank()) return@mapNotNull null
                val translation = translationManager.translate(block.text)

                // Segment Japanese text and resolve furigana (graceful degradation)
                val segmentedWords = if (sudachiSegmenter.isInitialized) {
                    try {
                        sudachiSegmenter.segment(block.text)
                    } catch (e: Exception) {
                        Log.w(TAG, "Segmentation failed for text block", e)
                        emptyList()
                    }
                } else emptyList()

                val furiganaSegments = if (segmentedWords.isNotEmpty()) {
                    try {
                        furiganaResolver.resolve(segmentedWords)
                    } catch (e: Exception) {
                        Log.w(TAG, "Furigana resolution failed", e)
                        emptyList()
                    }
                } else emptyList()

                val entry = TranslationEntry(
                    japanese = block.text,
                    english = translation,
                    sessionId = currentSessionId,
                    segmentedWords = segmentedWords,
                    furiganaSegments = furiganaSegments
                )

                // Persist to history (per-session, persisted to Room)
                translationHistoryDao.insert(
                    TranslationHistoryEntity(
                        sessionId = currentSessionId,
                        sourceText = block.text,
                        translatedText = translation,
                        timestamp = System.currentTimeMillis()
                    )
                )

                entry
            }

            // Room Flow collection (historyCollectionJob) automatically updates _translations
            // No manual list update needed

            // Auto-read hook: check each region for text changes and speak via TTS
            val autoReadEnabled = settingsRepository.getAutoReadEnabled()
            if (autoReadEnabled) {
                val flushMode = settingsRepository.getAutoReadFlushMode()
                val queueMode = AutoReadHelper.getQueueMode(flushMode)
                for (region in regions) {
                    if (region != null) {
                        val regionText = regionTextMap[region.id] ?: ""
                        val previousText = previousRegionTexts[region.id]
                        if (AutoReadHelper.shouldAutoRead(region, regionText, previousText, autoReadEnabled)) {
                            previousRegionTexts[region.id] = regionText
                            ttsManager.speak(regionText, queueMode)
                        }
                    }
                }
            }

            _pipelineState.value = if (_isContinuousActive.value) PipelineState.ContinuousActive else PipelineState.Done

            // Recycle bitmaps
            preprocessedBitmaps.forEach { pp ->
                if (pp !== bitmap) pp.recycle()
            }
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error", e)
            _pipelineState.value = PipelineState.Error("Pipeline error: ${e.message}")
        }
    }

    /**
     * Start continuous capture mode. Cancels any existing continuous job first.
     * Uses while(isActive) + delay(interval) pattern to prevent overlapping captures.
     */
    private fun startContinuousCapture() {
        continuousCaptureJob?.cancel()
        previousOcrText = ""  // Reset change detection
        previousRegionTexts.clear()  // Reset per-region auto-read tracking
        _isContinuousActive.value = true
        _currentSessionId.value = currentSessionId

        continuousCaptureJob = serviceScope.launch {
            _pipelineState.value = PipelineState.ContinuousActive
            while (isActive) {
                captureAndTranslate()
                val intervalMs = settingsRepository.getCaptureIntervalMs()
                delay(intervalMs)
            }
        }
    }

    /**
     * Stop continuous capture mode and return to idle.
     */
    private fun stopContinuousCapture() {
        continuousCaptureJob?.cancel()
        continuousCaptureJob = null
        _isContinuousActive.value = false
        _pipelineState.value = PipelineState.Idle
    }

    /**
     * Callback for play-audio button taps in the Presentation UI.
     */
    private fun onPlayAudio(text: String) {
        ttsManager.speak(text)
    }

    override fun onDestroy() {
        super.onDestroy()

        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.unregisterDisplayListener(displayListener)

        screenCaptureManager.release()
        mediaProjection?.unregisterCallback(projectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        presentation?.dismiss()
        presentation = null

        ttsManager.shutdown()

        screenCaptureManagerRef = null
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "CaptureService"

        const val ACTION_START = "com.dstranslator.action.START"
        const val ACTION_CAPTURE = "com.dstranslator.action.CAPTURE"
        const val ACTION_STOP = "com.dstranslator.action.STOP"
        const val ACTION_START_CONTINUOUS = "com.dstranslator.action.START_CONTINUOUS"
        const val ACTION_STOP_CONTINUOUS = "com.dstranslator.action.STOP_CONTINUOUS"
        const val ACTION_OPEN_REGION_EDIT = "com.dstranslator.action.OPEN_REGION_EDIT"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "ds_translator_capture"

        /** Pipeline state observable by other components */
        private val _pipelineState = MutableStateFlow<PipelineState>(PipelineState.Idle)
        val pipelineState: StateFlow<PipelineState> = _pipelineState.asStateFlow()

        /** Accumulated translations observable by other components */
        private val _translations = MutableStateFlow<List<TranslationEntry>>(emptyList())
        val translations: StateFlow<List<TranslationEntry>> = _translations.asStateFlow()

        /** Whether continuous capture mode is currently active */
        private val _isContinuousActive = MutableStateFlow(false)
        val isContinuousActive: StateFlow<Boolean> = _isContinuousActive.asStateFlow()

        /** Current session ID for history grouping */
        private val _currentSessionId = MutableStateFlow<String?>(null)
        val currentSessionIdFlow: StateFlow<String?> = _currentSessionId.asStateFlow()

        /** ScreenCaptureManager reference for region setup screenshot acquisition */
        var screenCaptureManagerRef: ScreenCaptureManager? = null
            private set
    }
}
