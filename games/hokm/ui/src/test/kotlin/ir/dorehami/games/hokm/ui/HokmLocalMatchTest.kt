package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit
import ir.dorehami.gamesdk.MatchRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pure-JVM tests for [HokmLocalMatch] persistence (HK-011): no Android, no Compose rendering,
 * only the deterministic engine behind the solo table.
 */
class HokmLocalMatchTest {

    private fun newMatch(seed: Long = 42): HokmLocalMatch =
        HokmLocalMatch(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            playerName = "شما",
            playerCount = 4,
            targetPoints = 7,
            seed = seed,
            botLevel = HokmModule.BOT_EASY,
            onSound = {},
        )

    /** Seat of the first Ace drawn — the hakem — or -1 when the draw did not start at seat 0. */
    private fun hakemSeat(view: JsonObject?): Int {
        val draw = (view?.get("hakemDraw") as? JsonArray) ?: return -1
        for (e in draw) {
            val obj = (e as? JsonObject) ?: continue
            val card = Card.parse(obj["card"]?.jsonPrimitive?.content ?: "") ?: continue
            if (card.rank == Rank.ACE) {
                return obj["seat"]?.jsonPrimitive?.intOrNull ?: -1
            }
        }
        return -1
    }

    /** Rank codes of the human's hand, as reported by the private view. */
    private fun handCodes(view: JsonObject?): List<String> {
        return (view?.get("hand") as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive?.content } ?: emptyList()
    }

    @Test
    fun `saved record survives restore with identical state`() {
        val m1 = newMatch()
        val view1 = m1.humanView()

        // If the human (seat 0) is the hakem, play a real move so the record holds actions.
        if (hakemSeat(view1) == 0) {
            m1.chooseHokm(Suit.HEARTS)
            val code = handCodes(m1.humanView()).firstOrNull() ?: return
            Card.parse(code)?.let { m1.playCard(it) }
        }

        val record1: MatchRecord = m1.save()
        val m2 = newMatch(seed = 42) // same seed ⇒ identical fresh state
        m2.restore(record1)

        assertEquals(record1, m2.save())
        assertEquals(view1, m2.humanView())
    }

    @Test
    fun `restore rejects a tampered action log`() {
        // Find a seed where the human is the hakem, so the record contains at least one action.
        val seed = generateSequence(1L) { it + 1 }.first { seed ->
            hakemSeat(newMatch(seed = seed).humanView()) == 0
        }

        val m1 = newMatch(seed = seed)
        m1.chooseHokm(Suit.HEARTS)
        val tampered = m1.save().copy(
            actions = m1.save().actions.toMutableList().apply {
                set(0, get(0).copy(payload = buildJsonObject { put("suit", "not-a-suit") }))
            }.toList()
        )

        val m2 = newMatch(seed = seed)
        assertFailsWith<IllegalStateException> { m2.restore(tampered) }
    }
}
