package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmGlowTest {

    @Test
    fun `intensity highlighted higher`() {
        assertTrue(HokmGlow.intensity(true, 0f) > HokmGlow.intensity(false, 0f))
    }

    @Test
    fun `radius highlighted`() {
        assertTrue(HokmGlow.radius(true) > 0f)
        assertEquals(0f, HokmGlow.radius(false))
    }

    @Test
    fun `should glow respects reduced motion`() {
        assertTrue(HokmGlow.shouldGlow(true, false))
        assertTrue(!HokmGlow.shouldGlow(true, true))
        assertTrue(!HokmGlow.shouldGlow(false, false))
    }
}
