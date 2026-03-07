package com.dstranslator.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.dstranslator.domain.model.CaptureRegion
import java.util.UUID

/**
 * Fullscreen overlay View for drawing and editing capture regions directly on the live game screen.
 *
 * Replaces the Phase 1 frozen-screenshot approach with live overlay drawing.
 * This view consumes ALL touch events to prevent accidental game input during editing.
 *
 * Usage:
 * 1. Create and add to WindowManager with MATCH_PARENT, FLAG_NOT_FOCUSABLE | FLAG_SECURE
 * 2. Optionally call setExistingRegions() to load previously saved regions
 * 3. User draws rectangles (finger drag) and fine-tunes with drag handles
 * 4. Each region can be flagged as auto-read independently
 * 5. Confirm saves regions via onRegionsConfirmed callback
 * 6. Cancel discards changes via onEditCancelled callback
 */
class RegionEditOverlay(context: Context) : View(context) {

    // --- Draw state sealed class ---

    sealed class DrawState {
        data object Idle : DrawState()
        data class Drawing(val startX: Float, val startY: Float) : DrawState()
        data class Adjusting(val regionIndex: Int, val handleIndex: Int) : DrawState()
    }

    // --- Editable region data class ---

    data class EditableRegion(
        val bounds: RectF,
        val id: String,
        var autoRead: Boolean = false
    )

    // --- State ---

    private var drawState: DrawState = DrawState.Idle
    private val regions = mutableListOf<EditableRegion>()
    private var selectedRegionIndex: Int = -1
    private var pendingExistingRegions: List<CaptureRegion>? = null

    // Temporary rectangle while drawing
    private var tempRect: RectF? = null

    // --- Callbacks ---

    var onRegionsConfirmed: ((List<CaptureRegion>) -> Unit)? = null
    var onEditCancelled: (() -> Unit)? = null

    // --- Paints (initialized once for performance) ---

