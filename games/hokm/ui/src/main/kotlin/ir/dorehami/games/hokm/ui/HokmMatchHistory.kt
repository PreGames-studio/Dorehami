package ir.dorehami.games.hokm.ui

/**
 * HK-048 (history slice) — match history as **pure, unit-testable** logic.
 *
 * The roadmap asks for match history / previous stats. This object defines the history
 * entry data class, a bounded history buffer, and summary helpers. The Android layer
 * persists.
 */
object HokmMatchHistory {

    data class Entry(
        val matchId: String,
        val won: Boolean,
        val ourScore: Int,
        val theirScore: Int,
        val handsPlayed: Int,
        val timestampMs: Long,
        val kot: Boolean = false
    )

    const val MAX_ENTRIES: Int = 50

    fun add(history: List<Entry>, entry: Entry): List<Entry> {
        val new = listOf(entry) + history
        return if (new.size > MAX_ENTRIES) new.take(MAX_ENTRIES) else new
    }

    fun recentWins(history: List<Entry>, n: Int): Int =
        history.take(n).count { it.won }

    fun totalWins(history: List<Entry>): Int = history.count { it.won }

    fun totalLosses(history: List<Entry>): Int = history.count { !it.won }

    fun winRate(history: List<Entry>): Float =
        if (history.isEmpty()) 0f else totalWins(history).toFloat() / history.size.toFloat()

    fun kots(history: List<Entry>): Int = history.count { it.kot }

    fun bestStreak(history: List<Entry>): Int {
        var best = 0
        var cur = 0
        for (e in history.reversed()) {
            if (e.won) { cur += 1; if (cur > best) best = cur } else cur = 0
        }
        return best
    }

    fun summary(history: List<Entry>): String =
        "بازی: ${history.size} — برد: ${totalWins(history)} — باخت: ${totalLosses(history)}"
}
