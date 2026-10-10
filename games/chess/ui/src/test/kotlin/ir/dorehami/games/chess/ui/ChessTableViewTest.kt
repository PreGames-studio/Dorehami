package ir.dorehami.games.chess.ui

import ir.dorehami.games.chess.Board
import ir.dorehami.games.chess.EndReason
import ir.dorehami.games.chess.ui.gl.BoardScene
import ir.dorehami.games.chess.ui.gl.MoveAnim
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The view → scene bridge of the chess table: FEN parsing, what a tap means and the Persian
 * wording. This is the only thing the screen works out by itself — the rules stay in
 * `:games:chess:core`.
 */
class ChessTableViewTest {

    @Test
    fun `fen fields map to the rules' square numbering`() {
        val start = FenBoard.squares(Board.START_FEN)
        assertEquals(6, start[FenBoard.square("e1")], "white king on e1")
        assertEquals(1, start[FenBoard.square("e2")], "white pawn on e2")
        assertEquals(-1, start[FenBoard.square("e7")], "black pawn on e7")
        assertEquals(0, start[FenBoard.square("e4")], "e4 is empty")
        assertEquals(-6, start[FenBoard.square("e8")], "black king on e8")
        assertEquals(32, start.count { it != 0 }, "32 pieces at the start")
    }

    @Test
    fun `fen with empty rows and holes parses`() {
        val squares = FenBoard.squares("4k3/8/8/8/8/8/8/R3Q2R w - - 0 1")
        assertEquals(4, squares[FenBoard.square("a1")])
        assertEquals(5, squares[FenBoard.square("e1")])
        assertEquals(-6, squares[FenBoard.square("e8")])
        assertEquals(0, squares[FenBoard.square("d1")])
    }

    @Test
    fun `square names round trip`() {
        for (sq in 0 until 64) {
            assertEquals(sq, FenBoard.square(FenBoard.name(sq)), "square ${FenBoard.name(sq)}")
        }
        assertEquals(-1, FenBoard.square("z9"))
        assertEquals(-1, FenBoard.square("e"))
    }

    @Test
    fun `view parsing reads status, last move and captures`() {
        val view = TableView.parse(
            buildJsonObject {
                put("fen", Board.START_FEN)
                put("turn", "w")
                put("white", "p1")
                put("black", "p2")
                put("status", "playing")
                put("check", false)
                put("ply", 2)
                put("botLevel", 3)
                put("lastMove", "d2d4")
                put("drawOfferBy", "p2")
                putJsonObject("captured") {
                    put("w", "p")
                    put("b", "")
                }
                putJsonArray("san") { add(JsonPrimitive("d4")) }
            },
        )
        assertEquals(2, view.ply)
        assertEquals(3, view.botLevel)
        assertEquals(listOf("d4"), view.san)
        assertEquals("d2d4", view.lastMove)
        assertEquals(FenBoard.square("d2"), view.lastFrom)
        assertEquals(FenBoard.square("d4"), view.lastTo)
        assertEquals("p2", view.drawOfferBy)
        assertEquals("p", view.capturedByWhite)
        assertEquals("", view.capturedByBlack)
        assertFalse(view.finished)
        assertNull(view.outcomeReason)
        assertNull(view.winner)
        assertEquals(Side.WHITE, view.colorOf("p1"))
        assertEquals(Side.BLACK, view.colorOf("p2"))
        assertEquals(0, view.colorOf("nobody"))
        assertEquals(-1, view.checkSquare)
    }

    @Test
    fun `an empty view falls back to an empty board`() {
        val view = TableView.parse(buildJsonObject { })
        assertEquals(0, view.squares.count { it != 0 })
        assertEquals(0, view.ply)
        assertFalse(view.finished)
        assertNull(view.you)
        assertEquals(-1, view.lastFrom)
        assertEquals(-1, view.lastTo)
    }

    @Test
    fun `check square points at the side to move's king`() {
        val whiteChecked = TableView.parse(
            buildJsonObject {
                put("fen", "4k3/8/8/8/8/8/4Q3/4K3 w - - 0 1")
                put("turn", "w")
                put("check", true)
            },
        )
        assertEquals(FenBoard.square("e1"), whiteChecked.checkSquare)

        val blackChecked = TableView.parse(
            buildJsonObject {
                put("fen", "4k3/8/8/8/8/8/4q3/4K3 b - - 0 1")
                put("turn", "b")
                put("check", true)
            },
        )
        assertEquals(FenBoard.square("e8"), blackChecked.checkSquare)

        val quiet = blackChecked.copy(inCheck = false)
        assertEquals(-1, quiet.checkSquare, "no flag, no glow")
    }

