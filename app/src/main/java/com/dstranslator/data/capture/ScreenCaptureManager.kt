package com.dstranslator.data.capture

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.view.Display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages MediaProjection session and ImageReader for screen capture.
 * Wraps the Android MediaProjection API to provide a simple screenshot acquisition interface.
 *
 * Uses VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR to mirror the device's default display content.
 * The app's own window uses FLAG_SECURE to exclude itself from capture, preventing
 * a self-translation feedback loop.
 */
@Singleton
class ScreenCaptureManager @Inject constructor() {

    private data class CaptureTarget(
        val imageReader: ImageReader,
        val virtualDisplay: VirtualDisplay,
        val screenWidth: Int,
        val screenHeight: Int
    )

    private val targets = mutableMapOf<Int, CaptureTarget>()

    /**
     * Set up the ImageReader and VirtualDisplay for screen capture.
     *
     * @param mediaProjection The MediaProjection obtained from user consent
     * @param width Screen width in pixels
     * @param height Screen height in pixels
     * @param density Screen density in DPI
     * @param displayId Optional display ID to target. Pass null to use default display.
     *                  Note: MediaProjection always captures the default display content;
     *                  this parameter is reserved for future multi-display capture support.
     */
    fun setup(mediaProjection: MediaProjection, width: Int, height: Int, density: Int, displayId: Int? = null) {
        val targetDisplayId = displayId ?: Display.DEFAULT_DISPLAY
        targets[targetDisplayId]?.let { existing ->
            existing.virtualDisplay.release()
            existing.imageReader.close()
        }

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "DSTranslator-$targetDisplayId",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface,
            null,
            null
        )

        targets[targetDisplayId] = CaptureTarget(
            imageReader = imageReader,
            virtualDisplay = virtualDisplay,
            screenWidth = width,
            screenHeight = height
        )
    }

    fun hasTarget(displayId: Int): Boolean {
        return targets.containsKey(displayId)
    }

    /**
     * Acquire a screenshot from the virtual display.
     * Handles Image-to-Bitmap conversion with correct row padding.
     *
     * @return The captured Bitmap, or null if acquisition fails
     */
    suspend fun acquireScreenshot(displayId: Int? = null): Bitmap? = withContext(Dispatchers.IO) {
        val targetDisplayId = displayId ?: Display.DEFAULT_DISPLAY
        val target = targets[targetDisplayId] ?: return@withContext null
        val reader = target.imageReader
        var image: android.media.Image? = null
        try {
            image = reader.acquireLatestImage() ?: return@withContext null

            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * target.screenWidth

            // Create bitmap with padding included, then crop to actual size
            val bitmapWidth = target.screenWidth + rowPadding / pixelStride
            val paddedBitmap = Bitmap.createBitmap(bitmapWidth, target.screenHeight, Bitmap.Config.ARGB_8888)
            paddedBitmap.copyPixelsFromBuffer(buffer)

            // Crop to actual screen width (remove row padding)
            if (bitmapWidth != target.screenWidth) {
                val cropped = Bitmap.createBitmap(paddedBitmap, 0, 0, target.screenWidth, target.screenHeight)
                paddedBitmap.recycle()
                cropped
            } else {
                paddedBitmap
            }
        } catch (e: Exception) {
            null
        } finally {
            image?.close()
        }
    }

    /**
     * Release the virtual display and image reader resources.
     */
    fun release() {
        for ((_, target) in targets) {
            target.virtualDisplay.release()
            target.imageReader.close()
        }
        targets.clear()
    }
}
