package ir.dorehami.games.chess.ui.gl

import kotlin.math.abs

/**
 * What the 3D board should show. Built by the screen from the player's view; the renderer never
 * sees the game session. Squares use the rules' numbering (`a1 = 0 … h8 = 63`) and piece codes
 * (`+1..+6` white pawn..king, negative for black).
 */
data class BoardScene(
    val squares: IntArray,
    val selected: Int = -1,
    val quietTargets: Set<Int> = emptySet(),
    val captureTargets: Set<Int> = emptySet(),
    val lastFrom: Int = -1,
    val lastTo: Int = -1,
    val checkSquare: Int = -1,
    /** How the last change happened, so it can be animated (null = jump). */
    val anim: MoveAnim? = null,
    /** Changes whenever the position changes; an animation plays once per id. */
    val positionId: Long = 0,
) {
    override fun equals(other: Any?): Boolean = other is BoardScene &&
        squares.contentEquals(other.squares) && selected == other.selected &&
        quietTargets == other.quietTargets && captureTargets == other.captureTargets &&
        lastFrom == other.lastFrom && lastTo == other.lastTo && checkSquare == other.checkSquare &&
        anim == other.anim && positionId == other.positionId

    override fun hashCode(): Int = squares.contentHashCode() * 31 + positionId.hashCode()

    companion object {
        fun squareX(sq: Int): Float = (sq and 7) - 3.5f

        fun squareZ(sq: Int): Float = 3.5f - (sq shr 3)

        /** Square under the world point (x, z) on the board top, or -1. */
        fun squareAt(x: Float, z: Float): Int {
            val f = kotlin.math.floor(x + 4f).toInt()
            val r = kotlin.math.floor(4f - z).toInt()
            return if (f in 0..7 && r in 0..7) r * 8 + f else -1
        }
    }
}

/** One move to animate: the mover, an optional victim and the rook of a castle. */
data class MoveAnim(
    val from: Int,
    val to: Int,
    /** Piece code that left [from] (a pawn for promotions). */
    val moved: Int,
    val captured: Int = 0,
    val captureSquare: Int = -1,
    val rookFrom: Int = -1,
    val rookTo: Int = -1,
) {
    companion object {
        /**
         * Works out the animation of [uci] (`e2e4`, `e7e8q`) played on [before].
         * Returns null if the text does not describe a move of a piece on [before].
         */
        fun of(before: IntArray, uci: String): MoveAnim? {
            if (uci.length < 4) return null
            val from = parse(uci, 0)
            val to = parse(uci, 2)
            if (from < 0 || to < 0) return null
            val moved = before[from]
            if (moved == 0) return null
            val type = abs(moved)
            var captured = before[to]
            var captureSquare = if (captured != 0) to else -1
            // En passant: a pawn moving diagonally onto an empty square.
            if (type == 1 && (from and 7) != (to and 7) && captured == 0) {
                val victim = to - 8 * (if (moved > 0) 1 else -1)
                if (victim in 0..63 && before[victim] == -moved) {
                    captured = before[victim]
                    captureSquare = victim
                }
            }
            var rookFrom = -1
            var rookTo = -1
            if (type == 6 && abs((to and 7) - (from and 7)) == 2) {
                val base = from and 0x38
                if ((to and 7) == 6) { rookFrom = base + 7; rookTo = base + 5 } else { rookFrom = base; rookTo = base + 3 }
            }
            return MoveAnim(from, to, moved, captured, captureSquare, rookFrom, rookTo)
        }

        private fun parse(s: String, i: Int): Int {
            val f = s[i] - 'a'
            val r = s[i + 1] - '1'
            return if (f in 0..7 && r in 0..7) r * 8 + f else -1
        }
    }
}
