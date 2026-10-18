package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmRateLimiterTest {

    @Test
    fun `create full bucket`() {
        val b = HokmRateLimiter.create(10, 2, 0)
        assertEquals(10, b.tokens)
    }

    @Test
    fun `consume succeeds when enough`() {
        val b = HokmRateLimiter.create(10, 2, 0)
        val (ok, b2) = HokmRateLimiter.tryConsume(b, 0)
        assertTrue(ok)
        assertEquals(9, b2.tokens)
    }

    @Test
    fun `consume fails when empty`() {
        val b = HokmRateLimiter.create(0, 2, 0)
        val (ok, _) = HokmRateLimiter.tryConsume(b, 0)
        assertTrue(!ok)
    }

    @Test
    fun `refill adds tokens over time`() {
        val b = HokmRateLimiter.create(5, 2, 0)
        val b2 = HokmRateLimiter.refill(b.copy(tokens = 0), 2000)
        assertTrue(b2.tokens >= 2)
    }

    @Test
    fun `refill caps at capacity`() {
        val b = HokmRateLimiter.create(5, 2, 0)
        val b2 = HokmRateLimiter.refill(b, 100000)
        assertEquals(5, b2.tokens)
    }
}
