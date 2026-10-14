package ir.dorehami.games.chess.ui.gl

import ir.dorehami.games.chess.ui.ChessBoardStyle
import ir.dorehami.games.chess.ui.ChessPieceStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ChessBoardPaletteTest {

    @Test
    fun `all five board finishes keep the same black and white squares`() {
        ChessBoardStyle.values().forEach { style ->
            listOf(false, true).forEach { darkTheme ->
                val palette = ChessBoardPalette.forAppearance(
                    darkTheme = darkTheme,
                    boardStyle = style,
                    pieceStyle = ChessPieceStyle.CLASSIC,
                )
                assertEquals(0xFFF8F8F5.toInt(), palette.lightSquare, style.name)
                assertEquals(0xFF191A1D.toInt(), palette.darkSquare, style.name)
                assertTrue(contrastRatio(palette.lightSquare, palette.darkSquare) >= 10.0)
            }
        }
    }

    @Test
    fun `board finishes have distinct frame materials`() {
        val palettes = ChessBoardStyle.values().map { style ->
            ChessBoardPalette.forAppearance(false, style, ChessPieceStyle.CLASSIC)
        }
        assertEquals(5, palettes.map { it.frameStart }.toSet().size)
        assertEquals(5, palettes.map { it.tableColor }.toSet().size)
    }

    @Test
    fun `each piece finish provides both light and dark materials`() {
        ChessPieceStyle.values().forEach { style ->
            val palette = ChessBoardPalette.forAppearance(false, ChessBoardStyle.WOOD, style)
            assertEquals(style, palette.pieceStyle)
            assertNotEquals(palette.lightPiece, palette.darkPiece, style.name)
            assertTrue(palette.lightPieceSpecular >= 0f)
            assertTrue(palette.darkPieceSpecular >= 0f)
            assertTrue(palette.lightPieceShininess > 0f)
            assertTrue(palette.darkPieceShininess > 0f)
        }
    }

    private fun contrastRatio(first: Int, second: Int): Double {
        val l1 = luminance(first)
        val l2 = luminance(second)
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color ushr shift) and 0xFF) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun Double.pow(exponent: Double): Double = Math.pow(this, exponent)
}
