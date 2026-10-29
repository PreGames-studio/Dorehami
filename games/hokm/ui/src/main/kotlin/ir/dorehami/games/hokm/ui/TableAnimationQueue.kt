package ir.dorehami.games.hokm.ui

import ir.dorehami.games.hokm.HandWinKind
import ir.dorehami.games.hokm.Play
import ir.dorehami.uikit.toFa

/**
 * HK-010 — the bounded, versioned queue of *presentation* steps.
 *
 * Until now the table kept one slot per animation (`pendingCollect`, `pendingHandBanner`,
 * `pendingDeal`, `pendingPauseMs`). A burst — several bot moves, a host that resolves two tricks
 * before the phone finishes drawing one, a reconnect replay — overwrote those slots, so a trick
 * collect or a finished-hand banner could vanish, and nothing connected an animation to the hand
 * it belonged to. These types fix that without touching Compose:
 *
 * * every step carries the `handNumber`/`trickNumber`/`revision` it came from;
 * * a step with the same [TableStep.key] is ignored (duplicate delivery never animates twice);
 * * the queue is bounded, so a flood drops the *oldest* step instead of growing forever;
 * * a snapshot from a later hand drops the steps of the hand it replaced, so two hands are never
 *   animated at the same time (the "[snapshot] میتواند انیمیشنهای عقبمانده را کنار بگذارد" rule).
 */

/** One deferred presentation step. [key] identifies the event it came from. */
sealed interface TableStep {
    val handNumber: Int
    val trickNumber: Int
    val revision: Long
    val key: String

    /** "The trick is over, sweep the cards to [winner]." */
    data class CollectTrick(
        val winner: Int,
        override val handNumber: Int,
        override val trickNumber: Int,
        override val revision: Long,
    ) : TableStep {
        override val key: String get() = "collect:$handNumber:$trickNumber"
    }

    /** A short banner ("حکم: دل", "دستها کامل شد", "کُت!"). */
    data class ShowBanner(
        val banner: Banner,
        val holdMs: Long,
        override val handNumber: Int,
        override val trickNumber: Int,
        override val revision: Long,
    ) : TableStep {
        override val key: String get() = "banner:$handNumber:$trickNumber:${banner.title}"
    }

    /** The rest of the deck is dealt after hokm was named. */
    data class Deal(
        override val handNumber: Int,
        override val trickNumber: Int,
        override val revision: Long,
    ) : TableStep {
        override val key: String get() = "deal:$handNumber"
    }
}

/**
 * A bounded FIFO of [TableStep]s with duplicate suppression and hand-based staleness.
 *
 * Not thread-safe on purpose: the table touches it only from its own loop and from the coroutine
 * that feeds events, both on the UI dispatcher (same as `_state`).
 */
class TableAnimQueue(private val capacity: Int = DEFAULT_CAPACITY) {

    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val items = ArrayDeque<TableStep>()
    private var dropped = 0

    val size: Int get() = items.size
    val isEmpty: Boolean get() = items.isEmpty()

    /** How many steps were thrown away so far — because of overflow or a newer hand. */
    val droppedCount: Int get() = dropped

    fun peek(): TableStep? = items.firstOrNull()

    fun poll(): TableStep? = items.removeFirstOrNull()

    /**
     * Adds [step] unless an identical one is already queued. When the queue is full the oldest
     * step is dropped first: it is the most stale thing on screen.
     *
     * @return `true` when the step was accepted.
     */
    fun offer(step: TableStep): Boolean {
        if (items.any { it.key == step.key }) return false
        if (items.size >= capacity) {
            items.removeFirst()
            dropped++
        }
        items.addLast(step)
        return true
    }

    fun clear() {
        items.clear()
    }

    /**
     * Drops every step that belongs to a hand older than [handNumber].
     *
     * That is the only staleness rule the table applies automatically: once a snapshot proves the
     * table is in a later hand, replaying an earlier hand's banner or collect is wrong. Steps of
     * the *current* hand are kept, because they are what the player is watching.
     *
     * @return how many steps were dropped.
     */
    fun dropBeforeHand(handNumber: Int): Int {
        val before = items.size
        items.retainAll { it.handNumber >= handNumber }
        val removed = before - items.size
        dropped += removed
        return removed
    }

