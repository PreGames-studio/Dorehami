package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmRolloutTest {

    @Test
    fun `0 percent disables all`() {
        assertTrue(!HokmRollout.isEnabled("user1", 0))
    }

    @Test
    fun `100 percent enables all`() {
        assertTrue(HokmRollout.isEnabled("user1", 100))
    }

    @Test
    fun `clamp percentage`() {
        assertEquals(0, HokmRollout.clampPercentage(-5))
        assertEquals(100, HokmRollout.clampPercentage(150))
    }

    @Test
    fun `deterministic`() {
        assertTrue(HokmRollout.isEnabled("user1", 50) == HokmRollout.isEnabled("user1", 50))
    }
}
