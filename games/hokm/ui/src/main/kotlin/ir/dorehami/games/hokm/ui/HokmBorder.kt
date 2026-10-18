package ir.dorehami.games.hokm.ui

/**
 * GX-05 (border slice) — border helpers as **pure, unit-testable** logic.
 */
object HokmBorder {

    data class Border(val width: Float, val radius: Float, val color: String)

    fun cardBorder(selected: Boolean): Border =
        if (selected) Border(2f, 8f, "#FFD700") else Border(1f, 8f, "#888888")

    fun tableBorder(): Border = Border(3f, 16f, "#444444")
}
