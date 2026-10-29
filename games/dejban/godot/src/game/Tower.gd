## برجک‌های دژبان: مسلسل، توپخانه، لیزر و تسلای پنج‌سطحی پایهٔ ثابت و
## نشانه‌گیری پیوسته دارند. مسیر شیت‌های قدیمی فقط برای سازگاری نگه‌داری شده است.
class_name Tower
extends Node2D

signal sold(tower)

const PIVOT_OFFSET := Vector2(0.0, -18.0)
## اندازهٔ فریم ۱۲۸ پیکسلی برجک ایزومتریک روی نقشه.
const ISO_SCALE := 0.78
## زمان هر گام ۴۵ درجه‌ای چرخش سر برجک ایزومتریک.
const ISO_TURN_STEP := 0.045
## فاصلهٔ جانبی دهانه‌ها برای سلاح چندشلیک (پیکسل فریم).
const ISO_BARREL_SPREAD := 5.0

var battle = null
var weapon: Dictionary = {}
var level := 1
var invested := 0
var selected := false

var cooldown_left := 0.0
var target = null
var aim_angle := 0.0

var _base: Sprite2D = null
var _flash: Node2D = null
var _flash_anim: AnimatedSprite2D = null
var _flash_time := 0.0
## مدت نمایش شعلهٔ دهانه؛ با شیت انیمیشنی طولانی‌تر از تصویر ایستاست.
var _flash_duration := 0.09
var _ring := 0.0
var _deploy_t := 0.0
var _shot_count := 0

## برجک ایزومتریک: نام برجک در پک (خالی پیش از setup)، جهت فعلی و خواستهٔ سر.
var iso_kind := ""
var head_dir: int = Weapons.ICON_DIR
var _wanted_dir: int = Weapons.ICON_DIR
var _head: Sprite2D = null
var _level_marker: Node2D = null
var _head_player: IsoSheet.Player = null
var _turn_t := 0.0
## Layered machine-gun/artillery visual; its base never follows the mount.
var _machinegun_visual: MachinegunVisual = null
var _tesla_visual: TeslaVisual = null
var _laser_visual: LaserVisual = null
var _laser_beam: LaserBeam = null
var _pending_laser_level := 0

func setup(weapon_def: Dictionary, battle_ref, start_level := 1) -> void:
	weapon = weapon_def
	battle = battle_ref
	level = clampi(start_level, 1, level_cap())
	# Draw upgrade markers over the shifted artwork, while keeping them inside
	# the 2×2 footprint.  The range/selection guide stays underneath the art.
	if _level_marker == null:
		_level_marker = Node2D.new()
		_level_marker.name = "LevelMarker"
		_level_marker.z_index = 4
		_level_marker.draw.connect(_draw_level_marker)
		add_child(_level_marker)
	_level_marker.queue_redraw()
	invested = int(weapon.get("cost", 0))
	_deploy_t = 0.0

	iso_kind = String(weapon.get("iso", ""))
	if is_laser():
		_setup_laser_visual()
	elif is_tesla():
		_setup_tesla_visual()
	elif is_layered_machinegun() or is_artillery():
		_setup_layered_visual()
	elif is_iso():
		_setup_iso()

	# Energy weapons use their own light/beam components, not bullet flashes.
	if is_tesla() or is_laser():
		return

	# شعلهٔ دهانه: اگر شیت کارتونی چهارفریمی موجود باشد همان را می‌چرخانیم،
	# وگرنه مثل قبل دو تصویر ایستای towers/muzzle_1 و _2 را یکی‌یکی نشان می‌دهیم.
	var flash_frames := Fx.sheet_frames("fx/muzzle_flash")
	if flash_frames != null:
		_flash_anim = AnimatedSprite2D.new()
		_flash_anim.sprite_frames = flash_frames
		_flash_anim.animation = "play"
		_flash_anim.visible = false
		_flash_anim.z_index = 3
		add_child(_flash_anim)
		_flash = _flash_anim
		# مدت نمایش: کل چهار فریم با ۳۰ فریم‌برثانیه ≈ ۰٫۱۳ ثانیه
		_flash_duration = float(flash_frames.get_frame_count("play")) / maxf(1.0, flash_frames.get_animation_speed("play"))
	else:
		var static_flash := Sprite2D.new()
		static_flash.texture = Assets.tex("towers/muzzle_1")
		static_flash.visible = false
		static_flash.z_index = 3
		add_child(static_flash)
		_flash = static_flash
		_flash_duration = 0.09

