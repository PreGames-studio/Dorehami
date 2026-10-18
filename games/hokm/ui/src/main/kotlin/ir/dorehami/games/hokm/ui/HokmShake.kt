package ir.dorehami.games.hokm.ui

/**
 * GX-04 (shake slice) — shake helpers as **pure, unit-testable** logic.
 */
object HokmShake {

    fun offset(timeMs: Long, durationMs: Long, amplitude: Float): Float {
        if (timeMs > durationMs) return 0f
        val progress = timeMs.toFloat() / durationMs.toFloat()
        val decay = 1f - progress
        return amplitude * decay * kotlin.math.sin(progress * 10 * Math.PI).toFloat()
    }
}
