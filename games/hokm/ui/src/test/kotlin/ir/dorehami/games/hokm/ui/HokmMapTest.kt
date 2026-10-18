package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HokmMapTest {
    @Test
    fun `put get remove`() {
        var m = HokmMap.put(emptyMap<String, Int>(), "a", 1)
        assertEquals(1, HokmMap.get(m, "a"))
        m = HokmMap.remove(m, "a")
        assertEquals(null, HokmMap.get(m, "a"))
    }
    @Test
    fun `default`() {
        assertEquals(5, HokmMap.get(emptyMap<String, Int>(), "x", 5))
    }
    @Test
    fun `contains and size`() {
        val m = HokmMap.put(emptyMap<String, Int>(), "a", 1)
        assertTrue(HokmMap.containsKey(m, "a"))
        assertFalse(HokmMap.containsKey(m, "b"))
        assertEquals(1, HokmMap.size(m))
    }
}
