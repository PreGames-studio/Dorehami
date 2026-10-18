package ir.dorehami.games.hokm.ui

/**
 * HK-044 (avatar slice) — avatar helpers as **pure, unit-testable** logic.
 */
object HokmAvatar {

    const val COUNT: Int = 12

    fun isValid(id: Int): Boolean = id in 0 until COUNT

    fun clamp(id: Int): Int = id.coerceIn(0, COUNT - 1)

    fun name(id: Int): String = "avatar_$id"
}
