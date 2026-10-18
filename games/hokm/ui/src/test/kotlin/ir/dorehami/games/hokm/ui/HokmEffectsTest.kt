package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/** HK-025 first slice — effects origin/priority/quality guards (pure numbers, no device). */
class HokmEffectsTest {

    @Test
    fun `effects stay below cards for readability`() {
        assertTrue(HokmEffects.effectsBelowCards())
    }

    @Test
    fun `higher priority replaces lower instead of stacking`() {
        assertTrue(HokmEffects.replaces(HokmEffects.Kind.MATCH_WIN, HokmEffects.Kind.TRICK_WIN))
        assertTrue(HokmEffects.replaces(HokmEffects.Kind.KOT, HokmEffects.Kind.CUT))
        assertTrue(!HokmEffects.replaces(HokmEffects.Kind.CUT, HokmEffects.Kind.KOT))
    }

    @Test
    fun `same kind never stacks on one seat`() {
        assertTrue(!HokmEffects.canOverlap(HokmEffects.Kind.CUT, HokmEffects.Kind.CUT))
        assertTrue(HokmEffects.canOverlap(HokmEffects.Kind.CUT, HokmEffects.Kind.TRICK_WIN))
    }

    @Test
    fun `economy tier shows no particles`() {
        for (k in HokmEffects.Kind.values()) {
            assertEquals(0, HokmEffects.particleCount(k, HokmEffects.Quality.ECONOMY))
            assertTrue(!HokmEffects.particlesEnabled(HokmEffects.Quality.ECONOMY, false))
        }
    }

    @Test
    fun `high tier has more particles than standard`() {
        for (k in HokmEffects.Kind.values()) {
            assertTrue(
                HokmEffects.particleCount(k, HokmEffects.Quality.HIGH) >=
                    HokmEffects.particleCount(k, HokmEffects.Quality.STANDARD)
            )
        }
    }

    @Test
    fun `reduced motion disables particles`() {
        assertTrue(!HokmEffects.particlesEnabled(HokmEffects.Quality.HIGH, true))
    }

    @Test
    fun `origin seat stays in range`() {
        for (k in HokmEffects.Kind.values()) {
            val s = HokmEffects.originSeat(k, 3, 4)
            assertTrue(s in 0..3)
        }
        // clamping
        assertEquals(0, HokmEffects.originSeat(HokmEffects.Kind.CUT, -5, 4))
        assertEquals(3, HokmEffects.originSeat(HokmEffects.Kind.CUT, 99, 4))
    }

    @Test
    fun `match win has the highest priority`() {
        val max = HokmEffects.Kind.values().maxBy { it.priority }
        assertTrue(max == HokmEffects.Kind.MATCH_WIN)
    }
}
