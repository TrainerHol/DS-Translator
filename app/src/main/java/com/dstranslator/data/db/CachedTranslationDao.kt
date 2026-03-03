package com.dstranslator.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CachedTranslationDao {
    @Query("SELECT * FROM cached_translations WHERE sourceText = :text LIMIT 1")
    suspend fun findBySourceText(text: String): CachedTranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CachedTranslationEntity)

    @Query("DELETE FROM cached_translations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM cached_translations")
    suspend fun count(): Int
}
