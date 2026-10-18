package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * «عادی» (NORMAL): one correct guess ends the turn, skips keep it going (PH-B01).
 *
 * The rule is enforced by the reducer itself, so a single correct [GameAction.ResolveWord] must
 * hand the phone to the next team — the screen used to follow it with an EndTurn built on a stale
 * version, which was rejected, and the turn never ended on the owner's phone. SPEEDY must keep
 * its old behaviour: correct guesses keep the turn going until the timer runs out.
 */
class NormalModeTest {
    @Test
    fun `a correct guess ends the NORMAL turn and hands over to the next team in one action`() {
        var state = newGame(PlayMode.NORMAL)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))
        val before = state

        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w2"), moment(2_000)))

        assertEquals(before.version + 1, state.version, "one action, one version bump")
        assertEquals(GamePhase.READY, state.phase)
        assertNull(state.currentTurn)
        assertEquals("blue", PantohubEngine.currentTeam(state)?.id)
        assertEquals(1, state.teamScores.getValue("red"))
        assertEquals(1, state.completedTurnsByTeam.getValue("red"))
        val record = state.turns.single()
        assertEquals(listOf("w1" to WordOutcome.CORRECT), record.attempts.map { it.wordId to it.outcome }, "no phantom unresolved attempt")
        assertEquals(1, record.scoreEarned)
        assertFalse("w2" in state.usedWordIds, "a next word is never reserved for a turn that is over")
    }

    @Test
    fun `a skip keeps the NORMAL turn going with the next word hidden`() {
        var state = newGame(PlayMode.NORMAL)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))

        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.SKIPPED, word("w2"), moment(2_000)))

        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals("w2", state.currentTurn!!.currentWord?.id)
        assertFalse(state.currentTurn!!.wordRevealed)
        assertEquals(1, state.currentTurn!!.skippedWords)
        assertTrue(state.turns.isEmpty())
    }

    @Test
    fun `skips then a correct guess end the NORMAL turn with both attempts recorded`() {
        var state = newGame(PlayMode.NORMAL)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.SKIPPED, word("w2", Difficulty.MEDIUM), moment(2_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(2_100)))

        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w3"), moment(3_000)))

        assertEquals(GamePhase.READY, state.phase)
        assertEquals("blue", PantohubEngine.currentTeam(state)?.id)
        assertEquals(
            listOf("w1" to WordOutcome.SKIPPED, "w2" to WordOutcome.CORRECT),
            state.turns.single().attempts.map { it.wordId to it.outcome },
        )
        assertEquals(2, state.teamScores.getValue("red"), "the medium word scores 2")
    }

    @Test
    fun `a correct guess keeps the SPEEDY turn going`() {
        var state = newGame(PlayMode.SPEEDY)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))

        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w2"), moment(2_000)))

        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals("w2", state.currentTurn!!.currentWord?.id)
        assertTrue("w2" in state.usedWordIds)
        assertEquals(1, state.teamScores.getValue("red"))
        assertTrue(state.turns.isEmpty())
    }

    @Test
    fun `NORMAL rounds rotate the teams and the last correct guess finishes the match`() {
        var state = newGame(PlayMode.NORMAL, roundsPerTeam = 2)
        val plays = listOf(
            Triple("red", "w1", Difficulty.EASY),
            Triple("blue", "w2", Difficulty.MEDIUM),
            Triple("red", "w3", Difficulty.EASY),
            Triple("blue", "w4", Difficulty.HARD),
        )
        plays.forEachIndexed { index, (team, id, difficulty) ->
            assertEquals(team, PantohubEngine.currentTeam(state)?.id, "turn ${index + 1}")
            state = playCorrectTurn(state, word(id, difficulty), at = 10_000L * (index + 1))
        }

        assertEquals(GamePhase.FINISHED, state.phase)
        assertEquals(listOf("blue"), state.winnerTeamIds)
        assertEquals(2, state.teamScores.getValue("red"))
        assertEquals(5, state.teamScores.getValue("blue"))
        assertEquals(4, state.turns.size)
        assertTrue(state.turns.all { turn -> turn.attempts.single().outcome == WordOutcome.CORRECT })
    }

    @Test
    fun `a correct guess ends each NORMAL final-round turn with the multiplier`() {
        var state = newGame(PlayMode.NORMAL, finalRoundEnabled = true)
        state = playCorrectTurn(state, word("w1"), at = 10_000)
        state = playCorrectTurn(state, word("w2"), at = 20_000)
        assertEquals(GamePhase.FINAL_READY, state.phase, "a tie after the regular rounds goes to the final round")
        assertEquals("red", PantohubEngine.currentTeam(state)?.id)

        state = playCorrectTurn(state, word("w3"), at = 30_000)
        assertEquals(GamePhase.FINAL_READY, state.phase, "the final turn ended on the guess")
        assertEquals("blue", PantohubEngine.currentTeam(state)?.id)
        assertEquals(1 + 2, state.teamScores.getValue("red"), "final round doubles the easy point")

        state = playCorrectTurn(state, word("w4", Difficulty.MEDIUM), at = 40_000)
        assertEquals(GamePhase.FINISHED, state.phase)
        assertEquals(listOf("blue"), state.winnerTeamIds)
        assertEquals(1 + 4, state.teamScores.getValue("blue"))
    }

    @Test
    fun `double points apply to the guess that ends the NORMAL turn`() {
        var state = newGame(PlayMode.NORMAL, cardsEnabled = true)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1", Difficulty.MEDIUM), moment(1_000)))
        state = accepted(state, GameAction.UseCard(state.version, SpecialCard.DOUBLE_POINTS, moment(1_050)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))

        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w2"), moment(2_000)))

        assertEquals(GamePhase.READY, state.phase)
        assertEquals(4, state.teamScores.getValue("red"))
        assertEquals(setOf(SpecialCard.DOUBLE_POINTS), state.usedCardsByTeam.getValue("red"))
    }

    @Test
    fun `scoredCorrect is true for a counted guess in both modes and false for a skip`() {
        for (mode in PlayMode.entries) {
            var state = newGame(mode)
            state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
            state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))
            val before = state

            val correct = accepted(before, GameAction.ResolveWord(before.version, WordOutcome.CORRECT, word("w2"), moment(2_000)))
            assertTrue(PantohubEngine.scoredCorrect(before, correct), "$mode correct guess counted")

            val skipped = accepted(before, GameAction.ResolveWord(before.version, WordOutcome.SKIPPED, word("w2"), moment(2_000)))
            assertFalse(PantohubEngine.scoredCorrect(before, skipped), "$mode skip is not a correct guess")
            assertFalse(PantohubEngine.scoredCorrect(before, before), "$mode unchanged state")
        }
    }

    @Test
    fun `a correct tap after the time ran out closes the turn without points and is not celebrated`() {
        for (mode in PlayMode.entries) {
            var state = newGame(mode)
            state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
            state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))
            val before = state

            // The turn is 30 s long; this tap arrives at 40 s.
            state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w2"), moment(40_000)))

            assertEquals(GamePhase.READY, state.phase, "$mode: the expired turn is closed")
            assertEquals(0, state.teamScores.getValue("red"), "$mode: no points after the deadline")
            assertEquals(WordOutcome.UNRESOLVED, state.turns.single().attempts.single().outcome)
            assertFalse(PantohubEngine.scoredCorrect(before, state), "$mode: no 'correct' tone for an answer that did not count")
        }
    }

    @Test
    fun `the follow-up EndTurn the old screen sent is stale once the guess ended the turn`() {
        // Documents the original failure: with the turn already closed by the guess, the extra
        // EndTurn built on the previous version is refused. The screen must not send it.
        var state = newGame(PlayMode.NORMAL)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))
        val beforeGuess = state.version
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, null, moment(2_000)))

        val stale = PantohubEngine.apply(state, GameAction.EndTurn(beforeGuess, moment(2_050)))
        assertInstanceOf(GameActionResult.Rejected::class.java, stale)
        assertEquals(GamePhase.READY, state.phase)
    }

    private fun playCorrectTurn(start: GameState, first: WordRecord, at: Long): GameState {
        var state = accepted(start, GameAction.StartTurn(start.version, first, moment(at)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(at + 100)))
        return accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, null, moment(at + 1_000)))
    }

    private fun newGame(
        playMode: PlayMode,
        roundsPerTeam: Int = 1,
        finalRoundEnabled: Boolean = false,
        cardsEnabled: Boolean = false,
    ): GameState {
        val request = NewGameRequest(
            id = "normal-mode",
            name = "دورهمی",
            createdAtEpochMs = 100,
            teams = listOf(
                Team("red", "قرمزها", 0xffff0000.toInt(), listOf(Player("r1", "رضا"))),
                Team("blue", "آبی‌ها", 0xff0000ff.toInt(), listOf(Player("b1", "سارا"))),
            ),
            settings = GameSettings(
                playMode = playMode,
                turnSeconds = 30,
                roundsPerTeam = roundsPerTeam,
                finalRoundEnabled = finalRoundEnabled,
                finalRoundSeconds = 30,
                cardsEnabled = cardsEnabled,
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
