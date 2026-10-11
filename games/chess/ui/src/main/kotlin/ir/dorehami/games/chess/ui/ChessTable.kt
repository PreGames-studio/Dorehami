package ir.dorehami.games.chess.ui

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/*
 * Pure (Android-free) helpers between a chess view (`privateView`/`publicView` JSON) and the
 * screen: parsing, square selection and Persian wording. Unit-tested on the JVM.
 */

/** Colour constants as used by the 3D scene: white `+1`, black `-1`. */
object Side {
    const val WHITE = 1
    const val BLACK = -1

    fun fromLetter(s: String?): Int = if (s == "b") BLACK else WHITE
}

/** Piece placement from the first FEN field, `a1 = 0 … h8 = 63`, codes `±1..6` (pawn..king). */
object FenBoard {
    fun squares(fen: String): IntArray {
        val out = IntArray(64)
        val placement = fen.trim().substringBefore(' ')
        var rank = 7
        var file = 0
        for (c in placement) {
            when {
                c == '/' -> {
                    rank--
                    file = 0
                }
                c.isDigit() -> file += c - '0'
                else -> {
                    val type = "pnbrqk".indexOf(c.lowercaseChar()) + 1
                    if (type > 0 && rank in 0..7 && file in 0..7) {
                        out[rank * 8 + file] = if (c.isUpperCase()) type else -type
                    }
                    file++
                }
            }
        }
        return out
    }

    /** `"e4"` → 28, or -1. */
    fun square(name: String): Int {
        if (name.length != 2) return -1
        val f = name[0] - 'a'
        val r = name[1] - '1'
        return if (f in 0..7 && r in 0..7) r * 8 + f else -1
    }

    fun name(sq: Int): String = "${'a' + (sq and 7)}${'1' + (sq shr 3)}"

    fun kingSquare(squares: IntArray, side: Int): Int = squares.indexOfFirst { it == 6 * side }
}

/** The `you` block of a private view. */
data class YouView(
    val player: String,
    val color: Int,
    val myTurn: Boolean,
    val legalMoves: List<String>,
    val actions: List<String>,
    val drawOfferedToMe: Boolean,
)

/** Everything the table needs from one view, already typed. */
data class TableView(
    val fen: String,
    val squares: IntArray,
    val sideToMove: Int,
    val inCheck: Boolean,
    val ply: Int,
    val lastMove: String?,
    val san: List<String>,
    val uci: List<String>,
    /** FEN letters of the pieces white has captured (i.e. black pieces), in order. */
    val capturedByWhite: String,
    val capturedByBlack: String,
    val whitePlayer: String,
    val blackPlayer: String,
    val drawOfferBy: String?,
    val finished: Boolean,
    val outcomeReason: String?,
    /** Player id of the winner; null for a draw or while playing. */
    val winner: String?,
    val botLevel: Int,
    val you: YouView?,
) {
    /** Square of the king in check, or -1. */
    val checkSquare: Int get() = if (inCheck) FenBoard.kingSquare(squares, sideToMove) else -1

    val lastFrom: Int get() = lastMove?.let { FenBoard.square(it.take(2)) } ?: -1

    val lastTo: Int get() = lastMove?.let { if (it.length >= 4) FenBoard.square(it.substring(2, 4)) else -1 } ?: -1

    fun colorOf(player: String): Int = when (player) {
        whitePlayer -> Side.WHITE
        blackPlayer -> Side.BLACK
        else -> 0
    }

    override fun equals(other: Any?): Boolean = other is TableView && fen == other.fen && ply == other.ply &&
        drawOfferBy == other.drawOfferBy && finished == other.finished && you == other.you && san == other.san

    override fun hashCode(): Int = fen.hashCode() * 31 + ply

    companion object {
        /** Parses a chess `publicView`/`privateView`. Missing fields fall back to safe defaults. */
        fun parse(v: JsonObject): TableView {
            val fen = v.str("fen") ?: "8/8/8/8/8/8/8/8 w - - 0 1"
            val captured = v["captured"] as? JsonObject
            val outcome = v["outcome"] as? JsonObject
            val you = (v["you"] as? JsonObject)?.let { y ->
                YouView(
                    player = y.str("player") ?: "",
                    color = Side.fromLetter(y.str("color")),
                    myTurn = y.bool("myTurn"),
                    legalMoves = y.strList("legalMoves"),
                    actions = y.strList("actions"),
                    drawOfferedToMe = y.bool("drawOfferedToMe"),
                )
            }
            return TableView(
                fen = fen,
                squares = FenBoard.squares(fen),
                sideToMove = Side.fromLetter(v.str("turn")),
                inCheck = v.bool("check"),
                ply = (v["ply"] as? JsonPrimitive)?.intOrNull ?: 0,
                lastMove = v.str("lastMove"),
                san = v.strList("san"),
                uci = v.strList("uci"),
                capturedByWhite = captured?.str("w") ?: "",
                capturedByBlack = captured?.str("b") ?: "",
                whitePlayer = v.str("white") ?: "",
                blackPlayer = v.str("black") ?: "",
                drawOfferBy = v.str("drawOfferBy"),
                finished = v.str("status") == "finished",
                outcomeReason = outcome?.str("reason"),
                winner = outcome?.str("winner"),
                botLevel = (v["botLevel"] as? JsonPrimitive)?.intOrNull ?: 2,
                you = you,
            )
        }

        private fun JsonObject.str(key: String): String? {
            val e = this[key] ?: return null
            if (e is JsonNull) return null
            return (e as? JsonPrimitive)?.takeIf { it.isString }?.content
        }

        private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

        private fun JsonObject.strList(key: String): List<String> =
            (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content } ?: emptyList()
    }
}

