package ir.dorehami.games.hokm.ui.room

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HK-007 regression: leaving, failing or cancelling a room must release the connection, its
 * coroutines and its HTTP client exactly once. [RoomFlow] calls [RoomResources.releaseAll] from an
 * independent teardown Job, so the UI cancelling its scope cannot abort the cleanup (R-05).
 */
class RoomResourcesTest {

    @Test
    fun `resources close in reverse creation order like a real room`() = runBlocking {
        val resources = RoomResources()
        val order = mutableListOf<String>()
        resources.track("http") { order += "http" }
        resources.track("ws") { order += "ws" }
        resources.track("client") { order += "client" }
        resources.track("watcher") { order += "watcher" }

        assertTrue(resources.releaseAll().isEmpty())
        assertEquals(listOf("watcher", "client", "ws", "http"), order)
    }

    @Test
    fun `a throwing close is recorded and never blocks the remaining resources`() = runBlocking {
        val resources = RoomResources()
        val closed = mutableListOf<String>()
        resources.track("http") { closed += "http" }
        resources.track("client") { throw IllegalStateException("close failed") }
        resources.track("watcher") { closed += "watcher" }

        val failed = resources.releaseAll()
        assertEquals(listOf("client"), failed)
        assertEquals(listOf("client"), resources.lastFailures)
        assertEquals(listOf("watcher", "http"), closed)
    }

    @Test
    fun `a hanging close is bounded by the timeout and the rest still close`() = runBlocking {
        val resources = RoomResources(releaseTimeoutMs = 30)
        val closed = mutableListOf<String>()
        resources.track("http") { closed += "http" }
        resources.track("hung") { delay(10_000) }

        assertEquals(listOf("hung"), resources.releaseAll())
        assertEquals(listOf("http"), closed)
    }

    @Test
    fun `leave is idempotent and a resource is closed only once`() = runBlocking {
        val resources = RoomResources()
        var closes = 0
        resources.track("ws") { closes++ }

        assertTrue(resources.releaseAll().isEmpty())
        assertTrue(resources.releaseAll().isEmpty())
        assertEquals(1, closes)
        assertEquals(1, resources.releaseCount)
        assertEquals(1, resources.generation)
    }

    @Test
    fun `repeated enter and leave cycles settle each generation exactly once`() = runBlocking {
        val resources = RoomResources()
        repeat(3) { cycle ->
            val closed = mutableListOf<String>()
            resources.track("http-$cycle") { closed += "http" }
            resources.track("ws-$cycle") { closed += "ws" }
            resources.track("watcher-$cycle") { closed += "watcher" }
            assertEquals(3, resources.pendingCount)

            assertTrue(resources.releaseAll().isEmpty())
            assertEquals(listOf("watcher", "ws", "http"), closed)
            assertEquals(0, resources.pendingCount)

            // A second leave (e.g. onDispose after a failed attempt) must not re-close anything.
            assertTrue(resources.releaseAll().isEmpty())
            assertEquals(listOf("watcher", "ws", "http"), closed)
        }
        assertEquals(3, resources.releaseCount)
        assertEquals(3, resources.generation)
    }

    @Test
    fun `tracked jobs are cancelled during teardown`() = runBlocking {
        val resources = RoomResources()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val job = scope.launch { delay(10_000) }
        resources.track("watcher") { job.cancel() }

        assertTrue(resources.releaseAll().isEmpty())
        assertTrue(job.isCancelled)
        scope.cancel()
    }

    @Test
    fun `cleanup survives cancellation of the owning scope`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val resources = RoomResources(releaseTimeoutMs = 1_000)
        val started = CompletableDeferred<Unit>()
        var closed = false
        resources.track("ws") {
            started.complete(Unit)
            delay(20)
            closed = true
        }

        val uiJob = scope.launch { delay(10_000) }
        // Exactly the pattern in RoomFlow.teardown(): an independent, self-completing Job.
        // (launch(NonCancellable) is explicitly discouraged by the upstream KDoc.)
        val job: CompletableJob = Job()
        val cleanup = CoroutineScope(job + Dispatchers.Default).launch {
            try {
                resources.releaseAll()
            } catch (e: Exception) {
                Unit
            } finally {
                job.complete()
            }
        }
        started.await()
        scope.cancel()
        cleanup.join()
        job.join()

        assertTrue(uiJob.isCancelled, "the UI scope really was cancelled")
        assertTrue(job.isCompleted, "the teardown Job settles itself")
        assertTrue(closed, "suspend close must finish even after the UI scope is cancelled")
        assertEquals(1, resources.releaseCount)
        assertEquals(0, resources.pendingCount)
    }

    @Test
    fun `an empty ledger is a no op`() = runBlocking {
        val resources = RoomResources()
        assertTrue(resources.releaseAll().isEmpty())
        assertEquals(0, resources.releaseCount)
        assertEquals(0, resources.generation)
        assertEquals(0, resources.pendingCount)
    }
}
