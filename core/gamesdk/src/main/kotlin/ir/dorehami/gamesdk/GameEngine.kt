package ir.dorehami.gamesdk

import ir.dorehami.protocol.ErrCodes
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * تنها نویسندهٔ وضعیتِ بازی.
 *
 * حلقهٔ رسمی همیشه همین است: کلاینت حرکت می‌فرستد ← موتور آن را به نشست می‌دهد ← اگر پذیرفته شد در
 * لاگ می‌نویسد و رویدادها را پخش می‌کند. نه سرور و نه کلاینت حق ندارند وضعیتِ بعدی را خودشان حساب
 * کنند؛ به همین دلیل یک قاعده در بلوتوث و آنلاین و تک‌نفره دقیقاً یکسان رفتار می‌کند.
 *
 * @property module ماژول بازی (مثل `HokmModule`).
 * @property config سید و تنظیمات و بازیکن‌ها.
 */
class GameEngine(
    val module: GameModule,
    private val config: GameConfig,
) {
    /** نشستِ زندهٔ بازی. موتور مالک آن است؛ کسی مستقیماً روی آن `apply` نمی‌زند. */
    val session: GameSession = module.create(config)

    /** رویدادهای لحظهٔ ساخت (پخش کارتِ اولیه، تعیینِ حاکم…). */
    val bootEmissions: List<Emission> = session.drainPendingEmissions()

    private val actions = mutableListOf<LoggedAction>()

    /** لاگِ حرکت‌ها — همان چیزی که با آن می‌توان دست را بازپخش و راستی‌آزمایی کرد. */
    fun actionLog(): List<LoggedAction> = actions.toList()

    /**
     * اعمال یک حرکت از طرف یک بازیکن.
     *
     * ترتیبِ بررسی: (۱) بازیکن عضو دست است؟ (۲) بازی تمام نشده؟ (۳) خودِ نشست تصمیم می‌گیرد.
     * در صورت پذیرش، حرکت در لاگ ثبت می‌شود؛ در صورت رد، هیچ چیزی تغییر نمی‌کند (حتی `version`).
     */
    fun submit(player: PlayerId, action: GameAction): ApplyResult {
        if (config.seatOf(player) == null) {
            return ApplyResult.Rejected(ErrCodes.NOT_IN_MATCH, "شما عضو این دست نیستید")
        }
        if (session.phase == Phase.FINISHED) {
            return ApplyResult.Rejected(ErrCodes.GAME_FINISHED, "این دست تمام شده است")
        }

        val result = session.apply(player, action)
        if (result is ApplyResult.Ok) {
            actions += LoggedAction(
                seq = actions.size.toLong(),
                player = player.value,
                type = action.type,
                payload = action.payload,
                atMs = config.clock.nowMs(),
            )
        }
        return result
    }

    /** اجرای یک حرکتِ ربات برای بازیکنِ [player] (ربات هم در همان لاگ ثبت می‌شود). */
    fun submitBotTurn(player: PlayerId): ApplyResult? {
        val policy = module.botAction ?: return null
        val action = policy(session, player) ?: return null
        return submit(player, action)
    }

    /** نمای عمومیِ فعلی (برای همهٔ اعضای اتاق). */
    fun publicView(): JsonObject = session.publicView()

    /** نمای خصوصیِ یک بازیکن (فقط دستِ خودش را دارد). */
    fun privateView(player: PlayerId): JsonObject = session.privateView(player)

    /** رکوردِ کامل و قابلِ بازپخشِ این دست. */
    fun record(): MatchRecord = MatchRecord.of(
        matchId = config.matchId,
        gameId = module.meta.id.value,
        mode = "local",
        seed = config.seed,
        options = config.options,
        players = config.players,
        actions = actions.toList(),
        result = session.result(),
    )

    /** امضای این دست (برای مقایسهٔ بازپخش و ضدتقلب). */
    fun digest(): String = record().digest
}

/** یک حرکتِ ثبت‌شده در لاگِ دست. */
data class LoggedAction(
    val seq: Long,
    val player: String,
    val type: String,
    val payload: JsonObject,
    val atMs: Long,
) {
    fun toJson(): JsonObject = view {
        put("seq", JsonPrimitive(seq))
        put("player", JsonPrimitive(player))
        put("type", JsonPrimitive(type))
        put("payload", payload)
        put("atMs", JsonPrimitive(atMs))
    }
}
