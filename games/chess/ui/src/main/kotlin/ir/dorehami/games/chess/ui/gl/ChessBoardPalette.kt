package ir.dorehami.games.chess.ui.gl

import ir.dorehami.games.chess.ui.ChessBoardStyle
import ir.dorehami.games.chess.ui.ChessPieceStyle

/**
 * Materials for the chess table and pieces. Board squares intentionally stay monochrome for every
 * finish; the selected finish changes the frame, table, engraving and piece material only.
 */
internal data class ChessBoardPalette(
    val boardStyle: ChessBoardStyle,
    val pieceStyle: ChessPieceStyle,
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
    val lightPieceSpecular: Float,
    val darkPieceSpecular: Float,
    val lightPieceShininess: Float,
    val darkPieceShininess: Float,
    val lastMove: Int,
    val selected: Int,
    val quietMove: Int,
    val capture: Int,
    val check: Int,
) {
    companion object {
        private val WHITE_SQUARE = 0xFFF8F8F5.toInt()
        private val BLACK_SQUARE = 0xFF191A1D.toInt()

        /** Builds an appearance without tying the board itself to the Android light/dark theme. */
        fun forAppearance(
            darkTheme: Boolean,
            boardStyle: ChessBoardStyle,
            pieceStyle: ChessPieceStyle,
        ): ChessBoardPalette {
            val board = when (boardStyle) {
                ChessBoardStyle.METAL -> BoardFinish(
                    table = 0xFF65717A.toInt(),
                    start = 0xFFD8E0E5.toInt(),
                    end = 0xFF66737D.toInt(),
                    grain = 0xFFABB7BF.toInt(),
                    trim = 0xFFF1F4F5.toInt(),
                    coordinates = 0xFF343B40.toInt(),
                )
                ChessBoardStyle.WOOD -> BoardFinish(
                    table = 0xFF3C2419.toInt(),
                    start = 0xFF9A6038.toInt(),
                    end = 0xFF452318.toInt(),
                    grain = 0xFF61351F.toInt(),
                    trim = 0xFFD1A161.toInt(),
                    coordinates = 0xFFFFE9C5.toInt(),
                )
                ChessBoardStyle.LUXURY -> BoardFinish(
                    table = 0xFF111116.toInt(),
                    start = 0xFF343238.toInt(),
                    end = 0xFF09090C.toInt(),
                    grain = 0xFF242329.toInt(),
                    trim = 0xFFD8B568.toInt(),
                    coordinates = 0xFFE8CE91.toInt(),
                )
                ChessBoardStyle.TRADITIONAL -> BoardFinish(
                    table = 0xFF173F37.toInt(),
                    start = 0xFF9C482C.toInt(),
                    end = 0xFF4B2119.toInt(),
                    grain = 0xFF6F3323.toInt(),
                    trim = 0xFFE1C578.toInt(),
                    coordinates = 0xFFFFEBD0.toInt(),
                )
                ChessBoardStyle.PAPER -> BoardFinish(
                    table = 0xFFD6C6A8.toInt(),
                    start = 0xFFF0E2C5.toInt(),
                    end = 0xFFB39A73.toInt(),
                    grain = 0xFFB79C73.toInt(),
                    trim = 0xFF785A3E.toInt(),
                    coordinates = 0xFF5B4330.toInt(),
                )
            }
            val pieces = when (pieceStyle) {
                ChessPieceStyle.GLOSSY_METAL -> PieceFinish(
                    light = 0xFFE9F0F5.toInt(), dark = 0xFF4A5159.toInt(),
                    lightSpecular = 0.95f, darkSpecular = 1.08f,
                    lightShininess = 88f, darkShininess = 112f,
                )
                ChessPieceStyle.MATTE_METAL -> PieceFinish(
                    light = 0xFFD9DDE0.toInt(), dark = 0xFF51565B.toInt(),
                    lightSpecular = 0.16f, darkSpecular = 0.20f,
                    lightShininess = 18f, darkShininess = 22f,
                )
                ChessPieceStyle.WOOD -> PieceFinish(
                    light = 0xFFE7D2AC.toInt(), dark = 0xFF5A3F2E.toInt(),
                    lightSpecular = 0.10f, darkSpecular = 0.13f,
                    lightShininess = 12f, darkShininess = 16f,
                )
                ChessPieceStyle.CLASSIC -> PieceFinish(
                    light = 0xFFF5F1E8.toInt(), dark = 0xFF41403D.toInt(),
                    lightSpecular = 0.42f, darkSpecular = 0.55f,
                    lightShininess = 34f, darkShininess = 50f,
                )
            }

            return ChessBoardPalette(
                boardStyle = boardStyle,
                pieceStyle = pieceStyle,
                clearColor = if (darkTheme) 0xFF18171C.toInt() else 0xFFF1ECE3.toInt(),
                tableColor = board.table,
                frameStart = board.start,
                frameEnd = board.end,
                frameGrain = board.grain,
                lightSquare = WHITE_SQUARE,
                darkSquare = BLACK_SQUARE,
                trim = board.trim,
                coordinates = board.coordinates,
                lightPiece = pieces.light,
                darkPiece = pieces.dark,
                lightPieceSpecular = pieces.lightSpecular,
                darkPieceSpecular = pieces.darkSpecular,
                lightPieceShininess = pieces.lightShininess,
                darkPieceShininess = pieces.darkShininess,
                lastMove = if (darkTheme) 0xFFC9A5FF.toInt() else 0xFF7B4CC2.toInt(),
                selected = if (darkTheme) 0xFFFFB86B.toInt() else 0xFFD9480F.toInt(),
                quietMove = if (darkTheme) 0xFF5FD3C7.toInt() else 0xFF0E8F87.toInt(),
                capture = if (darkTheme) 0xFFFF8A3D.toInt() else 0xFFD9480F.toInt(),
                check = if (darkTheme) 0xFFFFB4A9.toInt() else 0xFFB3261E.toInt(),
            )
        }

        /** Backwards-friendly default appearance for previews and existing callers. */
        fun forDarkTheme(dark: Boolean): ChessBoardPalette =
            forAppearance(dark, ChessBoardStyle.WOOD, ChessPieceStyle.CLASSIC)

        val Light: ChessBoardPalette = forDarkTheme(dark = false)
        val Dark: ChessBoardPalette = forDarkTheme(dark = true)
    }
}

private data class BoardFinish(
    val table: Int,
    val start: Int,
    val end: Int,
    val grain: Int,
    val trim: Int,
    val coordinates: Int,
)

private data class PieceFinish(
    val light: Int,
    val dark: Int,
    val lightSpecular: Float,
    val darkSpecular: Float,
    val lightShininess: Float,
    val darkShininess: Float,
)
