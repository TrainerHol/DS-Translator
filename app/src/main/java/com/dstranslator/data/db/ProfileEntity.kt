package com.dstranslator.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a saved user profile.
 * Each profile stores a snapshot of all non-secret app settings as JSON blobs,
 * enabling users to switch between different game configurations quickly.
 *
 * @param settingsJson JSON blob containing: translationEngine, ocrEngine, ttsVoice,
 *   captureIntervalMs, furiganaMode, openAiBaseUrl, openAiModel, autoReadEnabled, autoReadFlushMode
 * @param captureRegionsJson JSON array of CaptureRegion objects (with autoRead flags)
 * @param autoReadEnabled Whether auto-read TTS is enabled for this profile
 * @param autoReadFlushMode true = QUEUE_FLUSH (interrupt), false = QUEUE_ADD (queue)
 */
@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isDefault: Boolean = false,
    val settingsJson: String,
    val captureRegionsJson: String,
    val autoReadEnabled: Boolean = false,
    val autoReadFlushMode: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
