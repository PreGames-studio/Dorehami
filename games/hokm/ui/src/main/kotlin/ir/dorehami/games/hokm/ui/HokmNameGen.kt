package ir.dorehami.games.hokm.ui

/**
 * HK-044 (name gen slice) — name generator helpers as **pure, unit-testable** logic.
 */
object HokmNameGen {

    val ADJECTIVES = listOf("سریع", "قدرتمند", "هوشمند", "شجاع")
    val NOUNS = listOf("شیر", "پلنگ", "عقاب", "گرگ")

    fun generate(seed: Long): String {
        val adj = ADJECTIVES[(seed % ADJECTIVES.size).toInt()]
        val noun = NOUNS[((seed / ADJECTIVES.size) % NOUNS.size).toInt()]
        return "$adj $noun"
    }

    fun isValid(name: String): Boolean = name.length in 2..20
}
