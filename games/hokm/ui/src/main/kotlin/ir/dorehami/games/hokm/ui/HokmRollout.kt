package ir.dorehami.games.hokm.ui

/**
 * HK-053 (rollout slice) — rollout helpers as **pure, unit-testable** logic.
 */
object HokmRollout {

    fun isEnabled(userId: String, percentage: Int): Boolean {
        val hash = HokmHash.djb2(userId)
        val bucket = (hash % 100).toInt().let { if (it < 0) it + 100 else it }
        return bucket < percentage
    }

    fun clampPercentage(p: Int): Int = p.coerceIn(0, 100)
}
