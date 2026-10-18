package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmMoveTest {

    @Test
    fun `create move`() {
        val m = HokmMove.create(0, "A♠", 100)
        assertEquals(0, m.seat)
        assertEquals("A♠", m.card)
        assertEquals(100L, m.timestampMs)
    }

    @Test
    fun `valid move`() {
        val m = HokmMove.create(0, "A♠", 0)
        assertTrue(HokmMove.isValid(m, listOf("A♠", "K♥")))
    }

    @Test
    fun `invalid move`() {
        val m = HokmMove.create(0, "Q♣", 0)
        assertTrue(!HokmMove.isValid(m, listOf("A♠", "K♥")))
    }

    @Test
    fun `format non-empty`() {
        val m = HokmMove.create(0, "A♠", 0)
        assertTrue(HokmMove.format(m).isNotEmpty())
    }
}
