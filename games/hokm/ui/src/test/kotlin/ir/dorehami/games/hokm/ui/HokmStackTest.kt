package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HokmStackTest {

    @Test
    fun `push pop round trip`() {
        val s = HokmStack.push(emptyList<Int>(), 5)
        val (v, rest) = HokmStack.pop(s)
        assertEquals(5, v)
        assertEquals(0, rest.size)
    }

    @Test
    fun `peek`() {
        val s = HokmStack.push(HokmStack.push(emptyList<Int>(), 1), 2)
        assertEquals(2, HokmStack.peek(s))
    }

    @Test
    fun `pop empty`() {
        val (v, rest) = HokmStack.pop(emptyList<Int>())
        assertNull(v)
        assertEquals(0, rest.size)
    }
}
