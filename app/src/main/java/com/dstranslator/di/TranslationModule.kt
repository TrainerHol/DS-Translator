package com.dstranslator.di

import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.translation.DeepLTranslationEngine
import com.dstranslator.data.translation.MlKitTranslationEngine
import com.dstranslator.data.translation.TranslationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
    fun provideTranslationManager(
        deepLEngine: DeepLTranslationEngine,
        mlKitEngine: MlKitTranslationEngine,
        settingsRepository: SettingsRepository
    ): TranslationManager {
        return TranslationManager(deepLEngine, mlKitEngine, settingsRepository)
    }
}
