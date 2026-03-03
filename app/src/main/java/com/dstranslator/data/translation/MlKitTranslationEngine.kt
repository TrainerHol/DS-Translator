package com.dstranslator.data.translation

import com.dstranslator.domain.engine.TranslationEngine
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit on-device translation engine. No API key required.
 * Provides lower-quality but free fallback translation.
 * Downloads the translation model on first use.
 */
@Singleton
class MlKitTranslationEngine @Inject constructor() : TranslationEngine {

    override val name: String = "ML Kit (On-Device)"
    override val requiresApiKey: Boolean = false

    private val translator = Translation.getClient(
        TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.JAPANESE)
            .setTargetLanguage(TranslateLanguage.ENGLISH)
            .build()
    )

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String {
        // Ensure the model is downloaded before translating
        val conditions = DownloadConditions.Builder().build()
        translator.downloadModelIfNeeded(conditions).await()
        return translator.translate(text).await()
    }
}
