package ir.dorehami.gamesdk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DeterminismTest {
    @Test
    fun `same seed same stream`() {
        val a = SplitMixRng(12345L)
        val b = SplitMixRng(12345L)
        repeat(1000) { assertEquals(a.nextInt(52), b.nextInt(52)) }
    }

    @Test
    fun `nextInt stays in range and covers it`() {
        val r = SplitMixRng(1L)
        val seen = HashSet<Int>()
        repeat(5000) {
            val v = r.nextInt(13)
            assertTrue(v in 0 until 13)
            seen += v
        }
        assertEquals(13, seen.size)
    }

    @Test
    fun `shuffle is a permutation and depends only on the seed`() {
        val list = (1..52).toList()
        val x = SplitMixRng(9L).shuffle(list)
        val y = SplitMixRng(9L).shuffle(list)
        assertEquals(x, y)
        assertEquals(list.toSet(), x.toSet())
        assertEquals(52, x.size)
    }

    @Test
    fun `manual clock only moves forward`() {
        val c = ManualClock()
        c.advance(10)
        assertEquals(10L, c.nowMillis())
        assertFailsWith<IllegalArgumentException> { c.advance(-1) }
    }
}
