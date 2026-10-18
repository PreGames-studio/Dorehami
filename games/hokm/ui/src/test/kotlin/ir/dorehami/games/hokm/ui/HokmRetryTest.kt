package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmRetryTest {

    @Test
    fun `should retry within limit`() {
        assertTrue(HokmRetry.shouldRetry(0))
        assertTrue(HokmRetry.shouldRetry(2))
    }

    @Test
    fun `should not retry at limit`() {
        assertFalse(HokmRetry.shouldRetry(3))
    }

    @Test
    fun `delay grows`() {
        assertTrue(HokmRetry.delayFor(0) < HokmRetry.delayFor(2))
    }

    @Test
    fun `delay capped`() {
        assertTrue(HokmRetry.delayFor(10) <= HokmRetry.DEFAULT.maxDelayMs)
    }

    @Test
    fun `last attempt detection`() {
        assertTrue(HokmRetry.isLastAttempt(2))
        assertFalse(HokmRetry.isLastAttempt(1))
    }
}
