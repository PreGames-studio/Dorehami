package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.Capability
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameId
import ir.dorehami.gamesdk.GameMeta
import ir.dorehami.gamesdk.GameModule
import ir.dorehami.gamesdk.GameSession
import ir.dorehami.gamesdk.PlayerId
import kotlinx.serialization.json.JsonObject

/**
 * ماژولِ بازی حکم: تنها نقطه‌ای که اپ و سرور با آن حرف می‌زنند.
 *
 * پاسخِ هفت سؤالِ قرارداد:
 * ۱) چهار نفر، دو تیمِ دونفره، با پشتیبانی از بلوتوث/آنلاین/حریف تصادفی/ربات/یک‌گوشی —
 *    در [meta].
 * ۲ و ۴ و ۵ و ۷) در [HokmSession].
 * ۳) در `HokmSession.publicView/privateView`.
 * ۶) در [botAction] که از [HokmBot] استفاده می‌کند.
 */
object HokmModule : GameModule {

    /** شناسهٔ ثابتِ بازی (در رکوردها و فهرست بازی‌ها با همین نام ذخیره می‌شود). */
    const val ID = "hokm"

    /** حرکت: تعیینِ خالِ حکم توسط حاکم. بدنه: `{"suit":"HEARTS"}`. */
    const val ACTION_DECLARE_TRUMP = "declare_trump"

    /** حرکت: بازی کردنِ یک کارت. بدنه: `{"card":"AS"}`. */
    const val ACTION_PLAY_CARD = "play_card"

    /** کلیدِ بدنه برای خال (`suit`). */
    const val PAYLOAD_SUIT = "suit"

    /** کلیدِ بدنه برای کارت (`card`). */
    const val PAYLOAD_CARD = "card"

    override val meta = GameMeta(
        id = GameId(ID),
        titleFa = "حکم",
        titleEn = "Hokm",
        taglineFa = "محبوب‌ترین بازی ورقِ ایران — چهار نفر، دو تیم",
        minPlayers = 4,
        maxPlayers = 4,
        teamCount = 2,
        playersPerTeam = 2,
        iconKey = "hokm",
        estimatedMinutes = 12,
        capabilities = setOf(
            Capability.BLUETOOTH_OFFLINE.key,
            Capability.ONLINE.key,
            Capability.RANDOM_MATCH.key,
            Capability.BOTS.key,
            Capability.PASS_AND_PLAY.key,
        ),
        defaultOptions = HokmOptions.defaultJson(),
    )

    override fun defaultOptions(): JsonObject = HokmOptions.defaultJson()

    override fun validateOptions(options: JsonObject): JsonObject =
        HokmOptions.parse(options).toJson()

    override fun preferredPartySize(): Int = 4

    override fun create(config: GameConfig): GameSession =
        HokmSession(config, HokmOptions.parse(config.options))

    override val botAction: ((GameSession, PlayerId) -> GameAction?)? = { session, player ->
        (session as? HokmSession)?.botActionFor(player)
    }
}
