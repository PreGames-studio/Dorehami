package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmShuffleTest {

    @Test
    fun `shuffle is permutation`() {
        val deck = listOf("A", "B", "C", "D", "E")
        val shuffled = HokmShuffle.shuffle(deck, 42)
        assertTrue(HokmShuffle.isPermutation(deck, shuffled))
    }

    @Test
    fun `shuffle deterministic`() {
        val deck = listOf("A", "B", "C", "D", "E")
        assertEquals(HokmShuffle.shuffle(deck, 42), HokmShuffle.shuffle(deck, 42))
    }

    @Test
    fun `shuffle changes order (usually)`() {
        val deck = (1..20).map { it.toString() }
        val shuffled = HokmShuffle.shuffle(deck, 42)
        assertTrue(shuffled != deck)
    }

    @Test
    fun `cut splits correctly`() {
        val deck = listOf("A", "B", "C", "D")
        assertEquals(listOf("C", "D", "A", "B"), HokmShuffle.cut(deck, 2))
    }

    @Test
    fun `cut at 0 returns same`() {
        val deck = listOf("A", "B", "C")
        assertEquals(deck, HokmShuffle.cut(deck, 0))
    }
}
