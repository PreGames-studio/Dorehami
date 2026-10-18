package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class HokmLRUTest {

    @Test
    fun `touch moves to end`() {
        assertEquals(listOf("a", "c", "b"), HokmLRU.touch(listOf("a", "b", "c"), "b"))
    }

    @Test
    fun `evict`() {
        assertEquals(listOf("b", "c"), HokmLRU.evictIfNeeded(listOf("a", "b", "c"), 2))
    }

    @Test
    fun `no evict under capacity`() {
        assertEquals(listOf("a", "b"), HokmLRU.evictIfNeeded(listOf("a", "b"), 5))
    }
}
