package ir.dorehami.games.chess

import ir.dorehami.games.chess.ChessTestSupport.BLACK
import ir.dorehami.games.chess.ChessTestSupport.WHITE
import ir.dorehami.games.chess.ChessTestSupport.action
import ir.dorehami.games.chess.ChessTestSupport.engine
import ir.dorehami.games.chess.ChessTestSupport.options
import ir.dorehami.games.chess.ChessTestSupport.play
import ir.dorehami.games.chess.ChessTestSupport.tryMove
import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfigException
import ir.dorehami.gamesdk.Phase
import ir.dorehami.protocol.ErrCodes
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Contract test 1 — rules: illegal moves are rejected with the right code; the game flows to the end. */
class ChessRulesTest {
    private fun rejected(r: ApplyResult, code: String): ApplyResult.Rejected {
        val rej = assertIs<ApplyResult.Rejected>(r)
        assertEquals(code, rej.code, rej.messageFa)
        return rej
    }

    private fun ir.dorehami.gamesdk.GameEngine.chess() = session as ChessSession

    @Test
    fun `scholar's mate in four moves`() {
        val e = engine()
        e.play("e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7")
        val s = e.chess()
        assertEquals(Phase.FINISHED, s.phase)
        assertEquals(EndReason.CHECKMATE, s.reason)
        assertEquals("Qxf7#", s.moves.last().san)
        assertEquals(listOf(WHITE), s.result()!!.winners)
    }

    @Test
    fun `fool's mate - black mates in two`() {
        val e = engine()
        e.play("f2f3", "e7e5", "g2g4", "d8h4")
        assertEquals(EndReason.CHECKMATE, e.chess().reason)
        assertEquals(listOf(BLACK), e.chess().result()!!.winners)
        assertEquals(emptyList(), e.chess().legalActions(WHITE))
    }

    @Test
    fun `moving out of turn is rejected and version does not change`() {
        val e = engine()
        val v = e.session.version
        rejected(e.tryMove(BLACK, "e7e5"), ErrCodes.NOT_YOUR_TURN)
        assertEquals(v, e.session.version)
        e.play("e2e4")
        assertEquals(v + 1, e.session.version)
        rejected(e.tryMove(WHITE, "d2d4"), ErrCodes.NOT_YOUR_TURN)
    }

    @Test
    fun `illegal geometry, empty squares, bad payloads and unknown actions are rejected`() {
        val e = engine()
        rejected(e.tryMove(WHITE, "e2e5"), ErrCodes.ILLEGAL_ACTION)
        rejected(e.tryMove(WHITE, "e4e5"), ErrCodes.ILLEGAL_ACTION)
        rejected(e.tryMove(WHITE, "e7e5"), ErrCodes.ILLEGAL_ACTION)
        rejected(e.tryMove(WHITE, "b1d2"), ErrCodes.ILLEGAL_ACTION)
        rejected(
            e.submit(WHITE, GameAction(ChessModule.ACTION_MOVE, buildJsonObject { put("from", "z9"); put("to", "e4") })),
            ErrCodes.BAD_PAYLOAD,
        )
        rejected(e.submit(WHITE, GameAction(ChessModule.ACTION_MOVE)), ErrCodes.BAD_PAYLOAD)
        rejected(e.submit(WHITE, action("fly")), ErrCodes.ILLEGAL_ACTION)
        assertEquals(0L, e.session.version)
    }

    @Test
    fun `a pinned piece cannot move and the message says why`() {
        val e = engine(options = options("4k3/4r3/8/8/8/8/4B3/4K3 w - - 0 1"))
        val r = rejected(e.tryMove(WHITE, "e2d3"), ErrCodes.ILLEGAL_ACTION)
        assertTrue("کیش" in r.messageFa)
    }

    @Test
    fun `must get out of check`() {
        val e = engine(options = options("4k3/8/8/8/8/8/4r3/R3K3 w Q - 0 1"))
        rejected(e.tryMove(WHITE, "a1a8"), ErrCodes.ILLEGAL_ACTION)
        rejected(e.tryMove(WHITE, "e1c1"), ErrCodes.ILLEGAL_ACTION) // cannot castle out of check
        e.play("e1e2") // captures the checking rook
        assertEquals("r", e.chess().moves.last().captured?.toString())
    }

