package com.dstranslator.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dstranslator.data.dictionary.JMdictRepository
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.tts.TtsManager
import com.dstranslator.domain.model.DictionaryResult
import com.dstranslator.domain.model.PipelineState
import com.dstranslator.domain.model.SegmentedWord
import com.dstranslator.domain.model.TranslationEntry
import com.dstranslator.service.CaptureService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the main screen. Exposes pipeline state, translations, and
 * configuration status (region set, API key configured) for the UI.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsManager: TtsManager,
    private val jmdictRepository: JMdictRepository
) : ViewModel() {

    /** Current pipeline state from CaptureService companion */
    val pipelineState: StateFlow<PipelineState> = CaptureService.pipelineState

    /** Accumulated translations from CaptureService companion */
    val translations: StateFlow<List<TranslationEntry>> = CaptureService.translations

    /** Whether the pipeline is actively running (not idle) */
    val isCapturing: StateFlow<Boolean> = pipelineState
        .map { it !is PipelineState.Idle }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** Number of translations in the current session */
    val translationCount: StateFlow<Int> = translations
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Whether a capture region has been configured */
    private val _hasRegion = MutableStateFlow(false)
    val hasRegion: StateFlow<Boolean> = _hasRegion.asStateFlow()

    /** Whether a DeepL API key has been configured */
    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    init {
        viewModelScope.launch {
            _hasRegion.value = settingsRepository.getCaptureRegion() != null
            _hasApiKey.value = !settingsRepository.getDeepLApiKey().isNullOrBlank()
        }
    }

    /** Play Japanese text aloud via TTS */
    fun onPlayAudio(text: String) {
        ttsManager.speak(text)
    }

    /** Look up a segmented word in the dictionary */
    suspend fun onWordLookup(word: SegmentedWord): List<DictionaryResult> {
        return jmdictRepository.lookupWord(word.dictionaryForm)
    }
}
