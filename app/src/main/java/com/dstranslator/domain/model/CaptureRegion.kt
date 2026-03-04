package com.dstranslator.domain.model

/**
 * Defines a rectangular region on the game screen for OCR capture.
 * User draws this region during setup; it persists across captures.
 *
 * @param x Left edge in bitmap (screen) coordinates
 * @param y Top edge in bitmap (screen) coordinates
 * @param width Width in pixels
 * @param height Height in pixels
 * @param id Unique identifier for this region (for multi-region support)
 * @param label User-facing label (e.g., "Dialogue", "Menu")
 * @param autoRead Whether captured text in this region should be automatically read aloud via TTS
 */
data class CaptureRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val id: String = "default",
    val label: String = "",
    val autoRead: Boolean = false
)
