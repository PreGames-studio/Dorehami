package ir.dorehami.games.hokm

/**
 * مدلِ داده‌ایِ ورقِ حکم: خال، رتبه، کارت و دستهٔ ۵۲تایی.
 *
 * این فایل هیچ وابستگی‌ای به هسته ندارد (حتی `kotlinx.serialization`) تا قواعدِ بازی در تمیزترین
 * حالت ممکن بمانند و بشود آن‌ها را جداگانه تست کرد.
 */

/** چهار خالِ استاندارد با نام و نمادِ فارسی. */
enum class Suit(
    /** نام فارسیِ خال (نمایش در رابط کاربری). */
    val faName: String,
    /** نمادِ خال (♠ ♥ ♦ ♣) برای چاپِ سریع و لاگ. */
    val symbol: String,
    /** حرفِ خال در کدِ کارت (`S`, `H`, `D`, `C`). */
    val code: Char,
    /** آیا خال قرمز است (برای رنگِ متنِ کارت). */
    val isRed: Boolean,
) {
    SPADES("پیک", "♠", 'S', isRed = false),
    HEARTS("دل", "♥", 'H', isRed = true),
    DIAMONDS("خشت", "♦", 'D', isRed = true),
    CLUBS("خاج", "♣", 'C', isRed = false),
    ;

    companion object {
        private val byCode = entries.associateBy { it.code }

        /** برگرداندنِ خال از روی حرفِ کد؛ اگر نامعتبر باشد `null`. */
        fun fromCode(code: Char): Suit? = byCode[code.uppercaseChar()]
    }
}

/** رتبه‌های کارت از ۲ تا آس. [order] همان قدرتِ کارت در خالِ خودش است. */
enum class Rank(
    /** قدرتِ کارت: هرچه بالاتر، قوی‌تر (آس = ۱۴). */
    val order: Int,
    /** نمادِ روی کارت (`A`, `K`, `Q`, `J`, `10`…`2`). */
    val label: String,
    /** نام فارسی (آس، شاه، بی‌بی، سرباز). */
    val faName: String,
) {
    TWO(2, "2", "دو"),
    THREE(3, "3", "سه"),
    FOUR(4, "4", "چهار"),
    FIVE(5, "5", "پنج"),
    SIX(6, "6", "شش"),
    SEVEN(7, "7", "هفت"),
    EIGHT(8, "8", "هشت"),
    NINE(9, "9", "نه"),
    TEN(10, "10", "ده"),
    JACK(11, "J", "سرباز"),
    QUEEN(12, "Q", "بی‌بی"),
    KING(13, "K", "شاه"),
    ACE(14, "A", "آس"),
    ;

    companion object {
        private val byLabel = entries.associateBy { it.label }

        /** برگرداندنِ رتبه از روی برچسب؛ اگر نامعتبر باشد `null`. */
        fun fromLabel(label: String): Rank? = byLabel[label.uppercase()]
    }
}

/**
 * یک ورق.
 *
 * @property suit خالِ کارت.
 * @property rank رتبهٔ کارت.
 */
data class Card(val suit: Suit, val rank: Rank) : Comparable<Card> {
    /** کدِ کوتاهِ کارت، مثل `"AS"` (آس پیک) یا `"TD"` (ده خشت). */
    val code: String
        get() = "${rank.label}${suit.code}"

    /** نام کاملِ فارسی، مثل «آس پیک». */
    val faName: String
        get() = "${rank.faName} ${suit.faName}"

    override fun compareTo(other: Card): Int = compareValuesBy(this, other, { it.suit }, { it.rank.order })

    override fun toString(): String = code

    companion object {
        /** ساخت کارت از کد (مثل `"AS"`)؛ اگر کد نامعتبر باشد `null`. */
        fun fromCode(code: String): Card? {
            val raw = code.trim().uppercase()
            if (raw.length < 2) return null
            val suit = Suit.fromCode(raw.last()) ?: return null
            val rank = Rank.fromLabel(raw.dropLast(1)) ?: return null
            return Card(suit, rank)
        }

        /**
         * دستهٔ کاملِ ۵۲تایی به ترتیبِ ثابت.
         *
         * ترتیب ثابت است چون سیدِ دست روی آن اعمال می‌شود: یک سید همیشه همان دسته را می‌دهد و
         * این زیربنای «قطعیت» است (یک دست را می‌توان بعداً دقیقاً بازپخش کرد).
         */
        fun fullDeck(): List<Card> = buildList {
            for (suit in Suit.entries) {
                for (rank in Rank.entries) {
                    add(Card(suit, rank))
                }
            }
        }
    }
}

/**
 * قدرتِ یک کارت در یک دست با خالِ حکمِ معلوم.
 *
 * حکم از هر خالِ دیگری قوی‌تر است؛ حتی ۲ِ حکم از آسِ خال‌های دیگر بالاتر است.
 */
internal fun Card.strength(trump: Suit, ledSuit: Suit): Int {
    val base = if (suit == trump) 100 else if (suit == ledSuit) 0 else -100
    return base + rank.order
}

/** آیا این کارت، کارتِ فعلاً برندهٔ [best] را در همین دست می‌برد؟ */
internal fun Card.beats(best: Card, trump: Suit, ledSuit: Suit): Boolean =
    strength(trump, ledSuit) > best.strength(trump, ledSuit)
