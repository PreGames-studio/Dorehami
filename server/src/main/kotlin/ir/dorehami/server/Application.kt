package ir.dorehami.server

import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.gamesdk.GameRegistry
import ir.dorehami.protocol.ErrCodes
import ir.dorehami.server.rooms.InMemoryMatchStore
import ir.dorehami.server.rooms.Matchmaker
import ir.dorehami.server.rooms.RoomException
import ir.dorehami.server.rooms.RoomHub
import ir.dorehami.server.rooms.toJson
import ir.dorehami.session.PlayerInfo
import ir.dorehami.protocol.PlayerId
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.seconds

/** Everything the routes need; built once per server (and once per test). */
class ServerDeps(
    tokenSecret: String,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    botDelayMs: Long = 900,
) {
    val registry = GameRegistry(listOf(HokmModule))
    val store = InMemoryMatchStore()
    val hub = RoomHub(registry, scope, store, botDelayMs = botDelayMs)
    val matchmaker = Matchmaker(hub)
    val tokens = Tokens(tokenSecret)
    val scope = scope
}

fun main() {
    val host = System.getenv("DOREHAMI_HOST") ?: "0.0.0.0"
    val port = System.getenv("DOREHAMI_PORT")?.toIntOrNull() ?: 8080
    val secret = System.getenv("DOREHAMI_TOKEN_SECRET") ?: run {
        System.err.println("DOREHAMI_TOKEN_SECRET not set — using a random secret (tokens die with the process)")
        java.util.UUID.randomUUID().toString()
    }
    val deps = ServerDeps(secret)
    embeddedServer(Netty, port = port, host = host) { dorehamiModule(deps) }.start(wait = true)
}

class ApiException(val status: HttpStatusCode, val code: String, val messageFa: String) : RuntimeException(messageFa)

