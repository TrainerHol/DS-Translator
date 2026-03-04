package com.dstranslator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "translation_history")
data class TranslationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val sourceText: String,
    val translatedText: String,
    val timestamp: Long,
    val profileId: Long? = null
)
