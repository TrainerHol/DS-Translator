package com.dstranslator.data.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.dstranslator.domain.model.CaptureRegion
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Stateless bitmap preprocessing pipeline for OCR.
 * Applies crop, upscale, and grayscale transformations to improve OCR accuracy.
 */
@Singleton
class OcrPreprocessor @Inject constructor() {

    /**
     * Preprocess a captured bitmap for OCR.
     *
     * Pipeline:
     * 1. Crop to the specified region (if provided), clamping to bitmap bounds
     * 2. Upscale small images to target height for ML Kit character detection.
     *    Uses nearest-neighbor interpolation (filter=false) to preserve hard edges
     *    in pixel fonts common in games like DS/retro titles.
     * 3. Enhance contrast to sharpen text against backgrounds
     * 4. Convert to grayscale for better OCR contrast
     *
     * @param bitmap The raw captured bitmap
     * @param region Optional capture region to crop to
     * @return Preprocessed bitmap ready for OCR
     */
    fun preprocess(bitmap: Bitmap, region: CaptureRegion?): Bitmap {
        var result = bitmap

        // Step 1: Crop to region if specified
        if (region != null) {
            result = cropToRegion(result, region)
        }

        // Step 2: Upscale if too small (ML Kit needs characters >= 16px, target ~200px height)
        // Uses nearest-neighbor scaling (filter=false) to preserve sharp pixel font edges.
        // Bilinear filtering blurs pixel fonts, making OCR significantly worse.
        if (result.height < TARGET_HEIGHT_FOR_OCR) {
            val scaleFactor = (TARGET_HEIGHT_FOR_OCR.toFloat() / result.height).coerceAtLeast(2f)
            val scaledWidth = (result.width * scaleFactor).roundToInt()
            val scaledHeight = (result.height * scaleFactor).roundToInt()
            val scaled = Bitmap.createScaledBitmap(result, scaledWidth, scaledHeight, false)
            if (scaled !== result && result !== bitmap) {
                result.recycle()
            }
            result = scaled
        }

        // Step 3: Enhance contrast for better text/background separation
        val contrasted = enhanceContrast(result)
        if (result !== bitmap) {
            result.recycle()
        }
        result = contrasted

        // Step 4: Convert to grayscale for better OCR contrast
        val grayscale = toGrayscale(result)
        if (result !== bitmap) {
            result.recycle()
        }

        return grayscale
    }

    /**
     * Crop bitmap to the specified region, clamping coordinates to bitmap bounds
     * to avoid IndexOutOfBoundsException.
     */
    private fun cropToRegion(bitmap: Bitmap, region: CaptureRegion): Bitmap {
        // Clamp region to bitmap bounds
        val x = region.x.coerceIn(0, bitmap.width - 1)
        val y = region.y.coerceIn(0, bitmap.height - 1)
        val width = region.width.coerceIn(1, bitmap.width - x)
        val height = region.height.coerceIn(1, bitmap.height - y)

        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    /**
     * Enhance contrast using a ColorMatrix to sharpen text against backgrounds.
     * Increases contrast by 1.5x with slight brightness boost, improving OCR accuracy
     * for low-contrast pixel font text common in game screenshots.
     */
    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                CONTRAST_SCALE, 0f, 0f, 0f, CONTRAST_TRANSLATE,
                0f, CONTRAST_SCALE, 0f, 0f, CONTRAST_TRANSLATE,
                0f, 0f, CONTRAST_SCALE, 0f, CONTRAST_TRANSLATE,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(contrastMatrix)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }

    /**
     * Convert a bitmap to grayscale using a ColorMatrix saturation filter.
     */
    private fun toGrayscale(bitmap: Bitmap): Bitmap {
        val grayscale = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(grayscale)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                ColorMatrix().apply { setSaturation(0f) }
            )
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayscale
    }

    companion object {
        /** Target bitmap height for OCR - upscale anything below this */
        private const val TARGET_HEIGHT_FOR_OCR = 200

        /** Contrast enhancement scale factor (1.0 = no change, >1.0 = more contrast) */
        private const val CONTRAST_SCALE = 1.5f

        /**
         * Contrast translation to keep midtones centered after scaling.
         * Formula: 128 * (1 - scale) = 128 * (1 - 1.5) = -64
         */
        private const val CONTRAST_TRANSLATE = -64f
    }
}
