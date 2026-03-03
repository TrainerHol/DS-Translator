package com.dstranslator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the settings screen. Manages DeepL API key, TTS voice selection,
 * and OCR engine selection with persistence via SettingsRepository.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsManager: TtsManager
) : ViewModel() {

    /** Current DeepL API key (loaded from encrypted storage) */
    private val _deepLApiKey = MutableStateFlow("")
    val deepLApiKey: StateFlow<String> = _deepLApiKey.asStateFlow()

    /** Currently selected TTS voice name */
    private val _ttsVoiceName = MutableStateFlow<String?>(null)
    val ttsVoiceName: StateFlow<String?> = _ttsVoiceName.asStateFlow()

    /** Currently selected OCR engine name */
    private val _ocrEngineName = MutableStateFlow("ML Kit")
    val ocrEngineName: StateFlow<String> = _ocrEngineName.asStateFlow()

    /** Available Japanese TTS voices */
    private val _availableVoices = MutableStateFlow<List<String>>(emptyList())
    val availableVoices: StateFlow<List<String>> = _availableVoices.asStateFlow()

    init {
        viewModelScope.launch {
            _deepLApiKey.value = settingsRepository.getDeepLApiKey() ?: ""
            _ttsVoiceName.value = settingsRepository.getTtsVoiceName()
            _ocrEngineName.value = settingsRepository.getOcrEngineName() ?: "ML Kit"
            _availableVoices.value = ttsManager.getJapaneseVoices().map { it.name }
        }
    }

    /** Save a new DeepL API key to encrypted storage. */
    fun saveDeepLApiKey(key: String) {
        _deepLApiKey.value = key
        viewModelScope.launch {
            settingsRepository.setDeepLApiKey(key)
        }
    }

    /** Save a TTS voice selection and apply it to the TTS engine. */
    fun saveTtsVoice(voiceName: String) {
        _ttsVoiceName.value = voiceName
        viewModelScope.launch {
            settingsRepository.setTtsVoiceName(voiceName)
            ttsManager.setVoice(voiceName)
        }
    }

    /** Save an OCR engine selection. */
    fun saveOcrEngine(name: String) {
        _ocrEngineName.value = name
        viewModelScope.launch {
            settingsRepository.setOcrEngineName(name)
        }
    }
}
