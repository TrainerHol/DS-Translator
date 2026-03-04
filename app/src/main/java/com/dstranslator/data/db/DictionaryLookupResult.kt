package com.dstranslator.data.db

import androidx.room.ColumnInfo

data class DictionaryLookupResult(
    @ColumnInfo(name = "ent_seq") val entSeq: Int,
    val kanji: String,
    val kana: String,
    val glosses: String,
    val pos: String,
    val jlpt: Int?
)
