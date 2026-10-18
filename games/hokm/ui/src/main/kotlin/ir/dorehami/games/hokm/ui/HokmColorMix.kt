package ir.dorehami.games.hokm.ui

/**
 * GX-02 (color mix slice) — color mix helpers as **pure, unit-testable** logic.
 */
object HokmColorMix {

    fun mix(a: HokmColor.RGB, b: HokmColor.RGB, t: Float): HokmColor.RGB {
        val c = t.coerceIn(0f, 1f)
        return HokmColor.RGB(
            (a.r + (b.r - a.r) * c).toInt(),
            (a.g + (b.g - a.g) * c).toInt(),
            (a.b + (b.b - a.b) * c).toInt()
        )
    }

    fun withAlpha(c: HokmColor.RGB, alpha: Float): String = "rgba(${c.r},${c.g},${c.b},${alpha.coerceIn(0f, 1f)})"
}
