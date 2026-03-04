package com.dstranslator.data.segmentation

import android.content.Context
import com.dstranslator.domain.model.SegmentedWord
import com.worksap.nlp.sudachi.Config
import com.worksap.nlp.sudachi.Dictionary
import com.worksap.nlp.sudachi.DictionaryFactory
import com.worksap.nlp.sudachi.Tokenizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Word segmentation service wrapping the Sudachi morphological analyzer.
 *
 * Sudachi requires a dictionary file on the filesystem (not in Android assets)
 * because it uses memory-mapped files (MappedByteBuffer) for efficient access.
 * On first launch, the dictionary is copied from assets to internal storage.
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
            context.assets.open("sudachi/system_core.dic").use { input ->
                dictFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        // Write minimal sudachi.json config pointing to the copied dictionary
        val configFile = File(dictDir, "sudachi.json")
        if (!configFile.exists()) {
            configFile.writeText("""{"systemDict": "system_core.dic"}""")
        }

        val config = Config.fromFile(configFile.toPath())
        dictionary = DictionaryFactory().create(config)
        tokenizer = dictionary!!.create()
    }

    /**
     * Whether the segmenter has been successfully initialized and is ready for use.
     */
    val isInitialized: Boolean get() = tokenizer != null

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
