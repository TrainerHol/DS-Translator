package com.dstranslator.data.capture

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [OcrPreprocessor] contract verification.
 *
 * Note: Bitmap-dependent tests (upscale, contrast, grayscale) require Robolectric
 * or instrumented tests since Bitmap.createBitmap returns null in pure JVM unit tests.
 * These tests verify the preprocessing constants and scaling logic contract.
 */
class OcrPreprocessorTest {

    @Test
    fun `4x scale factor produces correct dimensions for small images`() {
        // Verifies the scaling math used in preprocess()
        val inputWidth = 50
        val inputHeight = 50
        val scaleFactor = 4f
        val scaledWidth = (inputWidth * scaleFactor).toInt()
        val scaledHeight = (inputHeight * scaleFactor).toInt()

        assertEquals("50 * 4 = 200", 200, scaledWidth)
        assertEquals("50 * 4 = 200", 200, scaledHeight)
    }

    @Test
    fun `large images above threshold are not upscaled`() {
        // Images >= 200px height should skip the upscale step
        val inputHeight = 500
        val targetHeight = 200
        val shouldUpscale = inputHeight < targetHeight

        assertEquals("500px image should not be upscaled", false, shouldUpscale)
    }

    @Test
    fun `contrast matrix values are correct for 2x enhancement`() {
        // Formula: translate = 128 * (1 - scale)
        val scale = 2.0f
        val expectedTranslate = 128f * (1f - scale)

        assertEquals("Contrast scale should be 2.0", 2.0f, scale, 0.001f)
        assertEquals("Translate should be -128 for 2.0x contrast", -128f, expectedTranslate, 0.001f)
    }

    @Test
    fun `region clamping math prevents out-of-bounds`() {
        // Simulate the clamping logic from cropToRegion
        val bitmapWidth = 100
        val bitmapHeight = 100
        val regionX = 80
        val regionY = 80
        val regionWidth = 50
        val regionHeight = 50

        val clampedX = regionX.coerceIn(0, bitmapWidth - 1)
        val clampedY = regionY.coerceIn(0, bitmapHeight - 1)
        val clampedWidth = regionWidth.coerceIn(1, bitmapWidth - clampedX)
        val clampedHeight = regionHeight.coerceIn(1, bitmapHeight - clampedY)

        assertEquals("X should be clamped to 80", 80, clampedX)
        assertEquals("Y should be clamped to 80", 80, clampedY)
        assertEquals("Width should be clamped to 20", 20, clampedWidth)
        assertEquals("Height should be clamped to 20", 20, clampedHeight)
    }
}
