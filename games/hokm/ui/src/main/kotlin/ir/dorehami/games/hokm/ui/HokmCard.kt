package ir.dorehami.games.hokm.ui

/**
 * HK-003 (card slice) — card model as **pure, unit-testable** logic.
 */
object HokmCard {

    data class Card(val rank: Char, val suit: Char) {
        override fun toString(): String = "$rank$suit"
    }

    fun parse(code: String): Card? {
        if (code.length != 2) return null
        val r = code[0]
        val s = code[1]
        if (!HokmRank.isValid(r)) return null
        if (!HokmSuit.isValid(s)) return null
        return Card(r, s)
    }

    fun fullDeck(): List<Card> =
        HokmRank.ALL.flatMap { r -> HokmSuit.ALL.map { s -> Card(r, s) } }

    fun deckSize(): Int = 52

    fun isRed(card: Card): Boolean = HokmSuit.isRed(card.suit)

    fun labelFa(card: Card): String = "${HokmRank.labelFa(card.rank)} ${HokmSuit.nameFa(card.suit)}"
}
