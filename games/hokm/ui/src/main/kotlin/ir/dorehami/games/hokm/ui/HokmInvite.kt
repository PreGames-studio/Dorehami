package ir.dorehami.games.hokm.ui

/**
 * HK-037 (invite slice) — invite helpers as **pure, unit-testable** logic.
 */
object HokmInvite {

    data class Invite(val code: String, val from: String, val to: String, val timestampMs: Long, val accepted: Boolean = false)

    fun create(code: String, from: String, to: String, nowMs: Long): Invite = Invite(code, from, to, nowMs)

    fun accept(inv: Invite): Invite = inv.copy(accepted = true)

    fun isExpired(inv: Invite, nowMs: Long, ttlMs: Long = 300000): Boolean = (nowMs - inv.timestampMs) > ttlMs

    fun isValid(inv: Invite): Boolean = inv.code.isNotEmpty() && inv.from.isNotEmpty() && inv.to.isNotEmpty()
}
