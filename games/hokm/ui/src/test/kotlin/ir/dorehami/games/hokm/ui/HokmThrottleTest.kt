package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class HokmThrottleTest {

    @Test
    fun `can fire after interval`() {
        assertTrue(HokmThrottle.canFire(0, 100, 50))
        assertFalse(HokmThrottle.canFire(0, 10, 50))
    }

    @Test
    fun `next fire`() {
        assertEquals(100L, HokmThrottle.nextFireMs(50, 50))
    }
}
