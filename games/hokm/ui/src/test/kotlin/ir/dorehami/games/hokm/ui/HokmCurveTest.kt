package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmCurveTest {

    @Test
    fun `cubic at 0 returns p0`() {
        val p0 = HokmCurve.Point(0f, 0f)
        val p1 = HokmCurve.Point(1f, 2f)
        val p2 = HokmCurve.Point(3f, 4f)
        val p3 = HokmCurve.Point(5f, 0f)
        assertEquals(p0, HokmCurve.cubic(p0, p1, p2, p3, 0f))
    }

    @Test
    fun `cubic at 1 returns p3`() {
        val p0 = HokmCurve.Point(0f, 0f)
        val p1 = HokmCurve.Point(1f, 2f)
        val p2 = HokmCurve.Point(3f, 4f)
        val p3 = HokmCurve.Point(5f, 0f)
        assertEquals(p3, HokmCurve.cubic(p0, p1, p2, p3, 1f))
    }
}
