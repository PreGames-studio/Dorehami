package ir.dorehami.games.chess

import kotlin.math.abs

/**
 * Piece codes. Positive = white, negative = black, `0` = empty square.
 * Kept as plain `Int`s so the move generator and the bot search stay allocation-free.
 */
object Piece {
    const val EMPTY = 0
    const val PAWN = 1
    const val KNIGHT = 2
    const val BISHOP = 3
    const val ROOK = 4
    const val QUEEN = 5
    const val KING = 6

    const val WHITE = 1
    const val BLACK = -1

    fun type(piece: Int): Int = abs(piece)

    fun color(piece: Int): Int = if (piece > 0) WHITE else if (piece < 0) BLACK else 0

    /** FEN letter: upper case for white (`P N B R Q K`), lower case for black. */
    fun fenChar(piece: Int): Char {
        val c = "?pnbrqk"[type(piece)]
        return if (piece > 0) c.uppercaseChar() else c
    }

    /** Inverse of [fenChar]; returns [EMPTY] for unknown letters. */
    fun fromFenChar(c: Char): Int {
        val t = "pnbrqk".indexOf(c.lowercaseChar()) + 1
        if (t == 0) return EMPTY
        return if (c.isUpperCase()) t else -t
    }

    /** Promotion letter in UCI/payload form (`q r b n`) → piece type, or `0`. */
    fun promoFromLetter(c: Char): Int = when (c.lowercaseChar()) {
        'q' -> QUEEN
        'r' -> ROOK
        'b' -> BISHOP
        'n' -> KNIGHT
        else -> 0
    }
}

/** Square helpers. `a1 = 0`, `b1 = 1`, …, `h8 = 63`. */
object Squares {
    fun file(sq: Int): Int = sq and 7

    fun rank(sq: Int): Int = sq shr 3

    fun of(file: Int, rank: Int): Int = rank * 8 + file

    fun name(sq: Int): String = "${'a' + file(sq)}${'1' + rank(sq)}"

    /** `"e4"` → 28, or `-1` when the text is not a square. */
    fun parse(text: String): Int {
        if (text.length != 2) return -1
        val f = text[0] - 'a'
        val r = text[1] - '1'
        if (f !in 0..7 || r !in 0..7) return -1
        return of(f, r)
    }

    /** `true` for dark squares (a1 is dark). */
    fun isDark(sq: Int): Boolean = (file(sq) + rank(sq)) % 2 == 0
}

/**
 * A move packed into one `Int`: `from | to << 6 | promotion << 12 | flags << 16`.
 * Only ever created by the generator, so every packed move is at least pseudo-legal.
 */
object Move {
    const val FLAG_CAPTURE = 1
    const val FLAG_EN_PASSANT = 2
    const val FLAG_CASTLE = 4
    const val FLAG_DOUBLE_PUSH = 8

    fun encode(from: Int, to: Int, promotion: Int = 0, flags: Int = 0): Int =
        from or (to shl 6) or (promotion shl 12) or (flags shl 16)

    fun from(m: Int): Int = m and 63

    fun to(m: Int): Int = (m shr 6) and 63

    /** Promotion piece *type* (unsigned) or `0`. */
    fun promotion(m: Int): Int = (m shr 12) and 7

    fun flags(m: Int): Int = m ushr 16

    fun isCapture(m: Int): Boolean = flags(m) and FLAG_CAPTURE != 0

    fun isCastle(m: Int): Boolean = flags(m) and FLAG_CASTLE != 0

    fun isEnPassant(m: Int): Boolean = flags(m) and FLAG_EN_PASSANT != 0

    /** Long algebraic / UCI form: `e2e4`, `e7e8q`. */
    fun uci(m: Int): String {
        val promo = promotion(m)
        val suffix = if (promo == 0) "" else "${"??nbrq"[promo]}"
        return Squares.name(from(m)) + Squares.name(to(m)) + suffix
    }
}

/** Growable list of packed moves without boxing. */
class MoveList(capacity: Int = 64) {
    private var data = IntArray(capacity)
    var size = 0
        private set

    fun add(m: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = m
    }

    operator fun get(i: Int): Int = data[i]

    operator fun set(i: Int, m: Int) {
        data[i] = m
    }

