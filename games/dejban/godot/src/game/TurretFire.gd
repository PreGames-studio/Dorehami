## اسکریپت‌های شلیک و مدیریت انیمیشن ۵ خانوادهٔ برجک در ۵ سطح (از ضعیف تا افسانه‌ای).
##
## هر خانوادهٔ سلاح منطق شلیک، پرتابه و جلوهٔ ویژهٔ اختصاصی خودش را دارد:
##   1. machine_gun : رگبار گلوله‌های زرد کوچک، جرقهٔ دهانه و پرتاب پوکه‌های برنجی
##   2. cannon      : گلولهٔ سیاه بزرگ با دنبالهٔ آتشین، شعلهٔ دهانه، دود غلیظ و آسیب ناحیه‌ای
##   3. missile     : پرتاب پیاپی موشک‌های سفید با دنبالهٔ دود سفید و انفجار ناحیه‌ای
##   4. laser       : پرتو لیزر آبی درخشان زره‌شکاف همراه با ذرات انرژی آبی
##   5. tesla       : صاعقه‌ها و قوس‌های الکتریکی بنفش زنجیره‌ای از بالای سیم‌پیچ
class_name TurretFire
extends RefCounted

const ANIM_FRAMES := {
	"idle": 8,
	"aim": 8,
	"fire": 6,
	"reload": 4,
	"cooldown": 4,
}

const ANIM_FPS := {
	"idle": 8.0,
	"aim": 12.0,
	"fire": 14.0,
	"reload": 8.0,
	"cooldown": 8.0,
}

const COLOR_BULLET_YELLOW := Color(1.0, 0.86, 0.22, 1.0)
const COLOR_SHELL_FIRE := Color(1.0, 0.48, 0.12, 1.0)
const COLOR_MISSILE_SMOKE := Color(0.95, 0.97, 1.0, 0.9)
const COLOR_LASER_BLUE := Color(0.25, 0.78, 1.0, 0.95)
const COLOR_TESLA_PURPLE := Color(0.72, 0.36, 1.0, 0.95)


## مختصات نوک لوله (Muzzle Tip) هر برجک بر اساس نوع سلاح، سطح (۱ تا ۵) و زاویهٔ هدف‌گیری.
static func muzzle_offset(family_id: String, level: int, aim_angle: float, barrel_index := 0) -> Vector2:
	var lvl := clampi(level, 1, TurretWeapons.MAX_TIER)
	var forward := 18.0 + float(lvl) * 1.1
	var side := 0.0
	match family_id:
		"machine_gun":
			forward = 16.5 + float(lvl) * 1.0
			if lvl in [2, 3]:
				side = -2.3 if (barrel_index % 2 == 0) else 2.3
			elif lvl == 4:
				var offs4 := [-3.0, 0.0, 3.0]
				side = float(offs4[barrel_index % 3])
			elif lvl >= 5:
				var offs5 := [-3.8, -1.9, 0.0, 1.9, 3.8]
				side = float(offs5[barrel_index % 5])
		"cannon":
			forward = 17.5 + float(lvl) * 0.85
			if lvl == 4:
				side = -2.6 if (barrel_index % 2 == 0) else 2.6
		"missile":
			forward = 10.5 + float(lvl) * 0.4
			var m_sides := [-3.2, 3.2, 0.0]
			side = float(m_sides[barrel_index % m_sides.size()])
		"laser":
			forward = 13.0 + float(lvl) * 0.65
		"tesla":
			forward = 10.0 + float(lvl) * 0.45
		"omega":
			forward = 18.5
			var om_sides := [0.0, -4.4, 4.4, 0.0]
			side = float(om_sides[barrel_index % om_sides.size()])
	return Vector2(forward, side).rotated(aim_angle)


## گام‌برداری انیمیشن ۴ حالتهٔ برجک (idle -> aim -> fire -> reload/cooldown) با حفظ ثبات کامل پایهٔ سنگی.
static func compute_anim_frame(family_id: String, state_name: String, state_time: float) -> Dictionary:
	var fam := TurretWeapons.family_by_id(family_id)
	var reload_key := String(fam.get("reload_anim", "reload"))
	var active_anim := state_name
	if active_anim == "reload" or active_anim == "cooldown":
		active_anim = reload_key
	if not ANIM_FRAMES.has(active_anim):
		active_anim = "idle"
	var count := int(ANIM_FRAMES[active_anim])
	var fps := float(ANIM_FPS.get(active_anim, 8.0))
	var raw_frame := int(floor(maxf(0.0, state_time) * fps))
	var frame := raw_frame % count
	var finished := raw_frame >= count
	return {
		"animation": active_anim,
		"frames": count,
		"frame": frame,
		"fps": fps,
		"finished": finished,
		"base_rotation": 0.0,
	}


