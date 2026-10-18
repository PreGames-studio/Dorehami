package ir.dorehami.games.hokm.ui

/**
 * HK-048 (reward slice) — reward helpers as **pure, unit-testable** logic.
 */
object HokmReward {

    fun matchReward(won: Boolean, kot: Boolean): Int = when {
        kot -> 100
        won -> 50
        else -> 10
    }

    fun dailyReward(streak: Int): Int = (streak.coerceIn(1, 7) * 10)
}
