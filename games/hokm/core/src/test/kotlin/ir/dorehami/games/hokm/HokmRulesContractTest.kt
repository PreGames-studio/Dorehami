package ir.dorehami.games.hokm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HK-008 — the approved, versioned rules contract (v1.0), expressed as boundary examples.
 *
 * The user approved keeping the existing behaviour: in a three-player hand, kot means **any**
 * losing side took zero tricks; a tie at exhaustion is void with no points and a rotating hakem.
 * These tests pin those examples so neither the docs nor a future refactor can silently drift.
 */
class HokmRulesContractTest {

    private val kot = HokmModule.DEFAULT_KOT_POINTS
    private val hakemKot = HokmModule.DEFAULT_HAKEM_KOT_POINTS

    private fun settle(tricks: List<Int>, hakemTeam: Int, reachedSeven: Int?) =
        HokmRules.settleHand(tricks, hakemTeam, reachedSeven, kot, hakemKot)

    @Test
    fun `four players - normal kot and hakem kot examples`() {
        assertEquals(
            HokmRules.HandOutcome(0, HandWinKind.NORMAL, 1),
            settle(listOf(7, 6), hakemTeam = 0, reachedSeven = 0),
        )
        assertEquals(
            HokmRules.HandOutcome(0, HandWinKind.KOT, kot),
            settle(listOf(7, 0), hakemTeam = 0, reachedSeven = 0),
        )
        // The hakem's own side lost without a single trick: the more valuable kot.
        assertEquals(
            HokmRules.HandOutcome(1, HandWinKind.HAKEM_KOT, hakemKot),
            settle(listOf(0, 7), hakemTeam = 0, reachedSeven = 1),
        )
        // The hakem won; only the ordinary kot applies.
        assertEquals(
            HokmRules.HandOutcome(1, HandWinKind.KOT, kot),
            settle(listOf(0, 7), hakemTeam = 1, reachedSeven = 1),
        )
    }

    @Test
    fun `two players - the same two-side scoring examples hold`() {
        assertEquals(
            HokmRules.HandOutcome(0, HandWinKind.NORMAL, 1),
            settle(listOf(7, 4), hakemTeam = 1, reachedSeven = 0),
        )
        assertEquals(
            HokmRules.HandOutcome(1, HandWinKind.HAKEM_KOT, hakemKot),
            settle(listOf(0, 7), hakemTeam = 0, reachedSeven = 1),
        )
    }

    @Test
    fun `three players - any opponent on zero tricks is a kot`() {
        // This is the decision that the README used to describe as "7-0 against both".
        val examples = listOf(
            listOf(7, 5, 0),
            listOf(7, 0, 5),
            listOf(7, 0, 0),
        )
        examples.forEach { tricks ->
            assertEquals(
                HokmRules.HandOutcome(0, HandWinKind.KOT, kot),
                settle(tricks, hakemTeam = 0, reachedSeven = 0),
                "expected kot for $tricks",
            )
        }
    }

    @Test
    fun `three players - a win with both opponents scoring is only one point`() {
        assertEquals(
            HokmRules.HandOutcome(0, HandWinKind.NORMAL, 1),
            settle(listOf(7, 6, 4), hakemTeam = 0, reachedSeven = 0),
        )
        assertEquals(
            HokmRules.HandOutcome(1, HandWinKind.NORMAL, 1),
            settle(listOf(3, 7, 2), hakemTeam = 2, reachedSeven = 1),
        )
    }

    @Test
    fun `three players - hakem kot needs the hakem side on zero and losing`() {
        assertEquals(
            HokmRules.HandOutcome(1, HandWinKind.HAKEM_KOT, hakemKot),
            settle(listOf(0, 7, 0), hakemTeam = 0, reachedSeven = 1),
        )
        // Hakem had zero but was on the winning side: the other loser decides the ordinary kot.
        assertEquals(
            HokmRules.HandOutcome(0, HandWinKind.KOT, kot),
            settle(listOf(7, 0, 0), hakemTeam = 0, reachedSeven = 0),
        )
    }

    @Test
    fun `three players - a tie when all 17 tricks are played is void with zero points`() {
        listOf(listOf(6, 6, 5), listOf(6, 5, 6), listOf(5, 6, 6)).forEachIndexed { i, tricks ->
            assertEquals(
                HokmRules.HandOutcome(null, HandWinKind.VOID, 0),
                settle(tricks, hakemTeam = i, reachedSeven = null),
                "expected void for $tricks",
            )
        }
    }

    @Test
    fun `a 17 trick three player hand can never have a unique leader below seven`() {
        // 17 = 6+6+5 is the only shape with nobody reaching 7; two leaders always tie.
        for (a in 0..6) for (b in 0..6) for (c in 0..6) {
            if (a + b + c != 17) continue
            val tricks = listOf(a, b, c)
            assertNull(HokmRules.winnerWhenCardsExhausted(tricks), "unexpected unique winner for $tricks")
            assertEquals(HandWinKind.VOID, settle(tricks, 0, null).kind)
        }
    }

    @Test
    fun `exhaustion with a unique leader still settles normally for other variants`() {
        assertEquals(1, HokmRules.winnerWhenCardsExhausted(listOf(5, 6))!!)
        assertEquals(
            HokmRules.HandOutcome(1, HandWinKind.NORMAL, 1),
            settle(listOf(5, 6), hakemTeam = 0, reachedSeven = null),
        )
    }

    @Test
    fun `hakem rotates after a void or a loss and stays after a win`() {
        assertEquals(2, HokmRules.nextHakem(1, hakemTeamWon = false, players = 3))
        assertEquals(0, HokmRules.nextHakem(2, hakemTeamWon = false, players = 3))
        assertEquals(1, HokmRules.nextHakem(1, hakemTeamWon = true, players = 3))
        assertEquals(3, HokmRules.nextHakem(2, hakemTeamWon = false, players = 4))
    }

    @Test
    fun `seven tricks and seven target points are different numbers`() {
        assertEquals(7, HokmRules.TRICKS_TO_WIN_HAND)
        assertEquals(listOf(1, 3, 5, 7), HokmModule.ALLOWED_TARGETS)
        assertEquals(17, HokmRules.cardsPerPlayer(3))
        assertEquals(51, HokmRules.deckFor(3).size)
        assertEquals(52, HokmRules.deckFor(4).size)
        assertTrue(HokmRules.partnerOf(1, 3) == null)
        assertFailsWith<IllegalArgumentException> {
            HokmRules.handResult(0, 0, 0, false, kot, hakemKot)
        }
    }
}
