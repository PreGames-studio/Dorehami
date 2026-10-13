package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.Emission
import ir.dorehami.games.hokm.HokmTestSupport.engine
import ir.dorehami.games.hokm.HokmTestSupport.int
import ir.dorehami.games.hokm.HokmTestSupport.ints
import ir.dorehami.games.hokm.HokmTestSupport.playWithBots
import ir.dorehami.games.hokm.HokmTestSupport.str
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HK-008 approved rules contract — the exact scoring/kot/tie examples for 2/3/4 players.
 *
 * Terminology: ترفند = trick, دست = hand, مسابقه = match.
 * * **کُت:** هیچ‌یک از بازنده‌ها حتی یک ترفند نبرده باشد (۷–۰ در ۲/۴ نفره؛ ۷–۰–۰ در سه‌نفره).
 * * **حاکم‌کُت:** حاکم (تیم حاکم در ۴ نفره) هیچ ترفندی نبرده و بازنده شده باشد؛ بر کُت اولویت دارد.
 * * **مساوی سه‌نفره:** تمام ۱۷ ترفند بازی شود و نتیجه ۶–۶–۵ شود ⇒ دست بی‌نتیجه، بدون امتیاز،
 *   و حاکم به نفر بعدی می‌رسد.
 */
class HokmRulesContractTest {

    private fun settle(tricks: List<Int>, reachedSeven: Int?, hakem: Int, players: Int) =
        HokmRules.settleHand(tricks, reachedSeven, hakem, players, kotPoints = 2, hakemKotPoints = 3)

    // ------------------------------------------------------------------ 4 players (two teams)

    @Test
    fun `4p - 7-3 with the hakem winning is a normal hand, 1 point`() {
        val s = settle(listOf(7, 3), reachedSeven = 0, hakem = 0, players = 4)
        assertEquals(0, s.winnerTeam)
        assertEquals(HandWinKind.NORMAL, s.kind)
        assertEquals(1, s.points)
    }

    @Test
    fun `4p - 7-0 is kot, 2 points`() {
        val s = settle(listOf(0, 7), reachedSeven = 1, hakem = 1, players = 4)
        assertEquals(1, s.winnerTeam)
        assertEquals(HandWinKind.KOT, s.kind)
        assertEquals(2, s.points)
    }

    @Test
    fun `4p - the hakem side shut out 0-7 is hakem kot, 3 points`() {
        val s = settle(listOf(0, 7), reachedSeven = 1, hakem = 0, players = 4)
        assertEquals(1, s.winnerTeam)
        assertEquals(HandWinKind.HAKEM_KOT, s.kind)
        assertEquals(3, s.points)
    }

    // ------------------------------------------------------------------ 3 players

    @Test
    fun `3p - 7-2-1 is a normal hand, 1 point`() {
        val s = settle(listOf(7, 2, 1), reachedSeven = 0, hakem = 0, players = 3)
        assertEquals(0, s.winnerTeam)
        assertEquals(HandWinKind.NORMAL, s.kind)
        assertEquals(1, s.points)
    }

    @Test
    fun `3p - 7-0-3 is a normal hand, not kot - one loser still scored`() {
        val s = settle(listOf(7, 0, 3), reachedSeven = 0, hakem = 0, players = 3)
        assertEquals(0, s.winnerTeam)
        assertEquals(HandWinKind.NORMAL, s.kind)
        assertEquals(1, s.points)
    }

    @Test
    fun `3p - 7-0-0 is kot when the hakem wins, 2 points`() {
        val s = settle(listOf(7, 0, 0), reachedSeven = 0, hakem = 0, players = 3)
        assertEquals(0, s.winnerTeam)
        assertEquals(HandWinKind.KOT, s.kind)
        assertEquals(2, s.points)
    }

    @Test
    fun `3p - 7-0-0 with the hakem shut out is hakem kot, 3 points`() {
        val s = settle(listOf(7, 0, 0), reachedSeven = 0, hakem = 1, players = 3)
        assertEquals(0, s.winnerTeam)
        assertEquals(HandWinKind.HAKEM_KOT, s.kind)
        assertEquals(3, s.points)
    }

