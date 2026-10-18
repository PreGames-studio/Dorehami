package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmColorMixTest {

    @Test
    fun `mix at 0 returns a`() {
        val a = HokmColor.RGB(255, 0, 0)
        val b = HokmColor.RGB(0, 0, 255)
        assertEquals(a, HokmColorMix.mix(a, b, 0f))
    }

    @Test
    fun `mix at 1 returns b`() {
        val a = HokmColor.RGB(255, 0, 0)
        val b = HokmColor.RGB(0, 0, 255)
        assertEquals(b, HokmColorMix.mix(a, b, 1f))
    }

    @Test
    fun `with alpha non-empty`() {
        val s = HokmColorMix.withAlpha(HokmColor.RGB(255, 0, 0), 0.5f)
        assertTrue(s.startsWith("rgba"))
    }
}
