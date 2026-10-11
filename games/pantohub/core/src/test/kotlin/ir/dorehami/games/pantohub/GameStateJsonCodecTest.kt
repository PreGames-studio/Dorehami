package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GameStateJsonCodecTest {
    @Test
    fun `snapshot codec round trips the active game without losing private turn state`() {
        var state = (PantohubEngine.createGame(
            NewGameRequest(
                id = "persist-1",
                name = "بازی تست",
                createdAtEpochMs = 100,
                teams = listOf(
                    Team("a", "الف", 1, listOf(Player("p-a", "آوا"))),
                    Team("b", "ب", 2, listOf(Player("p-b", "باران"))),
                ),
                settings = GameSettings(),
            ),
        ) as GameActionResult.Accepted).state
        state = (PantohubEngine.apply(
            state,
            GameAction.StartTurn(
                state.version,
                WordRecord("w", "گربه", Difficulty.EASY, WordCategories.ANIMALS, validated = true),
                GameMoment(10, 1_000),
            ),
        ) as GameActionResult.Accepted).state

        val restored = GameStateJsonCodec.decode(GameStateJsonCodec.encode(state))
        assertEquals(state, restored)
        assertEquals("گربه", restored.currentTurn!!.currentWord!!.text)
    }

    @Test
    fun `statistics aggregate from completed turn events`() {
        val teams = listOf(
            Team("red", "قرمزها", 1, listOf(Player("r", "رها"))),
            Team("blue", "آبی‌ها", 2, listOf(Player("b", "بهنام"))),
        )
        val finished = GameState(
            id = "finished-1",
            name = "بازی",
            createdAtEpochMs = 1,
            finishedAtEpochMs = 5,
            settings = GameSettings(),
            teams = teams,
            phase = GamePhase.FINISHED,
            teamScores = mapOf("red" to 2, "blue" to 0),
            winnerTeamIds = listOf("red"),
            turns = listOf(
                TurnRecord(
                    teamId = "red",
                    playerId = "r",
                    playerName = "رها",
                    roundNumber = 1,
                    isFinalRound = false,
                    startedAtEpochMs = 2,
                    endedAtEpochMs = 3,
                    scoreEarned = 2,
                    attempts = listOf(
                        WordAttempt("w", "مسواک زدن", Difficulty.MEDIUM, WordOutcome.CORRECT, 2, 3),
                    ),
                ),
                TurnRecord("blue", "b", "بهنام", 1, false, 3, 4, 0, emptyList()),
            ),
        )

        val teamStats = GameStatistics.teams(listOf(finished)).associateBy { it.name }
        assertEquals(1, teamStats.getValue("قرمزها").wins)
        assertEquals(2, teamStats.getValue("قرمزها").totalPoints)
        assertEquals(1, teamStats.getValue("قرمزها").mediumCorrect)
        val players = GameStatistics.players(listOf(finished)).associateBy { it.playerId }
        assertEquals(setOf("r", "b"), players.keys)
        val player = players.getValue("r")
        assertEquals(1, player.correctAnswers)
        assertEquals(2, player.pointsEarned)
        assertEquals(0, players.getValue("b").pointsEarned)
    }
}
