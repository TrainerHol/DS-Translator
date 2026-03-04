package com.dstranslator.di

import com.dstranslator.data.cache.TranslationCache
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.translation.ClaudeTranslationEngine
import com.dstranslator.data.translation.DeepLTranslationEngine
import com.dstranslator.data.translation.MlKitTranslationEngine
import com.dstranslator.data.translation.OpenAiTranslationEngine
import com.dstranslator.data.translation.TranslationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TranslationModule {

    @Provides
    @Singleton
    fun provideDeepLTranslationEngine(
        settingsRepository: SettingsRepository
    ): DeepLTranslationEngine {
        return DeepLTranslationEngine(settingsRepository)
    }

    @Provides
    @Singleton
    fun provideMlKitTranslationEngine(): MlKitTranslationEngine {
        return MlKitTranslationEngine()
    }

    @Provides
    @Singleton
    fun provideOpenAiTranslationEngine(
        client: OkHttpClient,
        settingsRepository: SettingsRepository
    ): OpenAiTranslationEngine {
        return OpenAiTranslationEngine(client, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideClaudeTranslationEngine(
        client: OkHttpClient,
        settingsRepository: SettingsRepository
    ): ClaudeTranslationEngine {
        return ClaudeTranslationEngine(client, settingsRepository)
    }

    @Provides
    @Singleton
    fun provideTranslationManager(
        deepLEngine: DeepLTranslationEngine,
        openAiEngine: OpenAiTranslationEngine,
        claudeEngine: ClaudeTranslationEngine,
        mlKitEngine: MlKitTranslationEngine,
        settingsRepository: SettingsRepository,
        translationCache: TranslationCache
    ): TranslationManager {
        return TranslationManager(
            deepLEngine,
            openAiEngine,
            claudeEngine,
            mlKitEngine,
            settingsRepository,
            translationCache
        )
    }
}
