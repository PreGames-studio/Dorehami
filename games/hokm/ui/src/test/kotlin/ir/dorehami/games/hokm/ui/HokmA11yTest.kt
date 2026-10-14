package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.Play
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HK-013 — the words a screen reader speaks on the Hokm table, pinned by test. Pure JVM: these
 * assertions are about copy and about the state the copy is derived from, not about rendering.
 */
class HokmA11yTest {

    private val aceSpades = Card(Rank.ACE, Suit.SPADES)
    private val tenHearts = Card(Rank.TEN, Suit.HEARTS)

    @Test
    fun `a card is spoken as rank then suit, never as its wire code`() {
        assertEquals("آس پیک", HokmA11y.cardLabel(aceSpades))
        assertEquals("۱۰ دل", HokmA11y.cardLabel(tenHearts))
        assertEquals("شاه گشنیز", HokmA11y.cardLabel(Card(Rank.KING, Suit.CLUBS)))
        assertFalse(HokmA11y.cardLabel(aceSpades).contains("AS"), "the wire id must not be read aloud")
    }

    @Test
    fun `every card in the deck has a speakable label`() {
        for (suit in Suit.entries) {
            for (rank in Rank.entries) {
                val label = HokmA11y.cardLabel(Card(rank, suit))
                assertTrue(label.isNotBlank(), "$rank $suit must have a label")
                assertTrue(label.contains(suit.titleFa), "$label must name the suit")
                assertTrue(label.contains(rank.labelFa), "$label must name the rank")
            }
        }
    }

    @Test
    fun `the hand summary gives a count and then the cards, in Persian digits`() {
        val label = HokmA11y.handLabel(listOf(aceSpades, tenHearts))
        assertEquals("دست شما: ۲ کارت — آس پیک، ۱۰ دل", label)
        assertEquals("دست خالی", HokmA11y.handLabel(emptyList()))
        assertTrue(HokmA11y.handLabel(List(13) { aceSpades }).startsWith("دست شما: ۱۳ کارت"), "13 → ۱۳")
    }

    @Test
    fun `a seat line carries count, tricks and the markers that matter`() {
        val plain = HokmA11y.seatLabel(seat = 2, name = "رضا", cardCount = 9, tricksWon = 3, isTurn = false, isHakem = false, isMe = false)
        assertEquals("صندلی ۲ — رضا — ۹ کارت — ۳ ترفند", plain)

        val mine = HokmA11y.seatLabel(0, "شما", 13, 0, isTurn = true, isHakem = true, isMe = true)
        assertTrue(mine.startsWith("شما — "), "my seat is announced first: $mine")
        assertTrue(mine.endsWith("نوبت اوست"))
        assertTrue(mine.contains("حاکم"))

        // A blank name must not leave a dangling separator.
        assertFalse(HokmA11y.seatLabel(1, " ", 5, 1, false, false, false).contains(" —  — "))
    }

    @Test
    fun `hokm and phase labels match the words the table already uses`() {
        assertEquals("حکم: پیک", HokmA11y.hokmLabel(Suit.SPADES))
        assertEquals("حکم انتخاب نشده", HokmA11y.hokmLabel(null))
        assertEquals("انتخاب حکم", HokmA11y.phaseLabel(HandPhase.CHOOSING_HOKM))
        assertEquals("سوزاندن کارت", HokmA11y.phaseLabel(HandPhase.DISCARDING))
        assertEquals("کشیدن از دسته", HokmA11y.phaseLabel(HandPhase.DRAWING))
        assertEquals("بازی ترفند", HokmA11y.phaseLabel(HandPhase.PLAYING))
    }

    @Test
    fun `progress and trick labels use Persian digits and seat numbers`() {
        assertEquals("دست ۲، ترفند ۳", HokmA11y.progressLabel(handNumber = 2, trickNumber = 3))
        assertEquals(
            "روی میز: آس پیک از صندلی ۱، ۱۰ دل از صندلی ۳",
            HokmA11y.trickLabel(listOf(Play(1, aceSpades), Play(3, tenHearts))),
        )
        assertEquals("روی میز خالی است", HokmA11y.trickLabel(emptyList()))
    }

