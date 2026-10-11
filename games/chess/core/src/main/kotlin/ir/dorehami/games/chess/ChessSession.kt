package ir.dorehami.games.chess

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameSession
import ir.dorehami.gamesdk.MatchResult
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.Standing
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.ErrCodes
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Why a game ended. The `wire` names are part of the view contract (the UI translates them). */
enum class EndReason(val wire: String) {
    CHECKMATE("checkmate"),
    RESIGNATION("resign"),
    STALEMATE("stalemate"),
    INSUFFICIENT_MATERIAL("insufficient_material"),
    FIFTY_MOVES("fifty_moves"),
    THREEFOLD_REPETITION("threefold"),
    AGREEMENT("agreement"),
    ;

    val isDraw: Boolean get() = this != CHECKMATE && this != RESIGNATION
}

/** One played move, as kept in the history. */
data class PlayedMove(val uci: String, val san: String, val captured: Char?)

/**
 * The rules of one chess match. Pure function of `(config, action log)`: no clock, no randomness
 * except the seeded colour draw when the room asks for `colors = random`.
 *
 * Draw rules are applied **automatically** (no claim needed, friendlier on a phone): stalemate,
 * insufficient material, 50 moves without capture or pawn move, threefold repetition.
 */
class ChessSession(private val config: GameConfig) : GameSession {

    private val players: List<PlayerId> = config.players

    /** Seat (0/1) that plays white. */
    val whiteSeat: Int

    private val board: Board
    private val startFen: String
    private val history = ArrayList<PlayedMove>()
    private val repetitions = HashMap<String, Int>()

    /** Colour that offered a draw which is still waiting for an answer, `0` if none. */
    private var drawOfferBy = 0

    /** Ply count at which each colour last offered a draw (one offer per own move). */
    private var lastOfferPlyWhite = -1
    private var lastOfferPlyBlack = -1

    private var endReason: EndReason? = null

    /** `+1` white won, `-1` black won, `0` draw — meaningful once [endReason] is set. */
    private var winnerColor = 0

    private val botLevel: Int

    override var phase: Phase = Phase.PLAYING
        private set

    override var version: Long = 0L
        private set

    init {
        require(players.size == 2) { "chess needs exactly two players" }
        val opts = config.options
        val colors = (opts[ChessModule.Options.COLORS] as? JsonPrimitive)?.content ?: ChessModule.Options.COLORS_FIRST_WHITE
        whiteSeat = if (colors == ChessModule.Options.COLORS_RANDOM) config.rng.nextInt(2) else 0
        botLevel = (opts[ChessModule.Options.BOT_LEVEL] as? JsonPrimitive)?.intOrNull ?: 2
        startFen = (opts[ChessModule.Options.START_FEN] as? JsonPrimitive)?.content ?: Board.START_FEN
        board = Board.fromFen(startFen)
        repetitions[board.repetitionKey()] = 1
        // A start position can already be over (puzzle "find nothing": mate/stalemate given).
        evaluateEnd()
    }

    // ------------------------------------------------------------------ queries

    fun playerOf(color: Int): PlayerId = players[if (color == Piece.WHITE) whiteSeat else 1 - whiteSeat]

    fun colorOf(player: PlayerId): Int {
        val seat = players.indexOf(player)
        if (seat < 0) return 0
        return if (seat == whiteSeat) Piece.WHITE else Piece.BLACK
    }

    /** Side to move ([Piece.WHITE]/[Piece.BLACK]). */
    val sideToMove: Int get() = board.side

    val fen: String get() = board.toFen()

    val moves: List<PlayedMove> get() = history

    val reason: EndReason? get() = endReason

    /** Copy of the current position (for bots, analysis or the UI; the session keeps its own). */
    fun boardCopy(): Board = board.copy()

    /** Legal moves of [player] in UCI form; empty when it is not their turn or the game is over. */
    fun legalMovesFor(player: PlayerId): List<String> {
        if (phase != Phase.PLAYING || colorOf(player) != board.side) return emptyList()
        val legal = board.legalMoves()
        return List(legal.size) { Move.uci(legal[it]) }
    }

    override fun legalActions(player: PlayerId): List<String> {
        if (phase != Phase.PLAYING) return emptyList()
        val color = colorOf(player)
        if (color == 0) return emptyList()
        val out = ArrayList<String>(4)
        if (color == board.side) out += ChessModule.ACTION_MOVE
        out += ChessModule.ACTION_RESIGN
        if (canOfferDraw(color)) out += ChessModule.ACTION_OFFER_DRAW
        if (drawOfferBy == -color) {
            out += ChessModule.ACTION_ACCEPT_DRAW
            out += ChessModule.ACTION_DECLINE_DRAW
        }
        return out
    }

