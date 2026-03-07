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
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatListBulleted
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
import com.dstranslator.domain.model.PipelineState

/**
 * Primary app screen with start/stop capture controls, pipeline status display,
 * and navigation to settings and session captures.
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToVocabulary: () -> Unit,
    onNavigateToSavedVocabulary: () -> Unit,
    onStartCapture: () -> Unit,
    onStopCapture: () -> Unit
) {
    val pipelineState by viewModel.pipelineState.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val translationCount by viewModel.translationCount.collectAsState()
    val hasApiKey by viewModel.hasApiKey.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (isCapturing) {
            CapturingLayout(
                pipelineState = pipelineState,
                translationCount = translationCount,
                onStopCapture = onStopCapture,
                onNavigateToVocabulary = onNavigateToVocabulary,
                onNavigateToSavedVocabulary = onNavigateToSavedVocabulary,
                onNavigateToSettings = onNavigateToSettings,
            )
        } else {
            IdleLayout(
                pipelineState = pipelineState,
                translationCount = translationCount,
                hasApiKey = hasApiKey,
                onStartCapture = onStartCapture,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToVocabulary = onNavigateToVocabulary,
                onNavigateToSavedVocabulary = onNavigateToSavedVocabulary
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
    onStopCapture: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToVocabulary: () -> Unit,
    onNavigateToSavedVocabulary: () -> Unit
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

                IconButton(onClick = onNavigateToVocabulary) {
                    Icon(
                        imageVector = Icons.Default.FormatListBulleted,
                        contentDescription = "Captures",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onNavigateToSavedVocabulary) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Saved vocabulary",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Capturing… Open Captures to review.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Layout shown when idle: full control panel with status, buttons, and warnings.
 * When translations from a previous session exist, shows them below the controls
 * so the user can review vocabulary learned during the session.
 */
@Composable
private fun IdleLayout(
    pipelineState: PipelineState,
    translationCount: Int,
    hasApiKey: Boolean,
    onStartCapture: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToVocabulary: () -> Unit,
    onNavigateToSavedVocabulary: () -> Unit
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

        Spacer(modifier = Modifier.height(24.dp))

        // Status section
        StatusSection(pipelineState = pipelineState)

        Spacer(modifier = Modifier.height(8.dp))

        // Translation count
        if (translationCount > 0) {
            Text(
                text = "$translationCount translations from last session",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Warning chips
        if (!hasApiKey) {
            WarningChip(text = "No API key configured -- using on-device translation")
            Spacer(modifier = Modifier.height(8.dp))
        }

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

        Spacer(modifier = Modifier.height(12.dp))

        // Secondary action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(onClick = onNavigateToVocabulary) {
                Icon(
                    imageVector = Icons.Default.FormatListBulleted,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Captures")
            }
            FilledTonalButton(onClick = onNavigateToSavedVocabulary) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Saved")
            }
            IconButton(onClick = onNavigateToSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))
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
