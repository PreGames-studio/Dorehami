package ir.dorehami.games.hokm

/**
 * سیاستِ رباتِ حکم (سؤال ۶ قرارداد).
 *
 * دو ویژگی این ربات حیاتی است:
 * 1. **قطعیت:** هیچ تصادفی مصرف نمی‌کند؛ برای یک وضعیتِ یکسان همیشه همان کارت را برمی‌گرداند،
 *    بنابراین یک دستِ رباتی هم قابل بازپخش و راستی‌آزمایی است.
 * 2. **قانون‌مندی:** فقط از فهرستِ کارت‌های مجاز انتخاب می‌کند، پس هیچ‌وقت حرکتِ غیرقانونی
 *    نمی‌فرستد (تستِ `HokmBotTest` این را روی هزاران دست چک می‌کند).
 *
 * سطحِ بازی «هم‌بازیِ قابل‌قبول» است: خال را درست جواب می‌دهد، وقتی یارش برنده است بالا نمی‌زند،
 * وقتی نمی‌تواند ببرد از کوتاه‌ترین خال دور می‌ریزد تا خالی کند، و با حکمِ زیاد حکم‌کِشی می‌کند.
 */

/** آنچه ربات برای تصمیم‌گیری لازم دارد (یک نگاهِ کنترل‌شده به وضعیتِ بازی). */
internal data class BotView(
    /** دستِ ربات. */
    val hand: List<Card>,
    /** کارت‌هایی که در همین لحظه مجاز است بازی کند. */
    val legal: List<Card>,
    /** کارت‌های روی زمین در دستِ جاری (به ترتیبِ بازی). */
    val trick: List<TrickPlay>,
    /** خالِ حکم؛ اگر هنوز تعیین نشده `null`. */
    val trump: Suit?,
    /** تیمِ ربات (۰ یا ۱). */
    val myTeam: Int,
    /** تعداد دست‌های گرفته‌شده تا این لحظه. */
    val tricksWon: IntArray,
    /** چند کارت روی زمین است (۰ یعنی ربات شروع‌کننده است). */
    val position: Int,
)

internal object HokmBot {

    /** انتخابِ کارت برای بازی در دستِ جاری. */
    fun choose(view: BotView): Card {
        if (view.legal.isEmpty()) error("ربات هیچ کارتِ مجازی ندارد")
        if (view.legal.size == 1) return view.legal.first()
        return if (view.trick.isEmpty()) lead(view) else follow(view)
    }

    /** انتخابِ خالِ حکم بر اساسِ ۵ کارتِ اولِ حاکم. */
    fun chooseTrump(hand: List<Card>): Suit {
        // امتیازِ هر خال: طولِ خال + ارزشِ کارت‌های سر (آس ۴، شاه ۳، بی‌بی ۲، سرباز ۱).
        val scores = Suit.entries.associateWith { suit ->
            val cards = hand.filter { it.suit == suit }
            cards.size * 2 + cards.sumOf { honorValue(it) }
        }
        val best = scores.values.maxOrNull() ?: 0
        // ترتیبِ خال‌ها ثابت است، پس در صورت تساوی نتیجه قطعی می‌ماند.
        return Suit.entries.first { scores[it] == best }
    }

    // -------------------------------------------------------------------------
    // شروع‌کننده
    // -------------------------------------------------------------------------