    private val dimPaint = Paint().apply {
        color = Color.argb(100, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEAL_COLOR
        style = Paint.Style.STROKE
        strokeWidth = BORDER_STROKE_WIDTH
    }

    private val selectedBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEAL_LIGHT_COLOR
        style = Paint.Style.STROKE
        strokeWidth = BORDER_STROKE_WIDTH * 1.5f
    }

    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEAL_COLOR
        style = Paint.Style.FILL
    }

    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val autoReadIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 76, 175, 80) // Green
        style = Paint.Style.FILL
    }

    private val buttonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 48, 48, 48)
        style = Paint.Style.FILL
    }

    private val buttonIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
    }

    private val tempRectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEAL_COLOR
        style = Paint.Style.STROKE
        strokeWidth = BORDER_STROKE_WIDTH
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    // --- Control button rects (calculated in onDraw based on view size) ---

    private val confirmButtonRect = RectF()
    private val cancelButtonRect = RectF()
    private val addButtonRect = RectF()
    private val autoReadToggleRect = RectF()
    private val deleteButtonRect = RectF()
    private val clearAllButtonRect = RectF()

    // --- Initialization ---

    init {
        // Enable hardware layer for PorterDuff.CLEAR to work correctly
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    /**
     * Load existing capture regions for editing.
     * Converts CaptureRegion coordinates to EditableRegion with RectF bounds.
     */
    fun setExistingRegions(existingRegions: List<CaptureRegion>) {
        pendingExistingRegions = existingRegions
        applyPendingExistingRegionsIfReady()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyPendingExistingRegionsIfReady()
    }

    private fun applyPendingExistingRegionsIfReady() {
        val existing = pendingExistingRegions ?: return
        if (width <= 0 || height <= 0) return

        regions.clear()

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        existing.forEach { region ->
            val bounds = if (
                region.normalizedX != null &&
                region.normalizedY != null &&
                region.normalizedWidth != null &&
                region.normalizedHeight != null
            ) {
                val left = (region.normalizedX.coerceIn(0f, 1f) * viewWidth)
                    .coerceIn(0f, viewWidth)
                val top = (region.normalizedY.coerceIn(0f, 1f) * viewHeight)
                    .coerceIn(0f, viewHeight)
                val right = ((region.normalizedX + region.normalizedWidth).coerceIn(0f, 1f) * viewWidth)
                    .coerceIn(left + 1f, viewWidth)
                val bottom = ((region.normalizedY + region.normalizedHeight).coerceIn(0f, 1f) * viewHeight)
                    .coerceIn(top + 1f, viewHeight)
                RectF(left, top, right, bottom)
            } else {
                RectF(
                    region.x.toFloat(),
                    region.y.toFloat(),
                    (region.x + region.width).toFloat(),
                    (region.y + region.height).toFloat()
                )
            }

            regions.add(
                EditableRegion(
                    bounds = bounds,
                    id = region.id,
                    autoRead = region.autoRead
                )
            )
        }

        if (regions.isNotEmpty()) {
            selectedRegionIndex = 0
        }

        pendingExistingRegions = null
        invalidate()
    }

    // --- Drawing ---

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // 1. Draw semi-transparent dark overlay
        canvas.drawColor(Color.argb(100, 0, 0, 0))

        // 2. Draw each region
        regions.forEachIndexed { index, region ->
            drawRegion(canvas, region, index == selectedRegionIndex)
        }

        // 3. Draw temporary rectangle while user is drawing
        tempRect?.let { rect ->
            canvas.drawRect(rect, tempRectPaint)
        }

        // 4. Calculate and draw control buttons
        calculateControlButtonPositions(viewWidth, viewHeight)
        drawControlButtons(canvas)
    }

    private fun drawRegion(canvas: Canvas, region: EditableRegion, isSelected: Boolean) {
        val bounds = region.bounds

        // Draw region interior (slightly lighter to indicate region area)
        val regionFillPaint = Paint().apply {
            color = if (isSelected) Color.argb(40, 0, 188, 212) else Color.argb(20, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawRect(bounds, regionFillPaint)

        // Draw border
        canvas.drawRect(bounds, if (isSelected) selectedBorderPaint else borderPaint)

        // Draw resize handles (8 handles: 4 corners + 4 edge midpoints)
        if (isSelected) {
            val handles = getHandlePositions(bounds)
            handles.forEach { (hx, hy) ->
                canvas.drawCircle(hx, hy, HANDLE_RADIUS, handlePaint)
                canvas.drawCircle(hx, hy, HANDLE_RADIUS, handleBorderPaint)
            }
        }

        // Draw auto-read indicator (green dot in top-right corner)
        if (region.autoRead) {
            val indicatorX = bounds.right - 12f
            val indicatorY = bounds.top + 12f
            canvas.drawCircle(indicatorX, indicatorY, 8f, autoReadIndicatorPaint)
        }
    }

    /**
     * Get the 8 handle positions for a region (corners + edge midpoints).
     * Returns list of (x, y) pairs.
     */
    private fun getHandlePositions(bounds: RectF): List<Pair<Float, Float>> {
        return listOf(
            // Corners
            bounds.left to bounds.top,           // 0: top-left
            bounds.right to bounds.top,          // 1: top-right
            bounds.right to bounds.bottom,       // 2: bottom-right
            bounds.left to bounds.bottom,        // 3: bottom-left
            // Edge midpoints
            bounds.centerX() to bounds.top,      // 4: top-center
            bounds.right to bounds.centerY(),    // 5: right-center
            bounds.centerX() to bounds.bottom,   // 6: bottom-center
            bounds.left to bounds.centerY()      // 7: left-center
        )
    }

    private fun calculateControlButtonPositions(viewWidth: Float, viewHeight: Float) {
        val buttonSize = CONTROL_BUTTON_SIZE
        val margin = CONTROL_BUTTON_MARGIN
        val bottomY = viewHeight - margin - buttonSize

        // Confirm (bottom-right)
        confirmButtonRect.set(
            viewWidth - margin - buttonSize, bottomY,
            viewWidth - margin, bottomY + buttonSize
        )

        // Cancel (bottom-left)
        cancelButtonRect.set(
            margin, bottomY,
            margin + buttonSize, bottomY + buttonSize
        )

        // Add Region (top-center)
        addButtonRect.set(
            viewWidth / 2f - buttonSize / 2f, margin,
            viewWidth / 2f + buttonSize / 2f, margin + buttonSize
        )

        clearAllButtonRect.set(
            margin, margin,
            margin + buttonSize, margin + buttonSize
        )

        // Auto-Read Toggle (bottom-center-left, only shown when region selected)
        autoReadToggleRect.set(
            viewWidth / 2f - buttonSize - margin / 2f, bottomY,
            viewWidth / 2f - margin / 2f, bottomY + buttonSize
        )

        // Delete Region (bottom-center-right, only shown when region selected)
        deleteButtonRect.set(
            viewWidth / 2f + margin / 2f, bottomY,
            viewWidth / 2f + buttonSize + margin / 2f, bottomY + buttonSize
        )
    }

    private fun drawControlButtons(canvas: Canvas) {
        // Confirm button (checkmark)
        drawButton(canvas, confirmButtonRect, "\u2713") // checkmark

        // Cancel button (X)
        drawButton(canvas, cancelButtonRect, "\u2717") // X

        // Add Region button (+)
        drawButton(canvas, addButtonRect, "+")

        if (regions.isNotEmpty()) {
            drawButton(canvas, clearAllButtonRect, "CLR")
        }

        // Only show auto-read toggle and delete when a region is selected
        if (selectedRegionIndex in regions.indices) {
            val region = regions[selectedRegionIndex]

            // Auto-Read Toggle (speaker icon represented by text)
            val autoReadBg = Paint(buttonBgPaint).apply {
                color = if (region.autoRead) Color.argb(200, 76, 175, 80) else Color.argb(200, 48, 48, 48)
            }
            drawButton(canvas, autoReadToggleRect, "\uD83D\uDD0A", autoReadBg) // speaker emoji

            // Delete button (trash)
            drawButton(canvas, deleteButtonRect, "\uD83D\uDDD1") // wastebasket emoji
        }

        // Draw hint text at top
        val hintText = when {
            regions.isEmpty() -> "Draw a rectangle to create a capture region"
            selectedRegionIndex >= 0 -> "Region ${selectedRegionIndex + 1}/${regions.size} - Drag handles to resize"
            else -> "Tap a region to select, or draw a new one"
        }
        canvas.drawText(hintText, width / 2f, CONTROL_BUTTON_SIZE + CONTROL_BUTTON_MARGIN + 40f, labelPaint)
    }

    private fun drawButton(canvas: Canvas, rect: RectF, label: String, bgPaint: Paint = buttonBgPaint) {
        val cornerRadius = 12f
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)
        val textY = rect.centerY() + buttonIconPaint.textSize / 3f
        canvas.drawText(label, rect.centerX(), textY, buttonIconPaint)
    }

    // --- Touch handling ---

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // ALWAYS return true to consume all touches (blocks game input)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> handleTouchDown(event.x, event.y)
            MotionEvent.ACTION_MOVE -> handleTouchMove(event.x, event.y)
            MotionEvent.ACTION_UP -> handleTouchUp(event.x, event.y)
        }
        invalidate()
        return true
    }

    private fun handleTouchDown(x: Float, y: Float) {
        // Check control buttons first
        if (handleControlButtonTap(x, y)) return

        // Check if touch hits a resize handle (selected region only)
        if (selectedRegionIndex in regions.indices) {
            val handles = getHandlePositions(regions[selectedRegionIndex].bounds)
            for ((i, handle) in handles.withIndex()) {
                val dist = Math.hypot((x - handle.first).toDouble(), (y - handle.second).toDouble())
                if (dist <= HANDLE_TOUCH_TARGET) {
                    drawState = DrawState.Adjusting(selectedRegionIndex, i)
                    return
                }
            }
        }

        // Check if touch hits inside an existing region (select it)
        for ((i, region) in regions.withIndex()) {
            if (region.bounds.contains(x, y)) {
                selectedRegionIndex = i
                return
            }
        }

        // Otherwise start drawing a new region
        drawState = DrawState.Drawing(x, y)
        tempRect = RectF(x, y, x, y)
    }

    private fun handleTouchMove(x: Float, y: Float) {
        when (val state = drawState) {
            is DrawState.Drawing -> {
                // Update temporary rectangle
                tempRect = RectF(
                    minOf(state.startX, x),
                    minOf(state.startY, y),
                    maxOf(state.startX, x),
                    maxOf(state.startY, y)
                )
            }
            is DrawState.Adjusting -> {
                // Move the handle, update region bounds
                if (state.regionIndex in regions.indices) {
                    val region = regions[state.regionIndex]
                    adjustRegionHandle(region.bounds, state.handleIndex, x, y)
                }
            }
            is DrawState.Idle -> {
                // No action
            }
        }
    }

    private fun handleTouchUp(x: Float, y: Float) {
        when (val state = drawState) {
            is DrawState.Drawing -> {
                tempRect?.let { rect ->
                    // Enforce minimum size
                    val w = rect.width()
                    val h = rect.height()
                    if (w >= MIN_REGION_SIZE && h >= MIN_REGION_SIZE) {
                        val newRegion = EditableRegion(
                            bounds = RectF(rect),
                            id = UUID.randomUUID().toString(),
                            autoRead = false
                        )
                        regions.add(newRegion)
                        selectedRegionIndex = regions.size - 1
                    }
                }
                tempRect = null
            }
            is DrawState.Adjusting -> {
                // Finalize region bounds (ensure minimum size)
                if (state.regionIndex in regions.indices) {
                    enforceMinimumSize(regions[state.regionIndex].bounds)
                }
            }
            is DrawState.Idle -> {
                // No action
            }
        }
        drawState = DrawState.Idle
    }

    /**
     * Adjust a region's bounds when a resize handle is dragged.
     * Handle indices: 0=TL, 1=TR, 2=BR, 3=BL, 4=TC, 5=RC, 6=BC, 7=LC
     */
    private fun adjustRegionHandle(bounds: RectF, handleIndex: Int, x: Float, y: Float) {
        when (handleIndex) {
            0 -> { bounds.left = x; bounds.top = y }       // top-left
            1 -> { bounds.right = x; bounds.top = y }      // top-right
            2 -> { bounds.right = x; bounds.bottom = y }   // bottom-right
            3 -> { bounds.left = x; bounds.bottom = y }    // bottom-left
            4 -> { bounds.top = y }                         // top-center
            5 -> { bounds.right = x }                       // right-center
            6 -> { bounds.bottom = y }                      // bottom-center
            7 -> { bounds.left = x }                        // left-center
        }
        // Normalize bounds if inverted
        if (bounds.left > bounds.right) {
            val tmp = bounds.left
            bounds.left = bounds.right
            bounds.right = tmp
        }
        if (bounds.top > bounds.bottom) {
            val tmp = bounds.top
            bounds.top = bounds.bottom
            bounds.bottom = tmp
        }
    }

    /**
     * Enforce minimum region size of MIN_REGION_SIZE pixels.
     */
    private fun enforceMinimumSize(bounds: RectF) {
        if (bounds.width() < MIN_REGION_SIZE) {
            bounds.right = bounds.left + MIN_REGION_SIZE
        }
        if (bounds.height() < MIN_REGION_SIZE) {
            bounds.bottom = bounds.top + MIN_REGION_SIZE
        }
    }

    /**
     * Check if a touch hits a control button and execute the action.
     * Returns true if a button was hit.
     */
    private fun handleControlButtonTap(x: Float, y: Float): Boolean {
        when {
            confirmButtonRect.contains(x, y) -> {
                // Convert all EditableRegion to CaptureRegion and confirm
                val viewWidth = width.toFloat().coerceAtLeast(1f)
                val viewHeight = height.toFloat().coerceAtLeast(1f)

                val captureRegions = regions.map { editableRegion ->
                    val left = editableRegion.bounds.left.coerceIn(0f, viewWidth)
                    val top = editableRegion.bounds.top.coerceIn(0f, viewHeight)
                    val right = editableRegion.bounds.right.coerceIn(left + 1f, viewWidth)
                    val bottom = editableRegion.bounds.bottom.coerceIn(top + 1f, viewHeight)

                    val nx = (left / viewWidth).coerceIn(0f, 1f)
                    val ny = (top / viewHeight).coerceIn(0f, 1f)
                    val nw = ((right - left) / viewWidth).coerceIn(0f, 1f)
                    val nh = ((bottom - top) / viewHeight).coerceIn(0f, 1f)

                    CaptureRegion(
                        x = left.toInt(),
                        y = top.toInt(),
                        width = (right - left).toInt().coerceAtLeast(1),
                        height = (bottom - top).toInt().coerceAtLeast(1),
                        id = editableRegion.id,
                        autoRead = editableRegion.autoRead,
                        normalizedX = nx,
                        normalizedY = ny,
                        normalizedWidth = nw,
                        normalizedHeight = nh
                    )
                }
                onRegionsConfirmed?.invoke(captureRegions)
                return true
            }
            cancelButtonRect.contains(x, y) -> {
                onEditCancelled?.invoke()
                return true
            }
            addButtonRect.contains(x, y) -> {
                // Reset draw state to allow drawing another region
                drawState = DrawState.Idle
                selectedRegionIndex = -1
                return true
            }
            clearAllButtonRect.contains(x, y) && regions.isNotEmpty() -> {
                regions.clear()
                selectedRegionIndex = -1
                drawState = DrawState.Idle
                tempRect = null
                return true
            }
            autoReadToggleRect.contains(x, y) && selectedRegionIndex in regions.indices -> {
                // Toggle auto-read on selected region
                val region = regions[selectedRegionIndex]
                region.autoRead = !region.autoRead
                return true
            }
            deleteButtonRect.contains(x, y) && selectedRegionIndex in regions.indices -> {
                // Remove selected region
                regions.removeAt(selectedRegionIndex)
                selectedRegionIndex = if (regions.isNotEmpty()) {
                    (selectedRegionIndex - 1).coerceAtLeast(0)
                } else {
                    -1
                }
                return true
            }
        }
        return false
    }

    companion object {
        private const val TEAL_COLOR = 0xFF00BCD4.toInt()
        private const val TEAL_LIGHT_COLOR = 0xFF4DD0E1.toInt()
        private const val BORDER_STROKE_WIDTH = 4f
        private const val HANDLE_RADIUS = 10f
        private const val HANDLE_TOUCH_TARGET = 36f // 24dp touch target as pixels (approx)
        private const val MIN_REGION_SIZE = 50f
        private const val CONTROL_BUTTON_SIZE = 56f
        private const val CONTROL_BUTTON_MARGIN = 24f
    }
}
