## سنگر دژبان: سنگر کیسه‌شنی (`_base`) با یک سرباز تیرانداز به ازای هر سطح ارتقا.
## سربازها فرزند `_base` هستند تا با تغییر جهت و تکان شلیک همراه سنگر بچرخند؛
## هر سرباز در هر شلیک یک گلوله می‌زند و شعلهٔ دهانهٔ تفنگ از همان سرباز بیرون می‌آید.
class_name Tower
extends Node2D

signal sold(tower)

const PIVOT_OFFSET := Vector2(0.0, -18.0)
## نوک لولهٔ تفنگ سرباز نسبت به مرکز سنگر (در حالت رو به راست؛ با جهت، قرینه می‌شود).
const MUZZLE_OFFSET := Vector2(50.0, -8.0)
## اندازهٔ هر سرباز و فاصلهٔ افقی آن‌ها در مختصات سنگر (پیش از مقیاس برجک).
const SOLDIER_HEIGHT := 40.0
const SOLDIER_SPACING := 26.0
## خط دیوار کیسه‌شن جلو (بالای آن) نسبت به مرکز سنگر؛ پای سرباز روی این خط می‌نشیند.
const SOLDIER_WALL_Y := -6.0
## نوک لوله نسبت به مرکز سرباز، به کسری از اندازهٔ تصویر سرباز.
const RIFLE_TIP_FRACTION := Vector2(0.48, 0.12)

var battle = null
var weapon: Dictionary = {}
var level := 1
var invested := 0
var selected := false

var cooldown_left := 0.0
var target = null
var aim_angle := 0.0

var _base: Sprite2D = null
var _soldiers: Array[Sprite2D] = []
var _flash: Node2D = null
var _flash_anim: AnimatedSprite2D = null
var _flash_time := 0.0
## مدت نمایش شعلهٔ دهانه؛ با شیت انیمیشنی طولانی‌تر از تصویر ایستاست.
var _flash_duration := 0.09
var _recoil := 0.0
var _ring := 0.0
var _idle_phase := 0.0
var _deploy_t := 0.0
var _facing := 1.0
var _sign_x := 1.0
var _shot_count := 0

func setup(weapon_def: Dictionary, battle_ref, start_level := 1) -> void:
	weapon = weapon_def
	battle = battle_ref
	level = clamp(start_level, 1, Balance.MAX_LEVEL)
	invested = int(weapon.get("cost", 0))
	_idle_phase = randf() * TAU
	_deploy_t = 0.0

	var wid := _weapon_id()

	# تصویر یکپارچهٔ سنگر (کیسه‌شن + سرباز داخل آن)
	_base = Sprite2D.new()
	_base.texture = Assets.tex(Weapons.art_key(wid))
	_base.position = PIVOT_OFFSET
	_base.z_index = 1
	add_child(_base)
	_sync_soldiers()

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

# ------------------------------------------------------------------ سربازان سنگر

## تعداد سربازان داخل سنگر برابر سطح ارتقای فعلی است (سطح ۱ = یک سرباز تا سطح ۵ = پنج سرباز).
func soldier_count() -> int:
	return level

## سربازهای داخل سنگر را با سطح هم‌اندازه می‌کند و همه را به‌طور متقارن در دهانهٔ سنگر می‌چیند.
## اگر سنگر هنوز ساخته نشده باشد (مثلاً در تست‌های بدون صحنه) کاری نمی‌کند.
func _sync_soldiers() -> void:
	if _base == null:
		return
	var tex := Assets.tex("towers/sangar_soldier")
	if tex == null:
		return
	var want := soldier_count()
	while _soldiers.size() < want:
		var soldier := Sprite2D.new()
		soldier.texture = tex
		soldier.scale = Vector2.ONE * (SOLDIER_HEIGHT / float(tex.get_height()))
		_base.add_child(soldier)
		_soldiers.append(soldier)
	while _soldiers.size() > want:
		var extra: Sprite2D = _soldiers.pop_back()
		extra.queue_free()
	for i in _soldiers.size():
		_soldiers[i].position = Vector2((float(i) - float(want - 1) * 0.5) * SOLDIER_SPACING,
			SOLDIER_WALL_Y - SOLDIER_HEIGHT * 0.5)

## نقطهٔ نوک لولهٔ سرباز شمارهٔ index نسبت به PIVOT_OFFSET (مختصات محلی برجک).
## با جهت سنگر و تکان شلیک همراه است، پس شعله و گلوله دقیقاً از تفنگ همان سرباز بیرون می‌آیند.
func _muzzle_local(index := 0) -> Vector2:
	if _base == null or _soldiers.is_empty():
		return Vector2(MUZZLE_OFFSET.x * _sign_x, MUZZLE_OFFSET.y)
	var soldier: Sprite2D = _soldiers[index % _soldiers.size()]
	var size := soldier.texture.get_size() * soldier.scale
	var tip := soldier.position + Vector2(RIFLE_TIP_FRACTION.x * size.x, RIFLE_TIP_FRACTION.y * size.y)
	return (_base.position - PIVOT_OFFSET) + tip * _base.scale

