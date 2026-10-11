package ir.dorehami.transport

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FramesTest {
    @Test
    fun `round trip through small chunks, out of order, with duplicates and noise`() {
        val body = ByteArray(5000) { (it * 7).toByte() }
        val chunks = Frames.encode(42, body, chunkSize = 300)
        assertEquals(17, chunks.size)
        var t = 0L
        val r = Frames.Reassembler { t }
        var out: ByteArray? = null
        val order = chunks.indices.shuffled(kotlin.random.Random(3))
        for (i in order) {
            assertNull(out)
            r.feed(byteArrayOf(1, 2, 3)) // noise
            r.feed(chunks[i])?.let { out = it }
            r.feed(chunks[i]) // duplicate
        }
        assertContentEquals(body, out)
        assertEquals(0, r.incompleteCount)
    }

    @Test
    fun `single chunk messages and empty bodies`() {
        val r = Frames.Reassembler { 0L }
        assertContentEquals("سلام".toByteArray(), r.feed(Frames.encode(1, "سلام".toByteArray()).single()))
        assertContentEquals(ByteArray(0), r.feed(Frames.encode(2, ByteArray(0)).single()))
    }

    @Test
    fun `stale partial messages are swept after 15 seconds`() {
        var t = 0L
        val r = Frames.Reassembler { t }
        val chunks = Frames.encode(9, ByteArray(2000), chunkSize = 500)
        r.feed(chunks[0])
        assertEquals(1, r.incompleteCount)
        t = 14_000; r.sweep(); assertEquals(1, r.incompleteCount)
        t = 16_000; r.sweep(); assertEquals(0, r.incompleteCount)
    }

    @Test
    fun `id counter wraps to 1`() {
        val c = Frames.IdCounter()
        repeat(0xFFFE) { c.next() }
        assertEquals(0xFFFF, c.next())
        assertEquals(1, c.next())
    }
}
