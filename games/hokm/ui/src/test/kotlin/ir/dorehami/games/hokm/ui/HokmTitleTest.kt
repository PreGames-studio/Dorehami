package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmTitleTest {

    @Test
    fun `title for high win rate`() {
        assertEquals("قهرمان", HokmTitle.forWinRate(0.8f))
    }

    @Test
    fun `title for low win rate`() {
        assertEquals("تازه‌کار", HokmTitle.forWinRate(0.3f))
    }

    @Test
    fun `title for level`() {
        assertTrue(HokmTitle.forLevel(1).isNotEmpty())
    }
}
