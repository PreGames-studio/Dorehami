package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmPaginationTest {

    @Test
    fun `total pages`() {
        assertEquals(3, HokmPagination.totalPages(25, 10))
        assertEquals(1, HokmPagination.totalPages(5, 10))
        assertEquals(0, HokmPagination.totalPages(5, 0))
    }

    @Test
    fun `page items`() {
        val items = (1..25).map { it.toString() }
        assertEquals(listOf("1", "2", "3"), HokmPagination.pageItems(items, 1, 3))
        assertEquals(listOf("4", "5", "6"), HokmPagination.pageItems(items, 2, 3))
    }

    @Test
    fun `has next`() {
        assertTrue(HokmPagination.hasNext(1, 3))
        assertTrue(!HokmPagination.hasNext(3, 3))
    }

    @Test
    fun `has prev`() {
        assertTrue(HokmPagination.hasPrev(2))
        assertTrue(!HokmPagination.hasPrev(1))
    }
}
