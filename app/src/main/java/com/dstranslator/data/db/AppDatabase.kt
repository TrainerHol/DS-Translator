package com.dstranslator.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedTranslationEntity::class, TranslationHistoryEntity::class],
    version = 1
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedTranslationDao(): CachedTranslationDao
    abstract fun translationHistoryDao(): TranslationHistoryDao
}
