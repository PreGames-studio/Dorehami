package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfigException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.assertFailsWith
import ir.dorehami.gamesdk.Phase
import ir.dorehami.games.hokm.HokmTestSupport.cards
import ir.dorehami.games.hokm.HokmTestSupport.chooseHokm
import ir.dorehami.games.hokm.HokmTestSupport.engine
import ir.dorehami.games.hokm.HokmTestSupport.int
import ir.dorehami.games.hokm.HokmTestSupport.playCard
import ir.dorehami.games.hokm.HokmTestSupport.playWithBots
import ir.dorehami.games.hokm.HokmTestSupport.players
import ir.dorehami.games.hokm.HokmTestSupport.str
import ir.dorehami.protocol.ErrCodes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HokmRulesTest {

    private fun c(code: String) = Card.parse(code)!!

    @Test
    fun `highest trump wins, otherwise highest of led suit`() {
        val hokm = Suit.SPADES
        // No trump: highest of led suit (hearts) wins even if a diamond king is bigger on paper.
        val t1 = listOf(Play(0, c("7H")), Play(1, c("KD")), Play(2, c("QH")), Play(3, c("2H")))
        assertEquals(2, HokmRules.trickWinner(t1, hokm))
        // One trump beats any plain card.
        val t2 = listOf(Play(1, c("AH")), Play(2, c("2S")), Play(3, c("KH")), Play(0, c("QH")))
        assertEquals(2, HokmRules.trickWinner(t2, hokm))
        // Two trumps: higher trump wins.
        val t3 = listOf(Play(3, c("9C")), Play(0, c("5S")), Play(1, c("JS")), Play(2, c("AC")))
        assertEquals(1, HokmRules.trickWinner(t3, hokm))
        // Trump led: highest trump wins.
        val t4 = listOf(Play(2, c("TS")), Play(3, c("AS")), Play(0, c("KS")), Play(1, c("AH")))
        assertEquals(3, HokmRules.trickWinner(t4, hokm))
    }

    @Test
    fun `must follow suit when holding the led suit`() {
        val hand = listOf(c("2H"), c("AS"), c("9H"), c("KC"))
        assertEquals(listOf(c("2H"), c("9H")), HokmRules.legalCards(hand, listOf(Play(1, c("QH")))))
        assertEquals(hand, HokmRules.legalCards(hand, listOf(Play(1, c("QD")))))
        assertEquals(hand, HokmRules.legalCards(hand, emptyList()))
    }

    @Test
    fun `first hakem is the first seat that draws an ace`() {
        val view = engine(42L).session.publicView()
        val draw = view["hakemDraw"]!!.jsonArray.map { it.jsonObject }
        assertTrue(draw.isNotEmpty())
        // Nobody before the last card drew an ace, and the last card is an ace owned by the hakem.
        draw.dropLast(1).forEach { assertFalse(it.str("card").startsWith("A"), "draw must stop at the first ace") }
        val last = draw.last()
        assertTrue(last.str("card").startsWith("A"))
        assertEquals(view.int("hakem"), last.int("seat"))
        // Seats rotate 0,1,2,3,0,… from seat 0.
        draw.forEachIndexed { i, p -> assertEquals(i % 4, p.int("seat")) }
    }

    @Test
    fun `hakem gets five cards and must name hokm before anyone plays`() {
        val e = engine(7L)
        val v = e.session.publicView()
        val hakem = v.int("hakem")
        assertEquals(HandPhase.CHOOSING_HOKM.id, v.str("handPhase"))
        assertEquals(listOf(HokmModule.ACTION_CHOOSE_HOKM), e.session.legalActions(players[hakem]))
        assertEquals(5, e.session.privateView(players[hakem]).cards("hand").size)
        assertEquals(0, e.session.privateView(players[(hakem + 1) % 4]).cards("hand").size)

        val before = e.session.version
        val r = e.submit(players[hakem], playCard("AS"))
        assertIs<ApplyResult.Rejected>(r)
        assertEquals(ErrCodes.BAD_STATE, r.code)
        assertEquals(before, e.session.version, "rejection must not bump the version")

        val notHakem = e.submit(players[(hakem + 1) % 4], chooseHokm(Suit.HEARTS))
        assertIs<ApplyResult.Rejected>(notHakem)
        assertEquals(ErrCodes.NOT_YOUR_TURN, notHakem.code)

        val badSuit = e.submit(players[hakem], GameAction(HokmModule.ACTION_CHOOSE_HOKM))
        assertIs<ApplyResult.Rejected>(badSuit)
        assertEquals(ErrCodes.BAD_PAYLOAD, badSuit.code)

        val ok = e.submit(players[hakem], chooseHokm(Suit.HEARTS))
        assertIs<ApplyResult.Ok>(ok)
        assertEquals(before + 1, e.session.version)
        val after = e.session.publicView()
        assertEquals("hearts", after.str("hokm"))
        assertEquals(HandPhase.PLAYING.id, after.str("handPhase"))
        assertEquals(hakem, after.int("turn"), "hakem leads the first trick")
        for (p in players) assertEquals(13, e.session.privateView(p).cards("hand").size)
    }

    @Test
    fun `illegal plays are rejected with the right codes`() {
        val e = engine(99L)
        val hakem = e.session.publicView().int("hakem")
        assertIs<ApplyResult.Ok>(e.submit(players[hakem], chooseHokm(Suit.CLUBS)))

        // not your turn
        val other = players[(hakem + 2) % 4]
        val otherCard = e.session.privateView(other).cards("hand").first()
        val r1 = e.submit(other, playCard(otherCard))
        assertIs<ApplyResult.Rejected>(r1)
        assertEquals(ErrCodes.NOT_YOUR_TURN, r1.code)

        // card not in hand
        val me = players[hakem]
        val myHand = e.session.privateView(me).cards("hand")
        val notMine = Card.fullDeck().first { it.code !in myHand }
        val r2 = e.submit(me, playCard(notMine.code))
        assertIs<ApplyResult.Rejected>(r2)
        assertEquals(ErrCodes.ILLEGAL_ACTION, r2.code)

        // garbage payload
        val r3 = e.submit(me, playCard("ZZ"))
        assertIs<ApplyResult.Rejected>(r3)
        assertEquals(ErrCodes.BAD_PAYLOAD, r3.code)

        // unknown action type
        val r4 = e.submit(me, GameAction("dance"))
        assertIs<ApplyResult.Rejected>(r4)
        assertEquals(ErrCodes.ILLEGAL_ACTION, r4.code)

        // lead a card, then the next player must follow suit if able
        val lead = Card.parse(myHand.first())!!
        assertIs<ApplyResult.Ok>(e.submit(me, playCard(lead.code)))
        val next = players[(hakem + 1) % 4]
        val nextHand = e.session.privateView(next).cards("hand").map { Card.parse(it)!! }
        val canFollow = nextHand.filter { it.suit == lead.suit }
        if (canFollow.isNotEmpty()) {
            val offSuit = nextHand.firstOrNull { it.suit != lead.suit }
            if (offSuit != null) {
                val r5 = e.submit(next, playCard(offSuit.code))
                assertIs<ApplyResult.Rejected>(r5)
                assertEquals(ErrCodes.ILLEGAL_ACTION, r5.code)
            }
            assertEquals(canFollow.map { it.code }.sorted(), e.session.privateView(next).cards("legalCards").sorted())
        } else {
            assertEquals(nextHand.size, e.session.privateView(next).cards("legalCards").size)
        }
    }

    @Test
    fun `a full bot game runs from first ace to final point without a single rejection`() {
        val e = engine(2024L)
        val emissions = playWithBots(e)
        assertEquals(Phase.FINISHED, e.session.phase)
        val result = assertNotNull(e.session.result())
        assertEquals(2, result.standings.size)
        assertEquals(1, result.standings.first().rank)
        assertTrue(emissions.isNotEmpty())
        // Once finished, nothing more is accepted.
        val r = e.submit(players[0], playCard("AS"))
        assertIs<ApplyResult.Rejected>(r)
        assertEquals(ErrCodes.BAD_STATE, r.code)
        assertTrue(e.session.legalActions(players[0]).isEmpty())
    }

    @Test
    fun `bad options are rejected in Persian and good ones are normalised`() {
        val bad = buildJsonObject { put("targetPoints", 4) }
        val ex = assertFailsWith<GameConfigException> { HokmModule.validateOptions(bad) }
        assertEquals(ErrCodes.BAD_OPTIONS, ex.code)
        assertTrue(ex.messageFa.isNotBlank())

        val normalised = HokmModule.validateOptions(buildJsonObject { })
        assertEquals(HokmModule.DEFAULT_TARGET_POINTS, normalised.int("targetPoints"))
        assertEquals(HokmModule.DEFAULT_KOT_POINTS, normalised.int("kotPoints"))
    }
}
