package ir.dorehami.session

import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/** Message types (docs/PROTOCOL.md section 2). */
object Msg {
    const val SYS_HELLO = "sys.hello"
    const val SYS_ERROR = "sys.error"
    const val LOBBY_JOIN = "lobby.join"
    const val LOBBY_ACK = "lobby.ack"
    const val LOBBY_REJECTED = "lobby.rejected"
    const val LOBBY_ROSTER = "lobby.roster"
    const val LOBBY_READY = "lobby.ready"
    const val LOBBY_START = "lobby.start"
    const val LOBBY_LEAVE = "lobby.leave"
    const val SESSION_STATE = "session.state"
    const val SESSION_PRIVATE = "session.private"
    const val SESSION_ACTION = "session.action"
    const val SESSION_EVENT = "session.event"
    const val SESSION_RESYNC_REQUEST = "session.resync.request"
    const val SESSION_RESYNC = "session.resync"
    const val SESSION_FINISHED = "session.finished"
    const val CHAT_EMOJI = "chat.emoji"
}

const val PROTO_VERSION = 1

/** Maximum encoded envelope size (docs/PROTOCOL.md section 1). */
const val MAX_ENVELOPE_BYTES = 16 * 1024

/** The one and only wire message. `from` is diagnostic — identity comes from the transport peer. */
data class Envelope(
    val type: String,
    val room: String,
    val from: String,
    val payload: JsonObject,
    val to: String? = null,
    val seq: Long = 0,
    val id: String = "",
    val ts: Long = 0,
    val v: Int = PROTO_VERSION,
) {
    fun encode(): String = Json.encodeToString(
        buildJsonObject {
            put("v", v)
            put("id", id)
            put("ts", ts)
            put("type", type)
            put("room", room)
            put("from", from)
            if (to != null) put("to", to)
            put("seq", seq)
            put("payload", payload)
        },
    )

    companion object {
        /** Returns `null` for anything we must not act on: malformed, wrong version, too large. */
        fun decode(text: String): Envelope? {
            if (text.length > MAX_ENVELOPE_BYTES) return null
            val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
            val v = o["v"]?.jsonPrimitive?.int ?: return null
            if (v != PROTO_VERSION) return null
            val type = o["type"]?.jsonPrimitive?.contentOrNull ?: return null
            val room = o["room"]?.jsonPrimitive?.contentOrNull ?: return null
            val from = o["from"]?.jsonPrimitive?.contentOrNull ?: return null
            val payload = (o["payload"] as? JsonObject) ?: JsonObject(emptyMap())
            val to = (o["to"] as? JsonPrimitive)?.contentOrNull
            return Envelope(
                type = type,
                room = room,
                from = from,
                payload = payload,
                to = to,
                seq = o["seq"]?.jsonPrimitive?.long ?: 0L,
                id = o["id"]?.jsonPrimitive?.contentOrNull ?: "",
                ts = o["ts"]?.jsonPrimitive?.long ?: 0L,
                v = v,
            )
        }
    }
}

/** Who sits at the table. */
data class PlayerInfo(val id: PlayerId, val displayName: String, val avatarId: String = "default") {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id.value)
        put("displayName", displayName)
        put("avatarId", avatarId)
    }

    companion object {
        fun fromJson(o: JsonObject): PlayerInfo? {
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: return null
            return PlayerInfo(
                id = PlayerId(id),
                displayName = o["displayName"]?.jsonPrimitive?.contentOrNull ?: id,
                avatarId = o["avatarId"]?.jsonPrimitive?.contentOrNull ?: "default",
            )
        }
    }
}

data class Member(
    val player: PlayerInfo,
    val seat: Int,
    val ready: Boolean,
    val connected: Boolean,
    val isHost: Boolean,
    val isBot: Boolean = false,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("player", player.toJson())
        put("seat", seat)
        put("ready", ready)
        put("connected", connected)
        put("isHost", isHost)
        put("isBot", isBot)
    }

    companion object {
        fun fromJson(o: JsonObject): Member? {
            val p = (o["player"] as? JsonObject)?.let(PlayerInfo::fromJson) ?: return null
            return Member(
                player = p,
                seat = o["seat"]?.jsonPrimitive?.int ?: return null,
                ready = o["ready"]?.jsonPrimitive?.contentOrNull == "true",
                connected = o["connected"]?.jsonPrimitive?.contentOrNull != "false",
                isHost = o["isHost"]?.jsonPrimitive?.contentOrNull == "true",
                isBot = o["isBot"]?.jsonPrimitive?.contentOrNull == "true",
            )
        }
    }
}

internal fun membersJson(members: Collection<Member>): JsonElement = buildJsonArray {
    members.sortedBy { it.seat }.forEach { add(it.toJson()) }
}

fun membersFromJson(e: JsonElement?): List<Member> =
    (e as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonObject)?.let(Member::fromJson) } ?: emptyList()

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.strList(key: String): List<String> =
    (this[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

internal val NULL: JsonElement = JsonNull
