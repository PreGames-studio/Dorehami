package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class HokmAccessibilityTest {

    @Test
    fun `black on white max contrast`() {
        val r = HokmAccessibility.contrastRatio(Triple(0, 0, 0), Triple(255, 255, 255))
        assertTrue(r > 20.0)
    }

    @Test
    fun `same color contrast is 1`() {
        val r = HokmAccessibility.contrastRatio(Triple(128, 128, 128), Triple(128, 128, 128))
        assertTrue(r in 0.99..1.01)
    }

    @Test
    fun `white on black meets AA`() {
        assertTrue(HokmAccessibility.meetsAA(Triple(255, 255, 255), Triple(0, 0, 0)))
    }

    @Test
    fun `low contrast fails AA`() {
        assertFalse(HokmAccessibility.meetsAA(Triple(180, 180, 180), Triple(200, 200, 200)))
    }

    @Test
    fun `large text threshold lower`() {
        // a ratio between 3.0 and 4.5 passes for large only
        assertTrue(HokmAccessibility.meetsAA(Triple(110, 110, 110), Triple(0, 0, 0), true))
    }

    @Test
    fun `touch target ok`() {
        assertTrue(HokmAccessibility.touchTargetOk(48, 48))
        assertFalse(HokmAccessibility.touchTargetOk(47, 48))
    }

    @Test
    fun `focus order valid`() {
        assertTrue(HokmAccessibility.focusOrderValid(listOf(1, 2, 3)))
        assertFalse(HokmAccessibility.focusOrderValid(listOf(1, 1, 2)))
        assertFalse(HokmAccessibility.focusOrderValid(listOf(3, 2, 1)))
    }

    @Test
    fun `content description check`() {
        assertTrue(HokmAccessibility.hasContentDescription("play button"))
        assertFalse(HokmAccessibility.hasContentDescription(null))
        assertFalse(HokmAccessibility.hasContentDescription(""))
    }

    @Test
    fun `font scale support`() {
        assertTrue(HokmAccessibility.fontScaleSupported(2.0f))
        assertFalse(HokmAccessibility.fontScaleSupported(2.5f))
    }

    @Test
    fun `luminance black is 0`() {
        assertEquals(0.0, HokmAccessibility.luminance(0, 0, 0), 0.001)
    }
}
