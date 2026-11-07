package ir.dorehami.games.esmfamil

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfigException
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.view
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The round loop of اسم فامیل, driven only through the public session API. */
class EsmFamilSessionTest {

    private fun phase(view: JsonObject): String = view["phase"]!!.jsonPrimitive.content

    private fun score(view: JsonObject, player: String): Int =
        (view["scores"] as JsonObject)[player]!!.jsonPrimitive.int

    @Test
    fun `a match starts in the first round with a letter and six columns`() {
        val session = EsmFamilTestSupport.session()
        val view = session.publicView()
        assertEquals(Phase.PLAYING, session.phase)
        assertEquals(1, view["round"]!!.jsonPrimitive.int)
        assertEquals("WRITING", phase(view))
        assertTrue(EsmFamilSheet.COMMON_LETTERS.contains(EsmFamilTestSupport.letterOf(session)))
        assertEquals(6, (view["categories"] as kotlinx.serialization.json.JsonArray).size)
    }

    @Test
    fun `the round closes when the last sheet arrives, not before`() {
        val session = EsmFamilTestSupport.session()
        val letter = EsmFamilTestSupport.letterOf(session)

        assertTrue(session.apply(EsmFamilTestSupport.sara, EsmFamilTestSupport.sheet(letter, "ارا")) is ApplyResult.Ok)
        assertEquals("WRITING", phase(session.publicView()))
        assertTrue(session.apply(EsmFamilTestSupport.reza, EsmFamilTestSupport.sheet(letter, "عید")) is ApplyResult.Ok)
        assertEquals("WRITING", phase(session.publicView()))
        assertTrue(session.apply(EsmFamilTestSupport.mina, EsmFamilTestSupport.sheet(letter, "ارا")) is ApplyResult.Ok)
        assertEquals("REVEAL", phase(session.publicView()))
    }

    @Test
    fun `points follow the open sheets and accumulate over rounds`() {
        val session = EsmFamilTestSupport.session(rounds = 3)
        val letter = EsmFamilTestSupport.letterOf(session)
        session.apply(EsmFamilTestSupport.sara, EsmFamilTestSupport.sheet(letter, "ارا"))
        session.apply(EsmFamilTestSupport.reza, EsmFamilTestSupport.sheet(letter, "ارا"))
        session.apply(EsmFamilTestSupport.mina, EsmFamilTestSupport.sheet(letter, "عید"))

        val view = session.publicView()
        // sara and reza wrote the same thing in every column ⇒ 5 × 6; mina wrote her own ⇒ 10 × 6.
        assertEquals(30, score(view, EsmFamilTestSupport.sara.value))
        assertEquals(30, score(view, EsmFamilTestSupport.reza.value))
        assertEquals(60, score(view, EsmFamilTestSupport.mina.value))
    }

    @Test
    fun `the table can close a round without the missing sheets`() {
        val session = EsmFamilTestSupport.session()
        val letter = EsmFamilTestSupport.letterOf(session)
        session.apply(EsmFamilTestSupport.sara, EsmFamilTestSupport.sheet(letter, "ارا"))
        val closed = session.apply(EsmFamilTestSupport.reza, EsmFamilTestSupport.finishRound())
        assertTrue(closed is ApplyResult.Ok)
        assertEquals("REVEAL", phase(session.publicView()))
        // reza and mina never handed anything in, so they own no points this round.
        assertEquals(0, score(session.publicView(), EsmFamilTestSupport.reza.value))
    }

    @Test
    fun `a second sheet from the same player is rejected`() {
        val session = EsmFamilTestSupport.session()
        val letter = EsmFamilTestSupport.letterOf(session)
        session.apply(EsmFamilTestSupport.sara, EsmFamilTestSupport.sheet(letter, "ارا"))
        val again = session.apply(EsmFamilTestSupport.sara, EsmFamilTestSupport.sheet(letter, "ارا"))
        assertTrue(again is ApplyResult.Rejected)
        assertEquals("game.bad_state", (again as ApplyResult.Rejected).code)
    }

    @Test
    fun `a sheet sent after the sheets were opened is rejected`() {
        val session = EsmFamilTestSupport.session()
        session.apply(EsmFamilTestSupport.mina, EsmFamilTestSupport.finishRound())
        val late = session.apply(EsmFamilTestSupport.sara, EsmFamilTestSupport.sheet('س', "ارا"))
        assertTrue(late is ApplyResult.Rejected)
        assertEquals("game.bad_state", (late as ApplyResult.Rejected).code)
    }

