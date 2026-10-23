## برجک دژبان.
##
## سنگر: سنگر کیسه‌شنی (`_base`) با یک سرباز تیرانداز به ازای هر سطح ارتقا.
## سربازها فرزند `_base` هستند تا با تغییر جهت و تکان شلیک همراه سنگر بچرخند؛
## هر سرباز در هر شلیک یک گلوله می‌زند و شعلهٔ دهانهٔ تفنگ از همان سرباز بیرون می‌آید.
##
## برجک ایزومتریک (سلاحی که کلید «iso» دارد): پایهٔ ثابت (`_base`، از IsoSheet.base_texture)
## و سرِ گردان (`_head`) از شیت‌های پک towers_iso. سر در گام‌های ۴۵ درجه رو به هدف می‌چرخد
## (ردیف شیت، نه چرخش پیوسته)؛ بدون هدف «idle»، با هدف «aim» و در هر شلیک یک‌بار «fire».
## شعلهٔ دهانه و گلوله همان سامانهٔ مشترک پایین‌اند و از دهانهٔ همان جهت بیرون می‌آیند.
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
var _soldiers: Array[Sprite2D] = []
var _flash: Node2D = null
var _flash_anim: AnimatedSprite2D = null
var _flash_time := 0.0
## مدت نمایش شعلهٔ دهانه؛ با شیت انیمیشنی طولانی‌تر از تصویر ایستاست.
var _flash_duration := 0.09
## پس‌زدن هر سرباز جدا: هنگام شلیک همان سرباز فشرده می‌شود و کم‌کم برمی‌گردد.
var _soldier_kick: Array[float] = []
var _ring := 0.0
var _idle_phase := 0.0
var _deploy_t := 0.0
var _facing := 1.0
var _sign_x := 1.0
var _shot_count := 0

## برجک ایزومتریک: نام برجک در پک (خالی برای سنگر)، جهت فعلی و خواستهٔ سر.
var iso_kind := ""
var head_dir := Weapons.ICON_DIR
var _wanted_dir := Weapons.ICON_DIR
var _head: Sprite2D = null
var _head_player: IsoSheet.Player = null
var _turn_t := 0.0

func setup(weapon_def: Dictionary, battle_ref, start_level := 1) -> void:
	weapon = weapon_def
	battle = battle_ref
	level = clamp(start_level, 1, Balance.MAX_LEVEL)
	invested = int(weapon.get("cost", 0))
	_idle_phase = randf() * TAU
	_deploy_t = 0.0

	var wid := _weapon_id()
	iso_kind = String(weapon.get("iso", ""))

	if is_iso():
		_setup_iso()
	else:
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

# ------------------------------------------------------------------ برجک ایزومتریک

func is_iso() -> bool:
	return not iso_kind.is_empty()

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

## برجک ایزومتریک فقط وقتی شلیک می‌کند که سرش رو به هدف رسیده باشد؛ سنگر همیشه آماده است.
func _aimed() -> bool:
	return not is_iso() or head_dir == _wanted_dir

# ------------------------------------------------------------------ سربازان سنگر

## تعداد سربازان داخل سنگر برابر سطح ارتقای فعلی است (سطح ۱ = یک سرباز تا سطح ۴ = چهار سرباز).
## برجک ایزومتریک سرباز ندارد و با ارتقا قوی‌تر می‌شود، نه پرشلیک‌تر.
func soldier_count() -> int:
	return 0 if is_iso() else level

## جای ثابت سرباز شمارهٔ i از n سرباز، متقارن در دهانهٔ سنگر.
func _soldier_home(i: int, n: int) -> Vector2:
	return Vector2((float(i) - float(n - 1) * 0.5) * SOLDIER_SPACING, SOLDIER_WALL_Y - SOLDIER_HEIGHT * 0.5)

## سربازهای داخل سنگر را با سطح هم‌اندازه می‌کند و همه را به‌طور متقارن در دهانهٔ سنگر می‌چیند.
## اگر سنگر هنوز ساخته نشده باشد (مثلاً در تست‌های بدون صحنه) کاری نمی‌کند.
func _sync_soldiers() -> void:
	if _base == null or is_iso():
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
		_soldier_kick.append(0.0)
	while _soldiers.size() > want:
		var extra: Sprite2D = _soldiers.pop_back()
		extra.queue_free()
		_soldier_kick.pop_back()
	for i in _soldiers.size():
		_soldiers[i].position = _soldier_home(i, want)

