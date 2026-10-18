package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmRotateTest {

    @Test
    fun `normalize 360 to 0`() {
        assertEquals(0f, HokmRotate.normalize(360f))
    }

    @Test
    fun `normalize negative`() {
        assertEquals(270f, HokmRotate.normalize(-90f))
    }

    @Test
    fun `to radians`() {
        assertTrue(HokmRotate.toRadians(180f) > 3f)
    }
}
