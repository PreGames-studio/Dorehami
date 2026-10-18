package ir.dorehami.games.hokm.ui

/**
 * HK-031 (circuit breaker slice) — circuit breaker helpers as **pure, unit-testable** logic.
 */
object HokmCircuitBreaker {

    enum class State { CLOSED, OPEN, HALF_OPEN }

    data class Breaker(val state: State, val failures: Int, val threshold: Int)

    fun recordFailure(b: Breaker): Breaker {
        val f = b.failures + 1
        val s = if (f >= b.threshold) State.OPEN else b.state
        return b.copy(failures = f, state = s)
    }

    fun reset(b: Breaker): Breaker = b.copy(state = State.CLOSED, failures = 0)

    fun isOpen(b: Breaker): Boolean = b.state == State.OPEN
}
