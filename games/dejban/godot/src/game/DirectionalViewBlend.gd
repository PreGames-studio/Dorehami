## Keeps the 48-direction artwork crisp when the physical aim stops. During
## rotation, neighbouring views still fade smoothly, but the transition is
## concentrated near the boundary so the head is not translucent most of the
## turn. This changes only the artwork, never the continuous firing direction.
class_name DirectionalViewBlend
extends RefCounted

const SETTLE_SECONDS := 0.10
const TURN_EPSILON := 0.000001
const TRANSITION_START := 0.30
const TRANSITION_END := 0.70

var amount := 0.0

func note_turn(previous_angle: float, current_angle: float) -> void:
	if absf(angle_difference(previous_angle, current_angle)) > TURN_EPSILON:
		amount = 1.0

func advance(delta: float) -> void:
	amount = move_toward(amount, 0.0, maxf(delta, 0.0) / SETTLE_SECONDS)

## The nearest view takes over gently once rotation stops.  Both head and
## barrel use this same weight, so they cannot settle in different directions.
func next_weight(fraction: float) -> float:
	var nearest := 0.0 if fraction < 0.5 else 1.0
	var moving := smoothstep(TRANSITION_START, TRANSITION_END, fraction)
	return lerpf(nearest, moving, amount)
