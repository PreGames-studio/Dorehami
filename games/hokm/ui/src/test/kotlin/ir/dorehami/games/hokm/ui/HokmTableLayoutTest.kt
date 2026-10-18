package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmTableLayoutTest {

    @Test
    fun `seat positions in range`() {
        for (i in 0..3) {
            val p = HokmTableLayout.seatPosition(i)
            assertTrue(p.x in 0f..1f)
            assertTrue(p.y in 0f..1f)
        }
    }

    @Test
    fun `me is seat 0`() {
        assertTrue(HokmTableLayout.isMe(0))
        assertFalse(HokmTableLayout.isMe(1))
    }

    @Test
    fun `partners are opposite`() {
        assertTrue(HokmTableLayout.arePartners(0, 2))
        assertTrue(HokmTableLayout.arePartners(1, 3))
        assertFalse(HokmTableLayout.arePartners(0, 1))
    }

    @Test
    fun `trick center is middle`() {
        assertEquals(0.5f, HokmTableLayout.TRICK_CENTER.x)
        assertEquals(0.5f, HokmTableLayout.TRICK_CENTER.y)
    }

    @Test
    fun `play target between seat and center`() {
        val p = HokmTableLayout.playTarget(0)
        assertTrue(p.y > 0.5f && p.y < 0.85f)
    }

    @Test
    fun `distance non-negative`() {
        val d = HokmTableLayout.distance(HokmTableLayout.seatPosition(0), HokmTableLayout.seatPosition(2))
        assertTrue(d > 0f)
    }

    @Test
    fun `seats constant`() {
        assertEquals(4, HokmTableLayout.SEATS)
    }
}
