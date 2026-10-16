package ir.dorehami.games.hokm.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * GX-04 — the Compose side of the choreography: the only place where the pure table's
 * [HokmChoreography.Curve] and haptic slots become device things.
 *
 * Everything else (durations, offsets, reduced-motion variants) is decided in
 * `HokmChoreography.kt` and unit-tested there, so this file stays a lookup table with no logic of
 * its own — exactly the boundary HK-013.a drew for [HokmMotion] and the screen reads.
 */

/** The Compose easing of a movement's curve. */
internal fun HokmChoreography.Curve.toEasing(): Easing = when (this) {
    HokmChoreography.Curve.STANDARD -> FastOutSlowInEasing
    HokmChoreography.Curve.LINEAR -> LinearEasing
    // A throw leaves the hand fast and slows as it lands: ease-out, expressed as a cubic bezier so
    // it does not depend on which Compose version's named curves are available.
    HokmChoreography.Curve.DECELERATE -> CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

/**
 * The device haptic of a choreography slot. `cut` is the strongest nudge of the three (a trump
 * landed on somebody else's suit); the two card moments are light ticks — the same
 * `TextHandleMove` haptic the hand already used for a card, so the table keeps one tactile voice.
 */
internal fun hapticTypeOf(slot: String?): HapticFeedbackType = when (slot) {
    "cut" -> HapticFeedbackType.LongPress
    else -> HapticFeedbackType.TextHandleMove
}
