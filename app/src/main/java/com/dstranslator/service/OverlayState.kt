package com.dstranslator.service

import com.dstranslator.domain.model.OverlayMode

/**
 * Touch interaction states for overlay windows.
 *
 * Each state determines which WindowManager.LayoutParams flags are applied
 * to overlay windows to control touch event routing.
 */
enum class TouchState {
    /** Bubble only visible; all overlay areas pass through touches to the game. */
    FULL_PASSTHROUGH,

    /** Panel is open and interactive; panel consumes its area, rest passes through. */
    PANEL_INTERACTIVE,

    /** Panel is visible but locked; all touches pass through everywhere including the panel. */
    PANEL_LOCKED,

    /** Overlay-on-source labels are interactive (tappable for word detail). */
    SOURCE_INTERACTIVE
}

/**
 * Provides the correct WindowManager.LayoutParams flag combinations for each [TouchState].
 *
 * Uses integer constants directly (matching Android framework values) to allow
 * pure unit testing without Android framework dependency.
 */
object TouchStateFlags {
    // Android WindowManager.LayoutParams flag constants
    private const val FLAG_NOT_FOCUSABLE = 0x00000008
    private const val FLAG_NOT_TOUCHABLE = 0x00000010
    private const val FLAG_SECURE = 0x00002000

    /**
     * Get the combined WindowManager flags for the given touch state.
     *
     * All states include FLAG_SECURE to prevent OCR feedback loop.
     * All states include FLAG_NOT_FOCUSABLE to prevent stealing game focus.
     *
     * - [TouchState.FULL_PASSTHROUGH]: + FLAG_NOT_TOUCHABLE (everything passes through)
     * - [TouchState.PANEL_INTERACTIVE]: no FLAG_NOT_TOUCHABLE (panel consumes touches)
     * - [TouchState.PANEL_LOCKED]: + FLAG_NOT_TOUCHABLE (panel visible but non-interactive)
     * - [TouchState.SOURCE_INTERACTIVE]: no FLAG_NOT_TOUCHABLE (labels are tappable)
     */
    fun getFlagsForState(state: TouchState): Int {
        return when (state) {
            TouchState.FULL_PASSTHROUGH -> FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE or FLAG_SECURE
            TouchState.PANEL_INTERACTIVE -> FLAG_NOT_FOCUSABLE or FLAG_SECURE
            TouchState.PANEL_LOCKED -> FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE or FLAG_SECURE
            TouchState.SOURCE_INTERACTIVE -> FLAG_NOT_FOCUSABLE or FLAG_SECURE
        }
    }
}

/**
 * Describes a transition between two overlay modes, indicating whether
 * cleanup of the previous mode's views is required.
 */
data class OverlayTransition(
    val from: OverlayMode,
    val to: OverlayMode,
    val cleanupNeeded: Boolean
)

/**
 * State machine for overlay mode transitions.
 *
 * Determines what cleanup is needed when switching between overlay modes.
 * Cleanup means removing all overlay views (labels, panel, tooltips) from the previous mode
 * before creating views for the new mode.
 *
 * Rules:
 * - Same-to-same transition is a no-op (no cleanup needed).
 * - Transition from Off to any mode: no cleanup needed (nothing to remove).
 * - Transition from any active mode (OverlayOnSource, Panel): cleanup needed.
 */
object OverlayStateMachine {

    /**
     * Compute the transition from [current] mode to [next] mode.
     *
     * @return An [OverlayTransition] describing whether cleanup of [current] is needed.
     */
    fun transition(current: OverlayMode, next: OverlayMode): OverlayTransition {
        val cleanupNeeded = current != next && current != OverlayMode.Off
        return OverlayTransition(from = current, to = next, cleanupNeeded = cleanupNeeded)
    }
}
