package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmSearchTest {

    @Test
    fun `contains case insensitive`() {
        assertTrue(HokmSearch.contains("Hello", "hello"))
    }

    @Test
    fun `filter empty query returns all`() {
        val items = listOf("a", "b", "c")
        assertEquals(items, HokmSearch.filter(items, ""))
    }

    @Test
    fun `filter narrows`() {
        val items = listOf("apple", "banana", "apricot")
        assertEquals(listOf("apple", "apricot"), HokmSearch.filter(items, "ap"))
    }
}
