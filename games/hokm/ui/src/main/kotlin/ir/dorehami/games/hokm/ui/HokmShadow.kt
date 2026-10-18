package ir.dorehami.games.hokm.ui

/**
 * GX-05 (shadow slice) — shadow helpers as **pure, unit-testable** logic.
 */
object HokmShadow {

    data class Shadow(val blur: Float, val offsetX: Float, val offsetY: Float, val alpha: Float)

    fun cardShadow(elevated: Boolean): Shadow =
        if (elevated) Shadow(8f, 0f, 4f, 0.3f) else Shadow(2f, 0f, 1f, 0.15f)

    fun contactShadow(): Shadow = Shadow(3f, 0f, 2f, 0.25f)

    fun isVisible(s: Shadow): Boolean = s.alpha > 0f
}
