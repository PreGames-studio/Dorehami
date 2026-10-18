package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HokmHapticsTest {

    @Test
    fun `catalog covers all events`() {
        HokmHaptics.Event.values().forEach { e ->
            assertNotNull(HokmHaptics.CATALOG[e])
        }
    }

    @Test
    fun `shouldFire respects reduced motion`() {
        assertTrue(!HokmHaptics.shouldFire(true, true))
        assertTrue(!HokmHaptics.shouldFire(false, false))
        assertTrue(HokmHaptics.shouldFire(false, true))
    }

    @Test
    fun `durations positive`() {
        HokmHaptics.Event.values().forEach { e ->
            assertTrue(HokmHaptics.duration(e) > 0)
        }
    }

    @Test
    fun `amplitudes in range`() {
        HokmHaptics.Event.values().forEach { e ->
            val a = HokmHaptics.amplitude(e)
            assertTrue(a in 0..255)
        }
    }

    @Test
    fun `match win has highest amplitude`() {
        assertTrue(HokmHaptics.amplitude(HokmHaptics.Event.MATCH_WIN) >= HokmHaptics.amplitude(HokmHaptics.Event.CARD_PLAY))
    }
}
