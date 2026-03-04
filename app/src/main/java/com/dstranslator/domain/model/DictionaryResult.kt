package com.dstranslator.domain.model

/**
 * Represents a dictionary lookup result from JMdict.
 * Contains kanji writings, kana readings, English definitions,
 * part of speech information, and optional JLPT classification.
 */
data class DictionaryResult(
    val entSeq: Int,                // JMdict entry sequence number
    val kanji: List<String>,        // Kanji writings
    val kana: List<String>,         // Kana readings
    val glosses: List<String>,      // English definitions
    val partOfSpeech: List<String>, // Part of speech
    val jlptLevel: Int? = null      // N5=5, N4=4, N3=3, N2=2, N1=1, null=unclassified
)
