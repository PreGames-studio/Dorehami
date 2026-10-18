package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class HokmTimeoutTest {

    @Test
    fun `is expired`() {
        assertTrue(HokmTimeout.isExpired(100, 100))
        assertFalse(HokmTimeout.isExpired(99, 100))
    }

    @Test
    fun `remaining`() {
        assertEquals(10L, HokmTimeout.remainingMs(90, 100))
        assertEquals(0L, HokmTimeout.remainingMs(200, 100))
    }

    @Test
    fun `deadline from`() {
        assertEquals(150L, HokmTimeout.deadlineFrom(100, 50))
    }
}