    private fun canOfferDraw(color: Int): Boolean {
        if (drawOfferBy != 0) return false
        val last = if (color == Piece.WHITE) lastOfferPlyWhite else lastOfferPlyBlack
        return last != history.size
    }

    // ------------------------------------------------------------------ apply

    override fun apply(player: PlayerId, action: GameAction): ApplyResult {
        val color = colorOf(player)
        if (color == 0) return ApplyResult.Rejected(ErrCodes.UNKNOWN_PLAYER, "این بازیکن در این بازی نیست")
        if (phase != Phase.PLAYING) return ApplyResult.Rejected(ErrCodes.BAD_STATE, "بازی تمام شده است")
        return when (action.type) {
            ChessModule.ACTION_MOVE -> applyMove(color, action.payload)
            ChessModule.ACTION_RESIGN -> {
                finish(EndReason.RESIGNATION, -color)
                accepted(
                    Emission.Public("resigned", view { put("player", player.value) }),
                    Emission.Feed("chess_resigned", view { put("player", player.value) }, "end"),
                    Emission.Sound(SOUND_GAME_END),
                )
            }
            ChessModule.ACTION_OFFER_DRAW -> {
                if (drawOfferBy != 0) {
                    return ApplyResult.Rejected(ErrCodes.BAD_STATE, "یک پیشنهاد تساوی در انتظار پاسخ است")
                }
                if (!canOfferDraw(color)) {
                    return ApplyResult.Rejected(ErrCodes.ILLEGAL_ACTION, "برای پیشنهاد دوباره اول یک حرکت انجام بده")
                }
                drawOfferBy = color
                if (color == Piece.WHITE) lastOfferPlyWhite = history.size else lastOfferPlyBlack = history.size
                accepted(
                    Emission.Public("draw_offered", view { put("player", player.value) }),
                    Emission.Feed("chess_draw_offered", view { put("player", player.value) }),
                    Emission.Sound(SOUND_NOTIFY),
                )
            }
            ChessModule.ACTION_ACCEPT_DRAW -> {
                if (drawOfferBy != -color) {
                    return ApplyResult.Rejected(ErrCodes.BAD_STATE, "پیشنهاد تساوی‌ای برای پذیرفتن وجود ندارد")
                }
                finish(EndReason.AGREEMENT, 0)
                accepted(
                    Emission.Public("draw_agreed", view { put("player", player.value) }),
                    Emission.Feed("chess_draw_agreed", view {}, "end"),
                    Emission.Sound(SOUND_GAME_END),
                )
            }
            ChessModule.ACTION_DECLINE_DRAW -> {
                if (drawOfferBy != -color) {
                    return ApplyResult.Rejected(ErrCodes.BAD_STATE, "پیشنهاد تساوی‌ای برای رد کردن وجود ندارد")
                }
                drawOfferBy = 0
                accepted(
                    Emission.Public("draw_declined", view { put("player", player.value) }),
                    Emission.Feed("chess_draw_declined", view { put("player", player.value) }),
                )
            }
            else -> ApplyResult.Rejected(ErrCodes.ILLEGAL_ACTION, "حرکت ناشناخته: ${action.type}")
        }
    }

