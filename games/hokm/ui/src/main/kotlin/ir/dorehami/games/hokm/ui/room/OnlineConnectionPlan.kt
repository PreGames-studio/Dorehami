package ir.dorehami.games.hokm.ui.room

import ir.dorehami.transport.online.socketUrlFor

/**
 * The single, pure decision point for the endpoint of the **next** online connection.
 *
 * `RoomFlow` is Android-bound (it owns a `Context`, a Ktor client and coroutines), so the endpoint
 * choice lives here instead: it is a plain JVM object that can be regression-tested without a
 * device. `RoomFlow.online()` resolves it exactly once and feeds the same [Endpoint.httpBase] to
 * both `OnlineApi` and the WebSocket URL — HTTP and WS therefore cannot drift to different hosts.
 *
 * Validation itself stays in [OnlineServerAddress]; this class only adds the "one resolved
 * endpoint, two URLs" guarantee and the consistent error message.
 */
internal class OnlineConnectionPlan(initialUrl: String) {
    private val address = OnlineServerAddress(initialUrl)

    /** Called on every keystroke in the lobby's server field, before any connection is opened. */
    fun update(url: String) {
        address.update(url)
    }

    /** Returns the endpoint to connect to, or `null` when the user must see [ERROR_FA]. */
    fun resolve(): Endpoint? = address.validatedBaseUrl()?.let(::Endpoint)

    /** A resolved connection target. Both URLs are derived from the same normalized base. */
    data class Endpoint(val httpBase: String) {
        /** The socket for a room, using the shared protocol mapping (http→ws, https→wss). */
        fun socketUrl(code: String, token: String): String = socketUrlFor(httpBase, code, token)
    }

    companion object {
        /** Clear, actionable Persian message shown for an invalid address before connecting. */
        val ERROR_FA: String = OnlineServerAddress.INVALID_ADDRESS_MESSAGE_FA
    }
}
