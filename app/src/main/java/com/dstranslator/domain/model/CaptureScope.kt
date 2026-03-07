package com.dstranslator.domain.model

enum class CaptureScope {
    Primary,
    Secondary,
    Both;

    companion object {
        fun fromString(s: String): CaptureScope {
            return entries.firstOrNull { it.name.equals(s, ignoreCase = true) } ?: Both
        }
    }
}

