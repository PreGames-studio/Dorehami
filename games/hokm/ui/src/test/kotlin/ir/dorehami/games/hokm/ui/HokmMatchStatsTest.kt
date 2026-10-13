package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.HandWinKind
import ir.dorehami.games.hokm.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HokmMatchStatsTest {
    @Test
    fun `finished match stats are derived from the recorded hands`() {
        val state = HokmUiState(
            playerCount = 4,
            mySeat = 0,
            finished = true,
            winnerTeam = 0,
            handHistory = listOf(
                HandSummary(1, 0, 2, HandWinKind.KOT, 0, listOf(7, 0), Suit.SPADES),
                HandSummary(2, 1, 3, HandWinKind.HAKEM_KOT, 2, listOf(0, 7), Suit.HEARTS),
                HandSummary(3, 0, 1, HandWinKind.NORMAL, 0, listOf(7, 3), Suit.CLUBS),
                HandSummary(4, null, 0, HandWinKind.VOID, 1, listOf(4, 4), Suit.DIAMONDS),
            ),
        )

        assertEquals(
            HokmMatchStatsDelta(won = true, hands = 4, handsWon = 2, kots = 1),
            state.matchStatsDelta(),
        )
        assertNull(state.copy(finished = false).matchStatsDelta())
    }

    @Test
    fun `one finished match can record stats only once`() {
        var alreadyRecorded = false
        var updates = 0
        repeat(3) {
            if (shouldRecordMatchStats(finished = true, alreadyRecorded = alreadyRecorded)) {
                updates++
                alreadyRecorded = true
            }
        }

        assertEquals(1, updates)
        assertTrue(shouldRecordMatchStats(finished = true, alreadyRecorded = false))
        assertFalse(shouldRecordMatchStats(finished = true, alreadyRecorded = true))
        assertFalse(shouldRecordMatchStats(finished = false, alreadyRecorded = false))
    }
}
