package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.Phase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * تستِ ربات: هرگز حرکتِ غیرمجاز نمی‌فرستد، بازی را کامل می‌کند و تصمیم‌هایش قطعی است.
 *
 * این تست روی صد سیدِ مختلف اجرا می‌شود، بنابراین تعیینِ حاکم (رو کردنِ آس) و پخشِ واقعیِ کارت
 * هم همراه با آن stress-test می‌شوند.
 */
class HokmBotTest {

    @Test
    fun `ربات روی صد سید بازی را کامل می‌کند`() {
        repeat(100) { index ->
            val seed = index + 1L
            val session = randomSession(seed)
            session.autoPlay()

            assertEquals(Phase.FINISHED, session.phase, "بازی با سیدِ $seed تمام نشد")
            val view = session.publicView()
            val scores = view.intList("scores")
            assertTrue(
                scores.max() >= HokmOptions.DEFAULT_TARGET_POINTS,
                "در سیدِ $seed هیچ تیمی به امتیازِ هدف نرسید: $scores",
            )
            assertTrue(
                scores.min() < HokmOptions.DEFAULT_TARGET_POINTS,
                "در سیدِ $seed امتیازها نامعتبر است: $scores",
            )
            assertTrue(view.int("handNumber") <= 25, "در سیدِ $seed بازی بیش از حد طول کشید")
        }
    }

    @Test
    fun `ربات فقط در نوبتِ خودش حرکت می‌دهد`() {
        val session = randomSession(42L)
        session.declareAndStart(Suit.SPADES)

        val hakem = session.publicView().int("hakemSeat")
        val turn = session.turnSeat()
        assertEquals(hakem, turn, "شروع‌کننده حاکم است")

        assertNull(session.botActionFor(playerId((turn + 1) % 4)), "رباتِ خارج از نوبت نباید حرکتی بدهد")
        assertTrue(session.botActionFor(playerId(turn)) != null, "رباتِ صاحبِ نوبت باید حرکتی بدهد")
    }

    @Test
    fun `انتخابِ حکمِ ربات همیشه یکی از چهار خال است`() {
        repeat(50) { index ->
            val session = randomSession(index + 500L)
            val action = session.botActionFor(playerId(session.publicView().int("hakemSeat")))
            assertTrue(action != null)
            assertEquals(HokmModule.ACTION_DECLARE_TRUMP, action!!.type)
            val suit = Suit.valueOf(action.payload.string(HokmModule.PAYLOAD_SUIT))
            assertTrue(suit in Suit.entries)

            val result = session.apply(
                playerId(session.publicView().int("hakemSeat")),
                action,
            )
            assertTrue(result is ir.dorehami.gamesdk.ApplyResult.Ok)
        }
    }

    @Test
    fun `ربات‌ها می‌توانند یک دستِ کامل را تا انتها بازی کنند`() {
        val session = randomSession(2_024L)
        session.autoPlayCurrentHand()

        val view = session.publicView()
        assertEquals(2, view.int("handNumber"), "ربات‌ها باید دست را تمام کنند")
        val lastHand = view.obj("lastHand")
        assertEquals(1, lastHand.int("hand"))
        assertTrue(
            lastHand.intList("tricks").sum() in 7..13,
            "تعداد دست‌های گرفته‌شده باید بین ۷ تا ۱۳ باشد: ${lastHand.intList("tricks")}",
        )
        // دستِ جدید با همان قانون پخش می‌شود: یارِ حاکم تا تعیینِ حکم کارتی نمی‌گیرد
        val counts = view.intList("cardsLeft")
        val partner = (view.int("hakemSeat") + 2) % 4
        assertEquals(0, counts[partner], "یارِ حاکم تا تعیینِ حکم کارتی نمی‌گیرد")
        counts.forEachIndexed { seat, value ->
            if (seat != partner) assertEquals(5, value, "صندلیِ $seat باید ۵ کارت گرفته باشد")
        }
    }

    @Test
    fun `تصمیمِ ربات قطعی است`() {
        val first = randomSession(8_008L)
        val second = randomSession(8_008L)
        repeat(40) {
            val a = first.botActionFor(playerId(first.turnSeat()))
            val b = second.botActionFor(playerId(second.turnSeat()))
            assertEquals(a, b, "ربات برای یک وضعیتِ یکسان باید همان تصمیم را بگیرد")
            val actionA = a ?: return@repeat
            val actionB = b ?: return@repeat
            first.apply(playerId(first.turnSeat()), actionA)
            second.apply(playerId(second.turnSeat()), actionB)
        }
    }
}
