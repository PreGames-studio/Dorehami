package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class WordPrivacyTest {
    @Test
    fun `word is hidden by default can be covered without pausing and is not scored`() {
        var state = (PantohubEngine.createGame(
            NewGameRequest(
                "privacy-1",
                "بازی",
                1,
                listOf(
                    Team("a", "الف", 1, listOf(Player("pa", "آوا"))),
                    Team("b", "ب", 2, listOf(Player("pb", "باران"))),
                ),
                GameSettings(),
            ),
        ) as GameActionResult.Accepted).state
        state = accept(state, GameAction.StartTurn(state.version, word(), GameMoment(1_000, 10_000)))
        assertFalse(state.currentTurn!!.wordRevealed)

        state = accept(state, GameAction.RevealWord(state.version, GameMoment(2_000, 11_000)))
        assertEquals(true, state.currentTurn!!.wordRevealed)
        state = accept(state, GameAction.HideWord(state.version, GameMoment(3_000, 12_000)))
        assertFalse(state.currentTurn!!.wordRevealed)
        assertEquals(0, state.teamScores.getValue("a"))
        assertEquals(GamePhase.RUNNING, state.phase)
    }

    private fun accept(state: GameState, action: GameAction): GameState = when (val result = PantohubEngine.apply(state, action)) {
        is GameActionResult.Accepted -> result.state
        is GameActionResult.Rejected -> error(result.messageFa)
    }

    private fun word() = WordRecord(
        id = "secret",
        text = "گربه",
        difficulty = Difficulty.EASY,
        category = WordCategories.ANIMALS,
        validated = true,
    )
}
