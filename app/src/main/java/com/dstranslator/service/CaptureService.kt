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
import com.dstranslator.data.ocr.OcrVotingService
import com.dstranslator.data.db.ProfileDao
import com.dstranslator.data.dictionary.JMdictRepository
import com.dstranslator.data.segmentation.FuriganaResolver
import com.dstranslator.data.segmentation.SudachiSegmenter
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.translation.TranslationManager
import com.dstranslator.data.tts.TtsManager
import com.dstranslator.domain.engine.OcrEngine
import com.dstranslator.domain.model.CaptureRegion
import com.dstranslator.domain.model.CaptureScope
import com.dstranslator.domain.model.OcrResult
import com.dstranslator.domain.model.OcrTextBlock
import com.dstranslator.domain.model.PipelineState
import com.dstranslator.domain.model.TranslationEntry
import com.dstranslator.domain.model.resolveForBitmap
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
import kotlinx.coroutines.sync.Mutex
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
    @Inject lateinit var ocrVotingService: OcrVotingService
    @Inject lateinit var translationHistoryDao: TranslationHistoryDao
    @Inject lateinit var profileDao: ProfileDao
    @Inject lateinit var sudachiSegmenter: SudachiSegmenter
    @Inject lateinit var furiganaResolver: FuriganaResolver
    @Inject lateinit var jmdictRepository: JMdictRepository

    private var mediaProjection: MediaProjection? = null
    private var presentation: TranslationPresentation? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var continuousCaptureJob: Job? = null
    private val previousOcrTextsByDisplay = mutableMapOf<Int, String>()
    private val screenshotFailuresByDisplay = mutableMapOf<Int, Int>()
    private var currentSessionId: String = UUID.randomUUID().toString()

    /** Per-region text tracking for auto-read (replaces global previousOcrText for auto-read decisions) */
    private val previousRegionTexts = mutableMapOf<String, String>()

    /** Prevent overlapping capture/OCR/translate runs from rapid taps or mode switches. */
    private val captureMutex = Mutex()

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped by system")
            continuousCaptureJob?.cancel()
            continuousCaptureJob = null
            _isContinuousActive.value = false
            screenCaptureManagerRef = null
            _pipelineState.value = PipelineState.Error("Screen capture permission ended")
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_CAPTURE -> handleCapture()
            ACTION_STOP -> handleStop()
            ACTION_RELEASE_CAPTURE -> handleReleaseCaptureAndStop()
            ACTION_START_CONTINUOUS -> startContinuousCapture()
            ACTION_STOP_CONTINUOUS -> stopContinuousCapture()
            ACTION_DISMISS_PRESENTATION -> handleDismissPresentation()
            ACTION_RESTORE_PRESENTATION -> handleRestorePresentation()
            ACTION_SPEAK -> {
                val text = intent.getStringExtra(EXTRA_SPEAK_TEXT)
                if (!text.isNullOrBlank()) onPlayAudio(text)
            }
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
        val projection = mediaProjection
        if (projection == null) {
            Log.e(TAG, "Failed to obtain MediaProjection")
            stopSelf()
            return
        }

        // Set up screen capture
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        screenCaptureManager.setup(projection, metrics.widthPixels, metrics.heightPixels, metrics.densityDpi)

        // Expose capture manager for region setup screenshots
        screenCaptureManagerRef = screenCaptureManager

        // Expose JMdict repository for overlay word lookup by non-Hilt services
        jmdictRepositoryRef = jmdictRepository

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

        // Clear translations from previous session and start fresh.
        // Translation entries are added directly by captureAndTranslate() with full
        // segmentation and furigana data. We don't use Room Flow collection because
        // TranslationHistoryEntity doesn't store segmentation/furigana data, and
        // collecting from Room would overwrite rich entries with stripped-down ones.
        _translations.value = emptyList()

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

    /**
     * Stop OCR loop only. MediaProjection, VirtualDisplay, and the service continue
     * running so region editing and manual captures remain instant (no re-permission).
     * Translations are preserved for vocabulary review.
     */
    private fun handleStop() {
        // Stop continuous capture if running
        stopContinuousCapture()

        // Keep translations visible after stop so user can review vocabulary.
        // Only the pipeline state transitions to Idle; translations are preserved
        // until a new session starts (handleStart generates a new session ID).
        _pipelineState.value = PipelineState.Idle
    }

    /**
     * Full teardown: stop OCR loop, release MediaProjection, dismiss presentation,
     * and stop the service. Called when the app is truly closing.
     */
    private fun handleReleaseCaptureAndStop() {
        // Stop continuous capture if running
        stopContinuousCapture()

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
        jmdictRepositoryRef = null
        _latestOcrResult.value = null

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

    /**
     * Dismiss the Presentation display when overlay mode is active.
     * Called via intent from FloatingButtonService when overlay mode is enabled
     * and the user has configured dismissPresentationOnOverlay = true.
     */
    private fun handleDismissPresentation() {
        presentation?.dismiss()
        presentation = null
        Log.d(TAG, "Presentation dismissed for overlay mode")
    }

    /**
     * Restore the Presentation display when overlay mode is turned off.
     * Called via intent from FloatingButtonService when overlay mode returns to Off.
     */
    private fun handleRestorePresentation() {
        if (presentation == null) {
            setupPresentation()
            Log.d(TAG, "Presentation restored after overlay mode off")
        }
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
        if (!captureMutex.tryLock()) {
            return
        }

        try {
            _pipelineState.value = PipelineState.Capturing

            val projection = mediaProjection
            if (projection == null) {
                _pipelineState.value = PipelineState.Error("Screen capture permission not available")
                return
            }

            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val allDisplays = displayManager.displays.toList()
            val primaryDisplay = allDisplays.firstOrNull { it.displayId == Display.DEFAULT_DISPLAY }
            val secondaryDisplay = allDisplays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            val captureScope = settingsRepository.getCaptureScope()

            val targetDisplays = when (captureScope) {
                CaptureScope.Primary -> listOfNotNull(primaryDisplay)
                CaptureScope.Secondary -> listOfNotNull(secondaryDisplay ?: primaryDisplay)
                CaptureScope.Both -> listOfNotNull(primaryDisplay, secondaryDisplay).distinctBy { it.displayId }
            }

            if (targetDisplays.isEmpty()) {
                _pipelineState.value = PipelineState.Error("No displays available for capture")
                return
            }

            _pipelineState.value = PipelineState.Processing

            var anySucceeded = false
            for (display in targetDisplays) {
                val bitmap = acquireScreenshotForDisplay(projection, display)
                if (bitmap == null) {
                    continue
                }
                anySucceeded = true
                try {
                    captureAndTranslateBitmap(display.displayId, bitmap)
                } finally {
                    bitmap.recycle()
                }
            }

            if (!anySucceeded) {
                if (_isContinuousActive.value) {
                    continuousCaptureJob?.cancel()
                    continuousCaptureJob = null
                    _isContinuousActive.value = false
                }
                _pipelineState.value = PipelineState.Error("Failed to capture screenshot")
                return
            }

            _pipelineState.value = if (_isContinuousActive.value) PipelineState.ContinuousActive else PipelineState.Done
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error", e)
            _pipelineState.value = PipelineState.Error("Pipeline error: ${e.message}")
        }
        } finally {
            captureMutex.unlock()
        }
    }

    private fun getDisplayMetrics(display: Display): DisplayMetrics {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        return metrics
    }

    private suspend fun acquireScreenshotForDisplay(
        projection: MediaProjection,
        display: Display
    ): android.graphics.Bitmap? {
        val previousFailures = screenshotFailuresByDisplay[display.displayId] ?: 0
        if (previousFailures >= MAX_SCREENSHOT_FAILURES_PER_DISPLAY) return null

        if (!screenCaptureManager.hasTarget(display.displayId)) {
            val metrics = getDisplayMetrics(display)
            screenCaptureManager.setup(
                projection,
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                display.displayId
            )
        }
        val bitmap = screenCaptureManager.acquireScreenshot(display.displayId)
        if (bitmap == null) {
            screenshotFailuresByDisplay[display.displayId] = previousFailures + 1
        } else {
            screenshotFailuresByDisplay[display.displayId] = 0
        }
        return bitmap
    }

    private suspend fun captureAndTranslateBitmap(displayId: Int, bitmap: android.graphics.Bitmap) {
        // Get all capture regions for this display; fall back to single region or full screen
        val multiRegions = settingsRepository.getCaptureRegions(displayId)
        val regions: List<CaptureRegion?> = if (multiRegions.isNotEmpty()) {
            multiRegions
        } else {
            val single = if (displayId == Display.DEFAULT_DISPLAY) settingsRepository.getCaptureRegion() else null
            if (single != null) listOf(single) else listOf(null)
        }

        val allTextBlocks = mutableListOf<OcrTextBlock>()
        val preprocessedBitmaps = mutableListOf<android.graphics.Bitmap>()
        val regionTextMap = mutableMapOf<String, String>()

        val resolvedRegions = regions.map { region ->
            region?.resolveForBitmap(bitmap.width, bitmap.height)
        }

        for (region in resolvedRegions) {
            val preprocessed = ocrPreprocessor.preprocess(bitmap, region)
            preprocessedBitmaps.add(preprocessed)
            val textBlocks = ocrVotingService.recognizeWithVoting(preprocessed, runs = 3)
            allTextBlocks.addAll(textBlocks)
            if (region != null) {
                regionTextMap["$displayId:${region.id}"] = textBlocks.joinToString("") { it.text }.trim()
            }
        }

        _latestOcrResult.value = OcrResult(
            displayId = displayId,
            textBlocks = allTextBlocks.toList(),
            captureRegion = resolvedRegions.firstOrNull(),
            preprocessedWidth = preprocessedBitmaps.firstOrNull()?.width ?: 0,
            preprocessedHeight = preprocessedBitmaps.firstOrNull()?.height ?: 0
        )

        val currentText = allTextBlocks.joinToString("\n") { it.text }.trim()
        val previousOcrText = previousOcrTextsByDisplay[displayId] ?: ""
        if (currentText.isBlank() || isSimilarText(currentText, previousOcrText)) {
            preprocessedBitmaps.forEach { pp ->
                if (pp !== bitmap) pp.recycle()
            }
            return
        }
        previousOcrTextsByDisplay[displayId] = currentText

        val combinedText = allTextBlocks
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

        if (combinedText.isBlank()) {
            preprocessedBitmaps.forEach { pp -> if (pp !== bitmap) pp.recycle() }
            return
        }

        val translation = translationManager.translate(combinedText)

        val segmentedWords = try {
            if (sudachiSegmenter.ensureInitialized()) {
                sudachiSegmenter.segment(combinedText)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Segmentation failed", e)
            emptyList()
        }

        val furiganaSegments = if (segmentedWords.isNotEmpty()) {
            try {
                furiganaResolver.resolve(segmentedWords)
            } catch (e: Exception) {
                Log.w(TAG, "Furigana resolution failed", e)
                emptyList()
            }
        } else emptyList()

        val entry = TranslationEntry(
            japanese = combinedText,
            english = translation,
            sessionId = currentSessionId,
            segmentedWords = segmentedWords,
            furiganaSegments = furiganaSegments
        )

        translationHistoryDao.insert(
            TranslationHistoryEntity(
                sessionId = currentSessionId,
                sourceText = combinedText,
                translatedText = translation,
                timestamp = System.currentTimeMillis()
            )
        )

        val currentEntries = _translations.value.toMutableList()
        currentEntries.add(entry)
        _translations.value = currentEntries

        val autoReadEnabled = settingsRepository.getAutoReadEnabled()
        if (autoReadEnabled) {
            val flushMode = settingsRepository.getAutoReadFlushMode()
            val queueMode = AutoReadHelper.getQueueMode(flushMode)
            for (region in resolvedRegions) {
                if (region != null) {
                    val key = "$displayId:${region.id}"
                    val regionText = regionTextMap[key] ?: ""
                    val previousText = previousRegionTexts[key]
                    if (AutoReadHelper.shouldAutoRead(region, regionText, previousText, autoReadEnabled)) {
                        previousRegionTexts[key] = regionText
                        ttsManager.speak(regionText, queueMode)
                    }
                }
            }
        }

        preprocessedBitmaps.forEach { pp ->
            if (pp !== bitmap) pp.recycle()
        }
    }

    /**
     * Start continuous capture mode. Cancels any existing continuous job first.
     * Uses while(isActive) + delay(interval) pattern to prevent overlapping captures.
     */
    private fun startContinuousCapture() {
        continuousCaptureJob?.cancel()
        previousOcrTextsByDisplay.clear()
        screenshotFailuresByDisplay.clear()
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
     * Check if two OCR text strings are similar enough to be considered the same text.
     * Uses character-level similarity to handle OCR flicker where the engine intermittently
     * misses or misreads a few characters between consecutive captures.
     *
     * @return true if texts are similar enough to skip re-translation
     */
    private fun isSimilarText(current: String, previous: String): Boolean {
        if (previous.isEmpty()) return false
        if (current == previous) return true
        if (current.length < (previous.length * 0.6f)) return false
        if (previous.length < (current.length * 0.6f)) return false

        // If one is a substring of the other, it's likely OCR flicker
        if (current.contains(previous) || previous.contains(current)) return true

        // Compute similarity ratio based on longest common subsequence length
        val shorter = if (current.length <= previous.length) current else previous
        val longer = if (current.length > previous.length) current else previous

        if (longer.isEmpty()) return true
        if (shorter.isEmpty()) return false

        // For short texts, require higher similarity; for longer texts, allow more variance
        val matchCount = shorter.count { ch -> longer.contains(ch) }
        val similarity = matchCount.toFloat() / longer.length

        return similarity >= SIMILARITY_THRESHOLD
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
        jmdictRepositoryRef = null
        _latestOcrResult.value = null
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "CaptureService"
        private const val MAX_SCREENSHOT_FAILURES_PER_DISPLAY = 3

        /**
         * Similarity threshold for OCR change detection.
         * Texts with character-level similarity >= this value are treated as "same text"
         * and won't trigger re-translation. 0.8 = 80% similar characters.
         */
        private const val SIMILARITY_THRESHOLD = 0.8f

        const val ACTION_START = "com.dstranslator.action.START"
        const val ACTION_CAPTURE = "com.dstranslator.action.CAPTURE"
        const val ACTION_STOP = "com.dstranslator.action.STOP"
        const val ACTION_RELEASE_CAPTURE = "com.dstranslator.action.RELEASE_CAPTURE"
        const val ACTION_START_CONTINUOUS = "com.dstranslator.action.START_CONTINUOUS"
        const val ACTION_STOP_CONTINUOUS = "com.dstranslator.action.STOP_CONTINUOUS"
        const val ACTION_OPEN_REGION_EDIT = "com.dstranslator.action.OPEN_REGION_EDIT"
        const val ACTION_DISMISS_PRESENTATION = "com.dstranslator.action.DISMISS_PRESENTATION"
        const val ACTION_RESTORE_PRESENTATION = "com.dstranslator.action.RESTORE_PRESENTATION"
        const val ACTION_SPEAK = "com.dstranslator.action.SPEAK"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
        const val EXTRA_SPEAK_TEXT = "extra_speak_text"

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

        /** Latest OCR result with coordinate metadata for overlay-on-source positioning */
        private val _latestOcrResult = MutableStateFlow<OcrResult?>(null)
        val latestOcrResult: StateFlow<OcrResult?> = _latestOcrResult.asStateFlow()

        /** ScreenCaptureManager reference for region setup screenshot acquisition */
        var screenCaptureManagerRef: ScreenCaptureManager? = null
            private set

        /** JMdict repository reference for overlay word lookup by non-Hilt services (e.g., FloatingButtonService).
         *  Set by CaptureService.onCreate() after Hilt injection, following the same pattern as screenCaptureManagerRef. */
        var jmdictRepositoryRef: JMdictRepository? = null
            private set
    }
}
