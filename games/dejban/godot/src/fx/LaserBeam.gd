## One fixed-direction, piercing laser shot. The visual ray extends to the
## battlefield rectangle; collision uses the same hit circles as Projectile.
## The ray persists for `duration`, hitting each enemy at most once as it moves
## through the beam, without turning toward or stopping at a target.
class_name LaserBeam
extends Node2D

const DEATH_FX_CONTEXT := "_laser_beam_death"
const MAX_DAMAGE_LABELS := 12
const EPSILON := 0.000001

var battle = null
var start := Vector2.ZERO
var finish := Vector2.ZERO
var direction := Vector2.RIGHT
var duration := 0.35
var elapsed := 0.0
var damage_value := 0.0
var pierce_armor := true
var beam_width := 3.0
var firing_level := 1
var finished := false
var muzzle: LaserMuzzle = null
var strip: Texture2D = null
var hit_ids: Dictionary = {}
var previous_centers: Dictionary = {}
var damage_labels := 0

## Slab intersection includes the exact edge. If the muzzle is outside, only
## the portion that actually enters the terrain can hit; an outward ray is empty.
static func clip_ray(origin: Vector2, ray: Vector2, bounds: Rect2) -> PackedVector2Array:
	var result := PackedVector2Array()
	if ray.length_squared() <= EPSILON or bounds.size.x <= 0.0 or bounds.size.y <= 0.0:
		return result
	var d := ray.normalized()
	var entry := 0.0
	var exit := INF
	for axis in 2:
		var o: float = origin[axis]
		var component: float = d[axis]
		var low: float = bounds.position[axis]
		var high: float = bounds.end[axis]
		if absf(component) <= EPSILON:
			if o < low or o > high:
				return result
			continue
		var t0 := (low - o) / component
		var t1 := (high - o) / component
		entry = maxf(entry, minf(t0, t1))
		exit = minf(exit, maxf(t0, t1))
	if exit < entry or exit < 0.0 or is_inf(exit):
		return result
	result.append(origin + d * maxf(0.0, entry))
	result.append(origin + d * exit)
	return result

## The distance between the fixed beam segment and the enemy's swept centre.
## This also catches fast enemies that cross the beam between two frames.
static func segment_distance_squared(a: Vector2, b: Vector2, c: Vector2, d: Vector2) -> float:
	if Geometry2D.segment_intersects_segment(a, b, c, d) != null:
		return 0.0
	var best := a.distance_squared_to(Geometry2D.get_closest_point_to_segment(a, c, d))
	best = minf(best, b.distance_squared_to(Geometry2D.get_closest_point_to_segment(b, c, d)))
	best = minf(best, c.distance_squared_to(Geometry2D.get_closest_point_to_segment(c, a, b)))
	return minf(best, d.distance_squared_to(Geometry2D.get_closest_point_to_segment(d, a, b)))

static func brightness(level: int) -> float:
	return 0.6 + 0.1 * float(clampi(level, 1, 5) - 1)

## Called after adding the beam to the battlefield FX layer. Parameters are
## snapshotted: an upgrade, moving target, or sold tower cannot redirect it.
func setup(origin_global: Vector2, direction_global: Vector2, battle_ref, amount: float,
		armor_piercing: bool, shot_duration: float, width: float, level: int,
		bounds := Balance.TERRAIN_RECT) -> bool:
	battle = battle_ref
	duration = maxf(0.01, shot_duration)
	damage_value = maxf(0.0, amount)
	pierce_armor = armor_piercing
	beam_width = maxf(0.5, width)
	firing_level = clampi(level, 1, 5)
	elapsed = 0.0
	finished = false
	hit_ids.clear()
	previous_centers.clear()
	var origin := to_local(origin_global)
	var local_dir := (to_local(origin_global + direction_global) - origin).normalized()
	var clipped := clip_ray(origin, local_dir, bounds)
	if clipped.size() != 2 or clipped[0].distance_squared_to(clipped[1]) <= EPSILON:
		finished = true
		queue_free()
		return false
	start = clipped[0]
	finish = clipped[1]
	direction = (finish - start).normalized()
	strip = Assets.tex("towers_laser/beam_strip")
	muzzle = LaserMuzzle.new()
	muzzle.position = origin
	add_child(muzzle)
	muzzle.configure(beam_width, firing_level)
	z_index = 1
	_scan_enemies(1.0)
	queue_redraw()
	return true

