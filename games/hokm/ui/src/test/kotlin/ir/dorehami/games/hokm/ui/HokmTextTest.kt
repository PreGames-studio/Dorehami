package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmTextTest {

    @Test
    fun `truncate short unchanged`() {
        assertEquals("hi", HokmText.truncate("hi", 5))
    }

    @Test
    fun `truncate long`() {
        val t = HokmText.truncate("hello world", 6)
        assertTrue(t.length <= 6)
        assertTrue(t.endsWith("…"))
    }

    @Test
    fun `is empty`() {
        assertTrue(HokmText.isEmpty(null))
        assertTrue(HokmText.isEmpty("   "))
        assertTrue(!HokmText.isEmpty("hi"))
    }

    @Test
    fun `capitalize first`() {
        assertEquals("Hello", HokmText.capitalizeFirst("hello"))
    }

    @Test
    fun `word count`() {
        assertEquals(3, HokmText.wordCount("one two three"))
        assertEquals(0, HokmText.wordCount("   "))
    }
}
