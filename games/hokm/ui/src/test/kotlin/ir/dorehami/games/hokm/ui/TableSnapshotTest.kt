package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.HandWinKind
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.Play
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * HK-009 — the typed mapper and the snapshot/staleness rules, tested on the JVM.
 *
 * These tests never render Compose and never touch the device: they read a real `privateView()`
 * from the rules engine, break it on purpose, and check that the table refuses instead of crashing.
 */
class TableSnapshotTest {

    // ------------------------------------------------------------------ real views

    @Test
    fun `a fresh four player view maps to a complete snapshot`() {
        val engine = engine(4)
        val seats = players(4)
        val hakem = hakemOf(engine)
        val read = TableSnapshotMapper.read(engine.session.privateView(seats[hakem]), hakem, 4)

        val snapshot = assertIs<TableRead.Ready>(read).snapshot
        assertEquals(TABLE_VIEW_SCHEMA, snapshot.schemaVersion)
        assertEquals(4, snapshot.playerCount)
        assertEquals(hakem, snapshot.mySeat)
        assertEquals(HandPhase.CHOOSING_HOKM, snapshot.handPhase)
        assertEquals(5, snapshot.myHand.size, "the hakem starts with five cards")
        assertEquals(2, snapshot.tricksWon.size, "four players ⇒ two sides")
        assertEquals(2, snapshot.scores.size)
        assertEquals(4, snapshot.handCounts.size)
        assertNull(snapshot.hokm)
        assertNull(snapshot.lastHand)
        assertTrue(snapshot.revision >= 0)
    }

    @Test
    fun `the revision follows the session and never goes backwards`() {
        val engine = engine(4)
        val seats = players(4)
        val hakem = hakemOf(engine)
        val me = seats[hakem]
        val first = snapshot(engine, me)
        engine.submit(me, GameAction(HokmModule.ACTION_CHOOSE_HOKM, buildJsonObject { put("suit", "spades") }))
        val second = snapshot(engine, me)

        assertTrue(second.revision > first.revision, "a move must bump the revision")
        assertEquals(second.revision, snapshot(engine, me).revision, "re-reading the same view is stable")
        assertEquals(
            TableDecision.Stale,
            TableSnapshotMapper.decide(engine.session.privateView(me), second.revision + 1, hakem, 4),
        )
    }

    @Test
    fun `a stale view is refused and a current one is applied`() {
        val view = buildView()
        val revision = assertIs<TableRead.Ready>(TableSnapshotMapper.read(view)).snapshot.revision

        assertIs<TableDecision.Apply>(TableSnapshotMapper.decide(view, revision))
        assertIs<TableDecision.Apply>(TableSnapshotMapper.decide(view, revision - 1))
        assertEquals(TableDecision.Stale, TableSnapshotMapper.decide(view, revision + 1))
    }

    @Test
    fun `the current trick is part of the snapshot so a half played trick can be restored`() {
        val engine = engine(4)
        val seats = players(4)
        val hakem = hakemOf(engine)
        engine.submit(seats[hakem], GameAction(HokmModule.ACTION_CHOOSE_HOKM, buildJsonObject { put("suit", "hearts") }))
        engine.submitBot(seats[hakem])

        val snapshot = snapshot(engine, seats[(hakem + 1) % 4])
        assertEquals(1, snapshot.currentTrick.size, "the lead card is on the table")
        assertEquals(hakem, snapshot.currentTrick.first().seat)
        assertEquals(HandPhase.PLAYING, snapshot.handPhase)
    }

    // ------------------------------------------------------------------ refusals

    @Test
    fun `a missing key is named instead of throwing`() {
        val view = buildView()
        val withoutHand = JsonObject(view.filterKeys { it != "hand" })
        val read = TableSnapshotMapper.read(withoutHand)
        val incomplete = assertIs<TableRead.Incomplete>(read)
        assertTrue("hand" in incomplete.missing, "the missing key is reported: ${incomplete.missing}")
        assertIs<TableDecision.Rejected>(TableSnapshotMapper.decide(withoutHand, 0))
    }

    @Test
    fun `every required key is actually required`() {
        val view = buildView()
        for (key in TableSnapshotMapper.REQUIRED_KEYS) {
            val broken = JsonObject(view.filterKeys { it != key })
            val read = TableSnapshotMapper.read(broken)
            assertTrue(read !is TableRead.Ready, "removing $key must not produce a snapshot")
            if (key == "hakemDraw") {
                // Without a deal there is no table yet: the lobby state, not a broken table.
                assertEquals(TableRead.Absent, read, "removing hakemDraw means the session has not dealt")
                continue
            }
            assertIs<TableRead.Incomplete>(read, "removing $key ⇒ $read")
            assertTrue(key in read.missing, "removing $key should name it: ${read.missing}")
        }
    }

    @Test
    fun `an empty view is absent, not broken`() {
        val read = TableSnapshotMapper.read(JsonObject(emptyMap()))
        assertEquals(TableRead.Absent, read)
        assertEquals(TableDecision.Absent, TableSnapshotMapper.decide(JsonObject(emptyMap()), 0))
        assertEquals(TableRead.Absent, TableSnapshotMapper.read(null))
    }

    @Test
    fun `a newer schema is refused instead of half understood`() {
        val view = JsonObject(buildView() + ("schema" to JsonPrimitive(TABLE_VIEW_SCHEMA + 1)))
        val read = TableSnapshotMapper.read(view)
        assertEquals(TableRead.Unsupported(TABLE_VIEW_SCHEMA + 1), read)
        assertEquals(TableDecision.Unsupported(TABLE_VIEW_SCHEMA + 1), TableSnapshotMapper.decide(view, 0))
    }

