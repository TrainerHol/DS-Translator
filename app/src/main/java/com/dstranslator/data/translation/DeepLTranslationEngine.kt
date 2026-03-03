package com.dstranslator.data.translation

import com.deepl.api.Translator
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.domain.engine.TranslationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DeepL API translation engine. Requires a valid API key configured in settings.
 * Provides high-quality Japanese-to-English translation via the DeepL cloud API.
 */
@Singleton
class DeepLTranslationEngine @Inject constructor(
    private val settingsRepository: SettingsRepository
) : TranslationEngine {

    override val name: String = "DeepL"
    override val requiresApiKey: Boolean = true

    override suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.getDeepLApiKey()
            ?: throw IllegalStateException("DeepL API key not configured")

        val translator = Translator(apiKey)
        val result = translator.translateText(text, sourceLang, targetLang)
        result.text
    }
}
