package ir.dorehami.games.hokm.ui

/**
 * HK-033 (backoff slice) — backoff helpers as **pure, unit-testable** logic.
 */
object HokmBackoff {

    fun nextDelayMs(attempt: Int, baseMs: Long, maxMs: Long): Long {
        val raw = baseMs * (1L shl attempt.coerceAtMost(20))
        return raw.coerceAtMost(maxMs)
    }

    fun shouldRetry(attempt: Int, maxAttempts: Int): Boolean = attempt < maxAttempts
}
