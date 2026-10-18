package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmLightTest {

    @Test
    fun `default bright`() {
        assertTrue(HokmLight.isBright(HokmLight.defaultLight()))
    }

    @Test
    fun `dimmed reduces intensity`() {
        val l = HokmLight.dimmed(HokmLight.defaultLight(), 0.5f)
        assertTrue(l.intensity < HokmLight.defaultLight().intensity)
    }

    @Test
    fun `dimmed clamps`() {
        val l = HokmLight.dimmed(HokmLight.defaultLight(), 0f)
        assertEquals(0f, l.intensity)
    }
}
