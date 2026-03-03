package com.dstranslator.domain.engine

import android.graphics.Bitmap
import com.dstranslator.domain.model.OcrTextBlock

/**
 * Pluggable OCR engine interface.
 * Implementations extract Japanese text from screen captures.
 */
interface OcrEngine {
    /**
     * Recognize text in the given bitmap image.
     * @param bitmap The captured screen image (or cropped region)
     * @return List of detected text blocks with positions and confidence
     */
    suspend fun recognize(bitmap: Bitmap): List<OcrTextBlock>

    /** Display name for this engine */
    val name: String
}
