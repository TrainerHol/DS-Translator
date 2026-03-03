package com.dstranslator.domain.model

/**
 * Defines a rectangular region on the game screen for OCR capture.
 * User draws this region during setup; it persists across captures.
 */
data class CaptureRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)
