## دشمن ایزومتریک (تانک) از شیت‌های شبکه‌ای پک units_iso_tank با هشت جهت.
##
## - در مسیر با حالت «move» پیش می‌رود. جهت از خود مسیر می‌آید و فقط در گام‌های ۴۵ درجه
##   (ردیف شیت) عوض می‌شود؛ اسپرایت هیچ‌وقت پیوسته نمی‌چرخد.
## - کمی پیش از دژ می‌ایستد (محاصره)، رو به دژ می‌چرخد و بین شلیک‌ها «idle» است.
## - هر attack_interval ثانیه یک بار «attack» را پخش می‌کند؛ لیزر قرمز در خود فریم‌هاست و
##   در فریم LASER_FRAME پرتو تا دژ کشیده می‌شود و keep_damage جان کم می‌شود (سیگنال keep_attacked).
## - تا نابود نشود زنده و در فهرست دشمنان می‌ماند، پس برجک‌ها هدفش می‌گیرند و موج تمام نمی‌شود.
class_name IsoEnemy
extends Enemy

## اندازهٔ فریم ۱۲۸ پیکسلی روی نقشه (بدنهٔ تانک تقریباً هم‌پهنای جاده).
const SPRITE_SCALE := 0.8
## فاصلهٔ ایستادن تا انتهای مسیر (مرکز دژ).
const SIEGE_STANDOFF := 96.0
## فریمی از attack که پرتو لیزر کامل است (بیشترین پیکسل قرمز در شیت).
const LASER_FRAME := 2
## اولین شلیک پس از رسیدن، زودتر از فاصلهٔ کامل بین شلیک‌ها.
const FIRST_SHOT_DELAY := 0.6
## نوک پرتو لیزرِ داخل فریم attack برای هر جهت، نسبت به TANK_PIVOT (پیکسل فریم)؛
## از پیکسل‌های قرمزی که در فریم ۲ هست و در فریم ۰ نیست اندازه گرفته شده است.
const LASER_TIP := [Vector2(57, -47), Vector2(55, -2), Vector2(-2, 15), Vector2(-56, -2),
	Vector2(-58, -47), Vector2(-58, -57), Vector2(-3, -76), Vector2(56, -59)]
const LASER_TINT := Color(2.2, 0.32, 0.26, 1.0)
const LASER_TINTS := {
	"tank": Color(2.2, 0.32, 0.26, 1.0),
	"tank_armored": Color(0.28, 1.55, 2.35, 1.0),
	"tank_heavy": Color(2.15, 0.36, 2.25, 1.0),
}
const DEATH_TIME := 0.7

var family := "tank"
var action := ""
var dir := IsoSheet.DIR_S
var sieging := false
var siege_progress := 0.0
var attack_interval := 2.0
var keep_damage := 1
var shots_fired := 0

var _player: IsoSheet.Player = null
var _attack_cd := 0.0
var _attack_hit := false
var _keep_pos := Vector2.ZERO


func setup(enemy_def: Dictionary, game_map: GameMap, hp_scale: float, speed_scale: float,
		wave_index: int, lane_offset: float) -> void:
	_setup_stats(enemy_def, game_map, hp_scale, speed_scale, wave_index, lane_offset)
	family = String(def.get("iso", "tank"))
	attack_interval = maxf(0.5, float(def.get("attack_interval", 2.0)))
	keep_damage = maxi(1, int(def.get("keep_damage", 1)))
	_attack_cd = minf(FIRST_SHOT_DELAY, attack_interval)
	if map != null:
		siege_progress = maxf(0.0, map.total_length - float(def.get("standoff", SIEGE_STANDOFF)))
		_keep_pos = map.keep_pos
	_frame_h = IsoSheet.FRAME * SPRITE_SCALE

	_sprite = Sprite2D.new()
	_sprite.centered = false
	_sprite.offset = -IsoSheet.TANK_PIVOT
	_sprite.scale = Vector2.ONE * SPRITE_SCALE
	add_child(_sprite)
	_player = IsoSheet.Player.new(_sprite)
	dir = IsoSheet.dir_from_vector(_smooth_dir)
	_player.dir = dir
	_play("move")
	_update_transform(0.0)


func _sheet_key(action_name: String) -> String:
	return "units_iso_%s/%s_%s" % [family, family, action_name]


func _play(action_name: String, restart := false) -> void:
	action = action_name
	_player.play(_sheet_key(action_name), action_name, IsoSheet.TANK_ACTIONS.get(action_name, {}), restart)


