package com.dstranslator.data.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-device Japanese TTS engine wrapping sherpa-onnx with the Kokoro multi-lang v1.0 int8 model.
 *
 * Provides zero-setup Japanese TTS with both male and female voices.
 * Voice is randomly selected per playback call for variety.
 *
 * Model files are bundled in assets and copied to filesDir on first launch
 * (same pattern as SudachiSegmenter dictionary).
 */
class SherpaOnnxTtsEngine(private val context: Context) {

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null

    /** Whether the engine has been initialized successfully */
    var isInitialized: Boolean = false
        private set

    // Japanese speaker IDs in kokoro-multi-lang-v1.0
    // Female: 37 (jf_alpha, C+), 38 (jf_gongitsune, C), 39 (jf_nezumi, C-), 40 (jf_tebukuro, C)
    // Male: 41 (jm_kumo, C-)
    val japaneseFemaleIds = listOf(37, 38, 39, 40)
    val japaneseMaleIds = listOf(41)
    val allJapaneseIds = japaneseFemaleIds + japaneseMaleIds

    /**
     * Initialize the TTS engine by copying model files from assets to filesDir
     * and creating the OfflineTts instance.
     *
     * This is IO-bound and may take several seconds on first launch (model copy ~157MB).
     * Subsequent launches only need to load the model (~1-2 seconds).
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        try {
            val modelDir = copyModelToFilesDirIfNeeded()

            val kokoroConfig = OfflineTtsKokoroModelConfig(
                model = "$modelDir/model.int8.onnx",
                voices = "$modelDir/voices.bin",
                tokens = "$modelDir/tokens.txt",
                dataDir = "$modelDir/espeak-ng-data",
                lengthScale = 1.0f
            )

            val modelConfig = OfflineTtsModelConfig(
                kokoro = kokoroConfig,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )

            val ttsConfig = OfflineTtsConfig(
                model = modelConfig,
                maxNumSentences = 1
            )

            tts = OfflineTts(assetManager = null, config = ttsConfig)
            isInitialized = true

            Log.i(TAG, "SherpaOnnxTtsEngine initialized. Speakers: ${tts?.numSpeakers()}, SampleRate: ${tts?.sampleRate()}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SherpaOnnxTtsEngine", e)
            isInitialized = false
        }
    }

    /**
     * Speak the given Japanese text using a randomly selected voice.
     * Each call picks a random speaker from all Japanese voices (male and female).
     *
     * @param text The Japanese text to speak
     * @return true if audio was generated and playback started, false on failure
     */
    fun speak(text: String): Boolean {
        val engine = tts ?: return false
        if (!isInitialized || text.isBlank()) return false

        return try {
            val speakerId = allJapaneseIds.random()
            Log.d(TAG, "Speaking with speaker ID $speakerId: ${text.take(30)}...")

            val audio = engine.generate(text = text, sid = speakerId, speed = 1.0f)
            if (audio.samples.isEmpty()) {
                Log.w(TAG, "Generated empty audio for text: ${text.take(30)}")
                return false
            }

            playAudio(audio)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate/play TTS audio", e)
            false
        }
    }

    /**
     * Play generated audio samples via AudioTrack.
     */
    private fun playAudio(audio: GeneratedAudio) {
        // Stop any currently playing audio
        stopAudio()

        val sampleRate = audio.sampleRate
        val samples = audio.samples

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(bufferSize, samples.size * 4))
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        audioTrack = track
    }

    /**
     * Stop any currently playing audio.
     */
    private fun stopAudio() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore -- track may already be stopped
        }
        audioTrack = null
    }

    /**
     * Release all TTS engine resources.
     */
    fun shutdown() {
        stopAudio()
        tts = null
        isInitialized = false
        Log.i(TAG, "SherpaOnnxTtsEngine shut down")
    }

    /**
     * Copy model files from assets/sherpa-onnx-kokoro/ to filesDir/sherpa-onnx-kokoro/
     * if they haven't been copied already.
     *
     * @return The absolute path to the model directory in filesDir
     */
    private fun copyModelToFilesDirIfNeeded(): String {
        val destDir = File(context.filesDir, MODEL_DIR_NAME)
        val markerFile = File(destDir, ".copied")

        if (markerFile.exists()) {
            return destDir.absolutePath
        }

        Log.i(TAG, "Copying Kokoro model files from assets to filesDir...")
        destDir.mkdirs()

        copyAssetDirectory(ASSET_MODEL_DIR, destDir)

        // Write marker file to indicate successful copy
        markerFile.writeText("v1.0")
        Log.i(TAG, "Model files copied successfully to ${destDir.absolutePath}")

        return destDir.absolutePath
    }

    /**
     * Recursively copy an asset directory to a filesystem directory.
     */
    private fun copyAssetDirectory(assetPath: String, destDir: File) {
        val assets = context.assets.list(assetPath) ?: return

        if (assets.isEmpty()) {
            // It's a file, copy it
            copyAssetFile(assetPath, File(destDir, assetPath.substringAfterLast('/')))
            return
        }

        // It's a directory
        destDir.mkdirs()
        for (child in assets) {
            val childAssetPath = "$assetPath/$child"
            val childDest = File(destDir, child)

            val grandchildren = context.assets.list(childAssetPath)
            if (grandchildren != null && grandchildren.isNotEmpty()) {
                // Subdirectory
                copyAssetDirectory(childAssetPath, childDest)
            } else {
                // File
                copyAssetFile(childAssetPath, childDest)
            }
        }
    }

    /**
     * Copy a single asset file to a destination file.
     */
    private fun copyAssetFile(assetPath: String, destFile: File) {
        destFile.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }
    }

    companion object {
        private const val TAG = "SherpaOnnxTtsEngine"
        private const val ASSET_MODEL_DIR = "sherpa-onnx-kokoro"
        private const val MODEL_DIR_NAME = "sherpa-onnx-kokoro"
    }
}
