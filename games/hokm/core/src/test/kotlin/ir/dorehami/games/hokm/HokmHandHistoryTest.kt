package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.games.hokm.HokmTestSupport.engine
import ir.dorehami.games.hokm.HokmTestSupport.int
import ir.dorehami.games.hokm.HokmTestSupport.whoseTurn
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HokmHandHistoryTest {
    @Test
    fun `finished hand snapshot survives the next hand and deterministic replay`() {
        val game = engine(seed = 5_005L)
        val finishedHandNumber = game.session.publicView().int("handNumber")
        val emissions = ArrayList<Emission>()
        var actions = 0

        while (game.session.publicView().int("handNumber") == finishedHandNumber) {
            check(actions++ < 500) { "the first hand did not finish" }
            val player = whoseTurn(game)
            when (val result = game.submitBot(player)) {
                is ApplyResult.Ok -> emissions += result.emissions
                is ApplyResult.Rejected -> error("bot move rejected: ${result.code} ${result.messageFa}")
                null -> error("bot had no move for $player")
            }
        }

        val finished = emissions.filterIsInstance<Emission.Public>().single { it.type == "hand_finished" }.payload
        assertEquals(finishedHandNumber + 1, game.session.publicView().int("handNumber"))
        assertTrue(assertNotNull(finished["hokm"]).toString() != "null", "the finished hand has a trump")

        val lastHand = assertNotNull(game.session.publicView()["lastHandResult"]).jsonObject
        for (field in listOf("handNumber", "winnerTeam", "points", "kind", "hakem", "hokm", "tricks", "scores")) {
            assertEquals(finished[field], lastHand[field], "lastHandResult.$field must remain the finished-hand value")
        }

        val replayed = GameEngine.replay(HokmModule, game.record())
        assertEquals(game.digest(), replayed.digest())
        assertEquals(lastHand, replayed.session.publicView()["lastHandResult"])
    }
}
