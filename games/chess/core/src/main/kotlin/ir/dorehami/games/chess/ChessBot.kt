package ir.dorehami.games.chess

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic chess bot: negamax + alpha-beta + quiescence, iterative deepening with a **node**
 * budget (never a time budget — wall time would make replays differ, ADR-0003). No RNG at all:
 * the same position and level always give the same move.
 */
object ChessBot {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 4

    /** Score of a mate at the root; mates further away score a little less. */
    const val MATE = 100_000

    /** Search settings per level (1 = beginner … 4 = strong club player on a phone). */
    data class Params(val depth: Int, val quiescence: Boolean, val nodeBudget: Long)

    fun params(level: Int): Params = when (level.coerceIn(MIN_LEVEL, MAX_LEVEL)) {
        1 -> Params(depth = 1, quiescence = false, nodeBudget = 40_000)
        2 -> Params(depth = 2, quiescence = true, nodeBudget = 120_000)
        3 -> Params(depth = 3, quiescence = true, nodeBudget = 300_000)
        else -> Params(depth = 5, quiescence = true, nodeBudget = 900_000)
    }

    /**
     * Best move for the side to move (packed [Move]), or `0` when there is no legal move.
     *
     * [seenPositions] are [Board.repetitionKey]s that already occurred in the game: a root move
     * that repeats one scores as a draw, so the bot avoids repetition when it is better and seeks
     * it when it is worse (instead of shuffling pieces forever).
     */
    fun chooseMove(board: Board, level: Int, seenPositions: Set<String> = emptySet()): Int {
        val p = params(level)
        return Search(board.copy(), p.nodeBudget, p.quiescence, seenPositions).bestMove(p.depth)
    }

    /** Search score from the side to move's point of view (used for draw-offer decisions). */
    fun score(board: Board, depth: Int = 2): Int =
        Search(board.copy(), 200_000, true).rootScore(depth)

    /** Static evaluation in centipawns from the side to move's point of view. */
    fun evaluate(board: Board): Int {
        val sq = board.squares
        var mg = 0
        var nonPawn = 0
        var whiteMat = 0
        var blackMat = 0
        var whitePawns = 0
        var blackPawns = 0
        var wk = -1
        var bk = -1
        for (s in 0 until 64) {
            val p = sq[s]
            if (p == 0) continue
            val t = Piece.type(p)
            val white = p > 0
            // Tables are written rank 8 first, from white's point of view.
            val idx = if (white) (7 - Squares.rank(s)) * 8 + Squares.file(s) else Squares.rank(s) * 8 + Squares.file(s)
            if (t == Piece.KING) {
                if (white) wk = s else bk = s
                continue
            }
            val v = VALUE[t] + PST[t][idx]
            if (t != Piece.PAWN) nonPawn += VALUE[t]
            if (white) {
                mg += v
                whiteMat += VALUE[t]
                if (t == Piece.PAWN) whitePawns++
            } else {
                mg -= v
                blackMat += VALUE[t]
                if (t == Piece.PAWN) blackPawns++
            }
        }
        // King: blend middle-game shelter and end-game centralisation by remaining material.
        val phase = min(nonPawn, FULL_MATERIAL) * 256 / FULL_MATERIAL
        var score = mg
        if (wk >= 0) {
            val i = (7 - Squares.rank(wk)) * 8 + Squares.file(wk)
            score += (KING_MG[i] * phase + KING_EG[i] * (256 - phase)) / 256
        }
        if (bk >= 0) {
            val i = Squares.rank(bk) * 8 + Squares.file(bk)
            score -= (KING_MG[i] * phase + KING_EG[i] * (256 - phase)) / 256
        }
        // Mating help: drive the lone/weaker king to the edge and bring our king closer.
        if (wk >= 0 && bk >= 0) {
            if (whiteMat - blackMat >= 400 && blackPawns == 0) score += mopUp(wk, bk)
            if (blackMat - whiteMat >= 400 && whitePawns == 0) score -= mopUp(bk, wk)
        }
        return score * board.side
    }

    private fun mopUp(strongKing: Int, weakKing: Int): Int {
        val wf = Squares.file(weakKing)
        val wr = Squares.rank(weakKing)
        val centre = max(3 - wf, wf - 4) + max(3 - wr, wr - 4)
        val dist = abs(Squares.file(strongKing) - wf) + abs(Squares.rank(strongKing) - wr)
        return centre * 10 + (14 - dist) * 4
    }

