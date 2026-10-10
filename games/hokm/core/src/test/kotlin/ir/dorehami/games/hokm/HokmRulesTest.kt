package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.ApplyResult
import ir.dorehami.gamesdk.GameAction
import ir.dorehami.gamesdk.Phase
import ir.dorehami.gamesdk.PlayerId
import ir.dorehami.gamesdk.view
import ir.dorehami.protocol.ErrCodes
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * تستِ قواعدِ حکم (اولین تست از چهار تستِ قرارداد).
 *
 * هر سناریو با دست‌های دقیقاً معلوم نوشته شده تا رفتارِ موتور روی همان چیزی سنجیده شود که در
 * `docs/HOKM_RULES.md` قول داده‌ایم (منبع: pagat.com/whist/hokm.html).
 */
class HokmRulesTest {

    /** هر صندلی همهٔ ۱۳ کارتِ یک خال را دارد — ساده‌ترین حالت برای سنجیدنِ قدرتِ حکم. */
    private fun singleSuitHands(): List<List<Card>> = listOf(
        cards("AS", "KS", "QS", "JS", "TS", "9S", "8S", "7S", "6S", "5S", "4S", "3S", "2S"),
        cards("AH", "KH", "QH", "JH", "TH", "9H", "8H", "7H", "6H", "5H", "4H", "3H", "2H"),
        cards("AD", "KD", "QD", "JD", "TD", "9D", "8D", "7D", "6D", "5D", "4D", "3D", "2D"),
        cards("AC", "KC", "QC", "JC", "TC", "9C", "8C", "7C", "6C", "5C", "4C", "3C", "2C"),
    )

    @Test
    fun `دوی حکم از آسِ خالِ دیگر قوی‌تر است`() {
        val session = testSession(singleSuitHands())
        session.declareAndStart(Suit.HEARTS)

        session.playTrick("AS", "2H", "2D", "2C")

        val view = session.publicView()
        assertEquals(listOf(0, 1), view.intList("tricks"), "تیمِ دوم باید این دست را برده باشد")
        val last = view.obj("lastTrick")
        assertEquals(1, last.int("winnerSeat"), "برنده باید صاحبِ ۲ِ حکم باشد")
        assertEquals(1, last.int("team"))
        assertTrue(last.bool("ruffed"), "این دست با حکم بریده شده است")
    }

    @Test
    fun `وقتی خالِ زمین را داری باید همان را بازی کنی`() {
        val session = testSession(followSuitHands())
        session.declareAndStart(Suit.CLUBS)

        assertTrue(session.play(0, "5H") is ApplyResult.Ok)

        val illegal = session.play(1, "AS")
        assertTrue(illegal is ApplyResult.Rejected, "بازیکن دل دارد، پس پیک نباید پذیرفته شود")
        illegal as ApplyResult.Rejected
        assertEquals(ErrCodes.ILLEGAL_ACTION, illegal.code)
        assertTrue(illegal.messageFa.contains("دل"), "پیام باید خالِ واجب را بگوید: ${illegal.messageFa}")
        assertEquals(0, session.turnSeat(), "حرکتِ رد شده نباید نوبت را عوض کند")

        session.play(1, "2H")
        session.play(2, "2D")
        session.play(3, "2C")

        val last = session.publicView().obj("lastTrick")
        assertEquals(3, last.int("winnerSeat"), "حکم (۲ خاج) باید برنده باشد")
        assertEquals(listOf(0, 1), session.publicView().intList("tricks"))
    }

    @Test
    fun `وقتی خالِ زمین نداری می‌توانی هر کارتی بازی کنی`() {
        val session = testSession(followSuitHands())
        session.declareAndStart(Suit.CLUBS)

        session.play(0, "5H") // صندلیِ ۲ و ۳ هیچ دلی ندارند
        session.play(1, "2H")
        assertTrue(session.play(2, "AD") is ApplyResult.Ok)
        assertTrue(session.play(3, "2C") is ApplyResult.Ok)

        assertEquals(3, session.publicView().obj("lastTrick").int("winnerSeat"))
    }

