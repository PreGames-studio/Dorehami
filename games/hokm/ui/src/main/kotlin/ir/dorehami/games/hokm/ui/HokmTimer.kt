package ir.dorehami.games.hokm.ui

/**
 * HK-020/HK-034 (first slice) — turn timer as **pure, unit-testable** logic.
 *
 * The roadmap asks for turn timers (online) and reliable UX. This object defines the
 * timer state, countdown, expiry, and pause/resume policy. The Android layer drives the
 * actual clock and calls these pure functions.
 */
object HokmTimer {

    data class State(
        val remainingMs: Long,
        val totalMs: Long,
        val running: Boolean,
        val expired: Boolean
    )

    const val DEFAULT_TURN_MS: Long = 30_000L
    const val WARNING_THRESHOLD_MS: Long = 5_000L

    fun start(totalMs: Long = DEFAULT_TURN_MS): State =
        State(remainingMs = totalMs, totalMs = totalMs, running = true, expired = false)

    fun tick(s: State, elapsedMs: Long): State {
        if (!s.running || s.expired) return s
        val remaining = (s.remainingMs - elapsedMs).coerceAtLeast(0)
        return s.copy(remainingMs = remaining, expired = remaining == 0L)
    }

    fun pause(s: State): State = s.copy(running = false)

    fun resume(s: State): State = if (s.expired) s else s.copy(running = true)

    fun reset(s: State, totalMs: Long = s.totalMs): State =
        State(remainingMs = totalMs, totalMs = totalMs, running = true, expired = false)

    fun isWarning(s: State): Boolean = s.running && s.remainingMs <= WARNING_THRESHOLD_MS && !s.expired

    fun progress(s: State): Float =
        if (s.totalMs == 0L) 0f else s.remainingMs.toFloat() / s.totalMs.toFloat()

    fun expired(s: State): Boolean = s.expired
}
