package ir.dorehami.games.hokm.ui

/**
 * GX-05 (vignette slice) — vignette helpers as **pure, unit-testable** logic.
 */
object HokmVignette {

    fun opacity(tier: String): Float = when (tier) {
        "high" -> 0.6f
        "medium" -> 0.4f
        "low" -> 0.2f
        else -> 0f
    }

    fun isEnabled(quality: String): Boolean = quality != "off"
}
