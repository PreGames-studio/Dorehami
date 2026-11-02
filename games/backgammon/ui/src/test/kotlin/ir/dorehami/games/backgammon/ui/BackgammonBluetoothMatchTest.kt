package ir.dorehami.games.backgammon.ui

import ir.dorehami.games.backgammon.BackgammonModule
import ir.dorehami.games.backgammon.BackgammonRules
import ir.dorehami.gamesdk.ManualClock
import ir.dorehami.gamesdk.SplitMixRng
import ir.dorehami.protocol.PlayerId
import ir.dorehami.session.GuestController
import ir.dorehami.session.HostController
import ir.dorehami.session.PlayerInfo
import ir.dorehami.testkit.FakeHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Bluetooth table, end to end, over the in-memory hub — the same `HostController` /
 * `GuestController` pair the Nearby transport drives, so only the wire is faked.
 *
 * What this pins down is the part a UI review cannot see: the host's private view really is turned
 * into a board the player can tap, a full maximal turn survives the protocol, the turn passes to the
 * other phone, and the side that is not on move can neither roll nor move a checker.
 */
class BackgammonBluetoothMatchTest {

    private val roomCode = "4QP6R"

    @Test
    fun `both phones play a full turn and the turn passes over the room`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        try {
            val hub = FakeHub()
            val host = HostController(
                module = BackgammonModule,
                transport = hub.connect("peer_host"),
                scope = scope,
                roomCode = roomCode,
                roomName = "میز تست",
                hostPlayer = PlayerInfo(PlayerId("p_host"), "میزبان"),
                seed = 11L,
                clock = ManualClock(0),
                rngFactory = { SplitMixRng(it) },
                partySize = 2,
                botDelayMs = 0,
                botTakeoverOnDisconnect = false,
            ).also { it.open() }
            val guest = GuestController(
                hub.connect("peer_guest"),
                scope,
                roomCode = roomCode,
                hostPeer = "peer_host",
                player = PlayerInfo(PlayerId("p_guest"), "مهمان"),
            )
            guest.join()
            awaitUntil("the guest is in the room") { guest.state.value.room != null }
            assertTrue(host.startMatch(), "the host opens the table")
            awaitUntil("the room reports the match is on") { guest.state.value.phase != "SETUP" }

            val hostTable = BackgammonSessionMatch(scope, host, "تست").also { it.begin() }
            val guestTable = BackgammonSessionMatch(scope, guest, "تست").also { it.begin() }

            // Host is seat 0, so the host is white and rolls first.
            awaitUntil("the host may roll") { hostTable.state.value.canRoll }
            assertFalse(guestTable.state.value.canRoll, "the guest cannot roll out of turn")
            assertTrue(guestTable.state.value.hints.isEmpty(), "the guest is shown no legal source out of turn")

            hostTable.roll()
            awaitUntil("the host sees dice") { hostTable.state.value.dice.isNotEmpty() }

            val firstTurn = hostTable.state.value
            playMaximalTurn(hostTable, white = true)

            awaitUntil("the turn passes to the guest") { !hostTable.state.value.whiteToMove }
            awaitUntil("the guest may roll now") { guestTable.state.value.canRoll }
            assertFalse(
                hostTable.state.value.canRoll,
                "the host cannot roll while it is the guest's turn",
            )

            guestTable.roll()
            awaitUntil("the guest sees dice") { guestTable.state.value.dice.isNotEmpty() }
            playMaximalTurn(guestTable, white = false)
            awaitUntil("the turn comes back to the host") { hostTable.state.value.whiteToMove }

            // Both phones render the same board: the host is the only authority.
            awaitUntil("both boards agree") { hostTable.state.value.board == guestTable.state.value.board }
            assertEquals(firstTurn.target, guestTable.state.value.target, "the match length travels with the room")
        } finally {
            scope.cancel()
        }
    }

    /**
     * Taps the first maximal sequence the rules offer, move by move, exactly like a finger would:
     * pick the die, then pick the checker. The last tap submits the whole sequence.
     */
    private suspend fun playMaximalTurn(table: BackgammonSessionMatch, white: Boolean) {
        val before = table.state.value
        val sequence = BackgammonRules.sequences(before.board, before.dice, white).first()
        assertTrue(sequence.isNotEmpty(), "a roll always has a legal sequence")
        for (move in sequence) {
            table.tapDie(move.die)
            table.tapPoint(move.from)
        }
    }

    private suspend fun awaitUntil(what: String, timeoutMs: Long = 20_000, cond: () -> Boolean) {
        try {
            withTimeout(timeoutMs) { while (!cond()) delay(10) }
        } catch (e: Exception) {
            throw AssertionError("timed out waiting until $what", e)
        }
    }
}
