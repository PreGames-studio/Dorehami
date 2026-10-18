package ir.dorehami.games.hokm.ui

/**
 * GX-04 (wave slice) — wave helpers as **pure, unit-testable** logic.
 */
object HokmWave {

    fun value(timeMs: Long, periodMs: Long, amplitude: Float = 1f): Float {
        if (periodMs <= 0) return 0f
        val phase = (timeMs % periodMs).toFloat() / periodMs.toFloat()
        return amplitude * kotlin.math.sin(phase * 2 * Math.PI).toFloat()
    }
}
