package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmFlipTest {

    @Test
    fun `not flipped at start`() {
        assertTrue(!HokmFlip.isFlipped(0f))
    }

    @Test
    fun `flipped at end`() {
        assertTrue(HokmFlip.isFlipped(1f))
    }

    @Test
    fun `front alpha at start 1`() {
        assertEquals(1f, HokmFlip.frontAlpha(0f))
    }

    @Test
    fun `back alpha at end 1`() {
        assertEquals(1f, HokmFlip.backAlpha(1f))
    }
}
