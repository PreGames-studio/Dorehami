package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class HokmBorderTest {

    @Test
    fun `selected border wider`() {
        assertTrue(HokmBorder.cardBorder(true).width > HokmBorder.cardBorder(false).width)
    }

    @Test
    fun `selected border gold`() {
        assertEquals("#FFD700", HokmBorder.cardBorder(true).color)
    }

    @Test
    fun `table border`() {
        assertTrue(HokmBorder.tableBorder().width > 0f)
    }
}
