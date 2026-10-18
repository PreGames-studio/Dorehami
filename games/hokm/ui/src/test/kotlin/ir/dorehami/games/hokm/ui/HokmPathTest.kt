package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmPathTest {

    @Test
    fun `quadratic at 0 returns p0`() {
        val p0 = HokmPath.Point(0f, 0f)
        val p1 = HokmPath.Point(5f, 5f)
        val p2 = HokmPath.Point(10f, 0f)
        assertEquals(p0, HokmPath.quadratic(p0, p1, p2, 0f))
    }

    @Test
    fun `quadratic at 1 returns p2`() {
        val p0 = HokmPath.Point(0f, 0f)
        val p1 = HokmPath.Point(5f, 5f)
        val p2 = HokmPath.Point(10f, 0f)
        assertEquals(p2, HokmPath.quadratic(p0, p1, p2, 1f))
    }

    @Test
    fun `arc at 0 returns start`() {
        val s = HokmPath.Point(0f, 0f)
        val e = HokmPath.Point(10f, 0f)
        assertEquals(s, HokmPath.arc(s, e, 5f, 0f))
    }

    @Test
    fun `arc at 1 returns end`() {
        val s = HokmPath.Point(0f, 0f)
        val e = HokmPath.Point(10f, 0f)
        val r = HokmPath.arc(s, e, 5f, 1f)
        assertTrue(r.x == e.x && kotlin.math.abs(r.y - e.y) < 0.001f)
    }

    @Test
    fun `arc midpoint elevated`() {
        val s = HokmPath.Point(0f, 0f)
        val e = HokmPath.Point(10f, 0f)
        val m = HokmPath.arc(s, e, 5f, 0.5f)
        assertTrue(m.y < 0f)
    }
}
