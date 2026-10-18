package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HokmMatchmakingTest {

    @Test
    fun `can match similar skill`() {
        val a = HokmMatchmaking.QueueEntry("a", 100, 0)
        val b = HokmMatchmaking.QueueEntry("b", 120, 0)
        assertTrue(HokmMatchmaking.canMatch(a, b))
    }

    @Test
    fun `cannot match far skill`() {
        val a = HokmMatchmaking.QueueEntry("a", 100, 0)
        val b = HokmMatchmaking.QueueEntry("b", 500, 0)
        assertTrue(!HokmMatchmaking.canMatch(a, b))
    }

    @Test
    fun `find match`() {
        val queue = listOf(HokmMatchmaking.QueueEntry("b", 110, 0))
        val entry = HokmMatchmaking.QueueEntry("a", 100, 0)
        assertEquals("b", HokmMatchmaking.findMatch(queue, entry)?.playerId)
    }

    @Test
    fun `find match none`() {
        val queue = listOf(HokmMatchmaking.QueueEntry("b", 500, 0))
        val entry = HokmMatchmaking.QueueEntry("a", 100, 0)
        assertNull(HokmMatchmaking.findMatch(queue, entry))
    }

    @Test
    fun `remove player`() {
        val queue = listOf(HokmMatchmaking.QueueEntry("a", 100, 0), HokmMatchmaking.QueueEntry("b", 110, 0))
        val q2 = HokmMatchmaking.removePlayer(queue, "a")
        assertEquals(1, HokmMatchmaking.queueSize(q2))
    }
}
