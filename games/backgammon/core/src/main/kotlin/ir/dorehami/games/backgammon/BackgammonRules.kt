package ir.dorehami.games.backgammon

/**
 * Backgammon rules as pure functions over an immutable [BackgammonPosition].
 *
 * Board convention (single shared board, like a real table):
 *
 * * `points[0]` is point 1 and `points[23]` is point 24.
 * * A positive value is a white checker, a negative value a black checker.
 * * White plays **24 → 1** (home board = points 1..6 = indexes 0..5, distance to off = `index + 1`).
 * * Black plays **1 → 24** (home board = points 19..24 = indexes 18..23, distance = `24 - index`).
 *
 * No clock and no randomness live here: dice are always handed in (ADR-0003).
 */
object BackgammonRules {

    const val POINT_COUNT = 24

    /** Every player owns exactly this many checkers. */
    const val CHECKERS = 15

    /** Sentinel `from` value of a move that enters a checker from the bar. */
    const val FROM_BAR = -1

    /** Sentinel `to` value of a move that takes a checker off the board. */
    const val TO_OFF = -2

    /** Standard opening position: 2·24, 5·13, 3·8, 5·6 for white (mirrored for black). */
    val OPENING: List<Int> = run {
        val points = IntArray(POINT_COUNT)
        points[23] = 2
        points[12] = 5
        points[7] = 3
        points[5] = 5
        points[0] = -2
        points[11] = -5
        points[16] = -3
        points[18] = -5
        points.toList()
    }

    fun isHome(index: Int, white: Boolean): Boolean =
        if (white) index in 0..5 else index in 18..23

    /** How far [index] is from taking a checker off (`1` = already on the last point). */
    fun distanceToOff(index: Int, white: Boolean): Int =
        if (white) index + 1 else POINT_COUNT - index

    fun ownCount(position: BackgammonPosition, index: Int, white: Boolean): Int = if (white) {
        maxOf(0, position.points[index])
    } else {
        maxOf(0, -position.points[index])
    }

    fun rivalCount(position: BackgammonPosition, index: Int, white: Boolean): Int = if (white) {
        maxOf(0, -position.points[index])
    } else {
        maxOf(0, position.points[index])
    }

    fun onBar(position: BackgammonPosition, white: Boolean): Int =
        if (white) position.barWhite else position.barBlack

    fun offCount(position: BackgammonPosition, white: Boolean): Int =
        if (white) position.offWhite else position.offBlack

    fun onBoardCount(position: BackgammonPosition, white: Boolean): Int {
        var total = onBar(position, white)
        for (index in 0 until POINT_COUNT) total += ownCount(position, index, white)
        return total
    }

    /** Bearing off needs all fifteen checkers inside the home board and none on the bar. */
    fun allInHome(position: BackgammonPosition, white: Boolean): Boolean {
        if (onBar(position, white) > 0) return false
        for (index in 0 until POINT_COUNT) {
            if (!isHome(index, white) && ownCount(position, index, white) > 0) return false
        }
        return true
    }

    /** Where a checker from the bar enters the board for [die]. */
    fun entryIndex(die: Int, white: Boolean): Int = if (white) POINT_COUNT - die else die - 1

    /** Index of the point a move lands on, or [TO_OFF]. */
    fun destination(move: BackgammonMove, white: Boolean): Int = when {
        move.from == FROM_BAR -> entryIndex(move.die, white)
        white -> move.from - move.die
        else -> move.from + move.die
    }.let { if (it < 0 || it >= POINT_COUNT) TO_OFF else it }

    private fun hasCheckerFartherFromOff(
        position: BackgammonPosition,
        movedFrom: Int,
        white: Boolean,
    ): Boolean = if (white) {
        ((movedFrom + 1) until POINT_COUNT).any { ownCount(position, it, white) > 0 }
    } else {
        (0 until movedFrom).any { ownCount(position, it, white) > 0 }
    }

    /** Every legal use of one [die] right now. */
    fun legalMoves(position: BackgammonPosition, die: Int, white: Boolean): List<BackgammonMove> {
        if (die !in 1..6) return emptyList()
        val moves = ArrayList<BackgammonMove>(4)
        if (onBar(position, white) > 0) {
            val target = entryIndex(die, white)
            if (rivalCount(position, target, white) < 2) moves += BackgammonMove(FROM_BAR, die)
            return moves
        }
        for (index in 0 until POINT_COUNT) {
            if (ownCount(position, index, white) == 0) continue
            val target = if (white) index - die else index + die
            if (target in 0 until POINT_COUNT) {
                if (rivalCount(position, target, white) < 2) moves += BackgammonMove(index, die)
            } else if (allInHome(position, white)) {
                val distance = distanceToOff(index, white)
                when {
                    die == distance -> moves += BackgammonMove(index, die)
                    // Overshooting is allowed only when no checker is further from home.
                    die > distance && !hasCheckerFartherFromOff(position, index, white) ->
                        moves += BackgammonMove(index, die)
                }
            }
        }
        return moves
    }

