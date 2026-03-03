package com.dstranslator.di

import android.content.Context
import androidx.room.Room
import com.dstranslator.data.cache.TranslationCache
import com.dstranslator.data.db.AppDatabase
import com.dstranslator.data.db.CachedTranslationDao
import com.dstranslator.data.db.TranslationHistoryDao
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

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ds_translator.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    @Singleton
    fun provideCachedTranslationDao(db: AppDatabase): CachedTranslationDao {
        return db.cachedTranslationDao()
    }

    @Provides
    @Singleton
    fun provideTranslationHistoryDao(db: AppDatabase): TranslationHistoryDao {
        return db.translationHistoryDao()
    }

    @Provides
    @Singleton
    fun provideTranslationCache(dao: CachedTranslationDao): TranslationCache {
        return TranslationCache(dao)
    }
}