    @Test
    fun `3p - 7-0-3 with the hakem at zero is hakem kot, 3 points`() {
        val s = settle(listOf(7, 0, 3), reachedSeven = 0, hakem = 1, players = 3)
        assertEquals(0, s.winnerTeam)
        assertEquals(HandWinKind.HAKEM_KOT, s.kind)
        assertEquals(3, s.points)
    }

    @Test
    fun `3p - 6-6-5 after all 17 tricks is void, nobody scores`() {
        val s = settle(listOf(6, 6, 5), reachedSeven = null, hakem = 2, players = 3)
        assertNull(s.winnerTeam)
        assertEquals(HandWinKind.VOID, s.kind)
        assertEquals(0, s.points)
    }

    @Test
    fun `3p - a void hand passes the hakem to the next seat`() {
        // The session reports hakemTeamWon=false on a void, so the seat moves on.
        assertEquals(0, HokmRules.nextHakem(2, hakemTeamWon = false, players = 3))
        assertEquals(2, HokmRules.nextHakem(2, hakemTeamWon = true, players = 3))
    }

    // ------------------------------------------------------------------ 2 players

    @Test
    fun `2p - 7-0 is kot and a shut-out hakem suffers hakem kot`() {
        val s1 = settle(listOf(7, 0), reachedSeven = 0, hakem = 0, players = 2)
        assertEquals(HandWinKind.KOT to 2, s1.kind to s1.points)
        val s2 = settle(listOf(0, 7), reachedSeven = 1, hakem = 0, players = 2)
        assertEquals(HandWinKind.HAKEM_KOT to 3, s2.kind to s2.points)
    }

    // ------------------------------------------------------------------ session level

    @Test
    fun `3p - real played 6-6-5 hands settle as void, keep scores and rotate the hakem`() {
        var voidSeen = false
        for (seed in 400L until 460L) {
            val e = engine(seed, count = 3)
            val emissions = playWithBots(e)
            val hands = emissions.filterIsInstance<Emission.Public>().filter { it.type == "hand_finished" }
            var prevHakem = hands.first().payload.int("hakem")
            var prevScores = listOf(0, 0, 0)
            for (h in hands) {
                val p = h.payload
                val hakem = p.int("hakem")
                assertEquals(prevHakem, hakem, "seed $seed: hakem rotation broken")
                val tricks = p.ints("tricks")
                val kind = p.str("kind")
                val points = p.int("points")
                if (kind == "void") {
                    voidSeen = true
                    assertEquals(0, points, "seed $seed: a void hand scores nothing")
                    assertEquals(17, tricks.sum(), "seed $seed: a void hand exhausts all 17 tricks")
                    assertEquals(6, tricks.max(), "seed $seed: the tied leaders have 6 tricks")
                    assertEquals(2, tricks.count { it == 6 }, "seed $seed: exactly two leaders tie 6-6-5")
                    assertEquals(prevScores, p.ints("scores"), "seed $seed: a void hand changes no score")
                } else {
                    val w = p.int("winnerTeam")
                    val others = tricks.filterIndexed { i, t -> i != w }
                    val hakemTricks = tricks[hakem]
                    val expected = when {
                        hakemTricks == 0 && hakem != w -> HandWinKind.HAKEM_KOT.id to 3
                        others.all { it == 0 } -> HandWinKind.KOT.id to 2
                        else -> HandWinKind.NORMAL.id to 1
                    }
                    assertEquals(expected, kind to points, "seed $seed hand ${p.int("handNumber")}")
                }
                prevScores = p.ints("scores")
                prevHakem = HokmRules.nextHakem(hakem, hakemTeamWon = kind != "void" && p.int("winnerTeam") == hakem, players = 3)
            }
        }
        assertTrue(voidSeen, "no 6-6-5 void hand appeared in the scanned seeds")
    }
}
