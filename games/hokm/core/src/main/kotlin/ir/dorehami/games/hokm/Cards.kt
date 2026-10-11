package ir.dorehami.games.hokm

/** The four suits with the names Iranians actually use at the table. */
enum class Suit(val code: Char, val symbol: String, val titleFa: String) {
    SPADES('S', "♠", "پیک"),
    HEARTS('H', "♥", "دل"),
    DIAMONDS('D', "♦", "خشت"),
    CLUBS('C', "♣", "گشنیز"),
    ;

    companion object {
        fun fromCode(code: Char): Suit? = entries.firstOrNull { it.code == code.uppercaseChar() }

        /** Accepts the wire id (`spades`), the single letter (`S`) or the symbol (`♠`). */
        fun parse(text: String): Suit? {
            val t = text.trim()
            if (t.isEmpty()) return null
            if (t.length == 1) {
                return fromCode(t[0]) ?: entries.firstOrNull { it.symbol == t }
            }
            return entries.firstOrNull { it.name.equals(t, ignoreCase = true) }
        }
    }

    /** Wire id used in JSON (`"spades"`). */
    val id: String get() = name.lowercase()
}

/** Ranks in Hokm order: 2 is the lowest, Ace is the highest. */
enum class Rank(val value: Int, val code: Char, val labelFa: String) {
    TWO(2, '2', "۲"),
    THREE(3, '3', "۳"),
    FOUR(4, '4', "۴"),
    FIVE(5, '5', "۵"),
    SIX(6, '6', "۶"),
    SEVEN(7, '7', "۷"),
    EIGHT(8, '8', "۸"),
    NINE(9, '9', "۹"),
    TEN(10, 'T', "۱۰"),
    JACK(11, 'J', "سرباز"),
    QUEEN(12, 'Q', "بی‌بی"),
    KING(13, 'K', "شاه"),
    ACE(14, 'A', "آس"),
    ;

    companion object {
        fun fromCode(code: Char): Rank? = entries.firstOrNull { it.code == code.uppercaseChar() }
    }
}

/** One playing card. Wire format is two characters: rank code + suit code, e.g. `AS`, `TD`, `7C`. */
data class Card(val rank: Rank, val suit: Suit) : Comparable<Card> {
    val code: String get() = "${rank.code}${suit.code}"

    override fun toString(): String = code

    /** Sort by suit then rank — the usual way a hand is fanned. */
    override fun compareTo(other: Card): Int {
        val s = suit.ordinal.compareTo(other.suit.ordinal)
        return if (s != 0) s else rank.value.compareTo(other.rank.value)
    }

    companion object {
        fun parse(code: String): Card? {
            val c = code.trim()
            if (c.length != 2) return null
            val rank = Rank.fromCode(c[0]) ?: return null
            val suit = Suit.fromCode(c[1]) ?: return null
            return Card(rank, suit)
        }

        /** A full 52-card deck in canonical order (suits × ranks). */
        fun fullDeck(): List<Card> = Suit.entries.flatMap { s -> Rank.entries.map { r -> Card(r, s) } }
    }
}
