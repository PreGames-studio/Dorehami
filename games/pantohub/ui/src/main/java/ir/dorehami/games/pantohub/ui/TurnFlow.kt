package ir.dorehami.games.pantohub.ui

import ir.dorehami.games.pantohub.GamePhase

/**
 * When the «درست / رد واژه» buttons may be pressed.
 *
 * The engine refuses to record an outcome before the word has been shown to the performer
 * (`TurnProgress.wordRevealed`), so those buttons belong on the performer's private screen. Painting
 * them on the public screen produced two permanently greyed-out buttons — the bug this helper pins
 * down and the reason the UI now asks it instead of guessing.
 */
internal fun canRegisterOutcome(
    phase: GamePhase,
    wordRevealed: Boolean,
    hasWord: Boolean,
): Boolean = phase == GamePhase.RUNNING && wordRevealed && hasWord

/**
 * After an outcome is recorded the next word arrives hidden, which would bounce the phone back to
 * the public screen in the middle of a turn. While the performer is holding it we reveal the next
 * word straight away, so the private screen simply shows the next word.
 */
internal fun shouldAutoRevealNextWord(
    phase: GamePhase,
    wordRevealed: Boolean,
    hasWord: Boolean,
    wanted: Boolean,
): Boolean = wanted && phase == GamePhase.RUNNING && hasWord && !wordRevealed
