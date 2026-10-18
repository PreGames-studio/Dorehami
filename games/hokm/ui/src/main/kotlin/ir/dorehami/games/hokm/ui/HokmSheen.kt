package ir.dorehami.games.hokm.ui

/**
 * GX-05 (sheen slice) — sheen helpers as **pure, unit-testable** logic.
 */
object HokmSheen {

    fun intensity(winner: Boolean, trump: Boolean): Float {
        var s = 0f
        if (winner) s += 0.6f
        if (trump) s += 0.4f
        return s.coerceIn(0f, 1f)
    }

    fun shouldSheen(enabled: Boolean, reducedMotion: Boolean): Boolean = enabled && !reducedMotion
}
