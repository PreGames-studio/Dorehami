package ir.dorehami.games.hokm

import ir.dorehami.gamesdk.Phase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * تستِ امتیاز و پایان (چهارمین تستِ قرارداد): کوت، حاکم‌کُتی، چرخشِ حاکم، امتیازِ هدف و
 * حالت‌های مرزی.
 */
class HokmScoringTest {

    /** دست‌های سناریوهای امتیاز: تیمِ ۰ (صندلی‌های ۰ و ۲) می‌تواند هفت دست بگیرد. */
    private fun scoringHands(): List<List<Card>> = listOf(
        cards("AS", "KS", "QS", "JS", "10S", "9S", "8S", "AC", "KC", "QC", "JC", "10C", "9C"),
        cards("8C", "7C", "6C", "5C", "4C", "3C", "2C", "7D", "6D", "5D", "4D", "3D", "2D"),
        cards("AD", "KD", "QD", "JD", "10D", "9D", "8D", "7S", "6S", "5S", "4S", "3S", "2S"),
        cards("AH", "KH", "QH", "JH", "10H", "9H", "8H", "7H", "6H", "5H", "4H", "3H", "2H"),
    )

    /** دست‌هایی که با آن‌ها تیمِ ۱ (صندلی‌های ۱ و ۳) حاکم را کوت می‌کند. */
    private fun hakemKotiHands(): List<List<Card>> = listOf(
        cards("AH", "KH", "QH", "JH", "10H", "9H", "8H", "7H", "6H", "5H", "4H", "3H", "2H"),
        cards("AS", "KS", "QS", "JS", "10S", "9S", "8S", "AC", "KC", "QC", "JC", "10C", "9C"),
        cards("8C", "7C", "6C", "5C", "4C", "3C", "2C", "AD", "KD", "QD", "JD", "10D", "9D"),
        cards("7S", "6S", "5S", "4S", "3S", "2S", "8D", "7D", "6D", "5D", "4D", "3D", "2D"),
    )

    @Test
    fun `هفت دست گرفتن، دست را تمام می‌کند`() {
        val session = testSession(scoringHands())
        session.declareAndStart(Suit.SPADES)

        repeat(6) { index ->
            session.playTrick(
                listOf("AS", "KS", "QS", "JS", "10S", "9S")[index],
                listOf("2C", "3C", "4C", "5C", "6C", "7C")[index],
                listOf("2S", "3S", "4S", "5S", "6S", "7S")[index],
                listOf("2H", "3H", "4H", "5H", "6H", "7H")[index],
            )
        }
        assertEquals(listOf(6, 0), session.publicView().intList("tricks"))

        session.playTrick("8S", "8C", "AD", "8H")
        val view = session.publicView()
        assertEquals(listOf(7, 0), view.intList("tricks"), "تیمِ اول باید هفت دست گرفته باشد")
        assertEquals(2, view.int("handNumber"), "با هفت دست، دستِ جدید شروع می‌شود")
        assertEquals("trump", view.string("stage"))
    }

    @Test
    fun `کوتِ تیمِ حاکم ۲ امتیاز دارد و حاکم عوض نمی‌شود`() {
        val session = testSession(scoringHands())
        session.declareAndStart(Suit.SPADES)

        val spades = listOf("AS", "KS", "QS", "JS", "10S", "9S", "8S")
        val clubs = listOf("2C", "3C", "4C", "5C", "6C", "7C", "8C")
        val follows = listOf("2S", "3S", "4S", "5S", "6S", "7S", "AD")
        val hearts = listOf("2H", "3H", "4H", "5H", "6H", "7H", "8H")
        repeat(7) { index -> session.playTrick(spades[index], clubs[index], follows[index], hearts[index]) }

        val view = session.publicView()
        val lastHand = view.obj("lastHand")
        assertEquals(listOf(7, 0), lastHand.intList("tricks"))
        assertEquals(true, lastHand.bool("kot"), "بازنده هیچ دستی نگرفته، پس کوت است")
        assertEquals(false, lastHand.bool("hakemKoti"))
        assertEquals(2, lastHand.int("points"), "کوتِ تیمِ حاکم ۲ امتیاز دارد")
        assertEquals(listOf(2, 0), view.intList("scores"))
        assertEquals(0, view.int("hakemSeat"), "تیمِ حاکم برده، پس حاکم سر جایش است")
        assertEquals(listOf(5, 5, 0, 5), view.intList("cardsLeft"), "دستِ بعد با حاکمِ قبلی پخش می‌شود")
    }

