package com.dstranslator.ui.presentation

import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.dstranslator.domain.model.DictionaryResult
import com.dstranslator.domain.model.SegmentedWord
import com.dstranslator.domain.model.TranslationEntry
import com.dstranslator.ui.theme.DsTranslatorPresentationTheme
import kotlinx.coroutines.flow.StateFlow

/**
 * Presentation subclass that renders the translation list UI on the secondary display.
 * Uses Compose via ComposeView for the dark-themed translation list.
 *
 * Lifecycle is tied to CaptureService -- created when service starts, dismissed when service stops.
 *
 * @param context The service context
 * @param display The secondary display (AYN Thor bottom screen)
 * @param translations StateFlow of accumulated translation entries to display
 * @param onPlayAudio Callback when user taps play-audio on a translation entry
 * @param onWordLookup Callback to look up a word in the dictionary, returns DictionaryResult
 */
class TranslationPresentation(
    context: Context,
    display: Display,
    private val translations: StateFlow<List<TranslationEntry>>,
    private val onPlayAudio: (String) -> Unit,
    private val onWordLookup: (suspend (SegmentedWord) -> List<DictionaryResult>)? = null
) : android.app.Presentation(context, display) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent this window from appearing in MediaProjection captures.
        // Without this, the translator OCRs its own translation list and loops.
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val composeView = ComposeView(context).apply {
            setContent {
                DsTranslatorPresentationTheme {
                    TranslationListScreen(
                        translations = translations,
                        onPlayAudio = onPlayAudio,
                        onWordLookup = onWordLookup
                    )
                }
            }
        }

        // Set up ViewTree owners required by ComposeView
        val lifecycleOwner = PresentationLifecycleOwner()
        lifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        lifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        lifecycleOwner.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)

        composeView.setViewTreeLifecycleOwner(lifecycleOwner)
        composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

        setContentView(composeView)
    }
}
