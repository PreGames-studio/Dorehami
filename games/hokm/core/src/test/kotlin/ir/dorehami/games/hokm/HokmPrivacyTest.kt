package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.Phase
import ir.dorehami.games.hokm.HokmTestSupport.cards
import ir.dorehami.games.hokm.HokmTestSupport.chooseHokm
import ir.dorehami.games.hokm.HokmTestSupport.engine
import ir.dorehami.games.hokm.HokmTestSupport.int
import ir.dorehami.games.hokm.HokmTestSupport.players
import ir.dorehami.games.hokm.HokmTestSupport.whoseTurn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Contract rule 2: hidden information is structurally absent from the public view. */
class HokmPrivacyTest {

    private val secretKeys = listOf(HokmSession.Keys.HAND, "hands", "pending", "played", "rngState", HokmSession.Keys.LEGAL_CARDS)

    @Test
    fun `public view never contains any hand related key`() {
        val e = engine(5L)
        val hakem = e.session.publicView().int("hakem")
        e.submit(players[hakem], chooseHokm(Suit.DIAMONDS))
        // Check at several points of the game, including mid-trick.
        var step = 0
        while (e.session.phase != Phase.FINISHED && step < 60) {
            val pv = e.session.publicView()
            for (k in secretKeys) assertFalse(pv.containsKey(k), "public view leaks '$k'")
            assertIs<ApplyResult.Ok>(e.submitBot(whoseTurn(e)))
            step++
        }
    }

    @Test
    fun `each private view shows only its own hand and all hands are disjoint`() {
        val e = engine(11L)
        val hakem = e.session.publicView().int("hakem")
        e.submit(players[hakem], chooseHokm(Suit.SPADES))

        val hands = players.map { e.session.privateView(it).cards(HokmSession.Keys.HAND).toSet() }
        hands.forEach { assertEquals(13, it.size) }
        assertEquals(52, hands.flatten().toSet().size, "hands must be disjoint and cover the deck")
        players.forEachIndexed { seat, p ->
            assertEquals(seat, e.session.privateView(p).int(HokmSession.Keys.SEAT))
            // Other players' keys never appear: private view has one 'hand', never 'hands'.
            assertFalse(e.session.privateView(p).containsKey("hands"))
        }
    }

    @Test
    fun `private emissions are addressed to the right player and carry only that player's hand`() {
        val e = engine(13L)
        val hakem = e.session.publicView().int("hakem")
        val r = e.submit(players[hakem], chooseHokm(Suit.HEARTS))
        assertIs<ApplyResult.Ok>(r)
        val privates = r.emissions.filterIsInstance<Emission.Private>()
        assertEquals(players.toSet(), privates.map { it.to }.toSet())
        for (p in privates) {
            assertEquals(e.session.privateView(p.to).cards(HokmSession.Keys.HAND), p.view.cards(HokmSession.Keys.HAND))
        }
        val publics = r.emissions.filterIsInstance<Emission.Public>()
        assertTrue(publics.none { pub -> secretKeys.any { pub.payload.containsKey(it) } })
    }

    @Test
    fun `an outsider gets a public view only`() {
        val e = engine(17L)
        val outsider = ir.dorehami.protocol.PlayerId("p_spy")
        val v = e.session.privateView(outsider)
        for (k in secretKeys) assertFalse(v.containsKey(k))
        assertTrue(e.session.legalActions(outsider).isEmpty())
    }
}
