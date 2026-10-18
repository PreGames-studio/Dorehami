package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmThemeTest {

    @Test
    fun `light mode not dark`() {
        assertTrue(!HokmTheme.isDark(HokmTheme.Mode.LIGHT, true))
    }

    @Test
    fun `dark mode dark`() {
        assertTrue(HokmTheme.isDark(HokmTheme.Mode.DARK, false))
    }

    @Test
    fun `system follows system`() {
        assertTrue(HokmTheme.isDark(HokmTheme.Mode.SYSTEM, true))
        assertTrue(!HokmTheme.isDark(HokmTheme.Mode.SYSTEM, false))
    }

    @Test
    fun `toggle light to dark`() {
        assertEquals(HokmTheme.Mode.DARK, HokmTheme.toggle(HokmTheme.Mode.LIGHT))
    }

    @Test
    fun `toggle dark to light`() {
        assertEquals(HokmTheme.Mode.LIGHT, HokmTheme.toggle(HokmTheme.Mode.DARK))
    }

    @Test
    fun `resolve returns concrete mode`() {
        assertEquals(HokmTheme.Mode.DARK, HokmTheme.resolve(HokmTheme.Mode.SYSTEM, true))
        assertEquals(HokmTheme.Mode.LIGHT, HokmTheme.resolve(HokmTheme.Mode.SYSTEM, false))
    }
}
