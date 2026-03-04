package com.dstranslator.data.settings

import com.dstranslator.domain.model.OverlayConfig
import com.dstranslator.domain.model.OverlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for overlay settings serialization/deserialization and domain model defaults.
 * Tests pure functions and data classes -- no Android framework dependency.
 */
class OverlaySettingsTest {

    @Test
    fun `OverlayConfig JSON serialization round-trip produces identical object`() {
        val original = OverlayConfig(
            panelX = 250,
            panelY = 350,
            panelWidth = 500,
            panelHeight = 300,
            panelAlpha = 0.75f,
            textSizeSp = 18,
            isPinned = true,
            isLocked = false
        )

        val json = SettingsRepository.serializeOverlayConfig(original)
        val restored = SettingsRepository.deserializeOverlayConfig(json)

        assertEquals(original.panelX, restored.panelX)
        assertEquals(original.panelY, restored.panelY)
        assertEquals(original.panelWidth, restored.panelWidth)
        assertEquals(original.panelHeight, restored.panelHeight)
        assertEquals(original.panelAlpha, restored.panelAlpha, 0.01f)
        assertEquals(original.textSizeSp, restored.textSizeSp)
        assertEquals(original.isPinned, restored.isPinned)
        assertEquals(original.isLocked, restored.isLocked)
    }

    @Test
    fun `OverlayConfig deserialization of empty string produces defaults`() {
        val config = SettingsRepository.deserializeOverlayConfig("")

        assertEquals(100, config.panelX)
        assertEquals(100, config.panelY)
        assertEquals(0, config.panelWidth)   // sentinel
        assertEquals(0, config.panelHeight)  // sentinel
        assertEquals(0.85f, config.panelAlpha, 0.01f)
        assertEquals(14, config.textSizeSp)
        assertFalse(config.isPinned)
        assertFalse(config.isLocked)
    }

    @Test
    fun `OverlayConfig deserialization of malformed JSON produces defaults`() {
        val config = SettingsRepository.deserializeOverlayConfig("{invalid json")

        assertEquals(0, config.panelWidth)   // sentinel preserved
        assertEquals(0, config.panelHeight)  // sentinel preserved
    }

    @Test
    fun `OverlayMode fromString with valid values`() {
        assertEquals(OverlayMode.Off, OverlayMode.fromString("Off"))
        assertEquals(OverlayMode.OverlayOnSource, OverlayMode.fromString("OverlayOnSource"))
        assertEquals(OverlayMode.Panel, OverlayMode.fromString("Panel"))
    }

    @Test
    fun `OverlayMode fromString with invalid input returns Off`() {
        assertEquals(OverlayMode.Off, OverlayMode.fromString(""))
        assertEquals(OverlayMode.Off, OverlayMode.fromString("invalid"))
        assertEquals(OverlayMode.Off, OverlayMode.fromString("OVERLAY"))
    }

    @Test
    fun `OverlayMode fromString is case-insensitive`() {
        assertEquals(OverlayMode.Panel, OverlayMode.fromString("panel"))
        assertEquals(OverlayMode.Panel, OverlayMode.fromString("PANEL"))
        assertEquals(OverlayMode.OverlayOnSource, OverlayMode.fromString("overlayonsource"))
    }

    @Test
    fun `default OverlayConfig has sentinel 0 values for width and height`() {
        val config = OverlayConfig()

        assertEquals(0, config.panelWidth)
        assertEquals(0, config.panelHeight)
    }

    @Test
    fun `OverlayConfig resolveForDisplay computes correct sizes from sentinel`() {
        val config = OverlayConfig()  // panelWidth=0, panelHeight=0

        val resolved = config.resolveForDisplay(displayWidth = 1920, displayHeight = 1080)

        // 35% of 1920 = 672
        assertEquals(672, resolved.panelWidth)
        // 20% of 1080 = 216
        assertEquals(216, resolved.panelHeight)
    }

    @Test
    fun `OverlayConfig resolveForDisplay preserves explicit non-sentinel sizes`() {
        val config = OverlayConfig(panelWidth = 500, panelHeight = 300)

        val resolved = config.resolveForDisplay(displayWidth = 1920, displayHeight = 1080)

        // Explicit sizes should be preserved, not overridden
        assertEquals(500, resolved.panelWidth)
        assertEquals(300, resolved.panelHeight)
    }

    @Test
    fun `OverlayConfig serialization preserves sentinel values`() {
        val config = OverlayConfig()  // panelWidth=0, panelHeight=0

        val json = SettingsRepository.serializeOverlayConfig(config)
        val restored = SettingsRepository.deserializeOverlayConfig(json)

        assertEquals(0, restored.panelWidth)
        assertEquals(0, restored.panelHeight)
    }
}
