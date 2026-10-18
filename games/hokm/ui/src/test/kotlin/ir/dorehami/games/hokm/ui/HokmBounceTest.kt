package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmBounceTest {

    @Test
    fun `bounce endpoints`() {
        assertEquals(0f, HokmBounce.value(0f))
        assertEquals(1f, HokmBounce.value(1f))
    }

    @Test
    fun `bounce in range`() {
        val v = HokmBounce.value(0.5f)
        assertTrue(v in 0f..1f)
    }
}