# ------------------------------------------------------------------ ویژگی‌های سطح

func damage() -> float:
	return float(weapon.get("damage", 10.0)) * Balance.level_damage_mult(level) * Game.weapon_damage_mult(_weapon_id())

## برد شلیک یک سلاح در سطح داده‌شده. هم خود برجک و هم دایرهٔ پیش‌نمایش پیش از
## ساخت، از همین یک حساب استفاده می‌کنند تا بردِ نشان‌داده‌شده با برد واقعی یکی باشد.
static func range_for(weapon_def: Dictionary, level: int) -> float:
	var wid := String(weapon_def.get("id", "sangar"))
	return float(weapon_def.get("range", 160.0)) * Balance.level_range_mult(level) * Game.weapon_range_mult(wid)

func attack_range() -> float:
	return range_for(weapon, level)

func interval() -> float:
	return maxf(0.12, float(weapon.get("cooldown", 0.8)) * Balance.level_rate_mult(level) * Game.weapon_interval_mult(_weapon_id()))

func _weapon_id() -> String:
	return String(weapon.get("id", "sangar"))

func upgrade_cost() -> int:
	return Balance.upgrade_cost(int(weapon.get("cost", 60)), level)

func max_level() -> bool:
	return level >= Balance.MAX_LEVEL

func sell_value() -> int:
	return int(round(invested * Balance.SELL_RATIO))

func upgrade() -> void:
	if max_level():
		return
	invested += upgrade_cost()
	level += 1
	_sync_soldiers()
	_deploy_t = 0.25
	queue_redraw()

func set_selected(value: bool) -> void:
	selected = value
	queue_redraw()

# ------------------------------------------------------------------ چرخهٔ نبرد: پایه ثابت + چرخش و انیمیشن ۴ فریمی توپ بالایی

func _process(delta: float) -> void:
	cooldown_left -= delta
	_idle_phase += delta * 2.8
	if _deploy_t < 1.0:
		_deploy_t = minf(1.0, _deploy_t + delta * 4.2)
	if _flash_time > 0.0:
		_flash_time -= delta
		if _flash_time <= 0.0 and _flash != null:
			_flash.visible = false
	if _recoil > 0.0:
		_recoil = maxf(0.0, _recoil - delta * 4.8)
	if _ring > 0.0:
		_ring = maxf(0.0, _ring - delta)
		queue_redraw()

	if not _is_in_range(target):
		target = _pick_target()

	if target != null and is_instance_valid(target):
		var to_target: Vector2 = (target.position - Vector2(0.0, 12.0)) - (position + PIVOT_OFFSET)
		var desired_angle := to_target.angle()
		aim_angle = lerp_angle(aim_angle, desired_angle, clampf(delta * 13.0, 0.0, 1.0))
	else:
		# دیدبانی فعال به چپ و راست و بالا و پایین زمانی که دشمنی در برد نیست
		var patrol_dir := 0.0 if sin(_idle_phase * 0.28) >= 0.0 else PI
		var patrol_pitch := sin(_idle_phase * 0.9) * 0.42
		aim_angle = lerp_angle(aim_angle, patrol_dir + patrol_pitch, clampf(delta * 3.8, 0.0, 1.0))

	var aim_cos := cos(aim_angle)
	if absf(aim_cos) > 0.06:
		var target_dir := -1.0 if aim_cos < 0.0 else 1.0
		_facing = lerpf(_facing, target_dir, clampf(delta * 13.0, 0.0, 1.0))
	var sign_x := -1.0 if _facing < 0.0 else 1.0
	_sign_x = sign_x

	var base_scale := 1.0 + 0.05 * float(level - 1)
	var pop := 1.0
	if _deploy_t < 1.0:
		pop = 1.0 + sin(_deploy_t * PI) * 0.25 * (1.0 - _deploy_t * 0.5)

	# سنگر کامل: با تغییر جهت هدف آینه می‌شود (سرباز به سمت دشمن برمی‌گردد)،
	# با نفس‌کشیدن آرام نوسان می‌کند و با هر شلیک فشرده و کمی به عقب تکان می‌خورد.
	if _base != null:
		var base_shock := sin(_recoil * PI) * 0.035
		var breath := sin(_idle_phase * 1.3) * 0.018
		var flip_smooth := maxf(0.28, absf(_facing))
		var sx := base_scale * pop * (1.0 + base_shock - breath * 0.4)
		var sy := base_scale * pop * (1.0 - base_shock + breath)
		_base.rotation = 0.0
		_base.scale = Vector2(sx * sign_x * flip_smooth, sy)
		var recoil_offset := Vector2(-sign_x * _recoil * 2.5, 0.0)
		var turn_lift := Vector2(0.0, -(1.0 - absf(_facing)) * 2.2)
		_base.position = PIVOT_OFFSET + recoil_offset + turn_lift

	if cooldown_left <= 0.0 and _is_in_range(target):
		_fire()
		cooldown_left = interval()

