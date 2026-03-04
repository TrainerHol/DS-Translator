package com.dstranslator.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationHistoryDao {
    @Insert
    suspend fun insert(entry: TranslationHistoryEntity)

    @Query("SELECT * FROM translation_history WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySession(sessionId: String): Flow<List<TranslationHistoryEntity>>

    @Query("SELECT * FROM translation_history WHERE profileId = :profileId ORDER BY timestamp DESC")
    fun getByProfile(profileId: Long): Flow<List<TranslationHistoryEntity>>

    @Query("DELETE FROM translation_history WHERE profileId = :profileId")
    suspend fun deleteByProfile(profileId: Long)

    @Query("DELETE FROM translation_history")
    suspend fun deleteAll()
}
