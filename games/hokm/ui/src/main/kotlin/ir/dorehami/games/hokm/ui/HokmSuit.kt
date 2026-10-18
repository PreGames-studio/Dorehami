package ir.dorehami.games.hokm.ui

/**
 * HK-003 (suit slice) — suit helpers as **pure, unit-testable** logic.
 */
object HokmSuit {

    val ALL = listOf('♠', '♥', '♦', '♣')

    fun isRed(suit: Char): Boolean = suit == '♥' || suit == '♦'

    fun isBlack(suit: Char): Boolean = suit == '♠' || suit == '♣'

    fun nameFa(suit: Char): String = when (suit) {
        '♠' -> "پیک"
        '♥' -> "دل"
        '♦' -> "خشت"
        '♣' -> "گشنیز"
        else -> "?"
    }

    fun isValid(suit: Char): Boolean = suit in ALL
}
