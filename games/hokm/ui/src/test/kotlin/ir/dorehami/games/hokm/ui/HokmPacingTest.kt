package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.Emission
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.HandWinKind
import ir.dorehami.games.hokm.Play
import ir.dorehami.games.hokm.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM tests for the HK-010 event/pacing core: the versioned notice queue and the pure felt
 * rules, plus the merge contract that keeps a burst / skipped cycle / mid-trick resync from
 * ever duplicating or dropping a card or a sound. No Compose, no Android.
 */
class HokmPacingTest {

    // ------------------------------------------------------------------ fixtures

    private val c1 = Card(ir.dorehami.games.hokm.Rank.ACE, Suit.SPADES)
    private val c2 = Card(ir.dorehami.games.hokm.Rank.KING, Suit.SPADES)
    private val c3 = Card(ir.dorehami.games.hokm.Rank.TEN, Suit.HEARTS)
    private val c4 = Card(ir.dorehami.games.hokm.Rank.TWO, Suit.CLUBS)

    private fun state(
        vararg felt: Play,
        playerCount: Int = 4,
        trickNumber: Int = 1,
    ) = HokmUiState(
        seats = List(playerCount) { SeatInfo(it, "p$it", false) },
        playerCount = playerCount,
        mySeat = 0,
        handPhase = HandPhase.PLAYING,
        turn = 1,
        trickNumber = trickNumber,
        tableCards = felt.toList(),
        tableTrickNumber = trickNumber,
    )

    private fun snap(
        version: Long = 1L,
        playerCount: Int = 4,
        trickNumber: Int = 1,
        currentTrick: List<Play> = emptyList(),
        turn: Int = 1,
        myHand: List<Card> = listOf(c1),
    ) = TableSnapshot(
        version = version,
        mySeat = 0,
        playerCount = playerCount,
        finished = false,
        handPhase = HandPhase.PLAYING,
        handNumber = 1,
        hakem = 1,
        hokm = Suit.SPADES,
        turn = turn,
        trickNumber = trickNumber,
        leadSuit = currentTrick.firstOrNull()?.card?.suit,
        currentTrick = currentTrick,
        tricksWon = listOf(0, 0),
        scores = listOf(0, 0),
        targetPoints = 7,
        handCounts = listOf(4, 4, 4, 4),
        myHand = myHand,
        legalCards = myHand.toSet(),
        revealed = null,
        discardCount = 0,
        stockCount = 0,
        hakemDraw = emptyList(),
        lastTrick = emptyList(),
        lastTrickWinner = null,
        lastHand = null,
        winnerTeam = null,
    )

    private fun hand(no: Int) = HandSummary(no, winnerTeam = 0, points = 1, kind = HandWinKind.NORMAL, hakem = 1, tricks = listOf(7, 0))

    // ------------------------------------------------------------------ HokmEventQueue

    @Test
    fun `queue keeps notice order and empties after polling`() {
        val q = HokmEventQueue()
        val a = Notice(1, 0, listOf(Emission.Sound("card_play")))
        val b = Notice(2, 1, listOf(Emission.Sound("card_play")))
        assertTrue(q.offer(a))
        assertTrue(q.offer(b))
        assertEquals(2, q.size)
        assertEquals(a, q.poll())
        assertEquals(b, q.poll())
        assertNull(q.poll())
        assertTrue(q.isEmpty)
    }

    @Test
    fun `a re-delivered notice is refused so its sounds can never double`() {
        val q = HokmEventQueue()
        val n = Notice(5, -1, listOf(Emission.Feed("k", kotlinx.serialization.json.JsonObject(emptyMap()), "info")))
        assertTrue(q.offer(n))
        assertFalse(q.offer(n.copy()), "same version + same emissions = duplicate")
        assertFalse(q.offer(Notice(5, 3, n.emissions)), "even a different actor fingerprint-matches")
        assertEquals(1, q.size)
        assertEquals(2, q.duplicates)
    }

    @Test
    fun `different actions at one version are separate notices`() {
        val q = HokmEventQueue()
        assertTrue(q.offer(Notice(5, -1, listOf(Emission.Sound("card_play")))))
        assertTrue(q.offer(Notice(5, -1, listOf(Emission.Sound("whoosh")))))
        assertEquals(2, q.size, "rooms fan out one emission per notice at the shared seq")
    }

    @Test
    fun `overflow drops the oldest notices and counts them`() {
        val q = HokmEventQueue(capacity = 3)
        repeat(5) { assertTrue(q.offer(Notice(it.toLong() + 1, -1, listOf(Emission.Sound("s$it"))))) }
        assertEquals(3, q.size)
        assertEquals(2, q.dropped)
        assertEquals(3L, q.poll()!!.version, "the two oldest were dropped")
    }

    // ------------------------------------------------------------------ felt rules

    @Test
    fun `a played card lands on the felt exactly once`() {
        val s0 = state()
        val s1 = HokmPacing.onCardPlayed(s0, 1, c1, trickNumber = 1)
        assertEquals(listOf(Play(1, c1)), s1.tableCards)
        val s2 = HokmPacing.onCardPlayed(s1, 1, c1, trickNumber = 1)
        assertEquals(s1, s2, "the same card twice is ignored — no duplicate on the felt")
    }

    @Test
    fun `cards of another trick never mix into the felt`() {
        val s = state(Play(1, c1), Play(2, c2), trickNumber = 1)
        val after = HokmPacing.onCardPlayed(s, 3, c3, trickNumber = 2)
        assertEquals(s, after, "a card of trick 2 cannot append to trick 1's felt")
    }

