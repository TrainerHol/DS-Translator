package com.dstranslator.ui.region

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dstranslator.data.settings.SettingsRepository
import com.dstranslator.domain.model.CaptureRegion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the region setup screen. Manages the frozen screenshot,
 * current crop region being edited, and persistence of the saved region.
 */
@HiltViewModel
class RegionSetupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    /** Previously saved capture region from settings */
    private val _savedRegion = MutableStateFlow<CaptureRegion?>(null)
    val savedRegion: StateFlow<CaptureRegion?> = _savedRegion.asStateFlow()

    /** The frozen screenshot for region selection */
    private val _screenshotBitmap = MutableStateFlow<Bitmap?>(null)
    val screenshotBitmap: StateFlow<Bitmap?> = _screenshotBitmap.asStateFlow()

    /** The region currently being edited (drag handles update this) */
    private val _currentRegion = MutableStateFlow<CaptureRegion?>(null)
    val currentRegion: StateFlow<CaptureRegion?> = _currentRegion.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settingsRepository.getCaptureRegion()
            _savedRegion.value = saved
            _currentRegion.value = saved
        }
    }

    /**
     * Set the screenshot bitmap. If no saved region exists, initializes
     * the current region to center 80% of the bitmap dimensions.
     */
    fun setScreenshot(bitmap: Bitmap) {
        _screenshotBitmap.value = bitmap
        if (_currentRegion.value == null) {
            initializeDefaultRegion(bitmap.width, bitmap.height)
        }
    }

    /**
     * Update the current region from drag handle movement.
     */
    fun updateRegion(region: CaptureRegion) {
        _currentRegion.value = region
    }

    /**
     * Save the current region to persistent settings and update the saved region state.
     */
    fun saveRegion() {
        viewModelScope.launch {
            _currentRegion.value?.let { region ->
                settingsRepository.setCaptureRegion(region)
                _savedRegion.value = region
            }
        }
    }

    /**
     * Reset the region to the default center 80% of the current screenshot.
     */
    fun resetRegion() {
        val bitmap = _screenshotBitmap.value ?: return
        initializeDefaultRegion(bitmap.width, bitmap.height)
    }

    /**
     * Initialize region to center 80% of the given dimensions.
     */
    private fun initializeDefaultRegion(bitmapWidth: Int, bitmapHeight: Int) {
        val marginX = (bitmapWidth * 0.1f).toInt()
        val marginY = (bitmapHeight * 0.1f).toInt()
        _currentRegion.value = CaptureRegion(
            x = marginX,
            y = marginY,
            width = bitmapWidth - 2 * marginX,
            height = bitmapHeight - 2 * marginY
        )
    }
}
