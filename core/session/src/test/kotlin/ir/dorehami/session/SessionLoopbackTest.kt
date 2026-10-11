package ir.dorehami.session

import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.protocol.ErrCodes
import ir.dorehami.protocol.PlayerId
import ir.dorehami.testkit.FakeHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Host + guest over the in-memory hub play a whole Hokm match; the guest only ever sees what the
 * protocol gives it (public view + its own private view + legal actions). This is the same code path
 * Bluetooth and the online relay will drive — only the [ir.dorehami.transport.GameTransport] differs.
 */
class SessionLoopbackTest {

    private fun host(scope: CoroutineScope, hub: FakeHub, party: Int, seed: Long = 7L, turnMillis: Long = 0L): HostController {
        val t = hub.connect("peer_host")
        return HostController(
            module = HokmModule, transport = t, scope = scope, roomCode = "4QP6R", roomName = "اتاق تست",
            hostPlayer = PlayerInfo(PlayerId("p_host"), "میزبان"),
            seed = seed, clock = ManualClock(0), rngFactory = { SplitMixRng(it) },
            partySize = party, botDelayMs = 0, turnMillis = turnMillis,
        ).also { it.open() }
    }

    @Test
    fun `turn timer - idle humans are played by the bot so the room never stalls`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val hub = FakeHub()
            val h = host(scope, hub, party = 4, turnMillis = 60)
            val g = guest(scope, hub, "p_a")
            val feeds = ArrayList<SessionEvent.Feed>()
            scope.launch { g.events.collect { if (it is SessionEvent.Feed) feeds += it } }
            settle()
            g.join(); settle()
            assertTrue(h.startMatch()); settle()
            assertTrue(h.state.value.deadlineMs > 0 || g.state.value.deadlineMs > 0, "a deadline is published while a human must move")
            // Nobody touches the phone: the whole match must still finish.
            awaitUntil("finished without any human move", timeoutMs = 120_000) { g.state.value.result != null }
            assertTrue(feeds.any { it.key == "turn_timeout" }, "timeouts are announced in the feed")
        } finally { scope.cancel() }
    }

    private fun guest(scope: CoroutineScope, hub: FakeHub, id: String, peer: String = "peer_$id"): GuestController =
        GuestController(hub.connect(peer), scope, roomCode = "4QP6R", hostPeer = "peer_host", player = PlayerInfo(PlayerId(id), "مهمان $id"))

    private suspend fun settle() { delay(40) }

    private suspend fun awaitUntil(what: String, timeoutMs: Long = 10_000, cond: () -> Boolean) {
        withTimeout(timeoutMs) { while (!cond()) delay(10) }
    }

    /** A guest's whole brain: pick from what the host told it is legal. */
    private suspend fun guestMove(g: GuestController): Boolean {
        val s = g.state.value
        val legal = s.legalActions
        if (legal.isEmpty()) return false
        val priv = s.privateView
        val hand = (priv["hand"] as? JsonArray)?.map { it.jsonPrimitive.contentOrNull!! } ?: emptyList()
        val legalCards = (priv["legalCards"] as? JsonArray)?.map { it.jsonPrimitive.contentOrNull!! } ?: emptyList()
        when {
            "choose_hokm" in legal -> g.send(GameAction("choose_hokm", buildJsonObject { put("suit", hand.first().last().toString()) }))
            "discard" in legal -> {
                val n = priv["discardCount"]!!.jsonPrimitive.contentOrNull!!.toInt()
                g.send(GameAction("discard", buildJsonObject { put("cards", JsonArray(hand.take(n).map { JsonPrimitive(it) })) }))
            }
            "keep_card" in legal -> g.send(GameAction("keep_card"))
            "play_card" in legal -> g.send(GameAction("play_card", buildJsonObject { put("card", legalCards.first()) }))
            else -> return false
        }
        return true
    }

    @Test
    fun `lobby - join, roster, reject when full or started`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val hub = FakeHub()
            val h = host(scope, hub, party = 2)
            val g1 = guest(scope, hub, "p_a")
            g1.join(); settle()
            assertEquals(1, g1.state.value.seat)
            assertEquals(2, h.state.value.room!!.members.size)

            val errs = ArrayList<SessionEvent>()
            val g2 = guest(scope, hub, "p_b")
            val collect = scope.launch { g2.events.collect { errs += it } }
            settle()
            g2.join(); settle()
            assertTrue(errs.any { it is SessionEvent.Error && it.code == ErrCodes.ROOM_FULL }, "third seat at a 2-player table must be refused")

            assertTrue(h.startMatch()); settle()
            assertTrue(g1.state.value.room!!.started)
            assertEquals("PLAYING", g1.state.value.phase)
            collect.cancel()
        } finally { scope.cancel() }
    }

    @Test
    fun `full 4-player match host + guest + 2 bots over the hub`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val hub = FakeHub()
            val h = host(scope, hub, party = 4)
            val g = guest(scope, hub, "p_a")
            val guestSounds = ArrayList<String>()
            val privLeak = ArrayList<String>()
            scope.launch { g.events.collect { if (it is SessionEvent.Sound) guestSounds += it.name } }
            settle()
            g.join(); settle()
            assertTrue(h.startMatch()); settle()

            withTimeout(60_000) {
                var guard = 0
                while (h.state.value.result == null && guard++ < 5000) {
                    // Guest must never see another seat's hand.
                    val pub = g.state.value.publicView.toString()
                    if (pub.contains("\"hands\"") || pub.contains("\"stock\"")) privLeak += pub
                    val hostMoved = hostMove(h)
                    val guestMoved = guestMove(g)
                    settle()
                    if (!hostMoved && !guestMoved) delay(5)
                }
            }
            assertNotNull(h.state.value.result, "host sees a result")
            awaitUntil("guest finished") { g.state.value.result != null }
            assertTrue(privLeak.isEmpty(), "public view leaked private data")
            assertTrue(guestSounds.isNotEmpty(), "sounds reach the guest as session.event")
            assertEquals(h.state.value.result!!["result"].toString(), g.state.value.result!!["result"].toString())
            assertTrue(h.state.value.result!!.toString().length <= MAX_ENVELOPE_BYTES, "finished payload must fit one envelope")
            assertNotNull(h.record(), "host keeps the full replayable record")
        } finally { scope.cancel() }
    }

    @Test
    fun `2-player heads-up match works over the hub`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val hub = FakeHub()
            val h = host(scope, hub, party = 2, seed = 11)
            val g = guest(scope, hub, "p_a")
            g.join(); settle()
            assertTrue(h.startMatch()); settle()
            withTimeout(60_000) {
                var guard = 0
                while (h.state.value.result == null && guard++ < 5000) {
                    val a = hostMove(h); val b = guestMove(g); settle()
                    if (!a && !b) delay(5)
                }
            }
            awaitUntil("guest finished") { g.state.value.result != null }
        } finally { scope.cancel() }
    }

    @Test
    fun `disconnect - bot plays the empty seat, reconnect resyncs`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val hub = FakeHub()
            val h = host(scope, hub, party = 4)
            val g = guest(scope, hub, "p_a")
            g.join(); settle()
            assertTrue(h.startMatch()); settle()
            // Play a bit, then the guest's phone dies.
            repeat(6) { hostMove(h); guestMove(g); settle() }
            val seqBefore = h.state.value.seq
            hub.drop("peer_p_a"); settle()
            assertFalse(h.state.value.room!!.members.first { it.player.id.value == "p_a" }.connected)
            // Host alone must still be able to make progress: the bot covers seat 1.
            repeat(8) { hostMove(h); settle() }
            assertTrue(h.state.value.seq > seqBefore, "game stalled after a disconnect")

            // Same player id comes back on a new link.
            val g2 = guest(scope, hub, "p_a", peer = "peer_p_a2")
            g2.join(); settle()
            assertEquals(1, g2.state.value.seat)
            assertEquals(h.state.value.seq, g2.state.value.seq, "resync must deliver the current seq")
            assertTrue(h.state.value.room!!.members.first { it.player.id.value == "p_a" }.connected)
        } finally { scope.cancel() }
    }

    @Test
    fun `illegal action from guest is rejected with a code, not applied`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val hub = FakeHub()
            val h = host(scope, hub, party = 4)
            val g = guest(scope, hub, "p_a")
            val errs = ArrayList<SessionEvent.Error>()
            scope.launch { g.events.collect { if (it is SessionEvent.Error) errs += it } }
            settle()
            g.join(); settle()
            assertTrue(h.startMatch()); settle()
            val seq = h.state.value.seq
            g.send(GameAction("play_card", buildJsonObject { put("card", "AS") })); settle()
            assertEquals(seq, h.state.value.seq)
            assertTrue(errs.isNotEmpty(), "guest must be told why")
            assertNull(g.state.value.result)
        } finally { scope.cancel() }
    }

    @Test
    fun `envelope round-trips and rejects wrong version`() {
        val e = Envelope(type = Msg.SESSION_ACTION, room = "ABCDE", from = "p_x", payload = buildJsonObject { put("type", "play_card") }, to = "peer_host", seq = 5)
        val back = Envelope.decode(e.encode())
        assertEquals(e.type, back!!.type); assertEquals(e.room, back.room); assertEquals(e.to, back.to); assertEquals(5L, back.seq)
        assertNull(Envelope.decode(e.encode().replace("\"v\":1", "\"v\":2")))
        assertNull(Envelope.decode("{not json"))
    }

    /** Host plays like the guest does: from its own private view. */
    private suspend fun hostMove(h: HostController): Boolean {
        val s = h.state.value
        val legal = s.legalActions
        if (legal.isEmpty()) return false
        val priv = s.privateView
        val hand = (priv["hand"] as? JsonArray)?.map { it.jsonPrimitive.contentOrNull!! } ?: emptyList()
        val legalCards = (priv["legalCards"] as? JsonArray)?.map { it.jsonPrimitive.contentOrNull!! } ?: emptyList()
        when {
            "choose_hokm" in legal -> h.send(GameAction("choose_hokm", buildJsonObject { put("suit", hand.first().last().toString()) }))
            "discard" in legal -> {
                val n = priv["discardCount"]!!.jsonPrimitive.contentOrNull!!.toInt()
                h.send(GameAction("discard", buildJsonObject { put("cards", JsonArray(hand.take(n).map { JsonPrimitive(it) })) }))
            }
            "pass_card" in legal -> h.send(GameAction("pass_card"))
            "play_card" in legal -> h.send(GameAction("play_card", buildJsonObject { put("card", legalCards.first()) }))
            else -> return false
        }
        return true
    }
}