    @Test
    fun `castling both sides, never through an attacked square`() {
        val e = engine(options = options("4k3/8/8/8/8/8/5r2/R3K2R w KQ - 0 1"))
        rejected(e.tryMove(WHITE, "e1g1"), ErrCodes.ILLEGAL_ACTION) // f1 attacked by the rook on f2
        e.play("e1c1")
        assertTrue(e.chess().fen.startsWith("4k3/8/8/8/8/8/5r2/2KR3R b"))
        assertEquals("O-O-O", e.chess().moves.last().san)
    }

    @Test
    fun `castling rights are lost once the king has moved`() {
        val e = engine(options = options("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"))
        e.play("e1f1", "e8f8", "f1e1", "f8e8")
        rejected(e.tryMove(WHITE, "e1g1"), ErrCodes.ILLEGAL_ACTION)
        assertTrue(" w - " in e.chess().fen)
    }

    @Test
    fun `castling rights are lost when the rook is captured`() {
        val e = engine(options = options("r3k2r/8/8/8/8/8/6b1/R3K2R b KQkq - 0 1"))
        e.play("g2h1")
        assertTrue(" w Qkq " in e.chess().fen)
    }

    @Test
    fun `en passant only on the very next move`() {
        val e = engine()
        e.play("e2e4", "a7a6", "e4e5", "d7d5", "e5d6")
        val fen = e.chess().fen
        assertTrue(fen.startsWith("rnbqkbnr/1pp1pppp/p2P4/8/8/8/PPPP1PPP/RNBQKBNR b"), fen)
        assertEquals("exd6", e.chess().moves.last().san)

        val late = engine()
        late.play("e2e4", "a7a6", "e4e5", "d7d5", "h2h3", "a6a5")
        rejected(late.tryMove(WHITE, "e5d6"), ErrCodes.ILLEGAL_ACTION)
    }

    @Test
    fun `promotion needs a piece choice and allows under-promotion`() {
        val fen = "8/P6k/8/8/8/8/8/K7 w - - 0 1"
        val e = engine(options = options(fen))
        rejected(e.tryMove(WHITE, "a7a8"), ErrCodes.BAD_PAYLOAD)
        rejected(
            e.submit(WHITE, GameAction(ChessModule.ACTION_MOVE, buildJsonObject { put("from", "a7"); put("to", "a8"); put("promotion", "k") })),
            ErrCodes.BAD_PAYLOAD,
        )
        e.play("a7a8n")
        assertTrue(e.chess().fen.startsWith("N7/7k"))

        val q = engine(options = options(fen))
        q.play("a7a8q")
        assertTrue(q.chess().fen.startsWith("Q7/7k"))
        assertEquals("a8=Q", q.chess().moves.last().san)
    }

    @Test
    fun `stalemate is a draw`() {
        val e = engine(options = options("7k/4Q3/6K1/8/8/8/8/8 w - - 0 1"))
        e.play("e7f7")
        assertEquals(EndReason.STALEMATE, e.chess().reason)
        assertEquals(2, e.chess().result()!!.winners.size)
    }

    @Test
    fun `insufficient material ends the game`() {
        val e = engine(options = options("4k3/8/8/8/8/8/3r4/4K3 w - - 0 1"))
        e.play("e1d2")
        assertEquals(EndReason.INSUFFICIENT_MATERIAL, e.chess().reason)

        assertTrue(Board.fromFen("4k3/8/8/8/8/8/8/2B1KB2 w - - 0 1").let { !it.insufficientMaterial() }) // opposite bishops
        assertTrue(Board.fromFen("4k3/8/8/8/8/8/8/4KB2 w - - 0 1").insufficientMaterial())
        assertTrue(Board.fromFen("2b1k3/8/8/8/8/8/8/4KB2 w - - 0 1").insufficientMaterial()) // both light-squared
        assertTrue(!Board.fromFen("4k3/8/8/8/8/8/8/3NKN2 w - - 0 1").insufficientMaterial())
    }

    @Test
    fun `fifty-move rule`() {
        val e = engine(options = options("4k3/8/8/8/8/8/8/R3K3 w - - 99 80"))
        e.play("a1a2")
        assertEquals(EndReason.FIFTY_MOVES, e.chess().reason)
    }

