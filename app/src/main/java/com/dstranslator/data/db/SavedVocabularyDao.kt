package com.dstranslator.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedVocabularyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(word: SavedVocabularyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(words: List<SavedVocabularyEntity>)

    @Query("SELECT * FROM saved_vocabulary ORDER BY savedAt DESC")
    fun getAll(): Flow<List<SavedVocabularyEntity>>

    @Query("DELETE FROM saved_vocabulary WHERE dictionaryForm = :dictionaryForm")
    suspend fun delete(dictionaryForm: String)

    @Query("DELETE FROM saved_vocabulary")
    suspend fun deleteAll()
}

