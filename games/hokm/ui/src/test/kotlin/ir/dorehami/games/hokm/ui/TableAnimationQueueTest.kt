package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.HandWinKind
import ir.dorehami.games.hokm.Play
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HK-010 — the bounded, versioned animation queue and the pure event rules.
 *
 * These tests cover the three promises of the stage without Compose or a device:
 * a burst never loses or repeats a step, a snapshot of a later hand silences the old one, and a
 * duplicate or stale delivery does not put a card on the table twice.
 */
class TableAnimationQueueTest {

    private fun collect(hand: Int, trick: Int, winner: Int = 0, revision: Long = 0) =
        TableStep.CollectTrick(winner, hand, trick, revision)

    private fun banner(hand: Int, trick: Int, title: String) =
        TableStep.ShowBanner(Banner(title), 1300L, hand, trick, 0L)

    // ------------------------------------------------------------------ ordering and bursts

    @Test
    fun `a burst of tricks plays in the order it arrived`() {
        val queue = TableAnimQueue()
        // The old single-slot design kept only the last trick; both must survive now.
        assertTrue(queue.offer(collect(hand = 1, trick = 2, winner = 1)))
        assertTrue(queue.offer(collect(hand = 1, trick = 3, winner = 2)))
        assertTrue(queue.offer(banner(hand = 1, trick = 3, title = "دست تمام")))

        assertEquals(3, queue.size)
        assertEquals(2, (queue.poll() as TableStep.CollectTrick).trickNumber)
        assertEquals(3, (queue.poll() as TableStep.CollectTrick).trickNumber)
        assertEquals("دست تمام", (queue.poll() as TableStep.ShowBanner).banner.title)
        assertTrue(queue.isEmpty)
        assertNull(queue.poll())
    }

    @Test
    fun `the queue is bounded and drops the oldest step first`() {
        val queue = TableAnimQueue(capacity = 3)
        repeat(5) { i -> queue.offer(collect(hand = 1, trick = i)) }

        assertEquals(3, queue.size, "the queue never grows past its capacity")
        assertEquals(2, queue.droppedCount, "two stale steps were dropped")
        assertEquals(2, (queue.peek() as TableStep.CollectTrick).trickNumber, "the oldest remaining step is first")
    }

    // ------------------------------------------------------------------ duplicates

    @Test
    fun `an identical event is queued once`() {
        val queue = TableAnimQueue()
        assertTrue(queue.offer(collect(hand = 2, trick = 4, winner = 1)))
        assertFalse(queue.offer(collect(hand = 2, trick = 4, winner = 1)), "same hand/trick ⇒ duplicate")
        assertEquals(1, queue.size)
    }

    @Test
    fun `two banners with the same title in the same trick are one step`() {
        val queue = TableAnimQueue()
        assertTrue(queue.offer(banner(1, 0, "حکم: دل")))
        assertFalse(queue.offer(banner(1, 0, "حکم: دل")))
        assertTrue(queue.offer(banner(1, 0, "دست‌ها کامل شد")), "a different banner is a different step")
        assertEquals(2, queue.size)
    }

    // ------------------------------------------------------------------ staleness by hand

    @Test
    fun `a snapshot of a later hand drops the previous hand's steps`() {
        val queue = TableAnimQueue()
        queue.offer(banner(hand = 3, trick = 13, title = "کُت!"))
        queue.offer(collect(hand = 3, trick = 13))
        queue.offer(collect(hand = 4, trick = 1))

        assertEquals(2, queue.dropBeforeHand(4), "both steps of hand 3 are dropped")
        assertEquals(1, queue.size)
        assertEquals(4, queue.peek()!!.handNumber)
    }

    @Test
    fun `nothing is dropped while the queue is still in the current hand`() {
        val queue = TableAnimQueue()
        queue.offer(collect(hand = 5, trick = 2))
        queue.offer(banner(hand = 5, trick = 3, title = "دست تمام"))

        assertEquals(0, queue.dropBeforeHand(5))
        assertEquals(2, queue.size)
        assertFalse(queue.hasStepsBeforeHand(5))
    }

    @Test
    fun `the table can ask whether anything belongs to an old hand`() {
        val queue = TableAnimQueue()
        queue.offer(collect(hand = 2, trick = 5))
        assertTrue(queue.hasStepsBeforeHand(3), "hand 2 is history when the snapshot says 3")
        assertFalse(queue.hasStepsBeforeHand(2))
        assertFalse(queue.hasStepsBeforeHand(1))
    }

    @Test
    fun `an empty queue never reports anything to drop`() {
        val queue = TableAnimQueue()
        assertEquals(0, queue.dropBeforeHand(7))
        assertTrue(queue.isEmpty)
        assertNull(queue.peek())
    }

    // ------------------------------------------------------------------ cards on the table

    @Test
    fun `a duplicate card event does not put the same card on the table twice`() {
        val play = Play(1, Card(Rank.ACE, Suit.SPADES))
        val once = TableEvents.appendCard(emptyList(), play)
        assertFalse(once.duplicate)
        assertEquals(1, once.plays.size)

        val twice = TableEvents.appendCard(once.plays, play)
        assertTrue(twice.duplicate)
        assertEquals(once.plays, twice.plays, "the table is unchanged by a repeated delivery")
    }

