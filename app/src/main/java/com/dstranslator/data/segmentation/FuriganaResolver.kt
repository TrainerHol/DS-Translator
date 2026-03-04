package com.dstranslator.data.segmentation

import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.wanikani.WaniKaniRepository
import com.dstranslator.domain.model.FuriganaSegment
import com.dstranslator.domain.model.SegmentedWord
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves which segmented words should display furigana based on the user's
 * furigana mode setting and WaniKani learned kanji data.
 *
 * Modes:
 * - "all": Every word containing kanji gets furigana reading
 * - "none": No words get furigana
 * - "wanikani": Only words containing at least one unlearned kanji get furigana
 *
 * Pure kana words (hiragana/katakana only) never receive furigana regardless of mode.
 * Katakana readings from Sudachi are converted to hiragana for display.
 */
@Singleton
class FuriganaResolver @Inject constructor(
    private val waniKaniRepository: WaniKaniRepository,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Resolve furigana segments for a list of segmented words.
     *
     * @param words List of segmented words from SudachiSegmenter
     * @return List of FuriganaSegments with reading set based on mode
     */
    suspend fun resolve(words: List<SegmentedWord>): List<FuriganaSegment> {
        val mode = settingsRepository.getFuriganaMode()
        val learnedKanji: Set<String> = if (mode == "wanikani") {
            waniKaniRepository.getLearnedKanji()
        } else emptySet()

        return words.map { word ->
            val hasKanji = word.surface.any { it.isKanji() }
            val reading = when {
                !hasKanji -> null  // Pure kana -- no furigana needed
                mode == "none" -> null
                mode == "all" -> katakanaToHiragana(word.reading)
                mode == "wanikani" -> {
                    // Show furigana only if ANY kanji in the word is unlearned
                    val kanjiInWord = word.surface.filter { it.isKanji() }.map { it.toString() }
                    val allLearned = kanjiInWord.all { it in learnedKanji }
                    if (allLearned) null else katakanaToHiragana(word.reading)
                }
                else -> null
            }
            FuriganaSegment(text = word.surface, reading = reading)
        }
    }

    /**
     * Check if a character is a CJK kanji.
     * Covers CJK Unified Ideographs and CJK Extension A ranges.
     */
    private fun Char.isKanji(): Boolean =
        this in '\u4e00'..'\u9faf' || this in '\u3400'..'\u4dbf'

    /**
     * Convert katakana string to hiragana for furigana display.
     * Sudachi returns readings in katakana, but furigana is displayed in hiragana.
     */
    private fun katakanaToHiragana(katakana: String): String =
        katakana.map { if (it in '\u30A0'..'\u30FF') (it - 0x60) else it }.joinToString("")
}
