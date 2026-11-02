package ir.dorehami.session

import ir.dorehami.games.hokm.HokmModule
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.protocol.PlayerId
import ir.dorehami.testkit.FakeHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Hokm lobby has no separate "I am ready" step: joining a room *is* the readiness signal, and the
 * host must be able to start the moment the first guest appears (empty seats become bots).
 *
 * This is the session-layer contract behind that UX decision — the old `lobby.ready` ping was both
 * meaningless (nothing gated on it) and buggy (a roster rebuild could clear the flag again).
 */
class LobbyReadinessTest {

    private fun host(scope: CoroutineScope, hub: FakeHub, party: Int): HostController {
        val transport = hub.connect("peer_host")
        return HostController(
            module = HokmModule,
            transport = transport,
            scope = scope,
            roomCode = "4QP6R",
            roomName = "اتاق تست",
            hostPlayer = PlayerInfo(PlayerId("p_host"), "میزبان"),
            seed = 11L,
            clock = ManualClock(0),
            rngFactory = { SplitMixRng(it) },
            partySize = party,
            botDelayMs = 0,
        ).also { it.open() }
    }

    private fun guest(scope: CoroutineScope, hub: FakeHub, id: String, peer: String = "peer_$id"): GuestController =
        GuestController(
            hub.connect(peer),
            scope,
            roomCode = "4QP6R",
            hostPeer = "peer_host",
            player = PlayerInfo(PlayerId(id), "مهمان $id"),
        )

    private suspend fun settle() = delay(50)

    private suspend fun awaitUntil(what: String, cond: () -> Boolean) {
        withTimeout(10_000) { while (!cond()) delay(10) }
        check(cond()) { what }
    }

    @Test
    fun `a joined guest is ready without pressing anything`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val hub = FakeHub()
            val host = host(scope, hub, party = 4)
            val guest = guest(scope, hub, "p_a")
            settle()
            guest.join()
            awaitUntil("the guest appears in the roster") {
                host.state.value.room?.members?.any { it.player.id.value == "p_a" } == true
            }

            val joined = host.state.value.room?.members?.first { it.player.id.value == "p_a" }
            assertNotNull(joined)
            assertFalse(joined.isBot)
            assertTrue(joined.connected)
            assertTrue(joined.ready, "joining the room is readiness — no second tap required")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the host can start as soon as one human has joined`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val hub = FakeHub()
            val host = host(scope, hub, party = 4)
            val guest = guest(scope, hub, "p_a")
            settle()
            guest.join()
            awaitUntil("the seat is taken") {
                host.state.value.room?.members?.any { it.player.id.value == "p_a" } == true
            }

            assertTrue(host.startMatch(), "nothing waits for a ready round-trip")
            awaitUntil("the guest sees the match start") { guest.state.value.room?.started == true }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a seat that comes back is ready again`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val hub = FakeHub()
            val host = host(scope, hub, party = 4)
            val guest = guest(scope, hub, "p_a")
            fun seat() = host.state.value.room?.members?.firstOrNull { it.player.id.value == "p_a" }

            settle()
            guest.join()
            awaitUntil("joined") { seat()?.connected == true }

            // Before a match starts the lobby simply releases a seat whose phone went away.
            hub.drop("peer_p_a")
            awaitUntil("the dropped seat is released") { seat() == null }

            // It comes back on a new link with the same player id: full seat, and ready again.
            val again = guest(scope, hub, "p_a", peer = "peer_p_a2")
            again.join()
            awaitUntil("the seat returns") { seat()?.connected == true }
            assertTrue(seat()?.ready == true, "a returning player never has to press ready")
        } finally {
            scope.cancel()
        }
    }
}
