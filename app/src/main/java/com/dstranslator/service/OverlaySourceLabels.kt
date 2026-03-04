package com.dstranslator.service

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.dstranslator.domain.model.OcrResult
import com.dstranslator.domain.model.OcrTextBlock
import com.dstranslator.domain.model.TranslationEntry

/**
 * Manages Canvas-drawn translation labels positioned at OCR bounding box screen coordinates.
 *
 * Uses lightweight [SourceLabelView] (a custom View subclass, not ComposeView) since there
 * may be many small labels displayed simultaneously during overlay-on-source mode.
 *
 * Each label shows the English translation text at the screen position of the corresponding
 * OCR text block, with a semi-transparent dark background. Labels are auto-offset 4dp below
 * the OCR bounding box so the original Japanese text remains visible above.
 *
 * All labels use FLAG_SECURE to prevent OCR feedback loop. All addView/removeView calls
 * are wrapped in try-catch for safe cleanup.
 *
 * @param displayContext Context created for the target display
 * @param windowManager WindowManager for the target display
 * @param onLabelTap Callback when a label is tapped (receives the OCR block and matching translation)
 */
class OverlaySourceLabels(
    private val displayContext: Context,
    private val windowManager: WindowManager,
    private val onLabelTap: (OcrTextBlock, TranslationEntry) -> Unit
) {
    /** Tracked labels currently visible in WindowManager. */
    private val labels = mutableListOf<Pair<View, WindowManager.LayoutParams>>()

    /** Auto-offset in pixels below the OCR bounding box. */
    private val autoOffsetPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, AUTO_OFFSET_DP,
        displayContext.resources.displayMetrics
    ).toInt()

    /**
     * Display translation labels at the screen positions of OCR text blocks.
     *
     * Clears any existing labels first, then for each OCR text block:
     * 1. Maps the bounding box to screen coordinates via [OverlayCoordinateMapper]
     * 2. Finds the matching [TranslationEntry] by Japanese text
     * 3. Creates a [SourceLabelView] and adds it to WindowManager at the mapped position
     * 4. Applies a brief fade-in animation
     *
     * @param ocrResult The OCR result with text blocks and coordinate metadata
     * @param translations Current list of translation entries to match against
     */
    fun showLabels(ocrResult: OcrResult, translations: List<TranslationEntry>) {
        clearLabels()

        for (block in ocrResult.textBlocks) {
            val screenBounds = OverlayCoordinateMapper.mapToScreenCoordinates(block, ocrResult)
            if (screenBounds == ScreenBounds.EMPTY) continue

            // Find matching translation entry by Japanese text
            val entry = translations.find { it.japanese == block.text } ?: continue

            val labelView = SourceLabelView(displayContext).apply {
                translationText = entry.english
                setOnTapCallback { onLabelTap(block, entry) }
            }

            val labelWidth = (screenBounds.right - screenBounds.left).coerceAtLeast(MIN_LABEL_WIDTH_PX)
            val labelParams = WindowManager.LayoutParams(
                labelWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = screenBounds.left
                y = screenBounds.bottom + autoOffsetPx // Auto-offset below original text
                alpha = LABEL_ALPHA
            }

            try {
                windowManager.addView(labelView, labelParams)
                labels.add(labelView to labelParams)
            } catch (_: IllegalStateException) {
                // View already added
            }

            // Fade-in animation
            labelView.alpha = 0f
            ObjectAnimator.ofFloat(labelView, View.ALPHA, 0f, LABEL_ALPHA).apply {
                duration = FADE_IN_DURATION_MS
                start()
            }
        }
    }

    /**
     * Remove all labels from WindowManager.
     * Applies a brief fade-out before removal if labels are currently visible.
     */
    fun clearLabels() {
        for ((view, _) in labels) {
            // Quick fade-out then remove
            ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f).apply {
                duration = FADE_OUT_DURATION_MS
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        safeRemoveView(view)
                    }
                })
                start()
            }
        }
        labels.clear()
    }

    /**
     * Toggle touch interactivity on all labels.
     *
     * When not touchable (locked mode), FLAG_NOT_TOUCHABLE is added so all label
     * touches pass through to the game underneath.
     *
     * @param touchable true to make labels tappable, false to pass through
     */
    fun setTouchable(touchable: Boolean) {
        for ((view, lp) in labels) {
            if (touchable) {
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            } else {
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            }
            try {
                windowManager.updateViewLayout(view, lp)
            } catch (_: IllegalArgumentException) {
                // View not attached
            }
        }
    }

    /**
     * Safely remove a view from WindowManager, catching IllegalArgumentException
     * if the view was already removed.
     */
    private fun safeRemoveView(view: View) {
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // View already removed
        }
    }

    /**
     * Custom View that draws a semi-transparent dark background with white translation text.
     * Lightweight Canvas-based rendering (no Compose overhead).
     */
    private class SourceLabelView(context: Context) : View(context) {

        var translationText: String = ""
            set(value) {
                field = value
                requestLayout()
                invalidate()
            }

        private var onTapCallback: (() -> Unit)? = null

        fun setOnTapCallback(callback: () -> Unit) {
            onTapCallback = callback
        }

        private val bgPaint = Paint().apply {
            color = android.graphics.Color.argb(180, 30, 30, 30)
            style = Paint.Style.FILL
        }

        private val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            isAntiAlias = true
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP,
                context.resources.displayMetrics
            )
        }

        private val paddingHorizontal = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 6f, context.resources.displayMetrics
        )
        private val paddingVertical = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 3f, context.resources.displayMetrics
        )

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val textWidth = textPaint.measureText(translationText)
            val textHeight = textPaint.fontMetrics.let { it.descent - it.ascent }
            val w = (textWidth + paddingHorizontal * 2).toInt()
            val h = (textHeight + paddingVertical * 2).toInt()
            setMeasuredDimension(
                resolveSize(w, widthMeasureSpec),
                resolveSize(h, heightMeasureSpec)
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Draw background
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Draw text
            val textY = paddingVertical - textPaint.fontMetrics.ascent
            canvas.drawText(translationText, paddingHorizontal, textY, textPaint)
        }

        @Suppress("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_UP) {
                onTapCallback?.invoke()
                return true
            }
            return event.action == MotionEvent.ACTION_DOWN
        }

        companion object {
            private const val TEXT_SIZE_SP = 12f
        }
    }

    companion object {
        /** Minimum label width in pixels. */
        private const val MIN_LABEL_WIDTH_PX = 100

        /** Auto-offset below OCR bounding box so original Japanese text is visible above. */
        private const val AUTO_OFFSET_DP = 4f

        /** Label alpha value -- Android 12+ compliant for passthrough. */
        private const val LABEL_ALPHA = 0.8f

        /** Fade-in animation duration per label. */
        private const val FADE_IN_DURATION_MS = 200L

        /** Fade-out animation duration on clear. */
        private const val FADE_OUT_DURATION_MS = 150L
    }
}
