package com.dstranslator.service

import android.speech.tts.TextToSpeech
import com.dstranslator.domain.model.CaptureRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for auto-read text change detection logic.
 * Tests the pure function AutoReadHelper.shouldAutoRead() which determines
 * whether TTS should fire for a given region based on text changes.
 */
class AutoReadTest {

    // --- shouldAutoRead tests ---

    @Test
    fun `auto-read triggers TTS when region text changes`() {
        val region = CaptureRegion(0, 0, 100, 100, id = "region1", autoRead = true)
        val result = AutoReadHelper.shouldAutoRead(
            region = region,
            currentText = "New dialog text",
            previousText = "Old dialog text",
            autoReadEnabled = true
        )
        assertTrue("Should trigger TTS when text changes", result)
    }

    @Test
    fun `auto-read does NOT trigger TTS when region text is unchanged`() {
        val region = CaptureRegion(0, 0, 100, 100, id = "region1", autoRead = true)
        val result = AutoReadHelper.shouldAutoRead(
            region = region,
            currentText = "Same text",
            previousText = "Same text",
            autoReadEnabled = true
        )
        assertFalse("Should NOT trigger TTS when text unchanged", result)
    }

    @Test
    fun `auto-read does NOT trigger for regions where autoRead is false`() {
        val region = CaptureRegion(0, 0, 100, 100, id = "region1", autoRead = false)
        val result = AutoReadHelper.shouldAutoRead(
            region = region,
            currentText = "New text",
            previousText = "Old text",
            autoReadEnabled = true
        )
        assertFalse("Should NOT trigger TTS when region autoRead is false", result)
    }

    @Test
    fun `auto-read uses QUEUE_FLUSH when flush mode is true`() {
        val queueMode = AutoReadHelper.getQueueMode(flushMode = true)
        assertEquals(
            "Should use QUEUE_FLUSH when flush mode is true",
            TextToSpeech.QUEUE_FLUSH,
            queueMode
        )
    }

    @Test
    fun `auto-read uses QUEUE_ADD when flush mode is false`() {
        val queueMode = AutoReadHelper.getQueueMode(flushMode = false)
        assertEquals(
            "Should use QUEUE_ADD when flush mode is false",
            TextToSpeech.QUEUE_ADD,
            queueMode
        )
    }

    @Test
    fun `per-region tracking - region A changes while region B stays same`() {
        val regionA = CaptureRegion(0, 0, 100, 100, id = "regionA", autoRead = true)
        val regionB = CaptureRegion(100, 0, 100, 100, id = "regionB", autoRead = true)

        val previousTexts = mutableMapOf("regionA" to "Old A text", "regionB" to "B text")

        // Region A has new text
        val shouldReadA = AutoReadHelper.shouldAutoRead(
            region = regionA,
            currentText = "New A text",
            previousText = previousTexts["regionA"],
            autoReadEnabled = true
        )

        // Region B has same text
        val shouldReadB = AutoReadHelper.shouldAutoRead(
            region = regionB,
            currentText = "B text",
            previousText = previousTexts["regionB"],
            autoReadEnabled = true
        )

        assertTrue("Region A should trigger TTS (text changed)", shouldReadA)
        assertFalse("Region B should NOT trigger TTS (text unchanged)", shouldReadB)
    }

    @Test
    fun `blank OCR text does not trigger auto-read`() {
        val region = CaptureRegion(0, 0, 100, 100, id = "region1", autoRead = true)

        val resultEmpty = AutoReadHelper.shouldAutoRead(
            region = region,
            currentText = "",
            previousText = "Old text",
            autoReadEnabled = true
        )
        assertFalse("Empty text should NOT trigger TTS", resultEmpty)

        val resultBlank = AutoReadHelper.shouldAutoRead(
            region = region,
            currentText = "   ",
            previousText = "Old text",
            autoReadEnabled = true
        )
        assertFalse("Blank/whitespace text should NOT trigger TTS", resultBlank)
    }

    @Test
    fun `auto-read does not trigger when global auto-read is disabled`() {
        val region = CaptureRegion(0, 0, 100, 100, id = "region1", autoRead = true)
        val result = AutoReadHelper.shouldAutoRead(
            region = region,
            currentText = "New text",
            previousText = "Old text",
            autoReadEnabled = false
        )
        assertFalse("Should NOT trigger when global autoRead is disabled", result)
    }

    @Test
    fun `auto-read triggers when previousText is null (first detection)`() {
        val region = CaptureRegion(0, 0, 100, 100, id = "region1", autoRead = true)
        val result = AutoReadHelper.shouldAutoRead(
            region = region,
            currentText = "First text detected",
            previousText = null,
            autoReadEnabled = true
        )
        assertTrue("Should trigger TTS on first text detection (null previous)", result)
    }
}