    @Test
    fun `finished view exposes the outcome`() {
        val view = TableView.parse(
            buildJsonObject {
                put("fen", Board.START_FEN)
                put("status", "finished")
                putJsonObject("outcome") {
                    put("reason", "checkmate")
                    put("winner", "p1")
                }
            },
        )
        assertTrue(view.finished)
        assertEquals("checkmate", view.outcomeReason)
        assertEquals("p1", view.winner)
        assertEquals("کیش و مات", ChessText.reason("checkmate"))
    }

    @Test
    fun `state builds the scene the renderer draws`() {
        val view = TableView.parse(
            buildJsonObject {
                put("fen", "4k3/8/8/3B4/8/8/8/4K3 w - - 0 1")
                put("lastMove", "d8e8")
            },
        )
        val state = ChessUiState(
            view = view,
            selected = FenBoard.square("e1"),
            quietTargets = setOf(FenBoard.square("d1")),
            captureTargets = setOf(FenBoard.square("e8")),
            positionId = 9,
        )
        val scene = state.scene()
        assertEquals(3, scene.squares[FenBoard.square("d5")], "bishop on d5")
        assertEquals(FenBoard.square("e1"), scene.selected)
        assertEquals(setOf(FenBoard.square("d1")), scene.quietTargets)
        assertEquals(setOf(FenBoard.square("e8")), scene.captureTargets)
        assertEquals(9L, scene.positionId)
        assertEquals(FenBoard.square("d8"), scene.lastFrom)
        assertEquals(FenBoard.square("e8"), scene.lastTo)
        assertNull(state.outcome, "no outcome while playing")
    }

    @Test
    fun `empty state yields an empty scene instead of crashing`() {
        val scene = ChessUiState().scene()
        assertEquals(64, scene.squares.size)
        assertEquals(0, scene.squares.count { it != 0 })
    }
}

/** Selection: which tap selects, which tap moves, which one asks for a promotion piece. */
class ChessSelectionTest {

    private fun viewWith(
        legal: List<String>,
        fen: String = Board.START_FEN,
        myTurn: Boolean = true,
        finished: Boolean = false,
        check: Boolean = false,
    ): TableView =
        TableView.parse(
            buildJsonObject {
                put("fen", fen)
                put("turn", "w")
                put("white", "p1")
                put("black", "p2")
                put("check", check)
                put("status", if (finished) "finished" else "playing")
            },
        ).copy(you = YouView("p1", Side.WHITE, myTurn, legal, listOf("move"), drawOfferedToMe = false))

    @Test
    fun `tapping own piece selects it and lists its legal targets`() {
        val r = Selection.tap(viewWith(listOf("e2e4", "e2e3", "d2d4")), selected = -1, square = FenBoard.square("e2"))
        assertTrue(r is TapResult.Select, "expected select, got $r")
        r as TapResult.Select
        assertEquals(FenBoard.square("e2"), r.square)
        assertEquals(setOf(FenBoard.square("e4"), FenBoard.square("e3")), r.quietTargets)
        assertTrue(r.captureTargets.isEmpty())
    }

    @Test
    fun `capture targets are split from quiet ones`() {
        val v = viewWith(listOf("d4d5", "d4c5"), "4k3/8/8/2p5/3P4/8/8/4K3 w - - 0 1")
        val r = Selection.tap(v, -1, FenBoard.square("d4"))
        r as TapResult.Select
        assertEquals(setOf(FenBoard.square("c5")), r.captureTargets)
        assertEquals(setOf(FenBoard.square("d5")), r.quietTargets)
    }

    @Test
    fun `en passant square counts as a capture`() {
        val v = viewWith(listOf("b5c6"), "4k3/8/8/pPp5/8/8/8/4K3 w - c6 0 1")
        val r = Selection.tap(v, -1, FenBoard.square("b5"))
        r as TapResult.Select
        assertEquals(setOf(FenBoard.square("c6")), r.captureTargets)
    }

    @Test
    fun `tapping a target plays the matching move`() {
        val v = viewWith(listOf("e2e4", "e2e3"))
        assertEquals(TapResult.Move("e2e4"), Selection.tap(v, FenBoard.square("e2"), FenBoard.square("e4")))
    }

    @Test
    fun `promotions ask for a piece, in q r b n order`() {
        val v = viewWith(listOf("e7e8q", "e7e8r", "e7e8n", "e7e8b"), "4k3/4P3/8/8/8/8/8/4K3 w - - 0 1")
        val move = Selection.tap(v, FenBoard.square("e7"), FenBoard.square("e8"))
        assertTrue(move is TapResult.ChoosePromotion, "expected promotion prompt, got $move")
        move as TapResult.ChoosePromotion
        assertEquals(listOf('q', 'r', 'b', 'n'), move.pieces)
        assertEquals(FenBoard.square("e7"), move.from)
        assertEquals(FenBoard.square("e8"), move.to)
    }