    @Test
    fun `حاکم‌کُتی ۳ امتیاز دارد و حاکم به نفرِ بعدی می‌رود`() {
        val session = testSession(hakemKotiHands())
        session.declareAndStart(Suit.SPADES)

        // دستِ اول: صندلیِ ۱ با آسِ پیک می‌بُرد
        session.playTrick("2H", "AS", "2C", "2D")
        assertEquals(listOf(0, 1), session.publicView().intList("tricks"))

        val spades = listOf("KS", "QS", "JS", "10S", "9S", "8S")
        val clubs = listOf("3C", "4C", "5C", "6C", "7C", "8C")
        val follows = listOf("2S", "3S", "4S", "5S", "6S", "7S")
        val hearts = listOf("3H", "4H", "5H", "6H", "7H", "8H")
        repeat(6) { index -> session.playTrick(spades[index], clubs[index], follows[index], hearts[index]) }

        val view = session.publicView()
        val lastHand = view.obj("lastHand")
        assertEquals(listOf(0, 7), lastHand.intList("tricks"))
        assertEquals(true, lastHand.bool("kot"))
        assertEquals(true, lastHand.bool("hakemKoti"), "حریفِ حاکم کوت کرده است")
        assertEquals(3, lastHand.int("points"), "حاکم‌کُتی ۳ امتیاز دارد")
        assertEquals(listOf(0, 3), view.intList("scores"))
        assertEquals(1, view.int("hakemSeat"), "وقتی تیمِ حاکم می‌بازد، نفرِ بعدی حاکم می‌شود")
        assertEquals(0, view.int("dealerSeat"), "حاکمِ قبلی ورق‌دهنده می‌شود")
        assertEquals(
            listOf(5, 5, 5, 0),
            view.intList("cardsLeft"),
            "در دستِ جدید، یارِ حاکمِ جدید (صندلیِ ۳) کارتی نمی‌گیرد",
        )
    }

    @Test
    fun `بردِ معمولی ۱ امتیاز دارد`() {
        val session = testSession(normalWinHands())
        session.declareAndStart(Suit.SPADES)

        // دستِ اول را تیمِ حریف با آسِ حکم می‌برد
        session.playTrick("KS", "AS", "2S", "3C")
        assertEquals(listOf(0, 1), session.publicView().intList("tricks"))

        // حاکم با حکم پس می‌گیرد و بعد هفت دستِ تیمش کامل می‌شود
        session.playTrick("AH", "3S", "2H", "7S")
        session.playTrick("QS", "KH", "4S", "4C")
        session.playTrick("JS", "QH", "5S", "5C")
        session.playTrick("10S", "JH", "6S", "6C")
        session.playTrick("9S", "10H", "KD", "7C")
        session.playTrick("8S", "9H", "QD", "8C")
        session.playTrick("AC", "8H", "AD", "2C")

        val view = session.publicView()
        val lastHand = view.obj("lastHand")
        assertEquals(listOf(7, 1), lastHand.intList("tricks"))
        assertEquals(false, lastHand.bool("kot"), "حریف یک دست گرفته، پس کوت نیست")
        assertEquals(1, lastHand.int("points"))
        assertEquals(listOf(1, 0), view.intList("scores"))
        assertEquals(0, view.int("hakemSeat"), "تیمِ حاکم برده، پس حاکم سر جایش است")
    }

