package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class HokmPulseTest {

    @Test
    fun `pulse in range`() {
        val v = HokmPulse.value(100, 1000)
        assertTrue(v in 0f..1f)
    }

    @Test
    fun `pulse deterministic`() {
        assertTrue(HokmPulse.value(100, 1000) == HokmPulse.value(100, 1000))
    }
}
