package com.dstranslator.ui.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Color-coded JLPT level badge composable.
 *
 * Displays a small rounded badge showing "N5" through "N1" with
 * color-coded background indicating difficulty level:
 * - N5 (Green): Beginner
 * - N4 (Light Blue): Elementary
 * - N3 (Orange): Intermediate
 * - N2 (Deep Purple): Upper Intermediate
 * - N1 (Red): Advanced
 *
 * Shows nothing if level is null or out of range (1-5).
 *
 * @param level JLPT level (1-5) or null for unclassified
 * @param modifier Modifier for the badge
 */
@Composable
fun JlptIndicator(level: Int?, modifier: Modifier = Modifier) {
    if (level == null || level !in 1..5) return

    val (color, label) = when (level) {
        5 -> Color(0xFF4CAF50) to "N5"
        4 -> Color(0xFF03A9F4) to "N4"
        3 -> Color(0xFFFF9800) to "N3"
        2 -> Color(0xFF673AB7) to "N2"
        1 -> Color(0xFFF44336) to "N1"
        else -> return
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = color
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
