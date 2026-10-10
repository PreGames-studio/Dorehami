package ir.dorehami.session

import ir.dorehami.gamesdk.GameAction
import ir.dorehami.protocol.PlayerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

/** Lobby as a client sees it. */
data class RoomInfo(
    val code: String,
    val name: String,
    val gameId: String,
    val members: List<Member>,
    val started: Boolean,
)

/** Things that happen once (feed lines, sounds, public game emissions, reactions). */
sealed interface SessionEvent {
    data class Feed(val key: String, val args: JsonObject, val kind: String) : SessionEvent
    data class Sound(val name: String) : SessionEvent
    data class Public(val type: String, val payload: JsonObject) : SessionEvent
    data class Emoji(val from: PlayerId, val id: String) : SessionEvent
    data class Error(val code: String, val messageFa: String) : SessionEvent
}

/** Everything a game screen needs, whether I am host or guest, on Bluetooth or online. */
data class ClientState(
    val me: PlayerId,
    val room: RoomInfo? = null,
    val seat: Int = -1,
    val isHost: Boolean = false,
    val publicView: JsonObject = JsonObject(emptyMap()),
    val privateView: JsonObject = JsonObject(emptyMap()),
    val legalActions: List<String> = emptyList(),
    val phase: String = "SETUP",
    val seq: Long = 0,
    val result: JsonObject? = null,
    val connected: Boolean = true,
) {
    val players: List<PlayerId> get() = room?.members?.sortedBy { it.seat }?.map { it.player.id } ?: emptyList()
}

/**
 * The game screen talks to this and nothing else. [HostController] implements it for the phone that
 * owns the room (loopback, no transport hop); [GuestController] for everyone else.
 */
interface SessionClient {
    val state: StateFlow<ClientState>
    val events: Flow<SessionEvent>

    /** Tells the host I am ready to start. */
    suspend fun ready(ready: Boolean = true)

    /** Sends my move. Rejections arrive as [SessionEvent.Error]. */
    suspend fun send(action: GameAction)

    /** Quick reaction/sticker visible to everyone (docs/PROTOCOL.md `chat.emoji`). */
    suspend fun emoji(id: String)

    /** "I'm back, send me everything." */
    suspend fun resync()

    suspend fun leave()
}
