package ir.dorehami.games.hokm.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** JVM regression tests for the tilt-driven reflection and the adaptive effect budget. */
class HokmReflectionTest {

    private fun close(a: Float, b: Float, tolerance: Float = 0.0001f): Boolean = abs(a - b) <= tolerance

    @Test
    fun `at rest and with motion off the card has no reflection`() {
        assertEquals(HokmSheen.NEUTRAL, HokmReflection.sheen(0f, 0f, 1f))
        assertEquals(HokmSheen.NEUTRAL, HokmReflection.sheen(1f, -1f, 0f))
        assertEquals(HokmSheen.NEUTRAL, HokmReflection.sheen(0.5f, 0.2f, -3f))
        assertEquals(0f, HokmReflection.specularAlpha(HokmSheen.NEUTRAL, dimmed = false))
    }

    @Test
    fun `opposite tilts mirror the band but keep the same brightness`() {
        val right = HokmReflection.sheen(1f, 0f, 1f)
        val left = HokmReflection.sheen(-1f, 0f, 1f)
        assertTrue(close(right.offsetX, -left.offsetX))
        assertEquals(right.intensity, left.intensity)
        assertTrue(close(HokmReflection.bandCenter(right), 0.8f))
        assertTrue(close(HokmReflection.bandCenter(left), 0.2f))
    }

    @Test
    fun `out of range and non finite sensor values are clamped or ignored`() {
        val extreme = HokmReflection.sheen(50f, -50f, 1f)
        assertTrue(close(extreme.offsetX, 0.5f))
        assertTrue(close(extreme.offsetY, -0.5f))
        assertTrue(extreme.intensity <= HokmReflection.MAX_SHEEN_INTENSITY)
        assertEquals(HokmSheen.NEUTRAL, HokmReflection.sheen(Float.NaN, 0f, 1f))
        assertEquals(HokmSheen.NEUTRAL, HokmReflection.sheen(0f, Float.POSITIVE_INFINITY, 1f))
    }

    @Test
    fun `reflection never covers the rank and suit with an opaque layer`() {
        val values = listOf(-1f, -0.5f, 0f, 0.3f, 1f)
        for (x in values) {
            for (y in values) {
                for (scale in listOf(0f, 0.45f, 1f)) {
                    val sheen = HokmReflection.sheen(x, y, scale)
                    assertTrue(sheen.intensity in 0f..HokmReflection.MAX_SHEEN_INTENSITY)
                    assertTrue(sheen.offsetX in -0.5f..0.5f)
                    assertTrue(sheen.offsetY in -0.5f..0.5f)
                    assertTrue(HokmReflection.bandCenter(sheen) in 0.2f..0.8f)
                }
            }
        }
        assertTrue(HokmReflection.MAX_SHEEN_INTENSITY <= 0.12f)
    }

    @Test
    fun `reduced motion lowers amplitude and dimmed cards are never highlighted`() {
        val full = HokmReflection.sheen(0.8f, 0.4f, 1f)
        val reduced = HokmReflection.sheen(0.8f, 0.4f, 0.45f)
        assertTrue(reduced.intensity < full.intensity)
        assertTrue(reduced.offsetX < full.offsetX)
        assertEquals(0f, HokmReflection.specularAlpha(full, dimmed = true))
        assertEquals(full.intensity, HokmReflection.specularAlpha(full, dimmed = false))
    }

    @Test
    fun `quality policy respects both the device and the motion preference`() {
        assertEquals(HokmQualityLevel.LOW, HokmReflection.quality(128, 2, HokmMotionLevel.FULL))
        assertEquals(HokmQualityLevel.LOW, HokmReflection.quality(1024, 8, HokmMotionLevel.OFF))
        assertEquals(HokmQualityLevel.STANDARD, HokmReflection.quality(256, 4, HokmMotionLevel.FULL))
        assertEquals(HokmQualityLevel.STANDARD, HokmReflection.quality(1024, 8, HokmMotionLevel.REDUCED))
        assertEquals(HokmQualityLevel.HIGH, HokmReflection.quality(1024, 8, HokmMotionLevel.FULL))
        assertEquals(HokmQualityLevel.LOW, HokmReflection.quality(1024, 2, HokmMotionLevel.FULL))
    }

    @Test
    fun `low end devices spend fewer particles and no sheen`() {
        assertTrue(HokmReflection.particleScale(HokmQualityLevel.LOW) < HokmReflection.particleScale(HokmQualityLevel.STANDARD))
        assertTrue(HokmReflection.particleScale(HokmQualityLevel.STANDARD) < HokmReflection.particleScale(HokmQualityLevel.HIGH))
        assertEquals(0f, HokmReflection.sheenScale(HokmQualityLevel.LOW))
        assertEquals(1f, HokmReflection.particleScale(HokmQualityLevel.HIGH))
        assertEquals(1f, HokmReflection.sheenScale(HokmQualityLevel.HIGH))
    }

    @Test
    fun `pure functions are deterministic and independent of call order`() {
        val first = HokmReflection.sheen(0.31f, -0.72f, 0.7f)
        HokmReflection.sheen(-1f, 1f, 1f)
        assertEquals(first, HokmReflection.sheen(0.31f, -0.72f, 0.7f))
        assertEquals(
            HokmReflection.quality(512, 8, HokmMotionLevel.FULL),
            HokmReflection.quality(512, 8, HokmMotionLevel.FULL),
        )
    }
}