func _process(delta: float) -> void:
	if finished:
		return
	var dt := maxf(delta, 0.0)
	var active := minf(dt, maxf(0.0, duration - elapsed))
	var movement_fraction := active / dt if dt > 0.0 else 1.0
	_scan_enemies(movement_fraction)
	elapsed += active
	if elapsed >= duration:
		finished = true
		queue_free()
		return
	if muzzle != null:
		muzzle.update_progress(elapsed / duration)
	queue_redraw()

func _scan_enemies(movement_fraction: float) -> void:
	if battle == null or not is_instance_valid(battle):
		return
	var candidates: Array = battle.enemies.duplicate()
	# Projectile hit radii are measured in global pixels. Keep previous centres
	# in this layer's coordinates (so screen shake does not invent movement),
	# but do the final distance test in global coordinates. The cross product
	# gives the transformed perpendicular beam width even under nonuniform scale.
	var global_start := to_global(start)
	var global_finish := to_global(finish)
	var global_forward := (global_finish - global_start).normalized()
	var local_normal := Vector2(-direction.y, direction.x)
	var global_normal := to_global(start + local_normal) - global_start
	var half_width := beam_width * 0.5 * absf(global_forward.cross(global_normal))
	for enemy in candidates:
		if enemy == null or not is_instance_valid(enemy) or enemy.is_queued_for_deletion() or not enemy.alive:
			continue
		var id: int = enemy.get_instance_id()
		var center := to_local(Projectile.target_point_global(enemy))
		var previous: Vector2 = previous_centers.get(id, center)
		var active_center := previous.lerp(center, clampf(movement_fraction, 0.0, 1.0))
		previous_centers[id] = center
		if hit_ids.has(id) or damage_value <= 0.0:
			continue
		var radius := Projectile.target_radius(enemy) + half_width
		if segment_distance_squared(global_start, global_finish, to_global(previous), to_global(active_center)) > radius * radius:
			continue
		# A synchronous death signal may remove another candidate during this
		# pass. Check live membership only for actual collisions, not all N nodes.
		if not battle.enemies.has(enemy):
			continue
		hit_ids[id] = true
		var old_hp: float = enemy.hp
		enemy.set_meta(DEATH_FX_CONTEXT, true)
		# A penetrating laser deducts its full power from HP, including against shields.
		enemy.take_damage(damage_value, pierce_armor, true)
		if is_instance_valid(enemy):
			enemy.remove_meta(DEATH_FX_CONTEXT)
		if is_instance_valid(enemy) and damage_labels < MAX_DAMAGE_LABELS:
			var actual := maxf(0.0, old_hp - float(enemy.hp))
			Fx.text(get_parent(), center + Vector2(0.0, -18.0), Ui.fa(round(actual)), Color(0.66, 0.95, 1.0), 17)
			damage_labels += 1

func _draw() -> void:
	var length := start.distance_to(finish)
	if length <= 0.0:
		return
	var fade_in := minf(1.0, elapsed / 0.025)
	var fade_out := minf(1.0, (duration - elapsed) / 0.075)
	var alpha := clampf(fade_in * fade_out, 0.0, 1.0)
	var light := brightness(firing_level)
	# The reusable strip provides blue fringe and animated light; two lines add
	# a level-scaled cyan shell and a bright white central beam.
	if strip != null:
		draw_set_transform(start, direction.angle())
		var halo_width := beam_width * (3.5 + 0.15 * float(firing_level))
		draw_texture_rect(strip, Rect2(0.0, -halo_width * 0.5, length, halo_width), false,
			Color(1.0, 1.0, 1.0, alpha * light))
		draw_set_transform(Vector2.ZERO, 0.0, Vector2.ONE)
	draw_line(start, finish, Color(0.05, 0.62, 1.0, alpha * (0.60 + 0.30 * light)), beam_width, true)
	draw_line(start, finish, Color(0.92, 1.0, 1.0, alpha), maxf(1.0, beam_width * 0.36), true)
