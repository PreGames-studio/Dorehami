package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HokmColorTest {

    @Test
    fun `toHex`() {
        assertEquals("#FF0000", HokmColor.toHex(HokmColor.RGB(255, 0, 0)))
    }

    @Test
    fun `fromHex valid`() {
        assertEquals(HokmColor.RGB(255, 0, 0), HokmColor.fromHex("#FF0000"))
    }

    @Test
    fun `fromHex invalid`() {
        assertNull(HokmColor.fromHex("zzz"))
    }

    @Test
    fun `round trip`() {
        val c = HokmColor.RGB(10, 20, 30)
        assertEquals(c, HokmColor.fromHex(HokmColor.toHex(c)))
    }

    @Test
    fun `is dark black`() {
        assertTrue(HokmColor.isDark(HokmColor.RGB(0, 0, 0)))
    }

    @Test
    fun `is dark white false`() {
        assertTrue(!HokmColor.isDark(HokmColor.RGB(255, 255, 255)))
    }
}
