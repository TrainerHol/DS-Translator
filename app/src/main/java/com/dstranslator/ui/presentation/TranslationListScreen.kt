package com.dstranslator.ui.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dstranslator.domain.model.TranslationEntry
import kotlinx.coroutines.flow.StateFlow

/**
 * Translation list screen rendered on the secondary display via Presentation.
 * Shows a scrollable list of translation entries with auto-scroll to latest.
 *
 * @param translations StateFlow of accumulated translation entries
 * @param onPlayAudio Callback when user taps the play-audio button on an entry
 */
@Composable
fun TranslationListScreen(
    translations: StateFlow<List<TranslationEntry>>,
    onPlayAudio: (String) -> Unit
) {
    val entries by translations.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll to latest entry when new translations arrive
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (entries.isEmpty()) {
            // Empty state
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Tap the capture button to translate",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                state = listState
            ) {
                items(
                    items = entries,
                    key = { it.id }
                ) { entry ->
                    TranslationEntryItem(
                        entry = entry,
                        onPlayAudio = onPlayAudio
                    )
                }
            }
        }
    }
}

/**
 * Single translation entry item showing Japanese text (large, prominent),
 * English translation (smaller, below), and a play-audio icon button.
 */
@Composable
fun TranslationEntryItem(
    entry: TranslationEntry,
    onPlayAudio: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Text column
            Column(modifier = Modifier.weight(1f)) {
                // Japanese text - large and prominent
                Text(
                    text = entry.japanese,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                // English translation - smaller, dimmed
                Text(
                    text = entry.english,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            // Play audio button
            IconButton(onClick = { onPlayAudio(entry.japanese) }) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Play audio",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Thin divider for visual separation
        HorizontalDivider(
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
            thickness = 0.5.dp
        )
    }
}
