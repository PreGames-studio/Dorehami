package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.HandPhase
import ir.dorehami.games.hokm.Play
import ir.dorehami.games.hokm.Suit
import ir.dorehami.uikit.toFa

/**
 * HK-013 — accessibility labels for the Hokm table.
 *
 * These are **pure** functions of the typed table state, with no Compose and no Android (the
 * 48dp touch-target constant lives next to the Compose code that applies it), so the
 * exact words a screen reader speaks are pinned by JVM tests (`HokmA11yTest`) and reviewed like
 * any other product copy. The Compose layer only attaches them (`Modifier.semantics`), which keeps
 * the risk of breaking rendering away from the wording itself.
 *
 * Rules followed here (games/hokm/AGENTS.md 4.4/4.7 and ROADMAP §10.2 «دسترسی»):
 * - every number is Persian-digit formatted (`toFa`);
 * - a card is spoken as rank then suit («آس پیک»), never as its wire code (`AS`);
 * - the label says what the player needs in order of importance: whose turn, what phase, what is
 *   on the table, how many cards each seat holds;
 * - nothing is invented: a seat whose card count is unknown is not given a number.
 */
object HokmA11y {

    /** «آس پیک» — the name a Persian player uses, not the wire id. */
    fun cardLabel(card: Card): String = "${card.rank.labelFa} ${card.suit.titleFa}"

    /** «پیک» */
    fun suitLabel(suit: Suit): String = suit.titleFa

    /**
     * The whole hand as one spoken line: «دست شما: ۱۳ کارت — آس پیک، ۲ دل، …». A screen reader
     * user cannot scan a fan visually, so the fan itself gets one summary and each card its own
     * focusable label (see [cardLabel]).
     */
    fun handLabel(cards: List<Card>): String =
        if (cards.isEmpty()) "دست خالی"
        else "دست شما: ${cards.size.toFa()} کارت — " + cards.joinToString("، ") { cardLabel(it) }

    /**
     * One seat line: «صندلی ۲ — رضا — ۹ کارت — ۳ ترفند» plus the markers that matter most:
     * whose turn it is, who the hakem is, and that this seat is you.
     */
    fun seatLabel(
        seat: Int,
        name: String,
        cardCount: Int,
        tricksWon: Int,
        isTurn: Boolean,
        isHakem: Boolean,
        isMe: Boolean,
    ): String = buildString {
        if (isMe) append("شما — ")
        append("صندلی ${seat.toFa()}")
        if (name.isNotBlank()) append(" — $name")
        append(" — ${cardCount.toFa()} کارت")
        append(" — ${tricksWon.toFa()} ترفند")
        if (isHakem) append(" — حاکم")
        if (isTurn) append(" — نوبت اوست")
    }

    /** «حکم: پیک» or «حکم انتخاب نشده». */
    fun hokmLabel(suit: Suit?): String =
        if (suit == null) "حکم انتخاب نشده" else "حکم: ${suit.titleFa}"

    /** The phase, in the words the table already uses. */
    fun phaseLabel(phase: HandPhase): String = when (phase) {
        HandPhase.CHOOSING_HOKM -> "انتخاب حکم"
        HandPhase.DISCARDING -> "سوزاندن کارت"
        HandPhase.DRAWING -> "کشیدن از دسته"
        HandPhase.PLAYING -> "بازی ترفند"
    }

    /** «ترفند ۳ از دست ۲» — where the player is inside the match. */
    fun progressLabel(handNumber: Int, trickNumber: Int): String =
        "دست ${handNumber.toFa()}، ترفند ${trickNumber.toFa()}"

    /** «روی میز: آس پیک از صندلی ۱، ۵ دل از صندلی ۳». */
    fun trickLabel(plays: List<Play>): String =
        if (plays.isEmpty()) "روی میز خالی است"
        else "روی میز: " + plays.joinToString("، ") { "${cardLabel(it.card)} از صندلی ${it.seat.toFa()}" }

    /** «امتیاز: تیم شما ۳، تیم حریف ۱». */
    fun scoreLabel(scores: List<Int>, myTeam: Int, targetPoints: Int): String {
        val mine = scores.getOrElse(myTeam) { 0 }
        val theirs = scores.getOrElse(if (myTeam == 0) 1 else 0) { 0 }
        return "امتیاز: تیم شما ${mine.toFa()}، تیم حریف ${theirs.toFa()}، هدف ${targetPoints.toFa()}"
    }

    /** The single line that says what to do now — the most important thing to hear first. */
    fun turnLabel(state: HokmUiState): String = when {
        state.finished -> "بازی تمام شد"
        state.error != null -> "خطا: ${state.error}"
        state.iChooseHokm -> "نوبت شماست: حکم را انتخاب کنید"
        state.iDiscard -> "نوبت شماست: ${state.discardCount.toFa()} کارت بسوزانید"
        state.iDraw -> "نوبت شماست: کارت دسته را نگه دارید یا رد کنید"
        state.isMyTurn -> "نوبت شماست: یک کارت بازی کنید"
        state.turn in state.seats.indices -> "نوبت ${state.seats[state.turn].name} است"
        else -> "در انتظار حرکت حریف"
    }

    /**
     * One card in the hand fan, as spoken when it receives focus. Legal cards are announced as
     * playable so a screen reader user is not left guessing which taps will be refused.
     */
    fun handCardLabel(card: Card, legal: Boolean, selected: Boolean): String = buildString {
        append(cardLabel(card))
        if (selected) append(" — انتخاب‌شده")
        append(if (legal) " — قابل بازی" else " — غیرمجاز")
    }

    /** «کارت روی میز، صندلی شما» — the table region itself. */
    fun tableLabel(state: HokmUiState): String = buildString {
        append(phaseLabel(state.handPhase))
        append(" — ")
        append(progressLabel(state.handNumber, state.trickNumber))
        if (state.tableCards.isNotEmpty()) {
            append(" — ")
            append(trickLabel(state.tableCards))
        }
        if (state.hokm != null) {
            append(" — ")
            append(hokmLabel(state.hokm))
        }
    }
}