    fun clear() {
        size = 0
    }

    fun isEmpty(): Boolean = size == 0

    fun toList(): List<Int> = List(size) { data[it] }
}

/**
 * Mutable chess position with make/unmake. Shared by the rules ([ChessSession]) and the bot
 * ([ChessBot]); it knows nothing about players, rooms or JSON.
 */
class Board private constructor() {
    /** `squares[sq]` is a [Piece] code. */
    val squares = IntArray(64)

    /** [Piece.WHITE] or [Piece.BLACK]. */
    var side: Int = Piece.WHITE
        private set

    /** Bits: 1 = white O-O, 2 = white O-O-O, 4 = black O-O, 8 = black O-O-O. */
    var castling: Int = 0
        private set

    /** Square a pawn just skipped with a double push, or `-1`. */
    var epSquare: Int = -1
        private set

    var halfmoveClock: Int = 0
        private set

    var fullmoveNumber: Int = 1
        private set

    // Undo stack (enough for any bot search; the session calls [commit] after every real move).
    private val undoMove = IntArray(MAX_UNDO)
    private val undoCaptured = IntArray(MAX_UNDO)
    private val undoCastling = IntArray(MAX_UNDO)
    private val undoEp = IntArray(MAX_UNDO)
    private val undoHalfmove = IntArray(MAX_UNDO)
    private var undoTop = 0

    fun copy(): Board {
        val b = Board()
        squares.copyInto(b.squares)
        b.side = side
        b.castling = castling
        b.epSquare = epSquare
        b.halfmoveClock = halfmoveClock
        b.fullmoveNumber = fullmoveNumber
        return b
    }

    fun kingSquare(color: Int): Int {
        val king = Piece.KING * color
        for (sq in 0 until 64) if (squares[sq] == king) return sq
        return -1
    }

    fun inCheck(color: Int = side): Boolean {
        val k = kingSquare(color)
        return k >= 0 && isAttacked(k, -color)
    }

    /** Is [sq] attacked by any piece of colour [by]? */
    fun isAttacked(sq: Int, by: Int): Boolean {
        val f = Squares.file(sq)
        val r = Squares.rank(sq)

        // Pawns: a white pawn attacks upwards, so it sits one rank *below* the target.
        val pr = r - by
        if (pr in 0..7) {
            if (f > 0 && squares[Squares.of(f - 1, pr)] == Piece.PAWN * by) return true
            if (f < 7 && squares[Squares.of(f + 1, pr)] == Piece.PAWN * by) return true
        }
        for (i in KNIGHT_DF.indices) {
            val nf = f + KNIGHT_DF[i]
            val nr = r + KNIGHT_DR[i]
            if (nf in 0..7 && nr in 0..7 && squares[Squares.of(nf, nr)] == Piece.KNIGHT * by) return true
        }
        for (i in KING_DF.indices) {
            val nf = f + KING_DF[i]
            val nr = r + KING_DR[i]
            if (nf in 0..7 && nr in 0..7 && squares[Squares.of(nf, nr)] == Piece.KING * by) return true
        }
        // Sliders: the first 4 directions are orthogonal (rook/queen), the last 4 diagonal.
        for (d in 0 until 8) {
            var nf = f + KING_DF[d]
            var nr = r + KING_DR[d]
            while (nf in 0..7 && nr in 0..7) {
                val p = squares[Squares.of(nf, nr)]
                if (p != 0) {
                    if (Piece.color(p) == by) {
                        val t = Piece.type(p)
                        if (t == Piece.QUEEN) return true
                        if (d < 4 && t == Piece.ROOK) return true
                        if (d >= 4 && t == Piece.BISHOP) return true
                    }
                    break
                }
                nf += KING_DF[d]
                nr += KING_DR[d]
            }
        }
        return false
    }

