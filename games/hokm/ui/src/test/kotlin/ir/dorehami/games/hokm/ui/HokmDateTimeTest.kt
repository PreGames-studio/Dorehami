package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmDateTimeTest {

    @Test
    fun `format duration`() {
        assertEquals("1:30", HokmDateTime.formatDuration(90000))
    }

    @Test
    fun `format duration zero`() {
        assertEquals("0:00", HokmDateTime.formatDuration(0))
    }

    @Test
    fun `is today true`() {
        assertTrue(HokmDateTime.isToday(1000, 0))
    }

    @Test
    fun `is today false next day`() {
        assertTrue(!HokmDateTime.isToday(90000000, 0))
    }

    @Test
    fun `day index`() {
        assertEquals(0, HokmDateTime.dayIndex(0))
        assertEquals(1, HokmDateTime.dayIndex(86400000))
    }
}
