package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.FixedClock
import ir.dorehami.gamesdk.GameConfig
import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.gamesdk.LoggedAction
import ir.dorehami.gamesdk.MatchRecord
import ir.dorehami.gamesdk.MatchVerifier
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.SeededRng
import ir.dorehami.gamesdk.view
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * تستِ قطعیت (سومین تستِ قرارداد).
 *
 * یک بازی کامل با ربات‌ها اجرا می‌شود، لاگ ذخیره می‌شود و دوباره بازپخش می‌گردد: باید به همان
 * امضا و همان جدول برسیم. این همان چیزی است که بعداً روی سرور جلوی تقلب را می‌گیرد.
 */
class HokmDeterminismTest {

    private fun playFullMatch(seed: Long, options: HokmOptions = HokmOptions.default()): GameEngine {
        val config = GameConfig(
            matchId = "match-$seed",
            seed = seed,
            options = options.toJson(),
            players = testPlayers(),
            clock = FixedClock(),
            rng = SeededRng(seed),
        )
        val engine = GameEngine(HokmModule, config)
        var guard = 0
        while (engine.session.phase != Phase.FINISHED && guard < MAX_ACTIONS) {
            val view = engine.publicView()
            val seat = if (view.string("stage") == "trump") view.int("hakemSeat") else view.int("turnSeat")
            val result = engine.submitBotTurn((engine.session as HokmSession).seatPlayer(seat))
            check(result is ApplyResult.Ok) { "ربات حرکتِ غیرمجاز فرستاد: $result" }
            guard++
        }
        assertTrue(engine.session.phase == Phase.FINISHED, "بازی با سیدِ $seed تمام نشد")
        return engine
    }

    @Test
    fun `یک سید همیشه همان بازی را می‌دهد`() {
        val first = playFullMatch(1_234L)
        val second = playFullMatch(1_234L)

        assertEquals(first.digest(), second.digest(), "دو اجرای یک سید باید هم‌امضا باشند")
        assertEquals(first.record().result?.teams?.map { it.score }, second.record().result?.teams?.map { it.score })
        assertEquals(first.actionLog().size, second.actionLog().size)
    }

    @Test
    fun `سیدهای مختلف بازی‌های مختلف می‌دهند`() {
        val a = playFullMatch(1L)
        val b = playFullMatch(2L)
        assertNotEquals(a.digest(), b.digest(), "سیدهای مختلف نباید هم‌امضا باشند")
    }

    @Test
    fun `بازپخشِ رکورد همان امضا و همان جدول را می‌دهد`() {
        val engine = playFullMatch(9_999L)
        val record = engine.record()

        val verify = MatchVerifier.replay(record, HokmModule)
        assertTrue(verify.ok, "بازپخش باید تأیید شود: ${verify.reasonFa}")
        assertEquals(record.digest, verify.actualDigest)
        assertEquals(record.result?.winnerTeam, engine.session.result()?.winnerTeam)
    }

    @Test
    fun `رکوردِ دستکاری‌شده رد می‌شود`() {
        val engine = playFullMatch(555L)
        val record = engine.record()

        // آخرین حرکت را از لاگ برمی‌داریم ولی امضای قدیمی را نگه می‌داریم (تقلبِ کلاسیک).
        // پیشوندِ یک لاگِ سالم همیشه قانونی است، پس بازپخش آن را می‌پذیرد و فقط امضا فرق می‌کند.
        val tampered = record.copy(actions = record.actions.dropLast(1))

        val verify = MatchVerifier.replay(tampered, HokmModule)
        assertTrue(!verify.ok, "رکوردِ دستکاری‌شده باید رد شود")
        assertEquals("امضای دست با بازپخش یکی نیست (احتمال دستکاری)", verify.reasonFa)
    }

    @Test
    fun `حرکتِ ربات هم در لاگ ثبت می‌شود`() {
        val engine = playFullMatch(77L)
        val log = engine.actionLog()
        assertTrue(log.isNotEmpty())
        assertTrue(
            log.all { it.type == HokmModule.ACTION_PLAY_CARD || it.type == HokmModule.ACTION_DECLARE_TRUMP },
            "ربات فقط از همان دو حرکتِ انسانی استفاده می‌کند",
        )
        assertEquals(log.map { it.seq }, log.indices.map { it.toLong() }, "شمارهٔ حرکت‌ها پشت‌سرهم است")
    }

    @Test
    fun `رکورد قابل تبدیل به JSON است`() {
        val record = playFullMatch(31L).record()
        val json = record.toJson()
        assertEquals(MatchRecord.SCHEMA, json.int("schema"))
        assertEquals(HokmModule.ID, json.string("gameId"))
        assertEquals(record.digest, json.string("digest"))
        assertTrue(json.string("digest").length == 64)
    }

    private companion object {
        const val MAX_ACTIONS = 5_000
    }
}
