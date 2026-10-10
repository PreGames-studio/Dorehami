package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.FixedClock
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.Player
import ir.dorehami.gamesdk.PlayerId
import ir.dorehami.gamesdk.SeededRng
import ir.dorehami.gamesdk.view
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** ابزارهای مشترکِ تست‌های حکم: ساختِ نشست با دست‌های معلوم و خواندنِ امنِ viewها. */

internal fun playerId(seat: Int): PlayerId = PlayerId("p$seat")

internal fun testPlayers(): List<Player> = listOf(
    Player(playerId(0), "شما", 0, isBot = false),
    Player(playerId(1), "راست", 1, isBot = true),
    Player(playerId(2), "یار", 2, isBot = true),
    Player(playerId(3), "چپ", 3, isBot = true),
)

/** ساختِ فهرستی از کارت‌ها از کدهای کوتاه (`"AS"`، `"TD"`). */
internal fun cards(vararg codes: String): List<Card> = codes.map { code ->
    requireNotNull(Card.fromCode(code)) { "کارت نامعتبر در تست: $code" }
}

/**
 * یک نشستِ حکم با دست‌های دقیقاً معلوم.
 *
 * @param hands دستِ هر چهار صندلی در دستِ اول (هر کدام ۱۳ کارت).
 * @param moreHands دست‌های بعدی اگر تست به آن‌ها نیاز داشته باشد.
 */
internal fun testSession(
    hands: List<List<Card>>,
    vararg moreHands: List<List<Card>>,
    options: HokmOptions = HokmOptions.default(),
    seed: Long = 7L,
    skipHakemFlip: Boolean = true,
): HokmSession {
    val config = GameConfig(
        matchId = "test-match",
        seed = seed,
        options = options.toJson(),
        players = testPlayers(),
        clock = FixedClock(),
        rng = SeededRng(seed),
    )
    return HokmSession(
        config = config,
        options = options,
        scriptedHands = listOf(hands) + moreHands.toList(),
        skipHakemFlip = skipHakemFlip,
    )
}

/** یک نشست با پخشِ واقعی (بدون دستِ از پیش‌آماده) — برای تستِ قطعیت و ربات. */
internal fun randomSession(seed: Long, options: HokmOptions = HokmOptions.default()): HokmSession {
    val config = GameConfig(
        matchId = "seed-$seed",
        seed = seed,
        options = options.toJson(),
        players = testPlayers(),
        clock = FixedClock(),
        rng = SeededRng(seed),
    )
    return HokmSession(config = config, options = options)
}

internal fun HokmSession.declare(seat: Int, suit: Suit): ApplyResult = apply(
    playerId(seat),
    GameAction(
        type = HokmModule.ACTION_DECLARE_TRUMP,
        payload = view { put(HokmModule.PAYLOAD_SUIT, JsonPrimitive(suit.name)) },
    ),
)

internal fun HokmSession.play(seat: Int, card: Card): ApplyResult = apply(
    playerId(seat),
    GameAction(
        type = HokmModule.ACTION_PLAY_CARD,
        payload = view { put(HokmModule.PAYLOAD_CARD, JsonPrimitive(card.code)) },
    ),
)

internal fun HokmSession.play(seat: Int, code: String): ApplyResult =
    play(seat, requireNotNull(Card.fromCode(code)) { "کارت نامعتبر در تست: $code" })

/** صندلی‌ای که حالا نوبتش است. */
internal fun HokmSession.turnSeat(): Int = publicView().int("turnSeat")

/** تعیینِ حکم و شروعِ بازی (تست‌هایی که به خودِ مرحلهٔ حکم کاری ندارند). */
internal fun HokmSession.declareAndStart(suit: Suit) {
    val result = declare(0, suit)
    check(result is ApplyResult.Ok) { "تعیینِ حکم باید پذیرفته شود، نشد: $result" }
}

/** بازی کردنِ چهار کارتِ یک دست پشت‌سرهم (به ترتیبِ نوبت). */
internal fun HokmSession.playTrick(vararg codes: String) {
    codes.forEach { code ->
        val seat = turnSeat()
        val result = play(seat, code)
        check(result is ApplyResult.Ok) {
            "انتظار داشتیم کارتِ $code از صندلیِ $seat پذیرفته شود، نشد: $result"
        }
    }
}

// --- خواندنِ امنِ مقادیر از viewها --------------------------------------------

internal fun JsonObject.string(key: String): String =
    requireNotNull(this[key]?.jsonPrimitive?.contentOrNull) { "کلیدِ $key در view نیست" }

internal fun JsonObject.stringOrNull(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.int(key: String): Int =
    requireNotNull(this[key]?.jsonPrimitive?.intOrNull) { "کلیدِ $key در view نیست یا عدد نیست" }

internal fun JsonObject.bool(key: String): Boolean =
    requireNotNull(this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()) {
        "کلیدِ $key در view نیست یا بولین نیست"
    }

internal fun JsonObject.intList(key: String): List<Int> =
    (this[key] as JsonArray).map { it.jsonPrimitive.intOrNull ?: -1 }

internal fun JsonObject.stringList(key: String): List<String> =
    (this[key] as JsonArray).map { it.jsonPrimitive.content }

internal fun JsonObject.objList(key: String): List<JsonObject> =
    (this[key] as JsonArray).map { it.jsonObject }

/** خواندنِ یک شیءِ تودرتو از view (مثل `lastTrick`). */
internal fun JsonObject.obj(key: String): JsonObject =
    requireNotNull(this[key] as? JsonObject) { "کلیدِ $key در view یک شیء نیست" }

/**
 * بقیهٔ بازی را ربات‌ها بازی می‌کنند (تا پایانِ کل مسابقه).
 *
 * هر حرکتِ ربات از همان مسیرِ `apply` می‌گذرد، پس اگر ربات کارتِ غیرمجازی انتخاب کند اینجا
 * تست شکست می‌خورد.
 */
internal fun HokmSession.autoPlay(maxActions: Int = 2_000): Int {
    var played = 0
    while (phase != Phase.FINISHED && played < maxActions) {
        val view = publicView()
        val seat = if (view.string("stage") == "trump") view.int("hakemSeat") else view.int("turnSeat")
        val action = botActionFor(playerId(seat)) ?: break
        val result = apply(playerId(seat), action)
        check(result is ApplyResult.Ok) { "ربات حرکتِ غیرمجاز فرستاد: $result" }
        played++
    }
    return played
}

/** فقط همین دست را با ربات‌ها تا انتها بازی می‌کند (برای تستِ پایانِ یک دست). */
internal fun HokmSession.autoPlayCurrentHand(maxActions: Int = 100): Int {
    val hand = publicView().int("handNumber")
    var played = 0
    while (publicView().int("handNumber") == hand && phase != Phase.FINISHED && played < maxActions) {
        val view = publicView()
        val seat = if (view.string("stage") == "trump") view.int("hakemSeat") else view.int("turnSeat")
        val action = botActionFor(playerId(seat)) ?: break
        val result = apply(playerId(seat), action)
        check(result is ApplyResult.Ok) { "ربات حرکتِ غیرمجاز فرستاد: $result" }
        played++
    }
    return played
}
