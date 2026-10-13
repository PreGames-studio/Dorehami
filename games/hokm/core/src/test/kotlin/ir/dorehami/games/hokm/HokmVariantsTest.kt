package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.view
import ir.dorehami.games.hokm.HokmTestSupport.cards
import ir.dorehami.games.hokm.HokmTestSupport.chooseHokm
import ir.dorehami.games.hokm.HokmTestSupport.engine
import ir.dorehami.games.hokm.HokmTestSupport.int
import ir.dorehami.games.hokm.HokmTestSupport.ints
import ir.dorehami.games.hokm.HokmTestSupport.playWithBots
import ir.dorehami.games.hokm.HokmTestSupport.players
import ir.dorehami.games.hokm.HokmTestSupport.str
import ir.dorehami.protocol.ErrCodes
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Two- and three-player variants. */
class HokmVariantsTest {

    // ------------------------------------------------------------------ two players

    @Test
    fun `two players - burn 3 and 2, then alternate draws until 13 each, hakem draws first`() {
        val e = engine(21L, count = 2)
        val hakem = e.session.publicView().int("hakem")
        val opp = 1 - hakem
        assertIs<ApplyResult.Ok>(e.submit(players[hakem], chooseHokm(Suit.SPADES)))

        var v = e.session.publicView()
        assertEquals(HandPhase.DISCARDING.id, v.str("handPhase"))
        assertEquals(hakem, v.int("turn"))
        assertEquals(listOf(HokmModule.ACTION_DISCARD), e.session.legalActions(players[hakem]))
        assertEquals(3, e.session.privateView(players[hakem]).int(HokmSession.Keys.DISCARD_COUNT))
        assertEquals(5, e.session.privateView(players[opp]).cards("hand").size)

        // wrong count rejected
        val hHand = e.session.privateView(players[hakem]).cards("hand")
        val bad = e.submit(players[hakem], discard(hHand.take(2)))
        assertIs<ApplyResult.Rejected>(bad)
        assertEquals(ErrCodes.ILLEGAL_ACTION, bad.code)
        // opponent cannot burn before hakem
        val early = e.submit(players[opp], discard(e.session.privateView(players[opp]).cards("hand").take(2)))
        assertIs<ApplyResult.Rejected>(early)
        assertEquals(ErrCodes.NOT_YOUR_TURN, early.code)

        assertIs<ApplyResult.Ok>(e.submit(players[hakem], discard(hHand.take(3))))
        assertEquals(2, e.session.privateView(players[hakem]).cards("hand").size)
        assertEquals(2, e.session.privateView(players[opp]).int(HokmSession.Keys.DISCARD_COUNT))
        assertIs<ApplyResult.Ok>(e.submit(players[opp], discard(e.session.privateView(players[opp]).cards("hand").take(2))))

        v = e.session.publicView()
        assertEquals(HandPhase.DRAWING.id, v.str("handPhase"))
        assertEquals(hakem, v.int("turn"))
        assertEquals(42, v.int("stockCount"))
        // the revealed card is private to the drawer only
        assertTrue(e.session.privateView(players[hakem]).containsKey(HokmSession.Keys.REVEALED))
        assertFalse(e.session.privateView(players[opp]).containsKey(HokmSession.Keys.REVEALED))
        assertFalse(v.containsKey(HokmSession.Keys.REVEALED))
        assertFalse(v.containsKey("revealedCard"))

        var draws = 0
        var expected = hakem
        while (e.session.publicView().str("handPhase") == HandPhase.DRAWING.id) {
            val t = e.session.publicView().int("turn")
            assertEquals(expected, t)
            val shown = Card.parse(e.session.privateView(players[t]).str(HokmSession.Keys.REVEALED))!!
            val before = e.session.privateView(players[t]).cards("hand")
            val keep = draws % 2 == 0
            assertIs<ApplyResult.Ok>(e.submit(players[t], GameAction(if (keep) HokmModule.ACTION_KEEP_CARD else HokmModule.ACTION_PASS_CARD)))
            val after = e.session.privateView(players[t]).cards("hand")
            assertEquals(before.size + 1, after.size)
            if (keep) assertTrue(shown.code in after) else assertFalse(shown.code in after)
            draws++
            expected = 1 - expected
        }
        assertEquals(21, draws)
        assertEquals(HandPhase.PLAYING.id, e.session.publicView().str("handPhase"))
        assertEquals(hakem, e.session.publicView().int("turn"))
        assertEquals(13, e.session.privateView(players[0]).cards("hand").size)
        assertEquals(13, e.session.privateView(players[1]).cards("hand").size)
    }

