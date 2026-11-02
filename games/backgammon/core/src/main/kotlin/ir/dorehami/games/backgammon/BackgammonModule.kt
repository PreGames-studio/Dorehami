package ir.dorehami.games.backgammon

import ir.dorehami.gamesdk.Capability
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameConfigException
import ir.dorehami.gamesdk.GameMeta
import ir.dorehami.gamesdk.GameModule
import ir.dorehami.gamesdk.GameSession
import ir.dorehami.protocol.GameId
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Backgammon (تخته نرد) — two players, perfect information, dice driven by the injected [ir.dorehami.gamesdk.Rng].
 *
 * Rules contract: `games/backgammon/RULES.md`. Board indexes and the meaning of a move are documented
 * on [BackgammonRules].
 */
object BackgammonModule : GameModule {

    const val ID = "backgammon"

    /** Roll the dice for the side to move. No payload. */
    const val ACTION_ROLL = "roll"

    /** Submit the whole maximal sequence of die-uses. Payload: `{ "moves": [{ "from": 12, "die": 5 }] }`. */
    const val ACTION_PLAY = "play"

    const val PAYLOAD_MOVES = "moves"

    const val DEFAULT_TARGET_POINTS = 5

    /** Match lengths offered in the room settings (match points needed to win). */
    val ALLOWED_TARGETS = listOf(1, 3, 5, 7)

    override val meta = GameMeta(
        id = GameId(ID),
        titleFa = "تخته نرد",
        titleEn = "Backgammon",
        taglineFa = "تاس، مهره و رقابت دونفرهٔ اصیل ایرانی",
        minPlayers = 2,
        maxPlayers = 2,
        iconKey = "backgammon",
        estimatedMinutes = 15,
        capabilities = setOf(
            Capability.BLUETOOTH_OFFLINE.name,
            Capability.ONLINE.name,
            Capability.RANDOM_MATCH.name,
            Capability.BOTS.name,
            Capability.SOLO.name,
        ),
        defaultOptions = buildJsonObject {
            put(BackgammonSession.Options.TARGET_POINTS, DEFAULT_TARGET_POINTS)
        },
    )

    /** The `play` action for a ready-made sequence (shared by the bot and the table). */
    fun playAction(moves: List<BackgammonMove>): GameAction = GameAction(
        type = ACTION_PLAY,
        payload = buildJsonObject {
            put(
                PAYLOAD_MOVES,
                buildJsonArray {
                    moves.forEach { move ->
                        add(
                            buildJsonObject {
                                put("from", move.from)
                                put("die", move.die)
                            },
                        )
                    }
                },
            )
        },
    )

    override fun validateOptions(options: JsonObject): JsonObject {
        val target = (options[BackgammonSession.Options.TARGET_POINTS] as? JsonPrimitive)?.intOrNull
            ?: DEFAULT_TARGET_POINTS
        if (target !in ALLOWED_TARGETS) {
            throw GameConfigException(
                "game.bad_options",
                "امتیاز پایان مسابقه باید یکی از ${ALLOWED_TARGETS.joinToString("، ")} باشد",
            )
        }
        return buildJsonObject { put(BackgammonSession.Options.TARGET_POINTS, target) }
    }

    override fun create(config: GameConfig): GameSession = BackgammonSession(config)

    override fun preferredPartySize(): Int = 2

    override val botAction: ((GameSession, PlayerId) -> GameAction?)? = { session, player ->
        (session as? BackgammonSession)?.let { BackgammonBot.action(it, player) }
    }
}
