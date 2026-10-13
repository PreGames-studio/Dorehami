package ir.dorehami.games.hokm.ui.room

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import ir.dorehami.transport.online.OnlineApi
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class OnlineServerAddressTest {
    @Test
    fun `updated lobby address is used by the next HTTP and WebSocket endpoints`() = runBlocking {
        val address = OnlineServerAddress("http://old.example:8080")
        address.update("  https://new.example:9443/hokm/  ")
        var http: HttpClient? = null
        var invalidAddressMessage: String? = null

        try {
            val api = address.createForNextConnection(
                onInvalidAddress = { invalidAddressMessage = it },
                createConnection = { baseUrl ->
                    val client = HttpClient { install(WebSockets) }.also { http = it }
                    OnlineApi(client, baseUrl)
                },
            )

            assertEquals("https://new.example:9443/hokm", api?.baseUrl)
            assertEquals(
                "wss://new.example:9443/hokm/v1/ws/room/ABCDE?ticket=test-token",
                api?.roomSocketUrl("ABCDE", "test-token"),
            )
            assertNull(invalidAddressMessage)
        } finally {
            http?.close()
        }
    }

    @Test
    fun `invalid lobby address reports a clear error without creating connection resources`() = runBlocking {
        val invalidAddresses = listOf(
            "",
            "example.com",
            "ftp://example.com",
            "https://",
            "https://example.com:70000",
            "https://user@example.com",
            "https://example.com?room=1",
            "https://example.com/#fragment",
        )

        invalidAddresses.forEach { invalid ->
            val address = OnlineServerAddress("http://10.0.2.2:8080")
            address.update(invalid)
            var createConnectionCalled = false
            var shownError: String? = null

            val connection = address.createForNextConnection(
                onInvalidAddress = { shownError = it },
                createConnection = { baseUrl ->
                    createConnectionCalled = true
                    baseUrl
                },
            )

            assertNull(connection, "should reject $invalid")
            assertFalse(createConnectionCalled, "must not create network resources for $invalid")
            assertEquals(OnlineServerAddress.INVALID_ADDRESS_MESSAGE_FA, shownError)
        }
    }
}
