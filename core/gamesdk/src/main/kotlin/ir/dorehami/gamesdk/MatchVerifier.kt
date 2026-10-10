package ir.dorehami.gamesdk

/**
 * راستی‌آزماییِ یک دست با بازپخش.
 *
 * ایده ساده است: رکورد فقط «سید + لاگ حرکت‌ها» است، پس اگر همان بازی را با همان سید دوباره اجرا
 * کنیم و همان حرکت‌ها را به ترتیب اعمال کنیم، باید به همان امضا و همان جدول برسیم. هر تفاوت یعنی
 * رکورد دستکاری شده یا نسخهٔ قواعد فرق کرده است.
 *
 * کاربردها: سرور بعد از هر دست آنلاین، و تورنمنت‌ها برای رد کردنِ نتیجهٔ ساختگی.
 */
object MatchVerifier {

    /** نتیجهٔ راستی‌آزمایی. */
    data class VerifyResult(
        /** آیا امضا و نتیجه با بازپخش یکی بود؟ */
        val ok: Boolean,
        /** امضای ذخیره‌شده در رکورد. */
        val expectedDigest: String,
        /** امضای به‌دست‌آمده از بازپخش. */
        val actualDigest: String,
        /** دلیلِ رد شدن (فارسی، آمادهٔ نمایش/لاگ). */
        val reasonFa: String? = null,
    )

    /**
     * یک رکورد را روی ماژولِ [module] بازپخش می‌کند.
     *
     * @param record رکوردِ ذخیره‌شده (از کلاینت یا دیتابیس).
     * @param module ماژولِ همان بازی (`record.gameId` باید با شناسهٔ ماژول یکی باشد).
     */
    fun replay(record: MatchRecord, module: GameModule): VerifyResult {
        if (record.schema != MatchRecord.SCHEMA) {
            return VerifyResult(
                ok = false,
                expectedDigest = record.digest,
                actualDigest = record.digest,
                reasonFa = "نسخهٔ قالب رکورد پشتیبانی نمی‌شود (${record.schema})",
            )
        }
        if (record.gameId != module.meta.id.value) {
            return VerifyResult(
                ok = false,
                expectedDigest = record.digest,
                actualDigest = record.digest,
                reasonFa = "این رکورد برای بازی «${record.gameId}» است، نه «${module.meta.id.value}»",
            )
        }

        val config = GameConfig(
            matchId = record.matchId,
            seed = record.seed,
            options = record.options,
            players = record.players,
            clock = FixedClock(),
            rng = SeededRng(record.seed),
        )
        val engine = GameEngine(module, config)
        val bySeat = record.players.sortedBy { it.seat }

        for (action in record.actions) {
            val seat = action.player.toIntOrNull()
            val player = if (seat != null && seat in bySeat.indices) {
                bySeat[seat].id
            } else {
                record.players.firstOrNull { it.id.value == action.player }?.id
            }
            if (player == null) {
                return VerifyResult(
                    ok = false,
                    expectedDigest = record.digest,
                    actualDigest = engine.digest(),
                    reasonFa = "فرستندهٔ حرکتِ ${action.seq} عضو این دست نیست",
                )
            }
            val result = engine.submit(
                player,
                GameAction(type = action.type, payload = action.payload),
            )
            if (result is ApplyResult.Rejected) {
                return VerifyResult(
                    ok = false,
                    expectedDigest = record.digest,
                    actualDigest = engine.digest(),
                    reasonFa = "حرکتِ ${action.seq} در بازپخش رد شد: ${result.messageFa}",
                )
            }
        }

        val replayed = engine.record()
        val digestMatches = replayed.digest == record.digest
        val resultMatches = sameResult(replayed.result, record.result)
        return VerifyResult(
            ok = digestMatches && resultMatches,
            expectedDigest = record.digest,
            actualDigest = replayed.digest,
            reasonFa = when {
                digestMatches && resultMatches -> null
                !digestMatches -> "امضای دست با بازپخش یکی نیست (احتمال دستکاری)"
                else -> "جدول پایانی با بازپخش یکی نیست"
            },
        )
    }

    private fun sameResult(a: MatchResult?, b: MatchResult?): Boolean {
        if (a == null || b == null) return a == null && b == null
        return a.standings.map { it.rank to it.score } == b.standings.map { it.rank to it.score } &&
            a.teams?.map { it.team to it.score } == b.teams?.map { it.team to it.score }
    }
}
