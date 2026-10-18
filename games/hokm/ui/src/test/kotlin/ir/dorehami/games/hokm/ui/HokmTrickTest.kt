package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmTrickTest {

    @Test
    fun `legal when following suit`() {
        val hand = listOf("3♥", "A♠")
        assertTrue(HokmTrick.isLegal("3♥", hand, '♥'))
    }

    @Test
    fun `illegal when not following suit`() {
        val hand = listOf("3♥", "A♠")
        assertTrue(!HokmTrick.isLegal("A♠", hand, '♥'))
    }

    @Test
    fun `legal any when void in lead`() {
        val hand = listOf("A♠", "K♠")
        assertTrue(HokmTrick.isLegal("A♠", hand, '♥'))
    }

    @Test
    fun `legal when leading`() {
        val hand = listOf("A♠", "K♥")
        assertTrue(HokmTrick.isLegal("A♠", hand, null))
    }

    @Test
    fun `illegal when card not in hand`() {
        val hand = listOf("A♠")
        assertTrue(!HokmTrick.isLegal("K♥", hand, null))
    }

    @Test
    fun `trump beats non-trump`() {
        val plays = listOf(0 to "A♥", 1 to "2♠")
        assertEquals(1, HokmTrick.winner(plays, '♠', '♥'))
    }

    @Test
    fun `higher of same suit wins`() {
        val plays = listOf(0 to "9♥", 1 to "K♥")
        assertEquals(1, HokmTrick.winner(plays, '♠', '♥'))
    }

    @Test
    fun `lead suit detected`() {
        val plays = listOf(0 to "9♥", 1 to "K♠")
        assertEquals('♥', HokmTrick.leadSuit(plays))
    }

    @Test
    fun `empty plays winner -1`() {
        assertEquals(-1, HokmTrick.winner(emptyList(), '♠', '♥'))
    }
}