# ------------------------------------------------------------------ برجک ایزومتریک

func is_iso() -> bool:
	return not iso_kind.is_empty()

func is_layered_machinegun() -> bool:
	return String(weapon.get("visual", "")) == "machinegun_layers"

func is_artillery() -> bool:
	return String(weapon.get("visual", "")) == "artillery_layers"

func is_tesla() -> bool:
	return String(weapon.get("visual", "")) == "tesla_layers"

func is_laser() -> bool:
	return String(weapon.get("visual", "")) == "laser_layers"

func _setup_laser_visual() -> void:
	_laser_visual = LaserVisual.new()
	_laser_visual.name = "LaserVisual"
	_laser_visual.position = LaserVisual.FOOTPRINT_OFFSET
	add_child(_laser_visual)
	_laser_visual.setup(level)
	_base = _laser_visual.base
	_head = _laser_visual.head
	aim_angle = _laser_visual.forward_global().angle()

func _laser_is_active() -> bool:
	return _laser_beam != null and is_instance_valid(_laser_beam) \
		and not _laser_beam.is_queued_for_deletion() and not _laser_beam.finished

func _process_laser(delta: float) -> void:
	if _laser_visual == null:
		return
	# The artwork can settle to a sharp still view without moving the beam axis.
	_laser_visual.advance(delta)
	# A firing beam and its source never rotate while it is visible. Reload
	# begins only after it ends, and an upgrade takes effect on the next shot.
	if _laser_is_active():
		return
	if _pending_laser_level > 0:
		_laser_visual.set_level(_pending_laser_level)
		_pending_laser_level = 0
	cooldown_left = maxf(0.0, cooldown_left - maxf(delta, 0.0))
	if not _is_in_range(target) or not _enemies().has(target):
		target = _pick_target()
	if not _is_in_range(target):
		return
	var aim_point := Projectile.target_point_global(target)
	var local_vector := _laser_visual.to_local(aim_point) - _laser_visual.to_local(_laser_visual.pivot_global())
	_laser_visual.aim_at(local_vector, turn_speed(), delta)
	var pivot := _laser_visual.pivot_global()
	aim_angle = (to_local(pivot + _laser_visual.forward_global()) - to_local(pivot)).angle()
	if cooldown_left <= 0.0 and _laser_visual.is_aimed():
		_fire_laser()

func _setup_tesla_visual() -> void:
	_tesla_visual = TeslaVisual.new()
	_tesla_visual.name = "TeslaVisual"
	_tesla_visual.position = TeslaVisual.FOOTPRINT_OFFSET
	add_child(_tesla_visual)
	_tesla_visual.setup(level)
	_base = _tesla_visual.base
	_head = _tesla_visual.head
	aim_angle = _tesla_visual.forward_global().angle()

func _animate_tesla_visual(delta: float) -> void:
	_tesla_visual.advance(delta)
	if _is_in_range(target):
		var aim_point := Projectile.target_point_global(target)
		var screen_vector := _tesla_visual.to_local(aim_point) - _tesla_visual.to_local(_tesla_visual.pivot_global())
		_tesla_visual.aim_at(screen_vector, turn_speed(), delta)
	else:
		_tesla_visual.idle_turn(delta)
	var pivot := _tesla_visual.pivot_global()
	aim_angle = (to_local(pivot + _tesla_visual.forward_global()) - to_local(pivot)).angle()

func _setup_layered_visual() -> void:
	_machinegun_visual = ArtilleryVisual.new() if is_artillery() else MachinegunVisual.new()
	_machinegun_visual.name = "ArtilleryVisual" if is_artillery() else "MachinegunVisual"
	_machinegun_visual.position = MachinegunVisual.FOOTPRINT_OFFSET
	add_child(_machinegun_visual)
	_machinegun_visual.setup(level)
	_base = _machinegun_visual.base
	_head = _machinegun_visual.head
	aim_angle = _machinegun_visual.forward_global().angle()

