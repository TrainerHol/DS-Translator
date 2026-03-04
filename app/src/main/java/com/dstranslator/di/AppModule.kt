package com.dstranslator.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dstranslator.data.cache.TranslationCache
import com.dstranslator.data.db.AppDatabase
import com.dstranslator.data.db.CachedTranslationDao
import com.dstranslator.data.db.JMdictDao
import com.dstranslator.data.db.JMdictDatabase
import com.dstranslator.data.db.ProfileDao
import com.dstranslator.data.db.TranslationHistoryDao
import com.dstranslator.data.db.WaniKaniDao
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.tts.TtsManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS wanikani_assignments (
                    subject_id INTEGER NOT NULL PRIMARY KEY,
                    subject_type TEXT NOT NULL,
                    srs_stage INTEGER NOT NULL,
                    passed_at TEXT,
                    kanji_character TEXT
                )
            """)
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS profiles (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    isDefault INTEGER NOT NULL DEFAULT 0,
                    settingsJson TEXT NOT NULL,
                    captureRegionsJson TEXT NOT NULL,
                    autoReadEnabled INTEGER NOT NULL DEFAULT 0,
                    autoReadFlushMode INTEGER NOT NULL DEFAULT 1,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """)
            db.execSQL("ALTER TABLE translation_history ADD COLUMN profileId INTEGER DEFAULT NULL")
        }
    }

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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
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
    fun provideWaniKaniDao(db: AppDatabase): WaniKaniDao {
        return db.waniKaniDao()
    }

    @Provides
    @Singleton
    fun provideProfileDao(db: AppDatabase): ProfileDao {
        return db.profileDao()
    }

    @Provides
    @Singleton
    fun provideTranslationCache(dao: CachedTranslationDao): TranslationCache {
        return TranslationCache(dao)
    }

    @Provides
    @Singleton
    fun provideJMdictDatabase(
        @ApplicationContext context: Context
    ): JMdictDatabase {
        return Room.databaseBuilder(
            context,
            JMdictDatabase::class.java,
            "jmdict.db"
        ).createFromAsset("databases/jmdict.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun provideJMdictDao(db: JMdictDatabase): JMdictDao {
        return db.jmdictDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder().build()
    }
}
