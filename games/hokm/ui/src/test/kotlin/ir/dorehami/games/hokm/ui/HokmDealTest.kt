package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmDealTest {

    @Test
    fun `find hakem by ace`() {
        val hands = listOf(
            listOf("K♠", "Q♥"),
            listOf("A♦", "2♣"),
            listOf("3♠", "4♥"),
            listOf("5♦", "6♣")
        )
        assertEquals(1, HokmDeal.findHakem(hands))
    }

    @Test
    fun `find hakem defaults to 0`() {
        val hands = listOf(listOf("K♠"), listOf("Q♥"))
        assertEquals(0, HokmDeal.findHakem(hands))
    }

    @Test
    fun `first deal count 5`() {
        assertEquals(5, HokmDeal.firstDealCount())
    }

    @Test
    fun `cards per player 13`() {
        assertEquals(13, HokmDeal.cardsPerPlayer())
    }

    @Test
    fun `deal complete check`() {
        val hands = listOf(List(13) { "X" }, List(13) { "Y" })
        assertTrue(HokmDeal.isDealComplete(hands))
        val partial = listOf(List(5) { "X" })
        assertTrue(!HokmDeal.isDealComplete(partial))
    }
}
