package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GX-01 direction guard: the approved palette must stay readable, in numbers not taste. */
class HokmArtDirectionTest {

    @Test
    fun `contrast ratio is symmetric and black on white is 21`() {
        assertEquals(21.0, HokmArtDirection.contrastRatio(0xFF000000, 0xFFFFFFFF), 0.01)
        assertEquals(
            HokmArtDirection.contrastRatio(HokmArtDirection.IVORY, HokmArtDirection.FELT),
            HokmArtDirection.contrastRatio(HokmArtDirection.FELT, HokmArtDirection.IVORY),
            0.0001,
        )
    }

    @Test
    fun `cream text on the emerald felt passes AA normal and AAA large`() {
        // ~6.6:1 on the base felt: AA for normal text, AAA for large/bold (score numbers, titles).
        assertTrue(HokmArtDirection.contrastRatio(HokmArtDirection.IVORY, HokmArtDirection.FELT) >= 4.5)
        assertTrue(HokmArtDirection.contrastRatio(HokmArtDirection.IVORY, HokmArtDirection.FELT_EDGE) >= 7.0)
    }

    @Test
    fun `card inks on ivory pass AA`() {
        assertTrue(HokmArtDirection.contrastRatio(HokmArtDirection.INK_BLACK, HokmArtDirection.IVORY) >= 7.0)
        assertTrue(HokmArtDirection.contrastRatio(HokmArtDirection.INK_RED, HokmArtDirection.IVORY) >= 4.5)
    }

    @Test
    fun `brass on deep green passes AA for the medallion label`() {
        assertTrue(HokmArtDirection.contrastRatio(HokmArtDirection.BRASS_LIGHT, HokmArtDirection.DEEP) >= 4.5)
    }

    @Test
    fun `index fraction stays jumbo`() {
        assertTrue(HokmArtDirection.JUMBO_INDEX_FRACTION >= 0.24f)
    }
}
