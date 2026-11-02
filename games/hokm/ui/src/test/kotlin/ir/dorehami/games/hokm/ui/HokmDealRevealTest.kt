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
}
