package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmPingTest {

    @Test
    fun `good ping`() {
        assertTrue(HokmPing.isGood(50))
        assertTrue(!HokmPing.isGood(150))
    }

    @Test
    fun `acceptable ping`() {
        assertTrue(HokmPing.isAcceptable(200))
        assertTrue(!HokmPing.isAcceptable(400))
    }

    @Test
    fun `quality labels`() {
        assertEquals("excellent", HokmPing.quality(30))
        assertEquals("good", HokmPing.quality(80))
        assertEquals("fair", HokmPing.quality(200))
        assertEquals("poor", HokmPing.quality(500))
    }
}
