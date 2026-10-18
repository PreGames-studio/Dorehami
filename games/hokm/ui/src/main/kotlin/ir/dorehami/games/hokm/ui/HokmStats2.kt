package ir.dorehami.games.hokm.ui

/**
 * HK-048 (statistics slice) — statistics helpers as **pure, unit-testable** logic.
 */
object HokmStats2 {

    fun mean(values: List<Float>): Float =
        if (values.isEmpty()) 0f else values.sum() / values.size

    fun max(values: List<Float>): Float = values.maxOrNull() ?: 0f

    fun min(values: List<Float>): Float = values.minOrNull() ?: 0f

    fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)
}
