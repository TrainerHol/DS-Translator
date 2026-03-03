package com.dstranslator.domain.engine

/**
 * Pluggable translation engine interface.
 * Implementations provide Japanese-to-English translation via different backends.
 */
interface TranslationEngine {
    /**
     * Translate text from source language to target language.
     * @param text The text to translate
     * @param sourceLang Source language code (default: "ja" for Japanese)
     * @param targetLang Target language code (default: "en" for English)
     * @return Translated text
     */
    suspend fun translate(
        text: String,
        sourceLang: String = "ja",
        targetLang: String = "en"
    ): String

    /** Display name for this engine */
    val name: String

    /** Whether this engine requires an API key to function */
    val requiresApiKey: Boolean
}
