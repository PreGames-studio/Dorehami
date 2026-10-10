package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.Rank
import ir.dorehami.games.hokm.Suit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** یک کارت در رابط کاربری (با کد، خال و رتبه برای راحتیِ رسم). */
data class UiCard(
    val code: String,
    val suit: Suit,
    val rank: Rank,
) {
    val faName: String
        get() = "${rank.faName} ${suit.faName}"
}

/** یک کارتِ بازی‌شده روی میز. */
data class UiPlay(val seat: Int, val card: UiCard)

/** یک صندلیِ میز (بازیکنِ نشسته). */
data class UiSeat(
    val seat: Int,
    val id: String,
    val name: String,
    val team: Int,
    val bot: Boolean,
    val cards: Int,
)

/** دستِ تمام‌شده، برای انیمیشنِ جمع‌کردن. */
data class UiTrick(
    val number: Int,
    val plays: List<UiPlay>,
    val winnerSeat: Int,
    val ruffed: Boolean,
)

/** پیامِ بزرگِ وسطِ صفحه (کوت، حاکم‌کُتی، پایانِ دست). */
data class UiBanner(
    val title: String,
    val subtitle: String,
    val kind: BannerKind,
    val key: Long,
)

enum class BannerKind { GOOD, BAD, BIG }

/** نتیجهٔ نهایی برای دیالوگِ پایان. */
data class UiResult(
    val winnerTeam: Int,
    val scores: List<Int>,
    val hands: Int,
    val yourTeam: Int,
) {
    val youWon: Boolean
        get() = winnerTeam == yourTeam
}

/**
 * تمامِ چیزی که صفحهٔ حکم برای رسم نیاز دارد.
 *
 * این مدل فقط از `privateView` ساخته می‌شود (یعنی همان چیزی که این بازیکن حق دارد ببیند)؛
 * هیچ داده‌ای از دستِ بقیه در آن نیست.
 */
data class HokmUiState(
    val version: Long = 0,
    val phase: String = "setup",
    val stage: String = "trump",
    val youId: String = "",
    val youSeat: Int = 0,
    val yourTeam: Int = 0,
    val isHakem: Boolean = false,
    val isYourTurn: Boolean = false,
    val hand: List<UiCard> = emptyList(),
    val legalCards: Set<String> = emptySet(),
    val trump: Suit? = null,
    val trumpFa: String = "",
    val trumpSymbol: String = "",
    val hakemId: String = "",
    val hakemSeat: Int = 0,
    val dealerSeat: Int = 0,
    val turnId: String = "",
    val turnSeat: Int = 0,
    val seats: List<UiSeat> = emptyList(),
    val trick: List<UiPlay> = emptyList(),
    val lastTrick: UiTrick? = null,
    val tricks: List<Int> = listOf(0, 0),
    val scores: List<Int> = listOf(0, 0),
    val cardsLeft: List<Int> = listOf(0, 0, 0, 0),
    val handNumber: Int = 1,
    val trickNumber: Int = 1,
    val targetPoints: Int = 7,
    val turnSeconds: Int = 0,
    val result: UiResult? = null,
    val banner: UiBanner? = null,
    val toast: String? = null,
) {
    /** آیا بازیکنِ محلی باید حکم را انتخاب کند؟ */
    val youMustDeclareTrump: Boolean
        get() = stage == "trump" && isHakem
}

