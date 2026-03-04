package com.dstranslator.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class JMdictEntryEntity(
    @PrimaryKey @ColumnInfo(name = "ent_seq") val entSeq: Int,
    @ColumnInfo(name = "kanji") val kanji: String,   // JSON array of kanji writings
    @ColumnInfo(name = "kana") val kana: String       // JSON array of kana readings
)