    @Test
    fun `threefold repetition`() {
        val e = engine()
        e.play("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1")
        assertEquals(Phase.PLAYING, e.chess().phase)
        e.play("f6g8")
        assertEquals(EndReason.THREEFOLD_REPETITION, e.chess().reason)
    }

    @Test
    fun `resignation ends the game and nothing is accepted afterwards`() {
        val e = engine()
        e.play("e2e4")
        assertIs<ApplyResult.Ok>(e.submit(WHITE, action(ChessModule.ACTION_RESIGN)))
        assertEquals(EndReason.RESIGNATION, e.chess().reason)
        assertEquals(listOf(BLACK), e.chess().result()!!.winners)
        rejected(e.tryMove(BLACK, "e7e5"), ErrCodes.BAD_STATE)
    }

    @Test
    fun `draw offer can be accepted`() {
        val e = engine()
        assertIs<ApplyResult.Ok>(e.submit(WHITE, action(ChessModule.ACTION_OFFER_DRAW)))
        rejected(e.submit(WHITE, action(ChessModule.ACTION_OFFER_DRAW)), ErrCodes.BAD_STATE)
        rejected(e.submit(WHITE, action(ChessModule.ACTION_ACCEPT_DRAW)), ErrCodes.BAD_STATE) // not your own offer
        assertTrue(ChessModule.ACTION_ACCEPT_DRAW in e.chess().legalActions(BLACK))
        assertIs<ApplyResult.Ok>(e.submit(BLACK, action(ChessModule.ACTION_ACCEPT_DRAW)))
        assertEquals(EndReason.AGREEMENT, e.chess().reason)
    }

    @Test
    fun `declined or ignored draw offers, and no spamming`() {
        val e = engine()
        assertIs<ApplyResult.Ok>(e.submit(WHITE, action(ChessModule.ACTION_OFFER_DRAW)))
        assertIs<ApplyResult.Ok>(e.submit(BLACK, action(ChessModule.ACTION_DECLINE_DRAW)))
        rejected(e.submit(WHITE, action(ChessModule.ACTION_OFFER_DRAW)), ErrCodes.ILLEGAL_ACTION)
        e.play("e2e4")
        assertIs<ApplyResult.Ok>(e.submit(WHITE, action(ChessModule.ACTION_OFFER_DRAW)))
        e.play("e7e5") // moving instead of answering declines
        assertTrue(ChessModule.ACTION_ACCEPT_DRAW !in e.chess().legalActions(BLACK))
        rejected(e.submit(BLACK, action(ChessModule.ACTION_ACCEPT_DRAW)), ErrCodes.BAD_STATE)
        assertEquals(Phase.PLAYING, e.chess().phase)
    }

    @Test
    fun `options are validated with Persian messages`() {
        fun bad(block: () -> Unit) {
            val ex = assertFailsWith<GameConfigException> { block() }
            assertEquals(ErrCodes.BAD_OPTIONS, ex.code)
        }
        bad { ChessModule.validateOptions(options(botLevel = 9)) }
        bad { ChessModule.validateOptions(options(colors = "purple")) }
        bad { ChessModule.validateOptions(options("4k3/8/8/8/8/8/8/3KK3 w - - 0 1")) }
        bad { ChessModule.validateOptions(options("4k2R/8/8/8/8/8/8/4K3 w - - 0 1")) } // side not to move is in check
        bad { ChessModule.validateOptions(options("not a fen")) }
        bad { ChessModule.validateOptions(options("P3k3/8/8/8/8/8/8/4K3 w - - 0 1")) }
        val ok = ChessModule.validateOptions(buildJsonObject {})
        assertEquals(ChessModule.meta.defaultOptions, ok)
    }

    @Test
    fun `random colours come from the seed only`() {
        val seeds = (1L..40L).map { ChessSession(ChessTestSupport.config(it, options(colors = "random"))).whiteSeat }
        assertEquals(seeds, (1L..40L).map { ChessSession(ChessTestSupport.config(it, options(colors = "random"))).whiteSeat })
        assertTrue(0 in seeds && 1 in seeds)
        val s = ChessSession(ChessTestSupport.config(7L, options(colors = "random")))
        assertNotEquals(s.playerOf(Piece.WHITE), s.playerOf(Piece.BLACK))
    }
}