    @Test
    fun `the score line is told from my side, with the target`() {
        assertEquals(
            "امتیاز: تیم شما ۳، تیم حریف ۱، هدف ۷",
            HokmA11y.scoreLabel(scores = listOf(3, 1), myTeam = 0, targetPoints = 7),
        )
        assertEquals(
            "امتیاز: تیم شما ۱، تیم حریف ۳، هدف ۷",
            HokmA11y.scoreLabel(scores = listOf(3, 1), myTeam = 1, targetPoints = 7),
            "the same numbers must flip when I sit on the other team",
        )
    }

    @Test
    fun `the turn label says what to do now, in priority order`() {
        val base = HokmUiState(playerCount = 4, mySeat = 0, handPhase = HandPhase.PLAYING, turn = 0, seats = seats())
        assertEquals("نوبت شماست: یک کارت بازی کنید", HokmA11y.turnLabel(base))

        assertEquals("بازی تمام شد", HokmA11y.turnLabel(base.copy(finished = true)))
        assertEquals("خطا: کارت نامجاز", HokmA11y.turnLabel(base.copy(error = "کارت نامجاز")), "an error outranks the turn")
        assertEquals(
            "نوبت شماست: حکم را انتخاب کنید",
            HokmA11y.turnLabel(base.copy(handPhase = HandPhase.CHOOSING_HOKM, hakem = 0, turn = -1)),
        )
        assertEquals(
            "نوبت شماست: ۳ کارت بسوزانید",
            HokmA11y.turnLabel(base.copy(playerCount = 2, handPhase = HandPhase.DISCARDING, turn = 0, discardCount = 3)),
        )
        assertEquals(
            "نوبت شماست: کارت دسته را نگه دارید یا رد کنید",
            HokmA11y.turnLabel(base.copy(playerCount = 2, handPhase = HandPhase.DRAWING, turn = 0, revealed = aceSpades)),
        )
        assertEquals("نوبت مریم است", HokmA11y.turnLabel(base.copy(turn = 2)))
        // Nobody to move and nothing pending: say so instead of an empty line.
        assertEquals("در انتظار حرکت حریف", HokmA11y.turnLabel(base.copy(turn = -1)))
    }

    @Test
    fun `a hand card says whether it is playable and whether it is selected`() {
        assertEquals("آس پیک — قابل بازی", HokmA11y.handCardLabel(aceSpades, legal = true, selected = false))
        assertEquals("آس پیک — غیرمجاز", HokmA11y.handCardLabel(aceSpades, legal = false, selected = false))
        assertEquals("آس پیک — انتخاب‌شده — قابل بازی", HokmA11y.handCardLabel(aceSpades, legal = true, selected = true))
    }

    @Test
    fun `the table line reports phase, progress, the felt and hokm`() {
        val state = HokmUiState(
            playerCount = 4,
            mySeat = 0,
            handPhase = HandPhase.PLAYING,
            handNumber = 2,
            trickNumber = 3,
            tableCards = listOf(Play(1, aceSpades)),
            hokm = Suit.SPADES,
            seats = seats(),
        )
        assertEquals(
            "بازی ترفند — دست ۲، ترفند ۳ — روی میز: آس پیک از صندلی ۱ — حکم: پیک",
            HokmA11y.tableLabel(state),
        )
        // A calm, empty table still says something useful.
        assertEquals("انتخاب حکم — دست ۱، ترفند ۰", HokmA11y.tableLabel(HokmUiState(playerCount = 4)))
    }

    private fun seats() = listOf(
        SeatInfo(0, "شما", isBot = false),
        SeatInfo(1, "رضا", isBot = true),
        SeatInfo(2, "مریم", isBot = true),
        SeatInfo(3, "امیر", isBot = true),
    )
}
