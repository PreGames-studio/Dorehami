package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.gamesdk.view
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.games.hokm.Play
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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
 * JVM tests for the typed view→state boundary (HK-009). Fixtures are **real** `privateView()`
 * payloads from a live [HokmSession], then deliberately damaged — so the tests pin the actual
 * wire format, not an imagined one. No Compose, no Android.
 */
class HokmUiMapperTest {

    private val players = listOf(PlayerId("p0"), PlayerId("p1"), PlayerId("p2"), PlayerId("p3"))

    private fun engine(count: Int = 4, seed: Long = 77L): GameEngine =
        GameEngine.start(
            HokmModule,
            GameConfig(
                seed = seed,
                players = players.take(count),
                options = HokmModule.defaultOptions(),
                clock = ManualClock(),
                rng = SplitMixRng(seed),
            ),
        )

    private fun viewOf(s: GameEngine, seat: Int): JsonObject =
        s.session.privateView(players[seat])

    private fun ok(mapped: MappedView): TableSnapshot {
        assertIs<MappedView.Valid>(mapped, "expected a valid mapping")
        return mapped.snapshot
    }

    private fun bad(mapped: MappedView): MappedView.Rejected {
        assertIs<MappedView.Rejected>(mapped, "expected the snapshot to be refused")
        return mapped
    }

    /** Removes a key from a real view. */
    private fun JsonObject.without(key: String) = JsonObject(this.filterKeys { it != key })

    /** Replaces one field of a real view. */
    private fun JsonObject.with(key: String, value: Number) =
        JsonObject(this + (key to kotlinx.serialization.json.JsonPrimitive(value)))

    private fun JsonObject.with(key: String, value: String) =
        JsonObject(this + (key to kotlinx.serialization.json.JsonPrimitive(value)))

    private fun JsonObject.with(key: String, value: JsonElement) =
        JsonObject(this + (key to value))

    // ------------------------------------------------------------------ mapping

    @Test
    fun `a complete real view maps every game field and my seat`() {
        val s = engine()
        val snap = ok(HokmUiMapper.map(viewOf(s, 2)))
        assertEquals(2, snap.mySeat, "mySeat comes from the view's seat key")
        assertEquals(4, snap.playerCount)
        assertEquals(HandPhase.CHOOSING_HOKM, snap.handPhase)
        assertEquals(
            (s.session.publicView()["hakem"] as kotlinx.serialization.json.JsonPrimitive).content.toInt(),
            snap.hakem,
        )
        assertEquals(1, snap.handNumber)
        val expectedHand = if (snap.mySeat == snap.hakem) 5 else 0
        assertEquals(expectedHand, snap.myHand.size, "while naming hokm only the hakem holds cards")
        assertEquals(emptyList(), snap.currentTrick)
        assertEquals(0L, snap.version)
        assertEquals(listOf(0, 0), snap.scores)
    }

    @Test
    fun `my seat follows the view, not a hard-coded zero`() {
        val s = engine()
        for (seat in 0 until 4) {
            assertEquals(seat, ok(HokmUiMapper.map(viewOf(s, seat))).mySeat)
        }
    }

    @Test
    fun `a mid-trick view rebuilds the current trick cards`() {
        val s = engine()
        val hakem = viewOf(s, 0)["hakem"]!!.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toInt() }
        assertIs<ApplyResult.Ok>(s.submit(players[hakem], GameAction(HokmModule.ACTION_CHOOSE_HOKM, view { put("suit", "spades") })))
        // Two seats play into the open trick.
        val t1 = ok(HokmUiMapper.map(viewOf(s, hakem))).turn
        assertIs<ApplyResult.Ok>(s.submit(players[t1], GameAction(HokmModule.ACTION_PLAY_CARD, view { put("card", cardOf(s, t1)) })))
        val t2 = ok(HokmUiMapper.map(viewOf(s, hakem))).turn
        assertIs<ApplyResult.Ok>(s.submit(players[t2], GameAction(HokmModule.ACTION_PLAY_CARD, view { put("card", cardOf(s, t2)) })))

