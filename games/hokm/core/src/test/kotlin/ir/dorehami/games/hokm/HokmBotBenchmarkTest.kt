package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.Phase
import ir.dorehami.games.hokm.HokmTestSupport.engine
import ir.dorehami.games.hokm.HokmTestSupport.playWithBots
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HK-027.a — benchmark harness for the pro bot: runs many seeded matches with equal seats and
 * checks that the bot stays deterministic, finishes every match, and keeps both sides competitive.
 * No randomness is introduced; the same seed always yields the same winner.
 */
class HokmBotBenchmarkTest {

    @Test
    fun `pro bot finishes every seeded match`() {
        var finished = 0
        for (seed in 700L until 720L) {
            val e = engine(seed)
            playWithBots(e)
            assertEquals(Phase.FINISHED, e.session.phase)
            finished++
        }
        assertEquals(20, finished)
    }

    @Test
    fun `replaying the same seed gives the same final state`() {
        for (seed in 720L until 725L) {
            val e1 = engine(seed)
            playWithBots(e1)
            val s1 = e1.session.publicView().toString()
            val e2 = engine(seed)
            playWithBots(e2)
            val s2 = e2.session.publicView().toString()
            assertEquals(s1, s2, "seed $seed must replay bit-for-bit")
        }
    }

    @Test
    fun `bot never plays an illegal card across many matches`() {
        // playWithBots throws on any rejected move, so reaching here means zero illegal moves.
        for (seed in 725L until 740L) {
            val e = engine(seed)
            playWithBots(e)
            assertEquals(Phase.FINISHED, e.session.phase)
        }
    }

    @Test
    fun `both sides win sometimes across seeded matches`() {
        var team0 = 0
        var team1 = 0
        for (seed in 740L until 760L) {
            val e = engine(seed)
            playWithBots(e)
            val scores = e.session.publicView()["scores"]!!.jsonArray
            val s0 = scores[0].jsonPrimitive.content.toInt()
            val s1 = scores[1].jsonPrimitive.content.toInt()
            if (s0 > s1) team0++ else team1++
        }
        // Neither side should dominate completely.
        assertTrue(team0 > 0 && team1 > 0, "team0=$team0 team1=$team1")
    }
}
