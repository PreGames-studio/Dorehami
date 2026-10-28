package ir.dorehami.transport.online

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import ir.dorehami.session.Member
import ir.dorehami.session.PlayerInfo
import ir.dorehami.session.membersFromJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class OnlineException(val code: String, val messageFa: String) : RuntimeException(messageFa)

data class GuestLogin(val player: PlayerInfo, val token: String)

data class RoomStatus(
    val code: String,
    val name: String,
    val gameId: String,
    val partySize: Int,
    val started: Boolean,
    val members: List<Member>,
)

data class MatchTicket(val id: String, val roomCode: String?)

/**
 * Pure derivation of the room WebSocket URL from an HTTP base URL (PROTOCOL.md section 7).
 *
 * Kept as a top-level function so the Hokm lobby's connection plan and [OnlineApi] cannot drift
 * apart: both callers derive the socket from the very same resolved base URL. No network, clock or
 * mutable state is involved, so the mapping is regression-testable on the JVM.
 */
fun socketUrlFor(baseUrl: String, code: String, token: String): String {
    val ws = baseUrl.trimEnd('/')
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://")
    return "$ws/v1/ws/room/$code?ticket=$token"
}

/**
 * The HTTP half of docs/PROTOCOL.md section 6. Tiny on purpose: five calls. Everything after the
 * socket opens is an Envelope handled by `core/session`.
 */
class OnlineApi(private val client: HttpClient, baseUrl: String) {
    val baseUrl: String = baseUrl.trimEnd('/')

    /** `wss://…/v1/ws/room/{code}?ticket=…` derived from the HTTP base URL. */
    fun roomSocketUrl(code: String, token: String): String = socketUrlFor(baseUrl, code, token)

    suspend fun guestLogin(displayName: String): GuestLogin {
        val o = json(client.post("$baseUrl/v1/auth/guest") { jsonBody(buildJsonObject { put("displayName", displayName) }) })
        return GuestLogin(PlayerInfo.fromJson(o["player"]!!.jsonObject)!!, o["token"]!!.jsonPrimitive.content)
    }

    suspend fun createRoom(token: String, gameId: String, partySize: Int, options: JsonObject? = null, name: String? = null): RoomStatus {
        val o = json(
            client.post("$baseUrl/v1/rooms") {
                bearer(token)
                jsonBody(buildJsonObject { put("gameId", gameId); put("partySize", partySize); if (options != null) put("options", options); if (name != null) put("name", name) })
            },
        )
        return room(o)
    }

    suspend fun room(code: String): RoomStatus = room(json(client.get("$baseUrl/v1/rooms/${code.uppercase()}")))

    suspend fun joinRoom(token: String, code: String): RoomStatus {
        val o = json(client.post("$baseUrl/v1/rooms/${code.uppercase()}/join") { bearer(token); jsonBody(JsonObject(emptyMap())) })
        return room(o["room"]!!.jsonObject)
    }

    suspend fun enqueue(token: String, gameId: String, partySize: Int): MatchTicket {
        val o = json(client.post("$baseUrl/v1/matchmaking") { bearer(token); jsonBody(buildJsonObject { put("gameId", gameId); put("partySize", partySize) }) })
        return MatchTicket(o["ticketId"]!!.jsonPrimitive.content, (o["roomCode"] as? JsonPrimitive)?.contentOrNull)
    }

    /** Long-polls up to [waitSeconds]; returns the ticket (roomCode non-null once matched). */
    suspend fun pollTicket(id: String, waitSeconds: Int = 20): MatchTicket {
        val o = json(client.get("$baseUrl/v1/matchmaking/$id?wait=$waitSeconds"))
        return MatchTicket(o["ticketId"]!!.jsonPrimitive.content, (o["roomCode"] as? JsonPrimitive)?.contentOrNull)
    }

    suspend fun cancelTicket(id: String) {
        client.delete("$baseUrl/v1/matchmaking/$id")
    }

    private fun room(o: JsonObject) = RoomStatus(
        code = o["code"]!!.jsonPrimitive.content,
        name = o["name"]?.jsonPrimitive?.contentOrNull ?: "",
        gameId = o["gameId"]!!.jsonPrimitive.content,
        partySize = o["partySize"]!!.jsonPrimitive.int,
        started = o["started"]?.jsonPrimitive?.contentOrNull == "true",
        members = membersFromJson(o["members"]),
    )

    private suspend fun json(r: HttpResponse): JsonObject {
        val text = r.bodyAsText()
        val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
            ?: throw OnlineException("proto.malformed", "پاسخ سرور قابل خواندن نبود")
        if (r.status.value >= 400) {
            throw OnlineException(o["code"]?.jsonPrimitive?.contentOrNull ?: "server.internal", o["messageFa"]?.jsonPrimitive?.contentOrNull ?: "خطای سرور")
        }
        return o
    }

    private fun io.ktor.client.request.HttpRequestBuilder.bearer(token: String) = header(HttpHeaders.Authorization, "Bearer $token")

    private fun io.ktor.client.request.HttpRequestBuilder.jsonBody(o: JsonObject) {
        contentType(ContentType.Application.Json)
        setBody(o.toString())
    }
}
