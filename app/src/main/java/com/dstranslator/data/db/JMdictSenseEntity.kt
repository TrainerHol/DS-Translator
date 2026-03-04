package com.dstranslator.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "senses")
data class JMdictSenseEntity(
    @PrimaryKey val id: Int,
    @ColumnInfo(name = "ent_seq") val entSeq: Int,
    @ColumnInfo(name = "glosses") val glosses: String,    // JSON array
    @ColumnInfo(name = "pos") val pos: String,            // JSON array
    @ColumnInfo(name = "jlpt") val jlptLevel: Int? = null // N5=5..N1=1
)
