package ir.dorehami.games.chess.ui

import ir.dorehami.games.chess.ChessModule
import ir.dorehami.games.chess.ui.room.ChessRoomMode
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.protocol.PlayerId
import ir.dorehami.session.GuestController
import ir.dorehami.session.HostController
import ir.dorehami.session.PlayerInfo
import ir.dorehami.testkit.FakeHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** A complete two-seat chess exchange through the same SessionClient contract used by Nearby. */
class ChessSessionMatchTest {
    @Test
    fun hostAndGuestSeeEachOthersMovesWithoutGuestWritingState() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val hub = FakeHub()
        val hostTransport = hub.connect("host")
        val guestTransport = hub.connect("guest-1")
        val hostPlayer = PlayerInfo(PlayerId("p_host"), "میزبان")
        val guestPlayer = PlayerInfo(PlayerId("p_guest"), "مهمان")
        val host = HostController(
            module = ChessModule,
            transport = hostTransport,
            scope = scope,
            roomCode = "CHESS",
            roomName = "تست شطرنج",
            hostPlayer = hostPlayer,
            seed = 7L,
            clock = { 0L },
            rngFactory = { SplitMixRng(it) },
            partySize = 2,
            botDelayMs = 0,
            botTakeoverOnDisconnect = false,
        )
        val guest = GuestController(
            transport = guestTransport,
            scope = scope,
            roomCode = "CHESS",
            hostPeer = "host",
            player = guestPlayer,
            nowMs = { 0L },
        )
        val hostMatch = ChessSessionMatch(scope, host, ChessRoomMode.BLUETOOTH) {}
        val guestMatch = ChessSessionMatch(scope, guest, ChessRoomMode.BLUETOOTH) {}
        try {
            host.open()
            guest.join()
            withTimeout(5_000) { guest.state.first { it.room != null } }
            host.startMatch()
            hostMatch.begin()
            guestMatch.begin()
            awaitTable(hostMatch)
            awaitTable(guestMatch)

            hostMatch.onSquareTap(FenBoard.square("e2"))
            hostMatch.onSquareTap(FenBoard.square("e4"))
            awaitPly(guestMatch, 1)
            assertEquals("e2e4", guestMatch.state.value.view?.uci?.lastOrNull())
            assertEquals(Side.BLACK, guestMatch.state.value.view?.you?.color)

            guestMatch.onSquareTap(FenBoard.square("e7"))
            guestMatch.onSquareTap(FenBoard.square("e5"))
            awaitPly(hostMatch, 2)
            assertEquals(listOf("e2e4", "e7e5"), hostMatch.state.value.view?.uci)
            assertNotNull(guestMatch.state.value.view?.you)
        } finally {
            hostMatch.stop()
            guestMatch.stop()
            runCatching { guest.leave() }
            runCatching { host.leave() }
            scope.cancel()
        }
    }

    private suspend fun awaitTable(match: ChessSessionMatch) {
        withTimeout(5_000) {
            while (match.state.value.view?.you == null) yield()
        }
    }

    private suspend fun awaitPly(match: ChessSessionMatch, ply: Int) {
        withTimeout(5_000) {
            while ((match.state.value.view?.ply ?: 0) < ply) yield()
        }
    }
}
