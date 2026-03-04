package com.dstranslator.service

import com.dstranslator.domain.model.OverlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for OverlayStateMachine mode transitions.
 */
class OverlayStateTest {

    @Test
    fun `transition from Off to OverlayOnSource indicates cleanup not needed`() {
        val transition = OverlayStateMachine.transition(OverlayMode.Off, OverlayMode.OverlayOnSource)

        assertEquals(OverlayMode.Off, transition.from)
        assertEquals(OverlayMode.OverlayOnSource, transition.to)
        assertFalse("No cleanup needed when transitioning from Off", transition.cleanupNeeded)
    }

    @Test
    fun `transition from OverlayOnSource to Panel needs cleanup`() {
        val transition = OverlayStateMachine.transition(OverlayMode.OverlayOnSource, OverlayMode.Panel)

        assertEquals(OverlayMode.OverlayOnSource, transition.from)
        assertEquals(OverlayMode.Panel, transition.to)
        assertTrue("Cleanup needed when leaving OverlayOnSource", transition.cleanupNeeded)
    }

    @Test
    fun `transition from Panel to Off needs cleanup`() {
        val transition = OverlayStateMachine.transition(OverlayMode.Panel, OverlayMode.Off)

        assertEquals(OverlayMode.Panel, transition.from)
        assertEquals(OverlayMode.Off, transition.to)
        assertTrue("Cleanup needed when leaving Panel", transition.cleanupNeeded)
    }

    @Test
    fun `transition from same to same is no-op`() {
        val transition = OverlayStateMachine.transition(OverlayMode.Panel, OverlayMode.Panel)

        assertEquals(OverlayMode.Panel, transition.from)
        assertEquals(OverlayMode.Panel, transition.to)
        assertFalse("Same-to-same is no-op, no cleanup needed", transition.cleanupNeeded)
    }

    @Test
    fun `transition from OverlayOnSource to Off needs cleanup`() {
        val transition = OverlayStateMachine.transition(OverlayMode.OverlayOnSource, OverlayMode.Off)

        assertEquals(OverlayMode.OverlayOnSource, transition.from)
        assertEquals(OverlayMode.Off, transition.to)
        assertTrue("Cleanup needed when leaving OverlayOnSource", transition.cleanupNeeded)
    }

    @Test
    fun `transition from Off to Panel does not need cleanup`() {
        val transition = OverlayStateMachine.transition(OverlayMode.Off, OverlayMode.Panel)

        assertEquals(OverlayMode.Off, transition.from)
        assertEquals(OverlayMode.Panel, transition.to)
        assertFalse("No cleanup needed when transitioning from Off", transition.cleanupNeeded)
    }
}
