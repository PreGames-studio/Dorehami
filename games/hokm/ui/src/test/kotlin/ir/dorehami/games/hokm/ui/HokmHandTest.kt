package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmHandTest {

    @Test
    fun `size`() {
        assertEquals(2, HokmHand.size(listOf("A♠", "K♥")))
    }

    @Test
    fun `is empty`() {
        assertTrue(HokmHand.isEmpty(emptyList()))
        assertTrue(!HokmHand.isEmpty(listOf("A♠")))
    }

    @Test
    fun `contains`() {
        assertTrue(HokmHand.contains(listOf("A♠", "K♥"), "A♠"))
        assertTrue(!HokmHand.contains(listOf("A♠"), "K♥"))
    }

    @Test
    fun `remove`() {
        assertEquals(listOf("K♥"), HokmHand.remove(listOf("A♠", "K♥"), "A♠"))
    }

    @Test
    fun `add`() {
        assertEquals(listOf("A♠", "K♥"), HokmHand.add(listOf("A♠"), "K♥"))
    }

    @Test
    fun `suits`() {
        assertEquals(setOf('♠', '♥'), HokmHand.suits(listOf("A♠", "K♥")))
    }

    @Test
    fun `has suit`() {
        assertTrue(HokmHand.hasSuit(listOf("A♠"), '♠'))
        assertTrue(!HokmHand.hasSuit(listOf("A♠"), '♥'))
    }
}
