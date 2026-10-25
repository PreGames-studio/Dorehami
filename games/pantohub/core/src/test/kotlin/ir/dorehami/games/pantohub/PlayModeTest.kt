package ir.dorehami.games.pantohub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayModeTest {
    private val teams = listOf(
        Team("t1", "قرمز", 0xffd94f52.toInt(), listOf(Player("p1", "آرش"))),
        Team("t2", "آبی", 0xff3777c8.toInt(), listOf(Player("p2", "نیلوفر"))),
    )

    private fun word(id: String, difficulty: Difficulty = Difficulty.MEDIUM) = WordRecord(
        id = id,
        text = "واژه $id",
        normalizedText = WordBankRules.normalizePersian("واژه $id"),
        category = "objects",
        difficulty = difficulty,
        phraseType = PhraseType.SINGLE_WORD,
    )

    @Test
    fun `default play mode is speedy and normal mode survives json roundtrip`() {
        assertEquals(PlayMode.SPEEDY, GameSettings().playMode)

        val created = (PantohubEngine.createGame(
            NewGameRequest(
                id = "normal-json",
                name = "پانتومیم عادی",
                createdAtEpochMs = 1_000L,
                teams = teams,
                settings = GameSettings(playMode = PlayMode.NORMAL, roundsPerTeam = 2),
            ),
        ) as GameActionResult.Accepted).state

        val decoded = GameStateJsonCodec.decode(GameStateJsonCodec.encode(created))
        assertEquals(PlayMode.NORMAL, decoded.settings.playMode)
        assertEquals(created, decoded)
    }

    @Test
    fun `legacy json without playMode or wordSeen decodes as speedy and unseen`() {
        val state = (PantohubEngine.createGame(
            NewGameRequest(
                id = "legacy",
                name = "قدیمی",
                createdAtEpochMs = 1_000L,
                teams = teams,
                settings = GameSettings(playMode = PlayMode.NORMAL),
            ),
        ) as GameActionResult.Accepted).state
        val started = (PantohubEngine.apply(
            state,
            GameAction.StartTurn(state.version, word("w1"), GameMoment(0L, 1_000L)),
        ) as GameActionResult.Accepted).state

        val legacyJson = GameStateJsonCodec.encode(started)
            .replace("\"playMode\":\"NORMAL\",", "")
            .replace(",\"wordSeen\":false", "")
            .replace("\"wordSeen\":false,", "")

        val decoded = GameStateJsonCodec.decode(legacyJson)
        assertEquals(PlayMode.SPEEDY, decoded.settings.playMode)
        assertEquals(false, decoded.currentTurn?.wordSeen)
    }

    @Test
    fun `scoredCorrect detects correct guess in both speedy and normal modes`() {
        val normal0 = (PantohubEngine.createGame(
            NewGameRequest("g-norm", "عادی", 1_000L, teams, GameSettings(playMode = PlayMode.NORMAL)),
        ) as GameActionResult.Accepted).state
        val normal1 = (PantohubEngine.apply(normal0, GameAction.StartTurn(normal0.version, word("w1"), GameMoment(0L, 1_000L))) as GameActionResult.Accepted).state
        val normal2 = (PantohubEngine.apply(normal1, GameAction.RevealWord(normal1.version, GameMoment(100L, 1_100L))) as GameActionResult.Accepted).state
        val normalSkipped = (PantohubEngine.apply(
            normal2,
            GameAction.ResolveWord(normal2.version, WordOutcome.SKIPPED, word("w2"), GameMoment(200L, 1_200L)),
        ) as GameActionResult.Accepted).state
        assertFalse(PantohubEngine.scoredCorrect(normal2, normalSkipped))

        val normalRevealed2 = (PantohubEngine.apply(normalSkipped, GameAction.RevealWord(normalSkipped.version, GameMoment(300L, 1_300L))) as GameActionResult.Accepted).state
        val normalCorrect = (PantohubEngine.apply(
            normalRevealed2,
            GameAction.ResolveWord(normalRevealed2.version, WordOutcome.CORRECT, null, GameMoment(400L, 1_400L)),
        ) as GameActionResult.Accepted).state
        assertEquals(GamePhase.READY, normalCorrect.phase)
        assertTrue(PantohubEngine.scoredCorrect(normalRevealed2, normalCorrect))
    }
}
