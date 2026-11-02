package ir.dorehami.games.backgammon.ui

import ir.dorehami.games.backgammon.BackgammonRules
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Bluetooth table draws whatever the host wrote into my private view, so the parser that turns
 * that JSON back into a board is the one place a wire change could silently break the game. A view
 * without a full 24-point board must be refused instead of drawn as an empty one, and the signed
 * point counts, bar and off counts must survive the round trip.
 *
 * The shapes here are copied from `BackgammonSession.publicView()`: `points` is a flat array of 24
 * signed counters (+ white, - black), `bar` / `off` are `{white, black}` objects.
 */
class BackgammonSessionViewTest {

    private fun view(points: Int = BackgammonRules.POINT_COUNT): JsonObject = buildJsonObject {
        put("points", buildJsonArray {
            for (index in 0 until points) add(index + 1)
        })
        put("bar", buildJsonObject {
            put("white", 2)
            put("black", 3)
        })
        put("off", buildJsonObject {
            put("white", 14)
            put("black", 15)
        })
        put("target", 5)
        put("turn", "host")
        put("white", "host")
        put("black", "guest")
    }

    @Test
    fun `a full view is read back into a board`() {
        val position = view().position() ?: error("a complete view must parse")
        assertEquals(BackgammonRules.POINT_COUNT, position.points.size)
        assertEquals(1, position.points[0])
        assertEquals(24, position.points[23])
        assertEquals(2, position.barWhite)
        assertEquals(3, position.barBlack)
        assertEquals(14, position.offWhite)
        assertEquals(15, position.offBlack)
    }

    @Test
    fun `a short board is refused instead of drawn empty`() {
        assertNull(view(points = BackgammonRules.POINT_COUNT - 1).position())
        assertNull(JsonObject(emptyMap()).position())
    }

    @Test
    fun `the seat keys are read as plain strings`() {
        val parsed = view()
        assertEquals("host", parsed.string("turn"))
        assertEquals("guest", parsed.string("black"))
        assertNull(parsed.string("missing"))
    }
}