/** What a tap on the board means, given the current selection. */
sealed interface TapResult {
    data class Select(val square: Int, val quietTargets: Set<Int>, val captureTargets: Set<Int>) : TapResult

    data object Deselect : TapResult

    data class Move(val uci: String) : TapResult

    /** Several moves differ only by the promotion piece — ask the player. [pieces] are `q r b n`. */
    data class ChoosePromotion(val from: Int, val to: Int, val pieces: List<Char>) : TapResult

    data class Rejected(val messageFa: String) : TapResult

    data object Ignore : TapResult
}

/**
 * Tap-to-select / tap-to-move, driven only by the legal moves in the player's view (the screen
 * never re-implements chess rules).
 */
object Selection {
    fun tap(view: TableView, selected: Int, square: Int): TapResult {
        val you = view.you ?: return TapResult.Ignore
        if (view.finished) return TapResult.Ignore
        if (square !in 0..63) return if (selected >= 0) TapResult.Deselect else TapResult.Ignore
        if (!you.myTurn) {
            return if (view.squares[square] != 0 && Integer.signum(view.squares[square]) == you.color) {
                TapResult.Rejected("صبر کن، نوبت حریف است")
            } else {
                TapResult.Ignore
            }
        }
        if (selected >= 0) {
            val prefix = FenBoard.name(selected) + FenBoard.name(square)
            val matches = you.legalMoves.filter { it.startsWith(prefix) }
            if (matches.size == 1) return TapResult.Move(matches[0])
            if (matches.size > 1) {
                val order = "qrbn"
                val pieces = matches.mapNotNull { it.getOrNull(4) }.distinct().sortedBy { order.indexOf(it) }
                return TapResult.ChoosePromotion(selected, square, pieces)
            }
            if (square == selected) return TapResult.Deselect
        }
        val piece = view.squares[square]
        if (piece != 0 && Integer.signum(piece) == you.color) {
            val from = FenBoard.name(square)
            val targets = you.legalMoves.filter { it.startsWith(from) }.map { FenBoard.square(it.substring(2, 4)) }.toSet()
            if (targets.isEmpty()) {
                return TapResult.Rejected(if (view.inCheck) "شاه در کیش است؛ این مهره نمی‌تواند کمک کند" else "این مهره حرکتی ندارد")
            }
            val captures = targets.filter { t -> view.squares[t] != 0 || isEnPassant(view, square, t) }.toSet()
            return TapResult.Select(square, targets - captures, captures)
        }
        return if (selected >= 0) TapResult.Deselect else TapResult.Ignore
    }

    private fun isEnPassant(view: TableView, from: Int, to: Int): Boolean =
        kotlin.math.abs(view.squares[from]) == 1 && (from and 7) != (to and 7) && view.squares[to] == 0
}

/** Persian wording for the table. */
object ChessText {
    fun reason(wire: String?): String = when (wire) {
        "checkmate" -> "کیش و مات"
        "resign" -> "تسلیم"
        "stalemate" -> "پات"
        "insufficient_material" -> "مهرهٔ کافی برای مات نمانده"
        "fifty_moves" -> "قانون ۵۰ حرکت"
        "threefold" -> "تکرار سه‌بارهٔ وضعیت"
        "agreement" -> "توافق دو طرف"
        else -> ""
    }

    fun colorName(side: Int): String = if (side == Side.WHITE) "سفید" else "سیاه"

    fun levelName(level: Int): String = when (level) {
        1 -> "تازه‌کار"
        2 -> "متوسط"
        3 -> "قوی"
        else -> "استاد"
    }

    /** Unicode glyph of a piece code (`±1..6`); outline glyphs for white, filled for black. */
    fun glyph(code: Int): String {
        val white = "♙♘♗♖♕♔"
        val black = "♟♞♝♜♛♚"
        val t = kotlin.math.abs(code)
        if (t !in 1..6) return ""
        return (if (code > 0) white[t - 1] else black[t - 1]).toString()
    }

    /** FEN letter → glyph (`'q'` → ♛, `'Q'` → ♕). */
    fun glyph(fenLetter: Char): String {
        val t = "pnbrqk".indexOf(fenLetter.lowercaseChar()) + 1
        return if (t == 0) "" else glyph(if (fenLetter.isUpperCase()) t else -t)
    }

    /** Material balance in pawns from captured letters (positive = white ahead). */
    fun materialBalance(capturedByWhite: String, capturedByBlack: String): Int {
        fun value(s: String) = s.sumOf { c ->
            when (c.lowercaseChar()) {
                'p' -> 1
                'n', 'b' -> 3
                'r' -> 5
                'q' -> 9
                else -> 0
            }
        }
        return value(capturedByWhite) - value(capturedByBlack)
    }

    /** Captured letters sorted by value (pawns first) for a tidy tray. */
    fun sortedTray(captured: String): String {
        val order = "pnbrq"
        return captured.toList().sortedBy { order.indexOf(it.lowercaseChar()) }.joinToString("")
    }

    fun promotionName(c: Char): String = when (c) {
        'q' -> "وزیر"
        'r' -> "رخ"
        'b' -> "فیل"
        else -> "اسب"
    }
}
