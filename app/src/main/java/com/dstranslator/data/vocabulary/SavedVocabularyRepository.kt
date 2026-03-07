package com.dstranslator.data.vocabulary

import com.dstranslator.data.db.SavedVocabularyDao
import com.dstranslator.data.db.SavedVocabularyEntity
import com.dstranslator.data.dictionary.JMdictRepository
import com.dstranslator.domain.model.SegmentedWord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class SavedVocabularyWord(
    val surface: String,
    val reading: String,
    val dictionaryForm: String,
    val jlptLevel: Int?,
    val definition: String?,
    val savedAt: Long
)

@Singleton
class SavedVocabularyRepository @Inject constructor(
    private val dao: SavedVocabularyDao,
    private val jmdictRepository: JMdictRepository
) {
    fun observeAll(): Flow<List<SavedVocabularyWord>> {
        return dao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun saveFromSegmentedWords(words: List<SegmentedWord>) {
        val filtered = words
            .asSequence()
            .filter { !it.isOov }
            .filter { it.surface.length > 1 }
            .distinctBy { it.dictionaryForm }
            .toList()

        val now = System.currentTimeMillis()
        val entities = filtered.map { word ->
            val jlptLevel = runCatching {
                jmdictRepository.getJlptLevel(word.dictionaryForm)
            }.getOrNull()

            val definition = runCatching {
                jmdictRepository.lookupWord(word.dictionaryForm)
                    .firstOrNull()
                    ?.glosses
                    ?.joinToString("; ")
            }.getOrNull()

            SavedVocabularyEntity(
                dictionaryForm = word.dictionaryForm,
                surface = word.surface,
                reading = word.reading,
                jlptLevel = jlptLevel,
                definition = definition,
                savedAt = now
            )
        }

        dao.upsertAll(entities)
    }

    suspend fun delete(dictionaryForm: String) {
        dao.delete(dictionaryForm)
    }

    private fun SavedVocabularyEntity.toDomain(): SavedVocabularyWord {
        return SavedVocabularyWord(
            surface = surface,
            reading = reading,
            dictionaryForm = dictionaryForm,
            jlptLevel = jlptLevel,
            definition = definition,
            savedAt = savedAt
        )
    }
}

