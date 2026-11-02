package ir.dorehami.games.backgammon.ui

import ir.dorehami.games.backgammon.BackgammonPosition
import ir.dorehami.games.backgammon.BackgammonRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The board animation is driven by a diff of two positions (the engine only publishes whole boards).
 * Every shape of move has to be recognised, otherwise a checker would teleport instead of sliding.
 */
class BackgammonMoveTraceTest {

    private fun empty(): IntArray = IntArray(BackgammonRules.POINT_COUNT)

    private fun position(
        points: IntArray,
        barWhite: Int = 0,
        barBlack: Int = 0,
        offWhite: Int = 0,
        offBlack: Int = 0,
    ) = BackgammonPosition(points.toList(), barWhite, barBlack, offWhite, offBlack)

    @Test
    fun `no change means no animation`() {
        val board = position(empty().also { it[5] = 3 })
        assertNull(diffCheckerMove(board, board))
    }

    @Test
    fun `a white point-to-point move is traced`() {
        val before = empty().also { it[10] = 2 }
        val after = empty().also {
            it[10] = 1
            it[7] = 1
        }
        assertEquals(CheckerTrace(from = 10, to = 7, white = true), diffCheckerMove(position(before), position(after)))
    }

    @Test
    fun `a black move is traced in its own direction`() {
        val before = empty().also { it[3] = -2 }
        val after = empty().also {
            it[3] = -1
            it[8] = -1
        }
        assertEquals(CheckerTrace(from = 3, to = 8, white = false), diffCheckerMove(position(before), position(after)))
    }

    @Test
    fun `entering from the bar starts at FROM_BAR`() {
        val before = empty().also { it[20] = 1 }
        val after = empty().also {
            it[20] = 2
        }
        assertEquals(
            CheckerTrace(from = BackgammonRules.FROM_BAR, to = 20, white = true),
            diffCheckerMove(position(before, barWhite = 1), position(after, barWhite = 0)),
        )
    }

    @Test
    fun `bearing off ends at TO_OFF`() {
        val before = empty().also { it[1] = 1 }
        val after = empty()
        assertEquals(
            CheckerTrace(from = 1, to = BackgammonRules.TO_OFF, white = true),
            diffCheckerMove(position(before, offWhite = 14), position(after, offWhite = 15)),
        )
    }

    @Test
    fun `a hit still traces the moving checker`() {
        // White leaves point 6 and hits a lone black checker there; black goes to the bar.
        val before = empty().also {
            it[5] = 2
            it[8] = -1
        }
        val after = empty().also {
            it[5] = 1
            it[8] = -2
        }
        assertEquals(
            CheckerTrace(from = 5, to = 8, white = true),
            diffCheckerMove(position(before), position(after, barBlack = 1)),
        )
    }
}