    /**
     * Pseudo-legal moves for the side to move (may leave the own king in check).
     * With [capturesOnly] only captures and promotions are produced (for the bot's quiescence).
     */
    fun generatePseudo(out: MoveList, capturesOnly: Boolean = false) {
        val us = side
        for (sq in 0 until 64) {
            val p = squares[sq]
            if (p == 0 || Piece.color(p) != us) continue
            val f = Squares.file(sq)
            val r = Squares.rank(sq)
            when (Piece.type(p)) {
                Piece.PAWN -> genPawn(out, sq, f, r, us, capturesOnly)
                Piece.KNIGHT -> genSteps(out, sq, f, r, us, KNIGHT_DF, KNIGHT_DR, capturesOnly)
                Piece.BISHOP -> genSlides(out, sq, f, r, us, 4, 8, capturesOnly)
                Piece.ROOK -> genSlides(out, sq, f, r, us, 0, 4, capturesOnly)
                Piece.QUEEN -> genSlides(out, sq, f, r, us, 0, 8, capturesOnly)
                Piece.KING -> {
                    genSteps(out, sq, f, r, us, KING_DF, KING_DR, capturesOnly)
                    if (!capturesOnly) genCastles(out, us)
                }
            }
        }
    }

    private fun genPawn(out: MoveList, sq: Int, f: Int, r: Int, us: Int, capturesOnly: Boolean) {
        val dir = 8 * us
        val startRank = if (us == Piece.WHITE) 1 else 6
        val lastRank = if (us == Piece.WHITE) 7 else 0
        val one = sq + dir
        if (one in 0..63 && squares[one] == 0) {
            if (Squares.rank(one) == lastRank) {
                addPromotions(out, sq, one, 0, capturesOnly)
            } else if (!capturesOnly) {
                out.add(Move.encode(sq, one))
                val two = one + dir
                if (r == startRank && squares[two] == 0) out.add(Move.encode(sq, two, 0, Move.FLAG_DOUBLE_PUSH))
            }
        }
        for (df in PAWN_DF) {
            val nf = f + df
            if (nf !in 0..7) continue
            val to = one + df
            if (to !in 0..63) continue
            val target = squares[to]
            if (target != 0 && Piece.color(target) == -us) {
                if (Squares.rank(to) == lastRank) {
                    addPromotions(out, sq, to, Move.FLAG_CAPTURE, false)
                } else {
                    out.add(Move.encode(sq, to, 0, Move.FLAG_CAPTURE))
                }
            } else if (target == 0 && to == epSquare) {
                out.add(Move.encode(sq, to, 0, Move.FLAG_CAPTURE or Move.FLAG_EN_PASSANT))
            }
        }
    }

    private fun addPromotions(out: MoveList, from: Int, to: Int, flags: Int, queenOnly: Boolean) {
        out.add(Move.encode(from, to, Piece.QUEEN, flags))
        if (queenOnly) return
        out.add(Move.encode(from, to, Piece.ROOK, flags))
        out.add(Move.encode(from, to, Piece.BISHOP, flags))
        out.add(Move.encode(from, to, Piece.KNIGHT, flags))
    }

    private fun genSteps(
        out: MoveList, sq: Int, f: Int, r: Int, us: Int,
        dfs: IntArray, drs: IntArray, capturesOnly: Boolean,
    ) {
        for (i in dfs.indices) {
            val nf = f + dfs[i]
            val nr = r + drs[i]
            if (nf !in 0..7 || nr !in 0..7) continue
            val to = Squares.of(nf, nr)
            val target = squares[to]
            if (target == 0) {
                if (!capturesOnly) out.add(Move.encode(sq, to))
            } else if (Piece.color(target) == -us) {
                out.add(Move.encode(sq, to, 0, Move.FLAG_CAPTURE))
            }
        }
    }

    private fun genSlides(
        out: MoveList, sq: Int, f: Int, r: Int, us: Int,
        dirFrom: Int, dirTo: Int, capturesOnly: Boolean,
    ) {
        for (d in dirFrom until dirTo) {
            var nf = f + KING_DF[d]
            var nr = r + KING_DR[d]
            while (nf in 0..7 && nr in 0..7) {
                val to = Squares.of(nf, nr)
                val target = squares[to]
                if (target == 0) {
                    if (!capturesOnly) out.add(Move.encode(sq, to))
                } else {
                    if (Piece.color(target) == -us) out.add(Move.encode(sq, to, 0, Move.FLAG_CAPTURE))
                    break
                }
                nf += KING_DF[d]
                nr += KING_DR[d]
            }
        }
    }