## Machine guns aim at the enemy sprite's hit point. Artillery aims at its
## ground position, which is snapshotted as the shell's fixed destination.
func _animate_layered_visual(delta: float) -> void:
	_machinegun_visual.advance(delta)
	if _is_in_range(target):
		var aim_point: Vector2 = target.global_position if is_artillery() else Projectile.target_point_global(target)
		var screen_vector := _machinegun_visual.to_local(aim_point) - _machinegun_visual.to_local(_machinegun_visual.pivot_global())
		_machinegun_visual.aim_at(screen_vector, turn_speed(), delta)
	var pivot := _machinegun_visual.pivot_global()
	aim_angle = (to_local(pivot + _machinegun_visual.forward_global()) - to_local(pivot)).angle()

## پایهٔ ثابت و سرِ گردان؛ هر دو با نقطهٔ زمین فریم (۶۴، ۹۷) روی مبدأ برجک می‌نشینند.
func _setup_iso() -> void:
	var anchor := Vector2(IsoSheet.FRAME * 0.5, IsoSheet.TOWER_GROUND_Y)
	_base = Sprite2D.new()
	_base.texture = IsoSheet.base_texture(IsoSheet.tower_key(iso_kind, "idle"))
	_base.centered = false
	_base.offset = -anchor
	_base.scale = Vector2.ONE * ISO_SCALE
	_base.z_index = 1
	add_child(_base)
	_head = Sprite2D.new()
	_head.centered = false
	_head.offset = -anchor
	_head.scale = Vector2.ONE * ISO_SCALE
	_head.z_index = 2
	add_child(_head)
	_head_player = IsoSheet.Player.new(_head)
	_head_player.dir = head_dir
	_play_head("idle")

func _play_head(action: String, restart := false) -> void:
	if _head_player == null:
		return
	_head_player.play(IsoSheet.tower_key(iso_kind, action), action, IsoSheet.TOWER_ACTIONS.get(action, {}), restart)

## حالت فعلی سر برجک ایزومتریک: idle | aim | fire (برای تست بی‌صفحه).
func head_action() -> String:
	if _laser_visual != null:
		if _laser_is_active():
			return "fire"
		return "aim" if _is_in_range(target) else "idle"
	if _tesla_visual != null:
		if _tesla_visual.firing_left > 0.0:
			return "fire"
		return "aim" if _is_in_range(target) else "idle"
	if _machinegun_visual != null:
		if _machinegun_visual.firing_left > 0.0:
			return "fire"
		return "aim" if _is_in_range(target) else "idle"
	return "" if _head_player == null else _head_player.action

## زاویهٔ روی صفحهٔ لولهٔ جهت d (با همان فشردگی عمق شیت‌ها).
static func iso_dir_angle(d: int) -> float:
	var a := deg_to_rad(float(posmod(d, IsoSheet.DIRS)) * 45.0)
	return Vector2(cos(a), sin(a) * IsoSheet.DEPTH_SQUASH).angle()

## سر رو به هدف می‌چرخد (هر ISO_TURN_STEP یک گام ۴۵ درجه از کوتاه‌ترین سمت) و حالت
## idle/aim/fire را عوض می‌کند؛ پایه ثابت است و فقط هنگام کاشت یک‌بار بزرگ می‌شود.
func _animate_iso(delta: float) -> void:
	var has_target: bool = target != null and is_instance_valid(target)
	if has_target:
		_wanted_dir = IsoSheet.dir_from_vector(target.position - position, head_dir)
	if head_dir != _wanted_dir:
		_turn_t -= delta
		if _turn_t <= 0.0:
			head_dir = IsoSheet.step_toward(head_dir, _wanted_dir)
			_turn_t = ISO_TURN_STEP
	else:
		_turn_t = 0.0
	_head_player.set_dir(head_dir)
	aim_angle = iso_dir_angle(head_dir)

	if _head_player.action == "fire":
		if _head_player.finished():
			_play_head("aim" if has_target else "idle")
	elif has_target:
		_play_head("aim")
	else:
		_play_head("idle")
	_head_player.advance(delta)

	var pop := 1.0
	if _deploy_t < 1.0:
		pop = 1.0 + sin(_deploy_t * PI) * 0.25 * (1.0 - _deploy_t * 0.5)
	_base.scale = Vector2.ONE * ISO_SCALE * pop
	_head.scale = _base.scale

