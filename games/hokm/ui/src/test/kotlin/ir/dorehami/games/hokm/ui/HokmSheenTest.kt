package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmSheenTest {

    @Test
    fun `intensity winner higher`() {
        assertTrue(HokmSheen.intensity(true, false) > HokmSheen.intensity(false, false))
    }

    @Test
    fun `intensity trump adds`() {
        assertTrue(HokmSheen.intensity(false, true) > HokmSheen.intensity(false, false))
    }

    @Test
    fun `intensity capped at 1`() {
        assertEquals(1f, HokmSheen.intensity(true, true))
    }

    @Test
    fun `should sheen respects reduced motion`() {
        assertTrue(HokmSheen.shouldSheen(true, false))
        assertTrue(!HokmSheen.shouldSheen(true, true))
    }
}