func _set_dir(value: int) -> void:
	dir = posmod(value, IsoSheet.DIRS)
	_player.set_dir(dir)


# ------------------------------------------------------------------ حرکت، محاصره و شلیک

func _process(delta: float) -> void:
	if _dying:
		_advance_death(delta)
		return
	if not alive or map == null:
		return
	_tick_effects(delta)
	if not alive:
		return
	_player.advance(delta)
	if sieging:
		_siege(delta)
	else:
		var speed := base_speed * speed_mult * (1.0 - clampf(slow_factor, 0.0, 0.95))
		progress = minf(progress + speed * delta, siege_progress)
		_update_transform(delta, speed)
		if progress >= siege_progress:
			_begin_siege()
	queue_redraw()


func _update_transform(delta: float, _current_speed := 46.0) -> void:
	if map == null:
		return
	var t := clampf(progress, 0.0, map.total_length)
	var raw_dir := map.direction_at(t)
	if delta > 0.0:
		_smooth_dir = _smooth_dir.lerp(raw_dir, clampf(delta * 8.0, 0.0, 1.0)).normalized()
	else:
		_smooth_dir = raw_dir
	var perp := Vector2(-_smooth_dir.y, _smooth_dir.x)
	position = map.point_at(t) + perp * lane - _smooth_dir * (_hit_jolt * 2.0)
	if not sieging and _player != null:
		# ردیف شیت از جهت مسیر، در گام‌های ۴۵ درجه.
		_set_dir(IsoSheet.dir_from_vector(_smooth_dir, dir))


func _begin_siege() -> void:
	sieging = true
	progress = siege_progress
	_set_dir(IsoSheet.dir_from_vector(_keep_pos - position, dir))
	_play("idle")


func _siege(delta: float) -> void:
	_update_transform(delta)
	_set_dir(IsoSheet.dir_from_vector(_keep_pos - position, dir))
	_attack_cd -= delta
	if action == "attack":
		if not _attack_hit and _player.frame_index >= LASER_FRAME:
			_attack_hit = true
			_laser_hit()
		if _player.finished():
			_play("idle")
	if _attack_cd <= 0.0 and action != "attack":
		_attack_cd += attack_interval
		_attack_hit = false
		_play("attack", true)


## لحظهٔ رسیدن لیزر: Battle جان دژ را کم می‌کند و پرتو را از laser_tip() تا دژ می‌کشد.
func _laser_hit() -> void:
	shots_fired += 1
	keep_attacked.emit(self, keep_damage)


## نوک پرتو لیزر در مختصات جهان برای جهت فعلی.
func laser_tip() -> Vector2:
	return position + (LASER_TIP[dir] as Vector2) * SPRITE_SCALE


## رنگ پرتو لیزر مطابق ظاهر هر خانوادهٔ تانک.
func laser_color() -> Color:
	return LASER_TINTS.get(family, LASER_TINT)


# ------------------------------------------------------------------ مرگ و نمایش

func _die() -> void:
	if not alive:
		return
	alive = false
	_dying = true
	_death_t = 0.0
	if _sprite != null:
		_sprite.modulate = Color.WHITE
	queue_redraw()
	died.emit(self)


## لاشهٔ سوخته: اسپرایت تیره می‌شود و محو می‌شود (انفجار بزرگ را Battle می‌سازد).
func _advance_death(delta: float) -> void:
	_death_t += delta
	var k := clampf(_death_t / DEATH_TIME, 0.0, 1.0)
	if _sprite != null:
		var dark := lerpf(1.0, 0.25, minf(1.0, k * 2.0))
		_sprite.modulate = Color(dark, dark * 0.9, dark * 0.85, 1.0 - k)
	if k >= 1.0:
		queue_free()


func active_anim() -> String:
	return "death" if _dying else action


func _draw() -> void:
	if not alive or max_hp <= 0.0:
		return
	var ratio := clampf(hp / max_hp, 0.0, 1.0)
	if ratio >= 0.999:
		return
	var width := BAR_WIDTH * scale_mult
	var top := -(IsoSheet.TANK_PIVOT.y - 22.0) * SPRITE_SCALE - 8.0
	draw_rect(Rect2(-width * 0.5 - 1.0, top - 1.0, width + 2.0, BAR_HEIGHT + 2.0), Color(0.02, 0.05, 0.06, 0.72))
	var color := Style.good
	if ratio < 0.28:
		color = Style.bad
	elif ratio < 0.6:
		color = Style.gold
	draw_rect(Rect2(-width * 0.5, top, width * ratio, BAR_HEIGHT), color)
