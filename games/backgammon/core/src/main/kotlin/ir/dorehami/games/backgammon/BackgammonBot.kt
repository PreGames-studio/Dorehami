package ir.dorehami.games.backgammon

import ir.dorehami.gamesdk.GameAction
import ir.dorehami.protocol.PlayerId
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Deterministic backgammon bot: every board is public information in backgammon, so the bot only
 * needs the position, the dice and the rules — no randomness, no clock, no hidden peek.
 *
 * Policy: play the maximal sequence with the best static evaluation (pips, checkers on the bar and
 * checkers already borne off). Ties keep the first sequence produced by [BackgammonRules.sequences],
 * which is stable for a given seed, so a replayed match reproduces every bot move.
 */
object BackgammonBot {

    fun action(session: BackgammonSession, player: PlayerId): GameAction? {
        val isWhite = session.playerColor(player) ?: return null
        if (isWhite != session.isWhiteTurn()) return null
        if (session.currentDice().isEmpty()) {
            return GameAction(BackgammonModule.ACTION_ROLL)
        }
        val sequences = session.availableSequences()
        val best = sequences.maxByOrNull { sequence -> value(session.board(), sequence, isWhite) } ?: return null
        return GameAction(
            type = BackgammonModule.ACTION_PLAY,
            payload = buildJsonObject {
                put(
                    BackgammonModule.PAYLOAD_MOVES,
                    buildJsonArray {
                        best.forEach { move ->
                            add(
                                buildJsonObject {
                                    put("from", move.from)
                                    put("die", move.die)
                                },
                            )
                        }
                    },
                )
            },
        )
    }

    /** Higher is better for the moving side. */
    internal fun value(board: BackgammonPosition, sequence: List<BackgammonMove>, isWhite: Boolean): Int {
        var position = board
        for (move in sequence) {
            if (!BackgammonRules.isLegal(position, move, isWhite)) return Int.MIN_VALUE
            position = BackgammonRules.apply(position, move, isWhite)
        }
        val ownPips = BackgammonRules.pipCount(position, isWhite)
        val rivalPips = BackgammonRules.pipCount(position, !isWhite)
        val ownOff = BackgammonRules.offCount(position, isWhite)
        val rivalBar = BackgammonRules.onBar(position, !isWhite)
        val ownBar = BackgammonRules.onBar(position, isWhite)
        return (rivalPips - ownPips) * 2 + rivalBar * 22 + ownOff * 12 - ownBar * 18
    }
}
