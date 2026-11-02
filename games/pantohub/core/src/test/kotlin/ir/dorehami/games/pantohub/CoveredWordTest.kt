package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class CoveredWordTest {
    private val teams = listOf(
        Team("t1", "اول", 0xffd94f52.toInt(), listOf(Player("p1", "مینا"))),
        Team("t2", "دوم", 0xff3777c8.toInt(), listOf(Player("p2", "پویا"))),
    )

    private fun word(id: String) = WordRecord(
        id = id,
        text = "گربه",
        difficulty = Difficulty.MEDIUM,
        category = WordCategories.ANIMALS,
        type = PhraseType.WORD,
        validated = true,
    )

    private fun accepted(result: GameActionResult): GameState = (result as GameActionResult.Accepted).state

    @Test
    fun `word cannot be resolved before reveal but can be resolved after hiding in speedy and normal modes`() {
        var speedy = accepted(
            PantohubEngine.createGame(
                NewGameRequest("cov-speedy", "سرعتی", 1_000L, teams, GameSettings(playMode = PlayMode.SPEEDY)),
            ),
        )
        speedy = accepted(PantohubEngine.apply(speedy, GameAction.StartTurn(speedy.version, word("w1"), GameMoment(0L, 1_000L))))

        // Before reveal, ResolveWord is rejected.
        assertInstanceOf(
            GameActionResult.Rejected::class.java,
            PantohubEngine.apply(speedy, GameAction.ResolveWord(speedy.version, WordOutcome.CORRECT, word("w2"), GameMoment(100L, 1_100L))),
        )

        // Performer reveals and hides the word; guessing team then scores it while covered.
        speedy = accepted(PantohubEngine.apply(speedy, GameAction.RevealWord(speedy.version, GameMoment(200L, 1_200L))))
        assertEquals(true, speedy.currentTurn?.wordSeen)
        speedy = accepted(PantohubEngine.apply(speedy, GameAction.HideWord(speedy.version, GameMoment(400L, 1_400L))))
        assertEquals(false, speedy.currentTurn?.wordRevealed)
        assertEquals(true, speedy.currentTurn?.wordSeen)

        speedy = accepted(PantohubEngine.apply(speedy, GameAction.ResolveWord(speedy.version, WordOutcome.CORRECT, word("w2"), GameMoment(1_000L, 2_000L))))
        assertEquals(2, speedy.teamScores["t1"])
        assertEquals("w2", speedy.currentTurn?.currentWord?.id)
        // Next word starts unseen and unrevealed.
        assertEquals(false, speedy.currentTurn?.wordRevealed)
        assertEquals(false, speedy.currentTurn?.wordSeen)

        // Same flow in NORMAL mode closes the turn while covered.
        var normal = accepted(
            PantohubEngine.createGame(
                NewGameRequest("cov-normal", "عادی", 1_000L, teams, GameSettings(playMode = PlayMode.NORMAL)),
            ),
        )
        normal = accepted(PantohubEngine.apply(normal, GameAction.StartTurn(normal.version, word("n1"), GameMoment(0L, 1_000L))))
        normal = accepted(PantohubEngine.apply(normal, GameAction.RevealWord(normal.version, GameMoment(200L, 1_200L))))
        normal = accepted(PantohubEngine.apply(normal, GameAction.HideWord(normal.version, GameMoment(300L, 1_300L))))
        normal = accepted(PantohubEngine.apply(normal, GameAction.ResolveWord(normal.version, WordOutcome.CORRECT, null, GameMoment(900L, 1_900L))))
        assertEquals(GamePhase.READY, normal.phase)
        assertEquals(2, normal.teamScores["t1"])
    }

    @Test
    fun `process restart hides word for privacy but preserves wordSeen after resume`() {
        var state = accepted(
            PantohubEngine.createGame(
                NewGameRequest("cov-restart", "بازیابی", 1_000L, teams, GameSettings(playMode = PlayMode.NORMAL)),
            ),
        )
        state = accepted(PantohubEngine.apply(state, GameAction.StartTurn(state.version, word("r1"), GameMoment(0L, 1_000L))))
        state = accepted(PantohubEngine.apply(state, GameAction.RevealWord(state.version, GameMoment(500L, 1_500L))))

        val recovered = PantohubEngine.recoverAfterProcessRestart(state, moment = GameMoment(0L, 5_000L))
        assertEquals(GamePhase.PAUSED, recovered.phase)
        assertEquals(false, recovered.currentTurn?.wordRevealed)
        assertEquals(true, recovered.currentTurn?.wordSeen)

        val resumed = accepted(PantohubEngine.apply(recovered, GameAction.ResumeTurn(recovered.version, GameMoment(0L, 5_100L))))
        val scored = accepted(PantohubEngine.apply(resumed, GameAction.ResolveWord(resumed.version, WordOutcome.CORRECT, null, GameMoment(1_000L, 6_100L))))
        assertEquals(GamePhase.READY, scored.phase)
        assertEquals(2, scored.teamScores["t1"])
    }
}