    @Test
    fun `the turn moves on after a real card and stays closed for a duplicate`() {
        val first = Play(1, Card(Rank.SEVEN, Suit.HEARTS))
        val second = Play(2, Card(Rank.EIGHT, Suit.HEARTS))

        val one = TableEvents.appendCard(emptyList(), first)
        assertEquals(2, TableEvents.turnAfter(one, first, players = 4))

        val two = TableEvents.appendCard(one.plays, second)
        assertEquals(3, TableEvents.turnAfter(two, second, players = 4))

        val again = TableEvents.appendCard(two.plays, second)
        assertTrue(again.duplicate)
        assertEquals(-1, TableEvents.turnAfter(again, second, players = 4), "a duplicate changes nobody's turn")
    }

    @Test
    fun `the fourth card closes the trick so nobody is on turn`() {
        var shown = emptyList<Play>()
        for (seat in 0 until 4) {
            val play = Play(seat, Card(Rank.TWO, Suit.CLUBS))
            shown = TableEvents.appendCard(shown, play).plays
        }
        val last = Play(3, Card(Rank.TWO, Suit.CLUBS))
        assertEquals(-1, TableEvents.turnAfter(TableEvents.TableCards(shown, duplicate = false), last, players = 4))
    }

    // ------------------------------------------------------------------ the hand banner is presentation only

    @Test
    fun `a kot banner is celebratory for my side and plain for theirs`() {
        val summary = HandSummary(2, winnerTeam = 0, points = 2, kind = HandWinKind.KOT, hakem = 0, tricks = listOf(7, 0))
        val mine = TableEvents.handBanner(summary, players = 4, myTeam = 0) { "تیم حریف" }
        assertEquals("کُت!", mine.title)
        assertEquals("۲ امتیاز برای تیم حریف", mine.subtitle)
        assertTrue(mine.big)

        val theirs = TableEvents.handBanner(summary, players = 4, myTeam = 1) { "تیم حریف" }
        assertEquals("کُت شدیم!", theirs.title)
    }

    @Test
    fun `the void hand says so and shows no points`() {
        val summary = HandSummary(4, winnerTeam = null, points = 0, kind = HandWinKind.VOID, hakem = 1, tricks = listOf(6, 6, 5))
        val banner = TableEvents.handBanner(summary, players = 3, myTeam = 0) { "x" }
        assertEquals("مساوی — این دست بی‌نتیجه ماند", banner.title)
        assertNull(banner.subtitle, "a void hand awards nothing, so nothing is claimed")
    }

    @Test
    fun `the hand sound follows the same summary as the banner`() {
        val normalLoss = HandSummary(1, winnerTeam = 1, points = 1, kind = HandWinKind.NORMAL, hakem = 0, tricks = listOf(3, 7))
        assertEquals("hand_lost", TableEvents.handSound(normalLoss, myTeam = 0))
        assertEquals("kot", TableEvents.handSound(normalLoss.copy(kind = HandWinKind.KOT), myTeam = 1))
    }

    // ------------------------------------------------------------------ sending gate

    @Test
    fun `a tapped card cannot be sent twice while the first move is in flight`() {
        val waiting = HokmUiState(playerCount = 4, mySeat = 1, turn = 1, handPhase = HandPhase.PLAYING)
        assertTrue(waiting.isMyTurn, "normally the table accepts my tap")

        val sending = waiting.copy(isSendingMove = true)
        assertFalse(sending.isMyTurn, "while a move is in flight the table refuses another one")
        assertFalse(sending.copy(handPhase = HandPhase.CHOOSING_HOKM, hakem = 1).iChooseHokm)
        assertFalse(sending.copy(handPhase = HandPhase.DRAWING, revealed = Card(Rank.ACE, Suit.HEARTS)).iDraw)
    }

    @Test
    fun `the sending gate clears only when the revision actually advances`() {
        assertFalse(
            TableEvents.sendingAfterSnapshot(oldRevision = 10, newRevision = 11, wasSending = true),
            "a newer revision is the answer to my move",
        )
        assertTrue(
            TableEvents.sendingAfterSnapshot(oldRevision = 10, newRevision = 10, wasSending = true),
            "re-reading the same view is not an answer — a second tap must stay blocked",
        )
        assertTrue(
            TableEvents.sendingAfterSnapshot(oldRevision = 10, newRevision = 9, wasSending = true),
            "a stale snapshot cannot clear the gate either",
        )
        assertFalse(TableEvents.sendingAfterSnapshot(4, 4, wasSending = false), "nothing to clear")
    }

    @Test
    fun `the sending gate does not hide the rest of the table`() {
        val sending = HokmUiState(playerCount = 4, mySeat = 1, turn = 2, isSendingMove = true)
        assertEquals(1, sending.mySeat)
        assertFalse(sending.finished, "waiting for an answer is not a finished match")
        assertTrue(HokmUiState(playerCount = 4).copy(isSendingMove = true).isTeamGame)
    }
}
