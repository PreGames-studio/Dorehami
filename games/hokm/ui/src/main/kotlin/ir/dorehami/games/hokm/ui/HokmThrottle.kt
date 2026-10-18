package ir.dorehami.games.hokm.ui

/**
 * HK-023 (throttle slice) — throttle helpers as **pure, unit-testable** logic.
 */
object HokmThrottle {

    fun canFire(lastFireMs: Long, nowMs: Long, intervalMs: Long): Boolean =
        nowMs - lastFireMs >= intervalMs

    fun nextFireMs(lastFireMs: Long, intervalMs: Long): Long = lastFireMs + intervalMs
}
