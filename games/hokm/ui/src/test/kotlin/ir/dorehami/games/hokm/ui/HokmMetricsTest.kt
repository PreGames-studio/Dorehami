package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmMetricsTest {

    @Test
    fun `average`() {
        val m = listOf(HokmMetrics.Metric("x", 10.0, 0), HokmMetrics.Metric("x", 20.0, 0))
        assertEquals(15.0, HokmMetrics.average(m))
    }

    @Test
    fun `max`() {
        val m = listOf(HokmMetrics.Metric("x", 10.0, 0), HokmMetrics.Metric("x", 20.0, 0))
        assertEquals(20.0, HokmMetrics.max(m))
    }

    @Test
    fun `count by name`() {
        val m = listOf(HokmMetrics.Metric("x", 1.0, 0), HokmMetrics.Metric("y", 2.0, 0))
        assertEquals(1, HokmMetrics.count(m, "x"))
    }

    @Test
    fun `average empty`() {
        assertEquals(0.0, HokmMetrics.average(emptyList()))
    }
}
