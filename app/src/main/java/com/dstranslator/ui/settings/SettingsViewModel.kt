package com.dstranslator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.data.translation.TranslationManager
import com.dstranslator.data.tts.TtsManager
import com.dstranslator.data.wanikani.WaniKaniRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the settings screen. Manages DeepL API key, TTS voice selection,
 * OCR engine selection, translation engine selection, OpenAI/Claude/WaniKani config,
 * and furigana mode with persistence via SettingsRepository.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsManager: TtsManager,
    private val translationManager: TranslationManager,
    private val waniKaniRepository: WaniKaniRepository
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

    /** Currently selected translation engine */
    private val _translationEngine = MutableStateFlow("deepl")
    val translationEngine: StateFlow<String> = _translationEngine.asStateFlow()

    /** OpenAI API key */
    private val _openAiApiKey = MutableStateFlow("")
    val openAiApiKey: StateFlow<String> = _openAiApiKey.asStateFlow()

    /** OpenAI base URL */
    private val _openAiBaseUrl = MutableStateFlow("")
    val openAiBaseUrl: StateFlow<String> = _openAiBaseUrl.asStateFlow()

    /** OpenAI model name */
    private val _openAiModel = MutableStateFlow("")
    val openAiModel: StateFlow<String> = _openAiModel.asStateFlow()

    /** Claude API key */
    private val _claudeApiKey = MutableStateFlow("")
    val claudeApiKey: StateFlow<String> = _claudeApiKey.asStateFlow()

    /** WaniKani API key */
    private val _waniKaniApiKey = MutableStateFlow("")
    val waniKaniApiKey: StateFlow<String> = _waniKaniApiKey.asStateFlow()

    /** WaniKani sync status */
    private val _waniKaniSyncStatus = MutableStateFlow("")
    val waniKaniSyncStatus: StateFlow<String> = _waniKaniSyncStatus.asStateFlow()

    /** Furigana mode: "all", "none", "wanikani" */
    private val _furiganaMode = MutableStateFlow("all")
    val furiganaMode: StateFlow<String> = _furiganaMode.asStateFlow()

    init {
        viewModelScope.launch {
            _deepLApiKey.value = settingsRepository.getDeepLApiKey() ?: ""
            _ttsVoiceName.value = settingsRepository.getTtsVoiceName()
            _ocrEngineName.value = settingsRepository.getOcrEngineName() ?: "ML Kit"
            _availableVoices.value = ttsManager.getJapaneseVoices().map { it.name }
            _captureIntervalMs.value = settingsRepository.getCaptureIntervalMs()
            _translationEngine.value = settingsRepository.getTranslationEngine() ?: "deepl"
            _openAiApiKey.value = settingsRepository.getOpenAiApiKey() ?: ""
            _openAiBaseUrl.value = settingsRepository.getOpenAiBaseUrl() ?: ""
            _openAiModel.value = settingsRepository.getOpenAiModel() ?: ""
            _claudeApiKey.value = settingsRepository.getClaudeApiKey() ?: ""
            _waniKaniApiKey.value = settingsRepository.getWaniKaniApiKey() ?: ""
            _furiganaMode.value = settingsRepository.getFuriganaMode()
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

    /** Save translation engine selection. */
    fun saveTranslationEngine(engine: String) {
        _translationEngine.value = engine
        viewModelScope.launch {
            settingsRepository.setTranslationEngine(engine)
        }
    }

    /** Save OpenAI API key. */
    fun saveOpenAiApiKey(key: String) {
        _openAiApiKey.value = key
        viewModelScope.launch {
            settingsRepository.setOpenAiApiKey(key)
        }
    }

    /** Save OpenAI base URL. */
    fun saveOpenAiBaseUrl(url: String) {
        _openAiBaseUrl.value = url
        viewModelScope.launch {
            settingsRepository.setOpenAiBaseUrl(url)
        }
    }

    /** Save OpenAI model name. */
    fun saveOpenAiModel(model: String) {
        _openAiModel.value = model
        viewModelScope.launch {
            settingsRepository.setOpenAiModel(model)
        }
    }

    /** Save Claude API key. */
    fun saveClaudeApiKey(key: String) {
        _claudeApiKey.value = key
        viewModelScope.launch {
            settingsRepository.setClaudeApiKey(key)
        }
    }

    /** Save WaniKani API key. */
    fun saveWaniKaniApiKey(key: String) {
        _waniKaniApiKey.value = key
        viewModelScope.launch {
            settingsRepository.setWaniKaniApiKey(key)
        }
    }

    /** Trigger WaniKani sync. */
    fun syncWaniKani() {
        _waniKaniSyncStatus.value = "Syncing..."
        viewModelScope.launch {
            try {
                waniKaniRepository.syncAssignments()
                _waniKaniSyncStatus.value = "Sync complete"
                delay(3000)
                _waniKaniSyncStatus.value = ""
            } catch (e: Exception) {
                _waniKaniSyncStatus.value = "Sync failed: ${e.message}"
                delay(5000)
                _waniKaniSyncStatus.value = ""
            }
        }
    }

    /** Save furigana mode. */
    fun saveFuriganaMode(mode: String) {
        _furiganaMode.value = mode
        viewModelScope.launch {
            settingsRepository.setFuriganaMode(mode)
        }
    }
}
