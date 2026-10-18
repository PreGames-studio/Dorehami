package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmKeyValueTest {

    @Test
    fun `get default`() {
        assertEquals("d", HokmKeyValue.get(emptyMap(), "k", "d"))
    }

    @Test
    fun `set and get`() {
        val s = HokmKeyValue.set(emptyMap(), "k", "v")
        assertEquals("v", HokmKeyValue.get(s, "k"))
    }

    @Test
    fun `remove`() {
        val s = HokmKeyValue.set(emptyMap(), "k", "v")
        val s2 = HokmKeyValue.remove(s, "k")
        assertTrue(!HokmKeyValue.has(s2, "k"))
    }

    @Test
    fun `has check`() {
        val s = HokmKeyValue.set(emptyMap(), "k", "v")
        assertTrue(HokmKeyValue.has(s, "k"))
        assertFalse(HokmKeyValue.has(s, "x"))
    }
}
