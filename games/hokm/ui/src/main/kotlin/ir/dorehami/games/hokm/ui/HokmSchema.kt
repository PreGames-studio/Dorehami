package ir.dorehami.games.hokm.ui

/**
 * HK-011 (schema slice) — schema validation helpers as **pure, unit-testable** logic.
 */
object HokmSchema {

    fun isNonEmpty(value: String): Boolean = value.trim().isNotEmpty()

    fun inRange(value: Int, min: Int, max: Int): Boolean = value in min..max

    fun matchesPattern(value: String, regex: Regex): Boolean = regex.matches(value)
}
