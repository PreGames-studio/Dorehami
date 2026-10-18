package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmSuitTest {

    @Test
    fun `all has 4 suits`() {
        assertEquals(4, HokmSuit.ALL.size)
    }

    @Test
    fun `red suits`() {
        assertTrue(HokmSuit.isRed('♥'))
        assertTrue(HokmSuit.isRed('♦'))
        assertTrue(!HokmSuit.isRed('♠'))
    }

    @Test
    fun `black suits`() {
        assertTrue(HokmSuit.isBlack('♠'))
        assertTrue(HokmSuit.isBlack('♣'))
        assertTrue(!HokmSuit.isBlack('♥'))
    }

    @Test
    fun `valid suit`() {
        assertTrue(HokmSuit.isValid('♠'))
        assertTrue(!HokmSuit.isValid('X'))
    }

    @Test
    fun `names non-empty`() {
        HokmSuit.ALL.forEach { s ->
            assertTrue(HokmSuit.nameFa(s).isNotEmpty())
        }
    }
}
