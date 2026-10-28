package ir.dorehami.games.hokm.ui.room

import kotlinx.coroutines.withTimeoutOrNull

/**
 * A pure, JVM-testable teardown ledger for the resources of one room connection.
 *
 * Resources are registered in creation order and released in reverse order (LIFO): the watcher job
 * and the session are closed before the transport, and the HTTP client last. Every action is
 * guarded — a throwing or hanging close must never prevent the remaining resources from being
 * released. [releaseAll] takes and clears the whole batch atomically, so calling it twice, or
 * calling it again after a failed attempt, cannot close a resource twice. Clearing also makes the
 * ledger reusable for the next enter/leave cycle.
 *
 * This class has no Android, Compose or network dependency. `RoomFlow` invokes [releaseAll] from
 * an independent teardown Job, because the UI cancels its own scope immediately after `leave()`
 * (risk R-05); see `RoomResourcesTest` for that regression.
 */
internal class RoomResources(private val releaseTimeoutMs: Long = 2_000L) {
    private data class Entry(val name: String, val release: suspend () -> Unit)

    private val lock = Any()
    private val entries = mutableListOf<Entry>()
    private var releases = 0
    private var failures: List<String> = emptyList()

    /** Number of teardown batches, i.e. how many enter/leave cycles have been settled. */
    @Volatile
    var generation: Int = 0
        private set

    /** Registers a named teardown action. Actions run in reverse registration order. */
    fun track(name: String, release: suspend () -> Unit) {
        synchronized(lock) { entries += Entry(name, release) }
    }

    /** Registers [resource] and returns it, so call sites stay a one-liner. */
    fun <T : Any> track(name: String, resource: T, release: suspend (T) -> Unit): T {
        track(name) { release(resource) }
        return resource
    }

    val pendingCount: Int get() = synchronized(lock) { entries.size }
    val releaseCount: Int get() = synchronized(lock) { releases }
    val lastFailures: List<String> get() = synchronized(lock) { failures }

    /**
     * Releases every registered resource exactly once and returns the names that failed (empty on
     * success). Safe to call repeatedly; an empty ledger is a no-op.
     */
    suspend fun releaseAll(): List<String> {
        val batch = synchronized(lock) {
            val copy = entries.toList()
            entries.clear()
            if (copy.isNotEmpty()) generation++
            copy
        }
        if (batch.isEmpty()) return emptyList()

        val failed = mutableListOf<String>()
        for (entry in batch.asReversed()) {
            val ok = try {
                withTimeoutOrNull(releaseTimeoutMs) {
                    entry.release()
                    true
                } ?: false // timed out
            } catch (e: Exception) {
                false // a failing close must not block the remaining resources
            }
            if (!ok) failed += entry.name
        }
        synchronized(lock) {
            releases++
            failures = failed
        }
        return failed
    }
}
