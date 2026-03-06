package com.dstranslator.ui.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dstranslator.data.dictionary.JMdictRepository
import com.dstranslator.data.tts.TtsManager
import com.dstranslator.domain.model.TranslationEntry
import com.dstranslator.service.CaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A single vocabulary word extracted from session translations.
 */
data class VocabularyWord(
    val surface: String,
    val reading: String,
    val dictionaryForm: String,
    val jlptLevel: Int?,
    val definition: String?
)

/**
 * ViewModel for the session vocabulary screen.
 *
 * Collects translations from CaptureService, extracts segmented words,
 * filters out OOV and single-character words, deduplicates by dictionary form,
 * and enriches each word with JLPT level and definition from JMdict.
 */
@HiltViewModel
class VocabularyViewModel @Inject constructor(
    private val jmdictRepository: JMdictRepository,
    private val ttsManager: TtsManager
) : ViewModel() {

    private val _sessionWords = MutableStateFlow<List<VocabularyWord>>(emptyList())
    val sessionWords: StateFlow<List<VocabularyWord>> = _sessionWords.asStateFlow()

    init {
        viewModelScope.launch {
            CaptureService.translations.collect { translations ->
                _sessionWords.value = extractVocabulary(translations)
            }
        }
    }

    /**
     * Extract unique vocabulary words from translation entries.
     * Filters OOV and single-char words, deduplicates by dictionaryForm (first occurrence wins).
     */
    internal suspend fun extractVocabulary(translations: List<TranslationEntry>): List<VocabularyWord> {
        val seen = mutableSetOf<String>()
        val words = mutableListOf<VocabularyWord>()

        for (entry in translations) {
            for (word in entry.segmentedWords) {
                // Filter out OOV words and single-character words
                if (word.isOov) continue
                if (word.surface.length <= 1) continue

                // Deduplicate by dictionary form (first occurrence wins for chronological order)
                if (!seen.add(word.dictionaryForm)) continue

                // Look up JLPT level and definition
                val jlptLevel = try {
                    jmdictRepository.getJlptLevel(word.dictionaryForm)
                } catch (_: Exception) { null }

                val definition = try {
                    val results = jmdictRepository.lookupWord(word.dictionaryForm)
                    results.firstOrNull()?.glosses?.joinToString("; ")
                } catch (_: Exception) { null }

                words.add(
                    VocabularyWord(
                        surface = word.surface,
                        reading = word.reading,
                        dictionaryForm = word.dictionaryForm,
                        jlptLevel = jlptLevel,
                        definition = definition
                    )
                )
            }
        }

        return words
    }

    /**
     * Play audio for the given text using TTS.
     */
    fun playAudio(text: String) {
        ttsManager.speak(text)
    }
}
