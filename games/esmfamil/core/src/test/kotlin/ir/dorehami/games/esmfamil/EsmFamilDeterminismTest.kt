package ir.dorehami.games.esmfamil

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameEngine
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Determinism (ADR-0003): the same seed and the same action log must rebuild the same match —
 * same letters, same sheets, same points, same digest.
 */
class EsmFamilDeterminismTest {

    /** Plays a whole match through the engine the way a table would. */
    private fun playFullMatch(seed: Long): GameEngine {
        val engine = EsmFamilTestSupport.engine(seed = seed, rounds = 3)
        val players = listOf(EsmFamilTestSupport.sara, EsmFamilTestSupport.reza, EsmFamilTestSupport.mina)
        repeat(3) {
            val letter = (engine.session.publicView()["letter"] as kotlinx.serialization.json.JsonPrimitive).content.first()
            players.forEachIndexed { index, player ->
                val word = if (index == 2) "عید" else "ارا"
                val result = engine.submit(player, EsmFamilTestSupport.sheet(letter, word))
                assertTrue(result is ApplyResult.Ok, "sheet rejected: $result")
            }
            assertTrue(engine.submit(players[0], EsmFamilTestSupport.nextRound()) is ApplyResult.Ok)
        }
        return engine
    }

    @Test
    fun `the same seed deals the same first letter`() {
        val a = EsmFamilTestSupport.session(seed = 42L)
        val b = EsmFamilTestSupport.session(seed = 42L)
        assertEquals(EsmFamilTestSupport.letterOf(a), EsmFamilTestSupport.letterOf(b))
    }

    @Test
    fun `different seeds can deal different letters`() {
        val letters = (1L..24L).map { EsmFamilTestSupport.letterOf(EsmFamilTestSupport.session(seed = it)) }.toSet()
        assertTrue(letters.size > 1, "the letter must actually depend on the seed")
    }

    @Test
    fun `replaying a record reproduces the same digest`() {
        val engine = playFullMatch(seed = 2026L)
        val record = engine.record()
        assertEquals(12, record.actions.size, "three rounds × (three sheets + one next)")

        val replayed = GameEngine.replay(EsmFamilModule, record)
        assertEquals(engine.digest(), replayed.digest())
        assertEquals(engine.session.result(), replayed.session.result())
        assertEquals(engine.session.privateView(EsmFamilTestSupport.sara), replayed.session.privateView(EsmFamilTestSupport.sara))
    }

    @Test
    fun `a tampered sheet is refused on replay`() {
        val engine = playFullMatch(seed = 99L)
        val record = engine.record()
        val tampered = record.copy(
            actions = record.actions.map { action ->
                if (action.type == EsmFamilModule.ACTION_SUBMIT) {
                    action.copy(payload = kotlinx.serialization.json.buildJsonObject {
                        put(
                            EsmFamilModule.PAYLOAD_ANSWERS,
                            kotlinx.serialization.json.buildJsonObject {
                                put("name", ir.dorehami.protocol.PlayerId("x").value)
                            },
                        )
                    })
                } else {
                    action
                }
            },
        )
        // A sheet that no longer matches the draw has a different letter, so the score changes and
        // the digest cannot match — that is the anti-cheat primitive the server relies on.
        val replayed = GameEngine.replay(EsmFamilModule, tampered)
        assertNotEquals(engine.digest(), replayed.digest())
    }
}