    @Test
    fun `رد شدنِ حرکت‌های نامعتبر با کد و پیام فارسی`() {
        val beforeTrump = testSession(singleSuitHands())
        val tooEarly = beforeTrump.play(0, "AS")
        assertEquals(ErrCodes.WRONG_PHASE, (tooEarly as ApplyResult.Rejected).code)
        assertEquals("صبر کن تا حاکم حکم را تعیین کند", tooEarly.messageFa)

        val wrongHakem = beforeTrump.declare(1, Suit.HEARTS) as ApplyResult.Rejected
        assertEquals(ErrCodes.NOT_YOUR_TURN, wrongHakem.code)
        assertEquals("فقط حاکم می‌تواند حکم را تعیین کند", wrongHakem.messageFa)

        val badSuitSession = testSession(singleSuitHands())
        val badSuitResult = badSuitSession.apply(
            playerId(0),
            GameAction(
                HokmModule.ACTION_DECLARE_TRUMP,
                view { put(HokmModule.PAYLOAD_SUIT, JsonPrimitive("BANANA")) },
            ),
        ) as ApplyResult.Rejected
        assertEquals(ErrCodes.BAD_PAYLOAD, badSuitResult.code)
        assertTrue(badSuitResult.messageFa.contains("BANANA"))

        val session = testSession(singleSuitHands())
        session.declareAndStart(Suit.HEARTS)
        assertEquals(ErrCodes.WRONG_PHASE, (session.declare(0, Suit.SPADES) as ApplyResult.Rejected).code)
        assertEquals(ErrCodes.NOT_YOUR_TURN, (session.play(1, "AH") as ApplyResult.Rejected).code)
        assertEquals(ErrCodes.BAD_PAYLOAD, (session.play(0, "AD") as ApplyResult.Rejected).code)
        assertEquals(
            ErrCodes.UNKNOWN_ACTION,
            (session.apply(playerId(0), GameAction("dance")) as ApplyResult.Rejected).code,
        )
        assertEquals(
            ErrCodes.NOT_IN_MATCH,
            (session.apply(PlayerId("غریبه"), GameAction("dance")) as ApplyResult.Rejected).code,
        )
    }

    @Test
    fun `نسخه فقط با حرکتِ موفق جلو می‌رود`() {
        val session = testSession(singleSuitHands())
        val start = session.version
        assertEquals(0L, start, "پخشِ اولیه نباید نسخه را جلو ببرد")

        session.play(0, "AS") // رد می‌شود (قبل از تعیینِ حکم)
        assertEquals(start, session.version)

        session.declareAndStart(Suit.HEARTS)
        assertEquals(start + 1, session.version)

        session.playTrick("AS", "2H", "2D", "2C")
        assertEquals(start + 5, session.version, "هر کارت یک نسخه جلو می‌برد")
    }

    @Test
    fun `پخشِ دورِ اول ۵ کارت است و یارِ حاکم کارتی نمی‌گیرد`() {
        val session = testSession(singleSuitHands())
        val view = session.publicView()

        assertEquals("trump", view.string("stage"))
        assertEquals(Phase.SETUP, session.phase)
        assertEquals(0, view.int("hakemSeat"))
        assertEquals(3, view.int("dealerSeat"), "ورق‌دهنده آخرین نفرِ ترتیبِ پخش است")
        assertEquals(
            listOf(5, 5, 0, 5),
            view.intList("cardsLeft"),
            "تا تعیینِ حکم، یارِ حاکم کارت نمی‌گیرد",
        )
        assertEquals(5, session.privateView(playerId(0)).stringList("hand").size)
        assertEquals(5, session.privateView(playerId(1)).stringList("hand").size)
        assertEquals(0, session.privateView(playerId(2)).stringList("hand").size)

        session.declareAndStart(Suit.HEARTS)
        assertEquals(listOf(13, 13, 13, 13), session.publicView().intList("cardsLeft"))
        assertEquals(13, session.privateView(playerId(2)).stringList("hand").size)
    }

