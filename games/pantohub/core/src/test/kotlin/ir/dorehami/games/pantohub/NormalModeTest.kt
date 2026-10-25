package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NormalModeTest {
    private val teams = listOf(
        Team("t1", "شیرها", 0xffd94f52.toInt(), listOf(Player("p1", "سارا"), Player("p2", "امیر"))),
        Team("t2", "پلنگ‌ها", 0xff3777c8.toInt(), listOf(Player("p3", "زهرا"), Player("p4", "کیوان"))),
    )

    private fun word(id: String, difficulty: Difficulty, category: String = WordCategories.CINEMA) = WordRecord(
        id = id,
        text = "واژهٔ نمایشی",
        difficulty = difficulty,
        category = category,
        type = PhraseType.WORD,
        validated = true,
    )

    private fun accepted(result: GameActionResult): GameState = (result as GameActionResult.Accepted).state

    @Test
    fun `normal mode closes turn on first correct guess in single version step without burning next word`() {
        var state = accepted(
            PantohubEngine.createGame(
                NewGameRequest(
                    id = "normal-1",
                    name = "بازی عادی",
                    createdAtEpochMs = 1_000L,
                    teams = teams,
                    settings = GameSettings(playMode = PlayMode.NORMAL, roundsPerTeam = 1, finalRoundEnabled = false),
                ),
            ),
        )

        val chosen = word("hard-1", Difficulty.HARD, "cinema")
        val unusedCandidate = word("hard-2", Difficulty.HARD, "cinema")

        state = accepted(PantohubEngine.apply(state, GameAction.StartTurn(state.version, chosen, GameMoment(0L, 1_000L))))
        state = accepted(PantohubEngine.apply(state, GameAction.RevealWord(state.version, GameMoment(500L, 1_500L))))
        val versionBeforeResolve = state.version

        state = accepted(
            PantohubEngine.apply(
                state,
                GameAction.ResolveWord(state.version, WordOutcome.CORRECT, unusedCandidate, GameMoment(4_000L, 5_000L)),
            ),
        )

        assertEquals(versionBeforeResolve + 1, state.version)
        assertEquals(GamePhase.READY, state.phase)
        assertNull(state.currentTurn)
        assertEquals(3, state.teamScores["t1"])
        assertEquals(setOf("hard-1"), state.usedWordIds)
        assertEquals(listOf(WordOutcome.CORRECT), state.turns.single().attempts.map { it.outcome })
        assertEquals("t2", PantohubEngine.currentTeam(state)?.id)

        // Team 2 plays an EASY word and wins 1 point -> match finishes cleanly with Team 1 winning 3 to 1.
        val easyWord = word("easy-1", Difficulty.EASY, "jobs")
        state = accepted(PantohubEngine.apply(state, GameAction.StartTurn(state.version, easyWord, GameMoment(5_000L, 6_000L))))
        state = accepted(PantohubEngine.apply(state, GameAction.RevealWord(state.version, GameMoment(5_500L, 6_500L))))
        state = accepted(PantohubEngine.apply(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, null, GameMoment(9_000L, 10_000L))))

        assertEquals(GamePhase.FINISHED, state.phase)
        assertEquals(mapOf("t1" to 3, "t2" to 1), state.teamScores)
        assertEquals(listOf("t1"), state.winnerTeamIds)
    }

    @Test
    fun `normal mode keeps turn running on skip and ends turn on timer expiry`() {
        var state = accepted(
            PantohubEngine.createGame(
                NewGameRequest(
                    id = "normal-skip-timeout",
                    name = "عادی با رد",
                    createdAtEpochMs = 1_000L,
                    teams = teams,
                    settings = GameSettings(
                        playMode = PlayMode.NORMAL,
                        turnSeconds = 30,
                        roundsPerTeam = 1,
                        skipPenalty = 1,
                        finalRoundEnabled = false,
                    ),
                ),
            ),
        )

        val w1 = word("w1", Difficulty.MEDIUM)
        val w2 = word("w2", Difficulty.MEDIUM)

        state = accepted(PantohubEngine.apply(state, GameAction.StartTurn(state.version, w1, GameMoment(0L, 1_000L))))
        state = accepted(PantohubEngine.apply(state, GameAction.RevealWord(state.version, GameMoment(1_000L, 2_000L))))
        state = accepted(PantohubEngine.apply(state, GameAction.ResolveWord(state.version, WordOutcome.SKIPPED, w2, GameMoment(5_000L, 6_000L))))

        assertEquals(GamePhase.RUNNING, state.phase)
        assertEquals("w2", state.currentTurn?.currentWord?.id)
        assertEquals(false, state.currentTurn?.wordRevealed)
        assertEquals(false, state.currentTurn?.wordSeen)
        assertEquals(-1, state.teamScores["t1"])

        // Timer expires at 30_000ms -> turn closes automatically and advances to Team 2.
        state = accepted(PantohubEngine.apply(state, GameAction.Tick(state.version, GameMoment(30_000L, 31_000L))))
        assertEquals(GamePhase.READY, state.phase)
        assertNull(state.currentTurn)
        assertEquals("t2", PantohubEngine.currentTeam(state)?.id)
        assertTrue(state.turns.single().attempts.any { it.wordId == "w2" && it.outcome == WordOutcome.UNRESOLVED })
    }

    @Test
    fun `per turn category and difficulty filter selects matching word for normal mode`() {
        val bank = listOf(
            word("c-easy", Difficulty.EASY, "cinema"),
            word("c-hard", Difficulty.HARD, "cinema"),
            word("s-hard", Difficulty.HARD, "sports"),
        )
        val baseSettings = GameSettings(
            playMode = PlayMode.NORMAL,
            allowedDifficulties = Difficulty.entries.toSet(),
            allowedCategories = emptySet(),
        )
        val turnSettings = baseSettings.copy(
            allowedDifficulties = setOf(Difficulty.HARD),
            allowedCategories = setOf("cinema"),
        )
        val eligible = WordBankRules.eligibleWords(bank, turnSettings, usedWordIds = emptySet())
        assertEquals(listOf("c-hard"), eligible.map { it.id })
    }
}