## نقطهٔ ورود واحد برای اجرای شلیک هر یک از ۵ سلاح در سطح ۱ تا ۵.
static func execute_fire(tower: Node2D, primary_target, enemies: Array, family_id: String, level: int) -> Dictionary:
	var spec := TurretWeapons.tier_def(family_id, level)
	if spec.is_empty():
		return {}
	match family_id:
		"machine_gun":
			return fire_machine_gun(tower, primary_target, enemies, spec)
		"cannon":
			return fire_cannon(tower, primary_target, enemies, spec)
		"missile":
			return fire_missile(tower, primary_target, enemies, spec)
		"laser":
			return fire_laser(tower, primary_target, enemies, spec)
		"tesla":
			return fire_tesla(tower, primary_target, enemies, spec)
		"omega":
			return fire_omega(tower, primary_target, enemies, spec)
	return {}


## ۱. اسکریپت شلیک مسلسل (Machine Gun):
## رگبار سریع گلوله‌های زرد کوچک، شعلهٔ دهانه، جرقه‌های زرد و پرتاب پوکه‌های برنجی.
static func fire_machine_gun(tower: Node2D, primary_target, enemies: Array, spec: Dictionary) -> Dictionary:
	var lvl := int(spec.get("level", 1))
	var shots := int(spec.get("shots", 1))
	var dmg := float(spec.get("damage", 10.0))
	var pierce := bool(spec.get("armor_pierce", false))
	var reach := float(spec.get("range", 150.0))
	var origin := tower.position if tower != null else Vector2.ZERO
	var aim := float(tower.get("aim_angle")) if (tower != null and "aim_angle" in tower) else 0.0

	var targets := _pick_targets(origin, reach, primary_target, enemies, shots)
	var total_damage := 0.0
	var muzzles: Array = []
	for i in shots:
		var m_pos := origin + muzzle_offset("machine_gun", lvl, aim, i)
		muzzles.append(m_pos)
		var tgt = targets[i % targets.size()] if not targets.is_empty() else primary_target
		if _valid_enemy(tgt, origin, reach):
			_apply_hit(tgt, dmg, pierce, spec)
			total_damage += dmg
			_spawn_fx_burst(tower, "fx/spark", m_pos, 0.35 + 0.05 * lvl, 0.16)

	return {
		"family": "machine_gun",
		"level": lvl,
		"tier": spec.get("tier_id", "weak"),
		"projectile": "small_yellow_bullets",
		"color": COLOR_BULLET_YELLOW,
		"shots_fired": shots,
		"shell_casings_ejected": shots,
		"total_damage": total_damage,
		"muzzle_positions": muzzles,
		"recoil": 1.6 + 0.35 * float(lvl),
	}


## ۲. اسکریپت شلیک توپ سنگین (Cannon):
## پرتاب گلولهٔ سیاه بزرگ با دنبالهٔ آتشین، شعلهٔ عظیم دهانه، ابر دود، لگد لوله و آسیب ناحیه‌ای.
static func fire_cannon(tower: Node2D, primary_target, enemies: Array, spec: Dictionary) -> Dictionary:
	var lvl := int(spec.get("level", 1))
	var shots := int(spec.get("shots", 1))
	var dmg := float(spec.get("damage", 32.0))
	var splash := float(spec.get("splash", 48.0))
	var pierce := bool(spec.get("armor_pierce", false))
	var reach := float(spec.get("range", 175.0))
	var origin := tower.position if tower != null else Vector2.ZERO
	var aim := float(tower.get("aim_angle")) if (tower != null and "aim_angle" in tower) else 0.0

	var total_damage := 0.0
	var hit_count := 0
	var muzzles: Array = []
	for i in shots:
		var m_pos := origin + muzzle_offset("cannon", lvl, aim, i)
		muzzles.append(m_pos)
		_spawn_fx_burst(tower, "fx/smoke_1", m_pos, 0.55 + 0.08 * lvl, 0.32)
		if _valid_enemy(primary_target, origin, reach):
			var impact_pos: Vector2 = (primary_target as Node2D).position
			_apply_hit(primary_target, dmg, pierce, spec)
			total_damage += dmg
			hit_count += 1
			for other in enemies:
				if other == primary_target or not _alive_enemy(other):
					continue
				var dist: float = (other as Node2D).position.distance_to(impact_pos)
				if dist <= splash:
					var falloff := dmg * (1.0 - 0.45 * (dist / maxf(1.0, splash)))
					_apply_hit(other, falloff, pierce, spec)
					total_damage += falloff
					hit_count += 1

	return {
		"family": "cannon",
		"level": lvl,
		"tier": spec.get("tier_id", "weak"),
		"projectile": "large_black_shell_fire_trail",
		"color": COLOR_SHELL_FIRE,
		"shots_fired": shots,
		"splash_radius": splash,
		"targets_hit": hit_count,
		"total_damage": total_damage,
		"muzzle_positions": muzzles,
		"recoil": 3.2 + 0.55 * float(lvl),
	}


