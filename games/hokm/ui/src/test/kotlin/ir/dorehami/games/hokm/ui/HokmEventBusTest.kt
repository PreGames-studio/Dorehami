package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HokmEventBusTest {

    @Test
    fun `enqueue dequeue round trip`() {
        val q = HokmEventBus.enqueue(emptyList(), HokmEventBus.Event("a"))
        val (e, rest) = HokmEventBus.dequeue(q)
        assertEquals("a", e?.name)
        assertTrue(rest.isEmpty())
    }

    @Test
    fun `size`() {
        val q = HokmEventBus.enqueue(HokmEventBus.enqueue(emptyList(), HokmEventBus.Event("a")), HokmEventBus.Event("b"))
        assertEquals(2, HokmEventBus.size(q))
    }

    @Test
    fun `dequeue empty`() {
        val (e, rest) = HokmEventBus.dequeue(emptyList())
        assertEquals(null, e)
        assertTrue(rest.isEmpty())
    }
}
