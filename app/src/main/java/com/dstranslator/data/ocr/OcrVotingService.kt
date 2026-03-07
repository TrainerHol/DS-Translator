package com.dstranslator.data.ocr

import android.graphics.Bitmap
import com.dstranslator.domain.engine.OcrEngine
import com.dstranslator.domain.model.OcrTextBlock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrVotingService @Inject constructor(
    private val ocrEngine: OcrEngine
) {
    suspend fun recognizeWithVoting(
        bitmap: Bitmap,
        runs: Int = 3
    ): List<OcrTextBlock> = coroutineScope {
        val actualRuns = runs.coerceIn(1, 5)
        val results = (0 until actualRuns).map {
            async { ocrEngine.recognize(bitmap) }
        }.awaitAll()

        val normalizedTexts = results.map { blocks ->
            normalizeForVoting(blocks)
        }

        val winningText = normalizedTexts
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            ?: ""

        val winnerIndex = normalizedTexts.indexOfFirst { it == winningText }
        if (winnerIndex >= 0) results[winnerIndex] else results.firstOrNull().orEmpty()
    }

    private fun normalizeForVoting(blocks: List<OcrTextBlock>): String {
        val raw = blocks
            .asSequence()
            .map { it.text }
            .map { it.replace("\r\n", "\n").replace('\r', '\n') }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        return normalized
            .replace(WHITESPACE, " ")
            .trim()
    }

    private companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}

