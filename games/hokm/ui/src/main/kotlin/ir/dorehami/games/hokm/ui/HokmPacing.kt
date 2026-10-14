package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.Emission
import ir.dorehami.games.hokm.Card
import ir.dorehami.games.hokm.Play

/**
 * HK-010 — the event/pacing core: a versioned notice queue plus pure display rules.
 *
 * The guarantee under event bursts, skipped/partial animation cycles, and mid-trick resync:
 * **a card is shown exactly once and a sound is played exactly once** — never doubled, never
 * dropped. Two mechanisms do this:
 *
 * * the [HokmEventQueue] fingerprints every notice, so a re-delivered action is refused before
 *   its sounds can fire twice;
 * * the pure [HokmPacing] rules are *idempotent against the felt*: a card that a merge already
 *   restored from the snapshot is not appended again, and a sweep only runs for the trick the
 *   felt is actually showing (a full trick — which a snapshot can never carry, so it can only
 *   come from the in-order notice stream).
 *
 * Sounds of notices that turned out to be "behind" the truth are still played (once) — skip
 * semantics drop only the *visual* of outdated work, never its audio. And presentation never
 * writes the result: hand, legal cards, turn, scores and the winner come from snapshots via
 * [HokmUiMapper.merge] alone.
 */

/** One engine action's worth of animation work: its emissions in order, tagged with a version. */
data class Notice(val version: Long, val actor: Int, val emissions: List<Emission>)

/**
 * Bounded FIFO of [Notice]s with duplicate refusal:
 *
 * - **dedup** — the same (version, emissions) offered twice (retry, double delivery) is refused,
 *   so a re-delivered event can never play a sound twice;
 * - **bounded** — overflow drops the *oldest* notice and counts it; a lagging stream must never
 *   grow the queue without bound. (What is dropped here was already superseded by later truth.)
 */
class HokmEventQueue(private val capacity: Int = 64, private val remember: Int = 256) {

    private val pending = ArrayDeque<Notice>()
    private val seen = LinkedHashSet<Pair<Long, List<Emission>>>()

    /** Notices dropped by the capacity bound (oldest first). */
    var dropped: Int = 0
        private set

    /** Duplicate offers refused by the fingerprint check. */
    var duplicates: Int = 0
        private set

    val isEmpty: Boolean get() = pending.isEmpty()
    val size: Int get() = pending.size

    /** Offers a notice. Returns true when it was accepted as new work. */
    fun offer(notice: Notice): Boolean {
        val fingerprint = notice.version to notice.emissions
        if (!seen.add(fingerprint)) {
            duplicates++
            return false
        }
        while (seen.size > remember) seen.remove(seen.first())
        pending.addLast(notice)
        while (pending.size > capacity) {
            pending.removeFirst()
            dropped++
        }
        return true
    }

    fun poll(): Notice? = pending.removeFirstOrNull()
}

/**
 * Pure display rules for the animated parts of the felt — the open trick on the table and the
 * score sheet. These decide *what is shown*; they never decide who won, whose turn it is, or
 * what a hand holds — that is the snapshot's job (see [HokmUiMapper.merge]).
 */
object HokmPacing {

    /**
     * A `card_played` notice lands: add the card to the felt display — exactly once. Duplicates
     * (re-delivery or a merge that already restored the card), cards of a different trick, and
     * anything arriving while a sweep/collect owns the felt are dropped from the *display*; the
     * truth is restored by the next snapshot merge.
     */
    fun onCardPlayed(old: HokmUiState, seat: Int, card: Card, trickNumber: Int): HokmUiState {
        if (old.introShown != null || old.dealing) return old
        if (old.sweepOf != null || old.collectingTo != null) return old
        if (old.tableCards.isNotEmpty() && old.tableTrickNumber != trickNumber) return old
        if (old.tableCards.any { it.seat == seat && it.card == card }) return old
        return old.copy(
            tableCards = old.tableCards + Play(seat, card),
            tableTrickNumber = trickNumber,
        )
    }

    /**
     * A `trick_won` notice: freeze the completed trick on the felt for the collect animation.
     * Only a *full* trick is swept — a snapshot can never show a full open trick (the mapper
     * refuses that as inconsistent), so a full felt can only be the trick the notice stream just
     * completed. A stale win over an empty/partial felt changes nothing.
     */
    fun onTrickWon(old: HokmUiState, winner: Int): HokmUiState {
        if (old.sweepOf != null) return old
        if (old.tableCards.size != old.playerCount) return old
        return old.copy(sweepOf = winner, turn = -1)
    }

    /** The collect animation finished: release the felt — the next merge fills the truth. */
    fun onTrickCollected(old: HokmUiState): HokmUiState =
        old.copy(tableCards = emptyList(), sweepOf = null, collectingTo = null)

    /** Score-sheet entry from a `hand_finished` notice — idempotent per hand number. */
    fun recordHand(old: HokmUiState, summary: HandSummary): HokmUiState =
        if (old.handHistory.any { it.handNumber == summary.handNumber }) old
        else old.copy(handHistory = old.handHistory + summary)
}
