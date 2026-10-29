## گلوله: به سوی هدف می‌رود و بر پایهٔ نوع سلاح آسیب می‌زند:
## تک‌هدف، ناحیه‌ای (splash) یا زنجیره‌ای (chain).
class_name Projectile
extends Node2D

var tower = null
var weapon: Dictionary = {}
var damage_value := 10.0
var speed := 500.0
var splash := 0.0
var chain := 0
var target = null
var last_target_pos := Vector2.ZERO
var lifetime := 4.0

var _dir := Vector2.RIGHT
## A non-zero launch direction opts into straight, swept machine-gun bullets.
var straight := false
var _shot_battle = null
var _previous_target_point := Vector2.ZERO
var _sprite: Sprite2D = null

func setup(source_tower, shot_target, amount: float, launch_direction := Vector2.ZERO) -> void:
	tower = source_tower
	weapon = source_tower.weapon if source_tower != null else {}
	damage_value = amount
	target = shot_target
	speed = float(weapon.get("bullet_speed", 520.0))
	splash = float(weapon.get("splash", 0.0))
	chain = int(weapon.get("chain", 0))
	_shot_battle = source_tower.battle if source_tower != null else null
	straight = launch_direction.length_squared() > 0.000001
	if straight:
		_dir = launch_direction.normalized()
		# Misses travel past the maximum range but do not persist for four seconds.
		lifetime = (source_tower.attack_range() + 80.0) / maxf(speed, 1.0)
	if shot_target != null and is_instance_valid(shot_target):
		last_target_pos = shot_target.position
		if straight:
			_previous_target_point = target_point_global(shot_target)

	_sprite = Sprite2D.new()
	_sprite.texture = Assets.tex("bullets/%s" % String(weapon.get("bullet", "stone")))
	_sprite.z_index = 12
	add_child(_sprite)
	z_index = 12
	rotation = 0.0

func _process(delta: float) -> void:
	if straight:
		# Test the entire swept segment, including on a long/last frame, so fast
		# projectiles cannot tunnel through the target's hit circle.
		var step_time := minf(maxf(delta, 0.0), maxf(lifetime, 0.0))
		_advance_straight(step_time)
		lifetime -= delta
		if lifetime <= 0.0 and not is_queued_for_deletion():
			queue_free()
		return
	lifetime -= delta
	if lifetime <= 0.0:
		_explode()
		return
	if target != null and is_instance_valid(target) and target.alive:
		last_target_pos = target.position + Vector2(0.0, -12.0)
	var to_target := last_target_pos - position
	var distance := to_target.length()
	var step := speed * delta
	if distance <= maxf(step, 8.0):
		position = last_target_pos
		_impact()
		return
	_dir = to_target / maxf(distance, 0.001)
	position += _dir * step
	if _sprite != null:
		rotation = _dir.angle()

## Aim and collision share this screen-space hit point. Tank sprites are taller
## and wider than infantry; no collision shapes are needed on the legacy enemies.
static func target_point_global(enemy) -> Vector2:
	var height := 24.0 if enemy is IsoEnemy else 12.0 * float(enemy.scale_mult)
	return enemy.global_position + Vector2(0.0, -height)

static func target_radius(enemy) -> float:
	return 28.0 if enemy is IsoEnemy else maxf(10.0, 12.0 * float(enemy.scale_mult))

func _straight_target_valid() -> bool:
	if target == null or not is_instance_valid(target) or target.is_queued_for_deletion() or not target.alive:
		return false
	if _shot_battle != null and is_instance_valid(_shot_battle):
		return _shot_battle.enemies.has(target)
	return true

func _advance_straight(delta: float) -> void:
	var from := global_position
	var to := from + _dir * speed * delta
	if _straight_target_valid():
		var centre := target_point_global(target)
		# Sweep in relative space so a moving enemy cannot cross the bullet
		# between frames without being detected either.
		var relative_start := from - _previous_target_point
		var relative_end := to - centre
		var closest := Geometry2D.get_closest_point_to_segment(Vector2.ZERO, relative_start, relative_end)
		if closest.length_squared() <= pow(target_radius(target), 2.0):
			var relative_motion := relative_end - relative_start
			var fraction := 0.0
			if relative_motion.length_squared() > 0.000001:
				fraction = clampf(-relative_start.dot(relative_motion) / relative_motion.length_squared(), 0.0, 1.0)
			global_position = from.lerp(to, fraction)
			_impact()
			return
		_previous_target_point = centre
	global_position = to
	global_rotation = _dir.angle()

# ------------------------------------------------------------------ برخورد

func _impact() -> void:
	if splash > 0.0:
		_splash_hit()
	elif target != null and is_instance_valid(target) and target.alive:
		_damage(target, damage_value)
		Fx.impact(_parent(), position, 0.42)
	else:
		Fx.smoke(_parent(), position, 0.55)
	_chain_hit()
	queue_free()

func _explode() -> void:
	if splash > 0.0:
		_splash_hit()
	queue_free()

func _splash_hit() -> void:
	var enemies: Array = _enemies()
	var from := position
	for enemy in enemies:
		if enemy == null or not is_instance_valid(enemy) or not enemy.alive:
			continue
		if enemy.position.distance_to(from) <= splash:
			_damage(enemy, damage_value)
	var size := clampf(splash / 34.0, 0.7, 2.2)
	Fx.blast(_parent(), from, size)
	Audio.play_combat_limited("combat/aircraft-hit", 160, 0.04, -13.0)

func _chain_hit() -> void:
	if chain <= 1:
		return
	var hit: Array = []
	if target != null and is_instance_valid(target):
		hit.append(target)
	var from := position
	var falloff := 0.75
	var jumps := chain - 1
	var reach := float(weapon.get("chain_range", 96.0))
	while jumps > 0:
		var next = _nearest(from, reach, hit)
		if next == null:
			break
		_damage(next, damage_value * falloff)
		Fx.bolt(_parent(), from, next.position, 0.7)
		Fx.burst(_parent(), "fx/spark", from, 0.7, 0.2, 1.5)
		from = next.position
		hit.append(next)
		falloff *= 0.75
		jumps -= 1

func _nearest(from: Vector2, reach: float, exclude: Array):
	var best = null
	var best_distance := reach
	for enemy in _enemies():
		if enemy == null or not is_instance_valid(enemy) or not enemy.alive:
			continue
		if exclude.has(enemy):
			continue
		var distance: float = enemy.position.distance_to(from)
		if distance < best_distance:
			best_distance = distance
			best = enemy
	return best

func _damage(enemy, amount: float) -> void:
	if enemy == null or not is_instance_valid(enemy) or not enemy.alive:
		return
	enemy.take_damage(amount, bool(weapon.get("armor_pierce", false)))
	if tower != null and is_instance_valid(tower):
		tower._apply_effects(enemy)
	Fx.text(_parent(), enemy.position + Vector2(randf_range(-8.0, 8.0), -30.0),
		Ui.fa(round(amount)), Color(1.0, 0.92, 0.75), 17)

func _enemies() -> Array:
	if tower != null and is_instance_valid(tower) and tower.battle != null:
		return tower.battle.enemies
	return []

func _parent() -> Node:
	if tower != null and is_instance_valid(tower) and tower.battle != null:
		return tower.battle.fx_layer()
	return get_parent()
