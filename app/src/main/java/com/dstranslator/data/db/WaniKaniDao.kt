package com.dstranslator.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface WaniKaniDao {
    @Upsert
    suspend fun upsertAll(assignments: List<WaniKaniAssignmentEntity>)

    @Query("SELECT * FROM wanikani_assignments WHERE kanji_character = :kanji LIMIT 1")
    suspend fun getAssignmentForKanji(kanji: String): WaniKaniAssignmentEntity?

    @Query("SELECT kanji_character FROM wanikani_assignments WHERE srs_stage >= 5 AND kanji_character IS NOT NULL")
    suspend fun getLearnedKanji(): List<String>

    @Query("DELETE FROM wanikani_assignments")
    suspend fun clearAll()
}
