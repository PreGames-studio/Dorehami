package ir.dorehami.games.chess.ui

/**
 * Sound names used by the chess table → bundled raw resources.
 *
 * The names emitted by the rules (`Emission.Sound`) are the first block; the ones added by the
 * screen (`select`, `illegal`, …) are UI-only. All files are synthesized in-house — no licences.
 */
object ChessSounds {
    val map: Map<String, Int> = mapOf(
        "move" to R.raw.chess_move,
        "capture" to R.raw.chess_capture,
        "check" to R.raw.chess_check,
        "castle" to R.raw.chess_castle,
        "promote" to R.raw.chess_promote,
        "notify" to R.raw.chess_notify,
        "select" to R.raw.chess_select,
        "illegal" to R.raw.chess_illegal,
        "undo" to R.raw.chess_undo,
        "win" to R.raw.chess_win,
        "lose" to R.raw.chess_lose,
        "draw" to R.raw.chess_draw,
    )
}
