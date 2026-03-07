package com.dstranslator.data.segmentation

import android.content.Context
import android.content.res.AssetManager
import com.dstranslator.domain.model.SegmentedWord
import com.worksap.nlp.sudachi.Config
import com.worksap.nlp.sudachi.Dictionary
import com.worksap.nlp.sudachi.DictionaryFactory
import com.worksap.nlp.sudachi.Tokenizer
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Word segmentation service wrapping the Sudachi morphological analyzer.
 *
 * Sudachi requires a dictionary file on the filesystem (not in Android assets)
 * because it uses memory-mapped files (MappedByteBuffer) for efficient access.
 * On first launch, the dictionary is copied from assets to internal storage.
 *
 * The dictionary file (system_core.dic) is bundled in the APK under
 * assets/sudachi/. It ships with the app so no external download is needed.
 *
 * Usage:
 * 1. Call initialize() once at app startup (async, copies dictionary if needed)
 * 2. Call segment() to split Japanese text into individual words
 * 3. Call close() when done (app shutdown) to release resources
 */
@Singleton
class SudachiSegmenter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var dictionary: Dictionary? = null
    private var tokenizer: Tokenizer? = null
    private var initializationInProgress = false
    private var initFailCount = 0

    companion object {
        private const val TAG = "SudachiSegmenter"
        private const val MAX_INIT_RETRIES = 3
    }

    /**
     * Initialize the Sudachi dictionary by copying it from assets to internal
     * storage (if not already present) and creating the tokenizer.
     *
     * This is an IO-bound operation that may take 1-3 seconds on first launch
     * (dictionary copy) and ~500ms on subsequent launches (dictionary load).
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val dictDir = File(context.filesDir, "sudachi")
        val dictFile = File(dictDir, "system_core.dic")

        if (!dictFile.exists()) {
            dictDir.mkdirs()
            val tmpFile = File(dictDir, "system_core.dic.tmp")
            if (tmpFile.exists()) {
                tmpFile.delete()
            }

            val assetManager = context.assets
            val legacyAssetPath = "sudachi/system_core.dic"

            if (assetExists(assetManager, legacyAssetPath)) {
                assetManager.open(legacyAssetPath).use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                val partAssets = findSplitGzipAssets(assetManager)
                if (partAssets.isEmpty()) {
                    throw IllegalStateException(
                        "Sudachi dictionary assets missing. Expected either " +
                            "\"$legacyAssetPath\" or split parts under assets/sudachi/ " +
                            "named \"system_core.dic.gz.000\", \".001\", ..."
                    )
                }

                val concatStream = AssetConcatInputStream(
                    assetManager = assetManager,
                    assetPaths = partAssets.map { "sudachi/$it" }
                )

                GZIPInputStream(BufferedInputStream(concatStream)).use { gzipInput ->
                    tmpFile.outputStream().use { output ->
                        gzipInput.copyTo(output)
                    }
                }
            }

            if (!tmpFile.renameTo(dictFile)) {
                dictFile.outputStream().use { output ->
                    tmpFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
                tmpFile.delete()
            }
        }

        // Write minimal sudachi.json config pointing to the copied dictionary
        val configFile = File(dictDir, "sudachi.json")
        if (!configFile.exists()) {
            configFile.writeText("""{"systemDict": "system_core.dic"}""")
        }

        val config = Config.fromFile(configFile.toPath())
        val createdDictionary = DictionaryFactory().create(config)
        dictionary = createdDictionary
        tokenizer = createdDictionary.create()
    }

    private fun assetExists(assetManager: AssetManager, assetPath: String): Boolean {
        return try {
            assetManager.open(assetPath).use { }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun findSplitGzipAssets(assetManager: AssetManager): List<String> {
        val prefix = "system_core.dic.gz."
        val sudachiAssets = assetManager.list("sudachi")?.toList().orEmpty()

        return sudachiAssets
            .asSequence()
            .filter { it.startsWith(prefix) }
            .filter { it.length == prefix.length + 3 }
            .filter { name ->
                name.substring(prefix.length).all { it.isDigit() }
            }
            .sortedBy { name ->
                name.substring(prefix.length).toInt()
            }
            .toList()
    }

    private class AssetConcatInputStream(
        private val assetManager: AssetManager,
        private val assetPaths: List<String>
    ) : InputStream() {
        private var currentIndex = 0
        private var currentStream: InputStream? = null

        override fun read(): Int {
            while (true) {
                val stream = ensureCurrentStream() ?: return -1
                val value = stream.read()
                if (value != -1) return value
                closeCurrentStream()
            }
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            while (true) {
                val stream = ensureCurrentStream() ?: return -1
                val count = stream.read(b, off, len)
                if (count != -1) return count
                closeCurrentStream()
            }
        }

        override fun close() {
            closeCurrentStream()
        }

        private fun ensureCurrentStream(): InputStream? {
            if (currentStream != null) return currentStream
            if (currentIndex >= assetPaths.size) return null
            currentStream = assetManager.open(assetPaths[currentIndex])
            return currentStream
        }

        private fun closeCurrentStream() {
            currentStream?.close()
            currentStream = null
            currentIndex++
        }
    }

    /**
     * Whether the segmenter has been successfully initialized and is ready for use.
     */
    val isInitialized: Boolean get() = tokenizer != null

    /**
     * Ensure the segmenter is initialized, retrying if needed (up to [MAX_INIT_RETRIES] attempts).
     * This allows lazy initialization on first use if the startup init was too early or failed.
     *
     * @return true if the segmenter is ready for use, false otherwise
     */
    suspend fun ensureInitialized(): Boolean {
        if (isInitialized) return true
        if (initFailCount >= MAX_INIT_RETRIES) return false
        if (initializationInProgress) return false

        return try {
            initializationInProgress = true
            initialize()
            isInitialized
        } catch (e: Exception) {
            initFailCount++
            Log.w(TAG, "Sudachi initialization attempt failed (${initFailCount}/$MAX_INIT_RETRIES)", e)
            false
        } finally {
            initializationInProgress = false
        }
    }

    /**
     * Segment Japanese text into individual words/morphemes.
     *
     * @param text The Japanese text to segment
     * @param mode Sudachi split mode:
     *   - Mode.A: finest granularity (individual words, best for dictionary lookup)
     *   - Mode.B: middle granularity
     *   - Mode.C: coarsest granularity (compound expressions)
     * @return List of [SegmentedWord] for each morpheme in the text
     * @throws IllegalStateException if [initialize] has not been called
     */
    fun segment(
        text: String,
        mode: Tokenizer.SplitMode = Tokenizer.SplitMode.A
    ): List<SegmentedWord> {
        val tok = tokenizer
            ?: throw IllegalStateException(
                "SudachiSegmenter not initialized. Call initialize() first."
            )
        if (text.isBlank()) return emptyList()

        return tok.tokenize(mode, text).map { morpheme ->
            SegmentedWord(
                surface = morpheme.surface(),
                reading = morpheme.readingForm(),
                dictionaryForm = morpheme.dictionaryForm(),
                partOfSpeech = morpheme.partOfSpeech(),
                isOov = morpheme.isOOV
            )
        }
    }

    /**
     * Release dictionary resources. Safe to call even if not initialized.
     * After close(), [isInitialized] returns false and [segment] will throw.
     */
    fun close() {
        dictionary?.close()
        dictionary = null
        tokenizer = null
    }
}