    @Test
    fun `next round deals a new letter and clears the sheets`() {
        val session = EsmFamilTestSupport.session(rounds = 3)
        val first = EsmFamilTestSupport.letterOf(session)
        session.apply(EsmFamilTestSupport.mina, EsmFamilTestSupport.finishRound())
        session.apply(EsmFamilTestSupport.mina, EsmFamilTestSupport.nextRound())
        val second = EsmFamilTestSupport.letterOf(session)
        assertEquals(2, session.publicView()["round"]!!.jsonPrimitive.int)
        assertEquals("WRITING", phase(session.publicView()))
        assertTrue(second != first, "the same letter must not come twice in a row")
        assertNull(session.publicView()["sheet"], "sheets belong to the round that just closed")
    }

    @Test
    fun `the match finishes after the last round with a standings table`() {
        val session = EsmFamilTestSupport.session(rounds = 3)
        repeat(3) {
            session.apply(EsmFamilTestSupport.mina, EsmFamilTestSupport.finishRound())
            session.apply(EsmFamilTestSupport.mina, EsmFamilTestSupport.nextRound())
        }
        assertEquals(Phase.FINISHED, session.phase)
        val result = assertNotNull(session.result())
        assertEquals(3, result.standings.size)
        // Nobody wrote anything, so everybody shares first place with zero points.
        assertEquals(setOf(1), result.standings.map { it.rank }.toSet())
        assertEquals(0, result.standings.first().score)
    }

    @Test
    fun `a player outside the table cannot move`() {
        val session = EsmFamilTestSupport.session()
        val stranger = ir.dorehami.protocol.PlayerId("p_stranger")
        val rejected = session.apply(stranger, EsmFamilTestSupport.finishRound())
        assertTrue(rejected is ApplyResult.Rejected)
        assertEquals("game.unknown_player", (rejected as ApplyResult.Rejected).code)
    }

    @Test
    fun `a missing column is stored as empty instead of crashing the round`() {
        val session = EsmFamilTestSupport.session()
        val letter = EsmFamilTestSupport.letterOf(session)
        val onlyName = GameAction(
            type = EsmFamilModule.ACTION_SUBMIT,
            payload = view {
                put(EsmFamilModule.PAYLOAD_ANSWERS, view { put("name", "$letter" + "ارا") })
            },
        )
        assertTrue(session.apply(EsmFamilTestSupport.sara, onlyName) is ApplyResult.Ok)
        session.apply(EsmFamilTestSupport.reza, EsmFamilTestSupport.finishRound())
        val mine = (session.privateView(EsmFamilTestSupport.sara)["mine"] as JsonObject)
        assertEquals("", mine["city"]!!.jsonPrimitive.content)
    }

    @Test
    fun `bad options are rejected in Persian`() {
        val module = EsmFamilModule
        val badRounds = assertFailsWith<GameConfigException> {
            module.validateOptions(view { put(EsmFamilSession.Options.ROUNDS, 4) })
        }
        assertTrue(badRounds.messageFa.contains("دور"))

        val tooFewColumns = assertFailsWith<GameConfigException> {
            module.validateOptions(
                view {
                    put(EsmFamilSession.Options.ROUNDS, 3)
                    put(
                        EsmFamilSession.Options.CATEGORIES,
                        buildJsonArray { add(JsonPrimitive("name")); add(JsonPrimitive("city")) },
                    )
                },
            )
        }
        assertTrue(tooFewColumns.messageFa.contains("ستون"))

        val ok = module.validateOptions(
            view {
                put(EsmFamilSession.Options.ROUNDS, 5)
                put(EsmFamilSession.Options.LETTERS, EsmFamilSheet.LETTERS_ALL)
            },
        )
        assertEquals(5, ok["rounds"]!!.jsonPrimitive.int)
    }

    @Test
    fun `legal actions follow the round phase`() {
        val session = EsmFamilTestSupport.session()
        assertEquals(
            setOf(EsmFamilModule.ACTION_SUBMIT, EsmFamilModule.ACTION_FINISH_ROUND),
            session.legalActions(EsmFamilTestSupport.sara).toSet(),
        )
        session.apply(EsmFamilTestSupport.sara, EsmFamilTestSupport.sheet(EsmFamilTestSupport.letterOf(session), "ارا"))
        assertEquals(
            listOf(EsmFamilModule.ACTION_FINISH_ROUND),
            session.legalActions(EsmFamilTestSupport.sara),
        )
        session.apply(EsmFamilTestSupport.mina, EsmFamilTestSupport.finishRound())
        assertEquals(
            listOf(EsmFamilModule.ACTION_NEXT_ROUND),
            session.legalActions(EsmFamilTestSupport.sara),
        )
    }
}
