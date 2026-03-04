package com.dstranslator.domain.model

/**
 * Represents a segment of text for furigana display.
 * Each segment has the main text and an optional reading (hiragana)
 * to display above the text as furigana annotation.
 */
data class FuriganaSegment(
    val text: String,              // The text to display (kanji, kana, or mixed)
    val reading: String? = null    // Hiragana reading above text (null = no furigana)
)
