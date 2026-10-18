package ir.dorehami.games.hokm.ui

/**
 * HK-020 (validation slice) — input validation as **pure, unit-testable** logic.
 */
object HokmValidation {

    fun isValidRoomCode(code: String): Boolean = code.length == 6 && code.all { it.isLetterOrDigit() }

    fun isValidPlayerName(name: String): Boolean = name.trim().length in 2..20

    fun isValidStickerCode(code: String): Boolean = code in HokmStickers.allCodes()

    fun isValidScore(score: Int): Boolean = score in 0..7

    fun isValidSeat(seat: Int): Boolean = seat in 0..3
}
