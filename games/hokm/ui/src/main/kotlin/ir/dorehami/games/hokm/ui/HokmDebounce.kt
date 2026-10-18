package ir.dorehami.games.hokm.ui

/**
 * HK-010/HK-019 (debounce slice) — debounce as **pure, unit-testable** logic.
 */
object HokmDebounce {

    data class State(val lastActionMs: Long = 0, val pending: Boolean = false)

    fun shouldFire(s: State, nowMs: Long, delayMs: Long): Boolean =
        (nowMs - s.lastActionMs) >= delayMs

    fun record(s: State, nowMs: Long): State = s.copy(lastActionMs = nowMs, pending = false)
}
