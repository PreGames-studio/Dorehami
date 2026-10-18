package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmReconnectTest {

    @Test
    fun `initial is connected`() {
        assertEquals(HokmReconnect.State.CONNECTED, HokmReconnect.initial().state)
    }

    @Test
    fun `disconnect changes state`() {
        val s = HokmReconnect.disconnect(HokmReconnect.initial())
        assertEquals(HokmReconnect.State.DISCONNECTED, s.state)
    }

    @Test
    fun `start reconnect`() {
        val s = HokmReconnect.startReconnect(HokmReconnect.disconnect(HokmReconnect.initial()))
        assertEquals(HokmReconnect.State.RECONNECTING, s.state)
    }

    @Test
    fun `success reconnects`() {
        val s = HokmReconnect.reconnectSuccess(HokmReconnect.startReconnect(HokmReconnect.disconnect(HokmReconnect.initial())))
        assertEquals(HokmReconnect.State.RECONNECTED, s.state)
    }

    @Test
    fun `fail increments attempts`() {
        val s = HokmReconnect.disconnect(HokmReconnect.initial())
        val s2 = HokmReconnect.reconnectFail(s, "timeout")
        assertEquals(1, s2.attempts)
        assertEquals("timeout", s2.lastError)
    }

    @Test
    fun `max attempts fails`() {
        var s = HokmReconnect.disconnect(HokmReconnect.initial())
        repeat(HokmReconnect.MAX_ATTEMPTS) { s = HokmReconnect.reconnectFail(s, "err") }
        assertEquals(HokmReconnect.State.FAILED, s.state)
        assertFalse(HokmReconnect.canRetry(s))
    }

    @Test
    fun `backoff grows`() {
        assertTrue(HokmReconnect.backoffMs(0) < HokmReconnect.backoffMs(3))
    }

    @Test
    fun `backoff capped`() {
        assertTrue(HokmReconnect.backoffMs(10) <= HokmReconnect.MAX_BACKOFF_MS)
    }

    @Test
    fun `can retry when not failed`() {
        val s = HokmReconnect.disconnect(HokmReconnect.initial())
        assertTrue(HokmReconnect.canRetry(s))
    }

    @Test
    fun `reset clears`() {
        val s = HokmReconnect.reset(HokmReconnect.disconnect(HokmReconnect.initial()))
        assertEquals(HokmReconnect.State.CONNECTED, s.state)
        assertEquals(0, s.attempts)
    }
}
