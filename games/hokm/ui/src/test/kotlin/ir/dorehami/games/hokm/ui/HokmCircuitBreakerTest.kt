package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HokmCircuitBreakerTest {

    @Test
    fun `opens after threshold`() {
        var b = HokmCircuitBreaker.Breaker(HokmCircuitBreaker.State.CLOSED, 0, 2)
        b = HokmCircuitBreaker.recordFailure(b)
        b = HokmCircuitBreaker.recordFailure(b)
        assertTrue(HokmCircuitBreaker.isOpen(b))
    }

    @Test
    fun `reset closes`() {
        val b = HokmCircuitBreaker.Breaker(HokmCircuitBreaker.State.OPEN, 5, 2)
        val r = HokmCircuitBreaker.reset(b)
        assertEquals(HokmCircuitBreaker.State.CLOSED, r.state)
        assertEquals(0, r.failures)
    }

    @Test
    fun `not open initially`() {
        val b = HokmCircuitBreaker.Breaker(HokmCircuitBreaker.State.CLOSED, 0, 2)
        assertFalse(HokmCircuitBreaker.isOpen(b))
    }
}
