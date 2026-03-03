package com.dstranslator.domain.model

import android.graphics.Rect

/**
 * A single block of text detected by OCR, with optional position and confidence.
 */
data class OcrTextBlock(
    val text: String,
    val boundingBox: Rect? = null,
    val confidence: Float? = null
)
