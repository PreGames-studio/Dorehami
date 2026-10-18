package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue

class HokmPaletteTest {

    @Test
    fun `palette for emerald table`() {
        val c = HokmPalette.forTable(HokmTableFelt.Felt.EMERALD)
        assertTrue(c.g > c.r)
    }

    @Test
    fun `palette for dark velvet`() {
        val c = HokmPalette.forTable(HokmTableFelt.Felt.DARK_VELVET)
        assertTrue(HokmColor.isDark(c))
    }
}
