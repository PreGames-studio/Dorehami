package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmScoringTest {

    @Test
    fun `kot detection`() {
        assertTrue(HokmScoring.isKot(7, 0))
        assertTrue(!HokmScoring.isKot(6, 1))
    }

    @Test
    fun `hakem koti detection`() {
        assertTrue(HokmScoring.isHakemKoti(7, 0, true))
        assertTrue(!HokmScoring.isHakemKoti(7, 0, false))
    }

    @Test
    fun `normal win gives 1 point`() {
        val (o, t) = HokmScoring.handPoints(5, 2, true)
        assertEquals(1, o)
        assertEquals(0, t)
    }

    @Test
    fun `kot gives 2 points`() {
        val (o, t) = HokmScoring.handPoints(7, 0, false)
        assertEquals(2, o)
        assertEquals(0, t)
    }

    @Test
    fun `loss gives opponent point`() {
        val (o, t) = HokmScoring.handPoints(2, 5, true)
        assertEquals(0, o)
        assertEquals(1, t)
    }

    @Test
    fun `match over at 7`() {
        assertTrue(HokmScoring.matchOver(7, 3))
        assertTrue(!HokmScoring.matchOver(6, 3))
    }

    @Test
    fun `winner us`() {
        assertEquals(0, HokmScoring.winner(7, 3))
    }

    @Test
    fun `winner them`() {
        assertEquals(1, HokmScoring.winner(3, 7))
    }

    @Test
    fun `no winner`() {
        assertEquals(-1, HokmScoring.winner(3, 3))
    }
}
