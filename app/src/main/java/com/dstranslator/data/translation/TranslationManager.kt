package com.dstranslator.data.translation

import com.dstranslator.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates translation with DeepL-primary and ML Kit-fallback chain.
 *
 * Fallback logic:
 * 1. If DeepL API key is null/blank -> use ML Kit directly
 * 2. Try DeepL translation
 * 3. On DeepL failure -> fall back to ML Kit
 * 4. On ML Kit failure -> return error message
 */
@Singleton
class TranslationManager @Inject constructor(
    private val deepLEngine: DeepLTranslationEngine,
    private val mlKitEngine: MlKitTranslationEngine,
    private val settingsRepository: SettingsRepository
) {
    /**
     * Translate Japanese text to English using the DeepL-primary, ML Kit-fallback chain.
     * Always returns a result (never throws) -- on total failure, returns an error message string.
     */
    suspend fun translate(text: String): String {
        val apiKey = settingsRepository.getDeepLApiKey()

        // If no API key, go straight to ML Kit
        if (apiKey.isNullOrBlank()) {
            return translateWithMlKit(text)
        }

        // Try DeepL first
        return try {
            deepLEngine.translate(text)
        } catch (e: Exception) {
            // DeepL failed, fall back to ML Kit
            translateWithMlKit(text)
        }
    }

    private suspend fun translateWithMlKit(text: String): String {
        return try {
            mlKitEngine.translate(text)
        } catch (e: Exception) {
            "Translation failed: ${e.message}"
        }
    }
}
