package com.dstranslator.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dstranslator.domain.model.DictionaryResult
import com.dstranslator.domain.model.PipelineState
import com.dstranslator.domain.model.SegmentedWord
import com.dstranslator.domain.model.TranslationEntry
import com.dstranslator.ui.presentation.TranslationListScreen
import kotlinx.coroutines.flow.StateFlow

/**
 * Primary app screen with start/stop capture controls, pipeline status display,
 * and navigation to settings and region setup.
 *
 * When capturing is active, switches to a compact top bar with an embedded
 * TranslationListScreen showing translations in real-time.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToRegionSetup: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onWordLookup: (suspend (SegmentedWord) -> List<DictionaryResult>)? = null
) {
    val pipelineState by viewModel.pipelineState.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val translationCount by viewModel.translationCount.collectAsState()
    val hasRegion by viewModel.hasRegion.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isCapturing) {
            CapturingLayout(
                pipelineState = pipelineState,
                translationCount = translationCount,
                translations = viewModel.translations,
                onStopCapture = onStopCapture,
                onNavigateToSettings = onNavigateToSettings,
                onPlayAudio = onPlayAudio,
                onWordLookup = onWordLookup
            )
        } else {
            IdleLayout(
                pipelineState = pipelineState,
                translationCount = translationCount,
                hasRegion = hasRegion,
                hasApiKey = hasApiKey,
                onStartCapture = onStartCapture,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToRegionSetup = onNavigateToRegionSetup
            )
        }
    }
}

/**
 * Layout shown when capturing: compact top bar + embedded translation list.
 */
@Composable
private fun CapturingLayout(
    pipelineState: PipelineState,
    translationCount: Int,
    translations: StateFlow<List<TranslationEntry>>,
    onStopCapture: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onWordLookup: (suspend (SegmentedWord) -> List<DictionaryResult>)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Compact top bar
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when (pipelineState) {
                        is PipelineState.ContinuousActive -> "Capturing"
                        is PipelineState.Processing -> "Processing..."
                        is PipelineState.Done -> "Waiting..."
                        else -> "Active"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$translationCount",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(onClick = onNavigateToSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = onStopCapture) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }

        // Embedded translation list fills remaining space
        TranslationListScreen(
            translations = translations,
            onPlayAudio = onPlayAudio,
            onWordLookup = onWordLookup
        )
    }
}

/**
 * Layout shown when idle: full control panel with status, buttons, and warnings.
 */
@Composable
private fun IdleLayout(
    pipelineState: PipelineState,
    translationCount: Int,
    hasRegion: Boolean,
    hasApiKey: Boolean,
    onStartCapture: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRegionSetup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App title
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "DS Translator",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Status section
        StatusSection(pipelineState = pipelineState)

        Spacer(modifier = Modifier.height(16.dp))

        // Translation count
        Text(
            text = "$translationCount translations this session",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Warning chips
        if (!hasApiKey) {
            WarningChip(text = "No API key configured -- using on-device translation")
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (!hasRegion) {
            WarningChip(text = "No capture region set")
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.weight(1f))

        // Primary action button
        FilledTonalButton(
            onClick = onStartCapture,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Capture", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Secondary action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onNavigateToRegionSetup) {
                Icon(
                    imageVector = Icons.Default.CropFree,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Set Region")
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Displays the current pipeline state with appropriate icon/indicator.
 */
@Composable
private fun StatusSection(pipelineState: PipelineState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        when (pipelineState) {
            is PipelineState.Idle -> {
                Text(
                    text = "Ready to capture",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            is PipelineState.Capturing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Capturing...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            is PipelineState.Processing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Processing OCR & Translation...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            is PipelineState.Done -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Translation complete",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            is PipelineState.ContinuousActive -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Continuous capture active",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            is PipelineState.Error -> {
                Text(
                    text = pipelineState.message,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Warning chip showing a configuration issue.
 */
@Composable
private fun WarningChip(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}
