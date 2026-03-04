package com.dstranslator.data.db

import androidx.room.Dao
import androidx.room.Query

@Dao
interface JMdictDao {
    @Query("""
        SELECT e.ent_seq, e.kanji, e.kana, s.glosses, s.pos, s.jlpt
        FROM entries e
        INNER JOIN senses s ON e.ent_seq = s.ent_seq
        WHERE e.kanji LIKE '%' || :word || '%'
           OR e.kana LIKE '%' || :word || '%'
        LIMIT 10
    """)
    suspend fun lookupWord(word: String): List<DictionaryLookupResult>

    @Query("SELECT jlpt FROM senses WHERE ent_seq = :entSeq AND jlpt IS NOT NULL LIMIT 1")
    suspend fun getJlptLevel(entSeq: Int): Int?

    @Query("""
        SELECT DISTINCT s.jlpt
        FROM entries e
        INNER JOIN senses s ON e.ent_seq = s.ent_seq
        WHERE (e.kanji LIKE '%' || :word || '%' OR e.kana LIKE '%' || :word || '%')
          AND s.jlpt IS NOT NULL
        LIMIT 1
    """)
    suspend fun getJlptLevelForWord(word: String): Int?
}
