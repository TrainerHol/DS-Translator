package com.dstranslator.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [JMdictEntryEntity::class, JMdictSenseEntity::class],
    version = 1,
    exportSchema = false
)
abstract class JMdictDatabase : RoomDatabase() {
    abstract fun jmdictDao(): JMdictDao
}
