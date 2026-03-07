package com.dstranslator.data.text

import java.text.Normalizer

object TextNormalizer {
    fun forTranslationInput(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        return normalized
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
    }
}

