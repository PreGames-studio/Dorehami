## دشمن: با انیمیشن ۸ فریمی گام‌برداری/چرخش شنی/ملخ و فیزیک نرم،
## دقیقاً در خط میانی جاده پیش می‌رود، آسیب و اثرها را می‌گیرد و در پایان مسیر به دژ می‌رسد.
class_name Enemy
extends Node2D

## نوار جان در گرهٔ جدا و بالاتر از اسپرایت رسم می‌شود تا زیر سر سرباز پنهان نماند.
class HealthBar:
	extends Node2D

	const HEIGHT := 4.0
	var ratio := 1.0
	var bar_width := 30.0

	func _draw() -> void:
		if ratio >= 0.999:
			return
		draw_rect(Rect2(-bar_width * 0.5 - 1.0, -1.0, bar_width + 2.0, HEIGHT + 2.0),
			Color(0.02, 0.05, 0.06, 0.72))
		var color: Color = Style.good
		if ratio < 0.28:
			color = Style.bad
		elif ratio < 0.6:
			color = Style.gold
		draw_rect(Rect2(-bar_width * 0.5, 0.0, bar_width * ratio, HEIGHT), color)

signal died(enemy)
signal reached_keep(enemy)
## دشمنی که کنار دژ می‌ایستد و به آن شلیک می‌کند (تانک IsoEnemy) با شلیک به دژ این سیگنال را می‌دهد.
signal keep_attacked(enemy, amount)
## پس از تکمیل تنها شلیک تانک به دژ صادر می‌شود تا تانک بی‌درنگ و بدون پاداش کشتن از میدان حذف شود.
signal finished_attack(enemy)

const BAR_WIDTH := 30.0
const BAR_HEIGHT := 4.0
const HEALTH_BAR_GAP := 8.0

## Die-Animation: 6 frames at 8 fps, the last frame holds a moment,
## then the body fades out and frees itself.
const RUN_FPS := 14.0
const DEATH_FPS := 8.0
const DEATH_HOLD := 0.45
const DEATH_FADE := 0.30

var def: Dictionary = {}
var map: GameMap = null
var wave := 1
var max_hp := 100.0
var hp := 100.0
var base_speed := 50.0
var speed_mult := 1.0
var armor := 0.0
var shield := 0.0
var reward := 5
var scale_mult := 1.0
var flying := false
var alive := true
var progress := 0.0
var lane := 0.0

var burn_dps := 0.0
var burn_time := 0.0
var poison_dps := 0.0
var poison_time := 0.0
var slow_factor := 0.0
var slow_time := 0.0

var _sprite: Sprite2D = null
var _health_bar: HealthBar = null
var _flash := 0.0
var _bob := 0.0
var _walk_phase := 0.0
var _smooth_dir := Vector2.RIGHT
var _facing_x := 1.0
var _hit_jolt := 0.0
var _unit_id := "soldier"
var _frame_h := 48.0

## Sheets: walk (one direction, mirrored), run and death (four directions).
var _walk_tex: Texture2D = null
var _walk_frame_h := 48.0
var _ground_anchor := 19.2      # node origin to ground line in the walk sheet
var _uses_run := false
var _anim := "walk"             # walk | run | death
var _sheet_dir := "front"
var _dying := false
var _death_t := 0.0

func setup(enemy_def: Dictionary, game_map: GameMap, hp_scale: float, speed_scale: float,
		wave_index: int, lane_offset: float) -> void:
	_setup_stats(enemy_def, game_map, hp_scale, speed_scale, wave_index, lane_offset)
	_setup_visual()

## آمار و جای آغاز دشمن از ورودی Enemies؛ بین سرباز و دشمن‌های ایزومتریک مشترک است.
func _setup_stats(enemy_def: Dictionary, game_map: GameMap, hp_scale: float, speed_scale: float,
		wave_index: int, lane_offset: float) -> void:
	def = enemy_def
	map = game_map
	wave = wave_index
	lane = lane_offset * 0.25
	_unit_id = String(def.get("id", "soldier"))
	max_hp = float(def.get("hp", 60.0)) * hp_scale
	hp = max_hp
	base_speed = float(def.get("speed", 46.0)) * speed_scale
	armor = float(def.get("armor", 0.0))
	shield = float(def.get("shield", 0.0))
	reward = int(def.get("reward", 5))
	scale_mult = float(def.get("scale", 1.0))
	var traits: Array = def.get("traits", [])
	flying = traits.has("flying")
	_walk_phase = randf() * TAU
	_bob = randf() * TAU
	if map != null:
		_smooth_dir = map.direction_at(0.0)
		if absf(_smooth_dir.x) > 0.05:
			_facing_x = -1.0 if _smooth_dir.x < 0.0 else 1.0

