package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmSettingsTest {

    @Test
    fun `defaults are sane`() {
        val s = HokmSettings.DEFAULTS
        assertTrue(!s.muted)
        assertEquals(HokmSettings.AnimSpeed.NORMAL, s.animSpeed)
        assertEquals(HokmSettings.Language.FA, s.language)
        assertEquals(HokmSettings.TableSkin.EMERALD, s.tableSkin)
    }

    @Test
    fun `sanitize keeps defaults`() {
        assertEquals(HokmSettings.DEFAULTS, HokmSettings.sanitize(HokmSettings.DEFAULTS))
    }

    @Test
    fun `large text forces reduced motion`() {
        val s = HokmSettings.Settings(largeText = true)
        assertTrue(HokmSettings.effectiveReducedMotion(s))
    }

    @Test
    fun `reduced motion flag forces reduced motion`() {
        val s = HokmSettings.Settings(reducedMotion = true)
        assertTrue(HokmSettings.effectiveReducedMotion(s))
    }

    @Test
    fun `anim off disables animations`() {
        val s = HokmSettings.Settings(animSpeed = HokmSettings.AnimSpeed.OFF)
        assertTrue(HokmSettings.animationsDisabled(s))
    }

    @Test
    fun `reduced motion disables animations`() {
        val s = HokmSettings.Settings(reducedMotion = true)
        assertTrue(HokmSettings.animationsDisabled(s))
    }

    @Test
    fun `fast speed multiplier less than 1`() {
        val s = HokmSettings.Settings(animSpeed = HokmSettings.AnimSpeed.FAST)
        assertTrue(HokmSettings.animMultiplier(s) < 1.0f)
    }

    @Test
    fun `slow speed multiplier greater than 1`() {
        val s = HokmSettings.Settings(animSpeed = HokmSettings.AnimSpeed.SLOW)
        assertTrue(HokmSettings.animMultiplier(s) > 1.0f)
    }

    @Test
    fun `coaching enabled check`() {
        assertTrue(HokmSettings.coachingEnabled(HokmSettings.Settings(showCoaching = true)))
        assertTrue(!HokmSettings.coachingEnabled(HokmSettings.Settings(showCoaching = false)))
    }

    @Test
    fun `off speed multiplier is zero`() {
        val s = HokmSettings.Settings(animSpeed = HokmSettings.AnimSpeed.OFF)
        assertEquals(0f, HokmSettings.animMultiplier(s))
    }
}
