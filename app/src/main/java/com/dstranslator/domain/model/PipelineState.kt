package com.dstranslator.domain.model

/**
 * Represents the current state of the capture-OCR-translate pipeline.
 * Observed by the UI to show progress and results.
 */
sealed class PipelineState {
    /** Pipeline is idle, waiting for capture trigger */
    data object Idle : PipelineState()

    /** Screen capture in progress */
    data object Capturing : PipelineState()

    /** OCR and translation processing */
    data object Processing : PipelineState()

    /** Pipeline completed successfully */
    data object Done : PipelineState()

    /** Pipeline encountered an error */
    data class Error(val message: String) : PipelineState()
}
