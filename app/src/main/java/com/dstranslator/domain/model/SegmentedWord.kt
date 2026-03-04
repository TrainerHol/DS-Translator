package com.dstranslator.domain.model

/**
 * Represents a single word/morpheme from morphological analysis.
 * Produced by SudachiSegmenter for each token in the input text.
 */
data class SegmentedWord(
    val surface: String,            // Original text as it appears
    val reading: String,            // Katakana reading from morphological analysis
    val dictionaryForm: String,     // Base/dictionary form for lookups
    val partOfSpeech: List<String>, // Part of speech tags from Sudachi
    val isOov: Boolean = false      // Out-of-vocabulary flag
)
