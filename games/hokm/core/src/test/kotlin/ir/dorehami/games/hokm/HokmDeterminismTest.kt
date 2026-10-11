package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.GameRegistry
import ir.dorehami.gamesdk.MatchVerifier
import ir.dorehami.gamesdk.Verdict
import ir.dorehami.gamesdk.view
import kotlinx.serialization.json.put
import ir.dorehami.games.hokm.HokmTestSupport.engine
import ir.dorehami.games.hokm.HokmTestSupport.playWithBots
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Contract rule 1: `(seed + action log)` replays to the same digest and the same table. */
class HokmDeterminismTest {

    @Test
    fun `replaying the record reproduces digest, result and snapshot`() {
        val live = engine(31337L)
        playWithBots(live)
        val record = live.record()
        assertTrue(record.actions.isNotEmpty())

        val replayed = GameEngine.replay(HokmModule, record)
        assertEquals(record.digest, replayed.digest())
        assertEquals(live.session.result(), replayed.session.result())
        assertEquals(live.session.snapshot(), replayed.session.snapshot())
        assertEquals(live.session.version, replayed.session.version)
    }

    @Test
    fun `same seed twice gives the identical game, different seed does not`() {
        val a = engine(1L).also { playWithBots(it) }
        val b = engine(1L).also { playWithBots(it) }
        val c = engine(2L).also { playWithBots(it) }
        assertEquals(a.digest(), b.digest())
        assertEquals(a.actions, b.actions)
        assertNotEquals(a.digest(), c.digest())
    }

    @Test
    fun `verifier accepts a genuine record and rejects a tampered one`() {
        val verifier = MatchVerifier(GameRegistry(HokmModule))
        val e = engine(77L)
        playWithBots(e)
        val record = e.record()
        assertIs<Verdict.Verified>(verifier.verify(record))

        // Flip the digest: must be rejected.
        assertIs<Verdict.Rejected>(verifier.verify(record.copy(digest = "00")))

        // Drop the last action: result no longer matches.
        val truncated = record.copy(actions = record.actions.dropLast(1))
        assertIs<Verdict.Rejected>(verifier.verify(truncated))

        // Swap the first play_card to a card that player does not hold: replay rejects it.
        val idx = record.actions.indexOfFirst { it.type == HokmModule.ACTION_PLAY_CARD }
        val forged = record.actions.toMutableList()
        val original = forged[idx]
        val originalCard = Card.parse(original.payload["card"]!!.toString().trim('"'))!!
        val otherCard = Card.fullDeck().first { it != originalCard }
        forged[idx] = original.copy(
            payload = view { put("card", otherCard.code) },
        )
        val verdict = verifier.verify(record.copy(actions = forged))
        assertIs<Verdict.Rejected>(verdict)
    }

    @Test
    fun `bots never use randomness - a long batch of seeds is stable`() {
        // Cheap smoke test over many seeds: every game ends, and twice the same.
        for (seed in 100L until 130L) {
            val x = engine(seed).also { playWithBots(it) }
            val y = engine(seed).also { playWithBots(it) }
            assertEquals(x.digest(), y.digest(), "seed $seed diverged")
        }
    }
}
