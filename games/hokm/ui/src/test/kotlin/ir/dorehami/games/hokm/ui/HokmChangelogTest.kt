package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HokmChangelogTest {

    @Test
    fun `latest entry`() {
        val entries = listOf(
            HokmChangelog.Entry("1.0", "first", 100),
            HokmChangelog.Entry("1.1", "second", 200)
        )
        assertEquals("1.1", HokmChangelog.latest(entries)?.version)
    }

    @Test
    fun `latest empty null`() {
        assertNull(HokmChangelog.latest(emptyList()))
    }

    @Test
    fun `format non-empty`() {
        val e = HokmChangelog.Entry("1.0", "notes", 0)
        assertTrue(HokmChangelog.format(e).isNotEmpty())
    }

    @Test
    fun `count`() {
        val entries = listOf(HokmChangelog.Entry("1.0", "n", 0))
        assertEquals(1, HokmChangelog.count(entries))
    }
}
