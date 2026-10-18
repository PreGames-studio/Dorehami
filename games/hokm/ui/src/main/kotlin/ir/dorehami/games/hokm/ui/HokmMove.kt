package ir.dorehami.games.hokm.ui

/**
 * HK-005 (move slice) — move model as **pure, unit-testable** logic.
 */
object HokmMove {

    data class Move(val seat: Int, val card: String, val timestampMs: Long)

    fun create(seat: Int, card: String, nowMs: Long): Move = Move(seat, card, nowMs)

    fun isValid(move: Move, hand: List<String>): Boolean = move.card in hand

    fun format(move: Move): String = " seat ${move.seat}: ${move.card}"
}
