package ir.dorehami.games.backgammon

import ir.dorehami.games.backgammon.BackgammonTestSupport.ScriptedRng
import ir.dorehami.games.backgammon.BackgammonTestSupport.black
import ir.dorehami.games.backgammon.BackgammonTestSupport.ok
import ir.dorehami.games.backgammon.BackgammonTestSupport.play
import ir.dorehami.games.backgammon.BackgammonTestSupport.roll
import ir.dorehami.games.backgammon.BackgammonTestSupport.session
import ir.dorehami.games.backgammon.BackgammonTestSupport.white
import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.protocol.ErrCodes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Contract test 1b — the session flow: roll, submit one maximal sequence, pass the turn. */
class BackgammonSessionTest {

    private fun JsonObject.ints(key: String) = (this[key] as JsonArray).map { it.jsonPrimitive.int }

    @Test
    fun `a turn is roll then one sequence and then the rival rolls`() {
        val session = session(seed = 7L, target = 5, rng = ScriptedRng(3, 1))
        assertEquals(listOf(BackgammonModule.ACTION_ROLL), session.legalActions(white))
        assertTrue(session.legalActions(black).isEmpty())

        ok(session.apply(white, roll()))
        assertEquals(1L, session.version)
        assertEquals(listOf(3, 1), session.currentDice())
        assertEquals(listOf(BackgammonModule.ACTION_PLAY), session.legalActions(white))

        ok(session.apply(white, play(23 to 3, 20 to 1)))
        assertEquals(2L, session.version)
        assertTrue(session.currentDice().isEmpty())
        assertEquals(black.value, session.publicView()["turn"]!!.jsonPrimitive.content)
        assertTrue(session.isWhiteTurn().not(), "the turn must pass to black after a completed play")

        assertEquals(listOf(BackgammonModule.ACTION_ROLL), session.legalActions(black))
    }

    @Test
    fun `the board moves exactly as the sequence says`() {
        val session = session(seed = 1L, rng = ScriptedRng(3, 1))
        session.apply(white, roll())
        session.apply(white, play(23 to 3, 20 to 1))
        val points = session.publicView().ints("points")
        assertEquals(1, points[23], "one checker left the 24 point")
        assertEquals(1, points[19], "the second die-use moved the same checker one more point")
        assertEquals(5, points[12])
    }

    @Test
    fun `an incomplete sequence is rejected with a contract code`() {
        val session = session(seed = 1L, rng = ScriptedRng(3, 1))
        session.apply(white, roll())
        val rejected = session.apply(white, play(23 to 3))
        assertIs<ApplyResult.Rejected>(rejected)
        assertEquals(ErrCodes.ILLEGAL_ACTION, rejected.code)
        assertEquals(1L, session.version, "a rejected action never advances the version")
        assertEquals(listOf(3, 1), session.currentDice())
    }

    @Test
    fun `the same die cannot be spent twice`() {
        val session = session(seed = 1L, rng = ScriptedRng(3, 3))
        session.apply(white, roll())
        // A double gives four uses of three; five uses are impossible.
        val tooMany = session.apply(white, play(23 to 3, 20 to 3, 17 to 3, 14 to 3, 11 to 3))
        assertIs<ApplyResult.Rejected>(tooMany)
    }

    @Test
    fun `rolling twice is refused`() {
        val session = session(seed = 1L, rng = ScriptedRng(4, 6))
        session.apply(white, roll())
        val again = session.apply(white, roll())
        assertIs<ApplyResult.Rejected>(again)
        assertEquals(ErrCodes.ILLEGAL_ACTION, again.code)
    }

    @Test
    fun `the rival cannot act on my turn`() {
        val session = session(seed = 1L, rng = ScriptedRng(2, 5))
        val result = session.apply(black, roll())
        assertIs<ApplyResult.Rejected>(result)
        assertEquals(ErrCodes.NOT_YOUR_TURN, result.code)
    }

    @Test
    fun `an unknown action is refused`() {
        val session = session(seed = 1L)
        val result = session.apply(white, ir.dorehami.gamesdk.GameAction("teleport"))
        assertIs<ApplyResult.Rejected>(result)
        assertEquals(ErrCodes.ILLEGAL_ACTION, result.code)
    }

    @Test
    fun `dice stay public and the private view carries only own actions`() {
        val session = session(seed = 3L, rng = ScriptedRng(5, 2))
        session.apply(white, roll())
        val public = session.publicView()
        assertEquals(listOf(5, 2), public.ints("dice"))
        assertTrue("dice" in public, "the rolled dice are on the table for everybody")

        val mine = session.privateView(white)
        assertEquals(white.value, mine["you"]!!.jsonPrimitive.content)
        assertEquals(listOf(BackgammonModule.ACTION_PLAY), (mine["actions"] as JsonArray).map { it.jsonPrimitive.content })
        val rival = session.privateView(black)
        assertEquals(black.value, rival["you"]!!.jsonPrimitive.content)
        assertTrue((rival["actions"] as JsonArray).isEmpty(), "waiting side has no legal action")
    }

    @Test
    fun `options are validated with a Persian message`() {
        val bad = buildJsonObject { put(BackgammonSession.Options.TARGET_POINTS, 11) }
        val error = runCatching { BackgammonModule.validateOptions(bad) }.exceptionOrNull()
        assertIs<ir.dorehami.gamesdk.GameConfigException>(error)
        assertTrue(error.messageFa.contains("امتیاز"), "the message is Persian")

        val good = buildJsonObject { put(BackgammonSession.Options.TARGET_POINTS, 7) }
        assertEquals(7, good["targetPoints"]!!.jsonPrimitive.int)
        assertEquals(7, BackgammonModule.validateOptions(good)["targetPoints"]!!.jsonPrimitive.int)
    }
}
