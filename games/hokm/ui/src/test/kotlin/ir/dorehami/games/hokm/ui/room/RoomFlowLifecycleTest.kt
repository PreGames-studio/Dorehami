package ir.dorehami.games.hokm.ui.room

import android.content.ContextWrapper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import kotlin.test.Test

class RoomFlowLifecycleTest {
    @Test
    fun `repeated online room entry and exit closes the active HTTP resource`() = runBlocking {
        repeat(12) {
            val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
            val requestStarted = CompletableDeferred<Unit>()
            val clientDisconnected = CompletableDeferred<Unit>()
            val serverJob = launch(Dispatchers.IO) {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5_000
                        val input = socket.getInputStream()
                        readRequestHeaders(input)
                        requestStarted.complete(Unit)
                        while (input.read() != -1) {
                            // Hold the HTTP request open until RoomFlow disposes its client.
                        }
                        clientDisconnected.complete(Unit)
                    }
                } catch (e: SocketException) {
                    if (requestStarted.isCompleted) {
                        clientDisconnected.complete(Unit)
                    } else if (!server.isClosed) {
                        throw e
                    }
                }
            }
            val roomScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val roomFlow = RoomFlow(
                context = ContextWrapper(null),
                scope = roomScope,
                mode = RoomMode.ONLINE,
                playerName = "Lifecycle test",
                playerCount = 2,
                targetPoints = 7,
                serverUrl = "http://127.0.0.1:${server.localPort}",
            )

            try {
                val connectAttempt = roomFlow.createOnline()
                withTimeout(5_000) { requestStarted.await() }

                withTimeout(5_000) { roomFlow.leave().join() }
                withTimeout(5_000) { clientDisconnected.await() }
                withTimeout(5_000) { connectAttempt.join() }
            } finally {
                roomScope.cancel()
                server.close()
                serverJob.cancel()
                serverJob.join()
            }
        }
    }

    private fun readRequestHeaders(input: InputStream) {
        val headerTerminator = intArrayOf('\r'.code, '\n'.code, '\r'.code, '\n'.code)
        var matched = 0
        while (matched < headerTerminator.size) {
            val byte = input.read()
            check(byte >= 0) { "HTTP client disconnected before sending its request headers" }
            matched = when {
                byte == headerTerminator[matched] -> matched + 1
                byte == headerTerminator[0] -> 1
                else -> 0
            }
        }
    }
}
