package ir.dorehami.games.backgammon

import ir.dorehami.games.backgammon.BackgammonTestSupport.engine
import ir.dorehami.games.backgammon.BackgammonTestSupport.playWithBots
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.GameRegistry
import ir.dorehami.gamesdk.MatchVerifier
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.Verdict
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Contract test 3 — determinism: the same seed replays the same dice, moves and digest. */
class BackgammonDeterminismTest {

    private val verifier = MatchVerifier(GameRegistry(BackgammonModule))

    @Test
    fun `a full bot match finishes and replays to the same digest`() {
        for (seed in listOf(1L, 3L, 11L)) {
            val engine = engine(seed = seed, target = 3)
            playWithBots(engine)
            assertEquals(Phase.FINISHED, engine.session.phase, "match must reach the target")
            val record = engine.record()
            val replayed = GameEngine.replay(BackgammonModule, record)
            assertEquals(record.digest, replayed.digest())
            assertEquals(engine.session.result(), replayed.session.result())
            assertIs<Verdict.Verified>(verifier.verify(record))
        }
    }

    @Test
    fun `a tampered record is rejected`() {
        val engine = engine(seed = 5L, target = 1)
        playWithBots(engine)
        val record = engine.record()
        assertTrue(record.actions.isNotEmpty())

        val playIndex = record.actions.indexOfFirst { it.type == BackgammonModule.ACTION_PLAY }
        assertTrue(playIndex >= 0, "a finished match contains at least one played turn")

        val dropped = record.copy(actions = record.actions.toMutableList().also { it.removeAt(playIndex) })
        assertIs<Verdict.Rejected>(verifier.verify(dropped))

        // A die that does not exist cannot be replayed, so the record cannot claim it happened.
        val impossibleDie = buildJsonObject {
            put(
                BackgammonModule.PAYLOAD_MOVES,
                buildJsonArray { add(buildJsonObject { put("from", 23); put("die", 7) }) },
            )
        }
        val forged = record.copy(
            actions = record.actions.toMutableList().also { it[playIndex] = it[playIndex].copy(payload = impossibleDie) },
        )
        assertIs<Verdict.Rejected>(verifier.verify(forged))
    }

    @Test
    fun `bot uses no randomness of its own`() {
        fun log(seed: Long): List<String> {
            val engine = engine(seed = seed, target = 1)
            playWithBots(engine)
            return engine.actions.map { "${it.player.value}:${it.type}:${it.payload}" }
        }
        assertEquals(log(21L), log(21L))
    }
}
