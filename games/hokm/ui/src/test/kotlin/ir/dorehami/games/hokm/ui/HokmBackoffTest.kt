package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HokmBackoffTest {

    @Test
    fun `next delay grows`() {
        val d1 = HokmBackoff.nextDelayMs(0, 100, 10000)
        val d2 = HokmBackoff.nextDelayMs(2, 100, 10000)
        assertTrue(d2 >= d1)
    }

    @Test
    fun `capped at max`() {
        assertEquals(1000L, HokmBackoff.nextDelayMs(10, 100, 1000))
    }

    @Test
    fun `should retry`() {
        assertTrue(HokmBackoff.shouldRetry(0, 3))
        assertFalse(HokmBackoff.shouldRetry(3, 3))
    }
}
