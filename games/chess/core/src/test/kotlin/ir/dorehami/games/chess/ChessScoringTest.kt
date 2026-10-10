package ir.dorehami.games.chess

import ir.dorehami.games.chess.ChessTestSupport.BLACK
import ir.dorehami.games.chess.ChessTestSupport.WHITE
import ir.dorehami.games.chess.ChessTestSupport.action
import ir.dorehami.games.chess.ChessTestSupport.engine
import ir.dorehami.games.chess.ChessTestSupport.options
import ir.dorehami.games.chess.ChessTestSupport.play
import ir.dorehami.games.chess.ChessTestSupport.playOutWithBots
import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.MatchResult
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.Standing
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contract test 4 — scoring and endings: result shape for wins/draws, bots finish games. */
class ChessScoringTest {
    @Test
    fun `win, loss and draw standings`() {
        val win = engine().apply { play("f2f3", "e7e5", "g2g4", "d8h4") }
        assertEquals(
            MatchResult(listOf(Standing(listOf(BLACK), 1, 2), Standing(listOf(WHITE), 2, 0))),
            win.session.result(),
        )
        val draw = engine().apply {
            submit(WHITE, action(ChessModule.ACTION_OFFER_DRAW))
            submit(BLACK, action(ChessModule.ACTION_ACCEPT_DRAW))
        }
        assertEquals(
            MatchResult(listOf(Standing(listOf(WHITE), 1, 1), Standing(listOf(BLACK), 1, 1))),
            draw.session.result(),
        )
        assertNull(engine().session.result(), "no result while playing")
    }

    @Test
    fun `outcome is in the public view`() {
        val e = engine().apply { play("f2f3", "e7e5", "g2g4", "d8h4") }
        val outcome = e.session.publicView().getValue("outcome").jsonObject
        assertEquals("checkmate", outcome.getValue("reason").jsonPrimitive.content)
        assertEquals(BLACK.value, outcome.getValue("winner").jsonPrimitive.content)
        assertEquals("finished", e.session.publicView().getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `a full bot game ends by a real rule`() {
        for (level in 1..2) {
            val e = engine(seed = level.toLong(), options = options(botLevel = level))
            e.playOutWithBots()
            val s = e.session as ChessSession
            assertEquals(Phase.FINISHED, s.phase, "level $level game did not finish: ${s.fen}")
            assertTrue(s.reason != EndReason.RESIGNATION && s.reason != EndReason.AGREEMENT)
        }
    }

    @Test
    fun `every level finds mate in one`() {
        for (level in ChessBot.MIN_LEVEL..ChessBot.MAX_LEVEL) {
            val b = Board.fromFen("6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1")
            assertEquals("a1a8", Move.uci(ChessBot.chooseMove(b, level)), "level $level")
        }
    }

    @Test
    fun `stronger levels grab a free queen, level 3 sees a mate in two`() {
        val free = Board.fromFen("4k3/8/8/3q4/8/8/8/3RK3 w - - 0 1")
        for (level in 1..4) assertEquals("d1d5", Move.uci(ChessBot.chooseMove(free, level)), "level $level")

        // 1. Qg7+?? no: 1. Rh8+ Kxh8?? — classic back-rank: 1. Qd8+ Rxd8 2. Rxd8#.
        val m2 = Board.fromFen("3r2k1/5ppp/8/8/8/8/5PPP/3QR1K1 w - - 0 1")
        val bot = ChessBot.chooseMove(m2, 3)
        assertEquals("d1d8", Move.uci(bot))
    }

    @Test
    fun `level 3 beats level 1`() {
        var strongScore = 0
        for (strongIsWhite in listOf(true, false)) {
            val b = Board.start()
            var plies = 0
            val reps = HashMap<String, Int>()
            while (plies < 300) {
                val legal = b.legalMoves()
                if (legal.isEmpty() || b.insufficientMaterial() || b.halfmoveClock >= 100) break
                val key = b.repetitionKey()
                reps[key] = (reps[key] ?: 0) + 1
                if (reps.getValue(key) >= 3) break
                val whiteToMove = b.side == Piece.WHITE
                val level = if (whiteToMove == strongIsWhite) 3 else 1
                b.makeMove(ChessBot.chooseMove(b, level))
                b.commit()
                plies++
            }
            val mated = b.legalMoves().isEmpty() && b.inCheck()
            val strongWon = mated && (b.side == Piece.WHITE) != strongIsWhite
            if (strongWon) strongScore += 2 else if (!mated) strongScore += 1
        }
        assertTrue(strongScore >= 3, "level 3 should beat level 1 (score $strongScore / 4)")
    }

    @Test
    fun `bot declines a draw when fine and accepts when lost`() {
        val equal = engine(options = options(botLevel = 2))
        equal.submit(WHITE, action(ChessModule.ACTION_OFFER_DRAW))
        assertEquals(ChessModule.ACTION_DECLINE_DRAW, (equal.session as ChessSession).botAction(BLACK)!!.type)

        // Black (bot) has a lone king against a queen: accept.
        val lost = engine(options = options("4k3/8/8/8/8/8/8/3QK3 w - - 0 1", botLevel = 2))
        lost.submit(WHITE, action(ChessModule.ACTION_OFFER_DRAW))
        val a = (lost.session as ChessSession).botAction(BLACK)!!
        assertEquals(ChessModule.ACTION_ACCEPT_DRAW, a.type)
        assertIs<ApplyResult.Ok>(lost.submit(BLACK, a))
        assertEquals(EndReason.AGREEMENT, (lost.session as ChessSession).reason)
    }

    @Test
    fun `bot mates with king and queen against a lone king`() {
        val e = engine(options = options("8/8/8/4k3/8/8/8/3QK3 w - - 0 1", botLevel = 3))
        e.playOutWithBots(maxActions = 140)
        val s = e.session as ChessSession
        assertEquals(EndReason.CHECKMATE, s.reason, "KQ vs K not converted: ${s.fen}")
        assertEquals(listOf(WHITE), s.result()!!.winners)
    }
}
