package ir.dorehami.games.pantohub.ui

import ir.dorehami.games.pantohub.GamePhase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The «درست / رد» buttons used to be painted on the public screen, where the engine refuses to
 * record anything until the word is revealed — so they were greyed out for the whole turn. These
 * tests pin the rule the UI now follows.
 */
class TurnFlowTest {

    @Test
    fun `an outcome can only be registered while the word is on the performer's screen`() {
        assertTrue(canRegisterOutcome(GamePhase.RUNNING, wordRevealed = true, hasWord = true))
        assertFalse(canRegisterOutcome(GamePhase.RUNNING, wordRevealed = false, hasWord = true))
        assertFalse(canRegisterOutcome(GamePhase.RUNNING, wordRevealed = true, hasWord = false))
        assertFalse(canRegisterOutcome(GamePhase.PAUSED, wordRevealed = true, hasWord = true))
        assertFalse(canRegisterOutcome(GamePhase.FINISHED, wordRevealed = true, hasWord = true))
    }

    @Test
    fun `the next word is revealed straight away for the performer`() {
        assertTrue(shouldAutoRevealNextWord(GamePhase.RUNNING, wordRevealed = false, hasWord = true, wanted = true))
        // Already visible: nothing to do, so no second reveal (which the engine would reject).
        assertFalse(shouldAutoRevealNextWord(GamePhase.RUNNING, wordRevealed = true, hasWord = true, wanted = true))
        // Out of words, paused or finished: stay on the public screen.
        assertFalse(shouldAutoRevealNextWord(GamePhase.RUNNING, wordRevealed = false, hasWord = false, wanted = true))
        assertFalse(shouldAutoRevealNextWord(GamePhase.PAUSED, wordRevealed = false, hasWord = true, wanted = true))
        assertFalse(shouldAutoRevealNextWord(GamePhase.FINISHED, wordRevealed = false, hasWord = true, wanted = true))
        // Nobody asked for it.
        assertFalse(shouldAutoRevealNextWord(GamePhase.RUNNING, wordRevealed = false, hasWord = true, wanted = false))
    }
}
