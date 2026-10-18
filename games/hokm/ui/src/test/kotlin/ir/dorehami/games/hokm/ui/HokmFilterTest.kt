package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmFilterTest {

    @Test
    fun `by suit`() {
        val cards = listOf("A♠", "K♥", "Q♠")
        assertEquals(listOf("A♠", "Q♠"), HokmFilter.bySuit(cards, '♠'))
    }

    @Test
    fun `by rank`() {
        val cards = listOf("A♠", "A♥", "K♠")
        assertEquals(listOf("A♠", "A♥"), HokmFilter.byRank(cards, 'A'))
    }

    @Test
    fun `non trump`() {
        val cards = listOf("A♠", "K♥")
        assertEquals(listOf("K♥"), HokmFilter.nonTrump(cards, '♠'))
    }

    @Test
    fun `trumps`() {
        val cards = listOf("A♠", "K♥")
        assertEquals(listOf("A♠"), HokmFilter.trumps(cards, '♠'))
    }
}
