package ir.dorehami.games.hokm.ui

import ir.dorehami.gamesdk.GameEngine
import ir.dorehami.games.hokm.HandWinKind

/** The exact lifetime-stat delta derived from one finished match. */
internal data class HokmMatchStatsDelta(
    val won: Boolean,
    val hands: Int,
    val handsWon: Int,
    val kots: Int,
)

/** Returns no delta until the match is finished; each hand is counted from its immutable summary. */
internal fun HokmUiState.matchStatsDelta(): HokmMatchStatsDelta? {
    if (!finished) return null
    val myHandWins = handHistory.filter { it.winnerTeam == myTeam }
    return HokmMatchStatsDelta(
        won = winnerTeam == myTeam,
        hands = handHistory.size,
        handsWon = myHandWins.size,
        kots = myHandWins.count { it.kind == HandWinKind.KOT || it.kind == HandWinKind.HAKEM_KOT },
    )
}

/** Composition recreation may re-run effects; a finished match can update preferences only once. */
internal fun shouldRecordMatchStats(finished: Boolean, alreadyRecorded: Boolean): Boolean = finished && !alreadyRecorded

/**
 * Stable identity of one finished match, derived only from its immutable outcome (HK-011).
 * The same match restored after process death yields the same id — so the durable stats guard
 * can recognise it — while two different matches almost surely differ (same score line, same
 * hand count, same kinds and trick splits would all have to collide).
 */
internal fun matchStatsId(state: HokmUiState): String {
    val canonical = buildString {
        append(state.playerCount).append('|').append(state.mySeat).append('|')
        append(state.scores.joinToString(",")).append('|').append(state.winnerTeam).append('|')
        append(state.handNumber).append('|')
        for (h in state.handHistory) {
            append(h.handNumber).append(':').append(h.kind.id).append(':').append(h.winnerTeam).append(':')
            append(h.points).append(':').append(h.tricks.joinToString(",")).append(';')
        }
    }
    return GameEngine.sha256(canonical)
}

/**
 * The durable half of the once-only rule: a match whose id was already recorded must never
 * increment the lifetime stats again — even across rotation, process death or a restore of
 * the finished match.
 */
internal fun shouldRecordStatsFor(matchId: String, lastRecordedMatchId: String?): Boolean =
    matchId != lastRecordedMatchId
