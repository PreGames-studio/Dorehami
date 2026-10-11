package ir.dorehami.games.chess

import ir.dorehami.games.chess.ChessTestSupport.BLACK
import ir.dorehami.games.chess.ChessTestSupport.WHITE
import ir.dorehami.games.chess.ChessTestSupport.engine
import ir.dorehami.games.chess.ChessTestSupport.play
import ir.dorehami.gamesdk.Emission
import ir.dorehami.gamesdk.ApplyResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Contract test 2 — information. Chess has no hidden cards, but the contract still holds
 * structurally: the per-player block (`you`: colour, legal moves, offers to me) is *absent* from the
 * public view, and each private view only describes its own player.
 */
class ChessPrivacyTest {
    @Test
    fun `public view has no private block, private views only describe their owner`() {
        val e = engine()
        e.play("e2e4", "c7c5")
        val s = e.session as ChessSession
        val pub = s.publicView()
        assertFalse(ChessSession.KEY_YOU in pub, "public view must not contain the private key at all")

        for (p in listOf(WHITE, BLACK)) {
            val priv = s.privateView(p)
            val you = priv.getValue(ChessSession.KEY_YOU).jsonObject
            assertEquals(p.value, you.getValue("player").jsonPrimitive.content)
            // Everything else is identical to the public view.
            assertEquals(pub, JsonObject(priv - ChessSession.KEY_YOU))
        }
        val w = s.privateView(WHITE).getValue(ChessSession.KEY_YOU).jsonObject
        val b = s.privateView(BLACK).getValue(ChessSession.KEY_YOU).jsonObject
        assertEquals("w", w.getValue("color").jsonPrimitive.content)
        assertEquals("b", b.getValue("color").jsonPrimitive.content)
        assertTrue(w.getValue("myTurn").jsonPrimitive.boolean)
        assertFalse(b.getValue("myTurn").jsonPrimitive.boolean)
        assertTrue(w.getValue("legalMoves").jsonArray.isNotEmpty())
        assertTrue(b.getValue("legalMoves").jsonArray.isEmpty(), "the waiting player gets no move list")
    }

    @Test
    fun `start position offers exactly twenty moves to white`() {
        val s = engine().session as ChessSession
        val moves = s.privateView(WHITE).getValue(ChessSession.KEY_YOU).jsonObject.getValue("legalMoves").jsonArray
        assertEquals(20, moves.size)
    }

    @Test
    fun `emissions never use the private channel for public facts`() {
        val e = engine()
        val r = assertIs<ApplyResult.Ok>(e.submit(WHITE, ChessSession.moveAction("e2e4")!!))
        assertTrue(r.emissions.none { it is Emission.Private })
        assertTrue(r.emissions.any { it is Emission.Public && it.type == "moved" })
        assertTrue(r.emissions.any { it is Emission.Sound })
    }
}
