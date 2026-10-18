package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class HokmNoiseTest {

    @Test
    fun `noise value in range`() {
        val v = HokmNoise.value(42, 1, 2)
        assertTrue(v in 0f..1f)
    }

    @Test
    fun `noise deterministic`() {
        assertTrue(HokmNoise.value(42, 1, 2) == HokmNoise.value(42, 1, 2))
    }

    @Test
    fun `enabled check`() {
        assertTrue(HokmNoise.isEnabled("high"))
        assertTrue(!HokmNoise.isEnabled("low"))
    }
}
