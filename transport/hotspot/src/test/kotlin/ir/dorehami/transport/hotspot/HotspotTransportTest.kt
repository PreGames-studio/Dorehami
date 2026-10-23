package ir.dorehami.transport.hotspot

import ir.dorehami.transport.TransportEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HotspotTransportTest {
    @Test
    fun hostAndGuestExchangeWholeUtf8Messages() = runBlocking {
        val host = HotspotTransport.host(port = 0)
        val accepted = async {
            withTimeout(5_000) { host.events.first { it is TransportEvent.PeerConnected } }
        }
        val guest = HotspotTransport.join("127.0.0.1", host.boundPort)
        try {
            val hostPeer = (withTimeout(5_000) { accepted.await() } as TransportEvent.PeerConnected).peer
            val incomingHost = async {
                withTimeout(5_000) {
                    host.events.first { it is TransportEvent.Message && it.text.contains("سلام") } as TransportEvent.Message
                }
            }
            val payload = "سلام دورهمی " + "♟".repeat(2_000)
            guest.send(null, payload)
            assertEquals(payload, incomingHost.await().text)

            val incomingGuest = async {
                withTimeout(5_000) {
                    guest.events.first { it is TransportEvent.Message } as TransportEvent.Message
                }
            }
            host.send(hostPeer, "پاسخ میزبان")
            val answer = incomingGuest.await()
            assertEquals(hostPeer, answer.from)
            assertEquals("پاسخ میزبان", answer.text)
        } finally {
            guest.close()
            host.close()
        }
    }

    @Test
    fun broadcastReachesEveryGuestAndDisconnectIsNotSilent() = runBlocking {
        val host = HotspotTransport.host(port = 0)
        val first = HotspotTransport.join("127.0.0.1", host.boundPort)
        val second = HotspotTransport.join("127.0.0.1", host.boundPort)
        try {
            withTimeout(5_000) {
                host.events.first { event ->
                    event is TransportEvent.PeerConnected && event.peer == "guest-2"
                }
            }
            host.send(null, "پیام همگانی")
            val firstMessage = withTimeout(5_000) {
                first.events.first { it is TransportEvent.Message } as TransportEvent.Message
            }
            val secondMessage = withTimeout(5_000) {
                second.events.first { it is TransportEvent.Message } as TransportEvent.Message
            }
            assertEquals("پیام همگانی", firstMessage.text)
            assertEquals("پیام همگانی", secondMessage.text)

            second.close()
            val disconnected = withTimeout(5_000) {
                host.events.first { it is TransportEvent.PeerDisconnected } as TransportEvent.PeerDisconnected
            }
            assertEquals("guest-2", disconnected.peer)
        } finally {
            first.close()
            second.close()
            host.close()
        }
    }

    @Test
    fun hostAddressInputIsNormalized() {
        assertEquals("192.168.43.1", HotspotTransport.normalizeHostAddress("  192.168.43.1  "))
        assertEquals("fd00::1", HotspotTransport.normalizeHostAddress("[fd00::1]"))
        assertTrue(HotspotTransport.DEFAULT_PORT in 1_024..65_535)
    }
}
