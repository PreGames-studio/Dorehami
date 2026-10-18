package ir.dorehami.games.hokm.ui

/**
 * HK-048 (first slice) — match stats / leaderboard entry as **pure, unit-testable** logic.
 *
 * The roadmap asks for stats tracking and leaderboard. This object defines the stats data
 * class, win-rate calculation, kot/hakem-koti tracking, and a leaderboard entry comparator.
 * The Android layer persists and renders.
 */
object HokmStats {

    data class Stats(
        val matchesPlayed: Int = 0,
        val matchesWon: Int = 0,
        val matchesLost: Int = 0,
        val matchesDrawn: Int = 0,
        val kots: Int = 0,
        val hakemKotis: Int = 0,
        val tricksWon: Int = 0,
        val tricksPlayed: Int = 0
    )

    fun recordWin(s: Stats, kot: Boolean = false, hakemKoti: Boolean = false): Stats =
        s.copy(
            matchesPlayed = s.matchesPlayed + 1,
            matchesWon = s.matchesWon + 1,
            kots = s.kots + if (kot) 1 else 0,
            hakemKotis = s.hakemKotis + if (hakemKoti) 1 else 0
        )

    fun recordLoss(s: Stats): Stats =
        s.copy(matchesPlayed = s.matchesPlayed + 1, matchesLost = s.matchesLost + 1)

    fun recordDraw(s: Stats): Stats =
        s.copy(matchesPlayed = s.matchesPlayed + 1, matchesDrawn = s.matchesDrawn + 1)

    fun recordTrick(s: Stats, won: Boolean): Stats =
        s.copy(
            tricksPlayed = s.tricksPlayed + 1,
            tricksWon = s.tricksWon + if (won) 1 else 0
        )

    fun winRate(s: Stats): Float =
        if (s.matchesPlayed == 0) 0f else s.matchesWon.toFloat() / s.matchesPlayed.toFloat()

    fun trickWinRate(s: Stats): Float =
        if (s.tricksPlayed == 0) 0f else s.tricksWon.toFloat() / s.tricksPlayed.toFloat()

    fun sanitize(s: Stats): Stats = s.copy(
        matchesPlayed = s.matchesPlayed.coerceAtLeast(0),
        matchesWon = s.matchesWon.coerceAtLeast(0),
        matchesLost = s.matchesLost.coerceAtLeast(0),
        matchesDrawn = s.matchesDrawn.coerceAtLeast(0),
        kots = s.kots.coerceAtLeast(0),
        hakemKotis = s.hakemKotis.coerceAtLeast(0),
        tricksWon = s.tricksWon.coerceAtLeast(0),
        tricksPlayed = s.tricksPlayed.coerceAtLeast(0)
    )

    /** Leaderboard entry comparator: higher win rate first, then more matches. */
    fun compare(a: Stats, b: Stats): Int {
        val cmp = winRate(b).compareTo(winRate(a))
        if (cmp != 0) return cmp
        return b.matchesPlayed.compareTo(a.matchesPlayed)
    }

    /** Whether stats are consistent (won+lost+drawn <= played). */
    fun isConsistent(s: Stats): Boolean =
        s.matchesWon + s.matchesLost + s.matchesDrawn <= s.matchesPlayed
}
