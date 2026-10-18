package ir.dorehami.games.hokm.ui

/**
 * HK-045 (friends slice) — friends helpers as **pure, unit-testable** logic.
 */
object HokmFriends {

    data class Friend(val id: String, val name: String, val online: Boolean = false)

    fun add(list: List<Friend>, f: Friend): List<Friend> =
        if (list.any { it.id == f.id }) list else list + f

    fun remove(list: List<Friend>, id: String): List<Friend> = list.filter { it.id != id }

    fun onlineCount(list: List<Friend>): Int = list.count { it.online }

    fun find(list: List<Friend>, id: String): Friend? = list.firstOrNull { it.id == id }
}
