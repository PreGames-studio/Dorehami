package ir.dorehami.games.hokm.ui

/**
 * GX-05 (blur slice) — blur helpers as **pure, unit-testable** logic.
 */
object HokmBlur {

    fun blurRadius(quality: String): Float = when (quality) {
        "high" -> 12f
        "medium" -> 6f
        "low" -> 2f
        else -> 0f
    }

    fun shouldBlur(enabled: Boolean, reducedMotion: Boolean): Boolean = enabled && !reducedMotion
}
