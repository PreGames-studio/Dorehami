package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.Phase
import ir.dorehami.games.hokm.HokmTestSupport.engine
import ir.dorehami.games.hokm.HokmTestSupport.int
import ir.dorehami.games.hokm.HokmTestSupport.ints
import ir.dorehami.games.hokm.HokmTestSupport.playWithBots
import ir.dorehami.games.hokm.HokmTestSupport.players
import ir.dorehami.games.hokm.HokmTestSupport.str
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HokmScoringTest {

    @Test
    fun `hand points - normal, kot and hakem kot`() {
        assertEquals(HandWinKind.NORMAL to 1, HokmRules.handResult(7, listOf(3), hakemTricks = 7, hakemLost = false, kotPoints = 2, hakemKotPoints = 3))
        assertEquals(HandWinKind.NORMAL to 1, HokmRules.handResult(7, listOf(6), hakemTricks = 6, hakemLost = true, kotPoints = 2, hakemKotPoints = 3))
        assertEquals(HandWinKind.KOT to 2, HokmRules.handResult(7, listOf(0), hakemTricks = 7, hakemLost = false, kotPoints = 2, hakemKotPoints = 3))
        assertEquals(HandWinKind.HAKEM_KOT to 3, HokmRules.handResult(7, listOf(0), hakemTricks = 0, hakemLost = true, kotPoints = 2, hakemKotPoints = 3))
    }

    @Test
    fun `hakem keeps the seat after a win and passes it on after a loss`() {
        assertEquals(2, HokmRules.nextHakem(2, hakemTeamWon = true, players = 4))
        assertEquals(3, HokmRules.nextHakem(2, hakemTeamWon = false, players = 4))
        assertEquals(0, HokmRules.nextHakem(3, hakemTeamWon = false, players = 4))
    }

    @Test
    fun `across many games every hand is consistent with the rules and the match ends at the target`() {
        for (seed in 500L until 520L) {
            val e = engine(seed)
            val emissions = playWithBots(e)
            val hands = emissions.filterIsInstance<Emission.Public>().filter { it.type == "hand_finished" }
            assertTrue(hands.isNotEmpty())
            var prevHakem = e.session.publicView()["hakemDraw"]!!.let { hands.first().payload.int("hakem") }
            val running = IntArray(2)
            for (h in hands) {
                val p = h.payload
                val winner = p.int("winnerTeam")
                val tricks = p.ints("tricks")
                val points = p.int("points")
                val kind = p.str("kind")
                val hakem = p.int("hakem")
                assertEquals(prevHakem, hakem, "seed $seed: hakem rotation broken")
                assertEquals(7, tricks[winner], "winner must have exactly 7 tricks")
                assertTrue(tricks[1 - winner] in 0..6)
                val hakemLost = HokmRules.teamOf(hakem, 4) != winner
                val expected = when {
                    tricks[1 - winner] > 0 -> "normal" to 1
                    hakemLost -> "hakem_kot" to 3
                    else -> "kot" to 2
                }
                assertEquals(expected, kind to points, "seed $seed hand ${p.int("handNumber")}")
                running[winner] += points
                assertEquals(running.toList(), p.ints("scores"))
                prevHakem = HokmRules.nextHakem(hakem, hakemTeamWon = !hakemLost, players = 4)
            }
            val final = e.session.publicView().ints("scores")
            assertEquals(Phase.FINISHED, e.session.phase)
            assertTrue(final.max() >= 7, "seed $seed: winner must reach 7")
            assertTrue(final.min() < 7, "seed $seed: only one team reaches the target")
            val result = e.session.result()!!
            val winnerTeam = final.indexOf(final.max())
            val expectedWinners = if (winnerTeam == 0) listOf(players[0], players[2]) else listOf(players[1], players[3])
            assertEquals(expectedWinners, result.winners)
            assertEquals(final[winnerTeam], result.standings.first().score)
        }
    }

    @Test
    fun `a shorter target ends the match sooner`() {
        val opts = buildJsonObject { put("targetPoints", 1) }
        val e = engine(9L, opts)
        val emissions = playWithBots(e)
        val hands = emissions.filterIsInstance<Emission.Public>().count { it.type == "hand_finished" }
        assertEquals(1, hands)
        assertEquals(Phase.FINISHED, e.session.phase)
        assertEquals(1, e.session.publicView().int("targetPoints"))
    }
}
