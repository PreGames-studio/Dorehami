package ir.dorehami.games.hokm.ui

/**
 * GX-04 (skew slice) — skew helpers as **pure, unit-testable** logic.
 */
object HokmSkew {

    fun clamp(degrees: Float): Float = degrees.coerceIn(-10f, 10f)
}
