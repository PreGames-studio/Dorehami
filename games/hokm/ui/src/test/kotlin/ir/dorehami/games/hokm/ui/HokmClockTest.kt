package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmClockTest {

    @Test
    fun `elapsed positive`() {
        assertEquals(500L, HokmClock.elapsed(1000, 1500))
    }

    @Test
    fun `elapsed clamps negative`() {
        assertEquals(0L, HokmClock.elapsed(1500, 1000))
    }

    @Test
    fun `is expired true`() {
        assertTrue(HokmClock.isExpired(0, 1000, 1500))
    }

    @Test
    fun `is expired false`() {
        assertTrue(!HokmClock.isExpired(0, 1000, 500))
    }
}
