package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * «حدس درست» on the shared (covered-word) screen — the owner's second report and PH-B05.
 *
 * The performer looks at the word and may cover it again («پوشاندن سریع واژه») while acting, or
 * the game pauses and covers it. The guess must still be recordable from the shared screen; those
 * buttons used to be permanently disabled because the engine only accepted a word that was on
 * screen at that very moment. A word nobody has seen still can be neither scored nor skipped.
 */
class CoveredWordTest {
    @Test
    fun `NORMAL - a correct guess on a covered word the performer has seen ends the turn`() {
        var state = newGame(PlayMode.NORMAL)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1", Difficulty.MEDIUM), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))
        assertTrue(state.currentTurn!!.wordSeen)
        state = accepted(state, GameAction.HideWord(state.version, moment(1_200)))
        assertFalse(state.currentTurn!!.wordRevealed)
        assertTrue(state.currentTurn!!.wordSeen, "covering the word keeps it seen")

        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, null, moment(2_000)))

        assertEquals(GamePhase.READY, state.phase)
        assertEquals("blue", PantohubEngine.currentTeam(state)?.id, "the second team is up")
        assertEquals(2, state.teamScores.getValue("red"), "a medium word is worth 2")
        assertEquals(2, state.turns.single().scoreEarned)
    }

    @Test
    fun `a word nobody has seen can be neither scored nor skipped`() {
        var state = newGame(PlayMode.NORMAL)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        assertFalse(state.currentTurn!!.wordSeen)

        for (outcome in listOf(WordOutcome.CORRECT, WordOutcome.SKIPPED)) {
            val result = PantohubEngine.apply(state, GameAction.ResolveWord(state.version, outcome, word("w2"), moment(1_500)))
            assertInstanceOf(GameActionResult.Rejected::class.java, result, "$outcome on an unseen word")
        }
        assertEquals(0, state.teamScores.getValue("red"))
    }

    @Test
    fun `SPEEDY - a covered seen word scores and the next word starts unseen`() {
        var state = newGame(PlayMode.SPEEDY)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))
        state = accepted(state, GameAction.HideWord(state.version, moment(1_200)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w2"), moment(2_000)))

        assertEquals(GamePhase.RUNNING, state.phase, "SPEEDY keeps the turn going")
        assertEquals(1, state.teamScores.getValue("red"))
        val turn = state.currentTurn!!
        assertEquals("w2", turn.currentWord?.id)
        assertFalse(turn.wordRevealed)
        assertFalse(turn.wordSeen, "the next word is new: nobody has seen it yet")
        val early = PantohubEngine.apply(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, word("w3"), moment(2_100)))
        assertInstanceOf(GameActionResult.Rejected::class.java, early, "the new word must be seen first")

        // A skip works from the covered screen too, once the word has been seen.
        state = accepted(state, GameAction.RevealWord(state.version, moment(2_200)))
        state = accepted(state, GameAction.HideWord(state.version, moment(2_300)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.SKIPPED, word("w3"), moment(2_400)))
        assertEquals(1, state.currentTurn!!.skippedWords)
        assertEquals("w3", state.currentTurn!!.currentWord?.id)
        assertFalse(state.currentTurn!!.wordSeen)
    }

    @Test
    fun `pausing covers the word but keeps it seen so the guess still counts after resuming`() {
        var state = newGame(PlayMode.NORMAL)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))
        state = accepted(state, GameAction.PauseTurn(state.version, moment(2_000)))
        assertFalse(state.currentTurn!!.wordRevealed, "a pause covers the word")
        assertTrue(state.currentTurn!!.wordSeen)

        state = accepted(state, GameAction.ResumeTurn(state.version, moment(10_000)))
        state = accepted(state, GameAction.ResolveWord(state.version, WordOutcome.CORRECT, null, moment(11_000)))

        assertEquals(GamePhase.READY, state.phase)
        assertEquals(1, state.teamScores.getValue("red"))
    }

    @Test
    fun `games saved before wordSeen existed still load and play`() {
        var state = newGame(PlayMode.NORMAL)
        state = accepted(state, GameAction.StartTurn(state.version, word("w1"), moment(1_000)))
        state = accepted(state, GameAction.RevealWord(state.version, moment(1_100)))
        val json = GameStateJsonCodec.encode(state)
        assertTrue("\"wordSeen\":true" in json)
        val legacy = json.replace(Regex(""","wordSeen":(true|false)"""), "")
        assertFalse("wordSeen" in legacy, "simulates a snapshot written by the previous version")

        var loaded = GameStateJsonCodec.decode(legacy)
        assertTrue(loaded.currentTurn!!.wordRevealed)
        assertFalse(loaded.currentTurn!!.wordSeen, "a missing field decodes to the safe default")
        // The word is on screen, so it can still be scored exactly as before.
        loaded = accepted(loaded, GameAction.ResolveWord(loaded.version, WordOutcome.CORRECT, null, moment(2_000)))
        assertEquals(GamePhase.READY, loaded.phase)
        assertEquals(1, loaded.teamScores.getValue("red"))
    }

    private fun newGame(playMode: PlayMode): GameState {
        val request = NewGameRequest(
            id = "covered-word",
            name = "دورهمی",
            createdAtEpochMs = 100,
            teams = listOf(
                Team("red", "قرمزها", 0xffff0000.toInt(), listOf(Player("r1", "رضا"))),
                Team("blue", "آبی‌ها", 0xff0000ff.toInt(), listOf(Player("b1", "سارا"))),
            ),
            settings = GameSettings(
                playMode = playMode,
                turnSeconds = 30,
                roundsPerTeam = 1,
                finalRoundEnabled = false,
                finalRoundSeconds = 30,
                cardsEnabled = false,
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