    @Test
    fun `an unknown hand phase or card is refused`() {
        val badPhase = JsonObject(buildView() + ("handPhase" to JsonPrimitive("teleporting")))
        assertTrue("handPhase" in assertIs<TableRead.Incomplete>(TableSnapshotMapper.read(badPhase)).missing)

        val badCard = JsonObject(buildView() + ("hand" to buildJsonArray { add(JsonPrimitive("ZZ")) }))
        assertTrue("hand" in assertIs<TableRead.Incomplete>(TableSnapshotMapper.read(badCard)).missing)

        val badTable = JsonObject(buildView() + ("tricksWon" to buildJsonArray { add(JsonPrimitive(1)) }))
        assertTrue("tricksWon" in assertIs<TableRead.Incomplete>(TableSnapshotMapper.read(badTable)).missing)
    }

    @Test
    fun `a view for another seat or another table size is refused`() {
        val view = buildView()
        // the table draws seat 0; a view that says seat 2 would put my cards on another chair
        assertTrue(
            "seat" in assertIs<TableRead.Incomplete>(TableSnapshotMapper.read(view, expectedSeat = 0)).missing,
        )
        assertTrue(
            "playerCount" in assertIs<TableRead.Incomplete>(TableSnapshotMapper.read(view, expectedPlayers = 3)).missing,
        )
        assertIs<TableRead.Ready>(TableSnapshotMapper.read(view, expectedSeat = 2, expectedPlayers = 4))
    }

    // ------------------------------------------------------------------ table cards (R-06)

    @Test
    fun `a calm table restores the trick from the snapshot and an animating table keeps its own cards`() {
        val snapshot = assertIs<TableRead.Ready>(TableSnapshotMapper.read(buildView())).snapshot
        val fromSnapshot = listOf(Play(1, Card(Rank.ACE, Suit.SPADES)))
        val shown = listOf(Play(0, Card(Rank.SEVEN, Suit.SPADES)))
        val withTrick = snapshot.copy(currentTrick = fromSnapshot)

        assertEquals(fromSnapshot, TableSync.tableCards(withTrick, shown, calm = true), "snapshot wins when calm")
        assertEquals(shown, TableSync.tableCards(withTrick, shown, calm = false), "events win mid-animation")
        assertEquals(emptyList(), TableSync.tableCards(snapshot.copy(currentTrick = emptyList()), fromSnapshot, calm = true))
    }

    // ------------------------------------------------------------------ hands, including the HK-008 void

    @Test
    fun `a void three player hand keeps a null winner and zero points`() {
        val summary = assertNotNull(
            TableSnapshotMapper.parseHand(
                buildJsonObject {
                    put("handNumber", 4)
                    put("winnerTeam", "null")
                    put("points", 0)
                    put("kind", "void")
                    put("hakem", 1)
                    put("hokm", "diamonds")
                    put("tricks", buildJsonArray { add(JsonPrimitive(6)); add(JsonPrimitive(6)); add(JsonPrimitive(5)) })
                    put("scores", buildJsonArray { add(JsonPrimitive(2)); add(JsonPrimitive(1)); add(JsonPrimitive(0)) })
                },
            ),
        )
        assertNull(summary.winnerTeam)
        assertEquals(0, summary.points)
        assertEquals(HandWinKind.VOID, summary.kind)
        assertEquals(listOf(6, 6, 5), summary.tricks)
        assertEquals(Suit.DIAMONDS, summary.hokm)
    }

    @Test
    fun `a broken hand summary is ignored instead of guessed`() {
        assertNull(TableSnapshotMapper.parseHand(buildJsonObject { put("handNumber", 1) }))
        assertNull(
            TableSnapshotMapper.parseHand(
                buildJsonObject {
                    put("handNumber", 1)
                    put("points", 2)
                    put("hakem", 0)
                    put("kind", "not_a_kind")
                    put("tricks", buildJsonArray { add(JsonPrimitive(7)) })
                    put("winnerTeam", 0)
                },
            ),
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun players(count: Int) = List(count) { PlayerId("p$it") }

    private fun hakemOf(engine: GameEngine): Int =
        (engine.session.publicView()["hakem"] as JsonPrimitive).content.toInt()

    private fun engine(count: Int, seed: Long = 909L): GameEngine = GameEngine.start(
        HokmModule,
        GameConfig(
            seed = seed,
            players = players(count),
            options = HokmModule.defaultOptions(),
            clock = ManualClock(),
            rng = SplitMixRng(seed),
        ),
    )

    private fun snapshot(engine: GameEngine, seat: PlayerId) =
        assertIs<TableRead.Ready>(TableSnapshotMapper.read(engine.session.privateView(seat))).snapshot

    /** A minimal, fully valid view; individual tests break one field at a time. */
    private fun buildView(): JsonObject = buildJsonObject {
        put("version", 7)
        put("playerCount", 4)
        put("phase", "PLAYING")
        put("handPhase", HandPhase.PLAYING.id)
        put("handNumber", 2)
        put("hakem", 1)
        put("turn", 2)
        put("hokm", "hearts")
        put("trickNumber", 3)
        put("tricksWon", buildJsonArray { add(JsonPrimitive(2)); add(JsonPrimitive(1)) })
        put("scores", buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(0)) })
        put("targetPoints", 7)
        put("handCounts", buildJsonArray { repeat(4) { add(JsonPrimitive(8)) } })
        put("seat", 2)
        put("hand", buildJsonArray { add(JsonPrimitive("AS")); add(JsonPrimitive("7H")) })
        put("legalCards", buildJsonArray { add(JsonPrimitive("AS")) })
        put("stockCount", 0)
        put("hakemDraw", buildJsonArray {})
        put("currentTrick", buildJsonArray { add(buildJsonObject { put("seat", 1); put("card", "QH") }) })
    }
}
