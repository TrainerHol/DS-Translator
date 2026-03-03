package com.dstranslator.data.translation

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
    private lateinit var mlKitEngine: MlKitTranslationEngine
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var translationManager: TranslationManager

    @Before
    fun setUp() {
        deepLEngine = mockk()
        mlKitEngine = mockk()
        settingsRepository = mockk()

        coEvery { deepLEngine.name } returns "DeepL"
        coEvery { mlKitEngine.name } returns "ML Kit (On-Device)"

        translationManager = TranslationManager(
            deepLEngine = deepLEngine,
            mlKitEngine = mlKitEngine,
            settingsRepository = settingsRepository
        )
    }

    @Test
    fun `translate uses DeepL when API key is present and DeepL succeeds`() = runTest {
        // Arrange
        coEvery { settingsRepository.getDeepLApiKey() } returns "valid-api-key"
        coEvery { deepLEngine.translate("hello", any(), any()) } returns "DeepL translation"

        // Act
        val result = translationManager.translate("hello")

        // Assert
        assertEquals("DeepL translation", result)
        coVerify(exactly = 1) { deepLEngine.translate("hello", any(), any()) }
        coVerify(exactly = 0) { mlKitEngine.translate(any(), any(), any()) }
    }

    @Test
    fun `translate uses ML Kit fallback when API key is null`() = runTest {
        // Arrange
        coEvery { settingsRepository.getDeepLApiKey() } returns null
        coEvery { mlKitEngine.translate("hello", any(), any()) } returns "ML Kit translation"

        // Act
        val result = translationManager.translate("hello")

        // Assert
        assertEquals("ML Kit translation", result)
        coVerify(exactly = 0) { deepLEngine.translate(any(), any(), any()) }
        coVerify(exactly = 1) { mlKitEngine.translate("hello", any(), any()) }
    }

    @Test
    fun `translate uses ML Kit fallback when API key is blank`() = runTest {
        // Arrange
        coEvery { settingsRepository.getDeepLApiKey() } returns "   "
        coEvery { mlKitEngine.translate("hello", any(), any()) } returns "ML Kit translation"

        // Act
        val result = translationManager.translate("hello")

        // Assert
        assertEquals("ML Kit translation", result)
        coVerify(exactly = 0) { deepLEngine.translate(any(), any(), any()) }
        coVerify(exactly = 1) { mlKitEngine.translate("hello", any(), any()) }
    }

    @Test
    fun `translate uses ML Kit fallback when DeepL throws exception`() = runTest {
        // Arrange
        coEvery { settingsRepository.getDeepLApiKey() } returns "valid-api-key"
        coEvery { deepLEngine.translate("hello", any(), any()) } throws RuntimeException("DeepL API error")
        coEvery { mlKitEngine.translate("hello", any(), any()) } returns "ML Kit fallback"

        // Act
        val result = translationManager.translate("hello")

        // Assert
        assertEquals("ML Kit fallback", result)
        coVerify(exactly = 1) { deepLEngine.translate("hello", any(), any()) }
        coVerify(exactly = 1) { mlKitEngine.translate("hello", any(), any()) }
    }

    @Test
    fun `translate returns error message when both DeepL and ML Kit fail`() = runTest {
        // Arrange
        coEvery { settingsRepository.getDeepLApiKey() } returns "valid-api-key"
        coEvery { deepLEngine.translate("hello", any(), any()) } throws RuntimeException("DeepL error")
        coEvery { mlKitEngine.translate("hello", any(), any()) } throws RuntimeException("ML Kit error")

        // Act
        val result = translationManager.translate("hello")

        // Assert
        assertTrue(result.startsWith("Translation failed:"))
    }

    @Test
    fun `translate returns error when no API key and ML Kit fails`() = runTest {
        // Arrange
        coEvery { settingsRepository.getDeepLApiKey() } returns null
        coEvery { mlKitEngine.translate("hello", any(), any()) } throws RuntimeException("ML Kit error")

        // Act
        val result = translationManager.translate("hello")

        // Assert
        assertTrue(result.startsWith("Translation failed:"))
    }
}
