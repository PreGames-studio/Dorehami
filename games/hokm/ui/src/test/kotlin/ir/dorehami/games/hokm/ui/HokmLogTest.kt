package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmLogTest {

    private fun entry(level: HokmLog.Level) = HokmLog.Entry(level, "tag", "msg", 0)

    @Test
    fun `append grows`() {
        val buf = HokmLog.append(emptyList(), entry(HokmLog.Level.INFO))
        assertEquals(1, buf.size)
    }

    @Test
    fun `append caps`() {
        var buf = emptyList<HokmLog.Entry>()
        repeat(HokmLog.MAX_ENTRIES + 5) { i ->
            buf = HokmLog.append(buf, HokmLog.Entry(HokmLog.Level.INFO, "t", "m$i", i.toLong()))
        }
        assertEquals(HokmLog.MAX_ENTRIES, buf.size)
    }

    @Test
    fun `filter by level`() {
        val buf = listOf(entry(HokmLog.Level.DEBUG), entry(HokmLog.Level.ERROR))
        val filtered = HokmLog.filterByLevel(buf, HokmLog.Level.WARN)
        assertEquals(1, filtered.size)
    }

    @Test
    fun `count by level`() {
        val buf = listOf(entry(HokmLog.Level.INFO), entry(HokmLog.Level.INFO), entry(HokmLog.Level.ERROR))
        assertEquals(2, HokmLog.count(buf, HokmLog.Level.INFO))
    }
}
