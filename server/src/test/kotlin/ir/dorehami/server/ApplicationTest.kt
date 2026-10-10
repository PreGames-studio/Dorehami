package ir.dorehami.server

import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import ir.dorehami.session.Envelope
import ir.dorehami.session.Msg
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** HTTP + WebSocket smoke test through the real Ktor pipeline (run with `./gradlew :server:test`). */
class ApplicationTest {
    @Test
    fun `guest logs in, creates a room, joins over websocket and gets lobby ack`() = testApplication {
        val deps = ServerDeps("test-secret-test-secret-test-secret", botDelayMs = 0)
        application { dorehamiModule(deps) }
        val client = createClient { install(WebSockets) }

        assertEquals(HttpStatusCode.OK, client.get("/healthz").status)

        val auth = client.post("/v1/auth/guest") {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"سارا"}""")
        }
        assertEquals(HttpStatusCode.OK, auth.status)
        val token = Json.parseToJsonElement(auth.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content

        val created = client.post("/v1/rooms") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"gameId":"hokm","partySize":4}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val code = Json.parseToJsonElement(created.bodyAsText()).jsonObject["code"]!!.jsonPrimitive.content

        client.webSocket("/v1/ws/room/$code?ticket=$token") {
            val me = deps.tokens.verify(token)!!
            send(Frame.Text(Envelope(Msg.LOBBY_JOIN, code, me.id.value, buildJsonObject { put("player", me.toJson()) }).encode()))
            val ack = withTimeout(5_000) {
                var env: Envelope? = null
                while (env?.type != Msg.LOBBY_ACK) {
                    val f = incoming.receive()
                    if (f is Frame.Text) env = Envelope.decode(f.readText())
                }
                env
            }
            assertNotNull(ack)
            assertEquals("0", ack.payload["seat"]!!.jsonPrimitive.content)
        }
    }
}
