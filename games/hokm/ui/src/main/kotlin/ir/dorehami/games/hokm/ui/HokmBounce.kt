package ir.dorehami.games.hokm.ui

/**
 * GX-04 (bounce slice) — bounce helpers as **pure, unit-testable** logic.
 */
object HokmBounce {

    fun value(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return if (c < 0.5f) 4f * c * c * c else 1f - ((-2f * c + 2f) * (-2f * c + 2f) * (-2f * c + 2f)) / 2f
    }
}