    private fun genCastles(out: MoveList, us: Int) {
        val base = if (us == Piece.WHITE) 0 else 56
        val kingFrom = base + 4
        if (squares[kingFrom] != Piece.KING * us) return
        val kingSide = if (us == Piece.WHITE) 1 else 4
        val queenSide = if (us == Piece.WHITE) 2 else 8
        val rook = Piece.ROOK * us
        if (castling and kingSide != 0 &&
            squares[base + 7] == rook && squares[base + 5] == 0 && squares[base + 6] == 0 &&
            !isAttacked(kingFrom, -us) && !isAttacked(base + 5, -us) && !isAttacked(base + 6, -us)
        ) {
            out.add(Move.encode(kingFrom, base + 6, 0, Move.FLAG_CASTLE))
        }
        if (castling and queenSide != 0 &&
            squares[base] == rook && squares[base + 1] == 0 && squares[base + 2] == 0 && squares[base + 3] == 0 &&
            !isAttacked(kingFrom, -us) && !isAttacked(base + 3, -us) && !isAttacked(base + 2, -us)
        ) {
            out.add(Move.encode(kingFrom, base + 2, 0, Move.FLAG_CASTLE))
        }
    }

    /** Fully legal moves of the side to move, in deterministic generator order. */
    fun legalMoves(): MoveList {
        val pseudo = MoveList()
        generatePseudo(pseudo)
        val legal = MoveList(pseudo.size.coerceAtLeast(1))
        val us = side
        for (i in 0 until pseudo.size) {
            val m = pseudo[i]
            makeMove(m)
            if (!inCheck(us)) legal.add(m)
            unmakeMove()
        }
        return legal
    }

    /** Plays a (pseudo-)legal move. Always pair with [unmakeMove] or [commit]. */
    fun makeMove(m: Int) {
        check(undoTop < MAX_UNDO) { "undo stack overflow" }
        val from = Move.from(m)
        val to = Move.to(m)
        val flags = Move.flags(m)
        val piece = squares[from]
        var captured = squares[to]

        undoMove[undoTop] = m
        undoCastling[undoTop] = castling
        undoEp[undoTop] = epSquare
        undoHalfmove[undoTop] = halfmoveClock

        if (flags and Move.FLAG_EN_PASSANT != 0) {
            val capSq = to - 8 * side
            captured = squares[capSq]
            squares[capSq] = 0
        }
        undoCaptured[undoTop] = captured
        undoTop++

        val promo = Move.promotion(m)
        squares[to] = if (promo != 0) promo * side else piece
        squares[from] = 0

        if (flags and Move.FLAG_CASTLE != 0) {
            val base = if (side == Piece.WHITE) 0 else 56
            if (to == base + 6) {
                squares[base + 5] = squares[base + 7]
                squares[base + 7] = 0
            } else {
                squares[base + 3] = squares[base]
                squares[base] = 0
            }
        }

        castling = castling and CASTLE_MASK[from] and CASTLE_MASK[to]
        epSquare = if (flags and Move.FLAG_DOUBLE_PUSH != 0) (from + to) / 2 else -1
        halfmoveClock = if (Piece.type(piece) == Piece.PAWN || captured != 0) 0 else halfmoveClock + 1
        if (side == Piece.BLACK) fullmoveNumber++
        side = -side
    }

    fun unmakeMove() {
        check(undoTop > 0) { "nothing to undo" }
        undoTop--
        val m = undoMove[undoTop]
        side = -side
        if (side == Piece.BLACK) fullmoveNumber--
        castling = undoCastling[undoTop]
        epSquare = undoEp[undoTop]
        halfmoveClock = undoHalfmove[undoTop]

        val from = Move.from(m)
        val to = Move.to(m)
        val flags = Move.flags(m)
        val moved = if (Move.promotion(m) != 0) Piece.PAWN * side else squares[to]
        squares[from] = moved
        val captured = undoCaptured[undoTop]
        if (flags and Move.FLAG_EN_PASSANT != 0) {
            squares[to] = 0
            squares[to - 8 * side] = captured
        } else {
            squares[to] = captured
        }
        if (flags and Move.FLAG_CASTLE != 0) {
            val base = if (side == Piece.WHITE) 0 else 56
            if (to == base + 6) {
                squares[base + 7] = squares[base + 5]
                squares[base + 5] = 0
            } else {
                squares[base] = squares[base + 3]
                squares[base + 3] = 0
            }
        }
    }