    @Test
    fun `single promotion choice is played without asking`() {
        val v = viewWith(listOf("e7e8q"), "4k3/4P3/8/8/8/8/8/4K3 w - - 0 1")
        assertEquals(TapResult.Move("e7e8q"), Selection.tap(v, FenBoard.square("e7"), FenBoard.square("e8")))
    }

    @Test
    fun `castling is just a king move two files over`() {
        val v = viewWith(listOf("e1g1"), "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val r = Selection.tap(v, -1, FenBoard.square("e1"))
        r as TapResult.Select
        assertEquals(setOf(FenBoard.square("g1")), r.quietTargets, "castle square is a quiet target")
        assertEquals(TapResult.Move("e1g1"), Selection.tap(v, FenBoard.square("e1"), FenBoard.square("g1")))
    }

    @Test
    fun `tapping elsewhere or the same piece deselects`() {
        val v = viewWith(listOf("e2e4"))
        assertEquals(TapResult.Deselect, Selection.tap(v, FenBoard.square("e2"), FenBoard.square("e2")))
        assertEquals(TapResult.Deselect, Selection.tap(v, FenBoard.square("e2"), FenBoard.square("a5")))
        assertEquals(TapResult.Ignore, Selection.tap(v, -1, FenBoard.square("a5")))
    }

    @Test
    fun `tapping off the board is ignored or deselects`() {
        val v = viewWith(listOf("e2e4"))
        assertEquals(TapResult.Ignore, Selection.tap(v, -1, -1))
        assertEquals(TapResult.Deselect, Selection.tap(v, FenBoard.square("e2"), -1))
    }

    @Test
    fun `a piece with no moves says why`() {
        val stuck = viewWith(emptyList(), "4k3/8/8/8/8/8/P6P/4K3 w - - 0 1")
        val r = Selection.tap(stuck, -1, FenBoard.square("h2"))
        assertTrue(r is TapResult.Rejected, "expected rejection, got $r")

        val mated = viewWith(emptyList(), "6k1/8/8/8/8/8/6q1/6K1 w - - 0 1", check = true)
        val rr = Selection.tap(mated, -1, FenBoard.square("g1"))
        assertTrue(rr is TapResult.Rejected, "checkmate is reported: $rr")
    }

    @Test
    fun `not my turn and finished games swallow taps`() {
        val waiting = viewWith(emptyList(), myTurn = false)
        // Tapping an enemy piece while waiting does nothing; tapping one of mine explains the wait.
        assertEquals(TapResult.Ignore, Selection.tap(waiting, -1, FenBoard.square("e7")))
        assertTrue(Selection.tap(waiting, -1, FenBoard.square("e2")) is TapResult.Rejected)

        val ended = viewWith(listOf("e2e4"), finished = true)
        assertEquals(TapResult.Ignore, Selection.tap(ended, FenBoard.square("e2"), FenBoard.square("e4")))
    }

    @Test
    fun `enemy pieces are never selected`() {
        val v = viewWith(listOf("e2e4"))
        assertEquals(TapResult.Ignore, Selection.tap(v, -1, FenBoard.square("e7")))
    }

    @Test
    fun `a view without the private block takes no input`() {
        val public = TableView.parse(buildJsonObject { put("fen", Board.START_FEN) })
        assertEquals(TapResult.Ignore, Selection.tap(public, -1, FenBoard.square("e2")))
    }
}

/** The board scene: coordinates of a square, and how a move becomes an animation. */
class ChessSceneTest {

    @Test
    fun `square centres round trip through the picking helper`() {
        for (sq in 0 until 64) {
            val x = BoardScene.squareX(sq)
            val z = BoardScene.squareZ(sq)
            assertEquals(sq, BoardScene.squareAt(x, z), "round trip for ${FenBoard.name(sq)}")
            assertEquals(sq, BoardScene.squareAt(x + 0.4f, z - 0.4f), "near corner of ${FenBoard.name(sq)}")
        }
        assertEquals(-1, BoardScene.squareAt(4.9f, 0f))
        assertEquals(-1, BoardScene.squareAt(0f, -4.9f))
    }

    @Test
    fun `a1 is on white's side and to the left`() {
        assertTrue(BoardScene.squareX(0) < 0f, "the a-file is left of centre")
        assertTrue(BoardScene.squareZ(0) > 0f, "rank 1 is on white's side (+z)")
        assertTrue(BoardScene.squareZ(63) < 0f, "rank 8 is away from white")
    }