    private fun applyMove(color: Int, payload: JsonObject): ApplyResult {
        if (color != board.side) return ApplyResult.Rejected(ErrCodes.NOT_YOUR_TURN, "نوبت شما نیست")
        val from = Squares.parse(payload.string(ChessModule.Payload.FROM) ?: "")
        val to = Squares.parse(payload.string(ChessModule.Payload.TO) ?: "")
        if (from < 0 || to < 0) {
            return ApplyResult.Rejected(ErrCodes.BAD_PAYLOAD, "خانهٔ مبدأ یا مقصد نامعتبر است")
        }
        val promoText = payload.string(ChessModule.Payload.PROMOTION)
        val promo = if (promoText.isNullOrEmpty()) 0 else Piece.promoFromLetter(promoText[0])
        if (promoText != null && promoText.isNotEmpty() && (promo == 0 || promoText.length != 1)) {
            return ApplyResult.Rejected(ErrCodes.BAD_PAYLOAD, "مهرهٔ ارتقا باید یکی از q، r، b یا n باشد")
        }

        val legal = board.legalMoves()
        var chosen = 0
        var needsPromotion = false
        for (i in 0 until legal.size) {
            val m = legal[i]
            if (Move.from(m) != from || Move.to(m) != to) continue
            if (Move.promotion(m) != 0 && promo == 0) {
                needsPromotion = true
                continue
            }
            if (Move.promotion(m) == promo) {
                chosen = m
                break
            }
        }
        if (chosen == 0) {
            if (needsPromotion) {
                return ApplyResult.Rejected(ErrCodes.BAD_PAYLOAD, "مهرهٔ ارتقای سرباز را انتخاب کن")
            }
            val piece = board.squares[from]
            if (piece == 0 || Piece.color(piece) != color) {
                return ApplyResult.Rejected(ErrCodes.ILLEGAL_ACTION, "در این خانه مهره‌ای از شما نیست")
            }
            if (leavesKingInCheck(from, to, promo)) {
                return ApplyResult.Rejected(ErrCodes.ILLEGAL_ACTION, "این حرکت شاه شما را در کیش می‌گذارد")
            }
            return ApplyResult.Rejected(ErrCodes.ILLEGAL_ACTION, "این حرکت مجاز نیست")
        }

        val san = board.san(chosen, legal)
        board.makeMove(chosen)
        val captured = board.lastCaptured()
        board.commit()
        history += PlayedMove(Move.uci(chosen), san, if (captured != 0) Piece.fenChar(captured) else null)
        // A move by the side the draw was offered to declines it; the offerer's own move keeps it open.
        if (drawOfferBy == -color) drawOfferBy = 0
        val key = board.repetitionKey()
        repetitions[key] = (repetitions[key] ?: 0) + 1

        evaluateEnd()

        val emissions = ArrayList<Emission>(4)
        emissions += Emission.Public(
            "moved",
            view {
                put("uci", Move.uci(chosen))
                put("san", san)
                put("fen", board.toFen())
                put("by", playerOf(color).value)
            },
        )
        val sound = when {
            phase == Phase.FINISHED -> SOUND_GAME_END
            board.inCheck() -> SOUND_CHECK
            Move.isCastle(chosen) -> SOUND_CASTLE
            Move.promotion(chosen) != 0 -> SOUND_PROMOTE
            captured != 0 -> SOUND_CAPTURE
            else -> SOUND_MOVE
        }
        endReason?.let { r ->
            emissions += Emission.Feed(
                "chess_${r.wire}",
                view { if (winnerColor != 0) put("winner", playerOf(winnerColor).value) },
                "end",
            )
        }
        emissions += Emission.Sound(sound)
        return accepted(emissions)
    }

    /** True when a pseudo-legal [from]→[to] exists but is illegal only because of the own king. */
    private fun leavesKingInCheck(from: Int, to: Int, promo: Int): Boolean {
        val pseudo = MoveList()
        board.generatePseudo(pseudo)
        for (i in 0 until pseudo.size) {
            val m = pseudo[i]
            if (Move.from(m) == from && Move.to(m) == to && (promo == 0 || Move.promotion(m) == promo)) return true
        }
        return false
    }

    private fun evaluateEnd() {
        if (phase == Phase.FINISHED) return
        val legal = board.legalMoves()
        when {
            legal.isEmpty() && board.inCheck() -> finish(EndReason.CHECKMATE, -board.side)
            legal.isEmpty() -> finish(EndReason.STALEMATE, 0)
            board.insufficientMaterial() -> finish(EndReason.INSUFFICIENT_MATERIAL, 0)
            (repetitions[board.repetitionKey()] ?: 0) >= 3 -> finish(EndReason.THREEFOLD_REPETITION, 0)
            board.halfmoveClock >= 100 -> finish(EndReason.FIFTY_MOVES, 0)
        }
    }

    private fun finish(reason: EndReason, winner: Int) {
        endReason = reason
        winnerColor = winner
        drawOfferBy = 0
        phase = Phase.FINISHED
    }

    private fun accepted(vararg emissions: Emission): ApplyResult = accepted(emissions.toList())

    private fun accepted(emissions: List<Emission>): ApplyResult {
        version++
        return ApplyResult.Ok(emissions)
    }

    // ------------------------------------------------------------------ result

    /** Win = 2 points, draw = 1 each (half points × 2, so scores stay integers). */
    override fun result(): MatchResult? {
        if (phase != Phase.FINISHED) return null
        val white = playerOf(Piece.WHITE)
        val black = playerOf(Piece.BLACK)
        return when (winnerColor) {
            Piece.WHITE -> MatchResult(listOf(Standing(listOf(white), 1, 2), Standing(listOf(black), 2, 0)))
            Piece.BLACK -> MatchResult(listOf(Standing(listOf(black), 1, 2), Standing(listOf(white), 2, 0)))
            else -> MatchResult(listOf(Standing(listOf(white), 1, 1), Standing(listOf(black), 1, 1)))
        }
    }

    // ------------------------------------------------------------------ views

    /** Everything is public in chess; the private view only adds "your colour / your moves". */
    override fun publicView(): JsonObject = view { putPublic() }

    override fun privateView(player: PlayerId): JsonObject = view {
        putPublic()
        val color = colorOf(player)
        putJsonObject(KEY_YOU) {
            put("player", player.value)
            put("color", colorLetter(color))
            put("myTurn", phase == Phase.PLAYING && color == board.side)
            putJsonArray("legalMoves") { legalMovesFor(player).forEach { add(JsonPrimitive(it)) } }
            putJsonArray("actions") { legalActions(player).forEach { add(JsonPrimitive(it)) } }
            put("drawOfferedToMe", drawOfferBy != 0 && drawOfferBy == -color)
        }
    }

