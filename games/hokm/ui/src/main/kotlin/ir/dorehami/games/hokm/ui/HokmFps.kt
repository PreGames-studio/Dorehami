package ir.dorehami.games.hokm.ui

/**
 * GX-06 (fps slice) — fps helpers as **pure, unit-testable** logic.
 */
object HokmFps {

    fun isSmooth(fps: Float): Boolean = fps >= 55f

    fun frameTimeMs(fps: Float): Float = if (fps <= 0f) 0f else 1000f / fps

    fun qualityForFps(fps: Float): String = when {
        fps >= 55f -> "high"
        fps >= 40f -> "medium"
        else -> "low"
    }
}
