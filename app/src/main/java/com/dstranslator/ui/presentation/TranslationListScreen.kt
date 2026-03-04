package com.dstranslator.ui.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dstranslator.domain.model.DictionaryResult
import com.dstranslator.domain.model.SegmentedWord
import com.dstranslator.domain.model.TranslationEntry
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Translation list screen rendered on the secondary display via Presentation.
 * Shows a scrollable list of translation entries with auto-scroll to latest.
 *
 * Enhanced with furigana rendering, JLPT badges, and dictionary popup on word tap.
 *
 * @param translations StateFlow of accumulated translation entries to display
 * @param onPlayAudio Callback when user taps the play-audio button on an entry
 * @param onWordLookup Optional callback to look up a word in the dictionary
 */
@Composable
fun TranslationListScreen(
    translations: StateFlow<List<TranslationEntry>>,
    onPlayAudio: (String) -> Unit,
    onWordLookup: (suspend (SegmentedWord) -> List<DictionaryResult>)? = null
) {
    val entries by translations.collectAsState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Dictionary popup state
    var selectedWord by remember { mutableStateOf<SegmentedWord?>(null) }
    var dictionaryResult by remember { mutableStateOf<DictionaryResult?>(null) }
    var showPopup by remember { mutableStateOf(false) }
    var popupOffset by remember { mutableStateOf(IntOffset.Zero) }

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
                        onPlayAudio = onPlayAudio,
                        onWordTap = { word ->
                            if (onWordLookup != null) {
                                selectedWord = word
                                coroutineScope.launch {
                                    val results = onWordLookup(word)
                                    dictionaryResult = results.firstOrNull()
                                    popupOffset = IntOffset(0, 0)
                                    showPopup = true
                                }
                            }
                        }
                    )
                }
            }
        }

        // Dictionary popup overlay
        if (showPopup && selectedWord != null) {
            DictionaryPopup(
                word = selectedWord!!.surface,
                result = dictionaryResult,
                offset = popupOffset,
                onDismiss = {
                    showPopup = false
                    selectedWord = null
                    dictionaryResult = null
                }
            )
        }
    }
}

/**
 * Single translation entry item showing Japanese text with furigana (if available),
 * JLPT badges for segmented words, English translation, and a play-audio icon button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranslationEntryItem(
    entry: TranslationEntry,
    onPlayAudio: (String) -> Unit,
    onWordTap: ((SegmentedWord) -> Unit)? = null
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
                // Japanese text with furigana (if segmented) or plain text
                if (entry.furiganaSegments.isNotEmpty()) {
                    FuriganaText(
                        segments = entry.furiganaSegments,
                        onWordTap = if (onWordTap != null && entry.segmentedWords.isNotEmpty()) {
                            { index ->
                                if (index in entry.segmentedWords.indices) {
                                    onWordTap(entry.segmentedWords[index])
                                }
                            }
                        } else null
                    )
                } else {
                    // Fallback: plain text for entries without segmentation
                    Text(
                        text = entry.japanese,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

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