    @Test
    fun `حاکم شروع می‌کند و برندهٔ هر دست شروع‌کنندهٔ دستِ بعد است`() {
        val session = testSession(singleSuitHands())
        session.declareAndStart(Suit.HEARTS)
        assertEquals(0, session.turnSeat(), "حاکم شروع‌کنندهٔ اولین دست است")

        session.playTrick("AS", "2H", "2D", "2C")
        assertEquals(1, session.turnSeat(), "برندهٔ دست باید شروع‌کنندهٔ دستِ بعد باشد")
    }

    @Test
    fun `قانونِ حکم لازم فقط در حالتِ overtrump اعمال می‌شود`() {
        val lazim = HokmOptions.default().copy(hokmLazim = HokmLazimMode.OVERTRUMP)
        val session = testSession(lazimHands(), options = lazim)
        session.declareAndStart(Suit.SPADES)

        session.play(0, "5H")
        session.play(1, "3S") // بریدن با حکم

        val legal = session.privateView(playerId(2)).stringList("legalCards").sorted()
        assertEquals(
            listOf("4S", "5S", "6S", "7S", "8S", "9S", "AS", "JS", "KS", "QS", "TS"),
            legal,
            "وقتی حکم لازم است، فقط حکم‌های بالاتر مجازند",
        )
        assertEquals(ErrCodes.ILLEGAL_ACTION, (session.play(2, "2S") as ApplyResult.Rejected).code)
        assertEquals(ErrCodes.ILLEGAL_ACTION, (session.play(2, "2C") as ApplyResult.Rejected).code)

        // در حالتِ استاندارد همان کارت‌ها مجازند
        val standard = testSession(lazimHands())
        standard.declareAndStart(Suit.SPADES)
        standard.play(0, "5H")
        standard.play(1, "3S")
        assertEquals(13, standard.privateView(playerId(2)).stringList("legalCards").size)
        assertTrue(standard.play(2, "2S") is ApplyResult.Ok)
    }

    @Test
    fun `ترتیبِ صندلی‌ها و تیم‌ها در نمای عمومی درست است`() {
        val session = testSession(singleSuitHands())
        val seats = session.publicView().objList("seats")
        assertEquals(4, seats.size)
        assertEquals(listOf(0, 1, 0, 1), seats.map { it.int("team") }, "یارها روبه‌روی هم‌اند: ۰ با ۲ و ۱ با ۳")
        assertEquals(listOf(0, 1, 2, 3), seats.map { it.int("seat") })
        assertEquals(listOf("p0", "p1", "p2", "p3"), seats.map { it.string("id") })
    }

    // -------------------------------------------------------------------------
    // دست‌های آماده برای سناریوها
    // -------------------------------------------------------------------------

    /**
     * سناریوی «جواب دادنِ خال»: صندلیِ ۱ یک دل دارد و ۱۲ پیک، پس وقتی دل روی زمین است
     * **باید** همان یک دل را بازی کند.
     */
    private fun followSuitHands(): List<List<Card>> = listOf(
        cards("AH", "KH", "QH", "JH", "TH", "9H", "8H", "7H", "6H", "5H", "4H", "3H", "2S"),
        cards("AS", "KS", "QS", "JS", "TS", "9S", "8S", "7S", "6S", "5S", "4S", "3S", "2H"),
        cards("AD", "KD", "QD", "JD", "TD", "9D", "8D", "7D", "6D", "5D", "4D", "3D", "2D"),
        cards("AC", "KC", "QC", "JC", "TC", "9C", "8C", "7C", "6C", "5C", "4C", "3C", "2C"),
    )

    /** سناریوی «حکم لازم»: صندلیِ ۲ می‌تواند حکمِ روی زمین را با حکمِ بالاتر ببرد. */
    private fun lazimHands(): List<List<Card>> = listOf(
        cards("AH", "KH", "QH", "JH", "TH", "9H", "8H", "7H", "6H", "5H", "4H", "3H", "2H"),
        cards("AC", "KC", "QC", "JC", "TC", "9C", "8C", "7C", "6C", "5C", "4C", "3C", "3S"),
        cards("AS", "KS", "QS", "JS", "TS", "9S", "8S", "7S", "6S", "5S", "4S", "2S", "2C"),
        cards("AD", "KD", "QD", "JD", "TD", "9D", "8D", "7D", "6D", "5D", "4D", "3D", "2D"),
    )
}
