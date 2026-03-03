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
import com.dstranslator.R
import com.dstranslator.data.settings.SettingsRepository
import kotlinx.coroutines.runBlocking

/**
 * Service that displays a draggable, semi-transparent floating capture button
 * via SYSTEM_ALERT_WINDOW permission.
 *
 * - Tap: Sends ACTION_CAPTURE intent to CaptureService with pulse animation feedback
 * - Drag: Repositions the button on screen, persists position
 *
 * This is a simple service (not @AndroidEntryPoint) that communicates with
 * CaptureService via explicit intents.
 */
class FloatingButtonService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingButton: View
    private lateinit var params: WindowManager.LayoutParams

    // SettingsRepository created manually since this service is not Hilt-injected
    private lateinit var settingsRepository: SettingsRepository

    // Drag state tracking
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        settingsRepository = SettingsRepository(applicationContext)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the floating button layout
        floatingButton = LayoutInflater.from(this).inflate(R.layout.floating_button, null)

        // Configure window parameters for overlay
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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

        // Set up touch listener for drag and tap
        floatingButton.setOnTouchListener(::onTouch)

        // Add button to window
        windowManager.addView(floatingButton, params)
    }

    @Suppress("ClickableViewAccessibility")
    private fun onTouch(view: View, event: MotionEvent): Boolean {
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

                // Start dragging if moved more than 10px
                if (!isDragging && (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD)) {
                    isDragging = true
                }

                if (isDragging) {
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(floatingButton, params)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    // This was a tap -- trigger capture
                    triggerCapture()
                    playPulseAnimation(view)
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
     * Send ACTION_CAPTURE intent to CaptureService.
     */
    private fun triggerCapture() {
        val captureIntent = Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_CAPTURE
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
        windowManager.removeView(floatingButton)
    }

    companion object {
        /** Minimum movement in pixels before a touch is considered a drag */
        private const val DRAG_THRESHOLD = 10

        /** Duration of the pulse animation in milliseconds */
        private const val PULSE_DURATION_MS = 200L
    }
}
