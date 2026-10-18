package ir.dorehami.games.hokm.ui

/**
 * GX-04 (pulse slice) — pulse helpers as **pure, unit-testable** logic.
 */
object HokmPulse {

    fun value(timeMs: Long, periodMs: Long): Float {
        if (periodMs <= 0) return 0f
        val phase = (timeMs % periodMs).toFloat() / periodMs.toFloat()
        return (kotlin.math.sin(phase * 2 * Math.PI).toFloat() + 1f) / 2f
    }
}
