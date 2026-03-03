package com.dstranslator.data.translation

import com.dstranslator.data.cache.TranslationCache
import com.dstranslator.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates translation with cache-first lookup, then DeepL-primary and ML Kit-fallback chain.
 *
 * Lookup order:
 * 1. Check two-tier cache (LRU in-memory -> Room database)
 * 2. If cache miss and no DeepL API key -> use ML Kit directly
 * 3. Try DeepL translation
 * 4. On DeepL failure -> fall back to ML Kit
 * 5. On ML Kit failure -> return error message
 * 6. On successful API translation -> store in cache for future lookups
 */
@Singleton
class TranslationManager @Inject constructor(
    private val deepLEngine: DeepLTranslationEngine,
    private val mlKitEngine: MlKitTranslationEngine,
    private val settingsRepository: SettingsRepository,
    private val translationCache: TranslationCache
) {
    /**
     * Translate Japanese text to English using cache-first, then DeepL-primary, ML Kit-fallback chain.
     * Always returns a result (never throws) -- on total failure, returns an error message string.
     */
    suspend fun translate(text: String): String {
        // Check cache first
        translationCache.get(text)?.let { cached ->
            return cached
        }

        // Cache miss -- call translation API (existing fallback chain)
        val result = translateViaApi(text)

        // Store in cache if translation succeeded (not an error message)
        if (!result.startsWith("Translation failed:")) {
            translationCache.put(text, result)
        }

        return result
    }

    /**
     * Translate via API with DeepL-primary, ML Kit-fallback chain.
     */
    private suspend fun translateViaApi(text: String): String {
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

    /**
     * Clear the translation cache (both in-memory and persistent).
     */
    suspend fun clearCache() {
        translationCache.clear()
    }
}
