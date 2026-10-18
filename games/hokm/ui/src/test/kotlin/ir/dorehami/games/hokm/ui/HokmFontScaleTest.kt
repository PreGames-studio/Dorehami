package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmFontScaleTest {

    @Test
    fun `clamp within range`() {
        assertEquals(1.0f, HokmFontScale.clamp(1.0f))
    }

    @Test
    fun `clamp below min`() {
        assertEquals(HokmFontScale.MIN_SCALE, HokmFontScale.clamp(0.5f))
    }

    @Test
    fun `clamp above max`() {
        assertEquals(HokmFontScale.MAX_SCALE, HokmFontScale.clamp(3.0f))
    }

    @Test
    fun `scaled size`() {
        assertEquals(20f, HokmFontScale.scaledSize(10f, 2.0f))
    }

    @Test
    fun `is large at 1_5`() {
        assertTrue(HokmFontScale.isLarge(1.5f))
        assertTrue(!HokmFontScale.isLarge(1.0f))
    }

    @Test
    fun `is accessibility at 2`() {
        assertTrue(HokmFontScale.isAccessibility(2.0f))
        assertTrue(!HokmFontScale.isAccessibility(1.5f))
    }
}
