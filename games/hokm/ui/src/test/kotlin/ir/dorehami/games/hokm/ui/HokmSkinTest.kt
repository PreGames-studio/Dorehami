package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HokmSkinTest {

    @Test
    fun `presets non-empty`() {
        assertTrue(HokmSkin.PRESETS.isNotEmpty())
    }

    @Test
    fun `byName finds preset`() {
        assertNotNull(HokmSkin.byName("کلاسیک ایرانی"))
    }

    @Test
    fun `byName returns null for unknown`() {
        assertTrue(HokmSkin.byName("unknown") == null)
    }

    @Test
    fun `default for high quality`() {
        assertEquals(HokmSkin.PRESETS[1], HokmSkin.defaultForQuality(true))
    }

    @Test
    fun `default for low quality`() {
        assertEquals(HokmSkin.PRESETS[0], HokmSkin.defaultForQuality(false))
    }

    @Test
    fun `count matches presets`() {
        assertEquals(HokmSkin.PRESETS.size, HokmSkin.count())
    }

    @Test
    fun `skin enums non-empty`() {
        assertTrue(HokmSkin.tableSkins().isNotEmpty())
        assertTrue(HokmSkin.cardSkins().isNotEmpty())
        assertTrue(HokmSkin.backSkins().isNotEmpty())
    }

    @Test
    fun `preset names non-empty`() {
        HokmSkin.PRESETS.forEach { assertTrue(it.nameFa.isNotEmpty()) }
    }
}
