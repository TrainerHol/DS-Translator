package com.dstranslator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dstranslator.data.db.ProfileDao
import com.dstranslator.data.db.ProfileEntity
import com.dstranslator.data.db.TranslationHistoryDao
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
 * furigana mode, profile management, and auto-read settings
 * with persistence via SettingsRepository.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ttsManager: TtsManager,
    private val translationManager: TranslationManager,
    private val waniKaniRepository: WaniKaniRepository,
    private val profileDao: ProfileDao,
    private val translationHistoryDao: TranslationHistoryDao
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

    /** All saved profiles (reactive from Room Flow) */
    private val _profiles = MutableStateFlow<List<ProfileEntity>>(emptyList())
    val profiles: StateFlow<List<ProfileEntity>> = _profiles.asStateFlow()

    /** Currently active profile ID */
    private val _activeProfileId = MutableStateFlow<Long?>(null)
    val activeProfileId: StateFlow<Long?> = _activeProfileId.asStateFlow()

    /** Brief status message for profile operations (auto-clears after 2s) */
    private val _profileOperationStatus = MutableStateFlow("")
    val profileOperationStatus: StateFlow<String> = _profileOperationStatus.asStateFlow()

    /** Whether auto-read TTS is enabled */
    private val _autoReadEnabled = MutableStateFlow(false)
    val autoReadEnabled: StateFlow<Boolean> = _autoReadEnabled.asStateFlow()

    /** Auto-read TTS mode: true = flush (interrupt), false = queue */
    private val _autoReadFlushMode = MutableStateFlow(true)
    val autoReadFlushMode: StateFlow<Boolean> = _autoReadFlushMode.asStateFlow()

    /** Whether TTS has Japanese voices available */
    private val _ttsJapaneseAvailable = MutableStateFlow(false)
    val ttsJapaneseAvailable: StateFlow<Boolean> = _ttsJapaneseAvailable.asStateFlow()

    /** Current TTS engine type: "bundled" or "system" */
    private val _ttsEngineType = MutableStateFlow(SettingsRepository.DEFAULT_TTS_ENGINE_TYPE)
    val ttsEngineType: StateFlow<String> = _ttsEngineType.asStateFlow()

    init {
        viewModelScope.launch {
            _deepLApiKey.value = settingsRepository.getDeepLApiKey() ?: ""
            _ttsVoiceName.value = settingsRepository.getTtsVoiceName()
            _ocrEngineName.value = settingsRepository.getOcrEngineName() ?: "ML Kit"
            _captureIntervalMs.value = settingsRepository.getCaptureIntervalMs()
            _translationEngine.value = settingsRepository.getTranslationEngine() ?: "deepl"
            _openAiApiKey.value = settingsRepository.getOpenAiApiKey() ?: ""
            _openAiBaseUrl.value = settingsRepository.getOpenAiBaseUrl() ?: ""
            _openAiModel.value = settingsRepository.getOpenAiModel() ?: ""
            _claudeApiKey.value = settingsRepository.getClaudeApiKey() ?: ""
            _waniKaniApiKey.value = settingsRepository.getWaniKaniApiKey() ?: ""
            _furiganaMode.value = settingsRepository.getFuriganaMode()
            _activeProfileId.value = settingsRepository.getActiveProfileId()
            _autoReadEnabled.value = settingsRepository.getAutoReadEnabled()
            _autoReadFlushMode.value = settingsRepository.getAutoReadFlushMode()
            _ttsEngineType.value = settingsRepository.getTtsEngineType()
        }
        // Collect profiles as Flow (auto-updates on changes)
        viewModelScope.launch {
            profileDao.getAll().collect { _profiles.value = it }
        }
        // Initialize TTS if not already done, then load voices.
        // TTS initialization is async so we need to poll until ready.
        viewModelScope.launch {
            if (!ttsManager.isInitialized) {
                ttsManager.initialize()
            }
            // Poll for TTS initialization (async callback, typically < 1 second)
            var attempts = 0
            while (!ttsManager.isInitialized && attempts < 20) {
                delay(250)
                attempts++
            }
            refreshTtsVoices()
        }
    }

    /**
     * Refresh the available TTS voices list from the TTS engine.
     * Called after TTS initialization or when user wants to re-check.
     */
    fun refreshTtsVoices() {
        _availableVoices.value = ttsManager.getJapaneseVoices().map { it.name }
        _ttsJapaneseAvailable.value = ttsManager.isJapaneseAvailable
    }

    /** Save a new DeepL API key to encrypted storage. */
    fun saveDeepLApiKey(key: String) {
        _deepLApiKey.value = key
        viewModelScope.launch {
            settingsRepository.setDeepLApiKey(key)
        }
    }

    /** Save TTS engine type selection and switch the TTS manager. */
    fun saveTtsEngineType(type: String) {
        _ttsEngineType.value = type
        viewModelScope.launch {
            settingsRepository.setTtsEngineType(type)
            ttsManager.setEngineType(TtsManager.EngineType.fromString(type))
            refreshTtsVoices()
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

    // ========== Profile CRUD Methods ==========

    /**
     * Save current settings as a new named profile.
     * Auto-generates name "Profile N" if no name is provided.
     */
    fun saveAsProfile(name: String? = null) {
        viewModelScope.launch {
            val profileName = name?.takeIf { it.isNotBlank() }
                ?: "Profile ${_profiles.value.size + 1}"

            val settingsSnapshot = settingsRepository.createSettingsSnapshot()
            val regionsSnapshot = settingsRepository.createCaptureRegionsSnapshot()

            val entity = ProfileEntity(
                name = profileName,
                settingsJson = settingsSnapshot,
                captureRegionsJson = regionsSnapshot,
                autoReadEnabled = _autoReadEnabled.value,
                autoReadFlushMode = _autoReadFlushMode.value
            )
            val newId = profileDao.insert(entity)
            settingsRepository.setActiveProfileId(newId)
            _activeProfileId.value = newId
            flashStatus("Profile saved")
        }
    }

    /**
     * Load a profile: atomically restores all settings and refreshes all StateFlows.
     */
    fun loadProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            settingsRepository.loadSettingsFromSnapshot(profile.settingsJson, profile.captureRegionsJson)
            settingsRepository.setAutoReadEnabled(profile.autoReadEnabled)
            settingsRepository.setAutoReadFlushMode(profile.autoReadFlushMode)
            settingsRepository.setActiveProfileId(profile.id)
            refreshAllSettings()
            flashStatus("Profile '${profile.name}' loaded")
        }
    }

    /**
     * Rename a profile.
     */
    fun renameProfile(profile: ProfileEntity, newName: String) {
        viewModelScope.launch {
            profileDao.update(profile.copy(name = newName, updatedAt = System.currentTimeMillis()))
            flashStatus("Profile renamed")
        }
    }

    /**
     * Delete a profile. Cannot delete the Default profile.
     * Optionally deletes associated translation history.
     */
    fun deleteProfile(profile: ProfileEntity, deleteHistory: Boolean) {
        if (profile.isDefault) {
            viewModelScope.launch { flashStatus("Cannot delete Default profile") }
            return
        }
        viewModelScope.launch {
            profileDao.deleteById(profile.id)
            if (deleteHistory) {
                translationHistoryDao.deleteByProfile(profile.id)
            }
            // If this was the active profile, switch to Default
            if (_activeProfileId.value == profile.id) {
                val defaultProfile = profileDao.getDefault()
                if (defaultProfile != null) {
                    loadProfile(defaultProfile)
                }
            }
            flashStatus("Profile deleted")
        }
    }

    // ========== Auto-Read Methods ==========

    /** Save auto-read enabled state. */
    fun saveAutoReadEnabled(enabled: Boolean) {
        _autoReadEnabled.value = enabled
        viewModelScope.launch {
            settingsRepository.setAutoReadEnabled(enabled)
        }
    }

    /** Save auto-read TTS mode. true = flush (interrupt current), false = queue. */
    fun saveAutoReadFlushMode(flush: Boolean) {
        _autoReadFlushMode.value = flush
        viewModelScope.launch {
            settingsRepository.setAutoReadFlushMode(flush)
        }
    }

    // ========== Helpers ==========

    /**
     * Refresh all ViewModel StateFlows by re-reading from SettingsRepository.
     * Called after profile load to ensure all UI state is synchronized.
     */
    private fun refreshAllSettings() {
        viewModelScope.launch {
            _deepLApiKey.value = settingsRepository.getDeepLApiKey() ?: ""
            _ttsVoiceName.value = settingsRepository.getTtsVoiceName()
            _ocrEngineName.value = settingsRepository.getOcrEngineName() ?: "ML Kit"
            _captureIntervalMs.value = settingsRepository.getCaptureIntervalMs()
            _translationEngine.value = settingsRepository.getTranslationEngine() ?: "deepl"
            _openAiApiKey.value = settingsRepository.getOpenAiApiKey() ?: ""
            _openAiBaseUrl.value = settingsRepository.getOpenAiBaseUrl() ?: ""
            _openAiModel.value = settingsRepository.getOpenAiModel() ?: ""
            _claudeApiKey.value = settingsRepository.getClaudeApiKey() ?: ""
            _waniKaniApiKey.value = settingsRepository.getWaniKaniApiKey() ?: ""
            _furiganaMode.value = settingsRepository.getFuriganaMode()
            _autoReadEnabled.value = settingsRepository.getAutoReadEnabled()
            _autoReadFlushMode.value = settingsRepository.getAutoReadFlushMode()
            _activeProfileId.value = settingsRepository.getActiveProfileId()
            _ttsEngineType.value = settingsRepository.getTtsEngineType()
        }
    }

    /**
     * Flash a brief status message that auto-clears after 2 seconds.
     */
    private suspend fun flashStatus(message: String) {
        _profileOperationStatus.value = message
        delay(2000)
        _profileOperationStatus.value = ""
    }
}