## ۳. اسکریپت شلیک موشک‌انداز (Missile Launcher):
## پرتاب تک‌به‌تک موشک‌های سفید با دنبالهٔ دود سفید از تیوب‌ها و انفجار ناحیه‌ای.
static func fire_missile(tower: Node2D, primary_target, enemies: Array, spec: Dictionary) -> Dictionary:
	var lvl := int(spec.get("level", 1))
	var shots := int(spec.get("shots", 1))
	var dmg := float(spec.get("damage", 24.0))
	var splash := float(spec.get("splash", 42.0))
	var pierce := bool(spec.get("armor_pierce", false))
	var reach := float(spec.get("range", 210.0))
	var origin := tower.position if tower != null else Vector2.ZERO
	var aim := float(tower.get("aim_angle")) if (tower != null and "aim_angle" in tower) else 0.0

	var targets := _pick_targets(origin, reach, primary_target, enemies, shots)
	var total_damage := 0.0
	var muzzles: Array = []
	for i in shots:
		var m_pos := origin + muzzle_offset("missile", lvl, aim, i)
		muzzles.append(m_pos)
		_spawn_fx_burst(tower, "fx/smoke_2", m_pos, 0.45 + 0.06 * lvl, 0.28)
		var tgt = targets[i % targets.size()] if not targets.is_empty() else primary_target
		if _valid_enemy(tgt, origin, reach):
			var impact_pos: Vector2 = (tgt as Node2D).position
			_apply_hit(tgt, dmg, pierce, spec)
			total_damage += dmg
			for other in enemies:
				if other == tgt or not _alive_enemy(other):
					continue
				var dist: float = (other as Node2D).position.distance_to(impact_pos)
				if dist <= splash:
					var splash_dmg := dmg * 0.55
					_apply_hit(other, splash_dmg, pierce, spec)
					total_damage += splash_dmg

	return {
		"family": "missile",
		"level": lvl,
		"tier": spec.get("tier_id", "weak"),
		"projectile": "white_missile_smoke_trail",
		"color": COLOR_MISSILE_SMOKE,
		"shots_fired": shots,
		"tubes_used": shots,
		"splash_radius": splash,
		"total_damage": total_damage,
		"muzzle_positions": muzzles,
		"recoil": 1.2 + 0.25 * float(lvl),
	}


## ۴. اسکریپت شلیک لیزر (Laser):
## پرتو لیزر آبی درخشان زره‌شکاف از عدسی به سمت هدف همراه با ذرات انرژی آبی.
static func fire_laser(tower: Node2D, primary_target, enemies: Array, spec: Dictionary) -> Dictionary:
	var lvl := int(spec.get("level", 1))
	var beams := int(spec.get("shots", 1))
	var dmg := float(spec.get("damage", 22.0))
	var reach := float(spec.get("range", 178.0))
	var origin := tower.position if tower != null else Vector2.ZERO
	var aim := float(tower.get("aim_angle")) if (tower != null and "aim_angle" in tower) else 0.0
	var lens_pos := origin + muzzle_offset("laser", lvl, aim, 0)

	var targets := _pick_targets(origin, reach, primary_target, enemies, beams)
	var total_damage := 0.0
	var beam_segments: Array = []
	for i in targets.size():
		var tgt = targets[i]
		if _valid_enemy(tgt, origin, reach):
			var tgt_pos: Vector2 = (tgt as Node2D).position
			_apply_hit(tgt, dmg, true, spec)
			total_damage += dmg
			beam_segments.append({"from": lens_pos, "to": tgt_pos})
			_spawn_fx_burst(tower, "fx/spark", tgt_pos, 0.45 + 0.07 * lvl, 0.20)

	return {
		"family": "laser",
		"level": lvl,
		"tier": spec.get("tier_id", "weak"),
		"projectile": "bright_blue_beam",
		"color": COLOR_LASER_BLUE,
		"shots_fired": beam_segments.size() if not beam_segments.is_empty() else beams,
		"armor_pierce": true,
		"beam_width": 2.4 + 0.55 * float(lvl),
		"beam_segments": beam_segments,
		"total_damage": total_damage,
		"muzzle_positions": [lens_pos],
		"recoil": 0.0,
	}


