package ir.dorehami.games.hokm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The table deals the rest of the deck one card at a time, so the order it walks has to be the real
 * one: five to everybody but the hakem, then rounds of four. A wrong order still ends with full
 * hands, but it looks wrong (and it is the kind of thing nobody notices until a player counts).
 */
class HokmDealOrderTest {

    @Test
    fun `four players - the dealer starts left of the hakem and finishes 13 each`() {
        val hakem = 2
        val order = HokmRules.dealOrder(players = 4, hakem = hakem)
        // 52 cards minus the hakem's first five.
        assertEquals(47, order.size, "every remaining card is dealt exactly once")

        // Five to each of the other three seats first, in turn order.
        assertEquals(listOf(3, 3, 3, 3, 3, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1), order.take(15))

        val counts = IntArray(4) { if (it == hakem) HokmRules.HAKEM_FIRST_DEAL else 0 }
        order.forEach { counts[it]++ }
        assertTrue(counts.all { it == 13 }, "each seat ends with 13 cards, got ${counts.toList()}")
    }

    @Test
    fun `three players - 51 cards split 17 each with the deuce already out`() {
        val order = HokmRules.dealOrder(players = 3, hakem = 0)
        assertEquals(51 - HokmRules.HAKEM_FIRST_DEAL, order.size)
        val counts = IntArray(3) { if (it == 0) HokmRules.HAKEM_FIRST_DEAL else 0 }
        order.forEach { counts[it]++ }
        assertTrue(counts.all { it == 17 }, "each seat ends with 17 cards, got ${counts.toList()}")
    }

    @Test
    fun `two players deal from the stock instead, so there is no deck order`() {
        assertTrue(HokmRules.dealOrder(players = 2, hakem = 0).isEmpty())
    }
}
