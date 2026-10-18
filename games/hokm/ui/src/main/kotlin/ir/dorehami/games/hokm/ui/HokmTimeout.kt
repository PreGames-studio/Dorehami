package ir.dorehami.games.hokm.ui

/**
 * HK-034 (timeout slice) — timeout helpers as **pure, unit-testable** logic.
 */
object HokmTimeout {

    fun isExpired(nowMs: Long, deadlineMs: Long): Boolean = nowMs >= deadlineMs

    fun remainingMs(nowMs: Long, deadlineMs: Long): Long = (deadlineMs - nowMs).coerceAtLeast(0)

    fun deadlineFrom(nowMs: Long, timeoutMs: Long): Long = nowMs + timeoutMs
}
