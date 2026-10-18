package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertFalse

class HokmChatTest {

    @Test
    fun `sanitize trims and truncates`() {
        val t = HokmChat.sanitize("  hello  ")
        assertEquals("hello", t)
    }

    @Test
    fun `sanitize truncates long`() {
        val t = HokmChat.sanitize("a".repeat(300))
        assertEquals(HokmChat.MAX_TEXT_LEN, t.length)
    }

    @Test
    fun `isValid empty false`() {
        assertFalse(HokmChat.isValid("   "))
    }

    @Test
    fun `isValid non-empty true`() {
        assertTrue(HokmChat.isValid("hi"))
    }

    @Test
    fun `create returns null for empty`() {
        assertNull(HokmChat.create("ali", "   ", 0, true))
    }

    @Test
    fun `create returns message`() {
        val m = HokmChat.create("ali", "salam", 100, true)
        assertNotNull(m)
        assertEquals("ali", m!!.sender)
    }

    @Test
    fun `append grows buffer`() {
        val m = HokmChat.create("ali", "hi", 0, true)!!
        val buf = HokmChat.append(emptyList(), m)
        assertEquals(1, buf.size)
    }

    @Test
    fun `append caps at max`() {
        var buf = emptyList<HokmChat.Message>()
        repeat(HokmChat.MAX_BUFFER + 5) { i ->
            val m = HokmChat.create("p", "m$i", i.toLong(), false)!!
            buf = HokmChat.append(buf, m)
        }
        assertEquals(HokmChat.MAX_BUFFER, buf.size)
    }

    @Test
    fun `format includes sender and text`() {
        val m = HokmChat.create("ali", "hi", 0, true)!!
        assertTrue(HokmChat.format(m).contains("ali"))
        assertTrue(HokmChat.format(m).contains("hi"))
    }
}
