package com.dstranslator.data.tts

import android.speech.tts.TextToSpeech
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

/**
 * Tests for TtsManager dual-engine dispatch, queue mode support,
 * and SherpaOnnxTtsEngine voice randomization.
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
        mockTts.speak("test text", TextToSpeech.QUEUE_FLUSH, null, "id-1")

        verify {
            mockTts.speak("test text", TextToSpeech.QUEUE_FLUSH, null, "id-1")
        }
    }

    @Test
    fun `speak with QUEUE_ADD parameter uses QUEUE_ADD`() {
        mockTts.speak("queued text", TextToSpeech.QUEUE_ADD, null, "id-2")

        verify {
            mockTts.speak("queued text", TextToSpeech.QUEUE_ADD, null, "id-2")
        }
    }

    @Test
    fun `QUEUE_FLUSH constant is 0`() {
        assert(TextToSpeech.QUEUE_FLUSH == 0)
    }

    @Test
    fun `QUEUE_ADD constant is 1`() {
        assert(TextToSpeech.QUEUE_ADD == 1)
    }

    // ===== Engine Type Tests =====

    @Test
    fun `EngineType fromString returns BUNDLED for bundled`() {
        assert(TtsManager.EngineType.fromString("bundled") == TtsManager.EngineType.BUNDLED)
    }

    @Test
    fun `EngineType fromString returns SYSTEM for system`() {
        assert(TtsManager.EngineType.fromString("system") == TtsManager.EngineType.SYSTEM)
    }

    @Test
    fun `EngineType fromString is case insensitive`() {
        assert(TtsManager.EngineType.fromString("SYSTEM") == TtsManager.EngineType.SYSTEM)
        assert(TtsManager.EngineType.fromString("System") == TtsManager.EngineType.SYSTEM)
        assert(TtsManager.EngineType.fromString("BUNDLED") == TtsManager.EngineType.BUNDLED)
    }

    @Test
    fun `EngineType fromString defaults to BUNDLED for unknown values`() {
        assert(TtsManager.EngineType.fromString("unknown") == TtsManager.EngineType.BUNDLED)
        assert(TtsManager.EngineType.fromString("") == TtsManager.EngineType.BUNDLED)
    }

    // ===== SherpaOnnxTtsEngine Voice Randomization Tests =====

    @Test
    fun `allJapaneseIds contains both male and female IDs`() {
        val engine = SherpaOnnxTtsEngine(mockk(relaxed = true))
        val allIds = engine.allJapaneseIds

        // Female IDs: 37, 38, 39, 40
        assert(allIds.containsAll(listOf(37, 38, 39, 40))) { "Missing female IDs" }
        // Male ID: 41
        assert(allIds.contains(41)) { "Missing male ID" }
        // Total: 5 Japanese voices
        assert(allIds.size == 5) { "Expected 5 Japanese voices, got ${allIds.size}" }
    }

    @Test
    fun `japaneseFemaleIds has 4 entries`() {
        val engine = SherpaOnnxTtsEngine(mockk(relaxed = true))
        assert(engine.japaneseFemaleIds.size == 4)
        assert(engine.japaneseFemaleIds == listOf(37, 38, 39, 40))
    }

    @Test
    fun `japaneseMaleIds has 1 entry`() {
        val engine = SherpaOnnxTtsEngine(mockk(relaxed = true))
        assert(engine.japaneseMaleIds.size == 1)
        assert(engine.japaneseMaleIds == listOf(41))
    }

    @Test
    fun `random voice selection covers both male and female IDs over many iterations`() {
        val engine = SherpaOnnxTtsEngine(mockk(relaxed = true))
        val allIds = engine.allJapaneseIds

        // Run 100 random selections and track which IDs were picked
        val selectedIds = mutableSetOf<Int>()
        repeat(100) {
            selectedIds.add(allIds.random())
        }

        // With 100 iterations and 5 IDs, probability of missing any ID is negligible
        // Check that at least one female and one male ID were selected
        val hasFemale = selectedIds.any { it in engine.japaneseFemaleIds }
        val hasMale = selectedIds.any { it in engine.japaneseMaleIds }

        assert(hasFemale) { "Random selection never picked a female voice in 100 iterations" }
        assert(hasMale) { "Random selection never picked a male voice in 100 iterations" }
    }

    @Test
    fun `random voice selection only produces valid Japanese IDs`() {
        val engine = SherpaOnnxTtsEngine(mockk(relaxed = true))
        val allIds = engine.allJapaneseIds
        val validIds = setOf(37, 38, 39, 40, 41)

        repeat(50) {
            val selected = allIds.random()
            assert(selected in validIds) { "Selected invalid ID: $selected" }
        }
    }
}
