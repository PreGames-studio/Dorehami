package ir.dorehami.games.chess

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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * Chess (شطرنج) — two players, classical FIDE rules (see `games/chess/README.md`).
 *
 * Seven questions of docs/GAME_CONTRACT.md: [meta] (1), [ChessSession.legalActions] (2),
 * [ChessSession.publicView]/[ChessSession.privateView] (3), [ChessSession.apply] (4),
 * [ChessSession.phase]/[ChessSession.result] (5), [botAction] (6), [ChessSession.snapshot] (7).
 */
object ChessModule : GameModule {

    // Action ids (fixed, snake_case — part of the wire contract).
    const val ACTION_MOVE = "move"
    const val ACTION_RESIGN = "resign"
    const val ACTION_OFFER_DRAW = "offer_draw"
    const val ACTION_ACCEPT_DRAW = "accept_draw"
    const val ACTION_DECLINE_DRAW = "decline_draw"

    /** Payload keys of [ACTION_MOVE]: `{"from":"e2","to":"e4","promotion":"q"}`. */
    object Payload {
        const val FROM = "from"
        const val TO = "to"
        const val PROMOTION = "promotion"
    }

    /** Room option keys. */
    object Options {
        /** `"first_white"` (seat 0 plays white) or `"random"` (decided by the seeded Rng). */
        const val COLORS = "colors"

        /** Bot strength 1..4. */
        const val BOT_LEVEL = "botLevel"

        /** Optional start position (FEN) — puzzles, lessons and tests. Standard start if absent. */
        const val START_FEN = "startFen"

        /** Per-move time for the session layer's turn timer; `0` = no limit. */
        const val TURN_SECONDS = "turnSeconds"

        const val COLORS_FIRST_WHITE = "first_white"
        const val COLORS_RANDOM = "random"
    }

    override val meta: GameMeta = GameMeta(
        id = GameId("chess"),
        titleFa = "شطرنج",
        titleEn = "Chess",
        taglineFa = "بازی فکری دونفره — با دوست یا ربات",
        minPlayers = 2,
        maxPlayers = 2,
        iconKey = "chess",
        estimatedMinutes = 20,
        capabilities = setOf(
            Capability.BLUETOOTH_OFFLINE.name,
            Capability.ONLINE.name,
            Capability.RANDOM_MATCH.name,
            Capability.BOTS.name,
            Capability.SOLO.name,
        ),
        defaultOptions = buildJsonObject {
            put(Options.COLORS, Options.COLORS_FIRST_WHITE)
            put(Options.BOT_LEVEL, 2)
            put(Options.TURN_SECONDS, 0)
        },
    )

    override fun validateOptions(options: JsonObject): JsonObject {
        val merged = LinkedHashMap(meta.defaultOptions)
        merged.putAll(options)

        val colors = (merged[Options.COLORS] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (colors != Options.COLORS_FIRST_WHITE && colors != Options.COLORS_RANDOM) {
            throw GameConfigException(ErrCodes.BAD_OPTIONS, "رنگ مهره‌ها باید «first_white» یا «random» باشد")
        }
        val level = (merged[Options.BOT_LEVEL] as? JsonPrimitive)?.intOrNull
        if (level == null || level !in ChessBot.MIN_LEVEL..ChessBot.MAX_LEVEL) {
            throw GameConfigException(ErrCodes.BAD_OPTIONS, "سطح ربات باید بین ۱ تا ۴ باشد")
        }
        val turnSeconds = (merged[Options.TURN_SECONDS] as? JsonPrimitive)?.intOrNull
        if (turnSeconds == null || turnSeconds < 0 || turnSeconds > 3600) {
            throw GameConfigException(ErrCodes.BAD_OPTIONS, "زمان هر حرکت باید بین ۰ تا ۳۶۰۰ ثانیه باشد")
        }
        val fenElement = merged[Options.START_FEN]
        if (fenElement != null) {
            val fen = (fenElement as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: throw GameConfigException(ErrCodes.BAD_OPTIONS, "وضعیت شروع (FEN) باید متن باشد")
            validateStartFen(fen)
        }
        return JsonObject(merged)
    }

    private fun validateStartFen(fen: String) {
        val board = try {
            Board.fromFen(fen)
        } catch (e: IllegalArgumentException) {
            throw GameConfigException(ErrCodes.BAD_OPTIONS, "وضعیت شروع نامعتبر است: ${e.message}")
        }
        val whiteKings = board.squares.count { it == Piece.KING }
        val blackKings = board.squares.count { it == -Piece.KING }
        if (whiteKings != 1 || blackKings != 1) {
            throw GameConfigException(ErrCodes.BAD_OPTIONS, "هر طرف باید دقیقاً یک شاه داشته باشد")
        }
        for (sq in 0 until 8) {
            if (Piece.type(board.squares[sq]) == Piece.PAWN || Piece.type(board.squares[56 + sq]) == Piece.PAWN) {
                throw GameConfigException(ErrCodes.BAD_OPTIONS, "سرباز نمی‌تواند در ردیف اول یا آخر باشد")
            }
        }
        if (board.inCheck(-board.side)) {
            throw GameConfigException(ErrCodes.BAD_OPTIONS, "شاهِ طرفی که نوبتش نیست در کیش است")
        }
    }

    override fun create(config: GameConfig): GameSession = ChessSession(config)

    override fun preferredPartySize(): Int = 2

    override val botAction: ((GameSession, PlayerId) -> GameAction?)? = { session, player ->
        (session as? ChessSession)?.botAction(player)
    }
}
