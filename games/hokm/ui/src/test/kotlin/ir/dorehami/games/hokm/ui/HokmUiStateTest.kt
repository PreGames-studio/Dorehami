package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** JVM tests for the interaction gates exposed by HokmUiState; these do not render Compose. */
class HokmUiStateTest {

    @Test
    fun `playing input requires my calm turn`() {
        val ready = state(HandPhase.PLAYING)
        assertTrue(ready.isMyTurn)
        assertFalse(ready.copy(turn = 0).isMyTurn)
        assertFalse(ready.copy(handPhase = HandPhase.DISCARDING).isMyTurn)
        assertFalse(ready.copy(finished = true).isMyTurn)
        assertFalse(ready.copy(introShown = 0).isMyTurn)
        assertFalse(ready.copy(collectingTo = 2).isMyTurn)
        assertFalse(ready.copy(sweepOf = 2).isMyTurn)
        assertFalse(ready.copy(dealing = true).isMyTurn)
        assertFalse(ready.copy(banner = Banner("دست تمام شد")).isMyTurn)
    }

    @Test
    fun `hokm choice is enabled only for the current hakem while calm`() {
        val choosing = HokmUiState(
            playerCount = 4,
            mySeat = 1,
            hakem = 1,
            handPhase = HandPhase.CHOOSING_HOKM,
        )
        assertTrue(choosing.iChooseHokm)
        assertFalse(choosing.copy(hakem = 0).iChooseHokm)
        assertFalse(choosing.copy(handPhase = HandPhase.PLAYING).iChooseHokm)
        assertFalse(choosing.copy(introShown = 0).iChooseHokm)
    }

    @Test
    fun `discard is enabled only on my burn turn with cards to choose`() {
        val discarding = HokmUiState(
            playerCount = 2,
            mySeat = 0,
            turn = 0,
            handPhase = HandPhase.DISCARDING,
            discardCount = 3,
        )
        assertTrue(discarding.iDiscard)
        assertFalse(discarding.copy(turn = 1).iDiscard)
        assertFalse(discarding.copy(discardCount = 0).iDiscard)
        assertFalse(discarding.copy(dealing = true).iDiscard)
    }

    @Test
    fun `draw decision needs my turn and a revealed stock card`() {
        val drawing = HokmUiState(
            playerCount = 2,
            mySeat = 0,
            turn = 0,
            handPhase = HandPhase.DRAWING,
            revealed = Card(Rank.ACE, Suit.HEARTS),
        )
        assertTrue(drawing.iDraw)
        assertFalse(drawing.copy(turn = 1).iDraw)
        assertFalse(drawing.copy(revealed = null).iDraw)
        assertFalse(drawing.copy(banner = Banner("انتخاب کارت")).iDraw)
    }

    @Test
    fun `team mode is reserved for four players`() {
        assertTrue(HokmUiState(playerCount = 4).isTeamGame)
        assertFalse(HokmUiState(playerCount = 2).isTeamGame)
        assertFalse(HokmUiState(playerCount = 3).isTeamGame)
    }

    private fun state(phase: HandPhase) = HokmUiState(
        playerCount = 4,
        mySeat = 1,
        turn = 1,
        handPhase = phase,
    )
}