    /** Snapshot helper: `true` when anything queued would animate an already-replaced hand. */
    fun hasStepsBeforeHand(handNumber: Int): Boolean = items.any { it.handNumber < handNumber }
}

/** The default bound; a full table never needs more than a couple of steps at once. */
const val DEFAULT_ANIM_QUEUE_CAPACITY = 8

/**
 * The pure parts of event handling: what a `card_played` event does to the cards on the table,
 * and what a finished hand says on screen.
 *
 * Nothing here can write a match result: these functions return presentation values only
 * (a list of plays, a [Banner]). Scores, the winner and the finished flag always come from a
 * validated snapshot (see [TableSnapshotMapper]); the event path is not allowed to guess them.
 */
object TableEvents {

    /** Cards currently drawn for a trick, after [play]; a duplicate delivery changes nothing. */
    data class TableCards(val plays: List<Play>, val duplicate: Boolean)

    fun isDuplicate(shown: List<Play>, play: Play): Boolean = shown.any { it.seat == play.seat && it.card == play.card }

    /**
     * Appends [play] unless the same seat already shows that exact card (a duplicated event must
     * never show two copies of one card).
     */
    fun appendCard(shown: List<Play>, play: Play): TableCards =
        if (isDuplicate(shown, play)) TableCards(shown, duplicate = true) else TableCards(shown + play, duplicate = false)

    /**
     * Whose turn it is once [play] was drawn. A closed trick has nobody on turn (`-1`) until the
     * snapshot names the winner again. A duplicate keeps the previous turn, because no new card
     * was added.
     */
    fun turnAfter(update: TableCards, play: Play, players: Int): Int = when {
        update.duplicate -> -1
        update.plays.size >= players -> -1
        else -> (play.seat + 1) % players
    }

    /**
     * The banner a finished hand shows. `null` only when there is nothing to celebrate (never for a
     * real summary). Pure: it reads the summary, my seat and the table size, nothing else.
     */
    fun handBanner(
        summary: HandSummary,
        players: Int,
        myTeam: Int,
        teamLabel: (Int) -> String,
    ): Banner {
        val mine = summary.winnerTeam == myTeam
        val we = if (players == 4) "بردیم" else "بردی"
        val lost = if (players == 4) "باختیم" else "باختی"
        val title = when (summary.kind) {
            HandWinKind.NORMAL -> if (mine) "این دست را $we!" else "این دست را $lost"
            HandWinKind.KOT -> if (mine) "کُت!" else "کُت ${if (players == 4) "شدیم" else "شدی"}!"
            HandWinKind.HAKEM_KOT ->
                if (mine) "حاکم‌کُت! ${summary.points.toFaDigits()} امتیاز" else "حاکم‌کُت ${if (players == 4) "شدیم" else "شدی"}…"
            HandWinKind.VOID -> "مساوی — این دست بی‌نتیجه ماند"
        }
        val subtitle = summary.winnerTeam?.let { "${summary.points.toFaDigits()} امتیاز برای ${teamLabel(it)}" }
        return Banner(title, subtitle, big = true)
    }

    /** One-shot sound for a finished hand, so a banner and its sound never come from two places. */
    fun handSound(summary: HandSummary, myTeam: Int): String {
        val mine = summary.winnerTeam == myTeam
        return when (summary.kind) {
            HandWinKind.NORMAL, HandWinKind.VOID -> if (mine) "hand_won" else "hand_lost"
            else -> if (mine) "kot" else "hand_lost"
        }
    }

    /**
     * Whether the "sending" gate is still on after a snapshot arrived.
     *
     * Only a **newer** revision is the answer to my move: re-reading the same view (a deadline
     * update, a reconnect ping) must not let a second tap slip through before the host decides.
     */
    fun sendingAfterSnapshot(oldRevision: Long, newRevision: Long, wasSending: Boolean): Boolean =
        if (newRevision > oldRevision) false else wasSending
}

private fun Int.toFaDigits(): String = toFa()
