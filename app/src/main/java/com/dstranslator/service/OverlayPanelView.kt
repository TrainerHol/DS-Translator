package com.dstranslator.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dstranslator.domain.model.DictionaryResult
import com.dstranslator.domain.model.OverlayConfig
import com.dstranslator.domain.model.SegmentedWord
import com.dstranslator.domain.model.TranslationEntry
import com.dstranslator.ui.presentation.PresentationLifecycleOwner
import com.dstranslator.ui.presentation.TranslationListScreen
import com.dstranslator.ui.theme.DsTranslatorPresentationTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * ComposeView wrapper that displays the translation panel as a draggable, resizable
 * overlay window via WindowManager.
 *
 * Features:
 * - Top bar with minimize (X), pin toggle, and lock toggle buttons
 * - Reuses existing [TranslationListScreen] composable for the translation list body
 * - Drag support via touch listener on the top bar
 * - Resize support via a bottom-right corner handle
 * - Lock mode: adds FLAG_NOT_TOUCHABLE so all touches pass through to the game
 * - Alpha clamped to 0.8f when locked for Android 12+ untrusted touch compliance
 * - FLAG_SECURE on all windows to prevent OCR feedback loop
 * - Proper lifecycle management via [PresentationLifecycleOwner]
 *
 * The [initialConfig] passed to this class must have ALREADY been resolved by
 * OverlayDisplayManager.showPanel() via resolveForDisplay(). panelWidth/panelHeight
 * are real pixel values, never 0 sentinels.
 *
 * @param displayContext Context created for the target display
 * @param windowManager WindowManager for the target display
 * @param translations StateFlow of translation entries to display
 * @param onPlayAudio Callback for TTS playback
 * @param onWordLookup Callback for dictionary word lookup
 * @param initialConfig Pre-resolved overlay configuration (no sentinel values)
 * @param onConfigChanged Callback when user drags/resizes the panel (persist new config)
 */
