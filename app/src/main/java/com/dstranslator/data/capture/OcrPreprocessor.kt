package com.dstranslator.data.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.dstranslator.domain.model.CaptureRegion
import com.dstranslator.domain.model.resolveToPixelRect
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Stateless bitmap preprocessing pipeline for OCR.
 * Applies crop, upscale, and grayscale transformations to improve OCR accuracy.
 * Uses 4x bicubic-quality upscaling and 2.0x contrast enhancement for pixel-font
 * game text common in DS/retro titles.
 */
@Singleton
class OcrPreprocessor @Inject constructor() {

    /**
     * Preprocess a captured bitmap for OCR.
     *
     * Pipeline:
     * 1. Crop to the specified region (if provided), clamping to bitmap bounds
     * 2. Upscale small images by fixed 4x factor with bicubic-quality interpolation
     *    (filter=true) for improved OCR accuracy on pixel-font game text.
     * 3. Enhance contrast (2.0x) to sharpen text against backgrounds
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

        // Step 2: Upscale by fixed 4x if too small for ML Kit character detection.
        // Uses bicubic-quality interpolation (filter=true) for smoother upscaling.
        if (result.height < TARGET_HEIGHT_FOR_OCR) {
            val scaleFactor = 4f
            val scaledWidth = (result.width * scaleFactor).roundToInt()
            val scaledHeight = (result.height * scaleFactor).roundToInt()
            val scaled = Bitmap.createScaledBitmap(result, scaledWidth, scaledHeight, true)
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
        val rect = region.resolveToPixelRect(bitmap.width, bitmap.height)
        return Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.width, rect.height)
    }

    /**
     * Enhance contrast using a ColorMatrix to sharpen text against backgrounds.
     * Increases contrast by 2.0x, improving OCR accuracy for low-contrast pixel
     * font text common in game screenshots.
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
        private const val CONTRAST_SCALE = 2.0f

        /**
         * Contrast translation to keep midtones centered after scaling.
         * Formula: 128 * (1 - scale) = 128 * (1 - 2.0) = -128
         */
        private const val CONTRAST_TRANSLATE = -128f
    }
}
