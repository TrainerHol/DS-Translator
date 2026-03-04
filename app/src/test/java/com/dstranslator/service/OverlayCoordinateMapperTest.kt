package com.dstranslator.service

import com.dstranslator.domain.model.CaptureRegion
import com.dstranslator.domain.model.OcrResult
import com.dstranslator.domain.model.OcrTextBlock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for OverlayCoordinateMapper -- pure function mapping OCR bounding boxes
 * from preprocessed-image coordinates to screen coordinates.
 *
 * Uses the raw-coordinate overload to avoid android.graphics.Rect dependency in unit tests.
 */
class OverlayCoordinateMapperTest {

    @Test
    fun `mapToScreenCoordinates with null captureRegion and no upscale returns bbox as-is`() {
        val result = OcrResult(
            textBlocks = emptyList(),
            captureRegion = null,
            preprocessedWidth = 1080,
            preprocessedHeight = 1920
        )

        val mapped = OverlayCoordinateMapper.mapToScreenCoordinates(
            bboxLeft = 10, bboxTop = 20, bboxRight = 110, bboxBottom = 70,
            result = result
        )

        assertEquals(ScreenBounds(10, 20, 110, 70), mapped)
    }

    @Test
    fun `mapToScreenCoordinates with captureRegion offsets bbox by region xy and scales`() {
        // Region at (200, 300) is 400x200, preprocessed bitmap is same size (no upscale)
        val region = CaptureRegion(x = 200, y = 300, width = 400, height = 200)
        val result = OcrResult(
            textBlocks = emptyList(),
            captureRegion = region,
            preprocessedWidth = 400,
            preprocessedHeight = 200
        )

        val mapped = OverlayCoordinateMapper.mapToScreenCoordinates(
            bboxLeft = 50, bboxTop = 30, bboxRight = 150, bboxBottom = 80,
            result = result
        )

        // scaleX = 400/400 = 1.0, scaleY = 200/200 = 1.0
        // screenLeft = 200 + (50 * 1.0) = 250
        // screenTop = 300 + (30 * 1.0) = 330
        // screenRight = 200 + (150 * 1.0) = 350
        // screenBottom = 300 + (80 * 1.0) = 380
        assertEquals(ScreenBounds(250, 330, 350, 380), mapped)
    }

    @Test
    fun `mapToScreenCoordinates with 2x upscale halves coordinates before offset`() {
        // Region at (100, 50) is 200x100; preprocessed bitmap is 2x upscaled = 400x200
        val region = CaptureRegion(x = 100, y = 50, width = 200, height = 100)
        val result = OcrResult(
            textBlocks = emptyList(),
            captureRegion = region,
            preprocessedWidth = 400,  // 2x upscale
            preprocessedHeight = 200  // 2x upscale
        )

        val mapped = OverlayCoordinateMapper.mapToScreenCoordinates(
            bboxLeft = 100, bboxTop = 60, bboxRight = 300, bboxBottom = 160,
            result = result
        )

        // scaleX = 200/400 = 0.5, scaleY = 100/200 = 0.5
        // screenLeft = 100 + (100 * 0.5) = 150
        // screenTop = 50 + (60 * 0.5) = 80
        // screenRight = 100 + (300 * 0.5) = 250
        // screenBottom = 50 + (160 * 0.5) = 130
        assertEquals(ScreenBounds(150, 80, 250, 130), mapped)
    }

    @Test
    fun `mapToScreenCoordinates with null boundingBox returns empty ScreenBounds`() {
        val block = OcrTextBlock(text = "test", boundingBox = null)
        val result = OcrResult(
            textBlocks = listOf(block),
            captureRegion = null,
            preprocessedWidth = 1080,
            preprocessedHeight = 1920
        )

        val mapped = OverlayCoordinateMapper.mapToScreenCoordinates(block, result)

        assertEquals(ScreenBounds.EMPTY, mapped)
    }
}