    override fun snapshot(): JsonObject = view {
        putPublic()
        put("version", version)
        put("startFen", startFen)
        put("lastOfferPlyWhite", lastOfferPlyWhite)
        put("lastOfferPlyBlack", lastOfferPlyBlack)
        putJsonObject("repetitions") {
            repetitions.keys.sorted().forEach { put(it, repetitions.getValue(it)) }
        }
    }

    private fun JsonObjectBuilder.putPublic() {
        put("fen", board.toFen())
        put("turn", colorLetter(board.side))
        put("white", playerOf(Piece.WHITE).value)
        put("black", playerOf(Piece.BLACK).value)
        put("status", if (phase == Phase.FINISHED) "finished" else "playing")
        put("check", board.inCheck())
        put("ply", history.size)
        put("botLevel", botLevel)
        history.lastOrNull()?.let { put("lastMove", it.uci) }
        putJsonArray("san") { history.forEach { add(JsonPrimitive(it.san)) } }
        putJsonArray("uci") { history.forEach { add(JsonPrimitive(it.uci)) } }
        // Pieces each side has taken, in capture order (FEN letters of the captured pieces).
        val byWhite = StringBuilder()
        val byBlack = StringBuilder()
        history.forEachIndexed { i, mv ->
            val c = mv.captured ?: return@forEachIndexed
            val whiteMoved = (i % 2 == 0) == startsWithWhite()
            if (whiteMoved) byWhite.append(c) else byBlack.append(c)
        }
        putJsonObject("captured") {
            put("w", byWhite.toString())
            put("b", byBlack.toString())
        }
        if (drawOfferBy != 0) put("drawOfferBy", playerOf(drawOfferBy).value)
        endReason?.let { r ->
            putJsonObject("outcome") {
                put("reason", r.wire)
                put("winner", if (winnerColor == 0) null else playerOf(winnerColor).value)
            }
        }
    }

    private fun startsWithWhite(): Boolean = startFen.trim().split(Regex("\\s+")).getOrNull(1) != "b"

    // ------------------------------------------------------------------ bot

    /**
     * Deterministic bot policy (contract question 6). Answers a pending draw offer first, then
     * moves when it is its turn. Never consumes the Rng.
     */
    fun botAction(player: PlayerId): GameAction? {
        if (phase != Phase.PLAYING) return null
        val color = colorOf(player)
        if (color == 0) return null
        if (drawOfferBy == -color) {
            // Accept only when clearly worse; the search is from the side to move, so flip if needed.
            val s = ChessBot.score(board, 2) * (if (board.side == color) 1 else -1)
            return GameAction(if (s <= -150) ChessModule.ACTION_ACCEPT_DRAW else ChessModule.ACTION_DECLINE_DRAW)
        }
        if (color != board.side) return null
        val m = ChessBot.chooseMove(board, botLevel, repetitions.keys)
        if (m == 0) return null
        return moveAction(m)
    }

    companion object {
        /** Key of the private block; structurally absent from [publicView]. */
        const val KEY_YOU = "you"

        const val SOUND_MOVE = "move"
        const val SOUND_CAPTURE = "capture"
        const val SOUND_CHECK = "check"
        const val SOUND_CASTLE = "castle"
        const val SOUND_PROMOTE = "promote"
        const val SOUND_GAME_END = "game_end"
        const val SOUND_NOTIFY = "notify"

        fun colorLetter(color: Int): String = if (color == Piece.WHITE) "w" else "b"

        /** Builds the `move` action for a packed move (used by bots, tests and the UI). */
        fun moveAction(m: Int): GameAction = GameAction(
            ChessModule.ACTION_MOVE,
            buildJsonObject {
                put(ChessModule.Payload.FROM, Squares.name(Move.from(m)))
                put(ChessModule.Payload.TO, Squares.name(Move.to(m)))
                val p = Move.promotion(m)
                if (p != 0) put(ChessModule.Payload.PROMOTION, "${"??nbrq"[p]}")
            },
        )

        /** `"e7e8q"` → `move` action. Returns `null` for malformed text. */
        fun moveAction(uci: String): GameAction? {
            if (uci.length !in 4..5) return null
            val from = uci.substring(0, 2)
            val to = uci.substring(2, 4)
            if (Squares.parse(from) < 0 || Squares.parse(to) < 0) return null
            return GameAction(
                ChessModule.ACTION_MOVE,
                buildJsonObject {
                    put(ChessModule.Payload.FROM, from)
                    put(ChessModule.Payload.TO, to)
                    if (uci.length == 5) put(ChessModule.Payload.PROMOTION, uci.substring(4))
                },
            )
        }
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
