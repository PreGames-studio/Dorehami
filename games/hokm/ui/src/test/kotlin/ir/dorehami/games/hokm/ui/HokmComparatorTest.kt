package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmComparatorTest {

    @Test
    fun `compare by score descending`() {
        assertTrue(HokmComparator.compareByScore(5, 3) < 0)
    }

    @Test
    fun `compare by name case insensitive`() {
        assertTrue(HokmComparator.compareByName("ali", "Bob") < 0)
    }

    @Test
    fun `top N`() {
        assertEquals(listOf(5, 4, 3), HokmComparator.topN(listOf(1, 5, 3, 4, 2), 3))
    }
}
