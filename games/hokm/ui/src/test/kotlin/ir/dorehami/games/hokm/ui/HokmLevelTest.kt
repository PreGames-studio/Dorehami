package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmLevelTest {

    @Test
    fun `level for xp`() {
        assertEquals(1, HokmLevel.levelForXp(0))
        assertEquals(2, HokmLevel.levelForXp(100))
        assertEquals(3, HokmLevel.levelForXp(250))
    }

    @Test
    fun `xp for next level`() {
        assertEquals(100, HokmLevel.xpForNextLevel(0))
        assertEquals(50, HokmLevel.xpForNextLevel(50))
    }

    @Test
    fun `title changes with level`() {
        assertEquals("تازه‌کار", HokmLevel.title(1))
        assertEquals("استاد", HokmLevel.title(25))
    }
}