fun Application.dorehamiModule(deps: ServerDeps) {
    install(ContentNegotiation) { json() }
    install(WebSockets) {
        pingPeriod = 20.seconds
        timeout = 60.seconds
        maxFrameSize = 24L * 1024
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Delete)
    }
    install(CallLogging)
    install(StatusPages) {
        exception<RoomException> { call, e ->
            val status = when (e.code) {
                ErrCodes.ROOM_NOT_FOUND -> HttpStatusCode.NotFound
                ErrCodes.ROOM_FULL, ErrCodes.ROOM_IN_PROGRESS -> HttpStatusCode.Conflict
                else -> HttpStatusCode.BadRequest
            }
            call.respond(status, errorJson(e.code, e.messageFa))
        }
        exception<ApiException> { call, e -> call.respond(e.status, errorJson(e.code, e.messageFa)) }
        exception<Throwable> { call, _ -> call.respond(HttpStatusCode.InternalServerError, errorJson(ErrCodes.SERVER_INTERNAL, "خطای داخلی سرور")) }
    }

    // Housekeeping: reap dead rooms, give lonely matchmaking tickets a bot room.
    deps.scope.launch {
        while (true) {
            delay(5_000)
            runCatching { deps.matchmaker.fillWithBotsOlderThan(25_000) }
            runCatching { deps.hub.reap() }
        }
    }

    routing {
        get("/healthz") {
            call.respond(buildJsonObject { put("ok", true); put("rooms", deps.hub.all().size); put("waiting", deps.matchmaker.waiting()) })
        }

        get("/v1/games") {
            call.respond(
                buildJsonArray {
                    deps.registry.all.forEach { m ->
                        add(
                            buildJsonObject {
                                put("id", m.meta.id.value); put("titleFa", m.meta.titleFa); put("titleEn", m.meta.titleEn)
                                put("minPlayers", m.meta.minPlayers); put("maxPlayers", m.meta.maxPlayers)
                                put("preferredPartySize", m.preferredPartySize()); put("defaultOptions", m.defaultOptions())
                            },
                        )
                    }
                },
            )
        }

        post("/v1/auth/guest") {
            val body = call.receive<JsonObject>()
            val name = body.str("displayName")?.trim()?.take(24)?.ifBlank { null } ?: "مهمان"
            val player = PlayerInfo(PlayerId(Tokens.newPlayerId()), name, body.str("avatarId") ?: "default")
            call.respond(buildJsonObject { put("player", player.toJson()); put("token", deps.tokens.issue(player)) })
        }

        post("/v1/rooms") {
            val me = call.player(deps)
            val body = call.receive<JsonObject>()
            val gameId = body.str("gameId") ?: throw ApiException(HttpStatusCode.BadRequest, ErrCodes.BAD_PAYLOAD, "gameId لازم است")
            val room = deps.hub.create(
                gameId = gameId,
                partySize = body["partySize"]?.jsonPrimitive?.intOrNull,
                options = body["options"] as? JsonObject,
                name = body.str("name")?.take(32) ?: "اتاق ${me.displayName}",
            )
            call.respond(HttpStatusCode.Created, roomJson(room))
        }

        get("/v1/rooms/{code}") {
            val room = deps.hub.get(call.parameters["code"] ?: "") ?: throw RoomException(ErrCodes.ROOM_NOT_FOUND, "اتاقی با این کد پیدا نشد")
            call.respond(roomJson(room))
        }

        post("/v1/rooms/{code}/join") {
            val me = call.player(deps)
            val room = deps.hub.get(call.parameters["code"] ?: "") ?: throw RoomException(ErrCodes.ROOM_NOT_FOUND, "اتاقی با این کد پیدا نشد")
            val already = room.controller.state.value.room?.members?.any { it.player.id == me.id } == true
            if (!already && room.started) throw RoomException(ErrCodes.ROOM_IN_PROGRESS, "بازی در این اتاق شروع شده")
            if (!already && room.memberCount >= room.partySize) throw RoomException(ErrCodes.ROOM_FULL, "اتاق پر است")
            call.respond(buildJsonObject { put("ok", true); put("room", roomJson(room)); put("wsPath", "/v1/ws/room/${room.code}") })
        }

        post("/v1/matchmaking") {
            val me = call.player(deps)
            val body = call.receive<JsonObject>()
            val gameId = body.str("gameId") ?: "hokm"
            val module = deps.registry[ir.dorehami.protocol.GameId(gameId)] ?: throw RoomException(ErrCodes.PROTO_UNKNOWN_TYPE, "این بازی روی سرور نیست")
            val size = (body["partySize"]?.jsonPrimitive?.intOrNull ?: module.preferredPartySize()).coerceIn(module.meta.minPlayers, module.meta.maxPlayers)
            val t = deps.matchmaker.enqueue(me, gameId, size)
            call.respond(HttpStatusCode.Accepted, buildJsonObject { put("ticketId", t.id); put("roomCode", t.roomCode) })
        }

        get("/v1/matchmaking/{id}") {
            val id = call.parameters["id"] ?: ""
            val wait = (call.request.queryParameters["wait"]?.toIntOrNull() ?: 0).coerceIn(0, 25)
            val deadline = System.currentTimeMillis() + wait * 1000L
            var t = deps.matchmaker.get(id) ?: throw ApiException(HttpStatusCode.NotFound, ErrCodes.ROOM_NOT_FOUND, "بلیت پیدا نشد")
            while (t.roomCode == null && System.currentTimeMillis() < deadline) {
                delay(500)
                t = deps.matchmaker.get(id) ?: throw ApiException(HttpStatusCode.NotFound, ErrCodes.ROOM_NOT_FOUND, "بلیت پیدا نشد")
            }
            call.respond(buildJsonObject { put("ticketId", t.id); put("roomCode", t.roomCode); put("waiting", deps.matchmaker.waiting()) })
        }

        delete("/v1/matchmaking/{id}") {
            deps.matchmaker.cancel(call.parameters["id"] ?: "")
            call.respond(HttpStatusCode.NoContent)
        }

        get("/v1/matches/{code}") {
            val rec = deps.store.get((call.parameters["code"] ?: "").uppercase()) ?: throw ApiException(HttpStatusCode.NotFound, ErrCodes.ROOM_NOT_FOUND, "رکوردی نیست")
            call.respond(rec.toJson())
        }

        // From here on everything is an Envelope (docs/PROTOCOL.md). Identity = the ticket, never the message.
        webSocket("/v1/ws/room/{code}") {
            val code = (call.parameters["code"] ?: "").uppercase()
            val me = deps.tokens.verify(call.request.queryParameters["ticket"])
            if (me == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, ErrCodes.AUTH_UNAUTHORIZED))
                return@webSocket
            }
            val sink: suspend (String) -> Unit = { text -> send(Frame.Text(text)) }
            try {
                deps.hub.attach(code, me, sink)
            } catch (e: RoomException) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.code))
                return@webSocket
            }
            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) deps.hub.inbound(code, me.id, frame.readText())
                }
            } finally {
                deps.hub.detach(code, me.id, sink)
            }
        }
    }
}

private fun ApplicationCall.player(deps: ServerDeps): PlayerInfo {
    val header = request.headers[HttpHeaders.Authorization] ?: ""
    val token = header.removePrefix("Bearer ").trim().ifBlank { request.queryParameters["ticket"] }
    return deps.tokens.verify(token) ?: throw ApiException(HttpStatusCode.Unauthorized, ErrCodes.AUTH_UNAUTHORIZED, "اول وارد شو")
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun errorJson(code: String, messageFa: String) = buildJsonObject { put("ok", false); put("code", code); put("messageFa", messageFa) }

private fun roomJson(room: ir.dorehami.server.rooms.ServerRoom): JsonObject {
    val st = room.controller.state.value
    return buildJsonObject {
        put("code", room.code)
        put("name", room.name)
        put("gameId", room.gameId)
        put("partySize", room.partySize)
        put("options", room.options)
        put("started", room.started)
        put("finished", room.finished)
        put("members", buildJsonArray { st.room?.members?.sortedBy { it.seat }?.forEach { add(it.toJson()) } })
    }
}
