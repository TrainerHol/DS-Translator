package com.dstranslator.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for TouchState flag combinations.
 * Uses integer constants directly to avoid Android framework dependency.
 */
class TouchStateTest {

    // Android WindowManager.LayoutParams flag constants (used as ints for pure unit testing)
    private val FLAG_NOT_FOCUSABLE = 0x00000008
    private val FLAG_NOT_TOUCHABLE = 0x00000010
    private val FLAG_SECURE = 0x00002000

    @Test
    fun `FULL_PASSTHROUGH produces FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE or FLAG_SECURE`() {
        val flags = TouchStateFlags.getFlagsForState(TouchState.FULL_PASSTHROUGH)
        val expected = FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE or FLAG_SECURE
        assertEquals(expected, flags)
    }

    @Test
    fun `PANEL_INTERACTIVE produces FLAG_NOT_FOCUSABLE or FLAG_SECURE (no FLAG_NOT_TOUCHABLE)`() {
        val flags = TouchStateFlags.getFlagsForState(TouchState.PANEL_INTERACTIVE)
        val expected = FLAG_NOT_FOCUSABLE or FLAG_SECURE
        assertEquals(expected, flags)
    }

    @Test
    fun `PANEL_LOCKED produces FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE or FLAG_SECURE`() {
        val flags = TouchStateFlags.getFlagsForState(TouchState.PANEL_LOCKED)
        val expected = FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE or FLAG_SECURE
        assertEquals(expected, flags)
    }

    @Test
    fun `SOURCE_INTERACTIVE produces FLAG_NOT_FOCUSABLE or FLAG_SECURE for each label view`() {
        val flags = TouchStateFlags.getFlagsForState(TouchState.SOURCE_INTERACTIVE)
        val expected = FLAG_NOT_FOCUSABLE or FLAG_SECURE
        assertEquals(expected, flags)
    }
}
