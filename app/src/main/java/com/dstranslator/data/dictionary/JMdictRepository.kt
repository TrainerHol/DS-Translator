package com.dstranslator.data.dictionary

import com.dstranslator.data.db.JMdictDao
import com.dstranslator.domain.model.DictionaryResult
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JMdictRepository @Inject constructor(
    private val jmdictDao: JMdictDao
) {
    suspend fun lookupWord(word: String): List<DictionaryResult> {
        if (word.isBlank()) return emptyList()
        return jmdictDao.lookupWord(word).map { row ->
            DictionaryResult(
                entSeq = row.entSeq,
                kanji = parseJsonArray(row.kanji),
                kana = parseJsonArray(row.kana),
                glosses = parseJsonArray(row.glosses),
                partOfSpeech = parseJsonArray(row.pos),
                jlptLevel = row.jlpt
            )
        }
    }

    suspend fun getJlptLevel(word: String): Int? {
        if (word.isBlank()) return null
        return jmdictDao.getJlptLevelForWord(word)
    }

    internal fun parseJsonArray(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            listOf(json)  // Fallback: treat as plain string
        }
    }
}
