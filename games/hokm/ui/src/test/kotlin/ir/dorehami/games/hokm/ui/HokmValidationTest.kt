package ir.dorehami.games.hokm.ui

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class HokmValidationTest {

    @Test
    fun `valid room code`() {
        assertTrue(HokmValidation.isValidRoomCode("ABC123"))
        assertFalse(HokmValidation.isValidRoomCode("AB"))
    }

    @Test
    fun `valid player name`() {
        assertTrue(HokmValidation.isValidPlayerName("ali"))
        assertFalse(HokmValidation.isValidPlayerName("a"))
        assertFalse(HokmValidation.isValidPlayerName("a".repeat(21)))
    }

    @Test
    fun `valid sticker code`() {
        assertTrue(HokmValidation.isValidStickerCode("thumbs_up"))
        assertFalse(HokmValidation.isValidStickerCode("nope"))
    }

    @Test
    fun `valid score`() {
        assertTrue(HokmValidation.isValidScore(5))
        assertFalse(HokmValidation.isValidScore(8))
    }

    @Test
    fun `valid seat`() {
        assertTrue(HokmValidation.isValidSeat(0))
        assertTrue(HokmValidation.isValidSeat(3))
        assertFalse(HokmValidation.isValidSeat(4))
    }
}
