package com.dstranslator.service

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.dstranslator.R
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.domain.model.CaptureScope
import com.dstranslator.domain.model.PipelineState
import com.dstranslator.domain.model.OverlayMode
import com.dstranslator.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
 * - Overlay mode button: Cycles Off/OverlayOnSource/Panel
 * - Screen switch button: Moves bubble and overlay to other display
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

    // View references -- Phase 4 buttons
    private lateinit var btnRegionEdit: ImageView
    private lateinit var btnAutoReadToggle: ImageView
    private lateinit var btnProfile: ImageView

    // View references -- Phase 5 overlay buttons
    private lateinit var btnOverlayMode: ImageView
    private lateinit var btnScreenSwitch: ImageView

    // Overlay display manager (non-null while overlay is active)
    private var overlayDisplayManager: OverlayDisplayManager? = null

    // Current overlay mode tracking
    private var currentOverlayMode: OverlayMode = OverlayMode.Off

    // Current display ID for screen switching
    private var currentDisplayId: Int = Display.DEFAULT_DISPLAY

    // Current display ID for overlays (panel/labels) which may differ from bubble display
    private var overlayDisplayId: Int = Display.DEFAULT_DISPLAY

    // Current display context for overlays on the active display
    private var currentDisplayContext: Context? = null

    // Whether multiple displays are available for switching
    private var hasMultipleDisplays: Boolean = false

    // Region edit overlay (non-null while overlay is visible)
    private var regionEditOverlay: RegionEditOverlay? = null

    // Auto-read state tracking
    private var isAutoReadActive = false

    private var currentCaptureScope: CaptureScope = CaptureScope.Both

    // Pending region edit flag: set when capture permission needs to be granted first
    private var pendingRegionEdit = false

    private var pendingSingleCapture = false
    private var pendingContinuousStart = false

    private var pendingRegionEditResetJob: Job? = null
    private var pendingSingleCaptureResetJob: Job? = null
    private var pendingContinuousStartResetJob: Job? = null

    // Latest pipeline state tracking for button enable/disable rules
    private var latestPipelineState: PipelineState = PipelineState.Idle
    private var latestContinuousActive: Boolean = false

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
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        hasMultipleDisplays = displayManager.displays.size >= 2

        val initialDisplay = displayManager.displays.firstOrNull { it.displayId == currentDisplayId }
            ?: displayManager.displays.firstOrNull()
        currentDisplayContext = if (initialDisplay != null) createDisplayContext(initialDisplay) else this
        overlayDisplayId = currentDisplayId
        windowManager = (currentDisplayContext ?: this).getSystemService(WINDOW_SERVICE) as WindowManager

        // Inflate the expandable bubble menu layout
        menuView = LayoutInflater.from(this).inflate(R.layout.floating_bubble_menu, null)

        // Find view references -- original buttons
        btnBubble = menuView.findViewById(R.id.btn_bubble)
        btnContinuousStart = menuView.findViewById(R.id.btn_continuous_start)
        btnContinuousStop = menuView.findViewById(R.id.btn_continuous_stop)
        btnSingleCapture = menuView.findViewById(R.id.btn_single_capture)

        // Find view references -- Phase 4 buttons
        btnRegionEdit = menuView.findViewById(R.id.btn_region_edit)
        btnAutoReadToggle = menuView.findViewById(R.id.btn_auto_read_toggle)
        btnProfile = menuView.findViewById(R.id.btn_profile)

        // Find view references -- Phase 5 overlay buttons
        btnOverlayMode = menuView.findViewById(R.id.btn_overlay_mode)
        btnScreenSwitch = menuView.findViewById(R.id.btn_screen_switch)

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
            handleContinuousStartClick()
            collapseMenu()
        }

        btnContinuousStop.setOnClickListener {
            sendCaptureAction(CaptureService.ACTION_STOP_CONTINUOUS)
            collapseMenu()
        }

        btnSingleCapture.setOnClickListener {
            handleSingleCaptureClick()
            collapseMenu()
        }

        // --- Phase 4 button click listeners ---

        // Pencil button: Region edit with permission guard
        btnRegionEdit.setOnClickListener {
            handleRegionEditClick()
            collapseMenu()
        }

        // Auto-read toggle: Toggle via SettingsRepository
        btnAutoReadToggle.setOnClickListener {
            val newState = !isAutoReadActive
            isAutoReadActive = newState
            updateAutoReadButtonAppearance(newState)
            observerScope.launch(Dispatchers.IO) { settingsRepository.setAutoReadEnabled(newState) }
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

        // --- Phase 5 overlay button click listeners ---

        // Overlay mode button: Cycle Off -> OverlayOnSource -> Panel -> Off
        btnOverlayMode.setOnClickListener {
            observerScope.launch {
                val nextMode = when (currentOverlayMode) {
                    OverlayMode.Off -> OverlayMode.OverlayOnSource
                    OverlayMode.OverlayOnSource -> OverlayMode.Panel
                    OverlayMode.Panel -> OverlayMode.Off
                }
                handleOverlayModeSwitch(nextMode)
            }
            collapseMenu()
        }

        // Screen switch button: Move bubble and overlay to other display
        btnScreenSwitch.setOnClickListener {
            observerScope.launch { handleScreenSwitch() }
            collapseMenu()
        }

        btnScreenSwitch.setOnLongClickListener {
            observerScope.launch(Dispatchers.IO) {
                val nextScope = if (!hasMultipleDisplays) {
                    when (currentCaptureScope) {
                        CaptureScope.Both -> CaptureScope.Primary
                        CaptureScope.Primary -> CaptureScope.Both
                        CaptureScope.Secondary -> CaptureScope.Both
                    }
                } else {
                    when (currentCaptureScope) {
                        CaptureScope.Primary -> CaptureScope.Secondary
                        CaptureScope.Secondary -> CaptureScope.Both
                        CaptureScope.Both -> CaptureScope.Primary
                    }
                }
                settingsRepository.setCaptureScope(nextScope)
            }
            true
        }

        // Observe continuous mode state to change bubble appearance and button states.
        // When continuous capture is active: show stop button highlighted, dim start button.
        // When inactive: show start button normal, dim stop button.
        observerScope.launch {
            CaptureService.isContinuousActive.collect { isActive ->
                latestContinuousActive = isActive
                val bgRes = if (isActive) R.drawable.bg_floating_button_active else R.drawable.bg_floating_button
                btnBubble.setBackgroundResource(bgRes)
                updateActionButtonStates()
            }
        }

        // Observe auto-read state to update toggle button appearance
        observerScope.launch {
            settingsRepository.autoReadEnabledFlow().collect { enabled ->
                isAutoReadActive = enabled
                updateAutoReadButtonAppearance(enabled)
            }
        }

        // Observe overlay mode state to update overlay mode button appearance
        observerScope.launch {
            settingsRepository.overlayModeFlow().collect { mode ->
                currentOverlayMode = mode
                val bgRes = if (mode != OverlayMode.Off) R.drawable.bg_floating_button_active else R.drawable.bg_floating_button
                btnOverlayMode.setBackgroundResource(bgRes)
            }
        }

        observerScope.launch {
            settingsRepository.captureScopeFlow().collect { scope ->
                currentCaptureScope = scope
                val bgRes = if (scope == CaptureScope.Both) {
                    R.drawable.bg_floating_button_active
                } else {
                    R.drawable.bg_floating_button
                }
                btnScreenSwitch.setBackgroundResource(bgRes)
            }
        }

        // Observe pipeline state for pending region edit after permission grant
        // and update button enabled states based on capture service state
        observerScope.launch {
            CaptureService.pipelineState.collect { state ->
                latestPipelineState = state
                val captureReady = CaptureService.screenCaptureManagerRef != null
                if (captureReady) {
                    if (pendingRegionEdit) {
                        pendingRegionEdit = false
                        openRegionEditOverlay()
                    }
                    if (pendingSingleCapture) {
                        pendingSingleCapture = false
                        sendCaptureAction(CaptureService.ACTION_CAPTURE)
                        playPulseAnimation(btnBubble)
                    }
                    if (pendingContinuousStart) {
                        pendingContinuousStart = false
                        sendCaptureAction(CaptureService.ACTION_START_CONTINUOUS)
                    }
                }
                updateActionButtonStates()
            }
        }

        // Add menu to window
        windowManager.addView(menuView, params)
        updateActionButtonStates()
    }

    private fun updateAutoReadButtonAppearance(enabled: Boolean) {
        val bgRes = if (enabled) R.drawable.bg_floating_button_active else R.drawable.bg_floating_button
        btnAutoReadToggle.setBackgroundResource(bgRes)
    }

    private fun setButtonEnabled(button: ImageView, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) BUTTON_ACTIVE_ALPHA else BUTTON_DIMMED_ALPHA
    }

    private fun updateActionButtonStates() {
        val state = latestPipelineState
        val isBusy = state is PipelineState.Capturing || state is PipelineState.Processing
        val isContinuous = latestContinuousActive

        setButtonEnabled(btnContinuousStart, enabled = !isContinuous && !isBusy)
        setButtonEnabled(btnContinuousStop, enabled = isContinuous)
        setButtonEnabled(btnSingleCapture, enabled = !isBusy && !isContinuous)

        setButtonEnabled(btnRegionEdit, enabled = regionEditOverlay == null)
        setButtonEnabled(btnAutoReadToggle, enabled = true)
        setButtonEnabled(btnProfile, enabled = true)
        setButtonEnabled(btnOverlayMode, enabled = true)
        setButtonEnabled(btnScreenSwitch, enabled = hasMultipleDisplays)
    }

    private fun handleContinuousStartClick() {
        if (CaptureService.screenCaptureManagerRef != null) {
            sendCaptureAction(CaptureService.ACTION_START_CONTINUOUS)
            return
        }
        pendingContinuousStart = true
        schedulePendingContinuousStartReset()
        requestCapturePermissionFromBubble()
    }

    private fun handleSingleCaptureClick() {
        if (CaptureService.screenCaptureManagerRef != null) {
            sendCaptureAction(CaptureService.ACTION_CAPTURE)
            playPulseAnimation(btnBubble)
            return
        }
        pendingSingleCapture = true
        schedulePendingSingleCaptureReset()
        requestCapturePermissionFromBubble()
    }

    private fun requestCapturePermissionFromBubble() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_START_CAPTURE_FROM_BUBBLE
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
    }

    /**
     * Handle overlay mode switching.
     *
     * - Off: Clean up overlay, restore Presentation if it was dismissed.
     * - OverlayOnSource / Panel: Create OverlayDisplayManager if needed,
     *   switch mode, persist to settings, optionally dismiss Presentation.
     */
    private suspend fun handleOverlayModeSwitch(mode: OverlayMode) {
        if (mode == OverlayMode.Off) {
            // Clean up existing overlay
            overlayDisplayManager?.cleanup()
            overlayDisplayManager = null
            settingsRepository.setOverlayMode(OverlayMode.Off)

            // Restore Presentation if it was dismissed
            sendCaptureAction(CaptureService.ACTION_RESTORE_PRESENTATION)
            Log.d(TAG, "Overlay mode switched to Off, Presentation restore requested")
        } else {
            // Create OverlayDisplayManager if not already active
            if (overlayDisplayManager == null) {
                overlayDisplayId = currentDisplayId
                val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
                val targetDisplay = displayManager.displays.firstOrNull { it.displayId == overlayDisplayId }
                    ?: displayManager.displays.first()

                overlayDisplayManager = OverlayDisplayManager(
                    service = this,
                    targetDisplay = targetDisplay,
                    translations = CaptureService.translations,
                    ocrResults = CaptureService.latestOcrResult,
                    onPlayAudio = { text ->
                        val intent = Intent(this, CaptureService::class.java).apply {
                            action = CaptureService.ACTION_SPEAK
                            putExtra(CaptureService.EXTRA_SPEAK_TEXT, text)
                        }
                        startService(intent)
                    },
                    onWordLookup = { word ->
                        CaptureService.jmdictRepositoryRef?.lookupWord(word.dictionaryForm) ?: emptyList()
                    },
                    settingsRepository = settingsRepository,
                    onRequestSwitchDisplay = ::requestOverlaySwitchDisplay
                )
            }

            // Switch mode on the overlay display manager
            overlayDisplayManager?.switchMode(mode)

            // Persist overlay mode
            settingsRepository.setOverlayMode(mode)

            // Optionally dismiss Presentation when overlay is active
            if (settingsRepository.getDismissPresentationOnOverlay()) {
                sendCaptureAction(CaptureService.ACTION_DISMISS_PRESENTATION)
            }

            Log.d(TAG, "Overlay mode switched to $mode")
        }
    }

    /**
     * Handle screen switch: move bubble and overlay to the other display.
     *
     * 1. Find all available displays
     * 2. Determine target display (switch between primary and secondary)
     * 3. Clean up current overlay on old display
     * 4. Remove bubble from current WindowManager
     * 5. Create new display context and WindowManager for target display
     * 6. Re-add bubble on new display
     * 7. Recreate overlay on new display if overlay mode is active
     */
    private suspend fun handleScreenSwitch() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val allDisplays = displayManager.displays
        if (allDisplays.size < 2) {
            Log.w(TAG, "Screen switch requested but only ${allDisplays.size} display(s) available")
            return
        }

        removeRegionEditOverlay()

        // Find target display (the one we're NOT currently on)
        val targetDisplay = allDisplays.firstOrNull { it.displayId != currentDisplayId }
            ?: allDisplays.first()

        // Clean up current overlay
        overlayDisplayManager?.cleanup()
        overlayDisplayManager = null

        // Remove bubble from current WindowManager
        try {
            windowManager.removeView(menuView)
        } catch (_: IllegalArgumentException) {
            // View already removed
        }

        // Create new display context and WindowManager for target display
        val targetContext = createDisplayContext(targetDisplay)
        currentDisplayContext = targetContext
        windowManager = targetContext.getSystemService(WINDOW_SERVICE) as WindowManager

        // Restore or use default position for the target display
        val savedPosition = settingsRepository.getFloatingButtonPosition()
        if (savedPosition != null) {
            params.x = savedPosition.first
            params.y = savedPosition.second
        }

        // Re-add menuView to new WindowManager
        windowManager.addView(menuView, params)

        // Update current display tracking
        currentDisplayId = targetDisplay.displayId
        overlayDisplayId = currentDisplayId
        hasMultipleDisplays = allDisplays.size >= 2

        // If overlay mode is active, recreate overlay on the new display
        if (currentOverlayMode != OverlayMode.Off) {
            overlayDisplayManager = OverlayDisplayManager(
                service = this,
                targetDisplay = targetDisplay,
                translations = CaptureService.translations,
                ocrResults = CaptureService.latestOcrResult,
                onPlayAudio = { text ->
                    val intent = Intent(this, CaptureService::class.java).apply {
                        action = CaptureService.ACTION_SPEAK
                        putExtra(CaptureService.EXTRA_SPEAK_TEXT, text)
                    }
                    startService(intent)
                },
                onWordLookup = { word ->
                    CaptureService.jmdictRepositoryRef?.lookupWord(word.dictionaryForm) ?: emptyList()
                },
                settingsRepository = settingsRepository,
                onRequestSwitchDisplay = ::requestOverlaySwitchDisplay
            )
            overlayDisplayManager?.switchMode(currentOverlayMode)
        }

        updateActionButtonStates()
        Log.d(TAG, "Bubble and overlay switched to display ${targetDisplay.displayId}")
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
            schedulePendingRegionEditReset()
            val intent = Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_START_CAPTURE_THEN_EDIT_REGIONS
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }

    private fun schedulePendingRegionEditReset() {
        pendingRegionEditResetJob?.cancel()
        pendingRegionEditResetJob = observerScope.launch {
            delay(PENDING_RESET_TIMEOUT_MS)
            if (pendingRegionEdit && CaptureService.screenCaptureManagerRef == null) {
                pendingRegionEdit = false
                updateActionButtonStates()
            }
        }
    }

    private fun schedulePendingSingleCaptureReset() {
        pendingSingleCaptureResetJob?.cancel()
        pendingSingleCaptureResetJob = observerScope.launch {
            delay(PENDING_RESET_TIMEOUT_MS)
            if (pendingSingleCapture && CaptureService.screenCaptureManagerRef == null) {
                pendingSingleCapture = false
                updateActionButtonStates()
            }
        }
    }

    private fun schedulePendingContinuousStartReset() {
        pendingContinuousStartResetJob?.cancel()
        pendingContinuousStartResetJob = observerScope.launch {
            delay(PENDING_RESET_TIMEOUT_MS)
            if (pendingContinuousStart && CaptureService.screenCaptureManagerRef == null) {
                pendingContinuousStart = false
                updateActionButtonStates()
            }
        }
    }

    /**
     * Create and show the fullscreen RegionEditOverlay.
     * Loads existing regions, sets up confirm/cancel callbacks.
     */
    private fun openRegionEditOverlay() {
        // Don't open if already showing
        if (regionEditOverlay != null) return

        val overlay = RegionEditOverlay(currentDisplayContext ?: this)

        // Load existing regions
        observerScope.launch {
            val existingRegions = settingsRepository.getCaptureRegions(currentDisplayId)
            overlay.setExistingRegions(existingRegions)
        }

        // Set up confirm callback: save regions and remove overlay
        overlay.onRegionsConfirmed = { regions ->
            observerScope.launch {
                settingsRepository.saveCaptureRegions(currentDisplayId, regions)
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
        updateActionButtonStates()
    }

    private fun requestOverlaySwitchDisplay() {
        observerScope.launch {
            switchOverlayDisplayOnly()
        }
    }

    private suspend fun switchOverlayDisplayOnly() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val allDisplays = displayManager.displays
        if (allDisplays.size < 2) return

        val targetDisplay = allDisplays.firstOrNull { it.displayId != overlayDisplayId }
            ?: allDisplays.firstOrNull()
            ?: return

        overlayDisplayManager?.cleanup()
        overlayDisplayManager = null
        overlayDisplayId = targetDisplay.displayId

        overlayDisplayManager = OverlayDisplayManager(
            service = this,
            targetDisplay = targetDisplay,
            translations = CaptureService.translations,
            ocrResults = CaptureService.latestOcrResult,
            onPlayAudio = { text ->
                val intent = Intent(this, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_SPEAK
                    putExtra(CaptureService.EXTRA_SPEAK_TEXT, text)
                }
                startService(intent)
            },
            onWordLookup = { word ->
                CaptureService.jmdictRepositoryRef?.lookupWord(word.dictionaryForm) ?: emptyList()
            },
            settingsRepository = settingsRepository,
            onRequestSwitchDisplay = ::requestOverlaySwitchDisplay
        )

        overlayDisplayManager?.switchMode(currentOverlayMode)
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
        updateActionButtonStates()
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
     * Expand the bubble menu to show all 8 action buttons in a 2x4 grid.
     * Animates each button with a staggered cascade effect.
     */
    private fun expandMenu() {
        isExpanded = true

        // Row 1 buttons
        val row1 = listOf(btnContinuousStart, btnContinuousStop, btnSingleCapture, btnRegionEdit)
        // Row 2 buttons
        val row2 = listOf(btnAutoReadToggle, btnProfile, btnOverlayMode, btnScreenSwitch)
        val allButtons = row1 + row2

        allButtons.forEachIndexed { index, button ->
            button.visibility = View.VISIBLE
            button.alpha = 0f
            button.translationX = -20f
            val targetAlpha = if (button.isEnabled) BUTTON_ACTIVE_ALPHA else BUTTON_DIMMED_ALPHA

            ObjectAnimator.ofPropertyValuesHolder(
                button,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, targetAlpha),
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

        // Reverse order for collapse animation
        val allButtons = listOf(
            btnScreenSwitch, btnOverlayMode, btnProfile, btnAutoReadToggle,
            btnRegionEdit, btnSingleCapture, btnContinuousStop, btnContinuousStart
        )

        allButtons.forEachIndexed { index, button ->
            val startAlpha = button.alpha
            ObjectAnimator.ofPropertyValuesHolder(
                button,
                PropertyValuesHolder.ofFloat(View.ALPHA, startAlpha, 0f),
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

        // Clean up overlay display manager before removing menu view
        overlayDisplayManager?.cleanup()
        overlayDisplayManager = null

        pendingRegionEditResetJob?.cancel()
        pendingRegionEditResetJob = null
        pendingSingleCaptureResetJob?.cancel()
        pendingSingleCaptureResetJob = null
        pendingContinuousStartResetJob?.cancel()
        pendingContinuousStartResetJob = null

        removeRegionEditOverlay()
        observerScope.cancel()
        try {
            windowManager.removeView(menuView)
        } catch (_: IllegalArgumentException) {
            // View already removed
        }
    }

    companion object {
        private const val TAG = "FloatingButtonService"

        /** Minimum movement in pixels before a touch is considered a drag */
        private const val DRAG_THRESHOLD = 10

        /** Duration of the pulse animation in milliseconds */
        private const val PULSE_DURATION_MS = 200L

        /** Duration of expand/collapse animation in milliseconds */
        private const val EXPAND_DURATION_MS = 150L

        /** Stagger delay between each button animation in milliseconds */
        private const val STAGGER_DELAY_MS = 30

        /** Alpha value for active/enabled buttons (fully visible) */
        private const val BUTTON_ACTIVE_ALPHA = 1.0f

        /** Alpha value for dimmed/disabled buttons (visually indicates unavailable) */
        private const val BUTTON_DIMMED_ALPHA = 0.35f

        /** Intent action to open profiles section in MainActivity */
        const val ACTION_OPEN_PROFILES = "com.dstranslator.action.OPEN_PROFILES"

        private const val PENDING_RESET_TIMEOUT_MS = 45_000L
    }
}
