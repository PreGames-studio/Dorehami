package ir.dorehami.games.hokm.ui

/**
 * HK-048 (achievement slice) — achievement helpers as **pure, unit-testable** logic.
 */
object HokmAchievement {

    enum class Achievement { FIRST_WIN, KOT_MASTER, TEN_WINS, HUNDRED_MATCHES }

    fun isUnlocked(a: Achievement, stats: HokmStats.Stats): Boolean = when (a) {
        Achievement.FIRST_WIN -> stats.matchesWon >= 1
        Achievement.KOT_MASTER -> stats.kots >= 5
        Achievement.TEN_WINS -> stats.matchesWon >= 10
        Achievement.HUNDRED_MATCHES -> stats.matchesPlayed >= 100
    }

    fun unlockedCount(stats: HokmStats.Stats): Int = Achievement.values().count { isUnlocked(it, stats) }
}
