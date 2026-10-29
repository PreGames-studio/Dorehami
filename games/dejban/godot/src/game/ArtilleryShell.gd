## A real artillery projectile, isolated from the legacy rocket and machine-gun
## behavior. It flies to the ground position snapshotted at launch, detects the
## first swept enemy contact (including moving enemies), then explodes exactly
## once on contact or at that ground point. Damage is resolved in the same frame.
class_name ArtilleryShell
extends Projectile

## Battle uses this transient mark to avoid stacking a second explosion sprite
## for every enemy killed by the same artillery blast.
const DEATH_FX_CONTEXT := "_artillery_explosion_death"

var ground_destination := Vector2.ZERO
var edge_damage_fraction := 0.5
var firing_level := 1
var impact_global := Vector2.ZERO
var _previous_enemy_positions: Dictionary = {}
var _detonated := false
## Stable battlefield coordinates: the shared FX layer moves with the world
## when the keep is shaken, so a shell never loses its ground destination.
var _space: Node2D = null

func setup(source_tower, shot_target, amount: float, launch_direction := Vector2.ZERO) -> void:
	# The parent creates the shared sprite and captures the battle; passing a zero
	# direction here deliberately avoids the machine-gun's straight-shot mode.
	super.setup(source_tower, shot_target, amount, Vector2.ZERO)
	if _shot_battle != null and is_instance_valid(_shot_battle):
		_space = _shot_battle.fx_layer() as Node2D
	ground_destination = _in_space(shot_target.global_position)
	splash = source_tower.splash_radius()
	firing_level = source_tower.level
	var stats := Tower.level_config(weapon, firing_level)
	edge_damage_fraction = clampf(float(stats.get("edge_damage_fraction", weapon.get("edge_damage_fraction", 0.5))), 0.0, 1.0)
	if launch_direction.length_squared() > 0.000001:
		_dir = (_in_space(source_tower.global_position + launch_direction) - _in_space(source_tower.global_position)).normalized()
	else:
		_dir = Vector2.RIGHT
	_sprite.texture = Assets.tex("towers_artillery/shell")
	_sprite.scale = Vector2.ONE * (0.72 + 0.04 * float(firing_level - 1))
	# A copy of positions is kept for swept relative-motion collision, so a
	# fast-moving enemy cannot pass through a shell between frames.
	for enemy in _enemies():
		if _live(enemy):
			_previous_enemy_positions[enemy.get_instance_id()] = _in_space(enemy.global_position)

func _process(delta: float) -> void:
	if _detonated:
		return
	var dt := maxf(delta, 0.0)
	var from := _in_space(global_position)
	var to_destination := ground_destination - from
	var distance := to_destination.length()
	if dt <= 0.0 and distance > 0.001:
		return
	# A target inside the muzzle cannot be fired at backwards. Detonate at the
	# muzzle instead; the target remains within this tower's small blast radius.
	var inside_muzzle := distance > 0.001 and to_destination.dot(_dir) < 0.0
	var available := maxf(speed, 1.0) * dt
	var travelled := 0.0 if inside_muzzle else minf(distance, available)
	var to := from + to_destination.normalized() * travelled if distance > 0.001 else from
	var fraction_of_frame := (travelled / available) if available > 0.000001 else 0.0
	var reaches_ground := inside_muzzle or distance <= available + 0.001
	var next_positions: Dictionary = {}
	var motions: Array = []
	for enemy in _enemies().duplicate():
		if not _live(enemy):
			continue
		var id: int = enemy.get_instance_id()
		var current: Vector2 = _in_space(enemy.global_position)
		var previous: Vector2 = _previous_enemy_positions.get(id, current)
		next_positions[id] = current
		motions.append({"enemy": enemy, "previous": previous, "current": current})
	var earliest := 2.0
	for motion in motions:
		var enemy = motion["enemy"]
		var previous: Vector2 = motion["previous"]
		var current: Vector2 = motion["current"]
		# The shell can reach ground part-way through a long frame. Test only the
		# corresponding part of each enemy's motion, not the full frame.
		var end_at_step := previous.lerp(current, fraction_of_frame)
		var hit := _entry_fraction(from - previous, to - end_at_step, target_radius(enemy))
		if hit >= 0.0 and hit < earliest:
			earliest = hit
	if (to - from).length_squared() > 0.000001:
		_dir = (to - from).normalized()
		var world_dir := _from_space(from + _dir) - _from_space(from)
		global_rotation = world_dir.angle()
	if earliest <= 1.0:
		var contact := from.lerp(to, earliest)
		global_position = _from_space(contact)
		_detonate(contact, motions, fraction_of_frame * earliest)
	elif reaches_ground:
		var landing := from if inside_muzzle else ground_destination
		global_position = _from_space(landing)
		_detonate(landing, motions, fraction_of_frame)
	else:
		global_position = _from_space(to)
		_previous_enemy_positions = next_positions