// ---------------------------------------------------------------------------
// تبدیلِ view به مدلِ رابط کاربری
// ---------------------------------------------------------------------------

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.num(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0

private fun JsonObject.bool(key: String): Boolean =
    this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

private fun JsonObject.ints(key: String): List<Int> =
    (this[key] as? JsonArray)?.map { it.jsonPrimitive.intOrNull ?: 0 } ?: emptyList()

private fun uiCard(code: String): UiCard {
    val card = Card.fromCode(code) ?: Card(Suit.SPADES, Rank.TWO)
    return UiCard(code = card.code, suit = card.suit, rank = card.rank)
}

/** ساختِ وضعیتِ رابط کاربری از نمای خصوصیِ بازیکنِ محلی. */
internal fun parseHokmView(view: JsonObject): HokmUiState {
    val trumpName = view.str("trump")
    val trickPlays = (view["trick"] as? JsonArray).orEmpty().map { element ->
        val obj = element.jsonObject
        UiPlay(seat = obj.num("seat"), card = uiCard(obj.str("card").orEmpty()))
    }
    val lastTrickObject = view["lastTrick"] as? JsonObject
    val lastTrick = lastTrickObject?.let { obj ->
        val plays = (obj["cards"] as? JsonArray).orEmpty().map { element ->
            val play = element.jsonObject
            UiPlay(seat = play.num("seat"), card = uiCard(play.str("card").orEmpty()))
        }
        UiTrick(
            number = obj.num("number"),
            plays = plays,
            winnerSeat = obj.num("winnerSeat"),
            ruffed = obj.bool("ruffed"),
        )
    }
    val resultObject = view["result"] as? JsonObject
    val result = resultObject?.let { obj ->
        UiResult(
            winnerTeam = obj.num("winnerTeam"),
            scores = obj.ints("scores"),
            hands = obj.num("hands"),
            yourTeam = view.num("team"),
        )
    }
    val seats = (view["seats"] as? JsonArray).orEmpty().map { element ->
        val obj = element.jsonObject
        UiSeat(
            seat = obj.num("seat"),
            id = obj.str("id").orEmpty(),
            name = obj.str("name").orEmpty(),
            team = obj.num("team"),
            bot = obj.bool("bot"),
            cards = obj.num("cards"),
        )
    }

    return HokmUiState(
        version = view.num("version").toLong(),
        phase = view.str("phase") ?: "setup",
        stage = view.str("stage") ?: "trump",
        youId = view.str("you").orEmpty(),
        youSeat = view.num("youSeat"),
        yourTeam = view.num("team"),
        isHakem = view.bool("isHakem"),
        isYourTurn = view.bool("isYourTurn"),
        hand = (view["hand"] as? JsonArray).orEmpty()
            .map { uiCard(it.jsonPrimitive.content) }
            .sortedWith(compareByDescending<UiCard> { it.suit.ordinal }.thenByDescending { it.rank.order }),
        legalCards = (view["legalCards"] as? JsonArray).orEmpty()
            .map { it.jsonPrimitive.content }
            .toSet(),
        trump = trumpName?.let { runCatching { Suit.valueOf(it) }.getOrNull() },
        trumpFa = view.str("trumpFa").orEmpty(),
        trumpSymbol = view.str("trumpSymbol").orEmpty(),
        hakemId = view.str("hakem").orEmpty(),
        hakemSeat = view.num("hakemSeat"),
        dealerSeat = view.num("dealerSeat"),
        turnId = view.str("turn").orEmpty(),
        turnSeat = view.num("turnSeat"),
        seats = seats,
        trick = trickPlays,
        lastTrick = lastTrick,
        tricks = view.ints("tricks").ifEmpty { listOf(0, 0) },
        scores = view.ints("scores").ifEmpty { listOf(0, 0) },
        cardsLeft = view.ints("cardsLeft").ifEmpty { listOf(0, 0, 0, 0) },
        handNumber = view.num("handNumber"),
        trickNumber = view.num("trickNumber"),
        targetPoints = (view["rules"] as? JsonObject)?.num("targetPoints") ?: 7,
        turnSeconds = (view["rules"] as? JsonObject)?.num("turnSeconds") ?: 0,
        result = result,
    )
}

/** اعدادِ فارسی برای رابط کاربری (الزامِ مستندات: اعداد با ارقام فارسی). */
fun persianDigits(value: Int): String {
    val digits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    return value.toString().map { ch -> if (ch in '0'..'9') digits[ch - '0'] else ch }.joinToString("")
}

/** اعدادِ فارسی برای رشته‌هایی که از قبل ساخته شده‌اند. */
fun persianDigits(value: String): String {
    val digits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
    return value.map { ch -> if (ch in '0'..'9') digits[ch - '0'] else ch }.joinToString("")
}
