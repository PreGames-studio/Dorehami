package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmJsonTest {

    @Test
    fun `escape quotes`() {
        assertEquals("\\\"", HokmJson.escape("\""))
    }

    @Test
    fun `round trip escape unescape`() {
        val s = "hello \"world\"\nline"
        assertEquals(s, HokmJson.unescape(HokmJson.escape(s)))
    }

    @Test
    fun `quote wraps`() {
        val q = HokmJson.quote("hi")
        assertTrue(q.startsWith("\"") && q.endsWith("\""))
    }
}
