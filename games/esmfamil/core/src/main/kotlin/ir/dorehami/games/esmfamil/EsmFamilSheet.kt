package ir.dorehami.games.esmfamil

import ir.dorehami.protocol.PlayerId

/**
 * The paper sheet of اسم فامیل: the categories a round asks for, the Persian letters a round can
 * start with, and the pure functions that turn a table of free-text answers into points.
 *
 * Everything here is a pure function of its arguments: no clock, no `Random()`, no Android. The
 * session calls it, the UI calls it for the live preview, and the tests call it directly.
 */
object EsmFamilSheet {

    /** One column of the sheet. `id` is what travels on the wire; `titleFa` is what players read. */
    data class Category(val id: String, val titleFa: String)

    /** Every column the game ships with. */
    val ALL: List<Category> = listOf(
        Category("name", "اسم"),
        Category("family", "فامیل"),
        Category("city", "شهر"),
        Category("animal", "حیوان"),
        Category("food", "خوراکی"),
        Category("object", "اشیا"),
        Category("plant", "گیاه"),
        Category("movie", "فیلم"),
    )

    /** The six columns a fresh game starts with (the classic paper sheet). */
    val DEFAULT_IDS: List<String> = listOf("name", "family", "city", "animal", "food", "object")

    fun byId(id: String): Category? = ALL.firstOrNull { it.id == id }

    fun titleOf(id: String): String = byId(id)?.titleFa ?: id

    /**
     * Letters a round may start with: the ones Iranian tables actually agree on.
     * `آ` is deliberately absent — it is scored as `ا` by [normalise], so asking for it would be a
     * trap rather than a rule.
     */
    val COMMON_LETTERS: List<Char> = listOf(
        'ا', 'ب', 'پ', 'ت', 'ج', 'چ', 'ح', 'خ', 'د', 'ر', 'ز', 'س', 'ش',
        'ع', 'ف', 'ق', 'ک', 'گ', 'ل', 'م', 'ن', 'و', 'ه', 'ی',
    )

    /** Every Persian letter, for tables that want the hard ones (ذ ض ظ غ ث ص ط ژ). */
    val ALL_LETTERS: List<Char> = listOf(
        'ا', 'ب', 'پ', 'ت', 'ث', 'ج', 'چ', 'ح', 'خ', 'د', 'ذ', 'ر', 'ز', 'ژ', 'س', 'ش',
        'ص', 'ض', 'ط', 'ظ', 'ع', 'غ', 'ف', 'ق', 'ک', 'گ', 'ل', 'م', 'ن', 'و', 'ه', 'ی',
    )

    const val LETTERS_COMMON = "common"
    const val LETTERS_ALL = "all"

    fun letters(choice: String): List<Char> =
        if (choice == LETTERS_ALL) ALL_LETTERS else COMMON_LETTERS

    /**
     * Makes two answers comparable without changing what the player sees.
     *
     * Persian text arrives with four different spellings of the same letter: Arabic `ي`/`ك`,
     * `ة`/`ه`, `أ/إ/آ/ا`, plus tatweel and diacritics. Duplicate detection that ignores this would
     * pay two players for the same answer typed on different keyboards.
     */
    fun normalise(text: String): String {
        val folded = StringBuilder(text.length)
        for (raw in text) {
            val c = when (raw) {
                'ي', 'ئ' -> 'ی'
                'ك' -> 'ک'
                'ة', 'ۀ' -> 'ه'
                'أ', 'إ', 'آ', 'ٱ', 'ٲ', 'ٳ' -> 'ا'
                'ؤ' -> 'و'
                '\u0640' -> continue // tatweel
                in '\u064B'..'\u0652' -> continue // fatha/kasra/damma/shadda/sukun…
                '\u200c', '\u200f', '\u200e' -> continue // ZWNJ / direction marks
                else -> raw
            }
            folded.append(c)
        }
        return folded.toString().trim().lowercase().replace(Regex("\\s+"), " ")
    }

    /** Comparison key: the same answer with or without spaces counts as the same answer. */
    fun key(text: String): String = normalise(text).replace(" ", "")

    /** `true` when [answer] starts with the round's [letter] (after normalisation). */
    fun startsWithLetter(answer: String, letter: Char): Boolean {
        val k = key(answer)
        if (k.isEmpty()) return false
        return k.first() == normalise(letter.toString()).firstOrNull() ?: letter
    }

    /** Points a single cell can carry. Classic Iranian scoring: only you ⇒ 10, shared ⇒ 5. */
    const val POINTS_UNIQUE = 10
    const val POINTS_SHARED = 5
    const val POINTS_NONE = 0

    /**
     * Score one round.
     *
     * A cell pays 0 when it is empty or starts with the wrong letter, [POINTS_SHARED] when another
     * player wrote the same answer in that column, and [POINTS_UNIQUE] when it stands alone.
     */
    fun scoreRound(
        letter: Char,
        categories: List<String>,
        answers: Map<PlayerId, Map<String, String>>,
    ): Map<PlayerId, Map<String, Int>> {
        val out = LinkedHashMap<PlayerId, Map<String, Int>>()
        val players = answers.keys.toList()
        players.forEach { out[it] = LinkedHashMap() }
        for (category in categories) {
            // Who wrote something valid in this column, and what does it compare to?
            val valid = LinkedHashMap<PlayerId, String>()
            for (player in players) {
                val raw = answers[player]?.get(category).orEmpty()
                if (raw.isBlank()) continue
                if (!startsWithLetter(raw, letter)) continue
                valid[player] = key(raw)
            }
            val counts = valid.values.groupingBy { it }.eachCount()
            for (player in players) {
                val mine = valid[player]
                val points = when {
                    mine == null -> POINTS_NONE
                    (counts[mine] ?: 0) > 1 -> POINTS_SHARED
                    else -> POINTS_UNIQUE
                }
                (out[player] as MutableMap)[category] = points
            }
        }
        return out
    }

    /** Which players filled every column with a valid answer (used for the "همه را نوشتی" badge). */
    fun completedSheet(points: Map<String, Int>): Boolean =
        points.isNotEmpty() && points.values.all { it > 0 }
}
