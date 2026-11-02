package ir.dorehami.games.backgammon.ui

import ir.dorehami.games.backgammon.BackgammonRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The tap map *is* the board as far as the player is concerned, so it is tested on its own: a tap on
 * the centre of a point must report that point, a tap on the bar must report `FROM_BAR`, and a tap
 * outside the board must report nothing. Drawing uses the same [BackgammonGeometry], so the two can
 * never disagree.
 */
class BackgammonGeometryTest {

    private val geometry = BackgammonGeometry(width = 1_000f, height = 700f)

    private fun centreX(index: Int): Float = geometry.leftOf(index) + geometry.cell / 2f

    @Test
    fun `every point round-trips through its own centre`() {
        for (index in 0 until BackgammonRules.POINT_COUNT) {
            val y = if (geometry.isTop(index)) 20f else 680f
            assertEquals(index, geometry.hit(centreX(index), y), "point ${index + 1} must map to itself")
        }
    }

    @Test
    fun `the two rows are split by the middle of the board`() {
        val y = geometry.height / 2f
        assertEquals(0, geometry.hit(centreX(0), y + 10f), "bottom row holds points 1..12")
        assertEquals(23, geometry.hit(centreX(0), y - 10f), "top row holds points 13..24")
        assertEquals(11, geometry.hit(centreX(11), y + 10f))
        assertEquals(12, geometry.hit(centreX(12), y - 10f))
    }

    @Test
    fun `the bar is its own drop target`() {
        val x = geometry.half + geometry.bar / 2f
        assertEquals(BackgammonRules.FROM_BAR, geometry.hit(x, 5f))
        assertEquals(BackgammonRules.FROM_BAR, geometry.hit(x, geometry.height - 5f))
    }

    @Test
    fun `taps outside the board are ignored`() {
        assertNull(geometry.hit(-1f, 10f))
        assertNull(geometry.hit(10f, -1f))
        assertNull(geometry.hit(1_001f, 10f))
        assertNull(geometry.hit(10f, 701f))
    }

    @Test
    fun `points stay inside the board horizontally`() {
        for (index in 0 until BackgammonRules.POINT_COUNT) {
            val left = geometry.leftOf(index)
            assertTrue(left >= 0f && left + geometry.cell <= geometry.width, "point ${index + 1} fits")
        }
    }
}
