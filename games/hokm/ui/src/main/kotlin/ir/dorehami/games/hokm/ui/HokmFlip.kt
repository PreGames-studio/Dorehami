package ir.dorehami.games.hokm.ui

/**
 * GX-04 (flip slice) — flip helpers as **pure, unit-testable** logic.
 */
object HokmFlip {

    fun isFlipped(progress: Float): Boolean = progress >= 0.5f

    fun frontAlpha(progress: Float): Float = if (progress < 0.5f) 1f - progress * 2f else 0f

    fun backAlpha(progress: Float): Float = if (progress < 0.5f) 0f else (progress - 0.5f) * 2f
}
