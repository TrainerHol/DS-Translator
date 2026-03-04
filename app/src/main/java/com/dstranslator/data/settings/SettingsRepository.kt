package com.dstranslator.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dstranslator.domain.model.CaptureRegion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persists user settings. API keys are stored in EncryptedSharedPreferences;
 * all other settings use DataStore Preferences.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // --- Encrypted storage for API keys ---

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    suspend fun getDeepLApiKey(): String? {
        return encryptedPrefs.getString(KEY_DEEPL_API, null)
    }

    suspend fun setDeepLApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_DEEPL_API, key).apply()
    }

    // --- OpenAI API key (encrypted) ---

    suspend fun getOpenAiApiKey(): String? {
        return encryptedPrefs.getString(KEY_OPENAI_API, null)
    }

    suspend fun setOpenAiApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_OPENAI_API, key).apply()
    }

    // --- Claude API key (encrypted) ---

    suspend fun getClaudeApiKey(): String? {
        return encryptedPrefs.getString(KEY_CLAUDE_API, null)
    }

    suspend fun setClaudeApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_CLAUDE_API, key).apply()
    }

    // --- WaniKani API key (encrypted) ---

    suspend fun getWaniKaniApiKey(): String? {
        return encryptedPrefs.getString(KEY_WANIKANI_API, null)
    }

    suspend fun setWaniKaniApiKey(key: String) {
        encryptedPrefs.edit().putString(KEY_WANIKANI_API, key).apply()
    }

    // --- OpenAI configuration (DataStore) ---

    suspend fun getOpenAiBaseUrl(): String? {
        return context.dataStore.data
            .map { prefs -> prefs[PREF_OPENAI_BASE_URL] }
            .first()
    }

    suspend fun setOpenAiBaseUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[PREF_OPENAI_BASE_URL] = url
        }
    }

    suspend fun getOpenAiModel(): String? {
        return context.dataStore.data
            .map { prefs -> prefs[PREF_OPENAI_MODEL] }
            .first()
    }

    suspend fun setOpenAiModel(model: String) {
        context.dataStore.edit { prefs ->
            prefs[PREF_OPENAI_MODEL] = model
        }
    }

    // --- Translation engine selection (DataStore) ---

    suspend fun getTranslationEngine(): String? {
        return context.dataStore.data
            .map { prefs -> prefs[PREF_TRANSLATION_ENGINE] }
            .first()
    }

    suspend fun setTranslationEngine(name: String) {
        context.dataStore.edit { prefs ->
            prefs[PREF_TRANSLATION_ENGINE] = name
        }
    }

    // --- Furigana mode (DataStore) ---

    suspend fun getFuriganaMode(): String {
        return context.dataStore.data
            .map { prefs -> prefs[PREF_FURIGANA_MODE] ?: DEFAULT_FURIGANA_MODE }
            .first()
    }

    suspend fun setFuriganaMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[PREF_FURIGANA_MODE] = mode
        }
    }

    // --- DataStore for other settings ---

    suspend fun getTtsVoiceName(): String? {
        return context.dataStore.data
            .map { prefs -> prefs[PREF_TTS_VOICE] }
            .first()
    }

    suspend fun setTtsVoiceName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[PREF_TTS_VOICE] = name
        }
    }

    suspend fun getOcrEngineName(): String? {
        return context.dataStore.data
            .map { prefs -> prefs[PREF_OCR_ENGINE] }
            .first()
    }

    suspend fun setOcrEngineName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[PREF_OCR_ENGINE] = name
        }
    }

    /**
     * Get the first/default capture region. Returns null if no regions are configured.
     * This is the backward-compatible method used by the single-region pipeline.
     */
    suspend fun getCaptureRegion(): CaptureRegion? {
        // Try multi-region storage first
        val regions = getCaptureRegions()
        if (regions.isNotEmpty()) return regions.first()

        // Fall back to legacy single-region storage
        return context.dataStore.data
            .map { prefs ->
                val x = prefs[PREF_REGION_X]
                val y = prefs[PREF_REGION_Y]
                val w = prefs[PREF_REGION_W]
                val h = prefs[PREF_REGION_H]
                if (x != null && y != null && w != null && h != null) {
                    CaptureRegion(x, y, w, h)
                } else {
                    null
                }
            }
            .first()
    }

    /**
     * Save a single capture region. For backward compatibility, also updates legacy keys.
     */
    suspend fun setCaptureRegion(region: CaptureRegion) {
        context.dataStore.edit { prefs ->
            prefs[PREF_REGION_X] = region.x
            prefs[PREF_REGION_Y] = region.y
            prefs[PREF_REGION_W] = region.width
            prefs[PREF_REGION_H] = region.height
        }
        // Also save to multi-region storage (replaces default region)
        val existing = getCaptureRegions().toMutableList()
        val defaultIndex = existing.indexOfFirst { it.id == "default" || it.id == region.id }
        if (defaultIndex >= 0) {
            existing[defaultIndex] = region.copy(id = region.id.ifBlank { "default" })
        } else {
            existing.add(0, region.copy(id = if (region.id.isBlank()) "default" else region.id))
        }
        saveCaptureRegions(existing)
    }

    /**
     * Get all capture regions. Returns empty list if none configured.
     */
    suspend fun getCaptureRegions(): List<CaptureRegion> {
        val json = context.dataStore.data
            .map { prefs -> prefs[PREF_CAPTURE_REGIONS] }
            .first() ?: return emptyList()

        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                CaptureRegion(
                    x = obj.getInt("x"),
                    y = obj.getInt("y"),
                    width = obj.getInt("width"),
                    height = obj.getInt("height"),
                    id = obj.optString("id", "default"),
                    label = obj.optString("label", ""),
                    autoRead = obj.optBoolean("autoRead", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save all capture regions as a JSON array.
     */
    suspend fun saveCaptureRegions(regions: List<CaptureRegion>) {
        val array = JSONArray()
        regions.forEach { region ->
            val obj = JSONObject().apply {
                put("x", region.x)
                put("y", region.y)
                put("width", region.width)
                put("height", region.height)
                put("id", region.id)
                put("label", region.label)
                put("autoRead", region.autoRead)
            }
            array.put(obj)
        }
        context.dataStore.edit { prefs ->
            prefs[PREF_CAPTURE_REGIONS] = array.toString()
        }
    }

    /**
     * Add a new capture region.
     */
    suspend fun addCaptureRegion(region: CaptureRegion): CaptureRegion {
        val regions = getCaptureRegions().toMutableList()
        val newRegion = region.copy(
            id = if (region.id.isBlank()) java.util.UUID.randomUUID().toString() else region.id
        )
        regions.add(newRegion)
        saveCaptureRegions(regions)
        return newRegion
    }

    /**
     * Remove a capture region by ID.
     */
    suspend fun removeCaptureRegion(regionId: String) {
        val regions = getCaptureRegions().toMutableList()
        regions.removeAll { it.id == regionId }
        saveCaptureRegions(regions)
    }

    suspend fun getFloatingButtonPosition(): Pair<Int, Int>? {
        return context.dataStore.data
            .map { prefs ->
                val x = prefs[PREF_BUTTON_X]
                val y = prefs[PREF_BUTTON_Y]
                if (x != null && y != null) Pair(x, y) else null
            }
            .first()
    }

    suspend fun setFloatingButtonPosition(x: Int, y: Int) {
        context.dataStore.edit { prefs ->
            prefs[PREF_BUTTON_X] = x
            prefs[PREF_BUTTON_Y] = y
        }
    }

    // --- Capture interval settings ---

    suspend fun getCaptureIntervalMs(): Long {
        return context.dataStore.data
            .map { prefs -> prefs[PREF_CAPTURE_INTERVAL] ?: DEFAULT_CAPTURE_INTERVAL_MS }
            .first()
    }

    fun captureIntervalFlow(): Flow<Long> {
        return context.dataStore.data
            .map { prefs -> prefs[PREF_CAPTURE_INTERVAL] ?: DEFAULT_CAPTURE_INTERVAL_MS }
    }

    suspend fun setCaptureIntervalMs(intervalMs: Long) {
        val clamped = intervalMs.coerceIn(MIN_CAPTURE_INTERVAL_MS, MAX_CAPTURE_INTERVAL_MS)
        context.dataStore.edit { prefs ->
            prefs[PREF_CAPTURE_INTERVAL] = clamped
        }
    }

    companion object {
        private const val KEY_DEEPL_API = "deepl_api_key"
        private const val KEY_OPENAI_API = "openai_api_key"
        private const val KEY_CLAUDE_API = "claude_api_key"
        private const val KEY_WANIKANI_API = "wanikani_api_key"

        private val PREF_TTS_VOICE = stringPreferencesKey("tts_voice_name")
        private val PREF_OCR_ENGINE = stringPreferencesKey("ocr_engine_name")
        private val PREF_REGION_X = intPreferencesKey("capture_region_x")
        private val PREF_REGION_Y = intPreferencesKey("capture_region_y")
        private val PREF_REGION_W = intPreferencesKey("capture_region_w")
        private val PREF_REGION_H = intPreferencesKey("capture_region_h")
        private val PREF_BUTTON_X = intPreferencesKey("floating_button_x")
        private val PREF_BUTTON_Y = intPreferencesKey("floating_button_y")
        private val PREF_CAPTURE_INTERVAL = longPreferencesKey("capture_interval_ms")
        private val PREF_OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        private val PREF_OPENAI_MODEL = stringPreferencesKey("openai_model")
        private val PREF_TRANSLATION_ENGINE = stringPreferencesKey("translation_engine")
        private val PREF_FURIGANA_MODE = stringPreferencesKey("furigana_mode")
        private val PREF_CAPTURE_REGIONS = stringPreferencesKey("capture_regions_json")

        const val DEFAULT_FURIGANA_MODE = "all"
        const val DEFAULT_CAPTURE_INTERVAL_MS = 2000L
        const val MIN_CAPTURE_INTERVAL_MS = 500L
        const val MAX_CAPTURE_INTERVAL_MS = 10000L
    }
}