    @Test
    fun `animation of a quiet move keeps from and to`() {
        val anim = MoveAnim.of(FenBoard.squares(Board.START_FEN), "g1f3")!!
        assertEquals(FenBoard.square("g1"), anim.from)
        assertEquals(FenBoard.square("f3"), anim.to)
        assertEquals(2, anim.moved)
        assertEquals(0, anim.captured)
        assertEquals(-1, anim.captureSquare)
        assertEquals(-1, anim.rookFrom)
    }

    @Test
    fun `capture and en passant animations find the victim`() {
        val plain = MoveAnim.of(FenBoard.squares("4k3/8/8/3p4/4P3/8/8/4K3 w - - 0 1"), "e4d5")!!
        assertEquals(-1, plain.captured, "the victim's code")
        assertEquals(FenBoard.square("d5"), plain.captureSquare)

        val ep = MoveAnim.of(FenBoard.squares("4k3/8/8/pPp5/8/8/8/4K3 w - c6 0 1"), "b5c6")!!
        assertEquals(FenBoard.square("c5"), ep.captureSquare, "the pawn behind the target square")
        assertEquals(-1, ep.captured)
    }

    @Test
    fun `castling animates the rook too`() {
        val before = FenBoard.squares("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1")
        val short = MoveAnim.of(before, "e1g1")!!
        assertEquals(FenBoard.square("h1"), short.rookFrom)
        assertEquals(FenBoard.square("f1"), short.rookTo)
        val long = MoveAnim.of(before, "e1c1")!!
        assertEquals(FenBoard.square("a1"), long.rookFrom)
        assertEquals(FenBoard.square("d1"), long.rookTo)
        assertEquals(-1, MoveAnim.of(before, "e1e2")!!.rookFrom, "a king walk is not a castle")
    }

    @Test
    fun `promotion travels as a pawn and impossible text is refused`() {
        val anim = MoveAnim.of(FenBoard.squares("4k3/4P3/8/8/8/8/8/4K3 w - - 0 1"), "e7e8q")!!
        assertEquals(1, anim.moved, "the piece that travels is still the pawn")
        assertNull(MoveAnim.of(FenBoard.squares(Board.START_FEN), "e4e5"), "nothing on e4 to move")
        assertNull(MoveAnim.of(FenBoard.squares(Board.START_FEN), "xx"), "not a move at all")
        assertNull(MoveAnim.of(FenBoard.squares(Board.START_FEN), ""), "empty")
    }

    @Test
    fun `scenes compare by content so the view can skip no-op updates`() {
        val squares = FenBoard.squares(Board.START_FEN)
        val a = BoardScene(squares.copyOf(), positionId = 1)
        val b = BoardScene(squares.copyOf(), positionId = 1)
        val c = BoardScene(squares.copyOf(), positionId = 2)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c)
    }
}

/** Persian strings, glyphs and the material tray. */
class ChessTextTest {

    @Test
    fun `every end reason has a Persian label`() {
        for (reason in EndReason.values()) {
            assertTrue(ChessText.reason(reason.wire).isNotEmpty(), "no label for ${reason.wire}")
        }
        assertEquals("", ChessText.reason("nonsense"))
        assertEquals("", ChessText.reason(null))
    }

    @Test
    fun `glyphs are the outline and filled chess symbols`() {
        assertEquals("♙", ChessText.glyph(1))
        assertEquals("♞", ChessText.glyph(-2))
        assertEquals("♔", ChessText.glyph(6))
        assertEquals("♛", ChessText.glyph(-5))
        assertEquals("♛", ChessText.glyph('q'))
        assertEquals("♕", ChessText.glyph('Q'))
        assertEquals("", ChessText.glyph(0))
        assertEquals("", ChessText.glyph('x'))
    }

    @Test
    fun `material balance and tray sorting`() {
        assertEquals(0, ChessText.materialBalance("", ""))
        assertEquals(-9, ChessText.materialBalance("", "q"))
        assertEquals(3, ChessText.materialBalance("ppp", ""))
        assertEquals("ppr", ChessText.sortedTray("rpp"))
        assertEquals("pnnbbrq", ChessText.sortedTray("qrnbnbp"), "pawns, knights, bishops, rooks, queens")
    }

    @Test
    fun `names for sides and levels`() {
        assertEquals("سفید", ChessText.colorName(Side.WHITE))
        assertEquals("سیاه", ChessText.colorName(Side.BLACK))
        assertEquals("تازه‌کار", ChessText.levelName(1))
        assertEquals("متوسط", ChessText.levelName(2))
        assertEquals("قوی", ChessText.levelName(3))
        assertEquals("استاد", ChessText.levelName(4))
        assertEquals("استاد", ChessText.levelName(9), "unknown levels clamp to the strongest label")
        assertEquals("وزیر", ChessText.promotionName('q'))
        assertEquals("رخ", ChessText.promotionName('r'))
        assertEquals("اسب", ChessText.promotionName('n'))
    }
}
