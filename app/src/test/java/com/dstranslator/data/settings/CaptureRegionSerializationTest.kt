package com.dstranslator.data.settings

import com.dstranslator.domain.model.CaptureRegion
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for CaptureRegion autoRead field serialization and deserialization,
 * including backward-compatible handling of legacy format (no autoRead key).
 */
class CaptureRegionSerializationTest {

    /**
     * Helper to serialize a CaptureRegion to JSON, matching the pattern
     * used in SettingsRepository.saveCaptureRegions().
     */
    private fun serializeRegion(region: CaptureRegion): JSONObject {
        return JSONObject().apply {
            put("x", region.x)
            put("y", region.y)
            put("width", region.width)
            put("height", region.height)
            put("id", region.id)
            put("label", region.label)
            put("autoRead", region.autoRead)
        }
    }

    /**
     * Helper to deserialize a CaptureRegion from JSON, matching the pattern
     * used in SettingsRepository.getCaptureRegions().
     */
    private fun deserializeRegion(obj: JSONObject): CaptureRegion {
        return CaptureRegion(
            x = obj.getInt("x"),
            y = obj.getInt("y"),
            width = obj.getInt("width"),
            height = obj.getInt("height"),
            id = obj.optString("id", "default"),
            label = obj.optString("label", ""),
            autoRead = obj.optBoolean("autoRead", false)
        )
    }

    @Test
    fun `CaptureRegion with autoRead true serializes correctly`() {
        val region = CaptureRegion(
            x = 10, y = 20, width = 100, height = 50,
            id = "r1", label = "Dialogue", autoRead = true
        )
        val json = serializeRegion(region)
        assertTrue(json.getBoolean("autoRead"))
    }

    @Test
    fun `CaptureRegion with autoRead false serializes correctly`() {
        val region = CaptureRegion(
            x = 10, y = 20, width = 100, height = 50,
            id = "r1", label = "Menu", autoRead = false
        )
        val json = serializeRegion(region)
        assertFalse(json.getBoolean("autoRead"))
    }

    @Test
    fun `CaptureRegion default autoRead is false`() {
        val region = CaptureRegion(x = 10, y = 20, width = 100, height = 50)
        assertFalse(region.autoRead)
    }

    @Test
    fun `legacy JSON without autoRead key deserializes with autoRead false`() {
        val legacyJson = JSONObject().apply {
            put("x", 10)
            put("y", 20)
            put("width", 100)
            put("height", 50)
            put("id", "default")
            put("label", "")
        }
        // No "autoRead" key present
        assertFalse(legacyJson.has("autoRead"))

        val region = deserializeRegion(legacyJson)
        assertFalse(region.autoRead)
    }

    @Test
    fun `full roundtrip preserves autoRead flag`() {
        val original = CaptureRegion(
            x = 50, y = 100, width = 200, height = 100,
            id = "test", label = "Test", autoRead = true
        )
        val json = serializeRegion(original)
        val deserialized = deserializeRegion(json)

        assertEquals(original, deserialized)
        assertTrue(deserialized.autoRead)
    }

    @Test
    fun `JSON array roundtrip with mixed autoRead values`() {
        val regions = listOf(
            CaptureRegion(x = 0, y = 0, width = 100, height = 50, id = "a", label = "A", autoRead = true),
            CaptureRegion(x = 100, y = 0, width = 100, height = 50, id = "b", label = "B", autoRead = false)
        )

        val array = JSONArray()
        regions.forEach { array.put(serializeRegion(it)) }

        val deserialized = (0 until array.length()).map { i ->
            deserializeRegion(array.getJSONObject(i))
        }

        assertEquals(regions, deserialized)
        assertTrue(deserialized[0].autoRead)
        assertFalse(deserialized[1].autoRead)
    }
}