## شیت‌های سرباز (راه‌رفتن، دویدن و مرگ).
func _setup_visual() -> void:
	_sprite = Sprite2D.new()
	var base_key := String(def.get("sprite", "units/soldier"))
	var walk_key := base_key + "_walk"
	if Assets.has_key(walk_key):
		_walk_tex = Assets.tex(walk_key)
	else:
		_walk_tex = Assets.tex(base_key)
	_sprite.texture = _walk_tex
	# Fast units (the runner) move with the run sheet; the death sheet is for all.
	# شیت دویدن: قد و خط زمین آن ثابت است، پس سر بالا-پایین نمی‌شود و پاها حرکت را می‌برند.
	_uses_run = Assets.has_key(UnitSheets.tex_key(_unit_id, "run", "front"))
	_sheet_dir = UnitSheets.direction_name(_smooth_dir)
	if _sprite.texture != null:
		_walk_frame_h = float(_sprite.texture.get_height())
		_frame_h = _walk_frame_h
		_ground_anchor = 0.40 * _walk_frame_h
		_sprite.centered = false
	_sprite.scale = Vector2.ONE * scale_mult
	add_child(_sprite)
	_apply_sheet("run" if _uses_run else "walk")
	_health_bar = HealthBar.new()
	_health_bar.z_index = 1
	_health_bar.bar_width = BAR_WIDTH * scale_mult
	_health_bar.position.y = health_bar_top()
	add_child(_health_bar)
	_update_health_bar()
	_update_transform(0.0)

# ------------------------------------------------------------------ حرکت و انیمیشن چندفریمی نرم

func _process(delta: float) -> void:
	if _dying:
		_advance_death(delta)
		return
	if not alive or map == null:
		return
	_tick_effects(delta)
	if not alive:
		return
	_update_health_bar()

	var speed := base_speed * speed_mult * (1.0 - clampf(slow_factor, 0.0, 0.95))
	progress += speed * delta
	_update_transform(delta, speed)
	queue_redraw()
	if progress >= map.total_length:
		alive = false
		reached_keep.emit(self)

## زمان کندی، سوختن و مسمومیت و محوشدن برق ضربه؛ ممکن است دشمن را بکشد.
func _tick_effects(delta: float) -> void:
	if slow_time > 0.0:
		slow_time -= delta
		if slow_time <= 0.0:
			slow_factor = 0.0
	if burn_time > 0.0:
		burn_time -= delta
		_damage_over_time(burn_dps * delta)
	if poison_time > 0.0:
		poison_time -= delta
		_damage_over_time(poison_dps * delta)
	if not alive:
		return
	if _flash > 0.0:
		_flash = maxf(0.0, _flash - delta * 6.0)
	if _hit_jolt > 0.0:
		_hit_jolt = maxf(0.0, _hit_jolt - delta * 7.5)
	_update_tint()

