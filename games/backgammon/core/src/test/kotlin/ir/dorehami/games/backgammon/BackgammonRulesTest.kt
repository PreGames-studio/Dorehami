package ir.dorehami.games.backgammon

import ir.dorehami.games.backgammon.BackgammonRules.FROM_BAR
import ir.dorehami.games.backgammon.BackgammonRules.TO_OFF
import ir.dorehami.games.backgammon.BackgammonTestSupport.board
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Contract test 1 — the rules: movement, bar, hitting, bearing off and the maximal-use rule. */
class BackgammonRulesTest {

    @Test
    fun `opening position is the standard board`() {
        val opening = BackgammonPosition()
        assertEquals(2, opening.points[23])
        assertEquals(5, opening.points[12])
        assertEquals(3, opening.points[7])
        assertEquals(5, opening.points[5])
        assertEquals(-2, opening.points[0])
        assertEquals(-5, opening.points[11])
        assertEquals(-3, opening.points[16])
        assertEquals(-5, opening.points[18])
        assertEquals(15, BackgammonRules.onBoardCount(opening, white = true))
        assertEquals(15, BackgammonRules.onBoardCount(opening, white = false))
        assertEquals(167, BackgammonRules.pipCount(opening, white = true))
        assertEquals(167, BackgammonRules.pipCount(opening, white = false))
    }

    @Test
    fun `white moves towards point one and black towards point twenty four`() {
        val opening = BackgammonPosition()
        val whiteMoves = BackgammonRules.legalMoves(opening, die = 3, white = true)
        assertTrue(whiteMoves.contains(BackgammonMove(23, 3)))
        assertTrue(whiteMoves.none { it.from < 0 })
        val blackMoves = BackgammonRules.legalMoves(opening, die = 3, white = false)
        assertTrue(blackMoves.contains(BackgammonMove(0, 3)))
        assertEquals(20, BackgammonRules.destination(BackgammonMove(23, 3), white = true))
        assertEquals(3, BackgammonRules.destination(BackgammonMove(0, 3), white = false))
    }

    @Test
    fun `a point with two rival checkers is blocked`() {
        val position = board(23 to 1, 20 to -2)
        assertTrue(BackgammonRules.legalMoves(position, die = 3, white = true).isEmpty())
        // A single rival checker is only a blot: it may be hit.
        val blot = board(23 to 1, 20 to -1)
        assertTrue(BackgammonRules.legalMoves(blot, die = 3, white = true).contains(BackgammonMove(23, 3)))
    }

    @Test
    fun `hitting a blot sends the rival checker to the bar`() {
        val position = board(23 to 2, 20 to -1)
        val after = BackgammonRules.apply(position, BackgammonMove(23, 3), white = true)
        assertEquals(1, after.barBlack)
        assertEquals(1, after.points[20])
        assertEquals(1, after.points[23])
    }

    @Test
    fun `a checker on the bar must enter the rival home board first`() {
        val position = board(barWhite = 1, offWhite = 14, barBlack = 0)
        assertEquals(listOf(BackgammonMove(FROM_BAR, 1)), BackgammonRules.legalMoves(position, die = 1, white = true))
        assertEquals(23, BackgammonRules.entryIndex(1, white = true))
        assertEquals(18, BackgammonRules.entryIndex(6, white = true))
        assertEquals(0, BackgammonRules.entryIndex(1, white = false))
        assertEquals(5, BackgammonRules.entryIndex(6, white = false))

        val entered = BackgammonRules.apply(position, BackgammonMove(FROM_BAR, 1), white = true)
        assertEquals(0, entered.barWhite)
        assertEquals(1, entered.points[23])
        assertFalse(BackgammonRules.legalMoves(entered, die = 1, white = true).isEmpty())
    }

    @Test
    fun `bearing off needs every checker inside the home board`() {
        val stillOut = board(0 to 13, 6 to 1, 10 to 1, offWhite = 0)
        assertFalse(BackgammonRules.allInHome(stillOut, white = true))
        assertTrue(BackgammonRules.legalMoves(stillOut, die = 6, white = true).none { it.from == 0 })

        val allHome = board(0 to 14, 5 to 1)
        assertTrue(BackgammonRules.allInHome(allHome, white = true))
        assertTrue(BackgammonRules.legalMoves(allHome, die = 6, white = true).contains(BackgammonMove(5, 6)))
        assertEquals(TO_OFF, BackgammonRules.destination(BackgammonMove(5, 6), white = true))
    }

    @Test
    fun `overshooting is only allowed when nothing is farther from home`() {
        val farChecker = board(2 to 1, 5 to 1)
        assertTrue(BackgammonRules.legalMoves(farChecker, die = 6, white = true).none { it.from == 2 })
        assertTrue(BackgammonRules.legalMoves(farChecker, die = 6, white = true).contains(BackgammonMove(5, 6)))

        val lastChecker = board(2 to 1)
        assertTrue(BackgammonRules.legalMoves(lastChecker, die = 6, white = true).contains(BackgammonMove(2, 6)))
    }

    @Test
    fun `both dice must be used when both can be played`() {
        val opening = BackgammonPosition()
        val sequences = BackgammonRules.sequences(opening, listOf(3, 1), white = true)
        assertTrue(sequences.isNotEmpty())
        assertTrue(sequences.all { it.size == 2 })
        assertTrue(sequences.none { it.size == 1 })
    }

    @Test
    fun `the higher die is mandatory when only one of the two can be played`() {
        // White has one checker on the bar and everything else borne off; black blocks point 18,
        // so after entering with either die the second die cannot be used.
        val position = board(
            17 to -2,
            barWhite = 1,
            offWhite = 14,
        )
        val sequences = BackgammonRules.sequences(position, listOf(2, 5), white = true)
        assertTrue(sequences.isNotEmpty())
        assertTrue(sequences.all { it.size == 1 })
        assertTrue(sequences.all { it.first().die == 5 }, "the higher die is the only legal single move")
    }

    @Test
    fun `doubles give four die uses`() {
        val position = board(0 to 1, 5 to 1)
        val sequences = BackgammonRules.sequences(position, listOf(2, 2, 2, 2), white = true)
        assertTrue(sequences.isNotEmpty())
        assertTrue(sequences.all { it.size == 4 })
    }

    @Test
    fun `a move may not come from a point the player does not own`() {
        val position = board(0 to -1)
        assertFalse(BackgammonRules.isLegal(position, BackgammonMove(0, 2), white = true))
    }

    @Test
    fun `game values are single gammon and backgammon`() {
        val single = board(offWhite = 15, offBlack = 3)
        assertEquals(1, BackgammonRules.gameValue(single, loserIsWhite = false))
        val gammon = board(12 to -8, offWhite = 15, offBlack = 0, barBlack = 0)
        assertEquals(2, BackgammonRules.gameValue(gammon, loserIsWhite = false))
        val backgammonBar = board(12 to -7, offWhite = 15, offBlack = 0, barBlack = 1)
        assertEquals(3, BackgammonRules.gameValue(backgammonBar, loserIsWhite = false))
        val backgammonHome = board(2 to -8, offWhite = 15, offBlack = 0)
        assertEquals(3, BackgammonRules.gameValue(backgammonHome, loserIsWhite = false))
    }
}
