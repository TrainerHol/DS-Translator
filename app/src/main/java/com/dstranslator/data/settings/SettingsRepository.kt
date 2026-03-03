package com.dstranslator.data.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dstranslator.domain.model.CaptureRegion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    suspend fun getCaptureRegion(): CaptureRegion? {
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

    suspend fun setCaptureRegion(region: CaptureRegion) {
        context.dataStore.edit { prefs ->
            prefs[PREF_REGION_X] = region.x
            prefs[PREF_REGION_Y] = region.y
            prefs[PREF_REGION_W] = region.width
            prefs[PREF_REGION_H] = region.height
        }
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

    companion object {
        private const val KEY_DEEPL_API = "deepl_api_key"

        private val PREF_TTS_VOICE = stringPreferencesKey("tts_voice_name")
        private val PREF_OCR_ENGINE = stringPreferencesKey("ocr_engine_name")
        private val PREF_REGION_X = intPreferencesKey("capture_region_x")
        private val PREF_REGION_Y = intPreferencesKey("capture_region_y")
        private val PREF_REGION_W = intPreferencesKey("capture_region_w")
        private val PREF_REGION_H = intPreferencesKey("capture_region_h")
        private val PREF_BUTTON_X = intPreferencesKey("floating_button_x")
        private val PREF_BUTTON_Y = intPreferencesKey("floating_button_y")
    }
}
