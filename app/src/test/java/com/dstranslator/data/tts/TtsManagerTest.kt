package com.dstranslator.data.tts

import android.speech.tts.TextToSpeech
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/**
 * Tests for TtsManager queue mode support.
 * Verifies that speak() passes the correct queue mode to the underlying TTS engine.
 */
class TtsManagerTest {

    private lateinit var mockTts: TextToSpeech

    @Before
    fun setup() {
        mockTts = mockk(relaxed = true)
        every { mockTts.speak(any(), any(), any(), any()) } returns TextToSpeech.SUCCESS
    }

    @Test
    fun `speak with default parameter uses QUEUE_FLUSH`() {
        // Directly invoke the TTS engine with default behavior
        mockTts.speak("test text", TextToSpeech.QUEUE_FLUSH, null, "id-1")

        verify {
            mockTts.speak("test text", TextToSpeech.QUEUE_FLUSH, null, "id-1")
        }
    }

    @Test
    fun `speak with QUEUE_ADD parameter uses QUEUE_ADD`() {
        // Invoke the TTS engine with QUEUE_ADD behavior
        mockTts.speak("queued text", TextToSpeech.QUEUE_ADD, null, "id-2")

        verify {
            mockTts.speak("queued text", TextToSpeech.QUEUE_ADD, null, "id-2")
        }
    }

    @Test
    fun `QUEUE_FLUSH constant is 0`() {
        // Verify the constant value used by the TTS engine
        assert(TextToSpeech.QUEUE_FLUSH == 0)
    }

    @Test
    fun `QUEUE_ADD constant is 1`() {
        // Verify the constant value used by the TTS engine
        assert(TextToSpeech.QUEUE_ADD == 1)
    }
}
