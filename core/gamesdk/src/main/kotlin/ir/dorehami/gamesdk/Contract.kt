package ir.dorehami.gamesdk

import ir.dorehami.protocol.GameId
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject

/** What a game can do. Stored as names inside [GameMeta.capabilities] so the list is wire-friendly. */
enum class Capability {
    /** Playable over Bluetooth / Wi-Fi Direct with no server at all. */
    BLUETOOTH_OFFLINE,

    /** Playable through the online server. */
    ONLINE,

    /** Can be filled by the random-opponent queue. */
    RANDOM_MATCH,

    /** Has a deterministic bot policy (`GameModule.botAction`). */
    BOTS,

    /** Can be played on one phone against bots, with no connection. */
    SOLO,
}

/** Question 1 of the contract: how many players, teams, capabilities, defaults. */
data class GameMeta(
    val id: GameId,
    val titleFa: String,
    val titleEn: String,
    val taglineFa: String,
    val minPlayers: Int,
    val maxPlayers: Int,
    /** `0` for individual games. */
    val teamCount: Int = 0,
    val playersPerTeam: Int = 0,
    val iconKey: String,
    val estimatedMinutes: Int,
    val capabilities: Set<String>,
    val defaultOptions: JsonObject,
) {
    init {
        require(minPlayers in 1..maxPlayers) { "minPlayers must be within 1..maxPlayers" }
        if (teamCount > 0) {
            require(playersPerTeam > 0) { "team games need playersPerTeam" }
        }
    }

    val isTeamGame: Boolean get() = teamCount > 0
}

/** Lifecycle of one [GameSession]. */
enum class Phase { SETUP, PLAYING, FINISHED }

/** Everything a session needs to be created deterministically (contract rule 1). */
data class GameConfig(
    val seed: Long,
    /** Seat order. Index in this list == seat number. */
    val players: List<PlayerId>,
    val options: JsonObject,
    val clock: Clock,
    val rng: Rng,
) {
    init {
        require(players.isNotEmpty()) { "a game needs at least one player" }
        require(players.toSet().size == players.size) { "duplicate player ids" }
    }

    fun seatOf(player: PlayerId): Int = players.indexOf(player)
}

/** A move. `type` is a fixed snake_case identifier owned by the game (`play_card`, `choose_hokm`). */
data class GameAction(
    val type: String,
    val payload: JsonObject = EMPTY_JSON,
)

/** Thrown by [GameModule.validateOptions] when the host configured a room incorrectly. */
class GameConfigException(val code: String, val messageFa: String) : IllegalArgumentException(messageFa)

/** Result of [GameSession.apply]. Never throw from `apply`: return [Rejected] (contract rule 3). */
sealed interface ApplyResult {
    data class Ok(val emissions: List<Emission>) : ApplyResult
    data class Rejected(val code: String, val messageFa: String) : ApplyResult
}

/** "What should be broadcast" after a successful action. */
sealed interface Emission {
    /** For everybody in the room. */
    data class Public(val type: String, val payload: JsonObject) : Emission

    /** For a single player only — the only way hidden information may travel. */
    data class Private(val to: PlayerId, val view: JsonObject) : Emission

    /** One translatable feed line (`trick_won`, …); the UI renders the Persian text. */
    data class Feed(val key: String, val args: JsonObject, val kind: String = "info") : Emission

    /** Fixed sound-effect name; the UI maps it to a file. */
    data class Sound(val name: String) : Emission
}

/** Final standing of one player or team. Lower [rank] is better; ties share a rank. */
data class Standing(
    /** Player ids of this entry (one id for individual games, the whole team for team games). */
    val members: List<PlayerId>,
    val rank: Int,
    val score: Int,
)

/** Question 5: the final table. */
data class MatchResult(
    val standings: List<Standing>,
) {
    val winners: List<PlayerId> get() = standings.filter { it.rank == 1 }.flatMap { it.members }
}

/** Question 1–7 answered by one game. See docs/GAME_CONTRACT.md. */
interface GameModule {
    val meta: GameMeta

    fun defaultOptions(): JsonObject = meta.defaultOptions

    /** Returns the normalised options or throws [GameConfigException] with a Persian message. */
    fun validateOptions(options: JsonObject): JsonObject

    fun create(config: GameConfig): GameSession

    fun preferredPartySize(): Int = meta.minPlayers

    /** Question 6 — deterministic bot policy; `null` if the game has no bots. */
    val botAction: ((GameSession, PlayerId) -> GameAction?)?
}

/** The rules of one running match. Pure function of `(seed, action log)`. */
interface GameSession {
    val phase: Phase

    /** Increments by exactly one on every accepted action; unchanged on rejection. */
    val version: Long

    fun apply(player: PlayerId, action: GameAction): ApplyResult

    fun publicView(): JsonObject

    fun privateView(player: PlayerId): JsonObject

    fun legalActions(player: PlayerId): List<String>

    fun result(): MatchResult?

    fun snapshot(): JsonObject
}

/** Shared empty object so actions without payload do not allocate. */
val EMPTY_JSON: JsonObject = JsonObject(emptyMap())

/** Small sugar so game code reads `view { put("turn", 2) }`. */
inline fun view(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)
