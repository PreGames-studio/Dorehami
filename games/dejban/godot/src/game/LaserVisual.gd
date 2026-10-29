## Reference-derived five-level laser tower. The armored pedestal is stationary;
## the separately rendered head follows a continuous physical aiming axis.
class_name LaserVisual
extends Node2D

const GROUND := Vector2(192.0, 345.0)
const IMAGE_SCALE := 0.46
const DEPTH_SQUASH := 0.68
const FRAME := 384.0
const DIRECTION_CELL := 192.0
const DIRECTION_COUNT := 48
const DIRECTION_COLUMNS := 8
const DIRECTION_ROWS := 6
const AXIS_CENTERS := [
	Vector2(192.0, 191.1), Vector2(192.0, 170.4), Vector2(192.0, 149.25),
	Vector2(192.0, 124.5), Vector2(192.0, 103.9),
]
const MUZZLE_LENGTHS := [26.667, 34.627, 37.942, 35.069, 37.883]

var base: Sprite2D = null
var head: Sprite2D = null
var head_next: Sprite2D = null
var projection: Node2D = null
var mount: Node2D = null
var current_level := 1
var desired_angle := 0.0

func setup(start_level: int) -> void:
	texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	base = _sprite("FixedLaserPedestal")
	base.centered = false
	base.offset = -GROUND
	base.scale = Vector2.ONE * IMAGE_SCALE
	projection = Node2D.new()
	projection.name = "LaserAimProjection"
	projection.scale = Vector2(IMAGE_SCALE, IMAGE_SCALE * DEPTH_SQUASH)
	add_child(projection)
	mount = Node2D.new()
	mount.name = "RotatingLaserAxis"
	projection.add_child(mount)
	# Atlas views are upright children of this visual, not rotated flat cards.
	head = _sprite("LaserHead")
	head_next = _sprite("LaserHeadNext")
	for view in [head, head_next]:
		view.centered = false
		view.offset = -GROUND * (DIRECTION_CELL / FRAME)
		view.scale = Vector2.ONE * (IMAGE_SCALE * FRAME / DIRECTION_CELL)
		view.z_index = 1
	set_level(start_level)

func _sprite(sprite_name: String) -> Sprite2D:
	var sprite := Sprite2D.new()
	sprite.name = sprite_name
	add_child(sprite)
	return sprite

func _set_head_atlas(sprite: Sprite2D, texture: Texture2D) -> void:
	sprite.frame = 0
	sprite.hframes = 1
	sprite.vframes = 1
	sprite.texture = texture
	sprite.hframes = DIRECTION_COLUMNS
	sprite.vframes = DIRECTION_ROWS

func set_level(value: int) -> void:
	current_level = clampi(value, 1, 5)
	# Art details change on upgrade, but footprint, position, scale and rotation
	# of the pedestal are identical at every level and never follow the head.
	base.texture = Assets.tex("towers_laser/base_%d" % current_level)
	var atlas := Assets.tex("towers_laser/head_%d_directions" % current_level)
	_set_head_atlas(head, atlas)
	_set_head_atlas(head_next, atlas)
	projection.position = (AXIS_CENTERS[current_level - 1] - GROUND) * IMAGE_SCALE
	_refresh_head()

## Input is the screen-space vector from the bore axis to an enemy hit point.
func aim_at(screen_vector: Vector2, speed: float, delta: float) -> void:
	if screen_vector.length_squared() > 0.000001:
		desired_angle = Vector2(screen_vector.x, screen_vector.y / DEPTH_SQUASH).angle()
	mount.rotation = rotate_toward(mount.rotation, desired_angle, maxf(speed, 0.0) * maxf(delta, 0.0))
	_refresh_head()

func is_aimed(tolerance := 0.002) -> bool:
	return absf(angle_difference(mount.rotation, desired_angle)) <= tolerance

func pivot_global() -> Vector2:
	return mount.to_global(Vector2.ZERO)

func forward_global() -> Vector2:
	return (mount.to_global(Vector2.RIGHT) - mount.to_global(Vector2.ZERO)).normalized()

func muzzle_global() -> Vector2:
	return mount.to_global(Vector2(float(MUZZLE_LENGTHS[current_level - 1]), 0.0))

func _refresh_head() -> void:
	if head == null or mount == null:
		return
	var phase := fposmod(mount.rotation, TAU) * float(DIRECTION_COUNT) / TAU
	var first := posmod(floori(phase), DIRECTION_COUNT)
	var fraction := phase - floorf(phase)
	head.frame = first
	head_next.frame = posmod(first + 1, DIRECTION_COUNT)
	head.modulate = Color(1.0, 1.0, 1.0, 1.0 - fraction)
	head_next.modulate = Color(1.0, 1.0, 1.0, fraction)
