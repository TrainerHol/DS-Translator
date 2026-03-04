package com.dstranslator.ui.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.dstranslator.domain.model.DictionaryResult

/**
 * Speech-bubble-style dictionary popup that displays word definitions.
 *
 * Shows:
 * - Word in large text (dictionary form or surface form)
 * - Kana reading
 * - Part of speech tags
 * - JLPT level badge (via JlptIndicator)
 * - English definitions as a bulleted list
 *
 * Dismissible by tapping outside the popup.
 *
 * @param word The surface form of the tapped word
 * @param result Dictionary lookup result (null shows "No definition found")
 * @param offset Position offset for the popup
 * @param onDismiss Callback when popup is dismissed
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DictionaryPopup(
    word: String,
    result: DictionaryResult?,
    offset: IntOffset = IntOffset.Zero,
    onDismiss: () -> Unit
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .widthIn(min = 200.dp, max = 320.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                if (result != null) {
                    // Word header row with JLPT badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dictionary form or first kanji writing
                        Text(
                            text = result.kanji.firstOrNull() ?: word,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // JLPT badge
                        JlptIndicator(level = result.jlptLevel)
                    }

                    // Kana reading
                    if (result.kana.isNotEmpty()) {
                        Text(
                            text = result.kana.first(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Part of speech tags
                    if (result.partOfSpeech.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            result.partOfSpeech.take(3).forEach { pos ->
                                Text(
                                    text = pos,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // English definitions
                    result.glosses.forEachIndexed { index, gloss ->
                        Text(
                            text = "${index + 1}. $gloss",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    // No result found
                    Text(
                        text = word,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "No definition found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
