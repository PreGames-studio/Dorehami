package ir.dorehami.games.hokm.ui

/**
 * HK-020 (search slice) — search helpers as **pure, unit-testable** logic.
 */
object HokmSearch {

    fun contains(text: String, query: String, ignoreCase: Boolean = true): Boolean =
        text.contains(query, ignoreCase = ignoreCase)

    fun filter(items: List<String>, query: String): List<String> =
        if (query.isBlank()) items else items.filter { contains(it, query) }
}
