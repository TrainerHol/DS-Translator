package com.dstranslator.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wanikani_assignments")
data class WaniKaniAssignmentEntity(
    @PrimaryKey @ColumnInfo(name = "subject_id") val subjectId: Int,
    @ColumnInfo(name = "subject_type") val subjectType: String,
    @ColumnInfo(name = "srs_stage") val srsStage: Int,
    @ColumnInfo(name = "passed_at") val passedAt: String? = null,
    @ColumnInfo(name = "kanji_character") val kanjiCharacter: String? = null
)
