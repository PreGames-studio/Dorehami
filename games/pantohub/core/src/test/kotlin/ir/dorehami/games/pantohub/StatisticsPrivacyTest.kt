package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StatisticsPrivacyTest {
    @Test
    fun `engine omits player identity from saved turns when individual stats are disabled`() {
        var state = (PantohubEngine.createGame(
            NewGameRequest(
                "private-engine",
                "بازی",
                1,
                listOf(
                    Team("a", "الف", 1, listOf(Player("player-a", "آوا"))),
                    Team("b", "ب", 2, listOf(Player("player-b", "باران"))),
                ),
                GameSettings(roundsPerTeam = 1, finalRoundEnabled = false, individualStatisticsEnabled = false),
            ),
        ) as GameActionResult.Accepted).state
        state = accepted(state, GameAction.StartTurn(state.version, word("private-word-a"), GameMoment(1_000, 10_000)))
        state = accepted(state, GameAction.EndTurn(state.version, GameMoment(2_000, 11_000)))
        state = accepted(state, GameAction.StartTurn(state.version, word("private-word-b"), GameMoment(3_000, 12_000)))
        state = accepted(state, GameAction.EndTurn(state.version, GameMoment(4_000, 13_000)))

        assertEquals(GamePhase.FINISHED, state.phase)
        assertEquals(2, state.turns.size)
        assertEquals(true, state.turns.all { it.playerId == null && it.playerName == "بدون آمار فردی" })
        assertEquals(emptyList<PlayerStatistics>(), GameStatistics.players(listOf(state)))
    }

    @Test
    fun `individual statistics switch prevents player aggregates`() {
        val game = GameState(
            id = "private-stats",
            name = "بازی",
            createdAtEpochMs = 1,
            finishedAtEpochMs = 2,
            settings = GameSettings(individualStatisticsEnabled = false),
            teams = listOf(
                Team("a", "الف", 1, listOf(Player("player-a", "آوا"))),
                Team("b", "ب", 2, listOf(Player("player-b", "باران"))),
            ),
            phase = GamePhase.FINISHED,
            winnerTeamIds = listOf("a"),
            teamScores = mapOf("a" to 0, "b" to 0),
            turns = listOf(TurnRecord("a", null, "بدون آمار فردی", 1, false, 1, 2, 0, emptyList())),
        )
        assertEquals(emptyList<PlayerStatistics>(), GameStatistics.players(listOf(game)))
    }

    private fun accepted(state: GameState, action: GameAction): GameState = when (val result = PantohubEngine.apply(state, action)) {
        is GameActionResult.Accepted -> result.state
        is GameActionResult.Rejected -> error(result.messageFa)
    }

    private fun word(id: String) = WordRecord(id, "گربه", Difficulty.EASY, WordCategories.ANIMALS, validated = true)
}
