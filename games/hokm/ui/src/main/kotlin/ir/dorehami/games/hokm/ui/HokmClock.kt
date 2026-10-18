package ir.dorehami.games.hokm.ui

/**
 * HK-023 (clock slice) — clock helpers as **pure, unit-testable** logic.
 */
object HokmClock {

    fun now(): Long = System.currentTimeMillis()

    fun elapsed(startMs: Long, nowMs: Long): Long = (nowMs - startMs).coerceAtLeast(0)

    fun isExpired(startMs: Long, durationMs: Long, nowMs: Long): Boolean =
        elapsed(startMs, nowMs) >= durationMs
}
