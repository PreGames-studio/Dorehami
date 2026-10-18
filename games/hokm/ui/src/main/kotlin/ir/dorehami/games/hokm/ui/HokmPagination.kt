package ir.dorehami.games.hokm.ui

/**
 * HK-020 (pagination slice) — pagination helpers as **pure, unit-testable** logic.
 */
object HokmPagination {

    fun totalPages(itemCount: Int, pageSize: Int): Int =
        if (pageSize <= 0) 0 else (itemCount + pageSize - 1) / pageSize

    fun pageItems(items: List<String>, page: Int, pageSize: Int): List<String> {
        val start = (page - 1) * pageSize
        if (start < 0 || start >= items.size) return emptyList()
        return items.subList(start, (start + pageSize).coerceAtMost(items.size))
    }

    fun hasNext(page: Int, totalPages: Int): Boolean = page < totalPages

    fun hasPrev(page: Int): Boolean = page > 1
}
