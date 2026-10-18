package ir.dorehami.games.hokm.ui

/**
 * HK-001 (player slice) — player model as **pure, unit-testable** logic.
 */
object HokmPlayer {

    data class Player(val seat: Int, val name: String, val isBot: Boolean = false, val isMe: Boolean = false)

    fun displayName(p: Player): String = if (p.isMe) "شما" else p.name

    fun isTeammate(a: Player, b: Player): Boolean = (a.seat + 2) % 4 == b.seat % 4

    fun isOpponent(a: Player, b: Player): Boolean = !isTeammate(a, b) && a.seat != b.seat
}
