package ir.dorehami.games.chess.ui.gl

/**
 * Theme colors consumed only by the Chess OpenGL renderer. The board squares keep a stable,
 * high-contrast cream/teal scheme while the surrounding table follows the system theme.
 */
internal data class ChessBoardPalette(
    val clearColor: Int,
    val tableColor: Int,
    val frameStart: Int,
    val frameEnd: Int,
    val frameGrain: Int,
    val lightSquare: Int,
    val darkSquare: Int,
    val trim: Int,
    val coordinates: Int,
    val lightPiece: Int,
    val darkPiece: Int,
    val lastMove: Int,
    val selected: Int,
    val quietMove: Int,
    val capture: Int,
    val check: Int,
) {
    companion object {
        val Light = ChessBoardPalette(
            clearColor = 0xFFFFF4DC.toInt(),
            tableColor = 0xFFF8E8CC.toInt(),
            frameStart = 0xFF9A4F20.toInt(),
            frameEnd = 0xFF5C2A00.toInt(),
            frameGrain = 0xFF673715.toInt(),
            lightSquare = 0xFFFFE08A.toInt(),
            darkSquare = 0xFF0E8F87.toInt(),
            trim = 0xFFC9A96B.toInt(),
            coordinates = 0xFF5B4B33.toInt(),
            lightPiece = 0xFFFFF8E8.toInt(),
            darkPiece = 0xFF2E2415.toInt(),
            lastMove = 0xFF7B4CC2.toInt(),
            selected = 0xFFFF8A3D.toInt(),
            quietMove = 0xFF0E8F87.toInt(),
            capture = 0xFFD9480F.toInt(),
            check = 0xFFB3261E.toInt(),
        )

        val Dark = ChessBoardPalette(
            clearColor = 0xFF1E1A26.toInt(),
            tableColor = 0xFF2A2433.toInt(),
            frameStart = 0xFF6A3B00.toInt(),
            frameEnd = 0xFF3B2000.toInt(),
            frameGrain = 0xFF4A2A17.toInt(),
            lightSquare = 0xFFFFE2BF.toInt(),
            darkSquare = 0xFF0E8F87.toInt(),
            trim = 0xFFFFB86B.toInt(),
            coordinates = 0xFFB8F5EE.toInt(),
            lightPiece = 0xFFFFF8E8.toInt(),
            darkPiece = 0xFF2A2433.toInt(),
            lastMove = 0xFFC9A5FF.toInt(),
            selected = 0xFFFFB86B.toInt(),
            quietMove = 0xFF5FD3C7.toInt(),
            capture = 0xFFFF8A3D.toInt(),
            check = 0xFFFFB4A9.toInt(),
        )

        fun forDarkTheme(dark: Boolean): ChessBoardPalette = if (dark) Dark else Light
    }
}