## نفس‌کشیدن آرام، رو به دشمن چرخیدن و پس‌زدن هر سرباز. سربازها با فاز متفاوت حرکت می‌کنند تا یکنواخت نباشند.
func _animate_soldiers(delta: float, sign_x: float) -> void:
	var n := _soldiers.size()
	var flip := maxf(0.28, absf(_facing))
	for i in n:
		var soldier: Sprite2D = _soldiers[i]
		if soldier == null or soldier.texture == null:
			continue
		var kick := _soldier_kick[i] if i < _soldier_kick.size() else 0.0
		if i < _soldier_kick.size():
			_soldier_kick[i] = maxf(0.0, kick - delta * 5.0)
		var phase := _idle_phase + float(i) * 1.9
		var breath := sin(phase * 1.3) * 0.025
		var base_s := SOLDIER_HEIGHT / float(soldier.texture.get_height())
		soldier.scale = Vector2(base_s * sign_x * flip * (1.0 - kick * 0.06), base_s * (1.0 + breath - kick * 0.04))
		soldier.position = _soldier_home(i, n) + Vector2(-sign_x * kick * 3.0, kick * 1.5)

## پس‌زدن همان سربازی که گلوله‌اش را شلیک کرد.
func _kick_soldier(index: int) -> void:
	if _soldier_kick.is_empty():
		return
	_soldier_kick[index % _soldier_kick.size()] = 1.0

## نقطهٔ نوک لولهٔ سرباز شمارهٔ index نسبت به PIVOT_OFFSET (مختصات محلی برجک).
## با جهت سنگر و تکان شلیک همراه است، پس شعله و گلوله دقیقاً از تفنگ همان سرباز بیرون می‌آیند.
func _muzzle_local(index := 0) -> Vector2:
	if is_iso() and _head != null:
		var tips: Array = IsoSheet.TOWER_MUZZLE.get(iso_kind, [])
		var tip: Vector2 = tips[head_dir] if head_dir < tips.size() else Vector2.ZERO
		if maxi(1, int(weapon.get("shots", 1))) > 1:
			var side := -1.0 if index % 2 == 0 else 1.0
			tip += Vector2.from_angle(aim_angle + PI * 0.5) * ISO_BARREL_SPREAD * side
		# نقطهٔ فریم (۶۴ + x، ۷۵ + y) نسبت به نقطهٔ زمین (۶۴، ۹۷) که روی مبدأ برجک است.
		var local := (tip + Vector2(0.0, IsoSheet.TOWER_PIVOT.y - IsoSheet.TOWER_GROUND_Y)) * _head.scale
		return local - PIVOT_OFFSET
	if _base == null or _soldiers.is_empty():
		return Vector2(MUZZLE_OFFSET.x * _sign_x, MUZZLE_OFFSET.y)
	var soldier: Sprite2D = _soldiers[index % _soldiers.size()]
	# کسرِ تصویر سرباز از گوشهٔ بالا-چپ است؛ سرباز مرکزی کشیده می‌شود و ممکن است آینه باشد.
	var size := soldier.texture.get_size() * soldier.scale.abs()
	var mirror := signf(soldier.scale.x)
	var tip := soldier.position + Vector2((RIFLE_TIP_FRACTION.x - 0.5) * size.x * mirror, (RIFLE_TIP_FRACTION.y - 0.5) * size.y)
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
	if _ring > 0.0:
		_ring = maxf(0.0, _ring - delta)
		queue_redraw()

	if not _is_in_range(target):
		target = _pick_target()

	if is_iso():
		_animate_iso(delta)
	else:
		_animate_sangar(delta)

	if cooldown_left <= 0.0 and _is_in_range(target) and _aimed():
		_fire()
		cooldown_left = interval()

## نشانه‌روی و دیدبانی سنگر و حرکت سربازهایش.
func _animate_sangar(delta: float) -> void:
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

	# خود سنگر ثابت است و فقط در هنگام کاشته‌شدن یک‌بار بزرگ می‌شود؛ حرکت از سربازهای داخلش می‌آید.
	if _base != null:
		var pop := 1.0
		if _deploy_t < 1.0:
			pop = 1.0 + sin(_deploy_t * PI) * 0.25 * (1.0 - _deploy_t * 0.5)
		_base.rotation = 0.0
		_base.scale = Vector2.ONE * pop
		_base.position = PIVOT_OFFSET
	_animate_soldiers(delta, sign_x)

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
	if is_iso():
		_play_head("fire", true)
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
	_kick_soldier(index)

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

## سرباز تفنگ‌دار با صدای تیر خشک شلیک می‌کند؛ برجک‌های ایزومتریک صدای خودشان را دارند.
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
		_flash_anim.scale = Vector2.ONE * (0.92 + 0.06 * float(level - 1))
		_flash_anim.play("play")
	else:
		var still := _flash as Sprite2D
		still.texture = Assets.tex("towers/muzzle_2" if (_shot_count % 2 == 0) else "towers/muzzle_1")
		still.scale = Vector2.ONE * (1.12 + 0.05 * float(level - 1))
	_flash_time = _flash_duration

func _draw() -> void:
	# سایهٔ بیضی زیر پایهٔ ثابت سنگر (تصویر برجک ایزومتریک سایهٔ خودش را دارد)
	if not is_iso():
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
