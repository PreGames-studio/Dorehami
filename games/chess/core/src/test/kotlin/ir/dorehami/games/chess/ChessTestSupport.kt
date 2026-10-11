package ir.dorehami.games.chess

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertIs

/** Shared helpers for the chess contract tests. No network, no sleeps, no mocks. */
internal object ChessTestSupport {
    val WHITE = PlayerId("p_white")
    val BLACK = PlayerId("p_black")
    val players = listOf(WHITE, BLACK)

    fun options(startFen: String? = null, botLevel: Int = 2, colors: String = "first_white"): JsonObject =
        buildJsonObject {
            put(ChessModule.Options.COLORS, colors)
            put(ChessModule.Options.BOT_LEVEL, botLevel)
            if (startFen != null) put(ChessModule.Options.START_FEN, startFen)
        }

    fun config(seed: Long = 1L, options: JsonObject = options()) = GameConfig(
        seed = seed,
        players = players,
        options = options,
        clock = ManualClock(),
        rng = SplitMixRng(seed),
    )

    fun engine(seed: Long = 1L, options: JsonObject = options()): GameEngine =
        GameEngine.start(ChessModule, config(seed, options))

    fun ChessSession.toMove(): PlayerId = playerOf(sideToMove)

    /** Plays a list of UCI moves, alternating sides, asserting every one is accepted. */
    fun GameEngine.play(vararg uci: String) {
        val s = session as ChessSession
        for (u in uci) {
            val r = submit(s.toMove(), ChessSession.moveAction(u)!!)
            assertIs<ApplyResult.Ok>(r, "move $u should be accepted (fen ${s.fen}) but was $r")
        }
    }

    fun GameEngine.tryMove(player: PlayerId, uci: String): ApplyResult =
        submit(player, ChessSession.moveAction(uci)!!)

    /** Bot vs bot until the end (with a generous ply cap); returns the number of actions. */
    fun GameEngine.playOutWithBots(maxActions: Int = 1200): Int {
        var n = 0
        while (session.phase == Phase.PLAYING && n < maxActions) {
            val s = session as ChessSession
            val r = submitBot(s.toMove()) ?: error("bot had no move in ${s.fen}")
            assertIs<ApplyResult.Ok>(r, "bot move rejected: $r")
            n++
        }
        return n
    }

    fun perft(board: Board, depth: Int): Long {
        if (depth == 0) return 1
        val moves = board.legalMoves()
        if (depth == 1) return moves.size.toLong()
        var total = 0L
        for (i in 0 until moves.size) {
            board.makeMove(moves[i])
            total += perft(board, depth - 1)
            board.unmakeMove()
        }
        return total
    }

    fun action(type: String) = GameAction(type)
}