## برجک ایزومتریک فقط وقتی شلیک می‌کند که سرش رو به هدف رسیده باشد.
func _aimed() -> bool:
	if _laser_visual != null:
		return _laser_visual.is_aimed()
	if _tesla_visual != null:
		return _tesla_visual.is_aimed()
	if _machinegun_visual != null:
		return _machinegun_visual.is_aimed(0.002 if is_artillery() else 0.025)
	return not is_iso() or head_dir == _wanted_dir

# ------------------------------------------------------------------ دهانه

## نقطهٔ دهانهٔ لولهٔ شمارهٔ index نسبت به PIVOT_OFFSET (مختصات محلی برجک)، برای جهت فعلی سر؛
## شعله و گلوله دقیقاً از همان دهانه بیرون می‌آیند.
func _muzzle_local(index := 0) -> Vector2:
	if _laser_visual != null:
		return to_local(_laser_visual.muzzle_global()) - PIVOT_OFFSET
	if _tesla_visual != null:
		return to_local(_tesla_visual.emitter_global()) - PIVOT_OFFSET
	if _machinegun_visual != null:
		return to_local(_machinegun_visual.muzzle_global()) - PIVOT_OFFSET
	if is_iso() and _head != null:
		var tips: Array = IsoSheet.TOWER_MUZZLE.get(iso_kind, [])
		var tip: Vector2 = tips[head_dir] if head_dir < tips.size() else Vector2.ZERO
		if maxi(1, int(weapon.get("shots", 1))) > 1:
			var side := -1.0 if index % 2 == 0 else 1.0
			tip += Vector2.from_angle(aim_angle + PI * 0.5) * ISO_BARREL_SPREAD * side
		# نقطهٔ فریم (۶۴ + x، ۷۵ + y) نسبت به نقطهٔ زمین (۶۴، ۹۷) که روی مبدأ برجک است.
		var local := (tip + Vector2(0.0, IsoSheet.TOWER_PIVOT.y - IsoSheet.TOWER_GROUND_Y)) * _head.scale
		return local - PIVOT_OFFSET
	# برجکِ بدون setup (فقط در تست‌های منطق): دهانه کمی بالای مرکز
	return Vector2.ZERO

# ------------------------------------------------------------------ ویژگی‌های سطح

## Explicit tuning rows are optional; the other towers keep the original balance formulas.
static func level_config(weapon_def: Dictionary, tower_level: int) -> Dictionary:
	var levels: Array = weapon_def.get("levels", [])
	if levels.is_empty():
		return {}
	return levels[clampi(tower_level, 1, levels.size()) - 1]

func level_cap() -> int:
	var levels: Array = weapon.get("levels", [])
	return levels.size() if not levels.is_empty() else Balance.MAX_LEVEL

func damage() -> float:
	var stats := level_config(weapon, level)
	var base := float(stats.get("damage", float(weapon.get("damage", 10.0)) * Balance.level_damage_mult(level)))
	return base * Game.weapon_damage_mult(_weapon_id())

## The build preview and the actual tower use exactly the same range calculation.
static func range_for(weapon_def: Dictionary, level: int) -> float:
	var wid := String(weapon_def.get("id", ""))
	var stats := level_config(weapon_def, level)
	var base := float(stats.get("range", float(weapon_def.get("range", 160.0)) * Balance.level_range_mult(level)))
	return base * Game.weapon_range_mult(wid)

func attack_range() -> float:
	return range_for(weapon, level)

func interval() -> float:
	var stats := level_config(weapon, level)
	var base := float(stats.get("cooldown", float(weapon.get("cooldown", 0.8)) * Balance.level_rate_mult(level)))
	# The old 0.12s floor applies to the legacy towers only: it would otherwise
	# cancel the machine gun's high-level/armory rate upgrades.
	var floor_value := 0.01 if is_layered_machinegun() else 0.12
	return maxf(floor_value, base * Game.weapon_interval_mult(_weapon_id()))

