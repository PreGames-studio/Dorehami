## Same stationary pedestal and continuous aiming transform as the machine gun,
## but with a single large recoiling cannon and no Gatling animation.
class_name ArtilleryVisual
extends MachinegunVisual

## The preview/build ghost and the initial battlefield pose match the reference:
## world azimuth −45°, projected to an upward-right screen-space barrel.
const ARTILLERY_START_ANGLE := -TAU / 8.0

func setup(start_level: int) -> void:
	super.setup(start_level)
	mount.rotation = ARTILLERY_START_ANGLE
	desired_angle = ARTILLERY_START_ANGLE
	_refresh_views()

func set_level(value: int) -> void:
	current_level = clampi(value, 1, 5)
	# Its footprint and transform are constant; the fixed armor/shell rack may
	# visually upgrade, but this sprite never inherits the rotating mount.
	base.texture = Assets.tex("towers_artillery/base_%d" % current_level)
	var head_texture := Assets.tex("towers_artillery/head_%d_directions" % current_level)
	var barrel_texture := Assets.tex("towers_artillery/barrel_%d_directions" % current_level)
	_set_sheet(head, head_texture, DIRECTION_COLUMNS, DIRECTION_ROWS)
	_set_sheet(head_next, head_texture, DIRECTION_COLUMNS, DIRECTION_ROWS)
	_set_sheet(barrels, barrel_texture, DIRECTION_COLUMNS, DIRECTION_ROWS)
	_set_sheet(barrels_next, barrel_texture, DIRECTION_COLUMNS, DIRECTION_ROWS)
	spin_phase = 0.0
	_refresh_views()

func advance(delta: float) -> void:
	var dt := maxf(delta, 0.0)
	recoil = move_toward(recoil, 0.0, RECOIL_RETURN * dt)
	barrel_mount.position.x = -recoil
	firing_left = maxf(0.0, firing_left - dt)
	_refresh_views()

func _refresh_views() -> void:
	var phase := fposmod(mount.rotation, TAU) * float(DIRECTION_COUNT) / TAU
	var first := posmod(floori(phase), DIRECTION_COUNT)
	var second := posmod(first + 1, DIRECTION_COUNT)
	var fraction := phase - floorf(phase)
	head.frame = first
	head_next.frame = second
	barrels.frame = first
	barrels_next.frame = second
	head.modulate = Color(1.0, 1.0, 1.0, 1.0 - fraction)
	head_next.modulate = Color(1.0, 1.0, 1.0, fraction)
	barrels.modulate = head.modulate
	barrels_next.modulate = head_next.modulate
	barrel_art.position = Vector2(-cos(mount.rotation), -sin(mount.rotation) * DEPTH_SQUASH) * recoil * DIRECTION_CELL / FRAME
