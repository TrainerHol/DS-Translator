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

    // View references
    private lateinit var btnBubble: ImageView
    private lateinit var btnContinuousStart: ImageView
    private lateinit var btnContinuousStop: ImageView
    private lateinit var btnSingleCapture: ImageView

    // Menu state
    private var isExpanded = false

    // Drag state tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // Coroutine scope for observing continuous mode state
    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        settingsRepository = SettingsRepository(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the expandable bubble menu layout
        menuView = LayoutInflater.from(this).inflate(R.layout.floating_bubble_menu, null)

        // Find view references
        btnBubble = menuView.findViewById(R.id.btn_bubble)
        btnContinuousStart = menuView.findViewById(R.id.btn_continuous_start)
        btnContinuousStop = menuView.findViewById(R.id.btn_continuous_stop)
        btnSingleCapture = menuView.findViewById(R.id.btn_single_capture)

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

        // Set up click listeners for action buttons
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

        // Observe continuous mode state to change bubble appearance
        observerScope.launch {
            CaptureService.isContinuousActive.collect { isActive ->
                val bgRes = if (isActive) R.drawable.bg_floating_button_active else R.drawable.bg_floating_button
                btnBubble.setBackgroundResource(bgRes)
            }
        }

        // Add menu to window
        windowManager.addView(menuView, params)
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
     * Expand the bubble menu to show Play, Stop, and Camera action buttons.
     * Animates each button with a staggered cascade effect.
     */
    private fun expandMenu() {
        isExpanded = true
        val buttons = listOf(btnContinuousStart, btnContinuousStop, btnSingleCapture)

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
     * Collapse the bubble menu to hide the action buttons.
     * Animates with reverse cascade effect.
     */
    private fun collapseMenu() {
        isExpanded = false
        val buttons = listOf(btnSingleCapture, btnContinuousStop, btnContinuousStart)

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
    }
}
