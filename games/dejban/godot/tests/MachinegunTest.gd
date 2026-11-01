## Headless behavioral regression tests for the actual battle tower and bullets.
extends RefCounted

class TestBattle extends Node2D:
	var enemies: Array = []
	func fx_layer() -> Node:
		return self

func _enemy(parent: Node, pos: Vector2) -> Enemy:
	var result := Enemy.new()
	parent.add_child(result)
	result.set_process(false)
	result.position = pos
	result.alive = true
	result.hp = 1000.0
	result.max_hp = 1000.0
	return result

func _projectiles(battle: Node) -> Array:
	var out: Array = []
	for child in battle.get_children():
		if child is Projectile and not child.is_queued_for_deletion():
			out.append(child)
	return out

func _casings(battle: Node) -> Array:
	var out: Array = []
	for child in battle.get_children():
		if child is ShellCasing and not child.is_queued_for_deletion():
			out.append(child)
	return out

func run(test) -> void:
	var weapon := Weapons.by_id("machinegun")
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := Tower.new()
	test.add_child(tower)
	tower.set_process(false)
	tower.position = Vector2(400, 300)
	tower.setup(weapon, battle)
	var visual := tower._machinegun_visual
	test.check(visual != null and tower.level_cap() == 5, "مسلسل باید پنج سطح و هنر لایه‌ای داشته باشد")
	if visual == null:
		tower.queue_free()
		battle.queue_free()
		return
	test.check(visual.base.scale.is_equal_approx(Vector2(MachinegunVisual.IMAGE_SCALE * 0.8, MachinegunVisual.IMAGE_SCALE * 0.64)),
		"عرض و ارتفاع پایه باید هرکدام ۲۰٪ نسبت به حالت قبلی کوچک‌تر شوند")
	var ghost := BuildGhost.new()
	test.add_child(ghost)
	ghost.set_weapon(weapon)
	test.check(ghost._base.offset == visual.base.offset and ghost._base.scale == visual.base.scale
		and ghost._base.position == visual.position,
		"پیش‌نمای مسلسل باید اندازه و موقعیت واقعی برج چهارخانه‌ای را نشان دهد")
	test.check(tower._level_marker != null and tower._level_marker.z_index > visual.z_index,
		"نشان سطح باید روی پایهٔ جابه‌جاشده دیده شود")
	ghost.queue_free()
	var base_transform: Transform2D = visual.base.transform
	var base_texture: Texture2D = visual.base.texture
	var tower_position: Vector2 = tower.position
	var previous_damage := 0.0
	var previous_range := 0.0
	var previous_interval := INF
	var previous_turn := 0.0

	# All five standalone PNGs are RGBA, same-size, with truly transparent corners.
	for level in range(1, 6):
		var key := "towers_machinegun/machinegun_%d" % level
		var texture := Assets.tex(key)
		test.check(Assets.has_key(key) and texture != null and texture.get_size() == Vector2(256, 256),
			"تصویر مستقل سطح %d مسلسل گم شده یا هم‌اندازه نیست" % level)
		if texture != null:
			var image := texture.get_image()
			test.check(image != null and not image.is_empty() and image.get_pixel(0, 0).a == 0.0
				and image.get_pixel(255, 255).a == 0.0, "پس‌زمینهٔ تصویر سطح %d باید شفاف باشد" % level)
		test.check(tower.level == level and tower.damage() > previous_damage and tower.attack_range() > previous_range
			and tower.interval() < previous_interval and tower.turn_speed() > previous_turn,
			"آسیب، برد، سرعت شلیک و سرعت چرخش سطح %d باید رشد کنند" % level)
		test.check(visual.base.texture == base_texture and visual.base.transform == base_transform and tower.position == tower_position,
			"پایه و جای برج در ارتقا به سطح %d نباید تغییر کنند" % level)
		test.check(visual.head.texture == Assets.tex("towers_machinegun/head_%d_directions" % level)
			and visual.head_next.texture == visual.head.texture,
			"ظاهر سر جهت‌دار در سطح %d عوض نشده است" % level)
		test.check(visual.head.hframes == 8 and visual.head.vframes == 6
			and visual.head.texture.get_size() == Vector2(1024, 768),
			"اطلس سر سطح %d باید ۴۸ نمای هم‌اندازه داشته باشد" % level)
		previous_damage = tower.damage()
		previous_range = tower.attack_range()
		previous_interval = tower.interval()
		previous_turn = tower.turn_speed()
		if level < 5:
			tower.upgrade()
	var invested: int = tower.invested
	tower.upgrade()
	test.check(tower.max_level() and tower.level == 5 and tower.invested == invested,
		"ارتقای ششم نباید سطح یا هزینه را عوض کند")
	test.check(visual.barrels.hframes == 16 and visual.barrels.vframes == 24
		and visual.barrels.texture.get_size() == Vector2(2048, 3072)
		and Assets.tex("towers_machinegun/barrels_5_spin").get_size() == Vector2(2048, 256),
		"گاتلینگ باید ۸ فریم مستقل در هر یک از ۴۸ جهت و شیت مستقل داشته باشد")
	# Even at maximum armory rate, the old global floor must not flatten upgrades.
	var saved_levels: Dictionary = Game.weapon_levels.duplicate(true)
	Game.weapon_levels["machinegun"] = Balance.ARMORY_MAX_LEVEL
	var prior_interval := INF
	for level in range(1, 6):
		tower.level = level
		test.check(tower.interval() < prior_interval, "ارتقای سرعت مسلسل با بیشترین پاداش زرادخانه باید اثر کند")
		prior_interval = tower.interval()
	tower.level = 5
	Game.weapon_levels = saved_levels

	# Whole-circle alignment in small angular increments, not eight snapped rows.
	for degrees in range(0, 360, 15):
		var angle := deg_to_rad(float(degrees))
		var screen_vector := Vector2(cos(angle), sin(angle) * MachinegunVisual.DEPTH_SQUASH) * 100.0
		visual.aim_at(screen_vector, 100.0, 1.0)
		test.check(visual.forward_global().dot(screen_vector.normalized()) > 0.9999,
			"جهت واقعی لوله در زاویهٔ %d با هدف هم‌راستا نیست" % degrees)
		test.check(visual.base.transform == base_transform and visual.base.rotation == 0.0,
			"پایه هنگام چرخش %d درجه جابه‌جا یا چرخیده است" % degrees)
	# Adjacent rendered views blend continuously, including the 359°→0° seam.
	var half_frame_angle := TAU * 7.5 / float(MachinegunVisual.DIRECTION_COUNT)
	visual.mount.rotation = TAU * 7.0 / float(MachinegunVisual.DIRECTION_COUNT)
	visual.aim_at(Vector2(cos(half_frame_angle), sin(half_frame_angle) * MachinegunVisual.DEPTH_SQUASH), 100.0, 1.0)
	test.check(visual.head.frame == 7 and visual.head_next.frame == 8
		and absf(visual.head.modulate.a - 0.5) < 0.001 and absf(visual.head_next.modulate.a - 0.5) < 0.001,
		"سر در میانهٔ دو نما باید پیوسته ترکیب شود، نه یک‌باره بپرد")
	var seam_angle := TAU * 47.5 / float(MachinegunVisual.DIRECTION_COUNT)
	visual.mount.rotation = TAU * 47.0 / float(MachinegunVisual.DIRECTION_COUNT)
	visual.aim_at(Vector2(cos(seam_angle), sin(seam_angle) * MachinegunVisual.DEPTH_SQUASH), 100.0, 1.0)
	test.check(visual.head.frame == 47 and visual.head_next.frame == 0
		and absf(visual.head_next.modulate.a - 0.5) < 0.001,
		"ترکیب نماها باید در مرز ۳۶۰ و صفر درجه نیز پیوسته باشد")
	visual.mount.rotation = deg_to_rad(179.0)
	var wrap_target := deg_to_rad(-179.0)
	visual.aim_at(Vector2(cos(wrap_target), sin(wrap_target) * MachinegunVisual.DEPTH_SQUASH), 1.0, 0.01)
	var turned := angle_difference(deg_to_rad(179.0), visual.mount.rotation)
	test.check(turned > 0.0 and turned <= 0.0101, "چرخش از ۱۷۹ به منفی ۱۷۹ باید نرم و از مسیر کوتاه باشد")

	# Spin is independent of both heading and base; recoil belongs only to barrels.
	var heading := visual.mount.rotation
	visual.fired()
	var expected_art_recoil: Vector2 = Vector2(-cos(heading), -sin(heading) * MachinegunVisual.DEPTH_SQUASH) * (
		MachinegunVisual.RECOIL_PIXELS * MachinegunVisual.DIRECTION_CELL / MachinegunVisual.FRAME)
	test.check(visual.barrel_mount.position.x < 0.0 and visual.base.transform == base_transform
		and visual.barrel_art.position.distance_to(expected_art_recoil) < 0.001,
		"پس‌زنی تصویری و دهانهٔ واقعی باید فقط همراه لوله جابه‌جا شوند")
	var first_barrel_frame: int = visual.barrels.frame
	visual.advance(0.06)
	test.check(visual.barrels.frame >= MachinegunVisual.DIRECTION_COUNT and visual.barrels.frame != first_barrel_frame
		and visual.mount.rotation == heading and visual.base.transform == base_transform,
		"انیمیشن گاتلینگ باید مستقل از چرخش سر و پایه باشد")
	visual.advance(0.2)
	var stopped_frame := visual.barrels.frame
	visual.advance(0.2)
	test.check(visual.barrels.frame == stopped_frame and is_zero_approx(visual.barrel_mount.position.x),
		"پس از شلیک، لوله باید از پس‌زنی برگردد و بدون شلیک نچرخد")

	# Default nearest acquisition, sticky lock, out-of-range/dead/removed retargeting.
	var near := _enemy(test, tower.position + Vector2(85, 0))
	var far := _enemy(test, tower.position + Vector2(-150, 0))
	var outside := _enemy(test, tower.position + Vector2(500, 0))
	battle.enemies = [far, outside, near]
	tower.target = null
	tower.cooldown_left = 100.0
	tower._process(0.02)
	test.check(tower.target == near, "مسلسل باید نزدیک‌ترین دشمن داخل برد را انتخاب کند")
	far.position = tower.position + Vector2(50, -15)
	tower._process(0.02)
	test.check(tower.target == near, "هدف زنده داخل برد باید تا خروج یا نابودی حفظ شود")
	near.position = tower.position + Vector2(500, 0)
	tower._process(0.02)
	test.check(tower.target == far, "پس از خروج هدف از برد باید هدف جدید انتخاب شود")
	far.alive = false
	near.position = tower.position + Vector2(110, 0)
	tower._process(0.02)
	test.check(tower.target == near, "پس از نابودی هدف باید دشمن زندهٔ بعدی انتخاب شود")
	battle.enemies = []
	tower._process(0.02)
	test.check(tower.target == null and visual.firing_left == 0.0,
		"هدف زنده‌ای که از فهرست میدان حذف شده نباید نگه داشته شود")
	var shots_when_empty: int = tower._shot_count
	tower.cooldown_left = 0.0
	tower._process(0.5)
	test.check(tower._shot_count == shots_when_empty and tower.target == null,
		"برج بدون دشمن نباید شلیک کند")
	# A long idle/turn frame must not be replayed as several instant shots on acquisition.
	near.position = tower.position + Vector2(110, 0)
	battle.enemies = [near]
	tower._process(1.0)
	test.check(tower._shot_count == shots_when_empty + 1, "زمان بی‌هدفی نباید به رگبار انباشته تبدیل شود")
	for child in battle.get_children():
		child.queue_free()

	# Aim a real shot, check muzzle/flash/casing and sweep across the target.
	far.alive = true
	far.position = near.position
	battle.enemies = [near, far]
	tower.target = near
	var direction_to_target: Vector2 = Projectile.target_point_global(near) - visual.pivot_global()
	visual.aim_at(direction_to_target, 100.0, 1.0)
	tower.cooldown_left = 0.0
	var initial_hp: float = near.hp
	var other_hp: float = far.hp
	tower._process(0.016)
	var shots := _projectiles(battle)
	var casings := _casings(battle)
	test.check(shots.size() == 1 and casings.size() == 1 and tower._flash.visible,
		"هر رگبار باید یک گلوله، شعلهٔ دهانه و پوکه تولید کند")
	if shots.size() == 1:
		var shot: Projectile = shots[0]
		var forward := visual.forward_global()
		test.check(shot.straight and shot._dir.dot(forward) > 0.99999
			and shot.global_position.distance_to(visual.muzzle_global()) < 0.01
			and tower.to_global(tower._flash.position).distance_to(shot.global_position) < 0.01,
			"گلوله و شعله باید از دهانه و دقیقاً در جهت واقعی لوله خارج شوند")
		var start := shot.global_position
		shot._process(0.02)
		test.check((shot.global_position - start).normalized().dot(forward) > 0.99999,
			"گلوله نباید پس از شلیک خودسرانه به هدف بچرخد")
		shot._process(0.5)
		test.check(shot.is_queued_for_deletion() and is_equal_approx(near.hp, initial_hp - tower.damage())
			and far.hp == other_hp, "برخورد جاروبی باید فقط به هدف انتخاب‌شده آسیب واقعی بزند")

	# Moving a target away after launch does not turn a bullet into a homing shot.
	var miss := Projectile.new()
	miss.setup(tower, near, tower.damage(), Vector2.RIGHT)
	battle.add_child(miss)
	miss.global_position = Vector2(250, 250)
	near.position = Vector2(450, 420)
	var hp_before_miss: float = near.hp
	miss._process(0.05)
	test.check(miss.global_position.distance_to(Vector2(291, 250)) < 0.01 and near.hp == hp_before_miss,
		"گلولهٔ مستقیم نباید هدفِ جابه‌جاشده را تعقیب یا بدون برخورد زخمی کند")
	miss.queue_free()

	# A moving target crossing a fast shot between frame endpoints is a real hit.
	near.position = Vector2(300, 222) # hit point starts at (300, 210)
	var crossing := Projectile.new()
	crossing.setup(tower, near, tower.damage(), Vector2.RIGHT)
	battle.add_child(crossing)
	crossing.global_position = Vector2(250, 250)
	near.position = Vector2(300, 302) # hit point ends at (300, 290)
	var hp_before_crossing: float = near.hp
	crossing._process(0.1)
	test.check(crossing.is_queued_for_deletion() and is_equal_approx(near.hp, hp_before_crossing - tower.damage()),
		"برخورد جاروبی باید عبور دشمن متحرک از مسیر گلوله را نیز تشخیص دهد")

	# A lethal real projectile must release the old target and acquire another.
	var doomed := _enemy(test, tower.position + Vector2(70, 0))
	doomed.hp = 1.0
	far.alive = true
	far.position = tower.position + Vector2(120, 40)
	battle.enemies = [doomed, far]
	tower.target = doomed
	var killing_shot := Projectile.new()
	killing_shot.setup(tower, doomed, tower.damage(), Vector2.RIGHT)
	battle.add_child(killing_shot)
	killing_shot.global_position = Projectile.target_point_global(doomed) - Vector2(30, 0)
	killing_shot._process(0.1)
	tower.cooldown_left = 100.0
	tower._process(0.016)
	test.check(not doomed.alive and tower.target == far, "پس از نابودی واقعی هدف، برج باید دشمن بعدی را بگیرد")
	doomed.queue_free()

	# Live pursuit and repeated firing in a fixed-step moving-enemy simulation.
	near.position = tower.position + Vector2(110, -60)
	far.alive = false
	battle.enemies = [near]
	tower.target = null
	tower.cooldown_left = 0.0
	var sim_hp: float = near.hp
	var shots_before_sim: int = tower._shot_count
	for frame in 120:
		near.position.y += 35.0 / 60.0
		tower._process(1.0 / 60.0)
		for projectile in _projectiles(battle):
			projectile._process(1.0 / 60.0)
	test.check(tower.target == near and tower._shot_count > shots_before_sim + 8 and near.hp < sim_hp,
		"مسلسل باید دشمن متحرک را دنبال و با رگبار برخوردی زخمی کند")
	test.check(visual.base.transform == base_transform and visual.base.texture == base_texture
		and tower.position == tower_position, "پایه باید در سراسر هدف‌گیری و رگبار کاملاً ثابت بماند")
	battle.enemies = []
	tower.queue_free()
	battle.queue_free()
	near.queue_free()
	far.queue_free()
	outside.queue_free()
