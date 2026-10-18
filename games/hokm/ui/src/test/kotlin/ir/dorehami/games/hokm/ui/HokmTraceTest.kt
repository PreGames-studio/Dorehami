package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmTraceTest {

    @Test
    fun `duration`() {
        val s = HokmTrace.create("op", 100, 250)
        assertEquals(150L, s.durationMs)
    }

    @Test
    fun `total duration`() {
        val spans = listOf(HokmTrace.create("a", 0, 100), HokmTrace.create("b", 0, 50))
        assertEquals(150L, HokmTrace.totalDuration(spans))
    }

    @Test
    fun `format non-empty`() {
        val s = HokmTrace.create("op", 0, 10)
        assertTrue(HokmTrace.format(s).isNotEmpty())
    }
}
