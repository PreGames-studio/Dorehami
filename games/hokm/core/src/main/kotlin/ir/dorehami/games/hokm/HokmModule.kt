package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.Capability
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameConfigException
import ir.dorehami.gamesdk.GameMeta
import ir.dorehami.gamesdk.GameModule
import ir.dorehami.gamesdk.GameSession
import ir.dorehami.protocol.ErrCodes
import ir.dorehami.protocol.GameId
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Hokm — the national card game. Entry point registered in the app's and server's `GameRegistry`. */
object HokmModule : GameModule {

    const val ID = "hokm"

    /** Hakem names the trump suit. Payload: `{ "suit": "spades" | "hearts" | "diamonds" | "clubs" }`. */
    const val ACTION_CHOOSE_HOKM = "choose_hokm"

    /** Play one card. Payload: `{ "card": "AS" }` (rank code + suit code). */
    const val ACTION_PLAY_CARD = "play_card"

    /** Two-player: burn cards. Payload: `{ "cards": ["2C","3D","4H"] }` (3 for hakem, 2 for the opponent). */
    const val ACTION_DISCARD = "discard"

    /** Two-player draw: keep the revealed card (the next card is burned blind). No payload. */
    const val ACTION_KEEP_CARD = "keep_card"

    /** Two-player draw: burn the revealed card and take the next one blind. No payload. */
    const val ACTION_PASS_CARD = "pass_card"

    const val PAYLOAD_SUIT = "suit"
    const val PAYLOAD_CARD = "card"
    const val PAYLOAD_CARDS = "cards"

    const val BOT_PRO = "pro"
    const val BOT_EASY = "easy"

    const val DEFAULT_TARGET_POINTS = 7
    const val DEFAULT_KOT_POINTS = 2
    const val DEFAULT_HAKEM_KOT_POINTS = 3

    /** Match lengths offered in the room settings (points needed to win). */
    val ALLOWED_TARGETS = listOf(1, 3, 5, 7)

    override val meta = GameMeta(
        id = GameId(ID),
        titleFa = "حکم",
        titleEn = "Hokm",
        taglineFa = "بازی ورق دو، سه یا چهارنفره؛ حاکم و حکم",
        minPlayers = 2,
        maxPlayers = 4,
        teamCount = 2,
        playersPerTeam = 2,
        iconKey = "hokm",
        estimatedMinutes = 25,
        capabilities = setOf(
            Capability.BLUETOOTH_OFFLINE.name,
            Capability.ONLINE.name,
            Capability.RANDOM_MATCH.name,
            Capability.BOTS.name,
            Capability.SOLO.name,
        ),
        defaultOptions = buildJsonObject {
            put(HokmSession.Options.TARGET_POINTS, DEFAULT_TARGET_POINTS)
            put(HokmSession.Options.KOT_POINTS, DEFAULT_KOT_POINTS)
            put(HokmSession.Options.HAKEM_KOT_POINTS, DEFAULT_HAKEM_KOT_POINTS)
            put(HokmSession.Options.BOT_LEVEL, BOT_PRO)
        },
    )

    override fun validateOptions(options: JsonObject): JsonObject {
        val target = options.intOr(HokmSession.Options.TARGET_POINTS, DEFAULT_TARGET_POINTS)
        if (target !in ALLOWED_TARGETS) {
            throw GameConfigException(ErrCodes.BAD_OPTIONS, "امتیاز پایان بازی باید ۱، ۳، ۵ یا ۷ باشد")
        }
        val kot = options.intOr(HokmSession.Options.KOT_POINTS, DEFAULT_KOT_POINTS)
        if (kot !in 1..3) throw GameConfigException(ErrCodes.BAD_OPTIONS, "امتیاز کُت باید بین ۱ تا ۳ باشد")
        val hakemKot = options.intOr(HokmSession.Options.HAKEM_KOT_POINTS, DEFAULT_HAKEM_KOT_POINTS)
        if (hakemKot !in kot..4) {
            throw GameConfigException(ErrCodes.BAD_OPTIONS, "امتیاز حاکم‌کُت باید حداقل برابر کُت و حداکثر ۴ باشد")
        }
        val level = (options[HokmSession.Options.BOT_LEVEL] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: BOT_PRO
        if (level != BOT_PRO && level != BOT_EASY) throw GameConfigException(ErrCodes.BAD_OPTIONS, "سطح ربات باید pro یا easy باشد")
        return buildJsonObject {
            put(HokmSession.Options.TARGET_POINTS, target)
            put(HokmSession.Options.KOT_POINTS, kot)
            put(HokmSession.Options.HAKEM_KOT_POINTS, hakemKot)
            put(HokmSession.Options.BOT_LEVEL, level)
        }
    }

    override fun create(config: GameConfig): GameSession {
        if (config.players.size !in HokmRules.SUPPORTED_PLAYERS) {
            throw GameConfigException(ErrCodes.BAD_OPTIONS, "حکم با دو، سه یا چهار بازیکن بازی می‌شود")
        }
        return HokmSession(config)
    }

    /** The classic table is four; the random queue fills to that. */
    override fun preferredPartySize(): Int = 4

    override val botAction: ((GameSession, PlayerId) -> GameAction?)? = { session, player ->
        (session as? HokmSession)?.botAction(player)
    }

    private fun JsonObject.intOr(key: String, default: Int): Int {
        val raw = this[key] ?: return default
        return runCatching { raw.jsonPrimitive.content.toInt() }.getOrElse {
            throw GameConfigException(ErrCodes.BAD_OPTIONS, "مقدار «$key» باید عدد باشد")
        }
    }
}
