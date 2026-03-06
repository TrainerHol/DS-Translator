package com.dstranslator.ui.vocabulary

import com.dstranslator.data.dictionary.JMdictRepository
import com.dstranslator.data.tts.TtsManager
import com.dstranslator.domain.model.DictionaryResult
import com.dstranslator.domain.model.SegmentedWord
import com.dstranslator.domain.model.TranslationEntry
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VocabularyViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var jmdictRepository: JMdictRepository
    private lateinit var ttsManager: TtsManager
    private lateinit var viewModel: VocabularyViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        jmdictRepository = mockk(relaxed = true)
        ttsManager = mockk(relaxed = true)

        coEvery { jmdictRepository.getJlptLevel(any()) } returns null
        coEvery { jmdictRepository.lookupWord(any()) } returns emptyList()

        viewModel = VocabularyViewModel(jmdictRepository, ttsManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `deduplicates by dictionaryForm keeping first occurrence`() = runTest {
        val word = SegmentedWord("食べた", "タベタ", "食べる", listOf("verb"))
        val word2 = SegmentedWord("食べて", "タベテ", "食べる", listOf("verb"))
        val word3 = SegmentedWord("食べます", "タベマス", "食べる", listOf("verb"))

        val entries = listOf(
            TranslationEntry(
                japanese = "食べた",
                english = "ate",
                segmentedWords = listOf(word)
            ),
            TranslationEntry(
                japanese = "食べてください",
                english = "please eat",
                segmentedWords = listOf(word2)
            ),
            TranslationEntry(
                japanese = "食べます",
                english = "will eat",
                segmentedWords = listOf(word3)
            )
        )

        val result = viewModel.extractVocabulary(entries)

        assertEquals(1, result.size)
        assertEquals("食べた", result[0].surface) // First occurrence wins
        assertEquals("食べる", result[0].dictionaryForm)
    }

    @Test
    fun `filters out OOV words`() = runTest {
        val oovWord = SegmentedWord("xyz123", "xyz123", "xyz123", emptyList(), isOov = true)
        val normalWord = SegmentedWord("食べる", "タベル", "食べる", listOf("verb"))

        val entries = listOf(
            TranslationEntry(
                japanese = "test",
                english = "test",
                segmentedWords = listOf(oovWord, normalWord)
            )
        )

        val result = viewModel.extractVocabulary(entries)

        assertEquals(1, result.size)
        assertEquals("食べる", result[0].surface)
    }

    @Test
    fun `filters out single-character words`() = runTest {
        val singleChar = SegmentedWord("の", "ノ", "の", listOf("particle"))
        val multiChar = SegmentedWord("食べる", "タベル", "食べる", listOf("verb"))

        val entries = listOf(
            TranslationEntry(
                japanese = "test",
                english = "test",
                segmentedWords = listOf(singleChar, multiChar)
            )
        )

        val result = viewModel.extractVocabulary(entries)

        assertEquals(1, result.size)
        assertEquals("食べる", result[0].surface)
    }

    @Test
    fun `maintains chronological order`() = runTest {
        val word1 = SegmentedWord("走る", "ハシル", "走る", listOf("verb"))
        val word2 = SegmentedWord("食べる", "タベル", "食べる", listOf("verb"))
        val word3 = SegmentedWord("飲む", "ノム", "飲む", listOf("verb"))

        val entries = listOf(
            TranslationEntry(japanese = "a", english = "a", segmentedWords = listOf(word1)),
            TranslationEntry(japanese = "b", english = "b", segmentedWords = listOf(word2)),
            TranslationEntry(japanese = "c", english = "c", segmentedWords = listOf(word3))
        )

        val result = viewModel.extractVocabulary(entries)

        assertEquals(3, result.size)
        assertEquals("走る", result[0].dictionaryForm)
        assertEquals("食べる", result[1].dictionaryForm)
        assertEquals("飲む", result[2].dictionaryForm)
    }

    @Test
    fun `enriches words with JLPT level and definition`() = runTest {
        coEvery { jmdictRepository.getJlptLevel("食べる") } returns 5
        coEvery { jmdictRepository.lookupWord("食べる") } returns listOf(
            DictionaryResult(
                entSeq = 1,
                kanji = listOf("食べる"),
                kana = listOf("たべる"),
                glosses = listOf("to eat", "to consume"),
                partOfSpeech = listOf("verb"),
                jlptLevel = 5
            )
        )

        val word = SegmentedWord("食べた", "タベタ", "食べる", listOf("verb"))
        val entries = listOf(
            TranslationEntry(japanese = "test", english = "test", segmentedWords = listOf(word))
        )

        val result = viewModel.extractVocabulary(entries)

        assertEquals(1, result.size)
        assertEquals(5, result[0].jlptLevel)
        assertEquals("to eat; to consume", result[0].definition)
    }

    @Test
    fun `empty translations yields empty vocabulary`() = runTest {
        val result = viewModel.extractVocabulary(emptyList())
        assertTrue(result.isEmpty())
    }
}
