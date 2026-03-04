package com.dstranslator.service

import android.graphics.Rect
import com.dstranslator.domain.model.CaptureRegion
import com.dstranslator.domain.model.OcrResult
import com.dstranslator.domain.model.OcrTextBlock
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for OverlayCoordinateMapper -- pure function mapping OCR bounding boxes
 * from preprocessed-image coordinates to screen coordinates.
 */
class OverlayCoordinateMapperTest {

    @Test
    fun `mapToScreenCoordinates with null captureRegion and no upscale returns bbox as-is`() {
        val block = OcrTextBlock(text = "test", boundingBox = Rect(10, 20, 110, 70))
        val result = OcrResult(
            textBlocks = listOf(block),
            captureRegion = null,
            preprocessedWidth = 1080,
            preprocessedHeight = 1920
        )

        val mapped = OverlayCoordinateMapper.mapToScreenCoordinates(block, result)

        assertEquals(Rect(10, 20, 110, 70), mapped)
    }

    @Test
    fun `mapToScreenCoordinates with captureRegion offsets bbox by region xy and scales`() {
        // Region at (200, 300) is 400x200, preprocessed bitmap is same size (no upscale)
        val block = OcrTextBlock(text = "test", boundingBox = Rect(50, 30, 150, 80))
        val region = CaptureRegion(x = 200, y = 300, width = 400, height = 200)
        val result = OcrResult(
            textBlocks = listOf(block),
            captureRegion = region,
            preprocessedWidth = 400,
            preprocessedHeight = 200
        )

        val mapped = OverlayCoordinateMapper.mapToScreenCoordinates(block, result)

        // scaleX = 400/400 = 1.0, scaleY = 200/200 = 1.0
        // screenLeft = 200 + (50 * 1.0) = 250
        // screenTop = 300 + (30 * 1.0) = 330
        // screenRight = 200 + (150 * 1.0) = 350
        // screenBottom = 300 + (80 * 1.0) = 380
        assertEquals(Rect(250, 330, 350, 380), mapped)
    }

    @Test
    fun `mapToScreenCoordinates with 2x upscale halves coordinates before offset`() {
        // Region at (100, 50) is 200x100; preprocessed bitmap is 2x upscaled = 400x200
        val block = OcrTextBlock(text = "test", boundingBox = Rect(100, 60, 300, 160))
        val region = CaptureRegion(x = 100, y = 50, width = 200, height = 100)
        val result = OcrResult(
            textBlocks = listOf(block),
            captureRegion = region,
            preprocessedWidth = 400,  // 2x upscale
            preprocessedHeight = 200  // 2x upscale
        )

        val mapped = OverlayCoordinateMapper.mapToScreenCoordinates(block, result)

        // scaleX = 200/400 = 0.5, scaleY = 100/200 = 0.5
        // screenLeft = 100 + (100 * 0.5) = 150
        // screenTop = 50 + (60 * 0.5) = 80
        // screenRight = 100 + (300 * 0.5) = 250
        // screenBottom = 50 + (160 * 0.5) = 130
        assertEquals(Rect(150, 80, 250, 130), mapped)
    }

    @Test
    fun `mapToScreenCoordinates with null boundingBox returns empty Rect`() {
        val block = OcrTextBlock(text = "test", boundingBox = null)
        val result = OcrResult(
            textBlocks = listOf(block),
            captureRegion = null,
            preprocessedWidth = 1080,
            preprocessedHeight = 1920
        )

        val mapped = OverlayCoordinateMapper.mapToScreenCoordinates(block, result)

        assertEquals(Rect(0, 0, 0, 0), mapped)
    }
}
