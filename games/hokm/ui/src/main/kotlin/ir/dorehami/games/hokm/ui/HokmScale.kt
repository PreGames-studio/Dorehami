package ir.dorehami.games.hokm.ui

/**
 * GX-04 (scale slice) — scale helpers as **pure, unit-testable** logic.
 */
object HokmScale {

    fun clamp(v: Float): Float = v.coerceIn(0.5f, 2f)

    fun selectedScale(selected: Boolean): Float = if (selected) 1.15f else 1.0f

    fun pressedScale(pressed: Boolean): Float = if (pressed) 0.95f else 1.0f
}
