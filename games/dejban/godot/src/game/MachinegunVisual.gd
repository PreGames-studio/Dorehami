## A fixed base and a separately aimed, layered machine-gun assembly.
## The art uses the tank renderer's isometric 3D projection, not a rotated flat
## top view. Adjacent views cross-fade while the *continuous* logical mount
## rotates; a stopped head settles to one sharp view. Bullets and recoil use
## the exact logical transform, not the rounded artwork.
class_name MachinegunVisual
extends Node2D

const FRAME := 256.0
const DIRECTION_CELL := 128.0
const DIRECTION_COUNT := 48
const DIRECTION_COLUMNS := 8
const DIRECTION_ROWS := 6
const GATLING_COLUMNS := 16
const GATLING_ROWS := 24
const GROUND := Vector2(128.0, 206.0)
const HEAD_PIVOT := Vector2(128.0, 100.0)
## Original design scale; artwork can extend beyond its ２×２ placement cell.
const IMAGE_SCALE := 0.39
const FOOTPRINT_OFFSET := Vector2(0.0, Balance.TOWER_SIZE * 0.35)
const DEPTH_SQUASH := 0.68
const MUZZLE := Vector2(100.0, 0.0)
const START_ANGLE := 0.0
const SPIN_FPS := 24.0
const SPIN_FRAMES := 8
const RECOIL_PIXELS := 5.0
const RECOIL_RETURN := 55.0

var base: Sprite2D = null
## These two nodes are the continuous, unprojected physical aiming transform.
var projection: Node2D = null
var mount: Node2D = null
var barrel_mount: Node2D = null
## Rendered views do not inherit mount.rotation: their upright dimensions stay
## upright as they turn, just like the project's existing isometric tank art.
var art: Node2D = null
var barrel_art: Node2D = null
var head: Sprite2D = null
var head_next: Sprite2D = null
var barrels: Sprite2D = null
var barrels_next: Sprite2D = null
var desired_angle := START_ANGLE
var spin_phase := 0.0
var firing_left := 0.0
var recoil := 0.0
var current_level := 1
var _view_blend := DirectionalViewBlend.new()

## Build the stationary base and the independent physical and visual assemblies.
func setup(start_level: int) -> void:
	texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	base = Sprite2D.new()
	base.name = "FixedBase"
	base.texture = Assets.tex("towers_machinegun/base")
	base.centered = false
	base.offset = -GROUND
	base.scale = Balance.base_scale(IMAGE_SCALE)
	add_child(base)

	projection = Node2D.new()
	projection.name = "AimProjection"
	projection.position = (HEAD_PIVOT - GROUND) * IMAGE_SCALE
	projection.scale = Vector2(IMAGE_SCALE, IMAGE_SCALE * DEPTH_SQUASH)
	add_child(projection)
	mount = Node2D.new()
	mount.name = "RotatingHead"
	mount.rotation = START_ANGLE
	projection.add_child(mount)
	barrel_mount = Node2D.new()
	barrel_mount.name = "RecoilingBarrels"
	mount.add_child(barrel_mount)

	art = Node2D.new()
	art.name = "IsometricViews"
	art.position = projection.position
	art.scale = Vector2.ONE * (IMAGE_SCALE * FRAME / DIRECTION_CELL)
	art.z_index = 1
	add_child(art)
	barrel_art = Node2D.new()
	barrel_art.name = "BarrelViews"
	art.add_child(barrel_art)
	barrels = _make_view(barrel_art, "Barrels")
	barrels_next = _make_view(barrel_art, "BarrelsNext")
	head = _make_view(art, "HeadArmor")
	head.z_index = 1
	head_next = _make_view(art, "HeadArmorNext")
	head_next.z_index = 1
	set_level(start_level)

## Each 128px view is drawn around the same 256px reference pivot.
func _make_view(parent: Node2D, sprite_name: String) -> Sprite2D:
	var sprite := Sprite2D.new()
	sprite.name = sprite_name
	sprite.centered = false
	sprite.offset = -HEAD_PIVOT * (DIRECTION_CELL / FRAME)
	parent.add_child(sprite)
	return sprite

func _set_sheet(sprite: Sprite2D, texture: Texture2D, columns: int, rows: int) -> void:
	# Reset frame/grid before changing sheets so a previous level's larger index
	# cannot temporarily exceed the new atlas's valid frame count.
	sprite.frame = 0
	sprite.hframes = 1
	sprite.vframes = 1
	sprite.texture = texture
	sprite.hframes = columns
	sprite.vframes = rows

