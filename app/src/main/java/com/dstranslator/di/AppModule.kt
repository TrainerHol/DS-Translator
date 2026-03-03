package com.dstranslator.di

import android.content.Context
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.tts.TtsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(
        @ApplicationContext context: Context
    ): SettingsRepository {
        return SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideTtsManager(
        @ApplicationContext context: Context,
        settingsRepository: SettingsRepository
    ): TtsManager {
        return TtsManager(context, settingsRepository)
    }
}
