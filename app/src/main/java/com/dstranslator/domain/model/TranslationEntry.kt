package com.dstranslator.domain.model

import java.util.UUID

/**
 * Represents a single translation result displayed on the bottom screen.
 * Each OCR text block becomes one TranslationEntry.
 */
data class TranslationEntry(
    val id: String = UUID.randomUUID().toString(),
    val japanese: String,
    val english: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String? = null
)
