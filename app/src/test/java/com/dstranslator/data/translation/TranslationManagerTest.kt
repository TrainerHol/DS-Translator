package com.dstranslator.data.translation

import com.dstranslator.data.cache.TranslationCache
import com.dstranslator.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TranslationManagerTest {

    private lateinit var deepLEngine: DeepLTranslationEngine
    private lateinit var openAiEngine: OpenAiTranslationEngine
    private lateinit var claudeEngine: ClaudeTranslationEngine
    private lateinit var mlKitEngine: MlKitTranslationEngine
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var translationCache: TranslationCache
    private lateinit var translationManager: TranslationManager

    @Before
    fun setUp() {
        deepLEngine = mockk()
        openAiEngine = mockk()
        claudeEngine = mockk()
        mlKitEngine = mockk()
        settingsRepository = mockk()
        translationCache = mockk(relaxed = true)

        coEvery { deepLEngine.name } returns "DeepL"
        coEvery { openAiEngine.name } returns "OpenAI"
        coEvery { claudeEngine.name } returns "Claude"
        coEvery { mlKitEngine.name } returns "ML Kit (On-Device)"
        // Default: cache always misses so tests exercise the API path
        coEvery { translationCache.get(any()) } returns null

        translationManager = TranslationManager(
            deepLEngine = deepLEngine,
            openAiEngine = openAiEngine,
            claudeEngine = claudeEngine,
            mlKitEngine = mlKitEngine,
            settingsRepository = settingsRepository,
            translationCache = translationCache
        )
    }

    // --- Engine selection tests ---

    @Test
    fun `translate routes to DeepL when engine setting is deepl`() = runTest {
        coEvery { settingsRepository.getTranslationEngine() } returns "deepl"
        coEvery { deepLEngine.translate("hello", any(), any()) } returns "DeepL translation"

        val result = translationManager.translate("hello")

        assertEquals("DeepL translation", result)
        coVerify(exactly = 1) { deepLEngine.translate("hello", any(), any()) }
        coVerify(exactly = 0) { openAiEngine.translate(any(), any(), any()) }
        coVerify(exactly = 0) { claudeEngine.translate(any(), any(), any()) }
    }

    @Test
    fun `translate routes to OpenAI when engine setting is openai`() = runTest {
        coEvery { settingsRepository.getTranslationEngine() } returns "openai"
        coEvery { openAiEngine.translate("hello", any(), any()) } returns "OpenAI translation"

        val result = translationManager.translate("hello")

        assertEquals("OpenAI translation", result)
        coVerify(exactly = 0) { deepLEngine.translate(any(), any(), any()) }
        coVerify(exactly = 1) { openAiEngine.translate("hello", any(), any()) }
        coVerify(exactly = 0) { claudeEngine.translate(any(), any(), any()) }
    }

    @Test
    fun `translate routes to Claude when engine setting is claude`() = runTest {
        coEvery { settingsRepository.getTranslationEngine() } returns "claude"
        coEvery { claudeEngine.translate("hello", any(), any()) } returns "Claude translation"

        val result = translationManager.translate("hello")

        assertEquals("Claude translation", result)
        coVerify(exactly = 0) { deepLEngine.translate(any(), any(), any()) }
        coVerify(exactly = 0) { openAiEngine.translate(any(), any(), any()) }
        coVerify(exactly = 1) { claudeEngine.translate("hello", any(), any()) }
    }

    @Test
    fun `translate defaults to DeepL when engine setting is null`() = runTest {
        coEvery { settingsRepository.getTranslationEngine() } returns null
        coEvery { deepLEngine.translate("hello", any(), any()) } returns "DeepL default"

        val result = translationManager.translate("hello")

        assertEquals("DeepL default", result)
        coVerify(exactly = 1) { deepLEngine.translate("hello", any(), any()) }
    }

    // --- Fallback tests ---

    @Test
    fun `translate falls back to ML Kit when OpenAI engine fails`() = runTest {
        coEvery { settingsRepository.getTranslationEngine() } returns "openai"
        coEvery { openAiEngine.translate("hello", any(), any()) } throws RuntimeException("API error")
        coEvery { mlKitEngine.translate("hello", any(), any()) } returns "ML Kit fallback"

        val result = translationManager.translate("hello")

        assertEquals("ML Kit fallback", result)
        coVerify(exactly = 1) { openAiEngine.translate("hello", any(), any()) }
        coVerify(exactly = 1) { mlKitEngine.translate("hello", any(), any()) }
    }

    @Test
    fun `translate falls back to ML Kit when Claude engine fails`() = runTest {
        coEvery { settingsRepository.getTranslationEngine() } returns "claude"
        coEvery { claudeEngine.translate("hello", any(), any()) } throws RuntimeException("API error")
        coEvery { mlKitEngine.translate("hello", any(), any()) } returns "ML Kit fallback"

        val result = translationManager.translate("hello")

        assertEquals("ML Kit fallback", result)
        coVerify(exactly = 1) { claudeEngine.translate("hello", any(), any()) }
        coVerify(exactly = 1) { mlKitEngine.translate("hello", any(), any()) }
    }

    @Test
    fun `translate falls back to ML Kit when DeepL engine fails`() = runTest {
        coEvery { settingsRepository.getTranslationEngine() } returns "deepl"
        coEvery { deepLEngine.translate("hello", any(), any()) } throws RuntimeException("DeepL error")
        coEvery { mlKitEngine.translate("hello", any(), any()) } returns "ML Kit fallback"

        val result = translationManager.translate("hello")

        assertEquals("ML Kit fallback", result)
        coVerify(exactly = 1) { deepLEngine.translate("hello", any(), any()) }
        coVerify(exactly = 1) { mlKitEngine.translate("hello", any(), any()) }
    }

    @Test
    fun `translate returns error message when both engine and ML Kit fail`() = runTest {
        coEvery { settingsRepository.getTranslationEngine() } returns "openai"
        coEvery { openAiEngine.translate("hello", any(), any()) } throws RuntimeException("OpenAI error")
        coEvery { mlKitEngine.translate("hello", any(), any()) } throws RuntimeException("ML Kit error")

        val result = translationManager.translate("hello")

        assertTrue(result.startsWith("Translation failed:"))
    }

    // --- Cache tests ---

    @Test
    fun `translate returns cached result when available`() = runTest {
        coEvery { translationCache.get("hello") } returns "Cached translation"

        val result = translationManager.translate("hello")

        assertEquals("Cached translation", result)
        coVerify(exactly = 0) { deepLEngine.translate(any(), any(), any()) }
        coVerify(exactly = 0) { openAiEngine.translate(any(), any(), any()) }
        coVerify(exactly = 0) { claudeEngine.translate(any(), any(), any()) }
    }

    @Test
    fun `translate caches successful translation`() = runTest {
        coEvery { settingsRepository.getTranslationEngine() } returns "openai"
        coEvery { openAiEngine.translate("hello", any(), any()) } returns "OpenAI result"

        translationManager.translate("hello")

        coVerify(exactly = 1) { translationCache.put("hello", "OpenAI result") }
    }

    @Test
    fun `translate does not cache error messages`() = runTest {
        coEvery { settingsRepository.getTranslationEngine() } returns "openai"
        coEvery { openAiEngine.translate("hello", any(), any()) } throws RuntimeException("fail")
        coEvery { mlKitEngine.translate("hello", any(), any()) } throws RuntimeException("fail too")

        translationManager.translate("hello")

        coVerify(exactly = 0) { translationCache.put(any(), any()) }
    }
}
