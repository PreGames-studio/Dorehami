package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class HokmRoomCodeTest {

    @Test
    fun `generate length 6`() {
        val code = HokmRoomCode.generate(42)
        assertEquals(HokmRoomCode.LENGTH, code.length)
    }

    @Test
    fun `generate valid`() {
        val code = HokmRoomCode.generate(42)
        assertTrue(HokmRoomCode.isValid(code))
    }

    @Test
    fun `generate deterministic`() {
        assertEquals(HokmRoomCode.generate(42), HokmRoomCode.generate(42))
    }

    @Test
    fun `isValid rejects short`() {
        assertFalse(HokmRoomCode.isValid("ABC"))
    }

    @Test
    fun `isValid rejects look-alike`() {
        assertFalse(HokmRoomCode.isValid("ABC0IO"))
    }

    @Test
    fun `normalize filters invalid`() {
        val n = HokmRoomCode.normalize("abc-123")
        assertTrue(n.length <= 6)
    }

    @Test
    fun `share text contains code`() {
        val t = HokmRoomCode.shareText("ABCDEF")
        assertTrue(t.contains("ABCDEF"))
    }
}
