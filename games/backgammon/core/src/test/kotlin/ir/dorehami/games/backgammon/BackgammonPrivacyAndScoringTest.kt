package ir.dorehami.games.backgammon

import ir.dorehami.games.backgammon.BackgammonTestSupport.black
import ir.dorehami.games.backgammon.BackgammonTestSupport.engine
import ir.dorehami.games.backgammon.BackgammonTestSupport.playWithBots
import ir.dorehami.games.backgammon.BackgammonTestSupport.white
import ir.dorehami.gamesdk.Phase
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contract tests 2 and 4 — no hidden information leaks, and the match ends with a sane table. */
class BackgammonPrivacyAndScoringTest {

    @Test
    fun `the public view never carries private bookkeeping`() {
        val session = BackgammonTestSupport.session(seed = 4L)
        val public = session.publicView()
        listOf("you", "actions", "seed", "rng", "snapshot").forEach { key ->
            assertTrue(key !in public, "public view must not contain '$key'")
        }
        assertTrue("points" in public)
        assertTrue("dice" in public)
    }

    @Test
    fun `each private view is addressed to one player only`() {
        val session = BackgammonTestSupport.session(seed = 4L)
        val mine = session.privateView(white)
        val rival = session.privateView(black)
        assertEquals(white.value, mine["you"]!!.jsonPrimitive.content)
        assertEquals(black.value, rival["you"]!!.jsonPrimitive.content)
        // Both players see the same board: backgammon has no hidden information by design.
        assertEquals(mine["points"], rival["points"])
    }

    @Test
    fun `a match ends exactly at the target with a ranked table`() {
        val engine = engine(seed = 8L, target = 1)
        playWithBots(engine)
        assertEquals(Phase.FINISHED, engine.session.phase)
        val result = assertNotNull(engine.session.result())
        assertEquals(2, result.standings.size)
        assertEquals(1, result.standings[0].rank)
        assertEquals(2, result.standings[1].rank)
        assertTrue(result.standings[0].score >= 1)
        assertEquals(1, result.winners.size)
        assertTrue(session_ended(engine))
    }

    @Test
    fun `a longer match needs more than one game`() {
        val engine = engine(seed = 4L, target = 7)
        playWithBots(engine)
        val snapshot = engine.session.snapshot()
        assertEquals(Phase.FINISHED, engine.session.phase)
        val score = snapshot["score"]!! as kotlinx.serialization.json.JsonObject
        val whiteScore = score["white"]!!.jsonPrimitive.int
        val blackScore = score["black"]!!.jsonPrimitive.int
        assertTrue(maxOf(whiteScore, blackScore) >= 7, "the winner reached the target")
        assertTrue((snapshot["game"]!!).jsonPrimitive.int >= 1)
    }

    @Test
    fun `finished match refuses further actions`() {
        val engine = engine(seed = 8L, target = 1)
        playWithBots(engine)
        val after = engine.submit(white, BackgammonTestSupport.roll())
        assertTrue(after is ir.dorehami.gamesdk.ApplyResult.Rejected)
    }

    private fun session_ended(engine: ir.dorehami.gamesdk.GameEngine): Boolean =
        (engine.session.snapshot()["phase"] as? JsonPrimitive)?.content == Phase.FINISHED.name

    private fun JsonArray.toInts() = map { it.jsonPrimitive.int }
}
