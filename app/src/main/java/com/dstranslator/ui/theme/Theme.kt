package com.dstranslator.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    background = DarkBackground,
    surface = DarkSurface,
    primary = TealAccent,
    onBackground = LightText,
    onSurface = LightText,
    error = ErrorRed,
    onError = DarkBackground,
    onPrimary = DarkBackground
)

/**
 * Main dark theme for DS Translator app.
 * Optimized for readability on the AYN Thor bottom screen.
 */
@Composable
fun DsTranslatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = DsTranslatorTypography,
        content = content
    )
}

/**
 * Presentation-specific theme wrapper for the secondary display.
 * Currently uses the same dark theme, but isolated for Presentation context
 * so it can be customized independently if needed.
 */
@Composable
fun DsTranslatorPresentationTheme(content: @Composable () -> Unit) {
    DsTranslatorTheme(content = content)
}
