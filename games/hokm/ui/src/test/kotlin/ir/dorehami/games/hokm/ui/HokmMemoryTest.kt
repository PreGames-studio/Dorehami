package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmMemoryTest {

    @Test
    fun `low memory detection`() {
        assertTrue(HokmMemory.isLowMemory(100))
        assertTrue(!HokmMemory.isLowMemory(500))
    }

    @Test
    fun `reduce cache on low`() {
        assertTrue(HokmMemory.shouldReduceCache(100))
        assertTrue(!HokmMemory.shouldReduceCache(500))
    }

    @Test
    fun `cache size small on low`() {
        assertEquals(16, HokmMemory.cacheSizeMb(100))
        assertEquals(64, HokmMemory.cacheSizeMb(500))
    }
}
