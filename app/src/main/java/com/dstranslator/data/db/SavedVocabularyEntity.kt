package com.dstranslator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_vocabulary")
data class SavedVocabularyEntity(
    @PrimaryKey val dictionaryForm: String,
    val surface: String,
    val reading: String,
    val jlptLevel: Int?,
    val definition: String?,
    val savedAt: Long
)

