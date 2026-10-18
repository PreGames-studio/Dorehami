package ir.dorehami.games.hokm.ui

/**
 * HK-033 (first slice) — reconnection state as **pure, unit-testable** logic.
 *
 * The roadmap asks for reliable reconnect (session/room). This object defines the
 * reconnection state machine: disconnected -> reconnecting -> reconnected/failed,
 * with attempt counting and backoff. The Android layer drives the network calls.
 */
object HokmReconnect {

    enum class State { CONNECTED, DISCONNECTED, RECONNECTING, RECONNECTED, FAILED }

    data class Status(
        val state: State,
        val attempts: Int,
        val lastError: String?
    )

    const val MAX_ATTEMPTS: Int = 5
    const val BASE_BACKOFF_MS: Long = 1000L
    const val MAX_BACKOFF_MS: Long = 16000L

    fun initial(): Status = Status(State.CONNECTED, 0, null)

    fun disconnect(s: Status): Status = s.copy(state = State.DISCONNECTED, lastError = null)

    fun startReconnect(s: Status): Status {
        if (s.state == State.CONNECTED || s.state == State.RECONNECTED) return s
        return s.copy(state = State.RECONNECTING)
    }

    fun reconnectSuccess(s: Status): Status = s.copy(state = State.RECONNECTED, lastError = null)

    fun reconnectFail(s: Status, error: String): Status {
        val attempts = s.attempts + 1
        val failed = attempts >= MAX_ATTEMPTS
        return s.copy(
            state = if (failed) State.FAILED else State.DISCONNECTED,
            attempts = attempts,
            lastError = error
        )
    }

    fun backoffMs(attempts: Int): Long {
        val raw = BASE_BACKOFF_MS * (1L shl attempts.coerceAtMost(4))
        return raw.coerceAtMost(MAX_BACKOFF_MS)
    }

    fun canRetry(s: Status): Boolean = s.state != State.FAILED && s.attempts < MAX_ATTEMPTS

    fun reset(s: Status): Status = Status(State.CONNECTED, 0, null)
}
