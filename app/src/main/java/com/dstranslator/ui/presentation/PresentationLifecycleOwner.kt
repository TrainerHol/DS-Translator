package com.dstranslator.ui.presentation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Minimal LifecycleOwner and SavedStateRegistryOwner for ComposeView in non-Activity contexts.
 *
 * Presentation and WindowManager overlay windows don't inherently provide these,
 * but ComposeView requires them for recomposition lifecycle management.
 *
 * Used by:
 * - [TranslationPresentation] for secondary-display Compose UI
 * - OverlayDisplayManager for overlay panel ComposeView (Phase 5)
 */
class PresentationLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController =
        SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }
}
