package com.dstranslator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.translation.TranslationManager
import com.dstranslator.data.tts.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
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
    private val ttsManager: TtsManager,
    private val translationManager: TranslationManager
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

    /** Current capture interval in milliseconds */
    private val _captureIntervalMs = MutableStateFlow(SettingsRepository.DEFAULT_CAPTURE_INTERVAL_MS)
    val captureIntervalMs: StateFlow<Long> = _captureIntervalMs.asStateFlow()

    /** Whether cache was recently cleared (for UI feedback) */
    private val _cacheCleared = MutableStateFlow(false)
    val cacheCleared: StateFlow<Boolean> = _cacheCleared.asStateFlow()

    init {
        viewModelScope.launch {
            _deepLApiKey.value = settingsRepository.getDeepLApiKey() ?: ""
            _ttsVoiceName.value = settingsRepository.getTtsVoiceName()
            _ocrEngineName.value = settingsRepository.getOcrEngineName() ?: "ML Kit"
            _availableVoices.value = ttsManager.getJapaneseVoices().map { it.name }
            _captureIntervalMs.value = settingsRepository.getCaptureIntervalMs()
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

    /** Save a new capture interval and persist it to settings. */
    fun saveCaptureInterval(intervalMs: Long) {
        _captureIntervalMs.value = intervalMs
        viewModelScope.launch {
            settingsRepository.setCaptureIntervalMs(intervalMs)
        }
    }

    /** Clear the translation cache (both in-memory LRU and Room). Shows brief confirmation. */
    fun clearTranslationCache() {
        viewModelScope.launch {
            translationManager.clearCache()
            _cacheCleared.value = true
            // Reset after 2 seconds
            delay(2000)
            _cacheCleared.value = false
        }
    }
}
