package com.dstranslator.data.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.dstranslator.domain.model.CaptureRegion
import javax.inject.Inject
import javax.inject.Singleton

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
     * 2. Upscale small images (height < 100px) by 2x for ML Kit character detection
     * 3. Convert to grayscale for better OCR contrast
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

        // Step 2: Upscale if too small (ML Kit needs characters >= 16px)
        if (result.height < MIN_HEIGHT_FOR_OCR) {
            val scaledWidth = result.width * UPSCALE_FACTOR
            val scaledHeight = result.height * UPSCALE_FACTOR
            val scaled = Bitmap.createScaledBitmap(result, scaledWidth, scaledHeight, true)
            if (scaled !== result && result !== bitmap) {
                result.recycle()
            }
            result = scaled
        }

        // Step 3: Convert to grayscale for better OCR contrast
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
        /** Minimum bitmap height for acceptable OCR accuracy */
        private const val MIN_HEIGHT_FOR_OCR = 100

        /** Scale factor for upscaling small bitmaps */
        private const val UPSCALE_FACTOR = 2
    }
}
