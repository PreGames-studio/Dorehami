package ir.dorehami.games.chess

import ir.dorehami.games.chess.ChessTestSupport.engine
import ir.dorehami.games.chess.ChessTestSupport.options
import ir.dorehami.games.chess.ChessTestSupport.playOutWithBots
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.GameRegistry
import ir.dorehami.gamesdk.MatchVerifier
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.Verdict
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Contract test 3 — determinism: replaying the record gives the same digest; tampering is caught. */
class ChessDeterminismTest {
    private val verifier = MatchVerifier(GameRegistry(ChessModule))

    @Test
    fun `bot game replays to the same digest and verifies`() {
        val e = engine(seed = 42L, options = options(botLevel = 1, colors = "random"))
        e.playOutWithBots()
        assertEquals(Phase.FINISHED, e.session.phase)
        val record = e.record()
        val replayed = GameEngine.replay(ChessModule, record)
        assertEquals(record.digest, replayed.digest())
        assertEquals(e.session.result(), replayed.session.result())
        assertIs<Verdict.Verified>(verifier.verify(record))
    }

    @Test
    fun `the same seed and level always produce the same game`() {
        fun run(): List<String> {
            val e = engine(seed = 9L, options = options(botLevel = 2))
            e.playOutWithBots(maxActions = 60)
            return (e.session as ChessSession).moves.map { it.uci }
        }
        val a = run()
        assertEquals(a, run())
        assertEquals(60, a.size)
    }

    @Test
    fun `a tampered record is rejected`() {
        val e = engine(seed = 3L, options = options(botLevel = 1))
        e.playOutWithBots(maxActions = 30)
        val record = e.record()

        // Change one move to a different (still well-formed) move.
        val i = 10
        val original = record.actions[i]
        val forged = original.copy(payload = JsonObject(original.payload + ("to" to JsonPrimitive("h8"))))
        val tampered = record.copy(actions = record.actions.toMutableList().also { it[i] = forged })
        assertIs<Verdict.Rejected>(verifier.verify(tampered))

        // Dropping a move breaks the digest.
        val dropped = record.copy(actions = record.actions.dropLast(1))
        assertIs<Verdict.Rejected>(verifier.verify(dropped))

        // Claiming another result is caught too.
        assertIs<Verdict.Rejected>(verifier.verify(record.copy(digest = "0".repeat(64))))
    }

    @Test
    fun `bot never consumes randomness`() {
        val cfg = ChessTestSupport.config(5L, options(botLevel = 2))
        val e = GameEngine.start(ChessModule, cfg)
        val before = cfg.rng.state
        e.playOutWithBots(maxActions = 20)
        assertEquals(before, cfg.rng.state)
        assertTrue((e.session as ChessSession).moves.size == 20)
    }
}
