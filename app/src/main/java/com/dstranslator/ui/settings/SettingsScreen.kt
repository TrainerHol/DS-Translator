package com.dstranslator.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.dstranslator.data.db.ProfileEntity
import kotlinx.coroutines.delay

/**
 * Settings screen with game profiles, translation engine configuration, API keys,
 * TTS voice picker, auto-read settings, OCR engine selector, WaniKani integration,
 * and furigana mode toggle.
 * All settings are persisted via SettingsRepository on change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    scrollToProfiles: Boolean = false
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
    val profiles by viewModel.profiles.collectAsState()
    val activeProfileId by viewModel.activeProfileId.collectAsState()
    val profileOperationStatus by viewModel.profileOperationStatus.collectAsState()
    val autoReadEnabled by viewModel.autoReadEnabled.collectAsState()
    val autoReadFlushMode by viewModel.autoReadFlushMode.collectAsState()
    val ttsJapaneseAvailable by viewModel.ttsJapaneseAvailable.collectAsState()

    var deepLKeyVisible by remember { mutableStateOf(false) }
    var openAiKeyVisible by remember { mutableStateOf(false) }
    var claudeKeyVisible by remember { mutableStateOf(false) }
    var waniKaniKeyVisible by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    // Auto-scroll to profiles section (at top) when deep-linked
    if (scrollToProfiles) {
        LaunchedEffect(Unit) {
            delay(100)
            scrollState.animateScrollTo(0)
        }
    }

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
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                // ===== Game Profiles Section (TOP) =====
                ProfilesSection(
                    profiles = profiles,
                    activeProfileId = activeProfileId,
                    operationStatus = profileOperationStatus,
                    onSaveAsProfile = { name -> viewModel.saveAsProfile(name) },
                    onLoadProfile = { profile -> viewModel.loadProfile(profile) },
                    onRenameProfile = { profile, newName -> viewModel.renameProfile(profile, newName) },
                    onDeleteProfile = { profile, deleteHistory -> viewModel.deleteProfile(profile, deleteHistory) }
                )

                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(24.dp))

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
                    if (!ttsJapaneseAvailable) {
                        Text(
                            text = "No Japanese TTS voice installed. Install Google TTS from the Play Store and enable the Japanese language pack in your device's TTS settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            text = "TTS is initializing... Tap Refresh if voices don't appear.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.refreshTtsVoices() }
                    ) {
                        Text("Refresh Voices")
                    }
                } else {
                    VoiceDropdown(
                        selectedVoice = ttsVoiceName,
                        voices = availableVoices,
                        onVoiceSelected = { viewModel.saveTtsVoice(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.refreshTtsVoices() }
                    ) {
                        Text("Refresh Voices")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // ===== Auto-Read Section (after TTS) =====
                AutoReadSection(
                    autoReadEnabled = autoReadEnabled,
                    autoReadFlushMode = autoReadFlushMode,
                    onAutoReadEnabledChanged = { viewModel.saveAutoReadEnabled(it) },
                    onAutoReadFlushModeChanged = { viewModel.saveAutoReadFlushMode(it) }
                )

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

// ==================== Profiles Section ====================

/**
 * Game profiles section with save, list, load, rename, and delete functionality.
 */
@Composable
private fun ProfilesSection(
    profiles: List<ProfileEntity>,
    activeProfileId: Long?,
    operationStatus: String,
    onSaveAsProfile: (String?) -> Unit,
    onLoadProfile: (ProfileEntity) -> Unit,
    onRenameProfile: (ProfileEntity, String) -> Unit,
    onDeleteProfile: (ProfileEntity, Boolean) -> Unit
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    Text(
        text = "Game Profiles",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Save settings per game for quick switching",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )
    Spacer(modifier = Modifier.height(12.dp))

    // Save as Profile button
    Button(
        onClick = { showSaveDialog = true },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Save as Profile")
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Profile list
    profiles.forEach { profile ->
        ProfileCard(
            profile = profile,
            isActive = profile.id == activeProfileId,
            onLoad = { onLoadProfile(profile) },
            onRename = { newName -> onRenameProfile(profile, newName) },
            onDelete = { deleteHistory -> onDeleteProfile(profile, deleteHistory) }
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Operation status
    AnimatedVisibility(visible = operationStatus.isNotEmpty()) {
        Text(
            text = operationStatus,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }

    // Save as Profile dialog
    if (showSaveDialog) {
        SaveProfileDialog(
            suggestedName = "Profile ${profiles.size + 1}",
            onConfirm = { name ->
                onSaveAsProfile(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false }
        )
    }
}

/**
 * Card displaying a single profile with load, rename, and delete actions.
 */
@Composable
private fun ProfileCard(
    profile: ProfileEntity,
    isActive: Boolean,
    onLoad: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: (Boolean) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onLoad() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (profile.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                if (isActive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Three-dot menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Profile options"
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    if (!profile.isDefault) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Delete",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    // Rename dialog
    if (showRenameDialog) {
        RenameProfileDialog(
            currentName = profile.name,
            onConfirm = { newName ->
                onRename(newName)
                showRenameDialog = false
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    // Delete dialog
    if (showDeleteDialog) {
        DeleteProfileDialog(
            profileName = profile.name,
            onConfirm = { deleteHistory ->
                onDelete(deleteHistory)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

/**
 * Dialog for saving current settings as a new profile.
 */
@Composable
private fun SaveProfileDialog(
    suggestedName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(suggestedName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as Profile") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Profile Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for renaming a profile.
 */
@Composable
private fun RenameProfileDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Profile") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("New Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for deleting a profile with option to delete associated history.
 */
@Composable
private fun DeleteProfileDialog(
    profileName: String,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var deleteHistory by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Profile") },
        text = {
            Column {
                Text("Delete profile '$profileName'?")
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deleteHistory = !deleteHistory }
                ) {
                    Checkbox(
                        checked = deleteHistory,
                        onCheckedChange = { deleteHistory = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Also delete associated translation history",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(deleteHistory) }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// ==================== Auto-Read Section ====================

/**
 * Auto-read section with enabled toggle and flush/queue mode selector.
 */
@Composable
private fun AutoReadSection(
    autoReadEnabled: Boolean,
    autoReadFlushMode: Boolean,
    onAutoReadEnabledChanged: (Boolean) -> Unit,
    onAutoReadFlushModeChanged: (Boolean) -> Unit
) {
    Text(
        text = "Auto-Read",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Automatically read new dialog text aloud via TTS",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    )
    Spacer(modifier = Modifier.height(12.dp))

    // Auto-Read Enabled switch
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Auto-Read Enabled",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Switch(
            checked = autoReadEnabled,
            onCheckedChange = onAutoReadEnabledChanged
        )
    }

    // Flush/Queue mode radio group (only shown when enabled)
    AnimatedVisibility(visible = autoReadEnabled) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .selectableGroup()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = autoReadFlushMode,
                        onClick = { onAutoReadFlushModeChanged(true) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = autoReadFlushMode,
                    onClick = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Flush mode (default)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Stop current speech, read new immediately",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = !autoReadFlushMode,
                        onClick = { onAutoReadFlushModeChanged(false) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = !autoReadFlushMode,
                    onClick = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Queue mode",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Add to speech queue after current finishes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Auto-read works with capture regions flagged as 'auto-read'. Use the pencil icon on the floating bubble to configure regions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

// ==================== Existing Helper Composables ====================

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
