package com.dstranslator.service

import android.app.Service
import android.content.Context
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.domain.model.DictionaryResult
import com.dstranslator.domain.model.OcrResult
import com.dstranslator.domain.model.OcrTextBlock
import com.dstranslator.domain.model.OverlayConfig
import com.dstranslator.domain.model.OverlayMode
import com.dstranslator.domain.model.SegmentedWord
import com.dstranslator.domain.model.TranslationEntry
import com.dstranslator.ui.presentation.OverlayTooltip
import com.dstranslator.ui.presentation.PresentationLifecycleOwner
import com.dstranslator.ui.theme.DsTranslatorPresentationTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Central manager for all overlay windows on a target display.
 *
 * Owns and orchestrates:
 * - [OverlayPanelView]: Scrollable translation panel with drag/resize/pin/lock
 * - [OverlaySourceLabels]: Canvas-drawn labels at OCR bounding box positions
 * - Tooltip windows: Compact word detail popups triggered by label/word taps
 *
 * Responsibilities:
 * - Resolves [OverlayConfig] sentinel values (0) to actual pixels via [OverlayConfig.resolveForDisplay]
 * - Mode switching via [OverlayStateMachine] transition logic
 * - Lock/pin state management across all overlay views
 * - Config persistence via [SettingsRepository]
 * - Centralized cleanup of all WindowManager views (no leaked views)
 *
 * All overlay windows use FLAG_SECURE to prevent OCR feedback loop.
 * All addView/removeView calls are wrapped in try-catch.
 *
 * @param service The parent service (provides base context)
 * @param targetDisplay The display to show overlays on
 * @param translations StateFlow of current translation entries
 * @param ocrResults StateFlow of latest OCR result with coordinate metadata
 * @param onPlayAudio Callback for TTS audio playback
 * @param onWordLookup Callback for dictionary word lookup
 * @param settingsRepository For loading/saving overlay configuration
 */
