package com.dstranslator.data.ocr

import android.graphics.Bitmap
import com.dstranslator.domain.engine.OcrEngine
import com.dstranslator.domain.model.OcrTextBlock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit Text Recognition v2 implementation for Japanese OCR.
 * Uses the bundled Japanese text recognition model.
 */
@Singleton
class MlKitOcrEngine @Inject constructor() : OcrEngine {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())

    override val name: String = "ML Kit"

    override suspend fun recognize(bitmap: Bitmap): List<OcrTextBlock> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()

        return result.textBlocks.map { block ->
            OcrTextBlock(
                text = block.text,
                boundingBox = block.boundingBox,
                confidence = block.lines.firstOrNull()?.confidence
            )
        }
    }
}
