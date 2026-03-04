package com.dstranslator.domain.model

import java.util.UUID

/**
 * Represents a single translation result displayed on the bottom screen.
 * Each OCR text block becomes one TranslationEntry.
 *
 * Extended with segmented word data for furigana rendering and dictionary lookup.
 * The segmentedWords and furiganaSegments fields default to empty lists for
 * backward compatibility with entries created before segmentation was available.
 */
data class TranslationEntry(
    val id: String = UUID.randomUUID().toString(),
    val japanese: String,
    val english: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String? = null,
    val segmentedWords: List<SegmentedWord> = emptyList(),
    val furiganaSegments: List<FuriganaSegment> = emptyList()
)
