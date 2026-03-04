package com.dstranslator.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Settings screen with translation engine configuration, API keys, TTS voice picker,
 * OCR engine selector, WaniKani integration, and furigana mode toggle.
 * All settings are persisted via SettingsRepository on change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val deepLApiKey by viewModel.deepLApiKey.collectAsState()
    val ttsVoiceName by viewModel.ttsVoiceName.collectAsState()
    val ocrEngineName by viewModel.ocrEngineName.collectAsState()
    val availableVoices by viewModel.availableVoices.collectAsState()
    val captureIntervalMs by viewModel.captureIntervalMs.collectAsState()
    val cacheCleared by viewModel.cacheCleared.collectAsState()
    val translationEngine by viewModel.translationEngine.collectAsState()
    val openAiApiKey by viewModel.openAiApiKey.collectAsState()
    val openAiBaseUrl by viewModel.openAiBaseUrl.collectAsState()
    val openAiModel by viewModel.openAiModel.collectAsState()
    val claudeApiKey by viewModel.claudeApiKey.collectAsState()
    val waniKaniApiKey by viewModel.waniKaniApiKey.collectAsState()
    val waniKaniSyncStatus by viewModel.waniKaniSyncStatus.collectAsState()
    val furiganaMode by viewModel.furiganaMode.collectAsState()

    var deepLKeyVisible by remember { mutableStateOf(false) }
    var openAiKeyVisible by remember { mutableStateOf(false) }
    var claudeKeyVisible by remember { mutableStateOf(false) }
    var waniKaniKeyVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // ===== Translation Engine Section =====
                Text(
                    text = "Translation Engine",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                TranslationEngineDropdown(
                    selectedEngine = translationEngine,
                    onEngineSelected = { viewModel.saveTranslationEngine(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // DeepL API Key
                ApiKeyField(
                    value = deepLApiKey,
                    onValueChange = { viewModel.saveDeepLApiKey(it) },
                    label = "DeepL API Key",
                    visible = deepLKeyVisible,
                    onToggleVisibility = { deepLKeyVisible = !deepLKeyVisible }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ===== OpenAI Configuration Section =====
                Text(
                    text = "OpenAI-Compatible Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "For OpenAI, Ollama, LM Studio, or any compatible API",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                ApiKeyField(
                    value = openAiApiKey,
                    onValueChange = { viewModel.saveOpenAiApiKey(it) },
                    label = "OpenAI API Key",
                    visible = openAiKeyVisible,
                    onToggleVisibility = { openAiKeyVisible = !openAiKeyVisible }
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = openAiBaseUrl,
                    onValueChange = { viewModel.saveOpenAiBaseUrl(it) },
                    label = { Text("Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = openAiModel,
                    onValueChange = { viewModel.saveOpenAiModel(it) },
                    label = { Text("Model") },
                    placeholder = { Text("gpt-4o-mini") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ===== Claude Configuration Section =====
                Text(
                    text = "Claude Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                ApiKeyField(
                    value = claudeApiKey,
                    onValueChange = { viewModel.saveClaudeApiKey(it) },
                    label = "Claude API Key",
                    visible = claudeKeyVisible,
                    onToggleVisibility = { claudeKeyVisible = !claudeKeyVisible }
                )

                Spacer(modifier = Modifier.height(32.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(24.dp))

                // ===== WaniKani Section =====
                Text(
                    text = "WaniKani",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Connect your WaniKani account to show furigana only for kanji you haven't learned yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(12.dp))

                ApiKeyField(
                    value = waniKaniApiKey,
                    onValueChange = { viewModel.saveWaniKaniApiKey(it) },
                    label = "WaniKani API Key",
                    visible = waniKaniKeyVisible,
                    onToggleVisibility = { waniKaniKeyVisible = !waniKaniKeyVisible }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { viewModel.syncWaniKani() },
                        enabled = waniKaniApiKey.isNotBlank() && waniKaniSyncStatus != "Syncing..."
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text("Sync Now")
                    }
                    if (waniKaniSyncStatus.isNotBlank()) {
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = waniKaniSyncStatus,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (waniKaniSyncStatus.startsWith("Sync failed"))
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ===== Furigana Mode Section =====
                Text(
                    text = "Furigana Mode",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Controls when furigana (reading hints) are shown above kanji",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                FuriganaModeSelector(
                    selectedMode = furiganaMode,
                    onModeSelected = { viewModel.saveFuriganaMode(it) }
                )

                Spacer(modifier = Modifier.height(32.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(24.dp))

                // ===== TTS Voice Section =====
                Text(
                    text = "Text-to-Speech",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (availableVoices.isEmpty()) {
                    Text(
                        text = "No Japanese voices available. TTS engine may not be initialized.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                } else {
                    VoiceDropdown(
                        selectedVoice = ttsVoiceName,
                        voices = availableVoices,
                        onVoiceSelected = { viewModel.saveTtsVoice(it) }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ===== OCR Engine Section =====
                Text(
                    text = "OCR Engine",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OcrEngineDropdown(
                    selectedEngine = ocrEngineName,
                    onEngineSelected = { viewModel.saveOcrEngine(it) }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ===== Capture Section =====
                Text(
                    text = "Capture",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Capture interval: ${"%.1f".format(captureIntervalMs / 1000f)}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "How often the app captures and checks for new dialog in continuous mode",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Slider(
                    value = captureIntervalMs.toFloat(),
                    onValueChange = { viewModel.saveCaptureInterval(it.toLong()) },
                    valueRange = 500f..10000f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ===== Cache Section =====
                Text(
                    text = "Cache",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.clearTranslationCache() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(if (cacheCleared) "Cache Cleared!" else "Clear Translation Cache")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Removes all cached translations. Future translations will require API calls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Reusable API key text field with toggle visibility.
 */
@Composable
private fun ApiKeyField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) {
                        Icons.Default.Visibility
                    } else {
                        Icons.Default.VisibilityOff
                    },
                    contentDescription = if (visible) "Hide" else "Show"
                )
            }
        }
    )
}

/**
 * Translation engine dropdown selector.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationEngineDropdown(
    selectedEngine: String,
    onEngineSelected: (String) -> Unit
) {
    val engines = listOf(
        "deepl" to "DeepL",
        "openai" to "OpenAI-Compatible",
        "claude" to "Claude"
    )
    var expanded by remember { mutableStateOf(false) }
    val displayName = engines.find { it.first == selectedEngine }?.second ?: "DeepL"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Translation Engine") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            engines.forEach { (key, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onEngineSelected(key)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Furigana mode radio group selector.
 */
@Composable
private fun FuriganaModeSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val modes = listOf(
        "all" to "All" to "Show furigana above all kanji",
        "none" to "None" to "Never show furigana",
        "wanikani" to "WaniKani-aware" to "Only show furigana for kanji you haven't learned on WaniKani"
    )

    Column(modifier = Modifier.selectableGroup()) {
        modes.forEach { (modeAndLabel, description) ->
            val (mode, label) = modeAndLabel
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = (selectedMode == mode),
                        onClick = { onModeSelected(mode) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (selectedMode == mode),
                    onClick = null // handled by selectable modifier
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Dropdown for TTS voice selection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceDropdown(
    selectedVoice: String?,
    voices: List<String>,
    onVoiceSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedVoice ?: "Select a voice",
            onValueChange = {},
            readOnly = true,
            label = { Text("Japanese Voice") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            voices.forEach { voice ->
                DropdownMenuItem(
                    text = { Text(voice) },
                    onClick = {
                        onVoiceSelected(voice)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Dropdown for OCR engine selection. Phase 1 only has ML Kit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrEngineDropdown(
    selectedEngine: String,
    onEngineSelected: (String) -> Unit
) {
    val engines = listOf("ML Kit")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedEngine,
            onValueChange = {},
            readOnly = true,
            label = { Text("OCR Engine") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            engines.forEach { engine ->
                DropdownMenuItem(
                    text = { Text(engine) },
                    onClick = {
                        onEngineSelected(engine)
                        expanded = false
                    }
                )
            }
        }
    }
}
