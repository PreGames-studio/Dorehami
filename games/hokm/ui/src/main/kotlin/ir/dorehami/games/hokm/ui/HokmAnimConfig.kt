package ir.dorehami.games.hokm.ui

/**
 * GX-04 (anim config slice) — animation config as **pure, unit-testable** logic.
 */
object HokmAnimConfig {

    data class Config(val durationMs: Long, val reducedMotion: Boolean, val speedMultiplier: Float)

    fun effectiveDuration(c: Config): Long =
        if (c.reducedMotion) 0L else (c.durationMs * c.speedMultiplier).toLong().coerceAtLeast(0)

    fun shouldAnimate(c: Config): Boolean = !c.reducedMotion && c.speedMultiplier > 0f

    fun clampMultiplier(m: Float): Float = m.coerceIn(0f, 3f)
}
