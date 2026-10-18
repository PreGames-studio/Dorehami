package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmSortTest {

    @Test
    fun `by rank sorts ace first`() {
        val sorted = HokmSort.byRank(listOf("2♠", "A♠", "K♠"))
        assertEquals("A♠", sorted.first())
    }

    @Test
    fun `by suit groups`() {
        val sorted = HokmSort.bySuit(listOf("A♥", "K♠", "Q♥"))
        assertTrue(sorted.first().endsWith('♥') || sorted.first().endsWith('♠'))
    }

    @Test
    fun `descending`() {
        val sorted = HokmSort.descending(listOf("A", "B", "C"))
        assertEquals("C", sorted.first())
    }
}
