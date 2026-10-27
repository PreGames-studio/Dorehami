package ir.dorehami.transport.online

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlin.test.Test
import kotlin.test.assertEquals

/** Compatibility guard for the pure socket-URL mapping shared with the Hokm lobby plan. */
class OnlineSocketUrlTest {

    @Test
    fun `helper maps schemes and keeps port path room code and ticket`() {
        assertEquals(
            "wss://example.test:9443/hokm/v1/ws/room/AB12?ticket=tok",
            socketUrlFor("https://example.test:9443/hokm/", "AB12", "tok"),
        )
        assertEquals(
            "ws://10.0.2.2:8080/v1/ws/room/XY34Z?ticket=v1.abc.def",
            socketUrlFor("http://10.0.2.2:8080", "XY34Z", "v1.abc.def"),
        )
        assertEquals(
            "wss://example.test/v1/ws/room/ACDE3?ticket=t2",
            socketUrlFor("https://example.test", "ACDE3", "t2"),
        )
    }

    @Test
    fun `OnlineApi still derives its socket from the shared helper`() {
        val client = HttpClient(CIO)
        try {
            listOf("https://example.test:9443/hokm/", "http://10.0.2.2:8080/").forEach { raw ->
                val api = OnlineApi(client, raw)
                assertEquals(raw.trimEnd('/'), api.baseUrl)
                assertEquals(
                    socketUrlFor(raw, "AB12", "tok"),
                    api.roomSocketUrl("AB12", "tok"),
                    "socket URL drifted for $raw",
                )
            }
        } finally {
            client.close()
        }
    }
}
