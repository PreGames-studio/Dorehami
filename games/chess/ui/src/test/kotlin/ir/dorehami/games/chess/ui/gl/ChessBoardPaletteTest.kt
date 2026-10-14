package ir.dorehami.games.chess.ui.gl

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ChessBoardPaletteTest {

    @Test
    fun `board colors use Pantohub warm and teal anchors`() {
        val palette = ChessBoardPalette.Light

        assertEquals(0xFFFFE08A.toInt(), palette.lightSquare)
        assertEquals(0xFF0E8F87.toInt(), palette.darkSquare)
        assertEquals(0xFF7B4CC2.toInt(), palette.lastMove)
        assertEquals(0xFFD9480F.toInt(), palette.capture)
    }

    @Test
    fun `system themes change the table while preserving readable square contrast`() {
        val light = ChessBoardPalette.forDarkTheme(dark = false)
        val dark = ChessBoardPalette.forDarkTheme(dark = true)

        assertNotEquals(light.clearColor, dark.clearColor)
        assertNotEquals(light.tableColor, dark.tableColor)
        assertTrue(contrastRatio(light.lightSquare, light.darkSquare) >= 3.0)
        assertTrue(contrastRatio(dark.lightSquare, dark.darkSquare) >= 3.0)
        assertTrue(contrastRatio(light.darkPiece, light.darkSquare) >= 3.0)
        assertTrue(contrastRatio(dark.darkPiece, dark.darkSquare) >= 3.0)
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