    /**
     * دست‌های سناریوی «بردِ معمولی»: تیمِ حریف فقط یک دست (با آسِ حکم) می‌گیرد و تیمِ حاکم هفت
     * دست — یعنی کوت نیست و فقط ۱ امتیاز دارد.
     */
    private fun normalWinHands(): List<List<Card>> = listOf(
        cards("KS", "QS", "JS", "10S", "9S", "8S", "7S", "AC", "KC", "QC", "JC", "10C", "9C"),
        cards("AS", "AH", "KH", "QH", "JH", "10H", "9H", "8H", "7H", "6H", "5H", "4H", "3H"),
        cards("6S", "5S", "4S", "3S", "2S", "AD", "KD", "QD", "JD", "10D", "9D", "8D", "7D"),
        cards("8C", "7C", "6C", "5C", "4C", "3C", "2C", "6D", "5D", "4D", "3D", "2D", "2H"),
    )

    @Test
    fun `بازی با رسیدن به امتیازِ هدف تمام می‌شود`() {
        val options = HokmOptions.default().copy(targetPoints = 2)
        val session = testSession(scoringHands(), options = options)
        session.declareAndStart(Suit.SPADES)

        val spades = listOf("AS", "KS", "QS", "JS", "10S", "9S", "8S")
        val clubs = listOf("2C", "3C", "4C", "5C", "6C", "7C", "8C")
        val follows = listOf("2S", "3S", "4S", "5S", "6S", "7S", "AD")
        val hearts = listOf("2H", "3H", "4H", "5H", "6H", "7H", "8H")
        repeat(7) { index -> session.playTrick(spades[index], clubs[index], follows[index], hearts[index]) }

        assertEquals(Phase.FINISHED, session.phase)
        val result = session.result()
        assertNotNull(result)
        assertEquals(0, result!!.winnerTeam)
        assertEquals(listOf(2, 0), session.publicView().intList("scores"))
        assertEquals(2, result.teams!!.first().score)
        assertEquals(2, result.standings.count { it.rank == 1 }, "هر دو عضوِ تیمِ برنده رتبهٔ یک دارند")
    }

    @Test
    fun `در حالت thirteen همهٔ دست‌ها بازی می‌شود`() {
        val options = HokmOptions.default().copy(handEnd = HandEndMode.THIRTEEN, targetPoints = 25)
        val session = testSession(scoringHands(), options = options)
        session.declareAndStart(Suit.SPADES)

        session.autoPlayCurrentHand()

        val view = session.publicView()
        assertEquals(2, view.int("handNumber"))
        val lastHand = view.obj("lastHand")
        assertEquals(13, lastHand.intList("tricks").sum(), "در این حالت همهٔ ۱۳ دست بازی می‌شود")
        assertTrue(
            lastHand.intList("tricks").max() > lastHand.intList("tricks").min(),
            "در ۱۳ دست مساوی ممکن نیست",
        )
    }

    @Test
    fun `امتیازِ کوت قابل تنظیم است`() {
        val options = HokmOptions.default().copy(kotHakemPoints = 5, kotOpponentPoints = 9)
        val session = testSession(scoringHands(), options = options)
        session.declareAndStart(Suit.SPADES)

        val spades = listOf("AS", "KS", "QS", "JS", "10S", "9S", "8S")
        val clubs = listOf("2C", "3C", "4C", "5C", "6C", "7C", "8C")
        val follows = listOf("2S", "3S", "4S", "5S", "6S", "7S", "AD")
        val hearts = listOf("2H", "3H", "4H", "5H", "6H", "7H", "8H")
        repeat(7) { index -> session.playTrick(spades[index], clubs[index], follows[index], hearts[index]) }

        assertEquals(listOf(5, 0), session.publicView().intList("scores"))
    }
}