## Explosion radius is in battlefield pixels and is snapshotted when fired.
func splash_radius() -> float:
	return maxf(0.0, float(level_config(weapon, level).get("splash", weapon.get("splash", 0.0))))

## Tesla uses a separate per-hop reach; the hard cap includes the first enemy.
func chain_radius() -> float:
	return maxf(0.0, float(level_config(weapon, level).get("chain_range", weapon.get("chain_range", 0.0))))

func tesla_chain_limit() -> int:
	return clampi(int(weapon.get("chain", 5)), 1, 5)

func turn_speed() -> float:
	return float(level_config(weapon, level).get("turn_speed", 3.6))

func laser_duration() -> float:
	return maxf(0.01, float(level_config(weapon, level).get("beam_duration", weapon.get("beam_duration", 0.32))))

func laser_width() -> float:
	return maxf(0.5, float(level_config(weapon, level).get("beam_width", weapon.get("beam_width", 3.6))))

func _weapon_id() -> String:
	return String(weapon.get("id", ""))

func upgrade_cost() -> int:
	return Balance.upgrade_cost(int(weapon.get("cost", 60)), level)

func max_level() -> bool:
	return level >= level_cap()

func sell_value() -> int:
	return int(round(invested * Balance.SELL_RATIO))

func upgrade() -> void:
	if max_level():
		return
	invested += upgrade_cost()
	level += 1
	if _machinegun_visual != null:
		_machinegun_visual.set_level(level)
	if _tesla_visual != null:
		_tesla_visual.set_level(level)
	if _laser_visual != null:
		if _laser_is_active():
			_pending_laser_level = level
		else:
			_laser_visual.set_level(level)
	_deploy_t = 0.25
	if _level_marker != null:
		_level_marker.queue_redraw()
	queue_redraw()

func set_selected(value: bool) -> void:
	selected = value
	queue_redraw()

# ------------------------------------------------------------------ چرخهٔ نبرد: پایه ثابت + چرخش و انیمیشن ۴ فریمی توپ بالایی

func _process(delta: float) -> void:
	if not is_laser():
		cooldown_left -= delta
	if _deploy_t < 1.0:
		_deploy_t = minf(1.0, _deploy_t + delta * 4.2)
	if _flash_time > 0.0:
		_flash_time -= delta
		if _flash_time <= 0.0 and _flash != null:
			_flash.visible = false
	if _ring > 0.0:
		_ring = maxf(0.0, _ring - delta)
		queue_redraw()

	if is_laser():
		_process_laser(delta)
		return

	# A live target removed from the battle (e.g. a tank after its keep attack)
	# must not be retained merely because its death animation still exists.
	if is_artillery() or is_tesla():
		# Re-evaluate each frame: artillery uses nearest, Tesla uses the enemy
		# furthest along the route. Both select only live enemies in range.
		target = _pick_target()
	elif not _is_in_range(target) or (is_layered_machinegun() and not _enemies().has(target)):
		var previous_target = target
		target = _pick_target()
		if is_layered_machinegun() and target != previous_target:
			# Time spent without this target must not turn into a burst backlog.
			cooldown_left = maxf(0.0, cooldown_left)

	if _tesla_visual != null:
		_animate_tesla_visual(delta)
	elif _machinegun_visual != null:
		_animate_layered_visual(delta)
	elif _head_player != null:
		_animate_iso(delta)

	if is_layered_machinegun():
		if not _is_in_range(target) and _machinegun_visual != null:
			_machinegun_visual.stop_firing()
		if not _is_in_range(target) or not _aimed():
			# Do not accumulate a backlog while idle or turning.
			cooldown_left = maxf(0.0, cooldown_left)
		else:
			# Preserve cadence even on slower frames / at accelerated game speed.
			var shots_this_frame := 0
			while cooldown_left <= 0.0 and shots_this_frame < 8 and _is_in_range(target):
				_fire()
				cooldown_left += interval()
				shots_this_frame += 1
			if shots_this_frame == 8:
				cooldown_left = maxf(0.0, cooldown_left)
	elif cooldown_left <= 0.0 and _is_in_range(target) and _aimed():
		_fire()
		cooldown_left = interval()