    private fun lead(view: BotView): Card {
        val trump = view.trump ?: return view.legal.minBy { it.rank.order }
        val hand = view.hand
        val legal = view.legal
        val trumps = legal.filter { it.suit == trump }

        // ۱) حکمِ زیاد ⇒ حکم‌کِشی: با بالاترین حکم شروع کن تا حکمِ حریف را بیرون بکشی.
        if (hand.count { it.suit == trump } >= TRUMP_DRAW_COUNT && trumps.isNotEmpty()) {
            return trumps.maxBy { it.rank.order }
        }

        // ۲) آسِ خالِ غیرِ حکم: معمولاً برنده است (مگر اینکه کسی حکم ببرد).
        val aces = legal.filter { it.rank == Rank.ACE && it.suit != trump }
        if (aces.isNotEmpty()) return aces.firstInDeckOrder()

        // ۳) شاهی که پشتش خالِ بلند است.
        val kings = legal.filter { card ->
            card.rank == Rank.KING &&
                card.suit != trump &&
                hand.count { it.suit == card.suit } >= LONG_SUIT_FOR_KING_LEAD
        }
        if (kings.isNotEmpty()) return kings.firstInDeckOrder()

        // ۴) راه انداختنِ یک خالِ بلندِ غیرِ حکم با پایین‌ترین کارتش.
        val nonTrumps = legal.filter { it.suit != trump }
        if (nonTrumps.isNotEmpty()) {
            val longest = nonTrumps
                .groupBy { it.suit }
                .entries
                .sortedWith(compareByDescending<Map.Entry<Suit, List<Card>>> { it.value.size }
                    .thenBy { it.key.ordinal })
                .first()
            return longest.value.minBy { it.rank.order }
        }

        // ۵) فقط حکم مانده است.
        return trumps.maxBy { it.rank.order }
    }

    // -------------------------------------------------------------------------
    // پاسخ به خالِ زمین
    // -------------------------------------------------------------------------

    private fun follow(view: BotView): Card {
        val trump = view.trump ?: return view.legal.minBy { it.rank.order }
        val led = view.trick.first().card.suit
        val best = view.trick.maxBy { it.card.strength(trump, led) }
        val partnerWinning = (best.seat % HokmSession.TEAMS) == view.myTeam
        val winners = view.legal.filter { it.beats(best.card, trump, led) }

        // یارمان برنده است: بالا نزن و کارتِ ارزان بده (حکم را هم خرج نکن).
        if (partnerWinning) return cheapest(view.legal, trump)

        if (winners.isNotEmpty()) {
            val cheapestWinner = winners.minBy { it.strength(trump, led) }
            // نفرِ سوم و چهارم باید ببرند (نفرِ چهارم که مشخص است؛ نفرِ سوم حریف را مجبور می‌کند).
            if (view.position >= FORCE_POSITION) return cheapestWinner
            // نفرِ دوم فقط وقتی می‌برد که ارزان تمام شود (آس/شاهِ حکم را خرج نمی‌کند).
            val expensive = cheapestWinner.suit == trump &&
                cheapestWinner.rank.order >= Rank.KING.order
            return if (expensive) cheapest(view.legal, trump) else cheapestWinner
        }

        // نمی‌توانیم ببریم: دور بریز و خال خالی کن.
        return discard(view, trump)
    }

    private fun discard(view: BotView, trump: Suit): Card {
        val nonTrumps = view.legal.filter { it.suit != trump }
        if (nonTrumps.isEmpty()) return view.legal.minBy { it.rank.order }
        val lengths = view.hand.filter { it.suit != trump }
            .groupingBy { it.suit }
            .eachCount()
        // از کوتاه‌ترین خال و پایین‌ترین کارتش دور می‌ریزیم تا زودتر خالی کنیم.
        return nonTrumps.minWith(
            compareBy<Card> { lengths[it.suit] ?: 0 }
                .thenBy { it.rank.order }
                .thenBy { it.suit.ordinal },
        )
    }

    private fun cheapest(legal: List<Card>, trump: Suit): Card {
        val nonTrumps = legal.filter { it.suit != trump }
        val pool = nonTrumps.ifEmpty { legal }
        return pool.minWith(compareBy<Card> { it.rank.order }.thenBy { it.suit.ordinal })
    }

    private fun honorValue(card: Card): Int = when (card.rank) {
        Rank.ACE -> 4
        Rank.KING -> 3
        Rank.QUEEN -> 2
        Rank.JACK -> 1
        else -> 0
    }

    /** ترتیبِ ثابت (مثل ترتیبِ دسته) تا انتخاب در حالتِ تساوی قطعی بماند. */
    private fun List<Card>.firstInDeckOrder(): Card =
        minWith(compareBy<Card> { it.suit.ordinal }.thenBy { it.rank.order })

    private const val TRUMP_DRAW_COUNT = 5
    private const val LONG_SUIT_FOR_KING_LEAD = 3
    private const val FORCE_POSITION = 2
}
