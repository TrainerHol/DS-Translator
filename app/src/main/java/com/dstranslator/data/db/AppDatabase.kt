package com.dstranslator.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedTranslationEntity::class,
        TranslationHistoryEntity::class,
        WaniKaniAssignmentEntity::class
    ],
    version = 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun cachedTranslationDao(): CachedTranslationDao
    abstract fun translationHistoryDao(): TranslationHistoryDao
    abstract fun waniKaniDao(): WaniKaniDao
}
