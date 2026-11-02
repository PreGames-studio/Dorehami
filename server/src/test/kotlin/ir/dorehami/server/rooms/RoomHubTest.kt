package ir.dorehami.server.rooms

import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameRegistry
import ir.dorehami.protocol.PlayerId
import ir.dorehami.session.GuestController
import ir.dorehami.session.PlayerInfo
import ir.dorehami.session.SessionEvent
import ir.dorehami.transport.GameTransport
import ir.dorehami.transport.TransportEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The server path without Ktor: a "socket" is just a pair of functions. Two real guests + two bots
 * play a Hokm match arbitrated by the headless server authority; the record lands in the store.
 */
class RoomHubTest {

    /** Client side of a fake socket bound to one room/player on the hub. */
    private class SocketTransport(private val hub: RoomHub, private val code: String, private val player: PlayerInfo) : GameTransport {
        private val inbox = Channel<TransportEvent>(Channel.UNLIMITED)
        override val localPeer: String = player.id.value
        override val events: Flow<TransportEvent> = inbox.receiveAsFlow()
        private val sink: suspend (String) -> Unit = { inbox.send(TransportEvent.Message("authority", it)) }
        fun connect() { hub.attach(code, player, sink) }
        override suspend fun send(to: String?, text: String) = hub.inbound(code, player.id, text)
        override suspend fun close() { hub.detach(code, player.id, sink); inbox.trySend(TransportEvent.Closed) }
    }

    /**
     * Waits for a condition instead of sleeping a fixed number of milliseconds: the hub hops between
     * coroutines, so `delay(40)` was a coin toss on a loaded CI runner (the "record stored" test
     * failed intermittently for exactly that reason).
     */
    private suspend fun awaitUntil(label: String, timeoutMs: Long = 15_000, condition: suspend () -> Boolean) {
        try {
            withTimeout(timeoutMs) {
                while (!condition()) delay(5)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw AssertionError("timed out waiting for: $label", e)
        }
    }

    private suspend fun move(g: GuestController): Boolean {
        val s = g.state.value
        val legal = s.legalActions
        if (legal.isEmpty()) return false
        val hand = (s.privateView["hand"] as? JsonArray)?.map { it.jsonPrimitive.contentOrNull!! } ?: emptyList()
        val legalCards = (s.privateView["legalCards"] as? JsonArray)?.map { it.jsonPrimitive.contentOrNull!! } ?: emptyList()
        when {
            "choose_hokm" in legal -> g.send(GameAction("choose_hokm", buildJsonObject { put("suit", hand.first().last().toString()) }))
            "discard" in legal -> {
                val n = s.privateView["discardCount"]!!.jsonPrimitive.contentOrNull!!.toInt()
                g.send(GameAction("discard", buildJsonObject { put("cards", JsonArray(hand.take(n).map { JsonPrimitive(it) })) }))
            }
            "keep_card" in legal -> g.send(GameAction("keep_card"))
            "play_card" in legal -> g.send(GameAction("play_card", buildJsonObject { put("card", legalCards.first()) }))
            else -> return false
        }
        return true
    }

    @Test
    fun `server-hosted room - owner starts, two guests + two bots finish, record stored`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val store = InMemoryMatchStore()
            val hub = RoomHub(GameRegistry(listOf(HokmModule)), scope, store, nowMs = { 1000L }, seedSource = { 5L }, codeSource = { "ROOM1" }, botDelayMs = 0)
            val room = hub.create("hokm", 4, null, "اتاق سرور")
            assertEquals("ROOM1", room.code)

            val pa = PlayerInfo(PlayerId("p_a"), "الف"); val pb = PlayerInfo(PlayerId("p_b"), "ب")
            val ta = SocketTransport(hub, "ROOM1", pa).also { it.connect() }
            val tb = SocketTransport(hub, "ROOM1", pb).also { it.connect() }
            val ga = GuestController(ta, scope, "ROOM1", "authority", pa)
            val gb = GuestController(tb, scope, "ROOM1", "authority", pb)
            val errsB = ArrayList<SessionEvent.Error>()
            // `events` is a replay-0 SharedFlow, so the collector must be subscribed *before* the
            // guest sends anything; UNDISPATCHED subscribes inline instead of racing a delay().
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                gb.events.collect { if (it is SessionEvent.Error) errsB += it }
            }
            delay(20)
            ga.join(); gb.join()
            awaitUntil("both guests seated") { ga.state.value.seat == 0 && gb.state.value.seat == 1 }
            assertEquals(0, ga.state.value.seat); assertEquals(1, gb.state.value.seat)
            assertTrue(ga.state.value.isHost, "first human owns the room")

            gb.requestStart()
            awaitUntil("unauthorized start error") { errsB.any { it.code == "auth.unauthorized" } }
            assertTrue(errsB.any { it.code == "auth.unauthorized" }, "only the owner may start")
            ga.requestStart()
            awaitUntil("room started") { room.started }
            assertTrue(room.started)
            awaitUntil("four members") { ga.state.value.room?.members?.size == 4 }
            assertEquals(4, ga.state.value.room!!.members.size)

            withTimeout(60_000) {
                while (ga.state.value.result == null || gb.state.value.result == null) {
                    val a = move(ga); val b = move(gb); delay(if (a || b) 15 else 5)
                }
            }
            awaitUntil("match record persisted") { store.get("ROOM1") != null }
            assertNotNull(store.get("ROOM1"), "record persisted on finish")
            assertEquals(ga.state.value.result!!["result"].toString(), gb.state.value.result!!["result"].toString())
            // The host marks the room finished a moment after the broadcast, so keep reaping while
            // waiting instead of calling reap() once and hoping the flag was already set.
            awaitUntil("room reaped") {
                hub.reap()
                hub.get("ROOM1") == null
            }
            assertNull(hub.get("ROOM1"), "finished room reaped")
        } finally { scope.cancel() }
    }

    @Test
    fun `matchmaker groups players of the same game and size`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            var t = 0L
            val hub = RoomHub(GameRegistry(listOf(HokmModule)), scope, InMemoryMatchStore(), nowMs = { t }, botDelayMs = 0)
            val mm = Matchmaker(hub) { t }
            val t1 = mm.enqueue(PlayerInfo(PlayerId("a"), "a"), "hokm", 2)
            assertNull(t1.roomCode)
            assertEquals(t1.id, mm.enqueue(PlayerInfo(PlayerId("a"), "a"), "hokm", 2).id, "no duplicate tickets")
            val t2 = mm.enqueue(PlayerInfo(PlayerId("b"), "b"), "hokm", 2)
            assertNotNull(t1.roomCode); assertEquals(t1.roomCode, t2.roomCode)
            assertEquals(2, hub.get(t1.roomCode!!)!!.partySize)

            val lonely = mm.enqueue(PlayerInfo(PlayerId("c"), "c"), "hokm", 4)
            t = 20_000; mm.fillWithBotsOlderThan(15_000)
            assertNotNull(lonely.roomCode, "after waiting, a bot room")
        } finally { scope.cancel() }
    }

    @Test
    fun `room codes avoid look-alike letters`() {
        repeat(200) { val c = RoomHub.randomCode(); assertEquals(5, c.length); assertTrue(c.none { it in "0O1IL" }) }
    }
}