## آیا دشمن همین حالا داخل برد شلیک است؟ تنها سنجش برد در کل برجک همین است؛
## پس هدفی که از برد بیرون رفته باشد نه انتخاب می‌شود و نه رویش شلیک می‌شود.
func _is_in_range(enemy) -> bool:
	if enemy == null or not is_instance_valid(enemy) or enemy.is_queued_for_deletion() or not enemy.alive:
		return false
	return _distance_from_tower(enemy) <= attack_range()

## Layered energy towers resolve range in shared battlefield/FX coordinates.
## This remains correct when the world or enemy parents are transformed.
func _battle_space(world_point: Vector2) -> Vector2:
	if battle != null and is_instance_valid(battle):
		var layer = battle.fx_layer()
		if layer is Node2D:
			return (layer as Node2D).to_local(world_point)
	return world_point

func _distance_from_tower(enemy) -> float:
	if is_tesla() or is_laser():
		return _battle_space(enemy.global_position).distance_to(_battle_space(global_position))
	return enemy.position.distance_to(position)

## فهرست دشمنان زندهٔ میدان.
func _enemies() -> Array:
	if battle == null or not is_instance_valid(battle):
		return []
	return battle.enemies

func _pick_target():
	var candidates: Array = []
	for enemy in _enemies():
		if _is_in_range(enemy):
			candidates.append(enemy)
	if candidates.is_empty():
		return null
	var mode := String(weapon.get("targeting", "first"))
	var best = candidates[0]
	for enemy in candidates:
		match mode:
			"strong":
				if enemy.hp > best.hp:
					best = enemy
			"weak":
				if enemy.hp < best.hp:
					best = enemy
			"close":
				if _distance_from_tower(enemy) < _distance_from_tower(best):
					best = enemy
			_:
				if enemy.progress > best.progress:
					best = enemy
	return best

# ------------------------------------------------------------------ شلیک

func _fire() -> void:
	if is_laser():
		_fire_laser()
		return
	if is_tesla():
		_fire_tesla()
		return
	var kind := String(weapon.get("kind", "single"))
	if _machinegun_visual != null:
		_machinegun_visual.fired()
	elif is_iso():
		_play_head("fire", true)
	_show_flash()
	if is_layered_machinegun() and _machinegun_visual != null:
		_eject_casing()
	Audio.play_weapon(_sound_key())
	match kind:
		"aura":
			_ring = 0.30
			queue_redraw()
			for enemy in _enemies():
				if enemy == null or not is_instance_valid(enemy) or not enemy.alive:
					continue
				if enemy.position.distance_to(position) <= attack_range():
					_hit(enemy, damage())
			Fx.ring(_fx_parent(), position, attack_range() * 0.55, Color(1, 1, 1, 0.35), 0.35)
		_:
			# سلاح چندشلیک در هر نوبت چند گلوله می‌زند؛ اگر دشمن کمتری در برد باشد،
			# گلوله‌های اضافی به همان دشمن هدف‌گیری می‌شوند.
			var shots := maxi(1, int(weapon.get("shots", 1)))
			var targets := _shot_targets(shots)
			if not targets.is_empty():
				var first = targets[0]
				while targets.size() < shots:
					targets.append(first)
			for i in targets.size():
				_spawn_projectile(targets[i], i)

## Start a fixed snapshot of the ray, lifetime, damage and reload. It damages
## real enemies using the shared projectile hit circles, not a visible-only FX.
func _fire_laser() -> void:
	if _laser_visual == null or _laser_is_active() or cooldown_left > 0.0 \
			or not _is_in_range(target) or not _enemies().has(target):
		return
	var parent := _fx_parent()
	if parent == null or not is_instance_valid(parent) or not (parent is Node2D):
		return
	var ray := LaserBeam.new()
	parent.add_child(ray)
	_laser_beam = ray
	if not ray.setup(_laser_visual.muzzle_global(), _laser_visual.forward_global(), battle, damage(),
			bool(weapon.get("armor_pierce", false)), laser_duration(), laser_width(), level, Balance.BUILD_RECT):
		return
	_shot_count += 1
	cooldown_left = interval()
	Audio.play_weapon(_sound_key())

