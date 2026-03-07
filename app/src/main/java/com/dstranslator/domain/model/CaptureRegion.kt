package com.dstranslator.domain.model

import kotlin.math.ceil
import kotlin.math.floor

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
 * @param normalizedX Left edge in normalized [0..1] coordinates (relative to the full captured bitmap width)
 * @param normalizedY Top edge in normalized [0..1] coordinates (relative to the full captured bitmap height)
 * @param normalizedWidth Width in normalized [0..1] units
 * @param normalizedHeight Height in normalized [0..1] units
 */
data class CaptureRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val id: String = "default",
    val label: String = "",
    val autoRead: Boolean = false,
    val normalizedX: Float? = null,
    val normalizedY: Float? = null,
    val normalizedWidth: Float? = null,
    val normalizedHeight: Float? = null
)

data class PixelRect(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

fun CaptureRegion.hasNormalizedRect(): Boolean {
    return normalizedX != null &&
        normalizedY != null &&
        normalizedWidth != null &&
        normalizedHeight != null
}

fun CaptureRegion.resolveToPixelRect(
    bitmapWidth: Int,
    bitmapHeight: Int
): PixelRect {
    if (bitmapWidth <= 0 || bitmapHeight <= 0) {
        return PixelRect(x = 0, y = 0, width = 1, height = 1)
    }

    val nx = normalizedX
    val ny = normalizedY
    val nw = normalizedWidth
    val nh = normalizedHeight

    if (nx == null || ny == null || nw == null || nh == null) {
        val clampedX = x.coerceIn(0, bitmapWidth - 1)
        val clampedY = y.coerceIn(0, bitmapHeight - 1)
        val clampedWidth = width.coerceIn(1, bitmapWidth - clampedX)
        val clampedHeight = height.coerceIn(1, bitmapHeight - clampedY)
        return PixelRect(clampedX, clampedY, clampedWidth, clampedHeight)
    }

    val leftNorm = nx.coerceIn(0f, 1f).toDouble()
    val topNorm = ny.coerceIn(0f, 1f).toDouble()
    val rightNorm = (nx + nw).coerceIn(0f, 1f).toDouble()
    val bottomNorm = (ny + nh).coerceIn(0f, 1f).toDouble()

    val leftPx = floor(leftNorm * bitmapWidth).toInt().coerceIn(0, bitmapWidth - 1)
    val topPx = floor(topNorm * bitmapHeight).toInt().coerceIn(0, bitmapHeight - 1)

    val rightPx = ceil(rightNorm * bitmapWidth).toInt().coerceIn(leftPx + 1, bitmapWidth)
    val bottomPx = ceil(bottomNorm * bitmapHeight).toInt().coerceIn(topPx + 1, bitmapHeight)

    return PixelRect(
        x = leftPx,
        y = topPx,
        width = (rightPx - leftPx).coerceAtLeast(1),
        height = (bottomPx - topPx).coerceAtLeast(1)
    )
}

fun CaptureRegion.resolveForBitmap(bitmapWidth: Int, bitmapHeight: Int): CaptureRegion {
    val rect = resolveToPixelRect(bitmapWidth, bitmapHeight)
    return copy(x = rect.x, y = rect.y, width = rect.width, height = rect.height)
}
