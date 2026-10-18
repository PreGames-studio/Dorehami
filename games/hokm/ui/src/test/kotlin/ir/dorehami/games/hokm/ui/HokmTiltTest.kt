package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmTiltTest {

    @Test
    fun `clamp range`() {
        assertEquals(15f, HokmTilt.clamp(30f))
        assertEquals(-15f, HokmTilt.clamp(-30f))
        assertEquals(5f, HokmTilt.clamp(5f))
    }

    @Test
    fun `tilt when dragging`() {
        assertTrue(HokmTilt.forCard(true) > 0f)
        assertEquals(0f, HokmTilt.forCard(false))
    }
}