## Resolve direct lightning immediately against real enemies. At each step the
## next target is the nearest *unhit* live enemy inside both tower range and
## the current victim's hop radius. The snapshot survives synchronous death
## signals removing entries from Battle.enemies, while the live membership
## check prevents striking an enemy removed by another signal.
func _fire_tesla() -> void:
	if _tesla_visual == null or not _is_in_range(target) or not _enemies().has(target):
		return
	var candidates: Array = _enemies().duplicate()
	var hit: Array = []
	var points := PackedVector2Array([_tesla_visual.emitter_global()])
	var next_enemy = target
	var factor := 1.0
	var base_damage := damage()
	if base_damage <= 0.0:
		return
	var falloff := clampf(float(weapon.get("chain_falloff", 1.0)), 0.0, 1.0)
	var radius := chain_radius()
	var parent := _fx_parent()
	_shot_count += 1
	_tesla_visual.fired()
	Audio.play_weapon(_sound_key())
	while next_enemy != null and hit.size() < tesla_chain_limit():
		if not _is_in_range(next_enemy) or not _enemies().has(next_enemy) or hit.has(next_enemy):
			break
		var enemy = next_enemy
		var last_ground := _battle_space(enemy.global_position)
		var impact := Projectile.target_point_global(enemy)
		var old_hp: float = enemy.hp
		hit.append(enemy)
		points.append(impact)
		var amount := base_damage * factor
		# Enemy.take_damage enforces a minimum of one; never call it for zero.
		if amount > 0.0:
			enemy.set_meta(TeslaArc.DEATH_FX_CONTEXT, true)
			_hit(enemy, amount)
			if is_instance_valid(enemy):
				enemy.remove_meta(TeslaArc.DEATH_FX_CONTEXT)
			if is_instance_valid(enemy) and parent != null and is_instance_valid(parent):
				var actual := maxf(0.0, old_hp - float(enemy.hp))
				var label_pos := (parent as Node2D).to_local(impact) if parent is Node2D else impact
				Fx.text(parent, label_pos + Vector2(0.0, -18.0), Ui.fa(round(actual)), Color(0.60, 0.94, 1.0), 17)
		if hit.size() >= tesla_chain_limit() or radius <= 0.0 or factor <= 0.0 or falloff <= 0.0:
			break
		next_enemy = null
		var best_distance_sq := radius * radius
		# Rebuild an O(1) live-membership set after each death signal. Using
		# Array.has for every candidate would become quadratic in large waves.
		var live_ids: Dictionary = {}
		for current in _enemies():
			if current != null and is_instance_valid(current):
				live_ids[current.get_instance_id()] = true
		for candidate in candidates:
			if candidate == null or not is_instance_valid(candidate) or hit.has(candidate):
				continue
			if not live_ids.has(candidate.get_instance_id()) or not _is_in_range(candidate):
				continue
			var distance_sq := _battle_space(candidate.global_position).distance_squared_to(last_ground)
			if distance_sq <= best_distance_sq and (next_enemy == null or distance_sq < best_distance_sq):
				best_distance_sq = distance_sq
				next_enemy = candidate
		factor *= falloff
	if points.size() > 1 and parent != null and is_instance_valid(parent):
		var arc := TeslaArc.new()
		parent.add_child(arc)
		arc.setup(points, level)

func _shot_targets(count: int) -> Array:
	var out: Array = []
	if _is_in_range(target):
		out.append(target)
	if count > 1:
		# تیرهای اضافی سلاح‌های چندشلیک هم فقط تا خودِ بردِ شلیک می‌روند؛ پیش‌تر
		# ۵٪ بیشتر اجازه داده می‌شد و گلوله بیرون از دایرهٔ برد اصابت می‌کرد.
		var others: Array = []
		for enemy in _enemies():
			if out.has(enemy) or not _is_in_range(enemy):
				continue
			others.append(enemy)
		others.sort_custom(func(a, b): return a.progress > b.progress)
		for enemy in others:
			if out.size() >= count:
				break
			out.append(enemy)
	return out

func _spawn_projectile(shot_target, index := 0) -> void:
	if shot_target == null or not is_instance_valid(shot_target):
		return
	var projectile = ArtilleryShell.new() if is_artillery() else Projectile.new()
	var direction := _machinegun_visual.forward_global() if _machinegun_visual != null else Vector2.ZERO
	projectile.setup(self, shot_target, damage(), direction)
	_fx_parent().add_child(projectile)
	projectile.global_position = to_global(PIVOT_OFFSET + _muzzle_local(index))
	if direction.length_squared() > 0.0:
		projectile.global_rotation = direction.angle()

