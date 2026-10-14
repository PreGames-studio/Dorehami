package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Plays one complete match exactly the way the UI drives the engine:
 * start turn → reveal word → resolve while the word is revealed → next word starts
 * hidden → end turn → rotate teams → finish with a winner. Guards the regression in
 * which the Correct/Skip buttons could never be used from the game screen.
 */
class FullGameFlowTest {
    @Test
    fun `resolving a hidden word is rejected so the UI must score from the revealed screen`() {
        var state = newGame()
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))

        val rejected = PantohubEngine.apply(
            state,
            GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w2"), moment(1_500)),
        )
        assertInstanceOf(GameActionResult.Rejected::class.java, rejected)
        assertEquals(0, state.teamScores.getValue("red"))
    }

    @Test
    fun `a complete match scores every word and finishes with a winner`() {
        var state = newGame()
        assertEquals("red", PantohubEngine.currentTeam(state)?.id)

        // Team red: correct (easy, 1), skip (0 penalty), correct (medium, 2) = 3 points.
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w2"), moment(2_000)))
        assertEquals(1, state.teamScores.getValue("red"))
        assertFalse(state.currentTurn!!.wordRevealed, "next word must start hidden")
        assertEquals("w2", state.currentTurn!!.currentWord?.id)

        state = accepted(state, GameAction.RevealWord(state.version, moment(2_100)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.SKIPPED, word("w3", Difficulty.MEDIUM), moment(3_000)))
        assertEquals(1, state.teamScores.getValue("red"))
        assertEquals(1, state.currentTurn!!.skippedWords)

        state = accepted(state, GameAction.RevealWord(state.version, moment(3_100)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, null, moment(4_000)))
        assertEquals(3, state.teamScores.getValue("red"))

        state = accepted(state, GameAction.EndTurn(state.version, moment(4_500)))
        assertEquals(GamePhase.READY, state.phase)
        assertEquals("blue", PantohubEngine.currentTeam(state)?.id)
        assertEquals(3, state.turns.single().attempts.size)

        // Team blue: one correct, then the 30s timer expires and closes the turn.
        state = accepted(state, GameAction.StartTurn(state.version, word("w4"), moment(5_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(5_100)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w5"), moment(6_000)))
        assertEquals(1, state.teamScores.getValue("blue"))

        state = accepted(state, GameAction.Tick(state.version, moment(35_500)))
        assertEquals(GamePhase.FINISHED, state.phase)
        assertEquals(listOf("red"), state.winnerTeamIds)
        assertEquals(3, state.teamScores.getValue("red"))
        assertEquals(1, state.teamScores.getValue("blue"))
        assertTrue(state.turns.last().attempts.any { it.outcome == WordOutcome.UNRESOLVED })
    }

    private fun newGame(): GameState {
        val request = NewGameRequest(
            id = "full-flow",
            name = "دورهمی",
            createdAtEpochMs = 100,
            teams = listOf(
                Team("red", "قرمزها", 0xffff0000.toInt(), listOf(Player("r1", "رضا"))),
                Team("blue", "آبی‌ها", 0xff0000ff.toInt(), listOf(Player("b1", "سارا"))),
            ),
            settings = GameSettings(
                turnSeconds = 30,
                roundsPerTeam = 1,
                finalRoundEnabled = false,
            ),
        )
        return (PantohubEngine.createGame(request) as GameActionResult.Accepted).state
    }

    private fun accepted(state: GameState, action: GameAction): GameState = when (val result = PantohubEngine.apply(state, action)) {
        is GameActionResult.Accepted -> result.state
        is GameActionResult.Rejected -> error("Unexpected rejection: ${result.messageFa}")
    }

    private fun word(id: String, difficulty: Difficulty = Difficulty.EASY) = WordRecord(
        id = id,
        text = when (difficulty) {
            Difficulty.EASY -> "گربه"
            Difficulty.MEDIUM -> "مسواک زدن"
            Difficulty.HARD -> "از کاه کوه ساختن"
        },
        difficulty = difficulty,
        category = WordCategories.ANIMALS,
        type = if (difficulty == Difficulty.HARD) PhraseType.IDIOM else PhraseType.WORD,
        validated = true,
    )

    private fun moment(elapsed: Long, epoch: Long = 100_000 + elapsed) = GameMoment(elapsed, epoch)
}
