package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmBlurTest {

    @Test
    fun `blur radius high`() {
        assertEquals(12f, HokmBlur.blurRadius("high"))
    }

    @Test
    fun `blur radius low`() {
        assertEquals(2f, HokmBlur.blurRadius("low"))
    }

    @Test
    fun `should blur respects reduced motion`() {
        assertTrue(HokmBlur.shouldBlur(true, false))
        assertTrue(!HokmBlur.shouldBlur(true, true))
        assertTrue(!HokmBlur.shouldBlur(false, false))
    }
}