class OverlayPanelView(
    private val displayContext: Context,
    private val windowManager: WindowManager,
    private val translations: StateFlow<List<TranslationEntry>>,
    private val onPlayAudio: (String) -> Unit,
    private val onWordLookup: (suspend (SegmentedWord) -> List<DictionaryResult>)?,
    private val initialConfig: OverlayConfig,
    private val onConfigChanged: (OverlayConfig) -> Unit
) {
    private var composeView: ComposeView? = null
    private var lifecycleOwner: PresentationLifecycleOwner? = null
    private var params: WindowManager.LayoutParams? = null

    /** Callback invoked when the user taps the minimize (X) button. */
    var onMinimize: (() -> Unit)? = null

    // Mutable state for Compose UI indicators
    private var isPinned by mutableStateOf(initialConfig.isPinned)
    private var isLocked by mutableStateOf(initialConfig.isLocked)

    // Current config tracking for reporting changes
    private var currentX = initialConfig.panelX
    private var currentY = initialConfig.panelY
    private var currentWidth = initialConfig.panelWidth
    private var currentHeight = initialConfig.panelHeight

    // Drag state
    private var dragInitialX = 0
    private var dragInitialY = 0
    private var dragTouchX = 0f
    private var dragTouchY = 0f

    // Resize state
    private var resizeInitialWidth = 0
    private var resizeInitialHeight = 0
    private var resizeTouchX = 0f
    private var resizeTouchY = 0f

    /**
     * Create and show the overlay panel window.
     */
    fun show() {
        val owner = PresentationLifecycleOwner()
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycleOwner = owner

        val view = ComposeView(displayContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                DsTranslatorPresentationTheme {
                    PanelContent()
                }
            }
        }
        composeView = view

        val layoutParams = WindowManager.LayoutParams(
            initialConfig.panelWidth,
            initialConfig.panelHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialConfig.panelX
            y = initialConfig.panelY
            alpha = initialConfig.panelAlpha
        }
        params = layoutParams

        // Apply lock state if initially locked
        if (initialConfig.isLocked) {
            layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            layoutParams.alpha = initialConfig.panelAlpha.coerceAtMost(MAX_PASSTHROUGH_ALPHA)
        }

        try {
            windowManager.addView(view, layoutParams)
        } catch (_: IllegalStateException) {
            // View already added
        }

        // Set up drag handling on the whole view (top bar intercepts in Compose)
        view.setOnTouchListener(::onViewTouch)
    }

    /**
     * Set the lock state of the panel.
     *
     * When locked, FLAG_NOT_TOUCHABLE is added so the panel becomes visible but
     * non-interactive -- all touches pass through to the game underneath.
     * Alpha is clamped to 0.8f for Android 12+ untrusted touch compliance.
     *
     * When unlocked, FLAG_NOT_TOUCHABLE is removed and original alpha restored.
     */
    fun setLocked(locked: Boolean) {
        isLocked = locked
        val lp = params ?: return
        val view = composeView ?: return

        if (locked) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            lp.alpha = initialConfig.panelAlpha.coerceAtMost(MAX_PASSTHROUGH_ALPHA)
        } else {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            lp.alpha = initialConfig.panelAlpha
        }

        try {
            windowManager.updateViewLayout(view, lp)
        } catch (_: IllegalArgumentException) {
            // View not attached
        }

        notifyConfigChanged()
    }

    /**
     * Remove the panel from the window manager and clean up lifecycle.
     */
    fun hide() {
        lifecycleOwner?.let { owner ->
            owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        lifecycleOwner = null

        composeView?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: IllegalArgumentException) {
                // View already removed
            }
        }
        composeView = null
        params = null
    }

    @Composable
    private fun PanelContent() {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xCC1A1A1A)) // Semi-transparent dark background
        ) {
            // Top bar with controls
            TopBar()

            // Translation list body (reuses existing composable)
            Box(modifier = Modifier.weight(1f)) {
                TranslationListScreen(
                    translations = translations,
                    onPlayAudio = onPlayAudio,
                    onWordLookup = onWordLookup
                )
            }

            // Resize handle at bottom-right
            ResizeHandle()
        }
    }

    @Composable
    private fun TopBar() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xBB111111)) // Darker semi-transparent top bar
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Pin toggle
            IconButton(
                onClick = {
                    isPinned = !isPinned
                    notifyConfigChanged()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = if (isPinned) "Unpin panel" else "Pin panel",
                    tint = if (isPinned) Color(0xFF4DD0E1) else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Lock toggle
            IconButton(
                onClick = {
                    setLocked(!isLocked)
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isLocked) "Unlock panel" else "Lock panel",
                    tint = if (isLocked) Color(0xFFF44336) else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Minimize button (X)
            IconButton(
                onClick = { onMinimize?.invoke() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Minimize panel",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }

    @Composable
    private fun ResizeHandle() {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            // Small grip indicator triangle in bottom-right corner
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
    }

    /**
     * Touch handler for the panel view. Handles drag (move) and resize via
     * location-based detection: touches in the top region drag, touches in
     * the bottom-right corner resize.
     */
    @Suppress("ClickableViewAccessibility")
    private fun onViewTouch(view: View, event: MotionEvent): Boolean {
        val lp = params ?: return false

        // Determine if touch is in the resize region (bottom-right 48x48 dp area)
        val resizeThresholdPx = (48 * view.context.resources.displayMetrics.density).toInt()
        val isResizeArea = event.x > view.width - resizeThresholdPx &&
                event.y > view.height - resizeThresholdPx

        // Determine if touch is in the top bar drag region (top 36dp)
        val dragThresholdPx = (36 * view.context.resources.displayMetrics.density).toInt()
        val isDragArea = event.y < dragThresholdPx

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isResizeArea) {
                    resizeInitialWidth = lp.width
                    resizeInitialHeight = lp.height
                    resizeTouchX = event.rawX
                    resizeTouchY = event.rawY
                } else if (isDragArea) {
                    dragInitialX = lp.x
                    dragInitialY = lp.y
                    dragTouchX = event.rawX
                    dragTouchY = event.rawY
                } else {
                    return false // Let Compose handle body touches
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isResizeArea || (resizeInitialWidth > 0 && resizeTouchX > 0f)) {
                    // Resize mode
                    val dx = (event.rawX - resizeTouchX).toInt()
                    val dy = (event.rawY - resizeTouchY).toInt()

                    val minWidthPx = (MIN_PANEL_WIDTH_DP * view.context.resources.displayMetrics.density).toInt()
                    val minHeightPx = (MIN_PANEL_HEIGHT_DP * view.context.resources.displayMetrics.density).toInt()

                    lp.width = (resizeInitialWidth + dx).coerceAtLeast(minWidthPx)
                    lp.height = (resizeInitialHeight + dy).coerceAtLeast(minHeightPx)

                    try {
                        windowManager.updateViewLayout(view, lp)
                    } catch (_: IllegalArgumentException) {}
                } else if (isDragArea || dragTouchX > 0f) {
                    // Drag mode
                    val dx = (event.rawX - dragTouchX).toInt()
                    val dy = (event.rawY - dragTouchY).toInt()

                    if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
                        lp.x = dragInitialX + dx
                        lp.y = dragInitialY + dy

                        try {
                            windowManager.updateViewLayout(view, lp)
                        } catch (_: IllegalArgumentException) {}
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                // Update tracked position/size and notify
                currentX = lp.x
                currentY = lp.y
                currentWidth = lp.width
                currentHeight = lp.height

                // Reset drag/resize state
                dragTouchX = 0f
                dragTouchY = 0f
                resizeTouchX = 0f
                resizeTouchY = 0f
                resizeInitialWidth = 0
                resizeInitialHeight = 0

                notifyConfigChanged()
                return true
            }
        }
        return false
    }

    private fun notifyConfigChanged() {
        val config = OverlayConfig(
            panelX = currentX,
            panelY = currentY,
            panelWidth = currentWidth,
            panelHeight = currentHeight,
            panelAlpha = initialConfig.panelAlpha,
            textSizeSp = initialConfig.textSizeSp,
            isPinned = isPinned,
            isLocked = isLocked
        )
        onConfigChanged(config)
    }

    companion object {
        /** Minimum movement in pixels before a touch is considered a drag */
        private const val DRAG_THRESHOLD = 10

        /** Minimum panel dimensions in dp */
        private const val MIN_PANEL_WIDTH_DP = 200
        private const val MIN_PANEL_HEIGHT_DP = 150

        /**
         * Maximum alpha for Android 12+ untrusted touch compliance.
         * When touch passthrough is needed (locked mode), alpha must be <= 0.8f
         * for the system to allow touches through the overlay.
         */
        private const val MAX_PASSTHROUGH_ALPHA = 0.8f
    }
}
