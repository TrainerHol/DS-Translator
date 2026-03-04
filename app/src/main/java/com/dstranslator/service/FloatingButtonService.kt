package com.dstranslator.service

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.dstranslator.R
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Service that displays a draggable, semi-transparent floating bubble menu
 * via SYSTEM_ALERT_WINDOW permission.
 *
 * - Tap bubble: Expand/collapse the action menu
 * - Drag bubble: Repositions the menu on screen, persists position
 * - Play button: Starts continuous capture mode
 * - Stop button: Stops continuous capture mode
 * - Camera button: Triggers a single capture with pulse animation
 * - Pencil button: Opens region edit overlay (with permission guard)
 * - Auto-read button: Toggles auto-read on/off
 * - Profile button: Opens settings/profiles section
 *
 * Observes CaptureService.isContinuousActive to change bubble appearance
 * when continuous mode is active (teal background indicator).
 *
 * This is a simple service (not @AndroidEntryPoint) that communicates with
 * CaptureService via explicit intents.
 */
class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var menuView: View
    private lateinit var params: WindowManager.LayoutParams

    // SettingsRepository created manually since this service is not Hilt-injected
    private lateinit var settingsRepository: SettingsRepository

    // View references -- original buttons
    private lateinit var btnBubble: ImageView
    private lateinit var btnContinuousStart: ImageView
    private lateinit var btnContinuousStop: ImageView
    private lateinit var btnSingleCapture: ImageView

    // View references -- new buttons (Phase 4)
    private lateinit var btnRegionEdit: ImageView
    private lateinit var btnAutoReadToggle: ImageView
    private lateinit var btnProfile: ImageView

    // Region edit overlay (non-null while overlay is visible)
    private var regionEditOverlay: RegionEditOverlay? = null

    // Auto-read state tracking
    private var isAutoReadActive = false

    // Pending region edit flag: set when capture permission needs to be granted first
    private var pendingRegionEdit = false

    // Menu state
    private var isExpanded = false

    // Drag state tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // Coroutine scope for observing state
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        settingsRepository = SettingsRepository(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the expandable bubble menu layout
        menuView = LayoutInflater.from(this).inflate(R.layout.floating_bubble_menu, null)

        // Find view references -- original buttons
        btnBubble = menuView.findViewById(R.id.btn_bubble)
        btnContinuousStart = menuView.findViewById(R.id.btn_continuous_start)
        btnContinuousStop = menuView.findViewById(R.id.btn_continuous_stop)
        btnSingleCapture = menuView.findViewById(R.id.btn_single_capture)

        // Find view references -- new buttons
        btnRegionEdit = menuView.findViewById(R.id.btn_region_edit)
        btnAutoReadToggle = menuView.findViewById(R.id.btn_auto_read_toggle)
        btnProfile = menuView.findViewById(R.id.btn_profile)

        // Configure window parameters for overlay.
        // FLAG_SECURE prevents this overlay from appearing in MediaProjection
        // captures, which would cause the translator to OCR its own controls.
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        // Restore saved position
        val savedPosition = runBlocking { settingsRepository.getFloatingButtonPosition() }
        if (savedPosition != null) {
            params.x = savedPosition.first
            params.y = savedPosition.second
        }

        // Set up touch listener on the main bubble for drag and tap (expand/collapse)
        btnBubble.setOnTouchListener(::onBubbleTouch)

        // Set up click listeners for original action buttons
        btnContinuousStart.setOnClickListener {
            sendCaptureAction(CaptureService.ACTION_START_CONTINUOUS)
            collapseMenu()
        }

        btnContinuousStop.setOnClickListener {
            sendCaptureAction(CaptureService.ACTION_STOP_CONTINUOUS)
            collapseMenu()
        }

        btnSingleCapture.setOnClickListener {
            sendCaptureAction(CaptureService.ACTION_CAPTURE)
            playPulseAnimation(btnBubble)
            collapseMenu()
        }

        // --- New button click listeners (Phase 4) ---

        // Pencil button: Region edit with permission guard
        btnRegionEdit.setOnClickListener {
            handleRegionEditClick()
            collapseMenu()
        }

        // Auto-read toggle: Toggle via SettingsRepository
        btnAutoReadToggle.setOnClickListener {
            observerScope.launch {
                val newState = !isAutoReadActive
                settingsRepository.setAutoReadEnabled(newState)
                // State will be updated via the Flow observer below
            }
            collapseMenu()
        }

        // Profile button: Open settings/profiles section
        btnProfile.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = ACTION_OPEN_PROFILES
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            collapseMenu()
        }

        // Observe continuous mode state to change bubble appearance
        observerScope.launch {
            CaptureService.isContinuousActive.collect { isActive ->
                val bgRes = if (isActive) R.drawable.bg_floating_button_active else R.drawable.bg_floating_button
                btnBubble.setBackgroundResource(bgRes)
            }
        }

        // Observe auto-read state to update toggle button appearance
        observerScope.launch {
            settingsRepository.autoReadEnabledFlow().collect { enabled ->
                isAutoReadActive = enabled
                val bgRes = if (enabled) R.drawable.bg_floating_button_active else R.drawable.bg_floating_button
                btnAutoReadToggle.setBackgroundResource(bgRes)
            }
        }

        // Observe pipeline state for pending region edit after permission grant
        observerScope.launch {
            CaptureService.pipelineState.collect { state ->
                if (pendingRegionEdit && CaptureService.screenCaptureManagerRef != null) {
                    pendingRegionEdit = false
                    openRegionEditOverlay()
                }
            }
        }

        // Add menu to window
        windowManager.addView(menuView, params)
    }

    /**
     * Handle pencil button click with MediaProjection permission guard.
     *
     * If capture permission is already held (screenCaptureManagerRef != null),
     * opens the RegionEditOverlay immediately. Otherwise, triggers the capture
     * permission flow via CaptureService/MainActivity and sets a pending flag
     * to open the overlay after permission is granted.
     */
    private fun handleRegionEditClick() {
        if (CaptureService.screenCaptureManagerRef != null) {
            // Permission already held -- open overlay immediately
            openRegionEditOverlay()
        } else {
            // Permission not held -- trigger capture permission flow
            // and set pending flag to open overlay after grant
            pendingRegionEdit = true
            sendCaptureAction(CaptureService.ACTION_START)
        }
    }

    /**
     * Create and show the fullscreen RegionEditOverlay.
     * Loads existing regions, sets up confirm/cancel callbacks.
     */
    private fun openRegionEditOverlay() {
        // Don't open if already showing
        if (regionEditOverlay != null) return

        val overlay = RegionEditOverlay(this)

        // Load existing regions
        observerScope.launch {
            val existingRegions = settingsRepository.getCaptureRegions()
            overlay.setExistingRegions(existingRegions)
        }

        // Set up confirm callback: save regions and remove overlay
        overlay.onRegionsConfirmed = { regions ->
            observerScope.launch {
                settingsRepository.saveCaptureRegions(regions)
            }
            removeRegionEditOverlay()
        }

        // Set up cancel callback: just remove overlay
        overlay.onEditCancelled = {
            removeRegionEditOverlay()
        }

        // Add fullscreen overlay to WindowManager
        // FLAG_NOT_FOCUSABLE: keyboard not needed
        // FLAG_SECURE: prevents overlay from appearing in screen captures
        // NOT using FLAG_NOT_TOUCHABLE: overlay must consume all touches
        val overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(overlay, overlayParams)
        regionEditOverlay = overlay
    }

    /**
     * Remove the RegionEditOverlay from WindowManager.
     */
    private fun removeRegionEditOverlay() {
        regionEditOverlay?.let { overlay ->
            try {
                windowManager.removeView(overlay)
            } catch (_: IllegalArgumentException) {
                // View already removed
            }
        }
        regionEditOverlay = null
    }

    @Suppress("ClickableViewAccessibility")
    private fun onBubbleTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params.x
                initialY = params.y
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - initialTouchX).toInt()
                val dy = (event.rawY - initialTouchY).toInt()

                // Start dragging if moved more than threshold
                if (!isDragging && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                    isDragging = true
                }

                if (isDragging) {
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(menuView, params)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // This was a tap -- toggle expand/collapse
                    if (isExpanded) {
                        collapseMenu()
                    } else {
                        expandMenu()
                    }
                }

                // Persist final position
                runBlocking {
                    settingsRepository.setFloatingButtonPosition(params.x, params.y)
                }
                return true
            }
        }
        return false
    }

    /**
     * Expand the bubble menu to show all 6 action buttons.
     * Animates each button with a staggered cascade effect.
     */
    private fun expandMenu() {
        isExpanded = true
        val buttons = listOf(
            btnContinuousStart, btnContinuousStop, btnSingleCapture,
            btnRegionEdit, btnAutoReadToggle, btnProfile
        )

        buttons.forEachIndexed { index, button ->
            button.visibility = View.VISIBLE
            button.alpha = 0f
            button.translationX = -20f

            ObjectAnimator.ofPropertyValuesHolder(
                button,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, -20f, 0f)
            ).apply {
                duration = EXPAND_DURATION_MS
                startDelay = (index * STAGGER_DELAY_MS).toLong()
                start()
            }
        }

        // Update layout so WindowManager knows the new size
        windowManager.updateViewLayout(menuView, params)
    }

    /**
     * Collapse the bubble menu to hide all action buttons.
     * Animates with reverse cascade effect.
     */
    private fun collapseMenu() {
        isExpanded = false
        val buttons = listOf(
            btnProfile, btnAutoReadToggle, btnRegionEdit,
            btnSingleCapture, btnContinuousStop, btnContinuousStart
        )

        buttons.forEachIndexed { index, button ->
            ObjectAnimator.ofPropertyValuesHolder(
                button,
                PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, -20f)
            ).apply {
                duration = EXPAND_DURATION_MS
                startDelay = (index * STAGGER_DELAY_MS).toLong()
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        button.visibility = View.GONE
                    }
                })
                start()
            }
        }

        // Update layout so WindowManager recalculates size
        windowManager.updateViewLayout(menuView, params)
    }

    /**
     * Send an action intent to CaptureService.
     */
    private fun sendCaptureAction(action: String) {
        val captureIntent = Intent(this, CaptureService::class.java).apply {
            this.action = action
        }
        startService(captureIntent)
    }

    /**
     * Play a brief pulse animation on the button for tap confirmation feedback.
     * Scales from 1.0 -> 1.3 -> 1.0 over 200ms.
     */
    private fun playPulseAnimation(view: View) {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.3f, 1.0f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.3f, 1.0f)
        ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY).apply {
            duration = PULSE_DURATION_MS
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        removeRegionEditOverlay()
        observerScope.cancel()
        windowManager.removeView(menuView)
    }

    companion object {
        /** Minimum movement in pixels before a touch is considered a drag */
        private const val DRAG_THRESHOLD = 10

        /** Duration of the pulse animation in milliseconds */
        private const val PULSE_DURATION_MS = 200L

        /** Duration of expand/collapse animation in milliseconds */
        private const val EXPAND_DURATION_MS = 150L

        /** Stagger delay between each button animation in milliseconds */
        private const val STAGGER_DELAY_MS = 30

        /** Intent action to open profiles section in MainActivity */
        const val ACTION_OPEN_PROFILES = "com.dstranslator.action.OPEN_PROFILES"
    }
}
