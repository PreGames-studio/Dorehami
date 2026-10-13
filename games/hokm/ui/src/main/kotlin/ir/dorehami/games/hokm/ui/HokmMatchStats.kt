package ir.dorehami.games.hokm.ui

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
