package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TeamRotationTest {
    @Test
    fun `four teams rotate in configured order and each team advances its own players`() {
        val teams = (0 until 4).map { teamIndex ->
            Team(
                id = "team-$teamIndex",
                name = "تیم ${teamIndex + 1}",
                colorArgb = teamIndex,
                players = listOf(
                    Player("p-$teamIndex-1", "بازیکن ۱ تیم $teamIndex"),
                    Player("p-$teamIndex-2", "بازیکن ۲ تیم $teamIndex"),
                ),
            )
        }
        var state = (PantohubEngine.createGame(
            NewGameRequest("rotation", "چند تیمی", 1, teams, GameSettings(roundsPerTeam = 2, finalRoundEnabled = false)),
        ) as GameActionResult.Accepted).state

        for (round in 0..1) {
            for (teamIndex in teams.indices) {
                assertEquals("team-$teamIndex", PantohubEngine.currentTeam(state)!!.id)
                assertEquals("p-$teamIndex-${round + 1}", PantohubEngine.currentPlayer(state)!!.id)
                state = accept(state, GameAction.StartTurn(state.version, word("word-$round-$teamIndex"), moment(1_000 + (round * 10 + teamIndex) * 1_000L)))
                state = accept(state, GameAction.EndTurn(state.version, moment(1_500 + (round * 10 + teamIndex) * 1_000L)))
            }
        }
        assertEquals(GamePhase.FINISHED, state.phase)
        assertEquals(8, state.turns.size)
    }

    @Test
    fun `skip penalty and per-turn limit are enforced`() {
        val teams = listOf(
            Team("a", "الف", 1, listOf(Player("pa", "آوا"))),
            Team("b", "ب", 2, listOf(Player("pb", "باران"))),
        )
        var state = (PantohubEngine.createGame(
            NewGameRequest("skips", "رد واژه", 1, teams, GameSettings(skipPenalty = 2, maxSkipsPerTurn = 1)),
        ) as GameActionResult.Accepted).state
        state = accept(state, GameAction.StartTurn(state.version, word("one"), moment(1_000)))
        state = accept(state, GameAction.RevealWord(state.version, moment(1_100)))
        state = accept(state, GameAction.ResolveWord(state.version, WordOutcome.SKIPPED, word("two"), moment(1_200)))
        assertEquals(-2, state.teamScores.getValue("a"))
        state = accept(state, GameAction.RevealWord(state.version, moment(1_300)))
        val secondSkip = PantohubEngine.apply(
            state,
            GameAction.ResolveWord(state.version, WordOutcome.SKIPPED, null, moment(1_400)),
        )
        assertEquals(GameActionResult.Rejected("سهمیهٔ رد کردن واژه در این نوبت تمام شده است."), secondSkip)
        assertEquals(-2, state.teamScores.getValue("a"))
    }

    private fun accept(state: GameState, action: GameAction): GameState = when (val result = PantohubEngine.apply(state, action)) {
        is GameActionResult.Accepted -> result.state
        is GameActionResult.Rejected -> error(result.messageFa)
    }

    private fun word(id: String) = WordRecord(id, "گربه", Difficulty.EASY, WordCategories.ANIMALS, validated = true)
    private fun moment(elapsed: Long) = GameMoment(elapsed, 100_000 + elapsed)
}
