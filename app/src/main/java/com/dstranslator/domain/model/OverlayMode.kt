package com.dstranslator.domain.model

/**
 * The three overlay display states.
 *
 * - [Off]: No overlay visible; translations on Presentation display only.
 * - [OverlayOnSource]: Semi-transparent translation labels positioned at OCR bounding boxes on the game screen.
 * - [Panel]: Floating, resizable, scrollable translation panel overlay on the game screen.
 */
enum class OverlayMode {
    Off,
    OverlayOnSource,
    Panel;

    companion object {
        /**
         * Deserialize from a stored string. Returns [Off] for unrecognized values,
         * providing backward compatibility when settings contain no overlay mode.
         */
        fun fromString(s: String): OverlayMode {
            return entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: Off
        }
    }
}
