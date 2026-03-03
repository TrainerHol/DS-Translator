package com.dstranslator.data.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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

    /**
     * Initialize the TTS engine. Sets language to Japanese and loads the
     * user's saved voice preference if available.
     */
    fun initialize() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.JAPANESE
                isInitialized = true

                // Load saved voice preference
                runBlocking {
                    val savedVoiceName = settingsRepository.getTtsVoiceName()
                    if (savedVoiceName != null) {
                        setVoice(savedVoiceName)
                    }
                }
            }
        }
    }

    /**
     * Speak the given Japanese text aloud. Flushes any currently playing speech.
     */
    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
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
    }
}
