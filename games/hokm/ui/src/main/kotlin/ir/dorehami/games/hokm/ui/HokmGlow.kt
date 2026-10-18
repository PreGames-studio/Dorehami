package ir.dorehami.games.hokm.ui

/**
 * GX-05 (glow slice) — glow helpers as **pure, unit-testable** logic.
 */
object HokmGlow {

    fun intensity(highlighted: Boolean, pulse: Float): Float {
        val base = if (highlighted) 1f else 0f
        return (base + pulse * 0.3f).coerceIn(0f, 1f)
    }

    fun radius(highlighted: Boolean): Float = if (highlighted) 16f else 0f

    fun shouldGlow(highlighted: Boolean, reducedMotion: Boolean): Boolean = highlighted && !reducedMotion
}
