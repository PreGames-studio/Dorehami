package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class HokmWaveTest {

    @Test
    fun `wave in range`() {
        val v = HokmWave.value(100, 1000)
        assertTrue(v in -1f..1f)
    }

    @Test
    fun `wave deterministic`() {
        assertTrue(HokmWave.value(100, 1000) == HokmWave.value(100, 1000))
    }
}
