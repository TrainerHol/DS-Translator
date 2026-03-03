package com.dstranslator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_translations")
data class CachedTranslationEntity(
    @PrimaryKey val sourceText: String,
    val translatedText: String,
    val timestamp: Long
)
