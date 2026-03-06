package com.dstranslator.data.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.dstranslator.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Japanese text-to-speech playback with dual-engine support.
 *
 * Dispatches to either:
 * - **Bundled** (default): On-device Kokoro TTS via sherpa-onnx, zero-setup, random voice per playback
 * - **System**: Android system TTS with user-selectable voice
 *
 * Falls back to system TTS if bundled engine fails to initialize.
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private var tts: TextToSpeech? = null
    private var sherpaEngine: SherpaOnnxTtsEngine? = null

    /** Current TTS engine type */
    var currentEngineType: EngineType = EngineType.BUNDLED
        private set

    /** Whether the TTS engine has been initialized successfully */
    var isInitialized: Boolean = false
        private set

    /** Whether a Japanese TTS voice is available (always true when bundled engine is active) */
    var isJapaneseAvailable: Boolean = false
        private set

    /**
     * TTS engine type: bundled Kokoro via sherpa-onnx or Android system TTS.
     */
    enum class EngineType {
        BUNDLED, SYSTEM;

        companion object {
            fun fromString(value: String): EngineType =
                if (value.equals("system", ignoreCase = true)) SYSTEM else BUNDLED
        }
    }

    /**
     * Initialize the TTS engine. Reads the user's engine type preference and
     * initializes the appropriate engine.
     *
     * Bundled engine is initialized asynchronously on a background thread.
     * System TTS is always initialized as fallback.
     */
    fun initialize() {
        // Read engine type preference
        val engineTypePref = runBlocking { settingsRepository.getTtsEngineType() }
        currentEngineType = EngineType.fromString(engineTypePref)
        Log.i(TAG, "TTS engine type: $currentEngineType")

        // Always initialize bundled engine (for fallback and availability)
        initializeBundledEngine()

        // Always initialize system TTS as fallback
        initializeSystemTts()
    }

    /**
     * Initialize the bundled sherpa-onnx Kokoro engine on a background thread.
     */
    private fun initializeBundledEngine() {
        val engine = SherpaOnnxTtsEngine(context)
        sherpaEngine = engine

        CoroutineScope(Dispatchers.IO).launch {
            try {
                engine.initialize()
                if (engine.isInitialized) {
                    Log.i(TAG, "Bundled TTS engine initialized successfully")
                    // Bundled engine always has Japanese
                    if (currentEngineType == EngineType.BUNDLED) {
                        isJapaneseAvailable = true
                        isInitialized = true
                    }
                } else {
                    Log.w(TAG, "Bundled TTS engine failed to initialize, falling back to system TTS")
                    if (currentEngineType == EngineType.BUNDLED) {
                        currentEngineType = EngineType.SYSTEM
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bundled TTS engine initialization error", e)
                if (currentEngineType == EngineType.BUNDLED) {
                    currentEngineType = EngineType.SYSTEM
                }
            }
        }
    }

    /**
     * Initialize the system TextToSpeech engine.
     */
    private fun initializeSystemTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech

                val langResult = engine.setLanguage(Locale.JAPANESE)
                val systemJapaneseAvailable = langResult != TextToSpeech.LANG_MISSING_DATA &&
                        langResult != TextToSpeech.LANG_NOT_SUPPORTED

                if (currentEngineType == EngineType.SYSTEM) {
                    isJapaneseAvailable = systemJapaneseAvailable
                    isInitialized = true
                }

                if (!systemJapaneseAvailable && currentEngineType == EngineType.SYSTEM) {
                    Log.w(TAG, "Japanese system TTS not available on this device")
                    try {
                        val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(installIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not launch TTS data install", e)
                    }
                }

                // Load saved voice preference for system TTS
                runBlocking {
                    val savedVoiceName = settingsRepository.getTtsVoiceName()
                    if (savedVoiceName != null) {
                        setVoice(savedVoiceName)
                    }
                }
            } else {
                Log.e(TAG, "System TTS initialization failed with status: $status")
                if (currentEngineType == EngineType.SYSTEM) {
                    isInitialized = false
                    isJapaneseAvailable = false
                }
            }
        }
    }

    /**
     * Speak the given Japanese text aloud using the current engine.
     *
     * When using bundled engine: randomly selects male or female voice per call.
     * When using system engine: uses the user's selected voice.
     * Falls back to system TTS if bundled engine fails.
     *
     * @param text The text to speak
     * @param queueMode TextToSpeech.QUEUE_FLUSH (default) or TextToSpeech.QUEUE_ADD
     * @return true if the speak request was submitted, false if TTS is not available
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH): Boolean {
        if (text.isBlank()) return false

        return when (currentEngineType) {
            EngineType.BUNDLED -> {
                val bundledResult = sherpaEngine?.speak(text) ?: false
                if (!bundledResult) {
                    // Fall back to system TTS
                    Log.w(TAG, "Bundled TTS failed, falling back to system TTS")
                    speakWithSystemTts(text, queueMode)
                } else {
                    true
                }
            }
            EngineType.SYSTEM -> {
                speakWithSystemTts(text, queueMode)
            }
        }
    }

    /**
     * Speak using the system TextToSpeech engine.
     */
    private fun speakWithSystemTts(text: String, queueMode: Int): Boolean {
        val engine = tts ?: return false
        if (!isInitialized || text.isBlank()) return false
        val result = engine.speak(text, queueMode, null, UUID.randomUUID().toString())
        return result == TextToSpeech.SUCCESS
    }

    /**
     * Get all available Japanese voices from the system TTS, sorted by name.
     */
    fun getJapaneseVoices(): List<Voice> {
        return tts?.voices
            ?.filter { it.locale.language == Locale.JAPANESE.language }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Set the active system TTS voice by name.
     */
    fun setVoice(voiceName: String) {
        val voice = tts?.voices?.find { it.name == voiceName }
        if (voice != null) {
            tts?.voice = voice
        }
    }

    /**
     * Switch the TTS engine type and persist the preference.
     */
    fun setEngineType(type: EngineType) {
        currentEngineType = type
        runBlocking {
            settingsRepository.setTtsEngineType(
                when (type) {
                    EngineType.BUNDLED -> "bundled"
                    EngineType.SYSTEM -> "system"
                }
            )
        }
        // Update availability based on new engine type
        when (type) {
            EngineType.BUNDLED -> {
                isJapaneseAvailable = sherpaEngine?.isInitialized == true
            }
            EngineType.SYSTEM -> {
                val langResult = tts?.setLanguage(Locale.JAPANESE)
                isJapaneseAvailable = langResult != TextToSpeech.LANG_MISSING_DATA &&
                        langResult != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        Log.i(TAG, "Switched TTS engine to $type, Japanese available: $isJapaneseAvailable")
    }

    /**
     * Stop any currently playing speech and release TTS resources.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        sherpaEngine?.shutdown()
        sherpaEngine = null
        isInitialized = false
        isJapaneseAvailable = false
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
