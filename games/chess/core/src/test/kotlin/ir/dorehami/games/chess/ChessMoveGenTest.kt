package ir.dorehami.games.chess

import ir.dorehami.games.chess.ChessTestSupport.perft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Move generator correctness against well-known perft numbers (chessprogramming.org/Perft_Results). */
class ChessMoveGenTest {
    @Test
    fun `perft from the start position`() {
        val b = Board.start()
        assertEquals(20L, perft(b, 1))
        assertEquals(400L, perft(b, 2))
        assertEquals(8_902L, perft(b, 3))
        assertEquals(197_281L, perft(b, 4))
        assertEquals(Board.START_FEN, b.toFen(), "make/unmake must restore the position exactly")
    }

    @Test
    fun `perft kiwipete - castling, en passant, promotions, pins`() {
        val b = Board.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1")
        assertEquals(48L, perft(b, 1))
        assertEquals(2_039L, perft(b, 2))
        assertEquals(97_862L, perft(b, 3))
    }

    @Test
    fun `perft position 3 - en passant discovered checks`() {
        val b = Board.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14L, perft(b, 1))
        assertEquals(191L, perft(b, 2))
        assertEquals(2_812L, perft(b, 3))
        assertEquals(43_238L, perft(b, 4))
    }

    @Test
    fun `perft position 4 - promotions and castling under attack`() {
        val b = Board.fromFen("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1")
        assertEquals(6L, perft(b, 1))
        assertEquals(264L, perft(b, 2))
        assertEquals(9_467L, perft(b, 3))
    }

    @Test
    fun `perft position 5`() {
        val b = Board.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(44L, perft(b, 1))
        assertEquals(1_486L, perft(b, 2))
        assertEquals(62_379L, perft(b, 3))
    }

    @Test
    fun `fen round trip`() {
        listOf(
            Board.START_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "rnbqkbnr/pp1ppppp/8/2p5/4P3/8/PPPP1PPP/RNBQKBNR w KQkq c6 0 2",
            "8/8/8/8/8/8/6k1/4K2R b K - 12 40",
        ).forEach { assertEquals(it, Board.fromFen(it).toFen()) }
    }

    @Test
    fun `bad fen is rejected with a message`() {
        assertFailsWith<IllegalArgumentException> { Board.fromFen("rnbqkbnr/pppppppp/8/8 w - -") }
        assertFailsWith<IllegalArgumentException> { Board.fromFen("rnbqkbnr/pppppppp/9/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") }
        assertFailsWith<IllegalArgumentException> { Board.fromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1") }
    }

    @Test
    fun `san covers castling, disambiguation, promotion and mate`() {
        fun san(fen: String, uci: String): String {
            val b = Board.fromFen(fen)
            val legal = b.legalMoves()
            val m = legal.toList().first { Move.uci(it) == uci }
            return b.san(m, legal)
        }
        assertEquals("Nf3", san(Board.START_FEN, "g1f3"))
        assertEquals("e4", san(Board.START_FEN, "e2e4"))
        assertEquals("O-O", san("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1g1"))
        assertEquals("O-O-O", san("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1c1"))
        // Two knights can reach d2: file disambiguation.
        assertEquals("Nbd2", san("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1", "b1d2"))
        // Two rooks on the same file: rank disambiguation.
        assertEquals("R1a3", san("4k3/8/8/8/R7/8/8/R3K3 w - - 0 1", "a1a3"))
        assertEquals("exd6", san("4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1", "e5d6"))
        assertEquals("e8=Q", san("8/4P3/8/8/8/8/k7/4K3 w - - 0 1", "e7e8q"))
        assertEquals("e8=Q+", san("8/4P3/8/8/k7/8/8/4K3 w - - 0 1", "e7e8q"))
        assertEquals("Qxf7#", san("r1bqkbnr/pppp1ppp/2n5/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 1", "h5f7"))
        assertEquals("a8=N", san("7k/P7/8/8/8/8/8/4K3 w - - 0 1", "a7a8n"))
    }
}
