package ir.dorehami.games.hokm.ui

/**
 * HK-031 (ping slice) — ping helpers as **pure, unit-testable** logic.
 */
object HokmPing {

    fun isGood(pingMs: Int): Boolean = pingMs < 100

    fun isAcceptable(pingMs: Int): Boolean = pingMs < 300

    fun quality(pingMs: Int): String = when {
        pingMs < 50 -> "excellent"
        pingMs < 100 -> "good"
        pingMs < 300 -> "fair"
        else -> "poor"
    }
}
