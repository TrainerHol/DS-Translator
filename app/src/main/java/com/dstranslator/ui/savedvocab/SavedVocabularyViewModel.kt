package com.dstranslator.ui.savedvocab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dstranslator.data.tts.TtsManager
import com.dstranslator.data.vocabulary.SavedVocabularyRepository
import com.dstranslator.data.vocabulary.SavedVocabularyWord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SavedVocabularyViewModel @Inject constructor(
    private val repository: SavedVocabularyRepository,
    private val ttsManager: TtsManager
) : ViewModel() {

    val words: StateFlow<List<SavedVocabularyWord>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun playAudio(text: String) {
        ttsManager.speak(text)
    }

    fun delete(dictionaryForm: String) {
        viewModelScope.launch {
            repository.delete(dictionaryForm)
        }
    }
}

