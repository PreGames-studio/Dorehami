package ir.dorehami.games.pantohub

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayModeTest {
    @Test
    fun `speedy is the default so existing matches keep their behaviour`() {
        assertEquals(PlayMode.SPEEDY, GameSettings().playMode)
    }

    @Test
    fun `play mode survives the saved-game codec`() {
        val request = NewGameRequest(
            id = "mode-1",
            name = "دورهمی",
            createdAtEpochMs = 100,
            teams = listOf(
                Team("red", "قرمزها", 1, listOf(Player("r1", "رضا"))),
                Team("blue", "آبی‌ها", 2, listOf(Player("b1", "سارا"))),
            ),
            settings = GameSettings(playMode = PlayMode.NORMAL),
        )
        val state = (PantohubEngine.createGame(request) as GameActionResult.Accepted).state

        val restored = GameStateJsonCodec.decode(GameStateJsonCodec.encode(state))

        assertEquals(PlayMode.NORMAL, restored.settings.playMode)
    }
}
