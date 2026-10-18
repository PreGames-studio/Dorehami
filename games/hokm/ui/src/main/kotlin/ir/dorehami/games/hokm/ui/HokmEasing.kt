package ir.dorehami.games.hokm.ui

/**
 * GX-04 (easing slice) — easing functions as **pure, unit-testable** logic.
 */
object HokmEasing {

    fun linear(t: Float): Float = t.coerceIn(0f, 1f)

    fun easeIn(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return c * c
    }

    fun easeOut(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return 1f - (1f - c) * (1f - c)
    }

    fun easeInOut(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return if (c < 0.5f) 2f * c * c else 1f - ((-2f * c + 2f) * (-2f * c + 2f)) / 2f
    }
}