func _update_transform(delta: float, current_speed := 46.0) -> void:
	if map == null:
		return
	var t := clampf(progress, 0.0, map.total_length)
	var raw_dir := map.direction_at(t)
	if delta > 0.0:
		_smooth_dir = _smooth_dir.lerp(raw_dir, clampf(delta * 10.0, 0.0, 1.0)).normalized()
	else:
		_smooth_dir = raw_dir
	var perp := Vector2(-_smooth_dir.y, _smooth_dir.x)
	var jolt_vec := -_smooth_dir * (_hit_jolt * 3.8)
	position = map.point_at(t) + perp * lane + jolt_vec
	if _sprite == null:
		return

	if absf(_smooth_dir.x) > 0.06:
		var target_face := -1.0 if _smooth_dir.x < 0.0 else 1.0
		if delta > 0.0:
			_facing_x = lerpf(_facing_x, target_face, clampf(delta * 13.0, 0.0, 1.0))
		else:
			_facing_x = target_face
	var face_sign := -1.0 if _facing_x < 0.0 else 1.0
	var flip_mag := maxf(0.28, absf(_facing_x))
	# The run and death sheets are four-directional and are never mirrored;
	# only the turn squash stays. The walk sheet is mirrored as before.
	var flip_scale := flip_mag if _anim != "walk" else face_sign * flip_mag
	var want_dir := UnitSheets.direction_name(_smooth_dir)
	if want_dir != _sheet_dir:
		_sheet_dir = want_dir
		if _uses_run:
			_apply_sheet("run")

	var road_angle := _smooth_dir.angle()
	var local_road_rot := road_angle if face_sign > 0.0 else wrapf(road_angle - PI, -PI, PI)

	var stride_rate := clampf(current_speed / 40.0, 0.55, 2.4)
	# The run sheet is authored at 14 frames per second, so at full speed the
	# phase turns exactly that fast (the walk sheets keep their own tuning).
	var run_rate := 0.0
	if _anim == "run":
		run_rate = TAU * RUN_FPS / 8.0 * clampf(current_speed / maxf(base_speed, 1.0), 0.6, 1.4)
	if flying:
		_bob += delta * 9.5 * stride_rate
		_walk_phase = _bob
		var hover_y := sin(_bob * 0.7) * 3.2 - 4.0 * scale_mult
		var hover_x := cos(_bob * 0.35) * 1.6
		_sprite.position = Vector2(hover_x, hover_y)
		var bank := clampf(local_road_rot * 0.38 + sin(_bob * 0.7) * 0.05, -0.32, 0.32)
		_sprite.rotation = bank
		var pulse := 1.0 + sin(_bob * 2.0) * 0.025
		_sprite.scale = Vector2(scale_mult * flip_scale * pulse, scale_mult * (2.0 - pulse))
	else:
		_walk_phase += delta * (run_rate if _anim == "run" else 10.8 * stride_rate)
		# در راه رفتن هیچ بزرگ‌وکوچک شدن عمودی روی تصویر نیست؛ هر تکان بالا-پایین از خود شیت می‌آید و همان‌جا مانده است.
		var squash := 0.0 if (_anim == "walk" or _anim == "run") else cos(_walk_phase * 2.0) * 0.05
		var slope_lean := clampf(local_road_rot * 0.24, -0.20, 0.20)
		if _anim == "walk" or _anim == "run":
			# خود شیت راه رفتن بالا-پایین شدن بدن و تاب پاها را دارد. اگر کل تصویر
			# در هر گام جابه‌جا یا چرخانده شود، سر به جلو و عقب می‌لغزد در حالی که
			# پاها روی زمین ثابت‌اند؛ پس فقط شیب جاده می‌ماند.
			_sprite.position = Vector2.ZERO
			_sprite.rotation = 0.045 * face_sign + slope_lean
		else:
			var step_hop := absf(sin(_walk_phase))
			var step_sway := sin(_walk_phase) * 0.085
			_sprite.position = Vector2(sin(_walk_phase) * 1.2, -step_hop * 2.8 * scale_mult)
			_sprite.rotation = step_sway + 0.045 * face_sign + slope_lean
		_sprite.scale = Vector2(scale_mult * flip_scale * (1.0 + squash), scale_mult * (1.0 - squash * 0.8))

	if _sprite.hframes > 1:
		var cycle := fposmod(_walk_phase / TAU, 1.0)
		_sprite.frame = int(floor(cycle * float(_sprite.hframes))) % _sprite.hframes

# ------------------------------------------------------------------ sheets and animation

## Puts the requested sheet on the sprite and aligns the offset with its ground
## line: the run / death frames are taller than the walk frames, so without this
## the body would jump when the animation changes.
func _apply_sheet(anim: String) -> void:
	if _sprite == null or _walk_tex == null:
		return
	var tex: Texture2D = _walk_tex
	var hframes := 1
	if Assets.has_key(String(def.get("sprite", "units/soldier")) + "_walk"):
		hframes = 8
	if anim != "walk":
		var key := UnitSheets.tex_key(_unit_id, anim, _sheet_dir)
		if Assets.has_key(key):
			tex = Assets.tex(key)
			if anim == "death":
				hframes = UnitSheets.DEATH_FRAMES
			elif anim == "run":
				hframes = UnitSheets.RUN_FRAMES
		else:
			anim = "walk"
	if _sprite.texture != tex:
		_sprite.texture = tex
	_sprite.hframes = hframes
	_anim = anim
	var fw := float(tex.get_width()) / float(hframes)
	# راه‌رفتن و دویدن هر دو قد و خط زمین یکسان دارند؛ مرگ خط زمین جدا دارد.
	var offset_y := -_walk_frame_h * 0.52
	if anim == "death":
		offset_y = -(UnitSheets.ground(_unit_id, anim, 0.92 * _walk_frame_h) - _ground_anchor)
	_sprite.offset = Vector2(-fw * 0.5, offset_y)
	if anim != "death" and _sprite.frame >= hframes:
		_sprite.frame = 0


