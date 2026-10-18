package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class HokmMemoizeTest {

    @Test
    fun `get or put computes once`() {
        val cache = mutableMapOf<String, Int>()
        var calls = 0
        val v1 = HokmMemoize.getOrPut(cache, "k") { calls++; 42 }
        val v2 = HokmMemoize.getOrPut(cache, "k") { calls++; 99 }
        assertEquals(42, v1)
        assertEquals(42, v2)
        assertEquals(1, calls)
    }

    @Test
    fun `size and clear`() {
        val cache = mutableMapOf("a" to 1)
        assertEquals(1, HokmMemoize.size(cache))
        HokmMemoize.clear(cache)
        assertEquals(0, HokmMemoize.size(cache))
    }
}