    @Test
    fun `two players - full bot match, replay and scoring are consistent`() {
        for (seed in 300L until 306L) {
            val e = engine(seed, count = 2)
            val emissions = playWithBots(e)
            assertEquals(Phase.FINISHED, e.session.phase)
            val hands = emissions.filterIsInstance<Emission.Public>().filter { it.type == "hand_finished" }
            for (h in hands) {
                val tricks = h.payload.ints("tricks")
                val w = h.payload.int("winnerTeam")
                assertEquals(7, tricks[w])
                val hakem = h.payload.int("hakem")
                val expectedPts = when {
                    tricks[1 - w] > 0 -> 1
                    hakem != w -> 3
                    else -> 2
                }
                assertEquals(expectedPts, h.payload.int("points"), "seed $seed")
            }
            val replay = GameEngine.replay(HokmModule, e.record())
            assertEquals(e.digest(), replay.digest(), "seed $seed")
            val result = assertNotNull(e.session.result())
            assertEquals(1, result.winners.size)
        }
    }

    // ------------------------------------------------------------------ three players

    @Test
    fun `three players - 51 cards, 17 each, everyone alone`() {
        val e = engine(33L, count = 3)
        val hakem = e.session.publicView().int("hakem")
        assertIs<ApplyResult.Ok>(e.submit(players[hakem], chooseHokm(Suit.HEARTS)))
        val hands = (0 until 3).map { e.session.privateView(players[it]).cards("hand") }
        hands.forEach { assertEquals(17, it.size) }
        val all = hands.flatten()
        assertEquals(51, all.toSet().size)
        assertFalse(HokmRules.THREE_PLAYER_REMOVED.code in all)
        assertEquals(listOf(0, 0, 0), e.session.publicView().ints("scores"))
    }

    @Test
    fun `three players - full bot matches end with one winner and sane hands`() {
        for (seed in 400L until 408L) {
            val e = engine(seed, count = 3)
            val emissions = playWithBots(e)
            assertEquals(Phase.FINISHED, e.session.phase)
            val hands = emissions.filterIsInstance<Emission.Public>().filter { it.type == "hand_finished" }
            for (h in hands) {
                val tricks = h.payload.ints("tricks")
                assertTrue(tricks.sum() <= 17)
                val kind = h.payload.str("kind")
                if (kind == "void") {
                    assertEquals(0, h.payload.int("points"))
                    assertEquals(17, tricks.sum())
                } else {
                    val w = h.payload.int("winnerTeam")
                    assertTrue(tricks[w] == 7 || (tricks.sum() == 17 && tricks[w] == tricks.max()))
                    // HK-008 contract: kot needs BOTH losers at zero; hakem-kot needs the hakem at zero.
                    val others = tricks.filterIndexed { i, t -> i != w }
                    val hakem = h.payload.int("hakem")
                    val expected = when {
                        tricks[hakem] == 0 && hakem != w -> "hakem_kot" to 3
                        others.all { it == 0 } -> "kot" to 2
                        else -> "normal" to 1
                    }
                    assertEquals(expected, kind to h.payload.int("points"), "seed $seed hand ${h.payload.int("handNumber")}")
                }
            }
            val scores = e.session.publicView().ints("scores")
            assertEquals(1, scores.count { it >= 7 }, "seed $seed: $scores")
            assertEquals(e.digest(), GameEngine.replay(HokmModule, e.record()).digest())
        }
    }

    @Test
    fun `five players are refused`() {
        val ex = kotlin.test.assertFailsWith<ir.dorehami.gamesdk.GameConfigException> {
            GameEngine.start(
                HokmModule,
                ir.dorehami.gamesdk.GameConfig(
                    1L,
                    players + ir.dorehami.protocol.PlayerId("p_e"),
                    HokmModule.defaultOptions(),
                    ir.dorehami.gamesdk.ManualClock(),
                    ir.dorehami.gamesdk.SplitMixRng(1L),
                ),
            )
        }
        assertEquals(ErrCodes.BAD_OPTIONS, ex.code)
    }

    private fun discard(codes: List<String>) = GameAction(
        HokmModule.ACTION_DISCARD,
        view { put(HokmModule.PAYLOAD_CARDS, buildJsonArray { codes.forEach { add(JsonPrimitive(it)) } }) },
    )
}
