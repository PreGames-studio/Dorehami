package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HokmSoundBankTest {

    @Test
    fun `catalog covers all events`() {
        HokmSoundBank.SoundEvent.values().forEach { e ->
            assertNotNull(HokmSoundBank.CATALOG[e])
        }
    }

    @Test
    fun `file names non-empty`() {
        HokmSoundBank.SoundEvent.values().forEach { e ->
            assertTrue(HokmSoundBank.fileName(e).isNotEmpty())
        }
    }

    @Test
    fun `priorities within range`() {
        HokmSoundBank.SoundEvent.values().forEach { e ->
            val p = HokmSoundBank.priority(e)
            assertTrue(p >= 1 && p <= 4)
        }
    }

    @Test
    fun `cooldowns non-negative`() {
        HokmSoundBank.SoundEvent.values().forEach { e ->
            assertTrue(HokmSoundBank.cooldownMs(e) >= 0)
        }
    }

    @Test
    fun `preload set is all events`() {
        assertEquals(HokmSoundBank.SoundEvent.values().size, HokmSoundBank.preloadSet().size)
    }

    @Test
    fun `match win is critical`() {
        assertTrue(HokmSoundBank.isCritical(HokmSoundBank.SoundEvent.MATCH_WIN))
    }

    @Test
    fun `button tap is not critical`() {
        assertTrue(!HokmSoundBank.isCritical(HokmSoundBank.SoundEvent.BUTTON_TAP))
    }

    @Test
    fun `specOf returns non-null`() {
        assertNotNull(HokmSoundBank.specOf(HokmSoundBank.SoundEvent.CARD_PLAY))
    }
}