class OverlayDisplayManager(
    private val service: Service,
    private val targetDisplay: Display,
    private val translations: StateFlow<List<TranslationEntry>>,
    private val ocrResults: StateFlow<OcrResult?>,
    private val onPlayAudio: (String) -> Unit,
    private val onWordLookup: (suspend (SegmentedWord) -> List<DictionaryResult>)?,
    private val settingsRepository: SettingsRepository,
    private val onRequestSwitchDisplay: (() -> Unit)? = null
) {
    // Display context and window manager for the target display
    private val displayContext: Context = service.createDisplayContext(targetDisplay)
    private val windowManager: WindowManager =
        displayContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    // Display metrics for sentinel resolution
    private val displayWidth: Int
    private val displayHeight: Int

    init {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        targetDisplay.getMetrics(metrics)
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
    }

    // Overlay view instances
    private var panelView: OverlayPanelView? = null
    private var sourceLabels: OverlaySourceLabels? = null

    // Tooltip state
    private var tooltipView: ComposeView? = null
    private var tooltipLifecycleOwner: PresentationLifecycleOwner? = null
    private var tooltipDismissJob: Job? = null

    // Current overlay state
    private var currentMode: OverlayMode = OverlayMode.Off
    private var isPinned = false
    private var isLocked = false

    // Coroutine scope for async work (collecting flows, tooltip timeout)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ocrCollectionJob: Job? = null

    /**
     * Whether the target display is the primary display.
     * Used to load/save the correct per-screen overlay config.
     */
    private val isPrimaryDisplay: Boolean
        get() = targetDisplay.displayId == Display.DEFAULT_DISPLAY

    /**
     * Show the translation panel overlay.
     *
     * Resolves sentinel values (0) in [config] to actual pixel sizes (~20% of screen)
     * via [OverlayConfig.resolveForDisplay], then creates and shows [OverlayPanelView].
     *
     * @param config Overlay configuration (may contain 0-sentinels for first-time use)
     */
    fun showPanel(config: OverlayConfig) {
        // Resolve sentinel 0 values to actual pixel sizes from display metrics
        val resolvedConfig = config.resolveForDisplay(displayWidth, displayHeight)

        val panel = OverlayPanelView(
            displayContext = displayContext,
            windowManager = windowManager,
            translations = translations,
            onPlayAudio = onPlayAudio,
            onWordLookup = onWordLookup,
            initialConfig = resolvedConfig,
            onConfigChanged = { updatedConfig ->
                // Persist the actual pixel values so sentinel is only used on first show
                scope.launch {
                    settingsRepository.setOverlayConfig(isPrimaryDisplay, updatedConfig)
                }
            }
        )

        panel.onMinimize = {
            hidePanel()
        }

        panel.onSwitchDisplay = {
            onRequestSwitchDisplay?.invoke()
        }

        panel.show()
        panelView = panel
        currentMode = OverlayMode.Panel

        Log.d(TAG, "Panel shown on display ${targetDisplay.displayId}: " +
                "${resolvedConfig.panelWidth}x${resolvedConfig.panelHeight} at " +
                "(${resolvedConfig.panelX}, ${resolvedConfig.panelY})")
    }

    /**
     * Hide and remove the translation panel overlay.
     */
    fun hidePanel() {
        panelView?.hide()
        panelView = null
    }

    /**
     * Show overlay-on-source translation labels.
     *
     * Creates [OverlaySourceLabels] and starts collecting [ocrResults] flow to
     * update label positions whenever new OCR data arrives.
     */
    fun showSourceLabels() {
        sourceLabels = OverlaySourceLabels(
            displayContext = displayContext,
            windowManager = windowManager,
            onLabelTap = { block, entry ->
                showTooltipForLabel(block, entry)
            }
        )

        // Start collecting OCR results + translations to update labels
        ocrCollectionJob = scope.launch {
            var lastOcrForDisplay: OcrResult? = null
            combine(ocrResults, translations) { ocr, entries -> ocr to entries }
                .collect { (result, entries) ->
                    if (result != null) {
                        if (result.displayId == null || result.displayId == targetDisplay.displayId) {
                            lastOcrForDisplay = result
                        }
                    }
                    val usableOcr = lastOcrForDisplay
                    if (usableOcr != null) {
                        sourceLabels?.showLabels(usableOcr, entries)
                    }
                }
        }

        currentMode = OverlayMode.OverlayOnSource
        Log.d(TAG, "Source labels mode started on display ${targetDisplay.displayId}")
    }

    /**
     * Hide and remove all source labels.
     */
    fun hideSourceLabels() {
        ocrCollectionJob?.cancel()
        ocrCollectionJob = null
        sourceLabels?.clearLabels()
        sourceLabels = null
    }

    /**
     * Switch the overlay display mode using [OverlayStateMachine] for clean transitions.
     *
     * Determines what cleanup is needed via state machine, removes previous mode's views,
     * then creates the new mode's views.
     *
     * @param mode The target overlay mode
     */
    fun switchMode(mode: OverlayMode) {
        val transition = OverlayStateMachine.transition(currentMode, mode)

        if (transition.cleanupNeeded) {
            when (transition.from) {
                OverlayMode.OverlayOnSource -> hideSourceLabels()
                OverlayMode.Panel -> hidePanel()
                OverlayMode.Off -> { /* Nothing to clean up */ }
            }
        }

        // Dismiss any visible tooltip
        dismissTooltip()

        when (mode) {
            OverlayMode.OverlayOnSource -> showSourceLabels()
            OverlayMode.Panel -> {
                scope.launch {
                    val config = settingsRepository.getOverlayConfig(isPrimaryDisplay)
                    showPanel(config)
                }
            }
            OverlayMode.Off -> { /* Already cleaned up above */ }
        }

        currentMode = mode
        Log.d(TAG, "Mode switched to $mode on display ${targetDisplay.displayId}")
    }

    /**
     * Set the lock state for all overlay views.
     *
     * When locked:
     * - Panel becomes visible but non-interactive (FLAG_NOT_TOUCHABLE)
     * - Source labels become non-touchable (pass through to game)
     *
     * @param locked true to lock, false to unlock
     */
    fun setLocked(locked: Boolean) {
        isLocked = locked
        panelView?.setPanelLocked(locked)
        sourceLabels?.setTouchable(!locked)
    }

    /**
     * Set the pin state. Pinned panel persists while playing (doesn't auto-collapse).
     *
     * @param pinned true to pin, false to unpin
     */
    fun setPinned(pinned: Boolean) {
        isPinned = pinned
    }

    /**
     * Clean up all overlay views and cancel all coroutines.
     * Called on service stop, profile switch, or overlay mode off.
     * Guarantees no leaked WindowManager views.
     */
    fun cleanup() {
        hidePanel()
        hideSourceLabels()
        dismissTooltip()
        scope.cancel()
        currentMode = OverlayMode.Off
        Log.d(TAG, "All overlay views cleaned up for display ${targetDisplay.displayId}")
    }

    /**
     * Show a tooltip near a tapped source label with word detail.
     *
     * Creates a small ComposeView overlay window with [OverlayTooltip] showing
     * dictionary definition, reading, JLPT badge, and audio button.
     * Auto-dismisses after [TOOLTIP_TIMEOUT_MS] or on outside tap.
     */
    private fun showTooltipForLabel(block: OcrTextBlock, entry: TranslationEntry) {
        // Dismiss any existing tooltip first
        dismissTooltip()

        // Look up the first segmented word from the entry for dictionary data
        val word = entry.segmentedWords.firstOrNull() ?: return

        val owner = PresentationLifecycleOwner()
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        tooltipLifecycleOwner = owner

        // Tooltip mutable state for dictionary result
        var tooltipDictResult by mutableStateOf<DictionaryResult?>(null)

        val view = ComposeView(displayContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                DsTranslatorPresentationTheme {
                    OverlayTooltip(
                        word = word,
                        dictionaryResult = tooltipDictResult,
                        jlptLevel = tooltipDictResult?.jlptLevel,
                        onPlayAudio = onPlayAudio,
                        onDismiss = { dismissTooltip() }
                    )
                }
            }
        }
        tooltipView = view

        // Position tooltip near the tapped label
        val screenBounds = OverlayCoordinateMapper.mapToScreenCoordinates(block, ocrResults.value ?: return)
        val tooltipParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Position below the label with some offset
            x = screenBounds.left.coerceIn(TOOLTIP_MARGIN_PX, displayWidth - TOOLTIP_MARGIN_PX)
            y = screenBounds.bottom + TOOLTIP_OFFSET_PX
            // If tooltip would go off bottom of screen, show above the label instead
            if (y > displayHeight - TOOLTIP_ESTIMATE_HEIGHT_PX) {
                y = (screenBounds.top - TOOLTIP_ESTIMATE_HEIGHT_PX).coerceAtLeast(TOOLTIP_MARGIN_PX)
            }
        }

        try {
            windowManager.addView(view, tooltipParams)
        } catch (_: IllegalStateException) {
            // View already added
        }

        // Async dictionary lookup
        if (onWordLookup != null) {
            scope.launch {
                try {
                    val results = onWordLookup.invoke(word)
                    tooltipDictResult = results.firstOrNull()
                } catch (e: Exception) {
                    Log.w(TAG, "Tooltip dictionary lookup failed", e)
                }
            }
        }

        // Auto-dismiss after timeout
        tooltipDismissJob = scope.launch {
            delay(TOOLTIP_TIMEOUT_MS)
            dismissTooltip()
        }
    }

    /**
     * Dismiss the currently visible tooltip overlay.
     */
    private fun dismissTooltip() {
        tooltipDismissJob?.cancel()
        tooltipDismissJob = null

        tooltipLifecycleOwner?.let { owner ->
            owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        tooltipLifecycleOwner = null

        tooltipView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // View already removed
            }
        }
        tooltipView = null
    }

    companion object {
        private const val TAG = "OverlayDisplayManager"

        /** Tooltip auto-dismiss timeout in milliseconds. */
        private const val TOOLTIP_TIMEOUT_MS = 5000L

        /** Tooltip vertical offset below the tapped label in pixels. */
        private const val TOOLTIP_OFFSET_PX = 16

        /** Margin from screen edges for tooltip positioning in pixels. */
        private const val TOOLTIP_MARGIN_PX = 16

        /** Estimated tooltip height for flip-above calculation in pixels. */
        private const val TOOLTIP_ESTIMATE_HEIGHT_PX = 180
    }
}