## ۵. اسکریپت شلیک تسلا (Tesla):
## صاعقه‌ها و قوس‌های الکتریکی بنفش که از بالای سیم‌پیچ به هدف اول و سپس دشمنان نزدیک زنجیره می‌شوند.
static func fire_tesla(tower: Node2D, primary_target, enemies: Array, spec: Dictionary) -> Dictionary:
	var lvl := int(spec.get("level", 1))
	var dmg := float(spec.get("damage", 18.0))
	var max_chain := int(spec.get("chain", 2))
	var chain_range := float(spec.get("chain_range", 84.0))
	var pierce := bool(spec.get("armor_pierce", false))
	var reach := float(spec.get("range", 156.0))
	var origin := tower.position if tower != null else Vector2.ZERO
	var aim := float(tower.get("aim_angle")) if (tower != null and "aim_angle" in tower) else 0.0
	var coil_pos := origin + muzzle_offset("tesla", lvl, aim, 0)

	var hit_chain: Array = []
	var arcs: Array = []
	var total_damage := 0.0

	if _valid_enemy(primary_target, origin, reach):
		var current = primary_target
		var current_dmg := dmg
		var from_pos: Vector2 = coil_pos
		for hop in max_chain:
			if current == null or not _alive_enemy(current):
				break
			var cur_pos: Vector2 = (current as Node2D).position
			_apply_hit(current, current_dmg, pierce, spec)
			total_damage += current_dmg
			hit_chain.append(current)
			arcs.append({"from": from_pos, "to": cur_pos})
			_spawn_fx_burst(tower, "fx/spark", cur_pos, 0.5 + 0.06 * lvl, 0.22)
			from_pos = cur_pos
			current_dmg *= 0.82
			current = _nearest_unchained(from_pos, chain_range, enemies, hit_chain)

	return {
		"family": "tesla",
		"level": lvl,
		"tier": spec.get("tier_id", "weak"),
		"projectile": "purple_lightning_arcs",
		"color": COLOR_TESLA_PURPLE,
		"shots_fired": 1,
		"max_chain": max_chain,
		"chained_targets": hit_chain.size(),
		"arcs": arcs,
		"total_damage": total_damage,
		"muzzle_positions": [coil_pos],
		"recoil": 0.0,
	}


