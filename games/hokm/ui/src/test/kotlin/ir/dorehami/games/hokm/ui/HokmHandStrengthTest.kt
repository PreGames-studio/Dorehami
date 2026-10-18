package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmHandStrengthTest {

    @Test
    fun `trump count`() {
        assertEquals(2, HokmHandStrength.trumpCount(listOf("A♠", "K♠", "3♥"), '♠'))
    }

    @Test
    fun `ace count`() {
        assertEquals(1, HokmHandStrength.aceCount(listOf("A♠", "K♥")))
    }

    @Test
    fun `has void`() {
        assertTrue(HokmHandStrength.hasVoid(listOf("A♠", "K♥"), '♦'))
        assertTrue(!HokmHandStrength.hasVoid(listOf("A♠", "K♦"), '♦'))
    }

    @Test
    fun `void count`() {
        val suits = listOf('♠', '♥', '♦', '♣')
        assertEquals(2, HokmHandStrength.voidCount(listOf("A♠", "K♥"), suits))
    }

    @Test
    fun `score in range`() {
        val s = HokmHandStrength.score(listOf("A♠", "K♠", "Q♠"), '♠')
        assertTrue(s in 0..100)
    }

    @Test
    fun `stronger hand higher score`() {
        val strong = HokmHandStrength.score(listOf("A♠", "K♠", "Q♠", "J♠"), '♠')
        val weak = HokmHandStrength.score(listOf("2♣", "3♦", "4♥"), '♠')
        assertTrue(strong > weak)
    }
}
