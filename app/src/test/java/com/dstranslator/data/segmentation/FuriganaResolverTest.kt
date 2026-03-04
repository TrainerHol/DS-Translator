package com.dstranslator.data.segmentation

import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.wanikani.WaniKaniRepository
import com.dstranslator.domain.model.SegmentedWord
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FuriganaResolverTest {

    private lateinit var waniKaniRepository: WaniKaniRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var resolver: FuriganaResolver

    @Before
    fun setup() {
        waniKaniRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        resolver = FuriganaResolver(waniKaniRepository, settingsRepository)
    }

    @Test
    fun `resolve modeAll allKanjiGetFurigana`() = runTest {
        coEvery { settingsRepository.getFuriganaMode() } returns "all"

        val words = listOf(
            SegmentedWord(
                surface = "\u98DF\u3079\u308B", // 食べる
                reading = "\u30BF\u30D9\u30EB", // タベル (katakana)
                dictionaryForm = "\u98DF\u3079\u308B",
                partOfSpeech = listOf("verb")
            ),
            SegmentedWord(
                surface = "\u5B66\u6821", // 学校
                reading = "\u30AC\u30C3\u30B3\u30A6", // ガッコウ
                dictionaryForm = "\u5B66\u6821",
                partOfSpeech = listOf("noun")
            )
        )

        val result = resolver.resolve(words)

        assertEquals(2, result.size)
        // Katakana readings should be converted to hiragana
        assertEquals("\u305F\u3079\u308B", result[0].reading) // たべる
        assertEquals("\u304C\u3063\u3053\u3046", result[1].reading) // がっこう
    }

    @Test
    fun `resolve modeNone noFurigana`() = runTest {
        coEvery { settingsRepository.getFuriganaMode() } returns "none"

        val words = listOf(
            SegmentedWord(
                surface = "\u98DF\u3079\u308B", // 食べる
                reading = "\u30BF\u30D9\u30EB",
                dictionaryForm = "\u98DF\u3079\u308B",
                partOfSpeech = listOf("verb")
            )
        )

        val result = resolver.resolve(words)

        assertEquals(1, result.size)
        assertNull(result[0].reading)
    }

    @Test
    fun `resolve modeWanikani onlyUnlearnedKanjiGetFurigana`() = runTest {
        coEvery { settingsRepository.getFuriganaMode() } returns "wanikani"
        // 食 is learned, 学 and 校 are not
        coEvery { waniKaniRepository.getLearnedKanji() } returns setOf("\u98DF") // 食

        val words = listOf(
            SegmentedWord(
                surface = "\u98DF\u3079\u308B", // 食べる -- 食 is learned
                reading = "\u30BF\u30D9\u30EB",
                dictionaryForm = "\u98DF\u3079\u308B",
                partOfSpeech = listOf("verb")
            ),
            SegmentedWord(
                surface = "\u5B66\u6821", // 学校 -- 学 and 校 are unlearned
                reading = "\u30AC\u30C3\u30B3\u30A6",
                dictionaryForm = "\u5B66\u6821",
                partOfSpeech = listOf("noun")
            )
        )

        val result = resolver.resolve(words)

        assertEquals(2, result.size)
        // 食べる: 食 is learned, so all kanji in this word are learned -> no furigana
        assertNull(result[0].reading)
        // 学校: 学 and 校 are NOT learned -> furigana shown
        assertEquals("\u304C\u3063\u3053\u3046", result[1].reading) // がっこう
    }

    @Test
    fun `resolve pureKana neverGetsFurigana`() = runTest {
        coEvery { settingsRepository.getFuriganaMode() } returns "all"

        val words = listOf(
            SegmentedWord(
                surface = "\u3053\u308C", // これ (hiragana)
                reading = "\u30B3\u30EC",
                dictionaryForm = "\u3053\u308C",
                partOfSpeech = listOf("pronoun")
            ),
            SegmentedWord(
                surface = "\u30B3\u30FC\u30D2\u30FC", // コーヒー (katakana)
                reading = "\u30B3\u30FC\u30D2\u30FC",
                dictionaryForm = "\u30B3\u30FC\u30D2\u30FC",
                partOfSpeech = listOf("noun")
            )
        )

        val result = resolver.resolve(words)

        assertEquals(2, result.size)
        // Pure kana words never get furigana regardless of mode
        assertNull(result[0].reading)
        assertNull(result[1].reading)
    }

    @Test
    fun `resolve katakanaReadingConvertedToHiragana`() = runTest {
        coEvery { settingsRepository.getFuriganaMode() } returns "all"

        val words = listOf(
            SegmentedWord(
                surface = "\u5148\u751F", // 先生
                reading = "\u30BB\u30F3\u30BB\u30A4", // センセイ (katakana)
                dictionaryForm = "\u5148\u751F",
                partOfSpeech = listOf("noun")
            )
        )

        val result = resolver.resolve(words)

        assertEquals(1, result.size)
        // Katakana reading should be converted to hiragana
        assertEquals("\u305B\u3093\u305B\u3044", result[0].reading) // せんせい
    }
}
