package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmMatchHistoryTest {

    private fun entry(won: Boolean, kot: Boolean = false) =
        HokmMatchHistory.Entry("id", won, 7, 3, 10, 0, kot)

    @Test
    fun `add grows history`() {
        val h = HokmMatchHistory.add(emptyList(), entry(true))
        assertEquals(1, h.size)
    }

    @Test
    fun `add caps at max`() {
        var h = emptyList<HokmMatchHistory.Entry>()
        repeat(HokmMatchHistory.MAX_ENTRIES + 5) { i ->
            h = HokmMatchHistory.add(h, HokmMatchHistory.Entry("id$i", true, 7, 3, 10, i.toLong()))
        }
        assertEquals(HokmMatchHistory.MAX_ENTRIES, h.size)
    }

    @Test
    fun `total wins`() {
        val h = listOf(entry(true), entry(false), entry(true))
        assertEquals(2, HokmMatchHistory.totalWins(h))
    }

    @Test
    fun `win rate`() {
        val h = listOf(entry(true), entry(false))
        assertEquals(0.5f, HokmMatchHistory.winRate(h))
    }

    @Test
    fun `win rate empty`() {
        assertEquals(0f, HokmMatchHistory.winRate(emptyList()))
    }

    @Test
    fun `kots count`() {
        val h = listOf(entry(true, true), entry(true, false))
        assertEquals(1, HokmMatchHistory.kots(h))
    }

    @Test
    fun `best streak`() {
        val h = listOf(entry(true), entry(true), entry(false), entry(true))
        // reversed: true, false, true, true -> streaks: 1, 0, 1, 2 -> best 2
        assertEquals(2, HokmMatchHistory.bestStreak(h))
    }

    @Test
    fun `recent wins`() {
        val h = listOf(entry(true), entry(false), entry(true), entry(true))
        assertEquals(2, HokmMatchHistory.recentWins(h, 3))
    }

    @Test
    fun `summary non-empty`() {
        val h = listOf(entry(true))
        assertTrue(HokmMatchHistory.summary(h).isNotEmpty())
    }
}
