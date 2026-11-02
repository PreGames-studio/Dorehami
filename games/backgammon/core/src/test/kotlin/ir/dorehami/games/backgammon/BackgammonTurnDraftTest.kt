package ir.dorehami.games.backgammon

import ir.dorehami.games.backgammon.BackgammonTestSupport.board
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The table's turn builder must stay inside exactly the sequences the authority accepts. */
class BackgammonTurnDraftTest {

    @Test
    fun `stacking both dice completes the turn`() {
        val draft = BackgammonTurnDraft(BackgammonPosition(), listOf(3, 1), isWhite = true)
        assertFalse(draft.isComplete())
        assertTrue(draft.sourcesFor(3).contains(23))
        assertNotNull(draft.play(23, 3))
        assertFalse(draft.isComplete(), "one die is still unspent")
        assertNotNull(draft.play(20, 1))
        assertTrue(draft.isComplete(), "no die-use is left, so the turn can be submitted")
        assertTrue(draft.isSubmittable())
        assertEquals(listOf(BackgammonMove(23, 3), BackgammonMove(20, 1)), draft.sequence())
    }

    @Test
    fun `an illegal pick is refused without changing the board`() {
        val draft = BackgammonTurnDraft(BackgammonPosition(), listOf(3, 1), isWhite = true)
        assertNull(draft.play(0, 3), "white does not own point one")
        assertNull(draft.play(23, 5), "that die was not rolled")
        assertEquals(BackgammonPosition(), draft.board)
        assertTrue(draft.sequence().isEmpty())
    }

    @Test
    fun `undo restores the previous board and the die`() {
        val draft = BackgammonTurnDraft(BackgammonPosition(), listOf(3, 1), isWhite = true)
        draft.play(23, 3)
        assertEquals(listOf(1), draft.diceLeft)
        draft.undo()
        assertEquals(BackgammonPosition(), draft.board)
        assertEquals(listOf(3, 1), draft.diceLeft)
        assertTrue(draft.sequence().isEmpty())
    }

    @Test
    fun `a single forced die-use is submittable`() {
        val position = board(17 to -2, barWhite = 1, offWhite = 14)
        val draft = BackgammonTurnDraft(position, listOf(2, 5), isWhite = true)
        draft.play(BackgammonRules.FROM_BAR, 5)
        assertTrue(draft.isComplete())
        assertTrue(draft.isSubmittable())

        val lower = BackgammonTurnDraft(position, listOf(2, 5), isWhite = true)
        lower.play(BackgammonRules.FROM_BAR, 2)
        assertTrue(lower.isComplete(), "the lower die leaves the same unusable second die")
        assertFalse(lower.isSubmittable(), "but the higher die was mandatory")
    }

    @Test
    fun `hits are visible before committing`() {
        val position = board(23 to 1, 20 to -1)
        val draft = BackgammonTurnDraft(position, listOf(3), isWhite = true)
        assertTrue(draft.hitsWith(23, 3))
        val safe = BackgammonTurnDraft(BackgammonPosition(), listOf(3), isWhite = true)
        assertFalse(safe.hitsWith(23, 3))
    }

    @Test
    fun `reset returns to the rolled board`() {
        val draft = BackgammonTurnDraft(BackgammonPosition(), listOf(6, 6, 6, 6), isWhite = true)
        draft.play(23, 6)
        draft.play(17, 6)
        draft.reset()
        assertEquals(BackgammonPosition(), draft.board)
        assertEquals(listOf(6, 6, 6, 6), draft.diceLeft)
    }
}
