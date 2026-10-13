package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.Phase
import ir.dorehami.games.hokm.HokmTestSupport.engine
import ir.dorehami.games.hokm.HokmTestSupport.playWithBots
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HokmEasyBotTest {

    @Test
    fun `easy bot can evaluate an incomplete three and four player trick`() {
        val lead = Card(Rank.NINE, Suit.SPADES)
        val winningFollow = Card(Rank.JACK, Suit.SPADES)

        for (count in listOf(3, 4)) {
            val view = BotView(
                seat = 1,
                players = count,
                hand = listOf(winningFollow),
                hokm = Suit.HEARTS,
                trick = listOf(Play(seat = 0, card = lead)),
                played = setOf(lead),
                tricksWon = List(HokmRules.teamCount(count)) { 0 },
                hakem = 0,
            )

            assertEquals(winningFollow, HokmBot.playCardEasy(view), "$count-player partial trick")
        }
    }

    @Test
    fun `easy bots complete and replay a full match with two three and four players`() {
        val options = HokmModule.validateOptions(
            buildJsonObject { put(HokmSession.Options.BOT_LEVEL, HokmModule.BOT_EASY) },
        )

        for (count in listOf(2, 3, 4)) {
            val game = engine(seed = 800L + count, options = options, count = count)
            val emissions = playWithBots(game)

            assertEquals(Phase.FINISHED, game.session.phase, "$count-player easy match")
            assertTrue(
                emissions.filterIsInstance<Emission.Public>().any { it.type == "hand_finished" },
                "$count-player easy match should finish at least one hand",
            )
            assertEquals(
                game.digest(),
                GameEngine.replay(HokmModule, game.record()).digest(),
                "$count-player easy replay",
            )
        }
    }
}
