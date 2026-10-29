## Five-level Tesla visual: the exact same pedestal stays fixed while the
## upright head is projected around its own globe. Charge and shot animation
## live in a separate overlay; aiming never tilts or moves the pedestal.
class_name TeslaVisual
extends Node2D

const GROUND := Vector2(192.0, 345.0)
const IMAGE_SCALE := 0.46
const DEPTH_SQUASH := 0.68
const FRAME := 384.0
const DIRECTION_CELL := 192.0
const DIRECTION_COUNT := 48
const DIRECTION_COLUMNS := 8
const DIRECTION_ROWS := 6
const IDLE_TURN_SPEED := 0.16
const CORE_CENTERS := [
	Vector2(192.0, 189.77), Vector2(191.57, 159.67), Vector2(191.57, 136.88),
	Vector2(192.0, 116.67), Vector2(192.86, 88.72),
]
const CORE_RADII := [15.05, 16.34, 16.77, 17.63, 18.49]
const ARM_SPANS := [39.0, 35.0, 36.0, 39.0, 58.0]

var base: Sprite2D = null
var body: Sprite2D = null
var head: Sprite2D = null
var head_next: Sprite2D = null
var core: Sprite2D = null
var projection: Node2D = null
var mount: Node2D = null
var charge: TeslaCharge = null
var current_level := 1
var desired_angle := 0.0
var firing_left := 0.0
var pulse_time := 0.0

func setup(start_level: int) -> void:
	texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	base = _sprite("FixedPedestal")
	base.texture = Assets.tex("towers_tesla/base")
	base.offset = -GROUND
	base.scale = Vector2.ONE * IMAGE_SCALE
	body = _sprite("FixedCoilBody")
	body.offset = -GROUND
	body.scale = Vector2.ONE * IMAGE_SCALE
	projection = Node2D.new()
	projection.name = "AimProjection"
	projection.scale = Vector2(IMAGE_SCALE, IMAGE_SCALE * DEPTH_SQUASH)
	add_child(projection)
	mount = Node2D.new()
	mount.name = "RotatingHeadAxis"
	projection.add_child(mount)
	# The upper hardware is rendered in projected yaw, rather than rotating an
	# upright picture in the plane of the screen. The globe stays circular.
	head = _sprite("ProjectedElectrodes")
	head.z_index = 1
	head_next = _sprite("ProjectedElectrodesNext")
	head_next.z_index = 1
	for view in [head, head_next]:
		view.offset = -GROUND * (DIRECTION_CELL / FRAME)
		view.scale = Vector2.ONE * (IMAGE_SCALE * FRAME / DIRECTION_CELL)
	core = _sprite("RoundEnergyCore")
	core.z_index = 2
	charge = TeslaCharge.new()
	charge.name = "ElectricCharge"
	charge.z_index = 3
	charge.scale = Vector2.ONE * IMAGE_SCALE
	add_child(charge)
	set_level(start_level)

func _sprite(node_name: String) -> Sprite2D:
	var sprite := Sprite2D.new()
	sprite.name = node_name
	sprite.centered = false
	add_child(sprite)
	return sprite

func set_level(value: int) -> void:
	current_level = clampi(value, 1, 5)
	var index := current_level - 1
	var center: Vector2 = CORE_CENTERS[index]
	body.texture = Assets.tex("towers_tesla/body_%d" % current_level)
	var atlas := Assets.tex("towers_tesla/head_%d_directions" % current_level)
	_set_head_atlas(head, atlas)
	_set_head_atlas(head_next, atlas)
	core.texture = Assets.tex("towers_tesla/core_%d" % current_level)
	var core_position := (center - GROUND) * IMAGE_SCALE
	projection.position = core_position
	core.position = core_position
	core.offset = -center
	core.scale = Vector2.ONE * IMAGE_SCALE
	charge.position = core_position
	charge.configure(current_level, float(CORE_RADII[index]), float(ARM_SPANS[index]))
	_refresh_head()

func _set_head_atlas(sprite: Sprite2D, texture: Texture2D) -> void:
	# Reset the old grid before swapping an atlas at upgrade time.
	sprite.frame = 0
	sprite.hframes = 1
	sprite.vframes = 1
	sprite.texture = texture
	sprite.hframes = DIRECTION_COLUMNS
	sprite.vframes = DIRECTION_ROWS

## Rotate at a bounded angular speed, including the shortest path across ±PI.
func aim_at(screen_vector: Vector2, speed: float, delta: float) -> void:
	if screen_vector.length_squared() > 0.000001:
		desired_angle = Vector2(screen_vector.x, screen_vector.y / DEPTH_SQUASH).angle()
	mount.rotation = rotate_toward(mount.rotation, desired_angle, maxf(speed, 0.0) * maxf(delta, 0.0))
	_refresh_head()

## Slowly scan while idle; acquisition continues smoothly from this angle.
func idle_turn(delta: float) -> void:
	mount.rotation = wrapf(mount.rotation + IDLE_TURN_SPEED * maxf(delta, 0.0), -PI, PI)
	desired_angle = mount.rotation
	_refresh_head()

func is_aimed(tolerance := 0.025) -> bool:
	return absf(angle_difference(mount.rotation, desired_angle)) <= tolerance

func forward_global() -> Vector2:
	return (mount.to_global(Vector2.RIGHT) - mount.to_global(Vector2.ZERO)).normalized()

func pivot_global() -> Vector2:
	return mount.to_global(Vector2.ZERO)

## Arc starts at the edge of the glowing sphere, on the same physical aim axis.
func emitter_global() -> Vector2:
	return mount.to_global(Vector2(float(CORE_RADII[current_level - 1]) * 0.86, 0.0))

func fired() -> void:
	firing_left = 0.20
	charge.flash = 1.0
	charge.queue_redraw()

func advance(delta: float) -> void:
	var dt := maxf(delta, 0.0)
	pulse_time = fposmod(pulse_time + dt, 120.0)
	firing_left = maxf(0.0, firing_left - dt)
	var pulse := 0.5 + 0.5 * sin(pulse_time * 4.4)
	var flash := clampf(firing_left / 0.20, 0.0, 1.0)
	core.modulate = Color(0.82 + pulse * 0.18 + flash * 0.18, 0.90 + pulse * 0.10 + flash * 0.12, 1.0, 1.0)
	charge.phase = pulse_time
	charge.flash = flash
	charge.queue_redraw()

func _refresh_head() -> void:
	if head == null or mount == null:
		return
	# Adjacent projected views cross-fade as the logical mount turns. The
	# portrait stays upright, the neck stays connected to the fixed shaft, and
	# the nearly symmetric ring and separate globe don't spin as flat stickers.
	var a := mount.rotation
	var phase := fposmod(a, TAU) * float(DIRECTION_COUNT) / TAU
	var first := posmod(floori(phase), DIRECTION_COUNT)
	var second := posmod(first + 1, DIRECTION_COUNT)
	var fraction := phase - floorf(phase)
	head.frame = first
	head_next.frame = second
	head.modulate = Color(1.0, 1.0, 1.0, 1.0 - fraction)
	head_next.modulate = Color(1.0, 1.0, 1.0, fraction)
	if charge != null:
		charge.yaw = a
		charge.width_factor = 0.72 + 0.28 * absf(cos(a))
		charge.queue_redraw()
