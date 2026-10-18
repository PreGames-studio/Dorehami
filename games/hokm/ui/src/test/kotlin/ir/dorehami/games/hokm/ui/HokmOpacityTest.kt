package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmOpacityTest {

    @Test
    fun `clamp range`() {
        assertEquals(0f, HokmOpacity.clamp(-0.5f))
        assertEquals(1f, HokmOpacity.clamp(1.5f))
    }

    @Test
    fun `fade out`() {
        assertEquals(0f, HokmOpacity.fadeOut(1f))
        assertEquals(1f, HokmOpacity.fadeOut(0f))
    }

    @Test
    fun `fade in`() {
        assertEquals(1f, HokmOpacity.fadeIn(1f))
        assertEquals(0f, HokmOpacity.fadeIn(0f))
    }

    @Test
    fun `disabled alpha`() {
        assertEquals(1f, HokmOpacity.disabledAlpha(true))
        assertTrue(HokmOpacity.disabledAlpha(false) < 1f)
    }
}
