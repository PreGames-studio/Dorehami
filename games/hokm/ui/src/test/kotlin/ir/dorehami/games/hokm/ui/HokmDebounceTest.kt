package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmDebounceTest {

    @Test
    fun `should fire initially`() {
        val s = HokmDebounce.State()
        assertTrue(HokmDebounce.shouldFire(s, 1000, 500))
    }

    @Test
    fun `should not fire within delay`() {
        val s = HokmDebounce.State(lastActionMs = 1000)
        assertTrue(!HokmDebounce.shouldFire(s, 1200, 500))
    }

    @Test
    fun `should fire after delay`() {
        val s = HokmDebounce.State(lastActionMs = 1000)
        assertTrue(HokmDebounce.shouldFire(s, 1600, 500))
    }

    @Test
    fun `record updates last action`() {
        val s = HokmDebounce.record(HokmDebounce.State(), 500)
        assertEquals(500L, s.lastActionMs)
    }
}