## آیا دشمن همین حالا داخل برد شلیک است؟ تنها سنجش برد در کل برجک همین است؛
## پس هدفی که از برد بیرون رفته باشد نه انتخاب می‌شود و نه رویش شلیک می‌شود.
func _is_in_range(enemy) -> bool:
	if enemy == null or not is_instance_valid(enemy) or not enemy.alive:
		return false
	return enemy.position.distance_to(position) <= attack_range()

## فهرست دشمنان زندهٔ میدان.
func _enemies() -> Array:
	if battle == null:
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
				if enemy.position.distance_to(position) < best.position.distance_to(position):
					best = enemy
			_:
				if enemy.progress > best.progress:
					best = enemy
	return best

# ------------------------------------------------------------------ شلیک

func _fire() -> void:
	var kind := String(weapon.get("kind", "single"))
	_show_flash()
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
			# هر سرباز داخل سنگر یک گلوله در هر شلیک می‌زند؛ اگر دشمن کمتری در برد باشد،
			# گلوله‌های اضافی به همان دشمن هدف‌گیری می‌شوند.
			var shots := maxi(1, int(weapon.get("shots", 1))) * maxi(1, soldier_count())
			var targets := _shot_targets(shots)
			if not targets.is_empty():
				var first = targets[0]
				while targets.size() < shots:
					targets.append(first)
			for i in targets.size():
				_spawn_projectile(targets[i], i)

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
	var projectile = Projectile.new()
	projectile.setup(self, shot_target, damage())
	_fx_parent().add_child(projectile)
	projectile.global_position = position + PIVOT_OFFSET + _muzzle_local(index)

## نقطهٔ لولهٔ تفنگ نسبت به مرکز سنگر، با توجه به جهت سرباز.
func _muzzle_local() -> Vector2:
	return Vector2(MUZZLE_OFFSET.x * _sign_x, MUZZLE_OFFSET.y)

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

## سرباز تفنگ‌دار با صدای تیر خشک شلیک می‌کند.
func _sound_key() -> String:
	return "shot_bolt"

func _fx_parent() -> Node:
	if battle == null:
		return get_parent()
	return battle.fx_layer()

func _show_flash() -> void:
	_recoil = 1.0
	_shot_count += 1
	if _flash == null:
		return
	_flash.visible = true
	_flash.rotation = aim_angle
	_flash.position = PIVOT_OFFSET + _muzzle_local(_shot_count)
	if _flash_anim != null:
		# شیت افکت اندازهٔ ثابت ۶۴ پیکسلی دارد؛ با سطح کمی بزرگ‌تر می‌شود.
		_flash_anim.scale = Vector2.ONE * (0.92 + 0.06 * float(level - 1))
		_flash_anim.play("play")
	else:
		var still := _flash as Sprite2D
		still.texture = Assets.tex("towers/muzzle_2" if (_shot_count % 2 == 0) else "towers/muzzle_1")
		still.scale = Vector2.ONE * (1.12 + 0.05 * float(level - 1))
	_flash_time = _flash_duration

func _draw() -> void:
	# سایهٔ بیضی زیر پایهٔ ثابت برجک
	draw_set_transform(Vector2(0.0, 4.0), 0.0, Vector2(1.0, 0.45))
	draw_circle(Vector2.ZERO, Balance.TOWER_RADIUS + 4.0, Color(0, 0, 0, 0.32))
	draw_set_transform(Vector2.ZERO, 0.0, Vector2.ONE)
	if not selected:
		return
	var reach := attack_range()
	draw_circle(Vector2.ZERO, reach, Color(0.35, 0.75, 0.85, 0.10))
	draw_arc(Vector2.ZERO, reach, 0.0, TAU, 48, Color(0.45, 0.85, 0.95, 0.75), 2.0, true)
	if _ring > 0.0:
		draw_arc(Vector2.ZERO, reach * (1.0 - _ring), 0.0, TAU, 40, Color(1, 1, 1, _ring * 2.4), 3.0, true)
