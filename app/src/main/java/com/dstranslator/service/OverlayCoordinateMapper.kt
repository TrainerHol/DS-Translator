package com.dstranslator.service

import android.graphics.Rect
import com.dstranslator.domain.model.OcrResult
import com.dstranslator.domain.model.OcrTextBlock

/**
 * Screen-coordinate bounding box returned by [OverlayCoordinateMapper].
 * Pure Kotlin data class -- no Android framework dependency -- enabling unit testing.
 */
data class ScreenBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    /** Convert to an Android [Rect] for use with WindowManager layouts. */
    fun toRect(): Rect = Rect(left, top, right, bottom)

    companion object {
        val EMPTY = ScreenBounds(0, 0, 0, 0)
    }
}

/**
 * Pure function mapping OCR bounding boxes from preprocessed-image coordinates
 * to screen coordinates.
 *
 * The OCR pipeline (OcrPreprocessor) crops the screenshot to the CaptureRegion,
 * then optionally upscales 2x if height < 100px, before passing to ML Kit.
 * Bounding boxes returned by ML Kit are therefore in preprocessed-image space.
 *
 * This mapper reverses that transform:
 * 1. Scale bounding box by (regionSize / preprocessedSize) to undo any upscale.
 * 2. Offset by (region.x, region.y) to convert from region-local to screen coordinates.
 */
object OverlayCoordinateMapper {

    /**
     * Map a single OcrTextBlock's bounding box to screen coordinates.
     *
     * @param block The OCR text block with a bounding box in preprocessed-image space.
     * @param result The OcrResult containing coordinate metadata (capture region, preprocessed dimensions).
     * @return A [ScreenBounds] in screen coordinates, or [ScreenBounds.EMPTY] if the bounding box is null.
     */
    fun mapToScreenCoordinates(block: OcrTextBlock, result: OcrResult): ScreenBounds {
        val bbox = block.boundingBox ?: return ScreenBounds.EMPTY

        val bboxLeft = bbox.left
        val bboxTop = bbox.top
        val bboxRight = bbox.right
        val bboxBottom = bbox.bottom

        val region = result.captureRegion
            ?: return ScreenBounds(bboxLeft, bboxTop, bboxRight, bboxBottom)

        val scaleX = region.width.toFloat() / result.preprocessedWidth
        val scaleY = region.height.toFloat() / result.preprocessedHeight

        return ScreenBounds(
            left = region.x + (bboxLeft * scaleX).toInt(),
            top = region.y + (bboxTop * scaleY).toInt(),
            right = region.x + (bboxRight * scaleX).toInt(),
            bottom = region.y + (bboxBottom * scaleY).toInt()
        )
    }

    /**
     * Overload accepting raw bounding box coordinates directly, for use when
     * the caller has already extracted coordinates from the Rect.
     * Also useful for unit testing without Android framework dependency.
     */
    fun mapToScreenCoordinates(
        bboxLeft: Int,
        bboxTop: Int,
        bboxRight: Int,
        bboxBottom: Int,
        result: OcrResult
    ): ScreenBounds {
        val region = result.captureRegion
            ?: return ScreenBounds(bboxLeft, bboxTop, bboxRight, bboxBottom)

        val scaleX = region.width.toFloat() / result.preprocessedWidth
        val scaleY = region.height.toFloat() / result.preprocessedHeight

        return ScreenBounds(
            left = region.x + (bboxLeft * scaleX).toInt(),
            top = region.y + (bboxTop * scaleY).toInt(),
            right = region.x + (bboxRight * scaleX).toInt(),
            bottom = region.y + (bboxBottom * scaleY).toInt()
        )
    }
}
