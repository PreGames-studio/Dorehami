package ir.dorehami.games.hokm.ui

/**
 * HK-011 (json slice) — json helpers as **pure, unit-testable** logic.
 */
object HokmJson {

    fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    fun unescape(s: String): String = s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")

    fun quote(s: String): String = "\"${escape(s)}\""
}