## Fallen body: the six frames play once, the last one holds, then it fades.
func _advance_death(delta: float) -> void:
	_death_t += delta
	var last := UnitSheets.DEATH_FRAMES - 1
	if _sprite != null and _anim == "death" and _sprite.hframes > 1:
		_sprite.frame = mini(int(_death_t * DEATH_FPS), last)
	var anim_end := float(last) / DEATH_FPS
	if _death_t < anim_end + DEATH_HOLD:
		return
	if _sprite != null:
		var fade := clampf((_death_t - anim_end - DEATH_HOLD) / DEATH_FADE, 0.0, 1.0)
		_sprite.modulate = Color(1.0, 1.0, 1.0, 1.0 - fade)
		if fade < 1.0:
			return
	queue_free()


## Is the body falling right now? (used by the headless test)
func is_dying() -> bool:
	return _dying


## Active sprite animation: walk | run | death (used by the headless test)
func active_anim() -> String:
	return _anim


func _update_tint() -> void:
	if _sprite == null:
		return
	var tint := Color.WHITE
	if burn_time > 0.0:
		tint = Color(1.3, 0.86, 0.66)
	elif poison_time > 0.0:
		tint = Color(0.82, 1.22, 0.78)
	if slow_time > 0.0:
		tint = tint.lerp(Color(0.7, 0.92, 1.3), 0.5)
	if _flash > 0.0:
		tint = tint.lerp(Color(2.0, 2.0, 2.0), _flash)
	_sprite.modulate = tint

## لبهٔ بالای شیت فعلی با کمی فاصله؛ کل نوار بالای سر قرار می‌گیرد، نه روی تصویر.
func health_bar_top() -> float:
	return (-_walk_frame_h * 0.52 - HEALTH_BAR_GAP) * scale_mult

func _update_health_bar() -> void:
	if _health_bar == null:
		return
	_health_bar.ratio = clampf(hp / max_hp, 0.0, 1.0) if max_hp > 0.0 else 0.0
	_health_bar.visible = alive and max_hp > 0.0
	_health_bar.queue_redraw()

# ------------------------------------------------------------------ آسیب و اثر

func take_damage(amount: float, pierce_armor := false) -> void:
	if not alive:
		return
	var damage := amount
	if not pierce_armor:
		damage = maxf(1.0, damage - armor)
	if shield > 0.0:
		damage = maxf(1.0, damage * (1.0 - shield))
	hp -= damage
	_flash = 1.0
	_hit_jolt = 1.0
	_update_health_bar()
	queue_redraw()
	if hp <= 0.0:
		_die()

func heal(amount: float) -> void:
	if not alive:
		return
	hp = minf(max_hp, hp + amount)
	_update_health_bar()
	queue_redraw()

func _damage_over_time(amount: float) -> void:
	if not alive:
		return
	hp -= amount
	_flash = maxf(_flash, 0.5)
	_update_health_bar()
	queue_redraw()
	if hp <= 0.0:
		_die()

func _die() -> void:
	if not alive:
		return
	alive = false
	_dying = true
	_death_t = 0.0
	_update_health_bar()
	# Death sheet of the direction it was walking; the body stays on the ground
	# and frees itself once the animation and the fade are done.
	_sheet_dir = UnitSheets.direction_name(_smooth_dir)
	_apply_sheet("death")
	if _sprite != null:
		_sprite.modulate = Color.WHITE
		_sprite.frame = 0
	queue_redraw()
	died.emit(self)

func apply_slow(factor: float, duration: float) -> void:
	slow_factor = maxf(slow_factor, clampf(factor, 0.0, 0.95))
	slow_time = maxf(slow_time, duration)

func apply_burn(dps: float, duration: float) -> void:
	burn_dps = maxf(burn_dps, dps)
	burn_time = maxf(burn_time, duration)

func apply_poison(dps: float, duration: float) -> void:
	poison_dps = maxf(poison_dps, dps)
	poison_time = maxf(poison_time, duration)

func has_trait(feature: String) -> bool:
	var traits: Array = def.get("traits", [])
	return traits.has(feature)

func crowd_radius() -> float:
	return 16.0 * scale_mult

# -------------------------------------------------- نمایش سایه؛ نوار جان در HealthBar بالای اسپرایت است

func _draw() -> void:
	if not alive or max_hp <= 0.0:
		return
	var shadow_y := _frame_h * 0.40 * scale_mult
	var shadow_scale := 1.0 - 0.14 * absf(sin(_walk_phase)) if not flying else 0.78
	draw_set_transform(Vector2(0.0, shadow_y), 0.0, Vector2(shadow_scale, 0.38 * shadow_scale))
	draw_circle(Vector2.ZERO, 13.0 * scale_mult, Color(0, 0, 0, 0.30))
	draw_set_transform(Vector2.ZERO, 0.0, Vector2.ONE)
