package ir.dorehami.games.hokm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * تستِ نشتِ اطلاعات (دومین تستِ قرارداد).
 *
 * هدف: ثابت کنیم دستِ هیچ بازیکنی از مسیرِ عمومی بیرون نمی‌رود — نه به صورتِ یک کلیدِ خالی،
 * **اصلاً کلیدی وجود ندارد**. این برای حکم حیاتی است چون در بلوتوث و آنلاین، وضعیتِ عمومی به همه
 * فرستاده می‌شود.
 */
class HokmPrivacyTest {

    private fun session(): HokmSession = testSession(
        listOf(
            cards("AS", "KS", "QS", "JS", "TS", "9S", "8S", "7S", "6S", "5S", "4S", "3S", "2S"),
            cards("AH", "KH", "QH", "JH", "TH", "9H", "8H", "7H", "6H", "5H", "4H", "3H", "2H"),
            cards("AD", "KD", "QD", "JD", "TD", "9D", "8D", "7D", "6D", "5D", "4D", "3D", "2D"),
            cards("AC", "KC", "QC", "JC", "TC", "9C", "8C", "7C", "6C", "5C", "4C", "3C", "2C"),
        ),
    )

    @Test
    fun `نمای عمومی هیچ کلیدِ خصوصی ندارد`() {
        val publicView = session().publicView()
        listOf("hand", "hands", "legalCards", "you", "youSeat", "isYourTurn", "isHakem")
            .forEach { key ->
                assertFalse(publicView.containsKey(key), "کلیدِ «$key» نباید در نمای عمومی باشد")
            }
    }

    @Test
    fun `کارت‌های دستِ هیچ‌کس در نمای عمومی نیست`() {
        val session = session()
        session.declareAndStart(Suit.HEARTS)
        val publicText = session.publicView().toString()

        (0..3).forEach { seat ->
            val hand = session.privateView(session.seatPlayer(seat)).stringList("hand")
            assertTrue(hand.isNotEmpty(), "دستِ صندلیِ $seat باید در نمای خصوصی باشد")
            hand.forEach { card ->
                // کارت‌های بازی‌شده مجازند در نمای عمومی باشند؛ بقیه نه.
                assertFalse(
                    publicText.contains("\\\"$card\\\""),
                    "کارتِ $card از دستِ صندلیِ $seat در نمای عمومی پیدا شد",
                )
            }
        }
    }

    @Test
    fun `هر بازیکن فقط دستِ خودش را می‌بیند`() {
        val session = session()
        session.declareAndStart(Suit.HEARTS)

        val mine = session.privateView(session.seatPlayer(0)).stringList("hand").toSet()
        val others = session.privateView(session.seatPlayer(1)).stringList("hand").toSet()

        assertEquals(13, mine.size)
        assertEquals(13, others.size)
        assertTrue(mine.intersect(others).isEmpty(), "هیچ کارتی نباید در دو دست باشد")
        assertEquals(
            setOf("AS", "KS", "QS", "JS", "TS"),
            mine.intersect(setOf("AS", "KS", "QS", "JS", "TS")),
        )
        assertFalse(mine.contains("AH"), "کارتِ حریف نباید در دستِ من باشد")
    }

    @Test
    fun `نمای خصوصی شامل کارت‌های مجازِ همان بازیکن است`() {
        val session = session()
        session.declareAndStart(Suit.HEARTS)

        val mine = session.privateView(session.seatPlayer(0))
        assertEquals(
            mine.stringList("hand").size,
            mine.stringList("legalCards").size,
            "در شروعِ دست همهٔ کارت‌ها مجازند",
        )
        assertEquals(listOf(HokmModule.ACTION_PLAY_CARD), mine.stringList("legalActions"))
        assertTrue(mine.bool("isYourTurn"))
        assertTrue(mine.bool("isHakem"))

        val next = session.privateView(session.seatPlayer(1))
        assertEquals(emptyList<String>(), next.stringList("legalCards"), "وقتی نوبتِ کسی نیست کارتِ مجاز ندارد")
        assertEquals(emptyList<String>(), next.stringList("legalActions"))
        assertFalse(next.bool("isYourTurn"))
    }

    @Test
    fun `یارِ حاکم تا قبل از تعیینِ حکم دستی ندارد`() {
        val session = session()
        assertEquals(0, session.privateView(session.seatPlayer(2)).stringList("hand").size)
        assertEquals(5, session.privateView(session.seatPlayer(0)).stringList("hand").size)

        session.declareAndStart(Suit.HEARTS)
        assertEquals(13, session.privateView(session.seatPlayer(2)).stringList("hand").size)
    }

    @Test
    fun `وضعیتِ کامل فقط در snapshot است`() {
        val session = session()
        session.declareAndStart(Suit.HEARTS)

        val snapshot = session.snapshot()
        assertTrue(snapshot.containsKey("hands"), "snapshot باید دستِ همه را داشته باشد (برای ذخیره و ادامه)")
        val hands = snapshot.obj("hands")
        (0..3).forEach { seat ->
            assertEquals(13, (hands["p$seat"] as kotlinx.serialization.json.JsonArray).size)
        }
        assertFalse(session.publicView().containsKey("hands"))
    }

    @Test
    fun `رویدادهای خصوصی فقط برای صاحبشان فرستاده می‌شوند`() {
        val session = session()
        val result = session.declare(0, Suit.SPADES)
        val emissions = (result as ir.dorehami.gamesdk.ApplyResult.Ok).emissions

        val privateTargets = emissions
            .filterIsInstance<ir.dorehami.gamesdk.Emission.Private>()
            .map { it.to.value }
            .toSet()
        assertEquals(setOf("p0", "p1", "p2", "p3"), privateTargets, "هر بازیکن باید دستِ خودش را بگیرد")

        emissions.filterIsInstance<ir.dorehami.gamesdk.Emission.Private>().forEach { emission ->
            when (emission.to.value) {
                "p0" -> assertEquals(
                    listOf("AS", "KS", "QS", "JS", "TS", "9S", "8S", "7S", "6S", "5S", "4S", "3S", "2S"),
                    emission.view.stringList("hand"),
                )
                "p1" -> assertEquals(
                    listOf("AH", "KH", "QH", "JH", "TH", "9H", "8H", "7H", "6H", "5H", "4H", "3H", "2H"),
                    emission.view.stringList("hand"),
                )
            }
        }
    }
}
