package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The deal presentation: the engine hands over a whole hand at once, so the table reveals it card by
 * card. These are the two pure decisions behind that — how much of my hand is visible, and how many
 * cards each chair has picked up — plus the sound a card makes when it lands.
 */
class HokmDealRevealTest {

    private val hand = listOf(
        Card(Rank.ACE, Suit.HEARTS),
        Card(Rank.KING, Suit.HEARTS),
        Card(Rank.SEVEN, Suit.CLUBS),
    )

    private fun state(
        reveal: List<Int>?,
        mySeat: Int = 0,
        counts: List<Int> = listOf(13, 13, 13, 13),
    ) = HokmUiState(
        playerCount = 4,
        mySeat = mySeat,
        myHand = hand,
        handCounts = counts,
        dealReveal = reveal,
    )

    @Test
    fun `no deal running - the whole hand and the real counts`() {
        val s = state(reveal = null)
        assertEquals(hand, s.visibleHand)
        assertEquals(13, s.visibleCount(2))
    }

    @Test
    fun `during the deal only the cards already handed to me are visible`() {
        val s = state(reveal = listOf(2, 0, 5, 5))
        assertEquals(hand.take(2), s.visibleHand)
        assertEquals(5, s.visibleCount(2))
        assertEquals(0, s.visibleCount(1))
    }

    @Test
    fun `a reveal larger than my hand can never read past it`() {
        val s = state(reveal = listOf(13, 13, 13, 13))
        assertEquals(hand, s.visibleHand)
    }

    @Test
    fun `dealing counts by my own seat, not by seat order`() {
        val s = state(reveal = listOf(13, 13, 1, 13), mySeat = 2)
        assertEquals(hand.take(1), s.visibleHand)
    }

    @Test
    fun `every trump card makes the heavy slap, everything else the felt tick`() {
        // A trump lead, a cut, and a trump following a trump all sound the same.
        assertEquals(TableAudio.SLAM, TableAudio.cardLands(trump = true, cut = false))
        assertEquals(TableAudio.SLAM, TableAudio.cardLands(trump = true, cut = true))
        assertEquals(TableAudio.SLAM, TableAudio.cardLands(trump = false, cut = true))
        assertEquals(TableAudio.PLAY, TableAudio.cardLands(trump = false, cut = false))
        assertTrue(TableAudio.SLAM != TableAudio.PLAY)
    }

    @Test
    fun `the deck waits on the table while the hakem picks a trump, and input stays free`() {
        val s = HokmUiState(
            playerCount = 4,
            mySeat = 0,
            myHand = hand,
            handCounts = listOf(13, 13, 13, 13),
            handReveal = listOf(5, 0, 0, 0),
            dealStock = 47,
            hakem = 0,
        )
        // Display-only: the five are on screen, but nothing is animating and nothing is blocked.
        assertEquals(5, s.visibleCount(0))
        assertEquals(0, s.visibleCount(1))
        assertEquals(hand, s.visibleHand)
        assertTrue(s.isCalm, "a paused deal must not block the hakem's choice")
        assertEquals(47, s.dealStock)
    }

    @Test
    fun `a reveal can never show more cards than the seat really holds`() {
        val s = HokmUiState(
            playerCount = 4,
            mySeat = 0,
            handCounts = listOf(2, 0, 0, 0),
            handReveal = listOf(5, 0, 0, 0),
        )
        assertEquals(2, s.visibleCount(0))
        assertEquals(0, s.visibleCount(1))
    }

    @Test
    fun `while the cards are in the air the table is busy`() {
        val s = HokmUiState(
            playerCount = 4,
            mySeat = 0,
            handCounts = listOf(13, 13, 13, 13),
            dealReveal = listOf(3, 2, 2, 1),
            dealStock = 44,
            dealFlights = listOf(DealFlight(id = 1, seat = 2, slot = 2)),
        )
        assertTrue(!s.isCalm, "a running deal must hold the table")
        assertEquals(3, s.visibleCount(0))
        assertEquals(1, s.dealFlights.size)
    }
}
