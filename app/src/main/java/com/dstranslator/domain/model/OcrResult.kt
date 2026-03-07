package com.dstranslator.domain.model

/**
 * Bundles OCR text blocks with the coordinate metadata needed to map bounding boxes
 * from preprocessed-image space back to screen coordinates.
 *
 * Published by CaptureService after each OCR pass; consumed by overlay-on-source mode
 * to position translation labels at the correct screen positions.
 *
 * @param textBlocks All text blocks detected in this OCR pass.
 * @param captureRegion The capture region used (null for full-screen capture).
 * @param preprocessedWidth Width of the preprocessed bitmap fed to OCR (may be upscaled).
 * @param preprocessedHeight Height of the preprocessed bitmap fed to OCR (may be upscaled).
 */
data class OcrResult(
    val displayId: Int? = null,
    val textBlocks: List<OcrTextBlock>,
    val captureRegion: CaptureRegion?,
    val preprocessedWidth: Int,
    val preprocessedHeight: Int
)
