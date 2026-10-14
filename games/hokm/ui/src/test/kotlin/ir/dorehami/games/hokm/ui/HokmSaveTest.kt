package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.MatchRecordCodec
import ir.dorehami.gamesdk.RecordDecode
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.gamesdk.view
import ir.dorehami.games.hokm.HandWinKind
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * JVM tests for HK-011 — the local match's save/restore lifecycle. A live Hokm session plays
 * real actions; the save is taken, "the process dies", and [HokmSave.load] must put the player
 * back at the exact same table — or refuse safely. No Compose, no Android.
 */
class HokmSaveTest {

    private val players = listOf(PlayerId("p0"), PlayerId("p1"), PlayerId("p2"), PlayerId("p3"))

    private fun engine(seed: Long = 77L, count: Int = 4): GameEngine = GameEngine.start(
        HokmModule,
        GameConfig(
            seed = seed,
            players = players.take(count),
            options = HokmModule.defaultOptions(),
            clock = ManualClock(),
            rng = SplitMixRng(seed),
        ),
    )

    private fun seatWithMove(e: GameEngine): Int =
        (0 until 4).first { e.session.legalActions(players[it]).isNotEmpty() }

    /** Plays one legal action for [seat] and asserts it was accepted. */
    private fun playOne(e: GameEngine, seat: Int) {
        val p = players[seat]
        val legal = e.session.legalActions(p)
        assertTrue(legal.isNotEmpty(), "seat $seat should have a legal action")
        val action = when {
            "choose_hokm" in legal -> GameAction("choose_hokm", view { put("suit", "spades") })
            "play_card" in legal -> {
                val cards = e.session.privateView(p)["legalCards"] as JsonArray
                GameAction("play_card", view { put("card", (cards.first() as JsonPrimitive).content) })
            }
            else -> GameAction(legal.first())
        }
        assertIs<ApplyResult.Ok>(e.submit(p, action), "seat $seat must be accepted")
    }

    // ------------------------------------------------------------------ continue healthily

    @Test
    fun `a saved match restores to the exact same table and continues`() {
        val e = engine()
        playOne(e, seatWithMove(e))
        playOne(e, seatWithMove(e))

        val loaded = HokmSave.load(HokmSave.save(e))
        assertIs<HokmSave.Load.Restored>(loaded, "a valid save must restore")

        // Same engine state — the player sees the same hand, turn and phase.
        assertEquals(e.record().digest, loaded.record.digest)
        assertEquals(e.digest(), loaded.engine.digest())
        assertEquals(
            e.session.privateView(players[0]).toString(),
            loaded.engine.session.privateView(players[0]).toString(),
            "the private view is identical after the restore",
        )

        // …and the game goes on: the next legal action applies cleanly on the restored engine.
        val restored = loaded.engine
        playOne(restored, seatWithMove(restored))
        assertTrue(restored.actions.size == e.actions.size + 1, "the restored match continues with new moves")
    }

    @Test
    fun `a save taken before any move restores the same deal`() {
        val e = engine(seed = 1234L)
        val loaded = HokmSave.load(HokmSave.save(e))
        assertIs<HokmSave.Load.Restored>(loaded)
        assertEquals(1234L, loaded.record.seed)
        assertEquals(
            e.session.privateView(players[0]).toString(),
            loaded.engine.session.privateView(players[0]).toString(),
            "the deal is identical after restore",
        )
    }

    @Test
    fun `a finished match restores finished and keeps the result`() {
        val e = engine()
        var guard = 0
        while (e.session.result() == null && guard++ < 2000) playOne(e, seatWithMove(e))
        assertTrue(e.session.result() != null, "the scripted game must finish")

        val loaded = HokmSave.load(HokmSave.save(e))
        assertIs<HokmSave.Load.Restored>(loaded)
        assertEquals(e.session.result(), loaded.engine.session.result(), "the result survives the save")
    }

    // ------------------------------------------------------------------ safe error, never a crash

    @Test
    fun `no save means empty - not an error`() {
        assertIs<HokmSave.Load.Empty>(HokmSave.load(null))
        assertIs<HokmSave.Load.Empty>(HokmSave.load(""))
    }

    @Test
    fun `a save from a newer schema fails safely`() {
        val newer = HokmSave.save(engine()).replace("\"schema\":1", "\"schema\":9")
        val failed = assertIs<HokmSave.Load.Failed>(HokmSave.load(newer))
        assertTrue(failed.messageFa.isNotBlank(), "the player gets a Persian safe error")
    }

    @Test
    fun `a tampered action log fails safely instead of restoring a wrong table`() {
        val e = engine()
        playOne(e, seatWithMove(e))
        val tampered = HokmSave.save(e).replace("choose_hokm", "choose_hokmX")
        assertIs<HokmSave.Load.Failed>(HokmSave.load(tampered), "a logged action that no longer applies must not restore")
        assertIs<HokmSave.Load.Failed>(HokmSave.load("{broken"))
    }

    @Test
    fun `a save whose digest no longer matches is refused`() {
        val e = engine()
        playOne(e, seatWithMove(e))
        val decoded = assertIs<RecordDecode.Decoded>(MatchRecordCodec.decode(HokmSave.save(e)))
        val rewritten = decoded.record.copy(digest = "0000")
        val failed = assertIs<HokmSave.Load.Failed>(HokmSave.load(MatchRecordCodec.encode(rewritten).toString()))
        assertTrue(failed.messageFa.isNotBlank())
    }

    // ------------------------------------------------------------------ once-only stats identity

    @Test
    fun `the same finished match has the same stats id`() {
        assertEquals(matchStatsId(finishedState()), matchStatsId(finishedState()), "restoring the same match must not change its identity")
    }

    @Test
    fun `different matches get different stats ids`() {
        assertNotEquals(matchStatsId(finishedState()), matchStatsId(finishedState(handsWon = 1)), "different outcomes are different matches")
    }

    @Test
    fun `stats are recorded once per match id`() {
        assertTrue(shouldRecordStatsFor("m1", null), "first finish records")
        assertTrue(!shouldRecordStatsFor("m1", "m1"), "a restore of the same finished match never records twice")
        assertTrue(shouldRecordStatsFor("m2", "m1"), "the next match records again")
    }

    private fun finishedState(handsWon: Int = 2) = HokmUiState(
        playerCount = 4,
        mySeat = 0,
        finished = true,
        winnerTeam = 0,
        scores = listOf(7, 3),
        handNumber = 3,
        handHistory = List(3) { i ->
            HandSummary(i + 1, winnerTeam = if (i < handsWon) 0 else 1, points = 1, kind = HandWinKind.NORMAL, hakem = 0, tricks = listOf(7, 0))
        },
    )
}
