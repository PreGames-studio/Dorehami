package ir.dorehami.games.hokm.ui

/**
 * HK-016 (text slice) — text helpers as **pure, unit-testable** logic.
 */
object HokmText {

    fun truncate(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1) + "…"

    fun isEmpty(text: String?): Boolean = text.isNullOrBlank()

    fun capitalizeFirst(text: String): String =
        if (text.isEmpty()) text else text[0].uppercaseChar() + text.drop(1)

    fun wordCount(text: String): Int = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.size
}