func _eject_casing() -> void:
	var casing := ShellCasing.new()
	_fx_parent().add_child(casing)
	casing.global_position = _machinegun_visual.casing_global()
	casing.launch(_machinegun_visual.forward_global(), _shot_count)

## آسیب یک ضربه روی یک دشمن، همراه با اثرهای سلاح.
func _hit(enemy, amount: float) -> void:
	if enemy == null or not is_instance_valid(enemy) or not enemy.alive:
		return
	enemy.take_damage(amount, bool(weapon.get("armor_pierce", false)))
	_apply_effects(enemy)

func _apply_effects(enemy) -> void:
	if enemy == null or not is_instance_valid(enemy) or not enemy.alive:
		return
	if weapon.has("slow"):
		enemy.apply_slow(float(weapon["slow"]) * (1.0 + 0.05 * (level - 1)), float(weapon.get("slow_time", 2.0)))
	if weapon.has("burn"):
		enemy.apply_burn(float(weapon["burn"]) * (1.0 + 0.18 * (level - 1)), float(weapon.get("burn_time", 3.0)))
	if weapon.has("poison"):
		enemy.apply_poison(float(weapon["poison"]) * (1.0 + 0.18 * (level - 1)), float(weapon.get("poison_time", 4.0)))

## هر برجک صدای شلیک خودش را دارد (کلید «sound» در Weapons).
func _sound_key() -> String:
	return String(weapon.get("sound", "shot_bolt"))

func _fx_parent() -> Node:
	if battle == null:
		return get_parent()
	return battle.fx_layer()

func _show_flash() -> void:
	_shot_count += 1
	if _flash == null:
		return
	_flash.visible = true
	_flash.rotation = aim_angle
	_flash.position = PIVOT_OFFSET + _muzzle_local(_shot_count)
	if _flash_anim != null:
		# شیت افکت اندازهٔ ثابت ۶۴ پیکسلی دارد؛ با سطح کمی بزرگ‌تر می‌شود.
		var flash_size := (0.52 + 0.04 * float(level - 1)) if is_layered_machinegun() else (0.92 + 0.06 * float(level - 1))
		_flash_anim.scale = Vector2.ONE * flash_size
		if _machinegun_visual != null:
			_flash_anim.stop()
			_flash_anim.frame = 0
		_flash_anim.play("play")
	else:
		var still := _flash as Sprite2D
		still.texture = Assets.tex("towers/muzzle_2" if (_shot_count % 2 == 0) else "towers/muzzle_1")
		still.scale = Vector2.ONE * (1.12 + 0.05 * float(level - 1))
	_flash_time = _flash_duration

func _draw_level_marker() -> void:
	if level <= 1:
		return
	var count: int = level
	var step := 10.5
	var start_x := -0.5 * step * float(count - 1)
	for i in count:
		var c := Vector2(start_x + float(i) * step, Balance.TOWER_SIZE * 0.5 - 5.0)
		_level_marker.draw_circle(c, 4.4, Color(0.06, 0.08, 0.10, 0.90))
		_level_marker.draw_circle(c, 2.9, Color(1.0, 0.82, 0.24, 1.0))

func _draw() -> void:
	if not selected:
		return
	var reach := attack_range()
	var half: float = Balance.TOWER_SIZE * 0.5
	draw_rect(Rect2(Vector2(-half, -half), Vector2.ONE * Balance.TOWER_SIZE),
		Color(1.0, 0.84, 0.32, 0.90), false, 2.5, true)
	draw_circle(Vector2.ZERO, reach, Color(0.35, 0.75, 0.85, 0.10))
	draw_arc(Vector2.ZERO, reach, 0.0, TAU, 48, Color(0.45, 0.85, 0.95, 0.75), 2.0, true)
	if _ring > 0.0:
		draw_arc(Vector2.ZERO, reach * (1.0 - _ring), 0.0, TAU, 40, Color(1, 1, 1, _ring * 2.4), 3.0, true)
