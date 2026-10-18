package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class HokmSkewTest {

    @Test
    fun `clamp range`() {
        assertEquals(10f, HokmSkew.clamp(20f))
        assertEquals(-10f, HokmSkew.clamp(-20f))
        assertEquals(5f, HokmSkew.clamp(5f))
    }
}
