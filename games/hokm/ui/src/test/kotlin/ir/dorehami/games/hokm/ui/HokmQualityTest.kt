package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/** HK-026 first slice — quality tier selection guards (pure numbers, no device). */
class HokmQualityTest {

    @Test
    fun `low ram forces economy`() {
        assertEquals(HokmQuality.Tier.ECONOMY, HokmQuality.selectTier(2f, HokmQuality.Thermal.COOL, false))
        assertEquals(HokmQuality.Tier.ECONOMY, HokmQuality.selectTier(2.5f, HokmQuality.Thermal.COOL, false))
    }

    @Test
    fun `hot device forces economy even with enough ram`() {
        assertEquals(HokmQuality.Tier.ECONOMY, HokmQuality.selectTier(8f, HokmQuality.Thermal.HOT, false))
        assertEquals(HokmQuality.Tier.ECONOMY, HokmQuality.selectTier(8f, HokmQuality.Thermal.CRITICAL, false))
    }

    @Test
    fun `high ram and cool device selects high`() {
        assertEquals(HokmQuality.Tier.HIGH, HokmQuality.selectTier(8f, HokmQuality.Thermal.COOL, false))
    }

    @Test
    fun `mid range selects standard`() {
        assertEquals(HokmQuality.Tier.STANDARD, HokmQuality.selectTier(4f, HokmQuality.Thermal.COOL, false))
        assertEquals(HokmQuality.Tier.STANDARD, HokmQuality.selectTier(4f, HokmQuality.Thermal.WARM, false))
    }

    @Test
    fun `selection is monotonic in ram`() {
        val t1 = HokmQuality.selectTier(3f, HokmQuality.Thermal.COOL, false)
        val t2 = HokmQuality.selectTier(6f, HokmQuality.Thermal.COOL, false)
        assertTrue(t2.ordinal >= t1.ordinal)
    }

    @Test
    fun `economy disables particles sheen vignette and soft shadows`() {
        assertTrue(!HokmQuality.particlesEnabled(HokmQuality.Tier.ECONOMY, false))
        assertTrue(!HokmQuality.sheenEnabled(HokmQuality.Tier.ECONOMY))
        assertTrue(!HokmQuality.fullVignette(HokmQuality.Tier.ECONOMY))
        assertTrue(!HokmQuality.softShadows(HokmQuality.Tier.ECONOMY))
    }

    @Test
    fun `reduced motion disables particles even on high tier`() {
        assertTrue(!HokmQuality.particlesEnabled(HokmQuality.Tier.HIGH, true))
    }

    @Test
    fun `high tier has larger particle multiplier than standard`() {
        assertTrue(HokmQuality.particleMultiplier(HokmQuality.Tier.HIGH) > HokmQuality.particleMultiplier(HokmQuality.Tier.STANDARD))
    }

    @Test
    fun `standard and high enable all features`() {
        for (t in listOf(HokmQuality.Tier.STANDARD, HokmQuality.Tier.HIGH)) {
            assertTrue(HokmQuality.particlesEnabled(t, false))
            assertTrue(HokmQuality.sheenEnabled(t))
            assertTrue(HokmQuality.fullVignette(t))
            assertTrue(HokmQuality.softShadows(t))
        }
    }
}

// Additional tests for HK-026 extension (appended)
class HokmQualityExtTest {
    @Test
    fun `coaching disabled on economy`() {
        assertTrue(!HokmQuality.coachingEnabled(HokmQuality.Tier.ECONOMY))
    }
    @Test
    fun `coaching enabled on standard`() {
        assertTrue(HokmQuality.coachingEnabled(HokmQuality.Tier.STANDARD))
    }
    @Test
    fun `celebration only on high`() {
        assertTrue(HokmQuality.celebrationEnabled(HokmQuality.Tier.HIGH, false))
        assertTrue(!HokmQuality.celebrationEnabled(HokmQuality.Tier.STANDARD, false))
        assertTrue(!HokmQuality.celebrationEnabled(HokmQuality.Tier.HIGH, true))
    }
    @Test
    fun `reduced motion disables fan animation`() {
        assertTrue(!HokmQuality.fanAnimationEnabled(HokmQuality.Tier.STANDARD, true))
    }
}
