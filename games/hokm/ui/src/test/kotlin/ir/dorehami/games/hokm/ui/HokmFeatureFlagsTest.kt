package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmFeatureFlagsTest {

    @Test
    fun `default coaching disabled`() {
        assertFalse(HokmFeatureFlags.defaultOf(HokmFeatureFlags.Flag.COACHING))
    }

    @Test
    fun `default chat enabled`() {
        assertTrue(HokmFeatureFlags.defaultOf(HokmFeatureFlags.Flag.CHAT))
    }

    @Test
    fun `set overrides default`() {
        val f = HokmFeatureFlags.set(HokmFeatureFlags.Flags(), HokmFeatureFlags.Flag.COACHING, true)
        assertTrue(HokmFeatureFlags.isEnabled(f, HokmFeatureFlags.Flag.COACHING))
    }

    @Test
    fun `reset restores default`() {
        val f = HokmFeatureFlags.set(HokmFeatureFlags.Flags(), HokmFeatureFlags.Flag.COACHING, true)
        val f2 = HokmFeatureFlags.reset(f, HokmFeatureFlags.Flag.COACHING)
        assertFalse(HokmFeatureFlags.isEnabled(f2, HokmFeatureFlags.Flag.COACHING))
    }

    @Test
    fun `resetAll clears overrides`() {
        val f = HokmFeatureFlags.set(HokmFeatureFlags.Flags(), HokmFeatureFlags.Flag.COACHING, true)
        val f2 = HokmFeatureFlags.resetAll()
        assertFalse(HokmFeatureFlags.isEnabled(f2, HokmFeatureFlags.Flag.COACHING))
    }

    @Test
    fun `allEnabled filters`() {
        val f = HokmFeatureFlags.Flags()
        val enabled = HokmFeatureFlags.allEnabled(f)
        assertTrue(enabled.contains(HokmFeatureFlags.Flag.CHAT))
        assertTrue(!enabled.contains(HokmFeatureFlags.Flag.COACHING))
    }
}
