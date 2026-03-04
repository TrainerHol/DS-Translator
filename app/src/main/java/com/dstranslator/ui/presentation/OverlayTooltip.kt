package com.dstranslator.ui.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.dstranslator.domain.model.DictionaryResult
import com.dstranslator.domain.model.SegmentedWord

/**
 * Lightweight Compose tooltip for word taps in overlay mode.
 *
 * Shows a compact popup near the tapped word with:
 * - Word surface form + reading
 * - JLPT level badge (color-coded)
 * - First definition (truncated to 2 lines)
 * - Audio play button
 *
 * Dismisses on tap outside the popup.
 *
 * @param word The tapped segmented word
 * @param dictionaryResult Dictionary lookup result (null shows basic word info only)
 * @param jlptLevel JLPT level from dictionary or WaniKani data
 * @param onPlayAudio Callback to play TTS audio for the word
 * @param onDismiss Callback when tooltip is dismissed
 * @param modifier Modifier for the tooltip
 */
@Composable
fun OverlayTooltip(
    word: SegmentedWord,
    dictionaryResult: DictionaryResult?,
    jlptLevel: Int?,
    onPlayAudio: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = modifier
                .widthIn(min = 160.dp, max = 280.dp),
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xE6202020) // Semi-transparent dark background
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Top row: word + JLPT badge + audio button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Word surface form
                    Text(
                        text = dictionaryResult?.kanji?.firstOrNull() ?: word.surface,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // JLPT badge
                    val effectiveJlpt = jlptLevel ?: dictionaryResult?.jlptLevel
                    JlptIndicator(level = effectiveJlpt)

                    // Audio button
                    IconButton(
                        onClick = { onPlayAudio(word.surface) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Play audio",
                            tint = Color(0xFF4DD0E1), // Teal accent
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Reading (kana)
                val reading = dictionaryResult?.kana?.firstOrNull() ?: word.reading
                if (reading.isNotBlank() && reading != word.surface) {
                    Text(
                        text = reading,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                }

                // First definition (truncated to 2 lines)
                if (dictionaryResult != null && dictionaryResult.glosses.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dictionaryResult.glosses.first(),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No definition found",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
