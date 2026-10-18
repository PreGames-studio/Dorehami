package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmStatsTest {

    @Test
    fun `recordWin increments`() {
        val s = HokmStats.recordWin(HokmStats.Stats(), true, true)
        assertEquals(1, s.matchesPlayed)
        assertEquals(1, s.matchesWon)
        assertEquals(1, s.kots)
        assertEquals(1, s.hakemKotis)
    }

    @Test
    fun `recordLoss increments`() {
        val s = HokmStats.recordLoss(HokmStats.Stats())
        assertEquals(1, s.matchesLost)
    }

    @Test
    fun `recordDraw increments`() {
        val s = HokmStats.recordDraw(HokmStats.Stats())
        assertEquals(1, s.matchesDrawn)
    }

    @Test
    fun `winRate zero when no matches`() {
        assertEquals(0f, HokmStats.winRate(HokmStats.Stats()))
    }

    @Test
    fun `winRate correct`() {
        var s = HokmStats.Stats()
        s = HokmStats.recordWin(s)
        s = HokmStats.recordLoss(s)
        assertEquals(0.5f, HokmStats.winRate(s))
    }

    @Test
    fun `trick win rate`() {
        var s = HokmStats.Stats()
        s = HokmStats.recordTrick(s, true)
        s = HokmStats.recordTrick(s, false)
        assertEquals(0.5f, HokmStats.trickWinRate(s))
    }

    @Test
    fun `consistent stats`() {
        val s = HokmStats.Stats(matchesPlayed = 5, matchesWon = 2, matchesLost = 2, matchesDrawn = 1)
        assertTrue(HokmStats.isConsistent(s))
    }

    @Test
    fun `inconsistent stats`() {
        val s = HokmStats.Stats(matchesPlayed = 2, matchesWon = 2, matchesLost = 2)
        assertFalse(HokmStats.isConsistent(s))
    }

    @Test
    fun `sanitize clamps negatives`() {
        val s = HokmStats.sanitize(HokmStats.Stats(matchesPlayed = -1))
        assertTrue(s.matchesPlayed >= 0)
    }

    @Test
    fun `compare orders by win rate`() {
        val a = HokmStats.Stats(matchesPlayed = 10, matchesWon = 8)
        val b = HokmStats.Stats(matchesPlayed = 10, matchesWon = 5)
        assertTrue(HokmStats.compare(a, b) < 0)
    }
}