    fun isLegal(position: BackgammonPosition, move: BackgammonMove, white: Boolean): Boolean =
        legalMoves(position, move.die, white).any { it.from == move.from }

    /** Every legal die-use for a whole roll (used by the table to know when a turn is over). */
    fun legalMovesForDice(
        position: BackgammonPosition,
        dice: List<Int>,
        white: Boolean,
    ): List<BackgammonMove> = dice.distinct().flatMap { legalMoves(position, it, white) }

    /** Applies one legal die-use; a blot is sent back to the bar. */
    fun apply(position: BackgammonPosition, move: BackgammonMove, white: Boolean): BackgammonPosition {
        require(isLegal(position, move, white)) { "illegal backgammon move: $move" }
        val points = position.points.toMutableList()
        var barWhite = position.barWhite
        var barBlack = position.barBlack
        var offWhite = position.offWhite
        var offBlack = position.offBlack
        val sign = if (white) 1 else -1

        if (move.from == FROM_BAR) {
            if (white) barWhite-- else barBlack--
        } else {
            points[move.from] -= sign
        }

        val target = destination(move, white)
        if (target == TO_OFF) {
            if (white) offWhite++ else offBlack++
        } else {
            if (rivalCount(position, target, white) == 1) {
                points[target] = 0
                if (white) barBlack++ else barWhite++
            }
            points[target] += sign
        }
        return BackgammonPosition(points.toList(), barWhite, barBlack, offWhite, offBlack)
    }

    /**
     * Every maximal way of playing [dice] — the only sequences a player may submit.
     *
     * Maximal means "no legal die-use is left behind"; when only one of two different dice can be
     * played at all, the higher one is mandatory (the standard backgammon obligation).
     */
    fun sequences(
        position: BackgammonPosition,
        dice: List<Int>,
        white: Boolean,
    ): List<List<BackgammonMove>> {
        if (dice.isEmpty()) return listOf(emptyList())
        val results = LinkedHashSet<List<BackgammonMove>>()

        fun walk(current: BackgammonPosition, remaining: List<Int>, played: List<BackgammonMove>) {
            var extended = false
            val triedDice = HashSet<Int>()
            for ((slot, die) in remaining.withIndex()) {
                if (!triedDice.add(die)) continue // identical dice are interchangeable
                val options = legalMoves(current, die, white)
                if (options.isEmpty()) continue
                extended = true
                val rest = remaining.toMutableList().also { it.removeAt(slot) }
                for (move in options) walk(apply(current, move, white), rest, played + move)
            }
            if (!extended) results += played
        }

        walk(position, dice, emptyList())
        if (results.isEmpty()) return listOf(emptyList())
        val longest = results.maxOf { it.size }
        var best = results.filter { it.size == longest }

        if (longest == 1 && dice.size == 2 && dice[0] != dice[1]) {
            val higher = dice.max()
            val lower = dice.min()
            val bothPlayable = legalMoves(position, lower, white).isNotEmpty() &&
                legalMoves(position, higher, white).isNotEmpty()
            if (bothPlayable) best = best.filter { it.first().die == higher }
        }
        return best.toList()
    }

    /** Pips left to travel (bar counts as 25, borne-off checkers as 0) — bot heuristics only. */
    fun pipCount(position: BackgammonPosition, white: Boolean): Int {
        var total = onBar(position, white) * 25
        for (index in 0 until POINT_COUNT) total += ownCount(position, index, white) * distanceToOff(index, white)
        return total
    }

    /** 1 = single, 2 = gammon, 3 = backgammon. */
    fun gameValue(loser: BackgammonPosition, loserIsWhite: Boolean): Int {
        if (offCount(loser, loserIsWhite) > 0) return 1
        val stillInWinnerHome = if (loserIsWhite) {
            (18..23).any { ownCount(loser, it, loserIsWhite) > 0 }
        } else {
            (0..5).any { ownCount(loser, it, loserIsWhite) > 0 }
        }
        val onBar = onBar(loser, loserIsWhite) > 0
        return if (stillInWinnerHome || onBar) 3 else 2
    }
}

/** One die-use: move the checker on [from] (an index or [BackgammonRules.FROM_BAR]) using [die]. */
data class BackgammonMove(val from: Int, val die: Int) {

    fun toJsonEntry(): String = "{\"from\":$from,\"die\":$die}"

    companion object {
        fun parse(from: Int, die: Int): BackgammonMove? = if (die in 1..6) BackgammonMove(from, die) else null
    }
}

/** Immutable full board: 24 points, bar counts and borne-off counts. */
data class BackgammonPosition(
    val points: List<Int> = BackgammonRules.OPENING,
    val barWhite: Int = 0,
    val barBlack: Int = 0,
    val offWhite: Int = 0,
    val offBlack: Int = 0,
) {
    init {
        require(points.size == BackgammonRules.POINT_COUNT) { "a backgammon board has 24 points" }
    }

    val whiteFinished: Boolean get() = offWhite >= BackgammonRules.CHECKERS
    val blackFinished: Boolean get() = offBlack >= BackgammonRules.CHECKERS
}
