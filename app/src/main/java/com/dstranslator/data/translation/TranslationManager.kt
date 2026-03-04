package com.dstranslator.data.translation

import com.dstranslator.data.cache.TranslationCache
import com.dstranslator.data.settings.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates translation with cache-first lookup and user-selectable engine routing.
 *
 * Lookup order:
 * 1. Check two-tier cache (LRU in-memory -> Room database)
 * 2. Route to user-selected engine (DeepL, OpenAI, or Claude)
 * 3. On engine failure -> fall back to ML Kit
 * 4. On ML Kit failure -> return error message
 * 5. On successful translation -> store in cache for future lookups
 */
@Singleton
class TranslationManager @Inject constructor(
    private val deepLEngine: DeepLTranslationEngine,
    private val openAiEngine: OpenAiTranslationEngine,
    private val claudeEngine: ClaudeTranslationEngine,
    private val mlKitEngine: MlKitTranslationEngine,
    private val settingsRepository: SettingsRepository,
    private val translationCache: TranslationCache
) {
    /**
     * Translate Japanese text to English using cache-first, then user-selected engine,
     * with ML Kit fallback. Always returns a result (never throws) -- on total failure,
     * returns an error message string.
     */
    suspend fun translate(text: String): String {
        // Check cache first
        translationCache.get(text)?.let { cached ->
            return cached
        }

        // Cache miss -- call translation API via selected engine
        val result = translateViaApi(text)

        // Store in cache if translation succeeded (not an error message)
        if (!result.startsWith("Translation failed:")) {
            translationCache.put(text, result)
        }

        return result
    }

    /**
     * Translate via the user-selected engine with ML Kit fallback on failure.
     */
    private suspend fun translateViaApi(text: String): String {
        val engine = when (settingsRepository.getTranslationEngine()) {
            "openai" -> openAiEngine
            "claude" -> claudeEngine
            else -> deepLEngine  // "deepl" or null default
        }

        return try {
            engine.translate(text)
        } catch (e: Exception) {
            // Fall back to ML Kit on any engine failure
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
