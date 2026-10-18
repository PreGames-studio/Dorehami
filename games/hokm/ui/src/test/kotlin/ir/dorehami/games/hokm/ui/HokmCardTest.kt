package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HokmCardTest {

    @Test
    fun `parse valid`() {
        val c = HokmCard.parse("A♠")
        assertEquals(HokmCard.Card('A', '♠'), c)
    }

    @Test
    fun `parse invalid`() {
        assertNull(HokmCard.parse("X"))
        assertNull(HokmCard.parse("AZ"))
    }

    @Test
    fun `full deck size 52`() {
        assertEquals(52, HokmCard.fullDeck().size)
    }

    @Test
    fun `full deck unique`() {
        assertEquals(52, HokmCard.fullDeck().toSet().size)
    }

    @Test
    fun `is red`() {
        assertTrue(HokmCard.isRed(HokmCard.Card('A', '♥')))
        assertTrue(!HokmCard.isRed(HokmCard.Card('A', '♠')))
    }

    @Test
    fun `label non-empty`() {
        assertTrue(HokmCard.labelFa(HokmCard.Card('A', '♠')).isNotEmpty())
    }
}
