package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class HokmConfigTest {

    @Test
    fun `get default`() {
        assertEquals("x", HokmConfig.get(HokmConfig.Config(), "k", "x"))
    }

    @Test
    fun `set get`() {
        val c = HokmConfig.set(HokmConfig.Config(), "k", "v")
        assertEquals("v", HokmConfig.get(c, "k"))
    }

    @Test
    fun `merge`() {
        val a = HokmConfig.set(HokmConfig.Config(), "k", "a")
        val b = HokmConfig.set(HokmConfig.Config(), "k", "b")
        assertEquals("b", HokmConfig.get(HokmConfig.merge(a, b), "k"))
    }
}
