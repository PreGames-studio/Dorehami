package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HokmQueueTest {

    @Test
    fun `enqueue dequeue round trip`() {
        val q = HokmQueue.enqueue(emptyList<Int>(), 7)
        val (v, rest) = HokmQueue.dequeue(q)
        assertEquals(7, v)
        assertEquals(0, rest.size)
    }

    @Test
    fun `peek`() {
        val q = HokmQueue.enqueue(HokmQueue.enqueue(emptyList<Int>(), 1), 2)
        assertEquals(1, HokmQueue.peek(q))
    }

    @Test
    fun `dequeue empty`() {
        val (v, rest) = HokmQueue.dequeue(emptyList<Int>())
        assertNull(v)
        assertEquals(0, rest.size)
    }
}