    /** Forgets the undo history: the moves played so far become the new base position. */
    fun commit() {
        undoTop = 0
    }

    /** Piece captured by the last made move (`0` if none). */
    fun lastCaptured(): Int = if (undoTop == 0) 0 else undoCaptured[undoTop - 1]

    /**
     * Standard Algebraic Notation of [m] (`Nf3`, `exd5`, `O-O`, `e8=Q#`).
     * [legal] must be [legalMoves] of the current position (passed in to avoid recomputing).
     */
    fun san(m: Int, legal: MoveList = legalMoves()): String {
        val sb = StringBuilder()
        val from = Move.from(m)
        val to = Move.to(m)
        val type = Piece.type(squares[from])
        if (Move.isCastle(m)) {
            sb.append(if (Squares.file(to) == 6) "O-O" else "O-O-O")
        } else {
            if (type == Piece.PAWN) {
                if (Move.isCapture(m)) sb.append('a' + Squares.file(from)).append('x')
                sb.append(Squares.name(to))
                val promo = Move.promotion(m)
                if (promo != 0) sb.append('=').append("??NBRQ"[promo])
            } else {
                sb.append("??NBRQK"[type])
                var ambiguous = false
                var sameFile = false
                var sameRank = false
                for (i in 0 until legal.size) {
                    val o = legal[i]
                    val of = Move.from(o)
                    if (o == m || Move.to(o) != to || of == from) continue
                    if (Piece.type(squares[of]) != type) continue
                    ambiguous = true
                    if (Squares.file(of) == Squares.file(from)) sameFile = true
                    if (Squares.rank(of) == Squares.rank(from)) sameRank = true
                }
                if (ambiguous) {
                    if (!sameFile) {
                        sb.append('a' + Squares.file(from))
                    } else if (!sameRank) {
                        sb.append('1' + Squares.rank(from))
                    } else {
                        sb.append(Squares.name(from))
                    }
                }
                if (Move.isCapture(m)) sb.append('x')
                sb.append(Squares.name(to))
            }
        }
        makeMove(m)
        if (inCheck(side)) sb.append(if (legalMoves().isEmpty()) '#' else '+')
        unmakeMove()
        return sb.toString()
    }

    /**
     * Neither side can ever mate: K vs K, K + one minor vs K, or only same-coloured bishops left.
     * (FIDE 9.6 dead positions beyond these are not detected.)
     */
    fun insufficientMaterial(): Boolean {
        var minors = 0
        var knights = 0
        var lightBishops = 0
        var darkBishops = 0
        for (sq in 0 until 64) {
            when (Piece.type(squares[sq])) {
                Piece.PAWN, Piece.ROOK, Piece.QUEEN -> return false
                Piece.KNIGHT -> { minors++; knights++ }
                Piece.BISHOP -> {
                    minors++
                    if (Squares.isDark(sq)) darkBishops++ else lightBishops++
                }
            }
        }
        if (minors <= 1) return true
        return knights == 0 && (lightBishops == 0 || darkBishops == 0)
    }

    /** Can the side to move actually capture en passant right now? (Matters for repetition keys.) */
    private fun epCapturePossible(): Boolean {
        if (epSquare < 0) return false
        val legal = legalMoves()
        for (i in 0 until legal.size) if (Move.isEnPassant(legal[i])) return true
        return false
    }

    /** Identity of the position for threefold repetition: placement, side, castling, real e.p. */
    fun repetitionKey(): String {
        val fen = toFen().split(' ')
        val ep = if (epCapturePossible()) Squares.name(epSquare) else "-"
        return "${fen[0]} ${fen[1]} ${fen[2]} $ep"
    }

