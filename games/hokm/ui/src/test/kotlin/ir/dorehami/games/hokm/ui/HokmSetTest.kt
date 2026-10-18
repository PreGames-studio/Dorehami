package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HokmSetTest {

    @Test
    fun `add remove contains`() {
        var s = HokmSet.add(emptySet<Int>(), 1)
        assertTrue(HokmSet.contains(s, 1))
        s = HokmSet.remove(s, 1)
        assertFalse(HokmSet.contains(s, 1))
    }

    @Test
    fun `union intersect`() {
        val a = setOf(1, 2)
        val b = setOf(2, 3)
        assertEquals(setOf(1, 2, 3), HokmSet.union(a, b))
        assertEquals(setOf(2), HokmSet.intersect(a, b))
    }

    @Test
    fun `size`() {
        assertEquals(2, HokmSet.size(setOf(1, 2)))
    }
}