    @Test
    fun `the sweep freezes the felt — late cards wait for the next merge`() {
        val full = state(Play(0, c1), Play(1, c2), Play(2, c3), Play(3, c4))
        val sweeping = HokmPacing.onTrickWon(full, winner = 2)
        assertEquals(2, sweeping.sweepOf)
        assertEquals(-1, sweeping.turn, "nobody is on turn while the closed trick is shown")
        assertTrue(sweeping.isMyTurn.not())
        val late = HokmPacing.onCardPlayed(sweeping, 0, c1, trickNumber = 2)
        assertEquals(sweeping, late, "nothing appends while the sweep owns the felt")
    }

    @Test
    fun `only a full trick is swept — a partial felt is not`() {
        val partial = state(Play(1, c2), Play(2, c3))
        val after = HokmPacing.onTrickWon(partial, winner = 1)
        assertNull(after.sweepOf)
        assertEquals(partial, after, "a stale win over a partial felt changes nothing")
    }

    @Test
    fun `collecting releases the felt for the truth`() {
        val sweeping = HokmPacing.onTrickWon(state(Play(0, c1), Play(1, c2), Play(2, c3), Play(3, c4)), 1)
        val done = HokmPacing.onTrickCollected(sweeping)
        assertEquals(emptyList(), done.tableCards)
        assertNull(done.sweepOf)
        assertNull(done.collectingTo)
    }

    @Test
    fun `the score sheet accepts each finished hand once`() {
        val s = HokmPacing.recordHand(state(), hand(1))
        val again = HokmPacing.recordHand(s, hand(1))
        assertEquals(s, again, "a re-delivered hand_finished must not double the score sheet")
        val next = HokmPacing.recordHand(again, hand(2))
        assertEquals(2, next.handHistory.size)
    }

    // ------------------------------------------------------------------ burst / resync convergence

    @Test
    fun `a mid-burst resync neither drops nor duplicates felt cards`() {
        // The felt is mid-trick: three cards from notices.
        var s = state(Play(0, c1), Play(1, c2), Play(2, c3), trickNumber = 1)

        // A snapshot from *further ahead* in the stream arrives while a notice is still queued:
        // truth updates, the felt does not (feltSync = false).
        s = HokmUiMapper.merge(s, snap(version = 9, currentTrick = emptyList(), myHand = listOf(c3)), feltSync = false)
        assertEquals(listOf(Play(0, c1), Play(1, c2), Play(2, c3)), s.tableCards, "the queued notices still own the felt")
        assertEquals(listOf(c3), s.myHand, "truth (myHand) is current even mid-burst")

        // The closing card + win land; the trick freezes at four cards.
        s = HokmPacing.onCardPlayed(s, 3, c4, trickNumber = 1)
        s = HokmPacing.onTrickWon(s, winner = 0)
        assertEquals(4, s.tableCards.size)
        assertEquals(0, s.sweepOf)

        // Even a truth merge during the sweep keeps the frozen trick.
        s = HokmUiMapper.merge(s, snap(version = 10, currentTrick = listOf(Play(1, c2), Play(2, c3))), feltSync = false)
        assertEquals(4, s.tableCards.size, "a resync mid-sweep cannot delete the trick being shown")
        assertEquals(-1, s.turn, "and nobody is on turn while it is shown")

        // Collect ends; a calm truth merge rebuilds the felt from the snapshot.
        s = HokmPacing.onTrickCollected(s)
        s = HokmUiMapper.merge(s, snap(version = 10, currentTrick = listOf(Play(1, c2), Play(2, c3)), trickNumber = 2))
        assertEquals(listOf(Play(1, c2), Play(2, c3)), s.tableCards)
        assertEquals(2, s.tableTrickNumber)

        // A re-delivered notice for a card already restored from the snapshot is not shown twice.
        s = HokmPacing.onCardPlayed(s, 1, c2, trickNumber = 2)
        assertEquals(listOf(Play(1, c2), Play(2, c3)), s.tableCards, "mid-trick resync: no duplicated card")
        // …while a genuinely new card lands exactly once.
        s = HokmPacing.onCardPlayed(s, 3, c3, trickNumber = 2)
        assertEquals(3, s.tableCards.size)
    }

    @Test
    fun `skip semantics - an outdated sweep cannot freeze a newer felt`() {
        // The felt already moved to the next trick (3 cards); a stale trick_won from the previous
        // trick arrives (its full felt is gone). Nothing may freeze.
        val s = state(Play(1, c2), Play(2, c3), Play(0, c1), trickNumber = 2)
        val after = HokmPacing.onTrickWon(s, winner = 3)
        assertNull(after.sweepOf)
        assertEquals(s, after)
    }

    // ------------------------------------------------------------------ merge contract

    @Test
    fun `merge freezes the felt through a sweep and releases it after`() {
        val sweeping = HokmPacing.onTrickWon(state(Play(0, c1), Play(1, c2), Play(2, c3), Play(3, c4)), winner = 2)
        val merged = HokmUiMapper.merge(sweeping, snap(version = 3, currentTrick = emptyList()))
        assertEquals(4, merged.tableCards.size, "the swept trick stays on screen until the collect ends")
        assertEquals(-1, merged.turn)
        val released = HokmUiMapper.merge(HokmPacing.onTrickCollected(merged), snap(version = 4, currentTrick = listOf(Play(2, c3))))
        assertEquals(listOf(Play(2, c3)), released.tableCards)
        assertEquals(1, released.turn)
    }

    @Test
    fun `merge rebuilds the felt from the snapshot when calm`() {
        val calm = state(Play(0, c1))
        val merged = HokmUiMapper.merge(calm, snap(version = 2, currentTrick = listOf(Play(1, c2), Play(2, c3)), trickNumber = 2, turn = 2))
        assertEquals(listOf(Play(1, c2), Play(2, c3)), merged.tableCards)
        assertEquals(2, merged.tableTrickNumber)
        assertEquals(2, merged.turn)
    }
}
