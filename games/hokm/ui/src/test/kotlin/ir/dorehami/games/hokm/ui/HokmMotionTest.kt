package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HK-013.a — the reduced-motion decision is a player setting, not a design opinion: these tests
 * pin the exact signals that turn it on and what it does to durations and decoration.
 */
class HokmMotionTest {

    @Test
    fun `system animations off means reduced motion`() {
        assertTrue(HokmMotion.isReducedMotion(animatorDurationScale = 0f, animatorsEnabled = false))
        // Either signal alone is enough; the player's setting is never second-guessed.
        assertTrue(HokmMotion.isReducedMotion(animatorDurationScale = 0f, animatorsEnabled = true))
        assertTrue(HokmMotion.isReducedMotion(animatorDurationScale = 1f, animatorsEnabled = false))
    }

    @Test
    fun `system animations on means normal motion`() {
        assertFalse(HokmMotion.isReducedMotion(animatorDurationScale = 1f, animatorsEnabled = true))
        assertFalse(HokmMotion.isReducedMotion(animatorDurationScale = 0.5f, animatorsEnabled = true))
    }

    @Test
    fun `reduced motion compresses essential durations but never removes them`() {
        assertEquals(HokmMotion.REDUCED_MAX_MS, HokmMotion.durationMs(480, reducedMotion = true))
        assertEquals(HokmMotion.REDUCED_MAX_MS, HokmMotion.durationMs(300, reducedMotion = true))
        // A short essential animation keeps its own length — compression is a ceiling, not a floor.
        assertEquals(50, HokmMotion.durationMs(50, reducedMotion = true))
        assertEquals(0, HokmMotion.durationMs(0, reducedMotion = true))
        assertEquals(0, HokmMotion.durationMs(-10, reducedMotion = true))
    }

    @Test
    fun `normal motion keeps requested durations`() {
        assertEquals(480, HokmMotion.durationMs(480, reducedMotion = false))
        assertEquals(0, HokmMotion.durationMs(-1, reducedMotion = false))
    }

    @Test
    fun `reduced motion removes decoration only`() {
        assertEquals(0, HokmMotion.particleCount(48, reducedMotion = true))
        assertEquals(48, HokmMotion.particleCount(48, reducedMotion = false))
        assertEquals(0, HokmMotion.particleCount(-3, reducedMotion = false))
        assertFalse(HokmMotion.infiniteMotionEnabled(reducedMotion = true))
        assertTrue(HokmMotion.infiniteMotionEnabled(reducedMotion = false))
    }
}
