package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmConnectivityTest {

    @Test
    fun `online is online`() {
        assertTrue(HokmConnectivity.isOnline(HokmConnectivity.State.ONLINE))
    }

    @Test
    fun `offline not online`() {
        assertTrue(!HokmConnectivity.isOnline(HokmConnectivity.State.OFFLINE))
    }

    @Test
    fun `should queue when offline`() {
        assertTrue(HokmConnectivity.shouldQueue(HokmConnectivity.State.OFFLINE))
        assertTrue(!HokmConnectivity.shouldQueue(HokmConnectivity.State.ONLINE))
    }

    @Test
    fun `labels non-empty`() {
        HokmConnectivity.State.values().forEach { s ->
            assertTrue(HokmConnectivity.label(s).isNotEmpty())
        }
    }
}
