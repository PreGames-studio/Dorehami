package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HokmOfflineQueueTest {

    private fun action(id: String) = HokmOfflineQueue.Action(id, "p", 0)

    @Test
    fun `enqueue grows`() {
        val q = HokmOfflineQueue.enqueue(emptyList(), action("a"))
        assertEquals(1, HokmOfflineQueue.size(q))
    }

    @Test
    fun `dedup by id`() {
        var q = HokmOfflineQueue.enqueue(emptyList(), action("a"))
        q = HokmOfflineQueue.enqueue(q, action("a"))
        assertEquals(1, HokmOfflineQueue.size(q))
    }

    @Test
    fun `dequeue returns first`() {
        var q = HokmOfflineQueue.enqueue(emptyList(), action("a"))
        q = HokmOfflineQueue.enqueue(q, action("b"))
        val (a, q2) = HokmOfflineQueue.dequeue(q)
        assertEquals("a", a!!.id)
        assertEquals(1, HokmOfflineQueue.size(q2))
    }

    @Test
    fun `dequeue empty returns null`() {
        val (a, q) = HokmOfflineQueue.dequeue(emptyList())
        assertNull(a)
        assertTrue(HokmOfflineQueue.isEmpty(q))
    }

    @Test
    fun `caps at max`() {
        var q = emptyList<HokmOfflineQueue.Action>()
        repeat(HokmOfflineQueue.MAX_SIZE + 5) { i ->
            q = HokmOfflineQueue.enqueue(q, action("a$i"))
        }
        assertEquals(HokmOfflineQueue.MAX_SIZE, HokmOfflineQueue.size(q))
    }

    @Test
    fun `clear empties`() {
        val q = HokmOfflineQueue.clear()
        assertTrue(HokmOfflineQueue.isEmpty(q))
    }
}
