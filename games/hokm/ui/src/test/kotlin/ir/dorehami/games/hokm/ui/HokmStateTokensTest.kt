package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue

/** GX-07 first slice — state/button token guards (pure numbers, no device). */
class HokmStateTokensTest {

    @Test
    fun `state colours on dark surfaces pass AA`() {
        for (c in HokmTokens.State.ALL_ON_DARK) {
            assertTrue(HokmArtDirection.contrastRatio(c, HokmTokens.Color.SURFACE_DEEP) >= 4.5)
        }
    }

    @Test
    fun `primary button keeps AA contrast`() {
        assertTrue(
            HokmArtDirection.contrastRatio(HokmTokens.Button.PRIMARY_FG, HokmTokens.Button.PRIMARY_BG) >= 3.0
        )
    }

    @Test
    fun `disabled affordance is dimmer than enabled`() {
        assertTrue(HokmTokens.State.DISABLED_ALPHA in 0.3f..0.5f)
        assertTrue(
            HokmArtDirection.contrastRatio(HokmTokens.Button.DISABLED_FG, HokmTokens.Button.DISABLED_BG) <
                HokmArtDirection.contrastRatio(HokmTokens.Button.PRIMARY_FG, HokmTokens.Button.PRIMARY_BG)
        )
    }

    @Test
    fun `focus ring is visible on the felt`() {
        assertTrue(
            HokmArtDirection.contrastRatio(HokmTokens.Button.FOCUS_RING, HokmTokens.Color.FELT) >= 3.0
        )
    }

    @Test
    fun `success warning info and error are distinct`() {
        val set = setOf(
            HokmTokens.State.SUCCESS, HokmTokens.State.WARNING,
            HokmTokens.State.INFO, HokmTokens.State.ERROR,
        )
        assertTrue(set.size == 4)
    }
}
