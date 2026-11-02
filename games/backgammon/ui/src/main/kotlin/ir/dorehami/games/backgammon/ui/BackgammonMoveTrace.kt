package ir.dorehami.games.backgammon.ui

import ir.dorehami.games.backgammon.BackgammonPosition
import ir.dorehami.games.backgammon.BackgammonRules

/**
 * The single checker that moved between two snapshots, as `(from, to, whiteIsMoving)` where the
 * indexes are point indexes, [BackgammonRules.FROM_BAR] or [BackgammonRules.TO_OFF].
 *
 * The engine only hands the screen full positions, so the board animation derives its target from
 * the diff instead of guessing. Pure on purpose: the JVM tests pin every case (point→point,
 * bar→point, point→off, and the black direction) without running any Compose code.
 */
internal data class CheckerTrace(val from: Int, val to: Int, val white: Boolean)

internal fun diffCheckerMove(previous: BackgammonPosition, next: BackgammonPosition): CheckerTrace? {
    if (previous == next) return null
    traceFor(previous, next, white = true)?.let { return it }
    return traceFor(previous, next, white = false)
}

private fun traceFor(previous: BackgammonPosition, next: BackgammonPosition, white: Boolean): CheckerTrace? {
    val from = when {
        white && next.barWhite < previous.barWhite -> BackgammonRules.FROM_BAR
        !white && next.barBlack < previous.barBlack -> BackgammonRules.FROM_BAR
        else -> {
            val lost = previous.points.indices.firstOrNull { index -> countOf(previous, index, white) > countOf(next, index, white) }
            lost ?: return null
        }
    }
    val to = when {
        white && next.offWhite > previous.offWhite -> BackgammonRules.TO_OFF
        !white && next.offBlack > previous.offBlack -> BackgammonRules.TO_OFF
        else -> {
            val gained = next.points.indices.firstOrNull { index -> countOf(next, index, white) > countOf(previous, index, white) }
            gained ?: return null
        }
    }
    return CheckerTrace(from = from, to = to, white = white)
}

private fun countOf(position: BackgammonPosition, index: Int, white: Boolean): Int {
    val value = position.points[index]
    return if (white) value.coerceAtLeast(0) else (-value).coerceAtLeast(0)
}
