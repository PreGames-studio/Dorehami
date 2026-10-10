package ir.dorehami.transport

/**
 * Chunking for transports that cap packet size (Bluetooth / Nearby `BYTES` payloads). Exactly the
 * 10-byte header of docs/PROTOCOL.md section 4:
 *
 * | 0 | 1 | 2–3 | 4–5 | 6–7 | 8–9 |
 * |magic 0x44 | version 1 | message id (u16) | chunk index | chunk count | chunk length |
 *
 * Pure functions + a small reassembler; no I/O, so it is unit-tested on the JVM.
 */
object Frames {
    const val MAGIC: Byte = 0x44
    const val VERSION: Byte = 1
    const val HEADER = 10

    /** Default chunk payload size; Nearby's BYTES payloads are safe well under 32KB, Bluetooth RFCOMM MTU ≈ 990. */
    const val DEFAULT_CHUNK = 900

    /** Unfinished messages older than this are dropped by [Reassembler.sweep]. */
    const val STALE_MS = 15_000L

    fun encode(messageId: Int, body: ByteArray, chunkSize: Int = DEFAULT_CHUNK): List<ByteArray> {
        require(chunkSize > 0)
        val count = maxOf(1, (body.size + chunkSize - 1) / chunkSize)
        require(count <= 0xFFFF) { "message too large to frame" }
        return (0 until count).map { i ->
            val from = i * chunkSize
            val len = minOf(chunkSize, body.size - from).coerceAtLeast(0)
            val out = ByteArray(HEADER + len)
            out[0] = MAGIC
            out[1] = VERSION
            putU16(out, 2, messageId and 0xFFFF)
            putU16(out, 4, i)
            putU16(out, 6, count)
            putU16(out, 8, len)
            if (len > 0) System.arraycopy(body, from, out, HEADER, len)
            out
        }
    }

    data class Chunk(val messageId: Int, val index: Int, val count: Int, val data: ByteArray)

    /** `null` when the packet is not one of ours (bad magic/version/length). */
    fun decodeChunk(packet: ByteArray): Chunk? {
        if (packet.size < HEADER || packet[0] != MAGIC || packet[1] != VERSION) return null
        val id = u16(packet, 2)
        val index = u16(packet, 4)
        val count = u16(packet, 6)
        val len = u16(packet, 8)
        if (count == 0 || index >= count || packet.size != HEADER + len) return null
        return Chunk(id, index, count, packet.copyOfRange(HEADER, HEADER + len))
    }

    /** Message id counter: 1..65535 then wraps to 1 (0 is never used). */
    class IdCounter {
        private var next = 1
        fun next(): Int { val v = next; next = if (next >= 0xFFFF) 1 else next + 1; return v }
    }

    /** Per-peer reassembly. Feed every packet from that peer; get whole messages back. */
    class Reassembler(private val now: () -> Long) {
        private class Pending(val count: Int, val startedAt: Long) {
            val parts = arrayOfNulls<ByteArray>(count)
            var have = 0
        }

        private val pending = HashMap<Int, Pending>()
        /** Recently completed ids, so a late duplicate chunk does not reopen a message. */
        private val done = ArrayDeque<Int>()

        /** Returns the complete message when this chunk finishes it, else `null`. Corrupt/duplicate chunks are ignored. */
        fun feed(packet: ByteArray): ByteArray? {
            val c = decodeChunk(packet) ?: return null
            if (c.count == 1) return c.data
            if (c.messageId in done) return null
            val p = pending.getOrPut(c.messageId) { Pending(c.count, now()) }
            if (p.count != c.count) { pending.remove(c.messageId); return null }
            if (p.parts[c.index] != null) return null // duplicate
            p.parts[c.index] = c.data
            p.have++
            if (p.have < p.count) return null
            pending.remove(c.messageId)
            done.addLast(c.messageId)
            while (done.size > 64) done.removeFirst()
            val total = p.parts.sumOf { it!!.size }
            val out = ByteArray(total)
            var pos = 0
            for (part in p.parts) { System.arraycopy(part!!, 0, out, pos, part.size); pos += part.size }
            return out
        }

        /** Drops messages that have been incomplete for longer than [STALE_MS]. */
        fun sweep() {
            val t = now()
            pending.entries.removeAll { t - it.value.startedAt > STALE_MS }
        }

        val incompleteCount: Int get() = pending.size
    }

    private fun putU16(b: ByteArray, at: Int, v: Int) {
        b[at] = (v ushr 8).toByte()
        b[at + 1] = v.toByte()
    }

    private fun u16(b: ByteArray, at: Int): Int = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
}