## Swap only rotating head/barrel sheets. Base texture, anchor and aim are unchanged.
func set_level(value: int) -> void:
	current_level = clampi(value, 1, 5)
	var head_texture := Assets.tex("towers_machinegun/head_%d_directions" % current_level)
	var barrel_texture := Assets.tex("towers_machinegun/barrels_%d_directions" % current_level)
	_set_sheet(head, head_texture, DIRECTION_COLUMNS, DIRECTION_ROWS)
	_set_sheet(head_next, head_texture, DIRECTION_COLUMNS, DIRECTION_ROWS)
	var columns := GATLING_COLUMNS if current_level == 5 else DIRECTION_COLUMNS
	var rows := GATLING_ROWS if current_level == 5 else DIRECTION_ROWS
	_set_sheet(barrels, barrel_texture, columns, rows)
	_set_sheet(barrels_next, barrel_texture, columns, rows)
	spin_phase = 0.0
	_refresh_views()

## Turn at bounded angular velocity, across ±PI by the shortest route.
## Input is a screen-space vector from the pivot to the target.
func aim_at(screen_vector: Vector2, speed: float, delta: float) -> void:
	if screen_vector.length_squared() > 0.000001:
		desired_angle = Vector2(screen_vector.x, screen_vector.y / DEPTH_SQUASH).angle()
	var previous_angle := mount.rotation
	mount.rotation = rotate_toward(mount.rotation, desired_angle, maxf(speed, 0.0) * maxf(delta, 0.0))
	_view_blend.note_turn(previous_angle, mount.rotation)
	_refresh_views()

func is_aimed(tolerance := 0.025) -> bool:
	return absf(angle_difference(mount.rotation, desired_angle)) <= tolerance

## Use the transformed barrel axis for the actual bullet and muzzle flash.
func forward_global() -> Vector2:
	return (barrel_mount.to_global(Vector2.RIGHT) - barrel_mount.to_global(Vector2.ZERO)).normalized()

func muzzle_global() -> Vector2:
	return barrel_mount.to_global(MUZZLE)

func pivot_global() -> Vector2:
	return mount.to_global(Vector2.ZERO)

func casing_global() -> Vector2:
	return mount.to_global(Vector2(-10.0, 28.0))

## Recoil and the axial Gatling spin are independent of aim and of the base.
func fired() -> void:
	recoil = RECOIL_PIXELS
	barrel_mount.position.x = -recoil
	firing_left = 0.14
	_refresh_views()

func advance(delta: float) -> void:
	var dt := maxf(delta, 0.0)
	_view_blend.advance(dt)
	recoil = move_toward(recoil, 0.0, RECOIL_RETURN * dt)
	barrel_mount.position.x = -recoil
	if firing_left > 0.0:
		if current_level == 5:
			spin_phase = fposmod(spin_phase + SPIN_FPS * minf(dt, firing_left), float(SPIN_FRAMES))
		firing_left = maxf(0.0, firing_left - dt)
	_refresh_views()

func stop_firing() -> void:
	firing_left = 0.0

## Neighbouring views blend while turning, then settle to the closest fully
## opaque view so the stationary head and barrel cannot look doubled or blurry.
func _refresh_views() -> void:
	var phase := fposmod(mount.rotation, TAU) * float(DIRECTION_COUNT) / TAU
	var first := posmod(floori(phase), DIRECTION_COUNT)
	var second := posmod(first + 1, DIRECTION_COUNT)
	var fraction: float = _view_blend.next_weight(phase - floorf(phase))
	head.frame = first
	head_next.frame = second
	head.modulate = Color(1.0, 1.0, 1.0, 1.0 - fraction)
	head_next.modulate = Color(1.0, 1.0, 1.0, fraction)
	var spin_index := int(spin_phase) if current_level == 5 else 0
	barrels.frame = spin_index * DIRECTION_COUNT + first
	barrels_next.frame = spin_index * DIRECTION_COUNT + second
	barrels.modulate = Color(1.0, 1.0, 1.0, 1.0 - fraction)
	barrels_next.modulate = Color(1.0, 1.0, 1.0, fraction)
	var cell_ratio := DIRECTION_CELL / FRAME
	barrel_art.position = Vector2(-cos(mount.rotation) * recoil,
		-sin(mount.rotation) * DEPTH_SQUASH * recoil) * cell_ratio
