package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class HokmPatternTest {

    @Test
    fun `has pattern`() {
        assertTrue(HokmPattern.hasPattern("girih"))
        assertFalse(HokmPattern.hasPattern("nope"))
    }

    @Test
    fun `pattern for back`() {
        assertEquals("girih", HokmPattern.patternForBack(HokmCardBack.Design.EMERALD_GIRIH))
    }
}
