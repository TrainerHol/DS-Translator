package com.dstranslator.domain.model

/**
 * Per-screen overlay configuration stored per profile.
 *
 * [panelWidth] and [panelHeight] use `0` as a sentinel value meaning
 * "compute from display metrics at runtime". This sentinel is resolved in
 * OverlayDisplayManager.showPanel() where display metrics are available,
 * using [DEFAULT_WIDTH_FRACTION] and [DEFAULT_HEIGHT_FRACTION].
 *
 * Once the user explicitly resizes the panel, the pixel values are stored
 * and the sentinel is never used again for that profile/screen combination.
 *
 * @param panelX Left position of the panel overlay window.
 * @param panelY Top position of the panel overlay window.
 * @param panelWidth Width in pixels (0 = sentinel for fraction-based default).
 * @param panelHeight Height in pixels (0 = sentinel for fraction-based default).
 * @param panelAlpha Overlay window alpha (0.0 fully transparent, 1.0 fully opaque).
 * @param textSizeSp Text size in SP for translation text in the panel.
 * @param isPinned Whether the panel stays open when user taps outside.
 * @param isLocked Whether the panel is in lock mode (all touches pass through).
 */
data class OverlayConfig(
    val panelX: Int = 100,
    val panelY: Int = 100,
    val panelWidth: Int = 0,
    val panelHeight: Int = 0,
    val panelAlpha: Float = 0.85f,
    val textSizeSp: Int = 14,
    val isPinned: Boolean = false,
    val isLocked: Boolean = false
) {
    companion object {
        /** Fraction of display width used when panelWidth is 0 (sentinel). */
        const val DEFAULT_WIDTH_FRACTION = 0.35f
        /** Fraction of display height used when panelHeight is 0 (sentinel). */
        const val DEFAULT_HEIGHT_FRACTION = 0.20f
    }

    /**
     * Resolve sentinel values (0) to actual pixel sizes based on display metrics.
     * Call this in OverlayDisplayManager.showPanel() where display size is known.
     *
     * 35% width x 20% height gives a compact panel covering roughly 20% of visible
     * screen per user decision "Default size: small (~20% of screen)".
     */
    fun resolveForDisplay(displayWidth: Int, displayHeight: Int): OverlayConfig {
        return copy(
            panelWidth = if (panelWidth == 0) (displayWidth * DEFAULT_WIDTH_FRACTION).toInt() else panelWidth,
            panelHeight = if (panelHeight == 0) (displayHeight * DEFAULT_HEIGHT_FRACTION).toInt() else panelHeight
        )
    }
}