    fun toFen(): String {
        val sb = StringBuilder()
        for (r in 7 downTo 0) {
            var empty = 0
            for (f in 0..7) {
                val p = squares[Squares.of(f, r)]
                if (p == 0) {
                    empty++
                } else {
                    if (empty > 0) sb.append(empty)
                    empty = 0
                    sb.append(Piece.fenChar(p))
                }
            }
            if (empty > 0) sb.append(empty)
            if (r > 0) sb.append('/')
        }
        sb.append(if (side == Piece.WHITE) " w " else " b ")
        if (castling == 0) {
            sb.append('-')
        } else {
            if (castling and 1 != 0) sb.append('K')
            if (castling and 2 != 0) sb.append('Q')
            if (castling and 4 != 0) sb.append('k')
            if (castling and 8 != 0) sb.append('q')
        }
        sb.append(' ').append(if (epSquare < 0) "-" else Squares.name(epSquare))
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber)
        return sb.toString()
    }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        private const val MAX_UNDO = 512

        // Directions: 0..3 orthogonal, 4..7 diagonal (order matters for isAttacked/genSlides).
        internal val KING_DF = intArrayOf(1, -1, 0, 0, 1, 1, -1, -1)
        internal val KING_DR = intArrayOf(0, 0, 1, -1, 1, -1, 1, -1)
        private val PAWN_DF = intArrayOf(-1, 1)
        private val KNIGHT_DF = intArrayOf(1, 2, 2, 1, -1, -2, -2, -1)
        private val KNIGHT_DR = intArrayOf(2, 1, -1, -2, -2, -1, 1, 2)

        /** Castling bits that survive a move touching each square. */
        private val CASTLE_MASK = IntArray(64) { 15 }.also {
            it[0] = 15 and 2.inv()
            it[7] = 15 and 1.inv()
            it[4] = 15 and 3.inv()
            it[56] = 15 and 8.inv()
            it[63] = 15 and 4.inv()
            it[60] = 15 and 12.inv()
        }

        fun start(): Board = fromFen(START_FEN)

        /** Parses a FEN; throws [IllegalArgumentException] with a Persian message on bad input. */
        fun fromFen(fen: String): Board {
            val parts = fen.trim().split(Regex("\\s+"))
            require(parts.size >= 4) { "FEN ناقص است" }
            val b = Board()
            val rows = parts[0].split('/')
            require(rows.size == 8) { "FEN باید ۸ ردیف داشته باشد" }
            for ((i, row) in rows.withIndex()) {
                val r = 7 - i
                var f = 0
                for (c in row) {
                    if (c in '1'..'8') {
                        f += c - '0'
                    } else {
                        val p = Piece.fromFenChar(c)
                        require(p != 0) { "حرف نامعتبر در FEN: $c" }
                        require(f < 8) { "ردیف FEN بیش از ۸ خانه است" }
                        b.squares[Squares.of(f, r)] = p
                        f++
                    }
                }
                require(f == 8) { "ردیف FEN باید دقیقاً ۸ خانه باشد" }
            }
            b.side = when (parts[1]) {
                "w" -> Piece.WHITE
                "b" -> Piece.BLACK
                else -> throw IllegalArgumentException("نوبت در FEN باید w یا b باشد")
            }
            var c = 0
            if (parts[2] != "-") {
                for (ch in parts[2]) {
                    c = c or when (ch) {
                        'K' -> 1
                        'Q' -> 2
                        'k' -> 4
                        'q' -> 8
                        else -> throw IllegalArgumentException("حق قلعه در FEN نامعتبر است")
                    }
                }
            }
            b.castling = c
            b.epSquare = if (parts[3] == "-") -1 else Squares.parse(parts[3]).also {
                require(it >= 0) { "خانهٔ آن‌پاسان در FEN نامعتبر است" }
            }
            b.halfmoveClock = parts.getOrNull(4)?.toIntOrNull() ?: 0
            b.fullmoveNumber = (parts.getOrNull(5)?.toIntOrNull() ?: 1).coerceAtLeast(1)
            // Drop castling rights that the placement cannot support (keeps generator assumptions true).
            if (b.squares[4] != Piece.KING) b.castling = b.castling and 3.inv()
            if (b.squares[0] != Piece.ROOK) b.castling = b.castling and 2.inv()
            if (b.squares[7] != Piece.ROOK) b.castling = b.castling and 1.inv()
            if (b.squares[60] != -Piece.KING) b.castling = b.castling and 12.inv()
            if (b.squares[56] != -Piece.ROOK) b.castling = b.castling and 8.inv()
            if (b.squares[63] != -Piece.ROOK) b.castling = b.castling and 4.inv()
            return b
        }
    }
}
