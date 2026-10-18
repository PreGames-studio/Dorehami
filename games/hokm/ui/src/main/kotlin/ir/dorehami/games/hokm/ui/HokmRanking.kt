package ir.dorehami.games.hokm.ui

/**
 * HK-048 (ranking slice) — ranking helpers as **pure, unit-testable** logic.
 */
object HokmRanking {

    fun rankOf(score: Int, leaderboard: List<Int>): Int {
        val sorted = leaderboard.sortedDescending()
        val idx = sorted.indexOfFirst { score >= it }
        return if (idx < 0) sorted.size + 1 else idx + 1
    }

    fun percentile(score: Int, leaderboard: List<Int>): Float {
        if (leaderboard.isEmpty()) return 1f
        val below = leaderboard.count { it < score }
        return below.toFloat() / leaderboard.size.toFloat()
    }
}