## Earliest segment/circle entry in relative space, or -1 if there is no hit.
static func _entry_fraction(from: Vector2, to: Vector2, radius: float) -> float:
	var offset := from
	var movement := to - from
	var c := offset.length_squared() - radius * radius
	if c <= 0.0:
		return 0.0
	var a := movement.length_squared()
	if a <= 0.000001:
		return -1.0
	var b := 2.0 * offset.dot(movement)
	var discriminant := b * b - 4.0 * a * c
	if discriminant < 0.0:
		return -1.0
	var entry := (-b - sqrt(discriminant)) / (2.0 * a)
	return entry if entry >= 0.0 and entry <= 1.0 else -1.0

static func _live(enemy) -> bool:
	return enemy != null and is_instance_valid(enemy) and not enemy.is_queued_for_deletion() and enemy.alive

func _in_space(world_point: Vector2) -> Vector2:
	return _space.to_local(world_point) if is_instance_valid(_space) else world_point

func _from_space(local_point: Vector2) -> Vector2:
	return _space.to_global(local_point) if is_instance_valid(_space) else local_point

## Values are computed from a snapshot before any death signal can remove an
## enemy from Battle.enemies. Distances are measured in world/screen pixels at
## the instant of impact. edge=1 gives constant damage; edge=0 gives zero at rim.
func _detonate(at: Vector2, motions: Array, time_fraction: float) -> void:
	if _detonated:
		return
	_detonated = true
	impact_global = _from_space(at)
	var victims: Array = []
	if splash > 0.0:
		for motion in motions:
			var enemy = motion["enemy"]
			if not _live(enemy):
				continue
			var previous: Vector2 = motion["previous"]
			var current: Vector2 = motion["current"]
			var at_impact := previous.lerp(current, clampf(time_fraction, 0.0, 1.0))
			var distance := at.distance_to(at_impact)
			if distance <= splash:
				var factor := lerpf(1.0, edge_damage_fraction, clampf(distance / splash, 0.0, 1.0))
				var amount: float = damage_value * factor
				if amount > 0.0:
					victims.append({"enemy": enemy, "amount": amount})
	# Signals may mutate the battle list as each enemy dies; never iterate it
	# directly here. Damage numbers are capped, not the damage itself.
	var numbers_shown := 0
	for victim in victims:
		var enemy = victim["enemy"]
		if not _live(enemy):
			continue
		var amount: float = victim["amount"]
		enemy.set_meta(DEATH_FX_CONTEXT, true)
		enemy.take_damage(amount, bool(weapon.get("armor_pierce", false)))
		if not is_instance_valid(enemy):
			continue
		enemy.remove_meta(DEATH_FX_CONTEXT)
		if tower != null and is_instance_valid(tower):
			tower._apply_effects(enemy)
		if numbers_shown < 6:
			_damage_text(enemy, amount)
			numbers_shown += 1
	var parent := _parent()
	if parent != null and is_instance_valid(parent):
		var blast := ArtilleryBlast.new()
		parent.add_child(blast)
		blast.position = (parent as Node2D).to_local(impact_global) if parent is Node2D else impact_global
		blast.setup(firing_level, splash)
	Audio.play_combat_limited("combat/aircraft-hit", 160, 0.04, -13.0)
	queue_free()

func _damage_text(enemy, amount: float) -> void:
	var parent := _parent()
	if parent == null or not is_instance_valid(parent):
		return
	var enemy_position: Vector2 = (parent as Node2D).to_local(enemy.global_position) if parent is Node2D else enemy.position
	Fx.text(parent, enemy_position + Vector2(0.0, -30.0), Ui.fa(round(amount)), Color(1.0, 0.92, 0.75), 17)

## The battle outlives a sold tower: shells already in flight still explode.
func _enemies() -> Array:
	if _shot_battle != null and is_instance_valid(_shot_battle):
		return _shot_battle.enemies
	return []
