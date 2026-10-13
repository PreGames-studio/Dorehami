package ir.dorehami.games.hokm.ui.room

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owns resources acquired by one room attempt and releases them once, in reverse acquisition order. */
internal class RoomResourceOwner(
    private val closeTimeoutMillis: Long = DEFAULT_CLOSE_TIMEOUT_MILLIS,
) {
    private val mutex = Mutex()
    private val closeComplete = CompletableDeferred<Unit>()
    private val cleanups = ArrayDeque<suspend () -> Unit>()
    private var closing = false

    init {
        require(closeTimeoutMillis > 0) { "closeTimeoutMillis must be positive" }
    }

    /**
     * Registers [cleanup] for the next close. If shutdown already began, waits for it and cleans
     * the late resource immediately instead of allowing it to leak.
     *
     * @return `true` when owned by this scope; `false` when it was already cleaned up.
     */
    suspend fun register(cleanup: suspend () -> Unit): Boolean = withContext(NonCancellable) {
        val accepted = mutex.withLock {
            if (closing) {
                false
            } else {
                cleanups.addFirst(cleanup)
                true
            }
        }
        if (!accepted) {
            closeComplete.await()
            runCleanup(cleanup)
        }
        accepted
    }

    /** Closes all registered resources once. One failing or slow close does not block the rest. */
    suspend fun close() = withContext(NonCancellable) {
        val pending = mutex.withLock {
            if (closing) {
                null
            } else {
                closing = true
                cleanups.toList().also { cleanups.clear() }
            }
        }
        if (pending == null) {
            closeComplete.await()
            return@withContext
        }

        try {
            pending.forEach { runCleanup(it) }
        } finally {
            closeComplete.complete(Unit)
        }
    }

    private suspend fun runCleanup(cleanup: suspend () -> Unit) {
        try {
            withTimeoutOrNull(closeTimeoutMillis) { cleanup() }
        } catch (_: Exception) {
            // Continue releasing the remaining resources even if this resource is already broken.
        }
    }

    private companion object {
        const val DEFAULT_CLOSE_TIMEOUT_MILLIS = 2_000L
    }
}
