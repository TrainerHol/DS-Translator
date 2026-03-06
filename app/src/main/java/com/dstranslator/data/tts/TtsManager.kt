package com.dstranslator.data.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.dstranslator.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.runBlocking
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Japanese text-to-speech playback and voice selection.
 * Supports listing available Japanese voices and persisting the user's choice.
 *
 * Detects whether a Japanese TTS voice is available on the device and exposes
 * [isJapaneseAvailable] for the UI to show guidance if no voice is installed.
 */
@Singleton
class TtsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    private var tts: TextToSpeech? = null

    /** Whether the TTS engine has been initialized successfully */
    var isInitialized: Boolean = false
        private set

    /** Whether a Japanese TTS voice is available on this device */
    var isJapaneseAvailable: Boolean = false
        private set

    /**
     * Initialize the TTS engine. Sets language to Japanese and loads the
     * user's saved voice preference if available.
     *
     * After initialization, checks whether Japanese TTS is actually available
     * and sets [isJapaneseAvailable] accordingly. If no Japanese voice exists,
     * TTS speak calls will silently fail -- the UI should check this flag and
     * guide the user to install a TTS engine (e.g., Google TTS from Play Store).
     */
    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val langResult = tts?.setLanguage(Locale.JAPANESE)
                isJapaneseAvailable = langResult != TextToSpeech.LANG_MISSING_DATA &&
                        langResult != TextToSpeech.LANG_NOT_SUPPORTED
                isInitialized = true

                if (!isJapaneseAvailable) {
                    Log.w(TAG, "Japanese TTS not available on this device. " +
                            "Install Google TTS or another engine with Japanese support.")
                    // Try to trigger TTS data download
                    try {
                        val installIntent = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(installIntent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not launch TTS data install", e)
                    }
                } else {
                    Log.d(TAG, "Japanese TTS initialized successfully")
                }

                // Load saved voice preference
                runBlocking {
                    val savedVoiceName = settingsRepository.getTtsVoiceName()
                    if (savedVoiceName != null) {
                        setVoice(savedVoiceName)
                    }
                }
            } else {
                Log.e(TAG, "TTS initialization failed with status: $status")
                isInitialized = false
                isJapaneseAvailable = false
            }
        }
    }

    /**
     * Speak the given Japanese text aloud.
     * @param text The text to speak
     * @param queueMode TextToSpeech.QUEUE_FLUSH (default, interrupts current speech)
     *   or TextToSpeech.QUEUE_ADD (queues after current speech)
     * @return true if the speak request was submitted, false if TTS is not available
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_FLUSH): Boolean {
        val engine = tts
        if (engine == null) {
            Log.w(TAG, "TTS speak called but engine is null (not initialized)")
            return false
        }
        if (!isInitialized) {
            Log.w(TAG, "TTS speak called but engine is not yet initialized")
            return false
        }
        if (text.isBlank()) {
            Log.d(TAG, "TTS speak called with blank text, ignoring")
            return false
        }
        val result = engine.speak(text, queueMode, null, UUID.randomUUID().toString())
        if (result != TextToSpeech.SUCCESS) {
            Log.e(TAG, "TTS speak failed with result code: $result for text: ${text.take(50)}")
            return false
        }
        Log.d(TAG, "TTS speaking: ${text.take(50)}...")
        return true
    }

    /**
     * Get all available Japanese voices, sorted by name.
     */
    fun getJapaneseVoices(): List<Voice> {
        return tts?.voices
            ?.filter { it.locale.language == Locale.JAPANESE.language }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    /**
     * Set the active TTS voice by name. Looks up the voice in the available voices
     * and applies it to the TTS engine.
     */
    fun setVoice(voiceName: String) {
        val voice = tts?.voices?.find { it.name == voiceName }
        if (voice != null) {
            tts?.voice = voice
        }
    }

    /**
     * Stop any currently playing speech and release TTS resources.
     */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        isJapaneseAvailable = false
    }

    companion object {
        private const val TAG = "TtsManager"
    }
}
