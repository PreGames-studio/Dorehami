package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmEasingTest {

    @Test
    fun `linear endpoints`() {
        assertEquals(0f, HokmEasing.linear(0f))
        assertEquals(1f, HokmEasing.linear(1f))
    }

    @Test
    fun `easeIn endpoints`() {
        assertEquals(0f, HokmEasing.easeIn(0f))
        assertEquals(1f, HokmEasing.easeIn(1f))
    }

    @Test
    fun `easeOut endpoints`() {
        assertEquals(0f, HokmEasing.easeOut(0f))
        assertEquals(1f, HokmEasing.easeOut(1f))
    }

    @Test
    fun `easeInOut endpoints`() {
        assertEquals(0f, HokmEasing.easeInOut(0f))
        assertEquals(1f, HokmEasing.easeInOut(1f))
    }

    @Test
    fun `easeIn slower at start`() {
        assertTrue(HokmEasing.easeIn(0.5f) < 0.5f)
    }

    @Test
    fun `easeOut faster at start`() {
        assertTrue(HokmEasing.easeOut(0.5f) > 0.5f)
    }
}
