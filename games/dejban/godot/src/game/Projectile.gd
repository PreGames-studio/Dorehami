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
var _sprite: Sprite2D = null

func setup(source_tower, shot_target, amount: float) -> void:
	tower = source_tower
	weapon = source_tower.weapon if source_tower != null else {}
	damage_value = amount
	target = shot_target
	speed = float(weapon.get("bullet_speed", 520.0))
	splash = float(weapon.get("splash", 0.0))
	chain = int(weapon.get("chain", 0))
	if shot_target != null and is_instance_valid(shot_target):
		last_target_pos = shot_target.position

	_sprite = Sprite2D.new()
	_sprite.texture = Assets.tex("bullets/%s" % String(weapon.get("bullet", "stone")))
	_sprite.z_index = 12
	add_child(_sprite)
	z_index = 12
	rotation = 0.0

func _process(delta: float) -> void:
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

# ------------------------------------------------------------------ برخورد

func _impact() -> void:
	if splash > 0.0:
		_splash_hit()
	elif target != null and is_instance_valid(target) and target.alive:
		_damage(target, damage_value)
		Fx.burst(_parent(), "fx/spark", position, 0.5, 0.22, 1.4)
	else:
		Fx.burst(_parent(), "fx/smoke_1", position, 0.5, 0.3, 1.2)
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
	Fx.explosion(_parent(), from, size)
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
