package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** GX-02 guard: the design language stays coherent and readable, in numbers. */
class HokmTokensTest {

    @Test
    fun `spacing stays on the 4dp grid`() {
        assertTrue(HokmTokens.Space.ALL_DP.all { it % HokmTokens.Space.GRID_DP == 0 })
        assertTrue(HokmTokens.Space.ALL_DP.zipWithNext().all { (a, b) -> b > a })
    }

    @Test
    fun `type ladder strictly decreases and never goes below caption`() {
        val ladder = HokmTokens.Type.LADDER
        assertTrue(ladder.zipWithNext().all { (a, b) -> a > b })
        assertTrue(ladder.last() >= 10f)
    }

    @Test
    fun `semantic text colours keep AA contrast on their surfaces`() {
        assertTrue(HokmArtDirection.contrastRatio(HokmTokens.Color.TEXT, HokmTokens.Color.SURFACE_DEEP) >= 4.5)
        assertTrue(HokmArtDirection.contrastRatio(HokmTokens.Color.TEXT_MUTED, HokmTokens.Color.SURFACE_DEEP) >= 4.5)
        assertTrue(HokmArtDirection.contrastRatio(HokmTokens.Color.GOLD, HokmTokens.Color.SURFACE_DEEP) >= 4.5)
        assertTrue(HokmArtDirection.contrastRatio(HokmTokens.Color.TEXT, HokmTokens.Color.FELT) >= 4.5)
    }

    @Test
    fun `art direction colours are the token colours`() {
        assertEquals(HokmArtDirection.FELT, HokmTokens.Color.FELT)
        assertEquals(HokmArtDirection.TURQUOISE, HokmTokens.Color.TURQUOISE)
        assertEquals(HokmArtDirection.INK_RED, HokmTokens.Color.INK_RED)
        // card inks must stay readable on the shipped card stock
        assertTrue(HokmArtDirection.contrastRatio(HokmTokens.Color.INK_BLACK, HokmTokens.Color.CARD_IVORY) >= 7.0)
        assertTrue(HokmArtDirection.contrastRatio(HokmTokens.Color.INK_RED, HokmTokens.Color.CARD_IVORY) >= 4.5)
    }

    @Test
    fun `GX-03 card inks are real tokens and stay readable on the stock`() {
        assertEquals(HokmArtDirection.INK_RED, HokmTokens.Color.INK_RED)
        // The four-colour deck's blue and green are tokens too, not one-off literals in the painter.
        listOf(HokmTokens.Color.INK_BLACK, HokmTokens.Color.INK_RED, HokmTokens.Color.INK_BLUE, HokmTokens.Color.INK_GREEN)
            .forEach { ink ->
                assertTrue(
                    HokmArtDirection.contrastRatio(ink, HokmTokens.Color.CARD_IVORY) >= 4.5,
                    "card ink ${ink.toString(16)} must stay AA on the ivory stock",
                )
            }
    }

    @Test
    fun `every motion token compresses under reduced motion instead of vanishing`() {
        HokmTokens.Motion.ALL_MS.forEach { ms ->
            val reduced = HokmMotion.durationMs(ms, reducedMotion = true)
            assertTrue(reduced in 1..HokmMotion.REDUCED_MAX_MS)
            assertEquals(ms, HokmMotion.durationMs(ms, reducedMotion = false))
        }
    }
}
