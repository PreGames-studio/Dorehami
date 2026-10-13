package ir.dorehami.games.hokm.ui.room

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoomResourceOwnerTest {
    @Test
    fun `closes resources once in reverse order and continues after a close failure`() = runBlocking {
        val owner = RoomResourceOwner()
        val closed = mutableListOf<String>()

        owner.register { closed += "http" }
        owner.register {
            closed += "websocket"
            error("socket is already closed")
        }
        owner.register { closed += "session" }

        owner.close()
        owner.close()

        assertEquals(listOf("session", "websocket", "http"), closed)
    }

    @Test
    fun `cleanup completes even when the caller is cancelled`() = runBlocking {
        val owner = RoomResourceOwner()
        val started = CompletableDeferred<Unit>()
        val allowClose = CompletableDeferred<Unit>()
        var released = false

        owner.register {
            started.complete(Unit)
            allowClose.await()
            released = true
        }
        val closeJob = launch { owner.close() }

        withTimeout(1_000) { started.await() }
        closeJob.cancel()
        allowClose.complete(Unit)
        withTimeout(1_000) { closeJob.join() }

        assertTrue(released)
    }

    @Test
    fun `concurrent close callers wait for one cleanup`() = runBlocking {
        val owner = RoomResourceOwner()
        val started = CompletableDeferred<Unit>()
        val allowClose = CompletableDeferred<Unit>()
        var closeCount = 0

        owner.register {
            started.complete(Unit)
            allowClose.await()
            closeCount++
        }
        val firstClose = launch { owner.close() }
        withTimeout(1_000) { started.await() }
        val secondClose = launch { owner.close() }
        yield()

        assertFalse(secondClose.isCompleted)
        allowClose.complete(Unit)
        withTimeout(1_000) {
            firstClose.join()
            secondClose.join()
        }

        assertEquals(1, closeCount)
    }

    @Test
    fun `slow cleanup is bounded and later resources still close`() = runBlocking {
        val owner = RoomResourceOwner(closeTimeoutMillis = 20)
        var slowCleanupStarted = false
        var laterResourceReleased = false

        owner.register { laterResourceReleased = true }
        owner.register {
            slowCleanupStarted = true
            awaitCancellation()
        }

        owner.close()

        assertTrue(slowCleanupStarted)
        assertTrue(laterResourceReleased)
    }

    @Test
    fun `resources registered after close are immediately cleaned`() = runBlocking {
        val owner = RoomResourceOwner()
        owner.close()
        var released = false

        val registered = owner.register { released = true }

        assertFalse(registered)
        assertTrue(released)
    }

    @Test
    fun `repeated resource acquisition and cleanup cycles release each resource exactly once`() = runBlocking {
        val sessions = 12
        var openResources = 0
        var releasedResources = 0

        repeat(sessions) {
            val owner = RoomResourceOwner()
            repeat(4) {
                openResources++
                owner.register {
                    openResources--
                    releasedResources++
                }
            }
            owner.close()
            owner.close()
            assertEquals(0, openResources)
        }

        assertEquals(sessions * 4, releasedResources)
    }
}