## ۶. اسکریپت شلیک اختصاصی ابرسلاح ایزدی سیمرغ (Omega — ۱۰ برابر قوی‌ترین سلاح بازی):
## شلیک هم‌زمان ۴ ابرپرتو و شهاب‌سنگ زره‌شکاف + انفجار خورشیدی ناحیه‌ای + رعد زنجیره‌ای ۸ هدفه + شوک و آتش کیهانی.
static func fire_omega(tower: Node2D, primary_target, enemies: Array, spec: Dictionary) -> Dictionary:
	var shots := int(spec.get("shots", 4))
	var dmg := float(spec.get("damage", 1600.0))
	var splash := float(spec.get("splash", 110.0))
	var max_chain := int(spec.get("chain", 8))
	var chain_range := float(spec.get("chain_range", 150.0))
	var reach := float(spec.get("range", 360.0))
	var origin := tower.position if tower != null else Vector2.ZERO
	var aim := float(tower.get("aim_angle")) if (tower != null and "aim_angle" in tower) else 0.0

	var targets := _pick_targets(origin, reach, primary_target, enemies, shots)
	var total_damage := 0.0
	var muzzles: Array = []
	var arcs: Array = []
	var chained: Array = []

	for i in shots:
		var m_pos := origin + muzzle_offset("omega", 6, aim, i)
		muzzles.append(m_pos)
		_spawn_fx_burst(tower, "fx/flame_2", m_pos, 0.85, 0.28)
		var tgt = targets[i % targets.size()] if not targets.is_empty() else primary_target
		if _valid_enemy(tgt, origin, reach):
			var impact_pos: Vector2 = (tgt as Node2D).position
			_apply_hit(tgt, dmg, true, spec)
			total_damage += dmg
			# انفجار خورشیدی ناحیه‌ای
			for other in enemies:
				if other == tgt or not _alive_enemy(other):
					continue
				var dist: float = (other as Node2D).position.distance_to(impact_pos)
				if dist <= splash:
					var s_dmg := dmg * 0.65
					_apply_hit(other, s_dmg, true, spec)
					total_damage += s_dmg

	# زنجیرهٔ رعد کیهانی بین ۸ دشمن
	if _valid_enemy(primary_target, origin, reach):
		var current = primary_target
		var arc_dmg := dmg * 0.5
		var from_pos: Vector2 = origin + muzzle_offset("omega", 6, aim, 0)
		for hop in max_chain:
			if current == null or not _alive_enemy(current):
				break
			var cur_pos: Vector2 = (current as Node2D).position
			_apply_hit(current, arc_dmg, true, spec)
			total_damage += arc_dmg
			chained.append(current)
			arcs.append({"from": from_pos, "to": cur_pos})
			_spawn_fx_burst(tower, "fx/spark", cur_pos, 0.9, 0.25)
			from_pos = cur_pos
			arc_dmg *= 0.85
			current = _nearest_unchained(from_pos, chain_range, enemies, chained)

	return {
		"family": "omega",
		"level": 6,
		"tier": "mythic",
		"projectile": "cosmic_beam_meteor_lightning",
		"color": COLOR_BULLET_YELLOW,
		"shots_fired": shots,
		"splash_radius": splash,
		"max_chain": max_chain,
		"chained_targets": chained.size(),
		"arcs": arcs,
		"total_damage": total_damage,
		"muzzle_positions": muzzles,
		"recoil": 4.5,
	}


# ------------------------------------------------------------------ توابع کمکی هدف‌گیری و اعمال اثر


static func _alive_enemy(enemy) -> bool:
	return enemy != null and is_instance_valid(enemy) and bool(enemy.get("alive"))


static func _valid_enemy(enemy, origin: Vector2, reach: float) -> bool:
	if not _alive_enemy(enemy):
		return false
	return (enemy as Node2D).position.distance_to(origin) <= reach


static func _pick_targets(origin: Vector2, reach: float, primary_target, enemies: Array, count: int) -> Array:
	var out: Array = []
	if _valid_enemy(primary_target, origin, reach):
		out.append(primary_target)
	if count > 1:
		for enemy in enemies:
			if out.size() >= count:
				break
			if not out.has(enemy) and _valid_enemy(enemy, origin, reach):
				out.append(enemy)
	return out


static func _nearest_unchained(from_pos: Vector2, chain_range: float, enemies: Array, visited: Array):
	var best = null
	var best_d := chain_range
	for enemy in enemies:
		if visited.has(enemy) or not _alive_enemy(enemy):
			continue
		var d: float = (enemy as Node2D).position.distance_to(from_pos)
		if d <= best_d:
			best_d = d
			best = enemy
	return best


static func _apply_hit(enemy, amount: float, pierce_armor: bool, spec: Dictionary) -> void:
	if not _alive_enemy(enemy):
		return
	if enemy.has_method("take_damage"):
		enemy.take_damage(amount, pierce_armor)
	if not _alive_enemy(enemy):
		return
	if spec.has("burn") and enemy.has_method("apply_burn"):
		enemy.apply_burn(float(spec["burn"]), float(spec.get("burn_time", 2.5)))
	if spec.has("slow") and enemy.has_method("apply_slow"):
		enemy.apply_slow(float(spec["slow"]), float(spec.get("slow_time", 1.5)))


static func _spawn_fx_burst(tower: Node2D, key: String, pos: Vector2, scale_val: float, duration: float) -> void:
	if tower == null or not is_instance_valid(tower):
		return
	if tower.has_method("_fx_parent"):
		var parent = tower._fx_parent()
		if parent != null and Assets.has_key(key):
			Fx.burst(parent, key, pos, scale_val, duration, 1.3)
