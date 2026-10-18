package ir.dorehami.games.hokm.ui

/**
 * HK-001 (match slice) — match state as **pure, unit-testable** logic.
 */
object HokmMatch {

    enum class Phase { DEALING, TRUMP_SELECT, PLAYING, HAND_OVER, MATCH_OVER }

    data class Match(
        val phase: Phase = Phase.DEALING,
        val ourScore: Int = 0,
        val theirScore: Int = 0,
        val handNumber: Int = 1,
        val hakemSeat: Int = 0
    )

    fun startHand(m: Match): Match = m.copy(phase = Phase.DEALING, handNumber = m.handNumber + 1)

    fun setTrump(m: Match): Match = m.copy(phase = Phase.PLAYING)

    fun endHand(m: Match, ourTricks: Int, theirTricks: Int): Match {
        val (o, t) = HokmScoring.handPoints(ourTricks, theirTricks, m.hakemSeat % 2 == 0)
        val newOur = m.ourScore + o
        val newTheir = m.theirScore + t
        val phase = if (HokmScoring.matchOver(newOur, newTheir)) Phase.MATCH_OVER else Phase.HAND_OVER
        return m.copy(phase = phase, ourScore = newOur, theirScore = newTheir)
    }

    fun isOver(m: Match): Boolean = m.phase == Phase.MATCH_OVER
}
