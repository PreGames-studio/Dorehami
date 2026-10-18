package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmShakeTest {

    @Test
    fun `offset zero after duration`() {
        assertEquals(0f, HokmShake.offset(2000, 1000, 5f))
    }

    @Test
    fun `offset in range during`() {
        val v = HokmShake.offset(100, 1000, 5f)
        assertTrue(v in -5f..5f)
    }
}