        val snap = ok(HokmUiMapper.map(viewOf(s, 1)))
        assertEquals(2, snap.currentTrick.size, "the snapshot carries both played cards")
        assertEquals(snap.leadSuit, snap.currentTrick.first().card.suit)
        assertEquals(listOf(t1, t2), snap.currentTrick.map { it.seat })
    }

    @Test
    fun `the table rebuilds mid-trick cards from the snapshot alone`() {
        val s = engine()
        val hakem = viewOf(s, 0)["hakem"]!!.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toInt() }
        assertIs<ApplyResult.Ok>(s.submit(players[hakem], GameAction(HokmModule.ACTION_CHOOSE_HOKM, view { put("suit", "hearts") })))
        val t1 = ok(HokmUiMapper.map(viewOf(s, hakem))).turn
        assertIs<ApplyResult.Ok>(s.submit(players[t1], GameAction(HokmModule.ACTION_PLAY_CARD, view { put("card", cardOf(s, t1)) })))

        // A fresh table (reconnect / resync) that saw no events still shows the open trick.
        val fresh = HokmUiState(seats = seats4, playerCount = 4, mySeat = 1)
        val merged = HokmUiMapper.merge(fresh, ok(HokmUiMapper.map(viewOf(s, 1))))
        assertEquals(1, merged.tableCards.size, "refresh() rebuilds the trick from the snapshot")
        assertEquals(t1, merged.tableCards.first().seat)
    }

    // ------------------------------------------------------------------ incomplete / invalid

    @Test
    fun `missing keys are refused instead of crashing`() {
        val v = viewOf(engine(), 0)
        for (key in listOf("seat", "hand", "legalCards", "version", "currentTrick", "handCounts", "phase")) {
            val r = bad(HokmUiMapper.map(v.without(key)))
            assertEquals("view.incomplete", r.code, "removing $key")
        }
        assertIs<MappedView.Rejected>(HokmUiMapper.map(null))
    }

    @Test
    fun `bad cards and impossible numbers are refused`() {
        val v = viewOf(engine(), 0)
        assertTrue(bad(HokmUiMapper.map(v.with("hand", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("ZZ")) }))).code.let { it == "view.invalid" || it == "view.inconsistent" })
        assertEquals("view.invalid", bad(HokmUiMapper.map(v.with("turn", 99))).code)
        assertEquals("view.invalid", bad(HokmUiMapper.map(v.with("playerCount", 5))).code)
        assertEquals("view.invalid", bad(HokmUiMapper.map(v.with("handNumber", 0))).code)
        assertEquals("view.invalid", bad(HokmUiMapper.map(v.with("phase", "SETUP"))).code)
        assertEquals("view.invalid", bad(HokmUiMapper.map(v.with("handPhase", "dancing"))).code)
    }

    @Test
    fun `internally inconsistent views are refused`() {
        val s = engine()
        val v = viewOf(s, 0)
        // handCounts[mySeat] no longer matches the hand I hold.
        assertEquals("view.inconsistent", bad(HokmUiMapper.map(v.with("handCounts", buildJsonArray { repeat(4) { add(kotlinx.serialization.json.JsonPrimitive(9)) } }))).code)
        // a "legal" card that is not in my hand.
        val alien = buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive("2C")) }
        val handCodes = (v["hand"] as kotlinx.serialization.json.JsonArray).map { (it as kotlinx.serialization.json.JsonPrimitive).content }
        if ("2C" !in handCodes) {
            assertEquals("view.inconsistent", bad(HokmUiMapper.map(v.with("legalCards", alien))).code)
        }
        // leadSuit contradicts the open trick.
        val hakem = viewOf(s, 0)["hakem"]!!.let { (it as kotlinx.serialization.json.JsonPrimitive).content.toInt() }
        assertIs<ApplyResult.Ok>(s.submit(players[hakem], GameAction(HokmModule.ACTION_CHOOSE_HOKM, view { put("suit", "spades") })))
        val t1 = ok(HokmUiMapper.map(viewOf(s, hakem))).turn
        assertIs<ApplyResult.Ok>(s.submit(players[t1], GameAction(HokmModule.ACTION_PLAY_CARD, view { put("card", cardOf(s, t1)) })))
        val mid = viewOf(s, 1)
        assertEquals("view.inconsistent", bad(HokmUiMapper.map(mid.with("leadSuit", "diamonds"))).code)
    }

    // ------------------------------------------------------------------ staleness & merge

    @Test
    fun `an older snapshot is stale and ignored`() {
        val s = engine()
        val v0 = viewOf(s, 0)
        val hakem = ok(HokmUiMapper.map(v0)).hakem
        assertIs<ApplyResult.Ok>(s.submit(players[hakem], GameAction(HokmModule.ACTION_CHOOSE_HOKM, view { put("suit", "clubs") })))
        val v1 = viewOf(s, 0)

        val shown = HokmUiMapper.merge(HokmUiState(seats = seats4, mySeat = 0), ok(HokmUiMapper.map(v1)))
        assertTrue(shown.viewVersion > 0)
        val afterStale = HokmUiMapper.merge(shown, ok(HokmUiMapper.map(v0)))
        assertEquals(shown, afterStale, "the old snapshot must not roll the table back")
    }

    @Test
    fun `merge keeps table furniture and refreshes the game fields`() {
        val s = engine()
        val snap = ok(HokmUiMapper.map(viewOf(s, 1)))
        val busy = HokmUiState(
            seats = seats4,
            mySeat = 1,
            feed = listOf("خبر قبلی"),
            banner = Banner("بنر"),
            stickers = listOf(SeatSticker(0, Stickers.all.first(), 1L)),
            fx = listOf(TableFx(FxKind.CUT, 0, 2L)),
            handHistory = listOf(HandSummary(0, 0, 1, ir.dorehami.games.hokm.HandWinKind.NORMAL, 0, listOf(7, 1))),
            turnDeadlineMs = 1234L,
            turnSeconds = 30,
            modeLabel = "آنلاین",
        )
        val merged = HokmUiMapper.merge(busy, snap)
        assertEquals(listOf("خبر قبلی"), merged.feed)
        assertEquals(Banner("بنر"), merged.banner)
        assertEquals(1, merged.stickers.size)
        assertEquals(1, merged.fx.size)
        assertEquals(1, merged.handHistory.size)
        assertEquals(1234L, merged.turnDeadlineMs)
        assertEquals(30, merged.turnSeconds)
        assertEquals("آنلاین", merged.modeLabel)
        // game fields come from the snapshot
        assertEquals(snap.myHand, merged.myHand)
        assertEquals(snap.hakem, merged.hakem)
        assertEquals(snap.version, merged.viewVersion)
    }

    @Test
    fun `merge does not wipe cards an animation is showing`() {
        val s = engine()
        val snap = ok(HokmUiMapper.map(viewOf(s, 0)))
        val showing = Play(2, Card.parse("7H")!!)
        val collecting = HokmUiState(
            seats = seats4,
            mySeat = 0,
            tableCards = listOf(showing),
            collectingTo = 2,
        )
        val merged = HokmUiMapper.merge(collecting, snap)
        assertEquals(listOf(showing), merged.tableCards, "collect animation owns the cards")
    }

    @Test
    fun `a view for another seat or table size is refused by merge`() {
        val s = engine()
        val snapSeat2 = ok(HokmUiMapper.map(viewOf(s, 2)))
        val me0 = HokmUiState(seats = seats4, mySeat = 0)
        assertEquals(me0, HokmUiMapper.merge(me0, snapSeat2))

        val s2 = engine(count = 2, seed = 5L)
        val snap2p = ok(HokmUiMapper.map(viewOf(s2, 0)))
        val me4 = HokmUiState(seats = seats4, mySeat = 0)
        assertEquals(me4, HokmUiMapper.merge(me4, snap2p))
    }

    @Test
    fun `discard selection clears when the phase leaves discarding`() {
        val s = engine(count = 2, seed = 9L)
        val hakem = ok(HokmUiMapper.map(viewOf(s, 0))).hakem
        assertIs<ApplyResult.Ok>(s.submit(players[hakem], GameAction(HokmModule.ACTION_CHOOSE_HOKM, view { put("suit", "hearts") })))
        val v = viewOf(s, hakem)
        assertEquals(HandPhase.DISCARDING, ok(HokmUiMapper.map(v)).handPhase)
        val card = Card.parse(ok(HokmUiMapper.map(v)).myHand.first().code)!!
        val old = HokmUiState(seats = seats2, playerCount = 2, mySeat = hakem, selectedForDiscard = setOf(card))
        val merged = HokmUiMapper.merge(old, ok(HokmUiMapper.map(v)))
        assertEquals(setOf(card), merged.selectedForDiscard, "selection survives while still burning")

        // Play on: once the hand phase moves on, the burn selection is gone.
        val later = merged.copy(handPhase = HandPhase.PLAYING, selectedForDiscard = setOf(card))
        val playingSnap = ok(HokmUiMapper.map(v)).copy(handPhase = HandPhase.PLAYING)
        assertTrue(HokmUiMapper.merge(later, playingSnap).selectedForDiscard.isEmpty())
    }

    // ------------------------------------------------------------------ hand summaries

    @Test
    fun `hand result payloads map to typed summaries and bad ones return null`() {
        val s = engine()
        val summary = HokmUiMapper.handSummary(
            buildJsonObject {
                put("handNumber", 3)
                put("winnerTeam", null as Int?)
                put("points", 0)
                put("kind", "void")
                put("hakem", 2)
                put("tricks", buildJsonArray { listOf(6, 6, 5).forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
                put("hokm", null as String?)
            },
        )
        assertNotNull(summary)
        assertNull(summary.winnerTeam)
        assertEquals(listOf(6, 6, 5), summary.tricks)
        assertEquals(ir.dorehami.games.hokm.HandWinKind.VOID, summary.kind)

        assertNull(HokmUiMapper.handSummary(buildJsonObject { put("handNumber", 1) }), "incomplete payloads map to null")
        assertNull(
            HokmUiMapper.handSummary(buildJsonObject { put("kind", "nope") }),
            "unknown kinds map to null",
        )
        // and a real session payload maps cleanly
        assertTrue(HokmUiMapper.map(viewOf(s, 0)).let { it is MappedView.Valid })
    }

    // ------------------------------------------------------------------ fixtures

    private val seats4 = listOf(
        SeatInfo(0, "الف", false),
        SeatInfo(1, "ب", true),
        SeatInfo(2, "پ", true),
        SeatInfo(3, "ت", true),
    )

    private val seats2 = listOf(
        SeatInfo(0, "الف", false),
        SeatInfo(1, "ب", true),
    )

    /** Any one legal card of [seat] right now. */
    private fun cardOf(s: GameEngine, seat: Int): String {
        val v = ok(HokmUiMapper.map(viewOf(s, seat)))
        val legal = v.legalCards.ifEmpty { v.myHand.toSet() }
        return legal.first().code
    }
}