    private class Search(
        val board: Board,
        val budget: Long,
        val quiescence: Boolean,
        val seen: Set<String> = emptySet(),
    ) {
        var nodes = 0L
        var aborted = false
        val lists = Array(MAX_PLY) { MoveList(96) }
        val scores = Array(MAX_PLY) { IntArray(256) }

        fun bestMove(maxDepth: Int): Int {
            val root = board.legalMoves()
            if (root.isEmpty()) return 0
            if (root.size == 1) return root[0]
            orderMoves(root, IntArray(root.size.coerceAtLeast(1) * 2))
            // Root moves that walk back into an earlier position are scored as a draw.
            val repeats = HashSet<Int>()
            if (seen.isNotEmpty()) {
                for (i in 0 until root.size) {
                    board.makeMove(root[i])
                    if (board.repetitionKey() in seen) repeats += root[i]
                    board.unmakeMove()
                }
            }
            var best = root[0]
            for (depth in 1..maxDepth) {
                var iterBest = 0
                var alpha = -INF
                for (i in 0 until root.size) {
                    val m = root[i]
                    board.makeMove(m)
                    val s = if (m in repeats) 0 else -negamax(depth - 1, -INF, -alpha, 1)
                    board.unmakeMove()
                    if (aborted) break
                    if (s > alpha || iterBest == 0) {
                        alpha = max(alpha, s)
                        iterBest = m
                    }
                }
                if (aborted && depth > 1) break
                if (iterBest != 0) {
                    best = iterBest
                    // Search the previous best first next time (stable: others keep their order).
                    var idx = 0
                    while (root[idx] != best) idx++
                    while (idx > 0) {
                        root[idx] = root[idx - 1]
                        idx--
                    }
                    root[0] = best
                }
                if (aborted || alpha >= MATE - MAX_PLY) break
            }
            return best
        }

        fun rootScore(depth: Int): Int = negamax(depth, -INF, INF, 0)

        fun negamax(depthIn: Int, alphaIn: Int, beta: Int, ply: Int): Int {
            if (++nodes > budget && budget > 0) {
                aborted = true
                return 0
            }
            if (board.halfmoveClock >= 100) return 0
            val us = board.side
            val inCheck = board.inCheck(us)
            var depth = depthIn
            if (inCheck && ply < MAX_PLY / 2) depth++
            if (depth <= 0 || ply >= MAX_PLY - 1) {
                return if (quiescence) qsearch(alphaIn, beta, ply) else evaluate(board)
            }
            val list = lists[ply]
            list.clear()
            board.generatePseudo(list)
            orderMoves(list, scores[ply])
            var alpha = alphaIn
            var best = -INF
            var legal = 0
            for (i in 0 until list.size) {
                val m = list[i]
                board.makeMove(m)
                if (board.inCheck(us)) {
                    board.unmakeMove()
                    continue
                }
                legal++
                val s = -negamax(depth - 1, -beta, -alpha, ply + 1)
                board.unmakeMove()
                if (aborted) return 0
                if (s > best) best = s
                if (s > alpha) alpha = s
                if (alpha >= beta) break
            }
            if (legal == 0) return if (inCheck) -MATE + ply else 0
            return best
        }

        fun qsearch(alphaIn: Int, beta: Int, ply: Int): Int {
            if (++nodes > budget && budget > 0) {
                aborted = true
                return 0
            }
            val stand = evaluate(board)
            if (stand >= beta || ply >= MAX_PLY - 1) return stand
            var alpha = max(alphaIn, stand)
            val us = board.side
            val list = lists[ply]
            list.clear()
            board.generatePseudo(list, capturesOnly = true)
            orderMoves(list, scores[ply])
            for (i in 0 until list.size) {
                val m = list[i]
                board.makeMove(m)
                if (board.inCheck(us)) {
                    board.unmakeMove()
                    continue
                }
                val s = -qsearch(-beta, -alpha, ply + 1)
                board.unmakeMove()
                if (aborted) return 0
                if (s >= beta) return s
                if (s > alpha) alpha = s
            }
            return alpha
        }

        /** MVV-LVA for captures, promotions first; stable insertion sort keeps it deterministic. */
        fun orderMoves(list: MoveList, buf: IntArray) {
            val n = list.size
            val sc = if (buf.size >= n) buf else IntArray(n)
            for (i in 0 until n) {
                val m = list[i]
                var s = 0
                if (Move.isCapture(m)) {
                    val victim = if (Move.isEnPassant(m)) Piece.PAWN else Piece.type(board.squares[Move.to(m)])
                    val attacker = Piece.type(board.squares[Move.from(m)])
                    s = 10_000 + VALUE[victim] * 10 - attacker
                }
                if (Move.promotion(m) != 0) s += 9_000 + VALUE[Move.promotion(m)]
                sc[i] = s
            }
            for (i in 1 until n) {
                val m = list[i]
                val s = sc[i]
                var j = i - 1
                while (j >= 0 && sc[j] < s) {
                    list[j + 1] = list[j]
                    sc[j + 1] = sc[j]
                    j--
                }
                list[j + 1] = m
                sc[j + 1] = s
            }
        }
    }

    private const val INF = 1_000_000
    private const val MAX_PLY = 64

    private val VALUE = intArrayOf(0, 100, 320, 330, 500, 900, 0)
    private const val FULL_MATERIAL = 2 * (2 * 320 + 2 * 330 + 2 * 500 + 900)

    // Piece-square tables (Simplified Evaluation Function, T. Michniewski). Rank 8 first.
    private val PAWN = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0,
    )
    private val KNIGHT = intArrayOf(
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50,
    )
    private val BISHOP = intArrayOf(
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20,
    )
    private val ROOK = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 5, 5, 0, 0, 0,
    )
    private val QUEEN = intArrayOf(
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20,
    )
    private val KING_MG = intArrayOf(
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20,
    )
    private val KING_EG = intArrayOf(
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50,
    )
    private val PST = arrayOf(IntArray(64), PAWN, KNIGHT, BISHOP, ROOK, QUEEN, IntArray(64))
}
