package com.dstranslator.service

import android.speech.tts.TextToSpeech
import com.dstranslator.domain.model.CaptureRegion

/**
 * Pure helper for auto-read decision logic.
 * Extracted from CaptureService for testability.
 *
 * Determines whether TTS should fire for a given capture region based on:
 * - Global auto-read enabled state
 * - Per-region autoRead flag
 * - Text change detection (current vs previous)
 * - Blank text filtering
 */
object AutoReadHelper {

    /**
     * Determine whether auto-read should trigger TTS for the given region.
     *
     * @param region The capture region with its autoRead flag
     * @param currentText Current OCR text for this region
     * @param previousText Previous OCR text for this region (null if first detection)
     * @param autoReadEnabled Global auto-read enabled state
     * @return true if TTS should be triggered
     */
    fun shouldAutoRead(
        region: CaptureRegion,
        currentText: String,
        previousText: String?,
        autoReadEnabled: Boolean
    ): Boolean {
        return autoReadEnabled &&
                region.autoRead &&
                currentText.isNotBlank() &&
                currentText != (previousText ?: "")
    }

    /**
     * Get the TextToSpeech queue mode based on flush mode setting.
     *
     * @param flushMode true = interrupt current speech, false = queue after current
     * @return TextToSpeech.QUEUE_FLUSH or TextToSpeech.QUEUE_ADD
     */
    fun getQueueMode(flushMode: Boolean): Int {
        return if (flushMode) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
    }
}
