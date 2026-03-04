package com.dstranslator.data.db

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for ProfileEntity JSON serialization roundtrips.
 * Verifies that settingsJson and captureRegionsJson can be created and parsed
 * without data loss.
 */
class ProfileEntityTest {

    @Test
    fun `settingsJson roundtrip preserves all keys`() {
        val settings = JSONObject().apply {
            put("translationEngine", "deepl")
            put("ocrEngine", "mlkit")
            put("ttsVoice", "ja-JP-default")
            put("captureIntervalMs", 3000L)
            put("furiganaMode", "all")
            put("autoReadEnabled", true)
            put("autoReadFlushMode", false)
        }

        val entity = ProfileEntity(
            id = 1,
            name = "Test Profile",
            isDefault = false,
            settingsJson = settings.toString(),
            captureRegionsJson = "[]",
            autoReadEnabled = true,
            autoReadFlushMode = false
        )

        val parsed = JSONObject(entity.settingsJson)
        assertEquals("deepl", parsed.getString("translationEngine"))
        assertEquals("mlkit", parsed.getString("ocrEngine"))
        assertEquals("ja-JP-default", parsed.getString("ttsVoice"))
        assertEquals(3000L, parsed.getLong("captureIntervalMs"))
        assertEquals("all", parsed.getString("furiganaMode"))
        assertEquals(true, parsed.getBoolean("autoReadEnabled"))
        assertEquals(false, parsed.getBoolean("autoReadFlushMode"))
    }

    @Test
    fun `captureRegionsJson roundtrip preserves autoRead flags`() {
        val regions = JSONArray().apply {
            put(JSONObject().apply {
                put("x", 10)
                put("y", 20)
                put("width", 100)
                put("height", 50)
                put("id", "region1")
                put("label", "Dialogue")
                put("autoRead", true)
            })
            put(JSONObject().apply {
                put("x", 200)
                put("y", 300)
                put("width", 150)
                put("height", 75)
                put("id", "region2")
                put("label", "Menu")
                put("autoRead", false)
            })
        }

        val entity = ProfileEntity(
            id = 1,
            name = "Test Profile",
            isDefault = false,
            settingsJson = "{}",
            captureRegionsJson = regions.toString()
        )

        val parsed = JSONArray(entity.captureRegionsJson)
        assertEquals(2, parsed.length())

        val region1 = parsed.getJSONObject(0)
        assertEquals(10, region1.getInt("x"))
        assertEquals(20, region1.getInt("y"))
        assertEquals(100, region1.getInt("width"))
        assertEquals(50, region1.getInt("height"))
        assertEquals("region1", region1.getString("id"))
        assertEquals("Dialogue", region1.getString("label"))
        assertTrue(region1.getBoolean("autoRead"))

        val region2 = parsed.getJSONObject(1)
        assertEquals(false, region2.getBoolean("autoRead"))
    }
}
