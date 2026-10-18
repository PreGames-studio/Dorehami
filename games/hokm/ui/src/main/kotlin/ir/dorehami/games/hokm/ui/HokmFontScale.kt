package ir.dorehami.games.hokm.ui

/**
 * HK-013/HK-016 (font scale slice) — font scale as **pure, unit-testable** logic.
 *
 * The roadmap asks for 200% font support. This object defines scaling and clamping.
 */
object HokmFontScale {

    const val MIN_SCALE: Float = 0.85f
    const val MAX_SCALE: Float = 2.0f
    const val DEFAULT_SCALE: Float = 1.0f

    fun clamp(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    fun scaledSize(baseSp: Float, scale: Float): Float = baseSp * clamp(scale)

    fun isLarge(scale: Float): Boolean = clamp(scale) >= 1.5f

    fun isAccessibility(scale: Float): Boolean = clamp(scale) >= 2.0f
}
