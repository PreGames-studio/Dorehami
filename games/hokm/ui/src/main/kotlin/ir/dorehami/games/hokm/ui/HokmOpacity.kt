package ir.dorehami.games.hokm.ui

/**
 * GX-05 (opacity slice) — opacity helpers as **pure, unit-testable** logic.
 */
object HokmOpacity {

    fun clamp(v: Float): Float = v.coerceIn(0f, 1f)

    fun fadeOut(progress: Float): Float = 1f - clamp(progress)

    fun fadeIn(progress: Float): Float = clamp(progress)

    fun disabledAlpha(enabled: Boolean): Float = if (enabled) 1f else 0.38f
}
