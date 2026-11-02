package com.loosewire.notifications.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwipeDownTest {

    private fun gesture() = SwipeDownGesture(triggerPx = 40f, slopPx = 12f)

    @Test
    fun `a long downward stroke arms and fires`() {
        val g = gesture()
        g.down(500f, 10f)
        g.move(500f, 30f)
        assertEquals(SwipeStage.Watching, g.stage)
        g.move(500f, 60f)
        assertEquals(SwipeStage.Armed, g.stage)
        assertTrue(g.up())
    }

    @Test
    fun `a stroke short of the trigger does not fire`() {
        val g = gesture()
        g.down(500f, 10f)
        g.move(500f, 45f)
        assertEquals(SwipeStage.Watching, g.stage)
        assertFalse(g.up())
    }

    @Test
    fun `a stroke that never moves does not fire`() {
        val g = gesture()
        g.down(500f, 10f)
        assertFalse(g.up())
    }

    @Test
    fun `moving up cancels the stroke`() {
        val g = gesture()
        g.down(500f, 100f)
        assertFalse(g.move(500f, 80f))
        assertEquals(SwipeStage.Cancelled, g.stage)
        assertFalse(g.up())
    }

    @Test
    fun `a sideways stroke is given away as a scroll`() {
        val g = gesture()
        g.down(500f, 10f)
        // Past the slop, and further sideways than down: somebody paging the toolbox.
        assertFalse(g.move(530f, 20f))
        assertEquals(SwipeStage.Cancelled, g.stage)
    }

    @Test
    fun `jitter inside the slop is not a cancellation`() {
        val g = gesture()
        g.down(500f, 10f)
        assertTrue(g.move(506f, 14f))
        assertEquals(SwipeStage.Watching, g.stage)
    }

    @Test
    fun `a cancelled stroke cannot come back`() {
        val g = gesture()
        g.down(500f, 10f)
        // Far enough up to be past the slop, which is what makes it a cancellation.
        g.move(500f, -10f)
        assertEquals(SwipeStage.Cancelled, g.stage)
        // Carrying on downwards must not re-arm what the user already abandoned.
        assertFalse(g.move(500f, 200f))
        assertEquals(SwipeStage.Cancelled, g.stage)
        assertFalse(g.up())
    }

    @Test
    fun `up resets for the next stroke`() {
        val g = gesture()
        g.down(500f, 10f)
        g.move(500f, 60f)
        assertTrue(g.up())
        assertEquals(SwipeStage.Idle, g.stage)

        g.down(500f, 10f)
        assertFalse(g.up())
    }

    @Test
    fun `strip height is capped at a quarter of the screen`() {
        assertEquals(56, stripHeight(screenPx = 1000, desiredPx = 56))
        // A dp height taller than the panel would be a window with negative height.
        assertEquals(250, stripHeight(screenPx = 1000, desiredPx = 900))
    }

    @Test
    fun `strip height is never zero`() {
        assertEquals(1, stripHeight(screenPx = 1000, desiredPx = 0))
        assertEquals(1, stripHeight(screenPx = 0, desiredPx = 0))
    }

    @Test
    fun `only the toolbox gets a strip`() {
        assertTrue(isHomePackage("com.lightos"))
        assertTrue(isHomePackage("com.lightos.launcher"))
        assertTrue(isHomePackage("com.thelightphone.sdk.emulator"))
        assertFalse(isHomePackage("com.loosewire.notifications"))
        assertFalse(isHomePackage("app.lightphonekeyboard"))
        // The bug the predicate exists for: a Light-looking id is not the toolbox.
        assertFalse(isHomePackage("com.lightphone.spotify"))
        assertFalse(isHomePackage(null))
    }
}
