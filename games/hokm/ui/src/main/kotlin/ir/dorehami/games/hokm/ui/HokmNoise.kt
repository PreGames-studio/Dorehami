package ir.dorehami.games.hokm.ui

/**
 * GX-05 (noise slice) — noise helpers as **pure, unit-testable** logic.
 */
object HokmNoise {

    fun value(seed: Long, x: Int, y: Int): Float {
        val h = seed + x * 374761393L + y * 668265263L
        return ((h ushr 16) and 0xFFFF).toFloat() / 65536f
    }

    fun isEnabled(quality: String): Boolean = quality != "low"
}
