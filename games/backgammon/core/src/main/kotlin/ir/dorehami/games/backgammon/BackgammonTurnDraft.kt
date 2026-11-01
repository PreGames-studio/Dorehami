package ir.dorehami.games.backgammon

/**
 * Builds one turn's maximal sequence on the screen without touching the authority.
 *
 * The table shows a preview board while the player stacks die-uses; [isComplete] becomes true as
 * soon as no legal die-use is left, which is exactly the moment the sequence may be submitted
 * ([BackgammonRules.sequences] only accepts maximal sequences). Everything here is pure — a
 * rejected submission can be undone locally without any hidden state.
 */
class BackgammonTurnDraft(
    position: BackgammonPosition,
    dice: List<Int>,
    val isWhite: Boolean,
) {
    private val startPosition: BackgammonPosition = position
    private val startDice: List<Int> = dice
    private val played = ArrayList<BackgammonMove>()

    private var current: BackgammonPosition = position
    private var remaining: List<Int> = dice

    /** The board as it looks after the moves stacked so far. */
    val board: BackgammonPosition get() = current

    /** Dice not spent yet. */
    val diceLeft: List<Int> get() = remaining

    /** The moves stacked so far, in order. */
    fun sequence(): List<BackgammonMove> = played.toList()

    /** Points the player may pick up with [die]. */
    fun sourcesFor(die: Int): List<Int> =
        BackgammonRules.legalMoves(current, die, isWhite).map { it.from }.distinct()

    /** Points that would be hit if [die] is played from [from]. */
    fun hitsWith(from: Int, die: Int): Boolean {
        val target = BackgammonRules.destination(BackgammonMove(from, die), isWhite)
        return target != BackgammonRules.TO_OFF &&
            BackgammonRules.rivalCount(current, target, isWhite) == 1
    }

    /** Stacks one die-use; returns the resulting board or `null` when the move is illegal. */
    fun play(from: Int, die: Int): BackgammonPosition? {
        val slot = remaining.indexOf(die)
        if (slot < 0) return null
        val move = BackgammonMove(from, die)
        if (!BackgammonRules.isLegal(current, move, isWhite)) return null
        current = BackgammonRules.apply(current, move, isWhite)
        remaining = remaining.toMutableList().also { it.removeAt(slot) }
        played += move
        return current
    }

    /** Removes the last stacked die-use. */
    fun undo(): BackgammonPosition {
        if (played.isEmpty()) {
            current = startPosition
            remaining = startDice
            return current
        }
        played.removeAt(played.lastIndex)
        var board = startPosition
        val dice = startDice.toMutableList()
        for (move in played) {
            val slot = dice.indexOf(move.die)
            if (slot >= 0) dice.removeAt(slot)
            board = BackgammonRules.apply(board, move, isWhite)
        }
        current = board
        remaining = dice
        return current
    }

    fun reset(): BackgammonPosition {
        played.clear()
        return undo()
    }

    /** True when nothing legal is left to play: the sequence is maximal and may be submitted. */
    fun isComplete(): Boolean = BackgammonRules.legalMovesForDice(current, remaining, isWhite).isEmpty()

    /** True when the stacked sequence is one the authority will accept. */
    fun isSubmittable(): Boolean = played.isNotEmpty() && played in BackgammonRules.sequences(startPosition, startDice, isWhite)

    fun action(): ir.dorehami.gamesdk.GameAction = BackgammonModule.playAction(played)
}
