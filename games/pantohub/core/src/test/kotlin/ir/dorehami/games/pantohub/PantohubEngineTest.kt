package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PantohubEngineTest {
    @Test
    fun `correct answer scores once and stale duplicate action is rejected`() {
        var state = newGame(rounds = 1)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_500)))
        val beforeResolve = state
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w2"), moment(5_000)))

        assertEquals(1, state.teamScores.getValue("red"))
        assertEquals(WordOutcome.CORRECT, state.currentTurn!!.attempts.single().outcome)
        val repeated = PantohubEngine.apply(
            state,
            GameAction.ResolveWord(beforeResolve.version, WordOutcome.CORRECT, word("w2"), moment(5_001)),
        )
        assertInstanceOf(GameActionResult.Rejected::class.java, repeated)
        assertEquals(1, state.teamScores.getValue("red"))
    }

    @Test
    fun `same display name on different teams is accepted when player identities differ`() {
        val request = NewGameRequest(
            id = "same-name-match",
            name = "دورهمی",
            createdAtEpochMs = 100,
            teams = listOf(
                Team("red", "قرمزها", 0xffff0000.toInt(), listOf(Player("red-player", "رضا"))),
                Team("blue", "آبی‌ها", 0xff0000ff.toInt(), listOf(Player("blue-player", "رضا"))),
            ),
            settings = GameSettings(),
        )

        val result = PantohubEngine.createGame(request)

        assertInstanceOf(GameActionResult.Accepted::class.java, result)
        val state = (result as GameActionResult.Accepted).state
        assertEquals("رضا", state.teams[0].players.single().name)
        assertEquals("رضا", state.teams[1].players.single().name)
    }

    @Test
    fun `same player identity is still rejected when assigned to multiple teams`() {
        val request = NewGameRequest(
            id = "duplicate-player-id-match",
            name = "دورهمی",
            createdAtEpochMs = 100,
            teams = listOf(
                Team("red", "قرمزها", 0xffff0000.toInt(), listOf(Player("shared-player", "رضا"))),
                Team("blue", "آبی‌ها", 0xff0000ff.toInt(), listOf(Player("shared-player", "رضا"))),
            ),
            settings = GameSettings(),
        )

        val result = PantohubEngine.createGame(request)

        assertInstanceOf(GameActionResult.Rejected::class.java, result)
        assertEquals(
            "شناسهٔ بازیکن تکراری است؛ هر جایگاه بازیکن باید شناسهٔ جداگانه داشته باشد.",
            (result as GameActionResult.Rejected).messageFa,
        )
    }

    @Test
    fun `timeout closes the turn and records an unresolved word exactly once`() {
        var state = newGame(rounds = 1)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_500)))
        state = accepted(state, GameAction.Tick(state.version, moment(31_000)))

        assertEquals(GamePhase.READY, state.phase)
        assertEquals(1, state.turns.size)
        assertEquals(WordOutcome.UNRESOLVED, state.turns.single().attempts.single().outcome)
        assertEquals("blue", PantohubEngine.currentTeam(state)!!.id)
        val duplicateTick = PantohubEngine.apply(state, GameAction.Tick(state.version, moment(99_000)))
        assertEquals(state, (duplicateTick as GameActionResult.Accepted).state)
    }

    @Test
    fun `pause and resume preserve the actual remaining duration`() {
        var state = newGame()
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_500)))
        state = accepted(state, GameAction.PauseTurn(state.version, moment(5_000)))
        assertEquals(GamePhase.PAUSED, state.phase)
        assertEquals(26_000L, state.currentTurn!!.remainingMs)
        assertFalse(state.currentTurn!!.wordRevealed)

        state = accepted(state, GameAction.ResumeTurn(state.version, moment(20_000)))
        assertEquals(46_000L, state.currentTurn!!.deadlineElapsedMs)
        state = accepted(state, GameAction.Tick(state.version, moment(45_999)))
        assertEquals(GamePhase.RUNNING, state.phase)
        state = accepted(state, GameAction.Tick(state.version, moment(46_000)))
        assertEquals(GamePhase.READY, state.phase)
    }

    @Test
    fun `process restart pauses the turn and covers the previously revealed word`() {
        var state = newGame()
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(2_000)))
        assertTrue(state.currentTurn!!.wordRevealed)

        state = PantohubEngine.recoverAfterProcessRestart(state, moment(5_000))

        assertEquals(GamePhase.PAUSED, state.phase)
        assertFalse(state.currentTurn!!.wordRevealed)
        assertEquals(26_000L, state.currentTurn!!.remainingMs)
    }

    @Test
    fun `free skip and double points cards are one-use and score according to rules`() {
        var state = newGame(settings = GameSettings(cardsEnabled = true, skipPenalty = 2, maxSkipsPerTurn = 2))
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_500)))
        state = accepted(state, GameAction.UseCard(state.version, SpecialCard.FREE_SKIP, moment(2_000)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.SKIPPED, word("w2"), moment(3_000)))
        assertEquals(0, state.teamScores.getValue("red"))
        assertFalse(SpecialCard.FREE_SKIP in state.currentTurn!!.activeCards)

        state = accepted(state, GameAction.UseCard(state.version, SpecialCard.DOUBLE_POINTS, moment(4_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(4_500)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, null, moment(5_000)))
        assertEquals(2, state.teamScores.getValue("red"))
        assertInstanceOf(
            GameActionResult.Rejected::class.java,
            PantohubEngine.apply(state, GameAction.UseCard(state.version, SpecialCard.DOUBLE_POINTS, moment(6_000))),
        )
    }

    @Test
    fun `tied leaders receive a final round and final scores determine the winner`() {
        val settings = GameSettings(roundsPerTeam = 1, finalRoundEnabled = true)
        var state = newGame(settings = settings)
        state = playOneCorrectTurn(state, word("red-1", Difficulty.EASY))
        state = accepted(state, GameAction.StartTurn(state.version, word("blue-1", Difficulty.EASY), moment(10_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(10_100)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, null, moment(11_000)))
        state = accepted(state, GameAction.EndTurn(state.version, moment(12_000)))
        assertEquals(GamePhase.FINAL_READY, state.phase)
        assertEquals(listOf("red", "blue"), state.finalRoundTeamIds)

        state = accepted(state, GameAction.StartTurn(state.version, word("red-final", Difficulty.HARD), moment(20_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(20_100)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, null, moment(21_000)))
        state = accepted(state, GameAction.EndTurn(state.version, moment(22_000)))
        state = accepted(state, GameAction.StartTurn(state.version, word("blue-final", Difficulty.MEDIUM), moment(30_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(30_100)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, null, moment(31_000)))
        state = accepted(state, GameAction.EndTurn(state.version, moment(32_000)))

        assertEquals(GamePhase.FINISHED, state.phase)
        assertEquals(listOf("red"), state.winnerTeamIds)
        assertEquals(7, state.teamScores.getValue("red"))
        assertEquals(5, state.teamScores.getValue("blue"))
    }

    @Test
    fun `unselected difficulty and repeated word are rejected`() {
        val settings = GameSettings(allowedDifficulties = setOf(Difficulty.EASY))
        var state = newGame(settings = settings)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_500)))
        val repeated = PantohubEngine.apply(
            state,
            GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w1"), moment(2_000)),
        )
        assertInstanceOf(GameActionResult.Rejected::class.java, repeated)
        assertEquals(0, state.teamScores.getValue("red"))
    }

    private fun playOneCorrectTurn(start: GameState, next: WordRecord): GameState {
        var state = accepted(start, GameAction.StartTurn(start.version, word("first-red"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_500)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, next, moment(2_000)))
        return accepted(state, GameAction.EndTurn(state.version, moment(3_000)))
    }

    private fun newGame(
        rounds: Int = 3,
        settings: GameSettings = GameSettings(roundsPerTeam = rounds),
    ): GameState {
        val request = NewGameRequest(
            id = "match-1",
            name = "دورهمی",
            createdAtEpochMs = 100,
            teams = listOf(
                Team("red", "قرمزها", 0xffff0000.toInt(), listOf(Player("r1", "رضا"))),
                Team("blue", "آبی‌ها", 0xff0000ff.toInt(), listOf(Player("b1", "سارا"))),
            ),
            settings = settings.copy(turnSeconds = 30, finalRoundSeconds = 30),
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
