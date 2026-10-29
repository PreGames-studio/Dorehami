## Real five-level laser behavior: fixed pedestal, aligned and frozen bore,
## bounded persistent beam, swept enemy hit circles and one hit per enemy.
extends RefCounted

class TestBattle extends Node2D:
	var enemies: Array = []
	func fx_layer() -> Node:
		return self

class CountingEnemy extends Enemy:
	var calls := 0
	var amounts: Array = []
	func take_damage(amount: float, pierce_armor := false, pierce_shield := false) -> void:
		calls += 1
		amounts.append(amount)
		super.take_damage(amount, pierce_armor, pierce_shield)

class TestProfile extends "res://src/autoload/Game.gd":
	func save() -> void:
		pass

func _tower(test, battle, start_level := 1) -> Tower:
	var tower := Tower.new()
	test.add_child(tower)
	tower.set_process(false)
	tower.position = Vector2(400, 300)
	tower.setup(Weapons.by_id("laser"), battle, start_level)
	return tower

## Position specifies the same global hit centre used by real projectiles.
func _enemy(parent: Node, center: Vector2, hp_value := 1000.0, armor_value := 0.0,
		shield_value := 0.0) -> CountingEnemy:
	var enemy := CountingEnemy.new()
	parent.add_child(enemy)
	enemy.set_process(false)
	enemy.global_position = center + Vector2(0.0, 12.0)
	enemy.hp = hp_value
	enemy.max_hp = hp_value
	enemy.armor = armor_value
	enemy.shield = shield_value
	enemy.alive = true
	return enemy

func _beams(parent: Node) -> Array:
	var found: Array = []
	for child in parent.get_children():
		if child is LaserBeam and not child.is_queued_for_deletion():
			found.append(child)
	return found

func _projectiles(parent: Node) -> Array:
	var found: Array = []
	for child in parent.get_children():
		if child is Projectile and not child.is_queued_for_deletion():
			found.append(child)
	return found

func run(test) -> void:
	var saved_levels: Dictionary = Game.weapon_levels.duplicate(true)
	Game.weapon_levels["laser"] = 0
	_test_economy_and_visual(test)
	_test_ray_clipping_and_geometry(test)
	_test_targeting_and_loss(test)
	_test_all_direction_shots(test)
	_test_each_level_shot(test)
	_test_piercing_and_moving_enemies(test)
	_test_reload_and_direction_lock(test)
	_test_upgrade_after_shot(test)
	_test_upgrade_and_sold_source(test)
	_test_list_mutation_and_zero_damage(test)
	_test_crowd(test)
	_test_transformed_world(test)
	_test_margin_shot(test)
	_test_real_battle(test)
	Game.weapon_levels = saved_levels

func _test_economy_and_visual(test) -> void:
	var weapon := Weapons.by_id("laser")
	test.check(int(weapon.get("cost", 0)) == 170 and int(weapon.get("star_cost", 0)) == 9
		and String(weapon.get("kind", "")) == "beam" and not weapon.has("bullet")
		and bool(weapon.get("armor_pierce", false)), "لیزر باید قیمت قبلی و زره‌شکافی را با شلیک پرتو حفظ کند")
	var profile := TestProfile.new()
	profile._apply(profile.fresh_profile())
	profile.stars = 8
	test.check(not profile.unlock_weapon("laser") and profile.stars == 8, "لیزر با کمتر از ۹ ستاره باز نشود")
	profile.stars = 9
	test.check(profile.unlock_weapon("laser") and profile.stars == 0
		and profile.is_weapon_unlocked("laser") and profile.is_weapon_unlocked("machinegun"),
		"بازکردن لیزر باید فقط ۹ ستاره کم کند و برج‌های قبلی را حفظ کند")
	profile.free()
	test.check(Audio.WEAPON_SOUND_VARIANTS.get("shot_laser", []).size() == 2
		and Assets.sound("combat/laser-beam-a") != null and Assets.sound("combat/laser-beam-b") != null,
		"دو صدای پرتو باید در استخر رزمی ثبت و قابل بارگذاری باشند")

	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var visual := tower._laser_visual
	test.check(visual != null and tower.is_laser() and tower.level_cap() == 5 and tower._flash == null,
		"لیزر باید ظاهر مستقل، پنج سطح و نور دهانهٔ اختصاصی داشته باشد")
	if visual == null:
		tower.queue_free()
		battle.queue_free()
		return
	var stable_base: Transform2D = visual.base.transform
	var text := FileAccess.get_file_as_string("res://assets/sprites/towers_laser/manifest.json")
	var manifest = JSON.parse_string(text)
	test.check(manifest is Dictionary and manifest.get("axis_centers", []).size() == 5,
		"مانیفست لیزر باید محورهای دقیق پنج سطح را داشته باشد")
	var old_damage := 0.0
	var old_range := 0.0
	var old_width := 0.0
	var old_light := 0.0
	var old_duration := 0.0
	var old_reload := INF
	var old_turn := 0.0
	for level in range(1, 6):
		var image := Assets.tex("towers_laser/laser_%d" % level)
		test.check(image != null and image.get_size() == Vector2(384, 384), "تصویر مستقل لیزر سطح %d گم است" % level)
		if image != null:
			var data := image.get_image()
			test.check(data != null and data.get_pixel(0, 0).a == 0.0 and data.get_pixel(383, 383).a == 0.0,
				"تصویر لیزر سطح %d باید آلفای واقعی داشته باشد" % level)
		test.check(visual.base.texture == Assets.tex("towers_laser/base_%d" % level)
			and visual.base.transform == stable_base and is_zero_approx(visual.base.rotation)
			and visual.head.texture == Assets.tex("towers_laser/head_%d_directions" % level)
			and visual.head_next.texture == visual.head.texture
			and visual.head.hframes == 8 and visual.head.vframes == 6,
			"پایهٔ سطح %d باید ثابت و سر ۴۸جهتی آن مستقل باشد" % level)
		var center: Vector2 = LaserVisual.AXIS_CENTERS[level - 1]
		test.check(visual.projection.position.distance_to((center - LaserVisual.GROUND) * LaserVisual.IMAGE_SCALE) < 0.001,
			"محور سر سطح %d باید با دارایی هم‌تراز باشد" % level)
		if manifest is Dictionary and manifest.get("axis_centers", []).size() == 5:
			var recorded: Array = manifest["axis_centers"][level - 1]
			var recorded_length := float(manifest["muzzle_lengths"][level - 1])
			test.check(center.distance_to(Vector2(float(recorded[0]), float(recorded[1]))) < 0.001
				and absf(recorded_length - float(LaserVisual.MUZZLE_LENGTHS[level - 1])) < 0.001,
				"محور و دهانهٔ سطح %d باید با مانیفست یکسان باشند" % level)
		test.check(tower.damage() > old_damage and tower.attack_range() > old_range
			and tower.laser_width() > old_width and LaserBeam.brightness(level) > old_light
			and tower.laser_duration() > old_duration and tower.interval() < old_reload and tower.turn_speed() > old_turn,
			"با ارتقای %d قدرت، برد و درخشش باید بهتر و بارگذاری کوتاه‌تر شوند" % level)
		old_damage = tower.damage()
		old_range = tower.attack_range()
		old_width = tower.laser_width()
		old_light = LaserBeam.brightness(level)
		old_duration = tower.laser_duration()
		old_reload = tower.interval()
		old_turn = tower.turn_speed()
		if level < 5:
			test.check(tower.upgrade_cost() == 170, "هر ارتقای لیزر باید همان ۱۷۰ سکه باشد")
			tower.upgrade()
	var invested: int = tower.invested
	tower.upgrade()
	test.check(tower.level == 5 and tower.max_level() and tower.invested == invested,
		"لیزر نباید سطح ششم یا هزینهٔ اضافی داشته باشد")
	var ghost := BuildGhost.new()
	test.add_child(ghost)
	ghost.set_weapon(weapon)
	test.check(Weapons.icon("laser") == Assets.tex("towers_laser/icon")
		and ghost._base.texture == Assets.tex("towers_laser/laser_1")
		and ghost._base.offset == visual.base.offset and ghost._base.scale == visual.base.scale
		and ghost._base.position == visual.position,
		"پیش‌نمای ساخت باید اندازه و محل واقعی پایه را حفظ کند")
	ghost.queue_free()

	for degrees in range(0, 360, 15):
		var angle := deg_to_rad(float(degrees))
		var direction := Vector2(cos(angle), sin(angle) * LaserVisual.DEPTH_SQUASH)
		visual.aim_at(direction, 100.0, 1.0)
		var muzzle_direction := (visual.muzzle_global() - visual.pivot_global()).normalized()
		test.check(visual.forward_global().dot(direction.normalized()) > 0.9999
			and muzzle_direction.dot(visual.forward_global()) > 0.9999
			and visual.base.transform == stable_base and is_zero_approx(visual.base.rotation),
			"در زاویهٔ %d دهانه باید هم‌جهت پرتو و پایه بی‌حرکت باشد" % degrees)
	visual.mount.rotation = deg_to_rad(179.0)
	var wrapped_angle := deg_to_rad(-179.0)
	visual.aim_at(Vector2(cos(wrapped_angle), sin(wrapped_angle) * LaserVisual.DEPTH_SQUASH), 1.0, 0.01)
	test.check(angle_difference(deg_to_rad(179.0), visual.mount.rotation) > 0.0
		and angle_difference(deg_to_rad(179.0), visual.mount.rotation) <= 0.0101,
		"سر لیزر باید از کوتاه‌ترین مسیر و با سرعت محدود بچرخد")
	visual.mount.rotation = TAU - 0.01
	var seam_target := TAU - 0.001
	visual.aim_at(Vector2(cos(seam_target), sin(seam_target) * LaserVisual.DEPTH_SQUASH), 100.0, 1.0)
	test.check(visual.head.frame == 47 and visual.head_next.frame == 0 and visual.head_next.modulate.a > 0.0,
		"فریم‌های لیزر باید در انتهای چرخش به نرمی به فریم اول برسند")
	tower.queue_free()
	battle.queue_free()

func _test_ray_clipping_and_geometry(test) -> void:
	var bounds := Rect2(0, 0, 1280, 720)
	var center := Vector2(400, 300)
	var cases := [
		[Vector2.RIGHT, Vector2(1280, 300)], [Vector2.LEFT, Vector2(0, 300)],
		[Vector2.UP, Vector2(400, 0)], [Vector2.DOWN, Vector2(400, 720)],
		[Vector2(1, 1), Vector2(820, 720)], [Vector2(-1, -1), Vector2(100, 0)],
	]
	for item in cases:
		var segment := LaserBeam.clip_ray(center, item[0], bounds)
		test.check(segment.size() == 2 and segment[0].distance_to(center) < 0.01
			and segment[1].distance_to(item[1]) < 0.01,
			"پرتو باید بدون توقف تا مرز درست میدان ادامه یابد: %s" % [item])
	var entering := LaserBeam.clip_ray(Vector2(-40, 300), Vector2.RIGHT, bounds)
	test.check(entering.size() == 2 and entering[0].distance_to(Vector2(0, 300)) < 0.01
		and entering[1].distance_to(Vector2(1280, 300)) < 0.01,
		"پرتوی بیرون میدان باید فقط بخش داخل مرز را داشته باشد")
	test.check(LaserBeam.clip_ray(Vector2(-40, 300), Vector2.LEFT, bounds).is_empty()
		and LaserBeam.clip_ray(center, Vector2.ZERO, bounds).is_empty(),
		"پرتوی رو به بیرون یا با جهت صفر نباید مسیر نامعتبر بسازد")
	test.check(is_zero_approx(LaserBeam.segment_distance_squared(Vector2(0, 0), Vector2(100, 0),
		Vector2(50, -30), Vector2(50, 30)))
		and is_equal_approx(LaserBeam.segment_distance_squared(Vector2(0, 0), Vector2(100, 0),
		Vector2(50, 20), Vector2(80, 20)), 400.0), "فاصلهٔ برخورد باید عبور سریع و خطای خارج مسیر را تشخیص دهد")

func _test_targeting_and_loss(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var axis: Vector2 = tower._laser_visual.pivot_global()
	var weak := _enemy(test, axis + Vector2(65, 0), 100.0)
	var strong := _enemy(test, axis + Vector2(0, -60), 200.0)
	var outside := _enemy(test, axis + Vector2(350, 0), 500.0)
	battle.enemies = [weak, strong, outside]
	var fixed_base: Transform2D = tower._laser_visual.base.transform
	tower.cooldown_left = 2.0
	test.check(tower._pick_target() == strong, "لیزر باید قوی‌ترین دشمن داخل برد را انتخاب کند")
	tower._process(0.01)
	var first_turn: float = tower._laser_visual.mount.rotation
	test.check(tower.target == strong and absf(first_turn) > 0.0 and absf(first_turn) <= tower.turn_speed() * 0.01 + 0.0001
		and tower._laser_visual.base.transform == fixed_base and tower._shot_count == 0,
		"در هدف‌گیری فقط سر باید نرم و با سرعت محدود بچرخد")
	battle.enemies.erase(strong) # Alive, but no longer in the battle.
	tower._process(0.01)
	test.check(tower.target == weak and tower._laser_visual.base.transform == fixed_base,
		"حذف هدف زنده باید باعث انتخاب هدف جدید شود")
	battle.enemies.erase(weak)
	tower._process(0.1)
	test.check(tower.target == null and tower._shot_count == 0 and tower.head_action() == "idle",
		"با از دست رفتن همهٔ اهداف در برد نباید شلیک یا خطا رخ دهد")
	var on_edge := _enemy(test, tower.global_position + Vector2(190.0, -12.0))
	var just_outside := _enemy(test, tower.global_position + Vector2(190.1, -12.0))
	test.check(tower._is_in_range(on_edge) and not tower._is_in_range(just_outside),
		"مرز برد هدف‌گیری باید دقیق و مستقل از طول پرتو باشد")
	battle.enemies = []
	for enemy in [weak, strong, outside, on_edge, just_outside]:
		enemy.queue_free()
	tower.queue_free()
	battle.queue_free()

func _test_all_direction_shots(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var visual := tower._laser_visual
	var axis: Vector2 = visual.pivot_global()
	for degrees in range(0, 360, 15):
		var angle := deg_to_rad(float(degrees))
		var forward := Vector2(cos(angle), sin(angle) * LaserVisual.DEPTH_SQUASH).normalized()
		visual.aim_at(forward, 100.0, 1.0)
		var enemy := _enemy(test, axis + forward * 80.0)
		battle.enemies = [enemy]
		tower.target = enemy
		tower.cooldown_left = 0.0
		tower._fire()
		var beams := _beams(battle)
		if beams.size() == 1:
			var beam: LaserBeam = beams[0]
			var on_border := absf(beam.finish.x - Balance.BUILD_RECT.position.x) < 0.01 \
				or absf(beam.finish.x - Balance.BUILD_RECT.end.x) < 0.01 \
				or absf(beam.finish.y - Balance.BUILD_RECT.position.y) < 0.01 \
				or absf(beam.finish.y - Balance.BUILD_RECT.end.y) < 0.01
			test.check(enemy.calls == 1 and beam.direction.dot(forward) > 0.9999
				and beam.start.distance_to(visual.muzzle_global()) < 0.01 and on_border,
				"شلیک زاویهٔ %d باید در امتداد دهانه به هدف و مرز میدان برسد" % degrees)
			beam.queue_free()
		else:
			test.check(false, "پرتوی زاویهٔ %d ساخته نشد" % degrees)
		enemy.queue_free()
	battle.enemies = []
	tower.queue_free()
	battle.queue_free()

func _test_each_level_shot(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var expected_damage := [26.0, 34.0, 43.0, 53.0, 65.0]
	for level in range(1, 6):
		var tower := _tower(test, battle, level)
		var axis: Vector2 = tower._laser_visual.pivot_global()
		var radius := 12.0 + tower.laser_width() * 0.5
		var target := _enemy(test, axis + Vector2(70, 0), 1000.0, 80.0, 0.75)
		var graze := _enemy(test, axis + Vector2(100, radius - 0.1), 1000.0, 50.0, 0.5)
		var outside := _enemy(test, axis + Vector2(100, radius + 0.1))
		battle.enemies = [target, graze, outside]
		tower.target = target
		tower._laser_visual.aim_at(Vector2.RIGHT, 100.0, 1.0)
		tower._fire()
		var beams := _beams(battle)
		if beams.size() == 1:
			var beam: LaserBeam = beams[0]
			test.check(target.calls == 1 and graze.calls == 1 and outside.calls == 0
				and is_equal_approx(target.hp, 1000.0 - expected_damage[level - 1])
				and is_equal_approx(graze.hp, 1000.0 - expected_damage[level - 1])
				and is_equal_approx(beam.damage_value, expected_damage[level - 1])
				and is_equal_approx(beam.beam_width, tower.laser_width())
				and is_equal_approx(beam.duration, tower.laser_duration()) and beam.firing_level == level,
				"آسیب، پهنا، مدت و مرز برخورد شلیک سطح %d باید از همان سطح گرفته شوند" % level)
			beam.queue_free()
		else:
			test.check(false, "پرتوی سطح %d ساخته نشد" % level)
		battle.enemies = []
		for enemy in [target, graze, outside]:
			enemy.queue_free()
		tower.queue_free()
	battle.queue_free()

func _test_piercing_and_moving_enemies(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var visual := tower._laser_visual
	var axis: Vector2 = visual.pivot_global()
	visual.aim_at(Vector2.RIGHT, 100.0, 1.0)
	var primary := _enemy(test, axis + Vector2(65, 0), 1000.0, 50.0, 0.25)
	var second := _enemy(test, axis + Vector2(210, 0))
	var third := _enemy(test, Vector2(1000, axis.y))
	var off_path := _enemy(test, Vector2(700, axis.y + 40.0))
	var behind := _enemy(test, axis - Vector2(50, 0))
	var past_edge := _enemy(test, Vector2(Balance.BUILD_RECT.end.x + 50.0, axis.y))
	var crossing := _enemy(test, Vector2(800, axis.y - 40.0))
	var tank := IsoEnemy.new()
	test.add_child(tank)
	tank.set_process(false)
	tank.global_position = Vector2(900, axis.y + 20.0 + 24.0)
	tank.hp = 1000.0
	tank.max_hp = 1000.0
	tank.armor = 50.0
	battle.enemies = [primary, second, third, off_path, behind, past_edge, crossing, tank]
	tower.target = primary
	tower._fire()
	var beams := _beams(battle)
	test.check(beams.size() == 1 and _projectiles(battle).is_empty() and tower._shot_count == 1,
		"لیزر باید یک پرتو واقعی و بدون گلوله ایجاد کند")
	if beams.size() != 1:
		for enemy in battle.enemies:
			enemy.queue_free()
		tower.queue_free()
		battle.queue_free()
		return
	var beam: LaserBeam = beams[0]
	beam.set_process(false)
	test.check(beam.start.distance_to(visual.muzzle_global()) < 0.01 and absf(beam.finish.x - Balance.BUILD_RECT.end.x) < 0.01
		and absf(beam.finish.y - axis.y) < 0.01 and beam.direction.dot(Vector2.RIGHT) > 0.99999,
		"پرتو باید از دهانه در خط ثابت تا لبهٔ نقشه باشد")
	test.check(beam.muzzle is LaserMuzzle and beam.muzzle.glow != null and beam.muzzle.flash != null and beam.strip != null,
		"نور دهانه، افکت شلیک و نوار پرتو باید اجزای مستقل باشند")
	test.check(primary.calls == 1 and is_equal_approx(primary.hp, 974.0) and second.calls == 1
		and third.calls == 1 and is_equal_approx(second.hp, 974.0) and is_equal_approx(third.hp, 974.0)
		and is_equal_approx(tank.hp, 974.0),
		"لیزر باید از دشمن اول عبور کند و روی سپر و شعاع واقعی تانک هم آسیب اعمال کند")
	test.check(off_path.calls == 0 and behind.calls == 0 and past_edge.calls == 0 and crossing.calls == 0,
		"دشمن خارج مسیر، پشت دهانه یا بیرون مرز نباید آسیب ببیند")
	var initial_start: Vector2 = beam.start
	# The new shield bypass is laser-specific; an ordinary armor-piercing hit
	# still receives the existing shield mitigation.
	off_path.shield = 0.25
	off_path.armor = 50.0
	off_path.take_damage(26.0, true)
	test.check(is_equal_approx(off_path.hp, 980.5), "آسیب غیرلیزری باید همچنان با سپر کاهش یابد")
	var initial_finish: Vector2 = beam.finish
	var initial_angle: float = visual.mount.rotation
	primary.position += Vector2(0, 80)
	tower._process(0.2)
	test.check(beam.start == initial_start and beam.finish == initial_finish and visual.mount.rotation == initial_angle
		and is_equal_approx(tower.cooldown_left, tower.interval()),
		"حرکت هدف نباید جهت پرتو یا سر شلیک‌شده را عوض کند و بارگذاری بعد از پرتو است")
	crossing.global_position = Vector2(800, axis.y + 40.0 + 12.0)
	beam._process(0.10)
	beam._process(0.10)
	test.check(crossing.calls == 1 and is_equal_approx(crossing.hp, 974.0)
		and primary.calls == 1 and second.calls == 1 and third.calls == 1,
		"عبور سریع از پرتو باید تشخیص داده شود ولی هیچ دشمنی دو بار آسیب نبیند")
	beam._process(1.0)
	test.check(beam.finished and beam.is_queued_for_deletion(), "پرتو باید پس از زمان تنظیم‌شده تمام شود")
	for enemy in battle.enemies:
		enemy.queue_free()
	battle.enemies = []
	tower.queue_free()
	battle.queue_free()

func _test_reload_and_direction_lock(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var axis: Vector2 = tower._laser_visual.pivot_global()
	var target := _enemy(test, axis + Vector2(70, 0))
	battle.enemies = [target]
	tower._process(0.01)
	var beams := _beams(battle)
	test.check(tower._shot_count == 1 and beams.size() == 1, "هدف در برد و هم‌راستا باید خودکار شلیک شود")
	if beams.is_empty():
		target.queue_free()
		tower.queue_free()
		battle.queue_free()
		return
	var beam: LaserBeam = beams[0]
	beam.set_process(false)
	var angle: float = tower._laser_visual.mount.rotation
	battle.enemies.erase(target)
	target.queue_free()
	var replacement := _enemy(test, axis + Vector2(70, 0))
	battle.enemies = [replacement]
	tower._process(0.20)
	test.check(tower._laser_visual.mount.rotation == angle and is_equal_approx(tower.cooldown_left, 0.60)
		and tower.head_action() == "fire", "حتی با حذف هدف جهت پرتو و بارگذاری باید تا پایان شلیک قفل باشند")
	beam._process(0.33)
	test.check(replacement.calls == 1, "دشمن جدید روی مسیر باید حتی بعد از حذف هدف نخست آسیب ببیند")
	tower._process(0.59)
	test.check(tower._shot_count == 1 and tower.cooldown_left > 0.0, "قبل از پایان بارگذاری نباید شلیک دوم رخ دهد")
	tower._fire()
	test.check(tower._shot_count == 1, "حتی فراخوانی مستقیم در زمان بارگذاری نباید پرتو تازه بسازد")
	tower._process(0.02)
	test.check(tower._shot_count == 2 and replacement.calls == 2 and _beams(battle).size() == 1,
		"پس از پایان بارگذاری پرتو دوم باید به هدف جایگزین آسیب بزند")
	for shot in _beams(battle):
		shot.queue_free()
	battle.enemies = []
	replacement.queue_free()
	tower.queue_free()
	battle.queue_free()

func _test_upgrade_after_shot(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var axis: Vector2 = tower._laser_visual.pivot_global()
	var enemy := _enemy(test, axis + Vector2(70, 0))
	battle.enemies = [enemy]
	tower.target = enemy
	tower._laser_visual.aim_at(Vector2.RIGHT, 100.0, 1.0)
	tower._fire()
	var old_beam := _beams(battle)[0] as LaserBeam
	old_beam.set_process(false)
	tower.upgrade()
	tower.upgrade()
	test.check(tower.level == 3 and tower._pending_laser_level == 3 and tower._laser_visual.current_level == 1,
		"چند ارتقا در شلیک باید تا پایان پرتو از نظر ظاهری به تعویق بیفتند")
	old_beam._process(0.33)
	tower._process(0.01)
	test.check(tower._pending_laser_level == 0 and tower._laser_visual.current_level == 3
		and tower._laser_visual.base.texture == Assets.tex("towers_laser/base_3"),
		"پس از پایان پرتو باید آخرین سطح ارتقا روی پایه و سر اعمال شود")
	# Level 3 moves the barrel upward. Put the target on that new axis.
	var level_three_axis: Vector2 = tower._laser_visual.pivot_global()
	enemy.global_position = level_three_axis + Vector2(70, 12)
	tower._process(0.60)
	var beams := _beams(battle)
	test.check(beams.size() == 1 and tower._shot_count == 2 and enemy.calls == 2
		and is_equal_approx(enemy.hp, 931.0) and is_equal_approx(beams[0].damage_value, 43.0)
		and is_equal_approx(beams[0].beam_width, 6.8),
		"شلیک بعدی باید از قدرت و ضخامت ارتقای جدید استفاده کند")
	battle.enemies = []
	enemy.queue_free()
	tower.queue_free()
	battle.queue_free()

func _test_upgrade_and_sold_source(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var axis: Vector2 = tower._laser_visual.pivot_global()
	var first := _enemy(test, axis + Vector2(65, 0))
	var later := _enemy(test, axis + Vector2(160, 40))
	var after_sale := _enemy(test, axis + Vector2(220, 40))
	battle.enemies = [first, later, after_sale]
	tower.target = first
	tower._laser_visual.aim_at(Vector2.RIGHT, 100.0, 1.0)
	tower._fire()
	var beam := _beams(battle)[0] as LaserBeam
	beam.set_process(false)
	var old_start: Vector2 = beam.start
	var old_finish: Vector2 = beam.finish
	tower.upgrade()
	test.check(tower.level == 2 and tower._laser_visual.current_level == 1 and tower._pending_laser_level == 2
		and is_equal_approx(beam.damage_value, 26.0) and is_equal_approx(beam.beam_width, 3.6)
		and is_equal_approx(beam.duration, 0.32) and beam.start == old_start and beam.finish == old_finish,
		"ارتقا نباید محل، زمان یا قدرت پرتو فعال را تغییر دهد")
	later.position.y -= 40.0
	beam._process(0.1)
	test.check(later.calls == 1 and is_equal_approx(later.hp, 974.0), "پرتوی قبلی باید قدرت لحظهٔ شلیک را حفظ کند")
	tower.free()
	after_sale.position.y -= 40.0
	beam._process(0.1)
	test.check(is_instance_valid(beam) and after_sale.calls == 1 and is_equal_approx(after_sale.hp, 974.0),
		"فروش برج نباید پرتو موجود یا آسیب واقعی آن را قطع کند")
	beam.queue_free()
	battle.enemies = []
	first.queue_free()
	later.queue_free()
	after_sale.queue_free()
	battle.queue_free()

func _test_list_mutation_and_zero_damage(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var axis: Vector2 = tower._laser_visual.pivot_global()
	var doomed := _enemy(test, axis + Vector2(60, 0), 1.0)
	var removed := _enemy(test, axis + Vector2(100, 0))
	var last := _enemy(test, axis + Vector2(140, 0))
	battle.enemies = [doomed, removed, last, last]
	doomed.died.connect(func(_victim):
		battle.enemies.erase(doomed)
		battle.enemies.erase(removed)
	)
	tower.target = doomed
	tower._laser_visual.aim_at(Vector2.RIGHT, 100.0, 1.0)
	tower._fire()
	test.check(not doomed.alive and doomed.calls == 1 and removed.calls == 0 and last.calls == 1
		and not doomed.has_meta(LaserBeam.DEATH_FX_CONTEXT),
		"حذف هم‌زمان دشمن نباید آسیب به عضو حذف‌شده یا آسیب تکراری ایجاد کند")
	for shot in _beams(battle):
		shot.queue_free()
	var zero := _enemy(test, Vector2(700, 300))
	battle.enemies = [zero]
	var zero_beam := LaserBeam.new()
	battle.add_child(zero_beam)
	zero_beam.set_process(false)
	zero_beam.setup(Vector2(400, 300), Vector2.RIGHT, battle, 0.0, false, 0.3, 4.0, 1)
	zero_beam._process(0.1)
	test.check(zero.calls == 0 and is_equal_approx(zero.hp, 1000.0),
		"آسیب صفر نباید به حداقل آسیب یک در Enemy تبدیل شود")
	zero_beam.queue_free()
	# A long frame can straddle the shot's end. Only the fraction of each
	# enemy's swept movement that happened during the shot may be hit.
	var early := _enemy(test, Vector2(700, 250))
	var late := _enemy(test, Vector2(800, 200))
	battle.enemies = [early, late]
	var short_beam := LaserBeam.new()
	battle.add_child(short_beam)
	short_beam.set_process(false)
	short_beam.setup(Vector2(400, 300), Vector2.RIGHT, battle, 26.0, true, 0.1, 3.6, 1)
	early.global_position = Vector2(700, 350 + 12)
	late.global_position = Vector2(800, 330 + 12)
	short_beam._process(0.2)
	test.check(early.calls == 1 and late.calls == 0 and short_beam.finished,
		"در فریم بلند باید فقط عبور پیش از پایان مدت پرتو آسیب ببیند")
	battle.enemies = []
	for enemy in [doomed, removed, last, zero, early, late]:
		enemy.queue_free()
	tower.queue_free()
	battle.queue_free()

func _test_crowd(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var axis: Vector2 = tower._laser_visual.pivot_global()
	var crowd: Array = []
	for index in 100:
		crowd.append(_enemy(test, Vector2(axis.x + 45.0 + float(index) * 6.0, axis.y)))
	battle.enemies = crowd.duplicate()
	tower.target = crowd[0]
	tower._laser_visual.aim_at(Vector2.RIGHT, 100.0, 1.0)
	tower._fire()
	var hit_count := 0
	var duplicates := false
	for enemy in crowd:
		hit_count += 1 if enemy.calls == 1 else 0
		duplicates = duplicates or enemy.calls > 1
	var labels := 0
	for child in battle.get_children():
		if child is FloatingText:
			labels += 1
	test.check(hit_count == 100 and not duplicates and _beams(battle).size() == 1
		and labels <= LaserBeam.MAX_DAMAGE_LABELS and _projectiles(battle).is_empty(),
		"پرتو باید از ۱۰۰ دشمن بگذرد؛ آسیب محدود نباشد و افکت‌ها کنترل شوند")
	battle.enemies = []
	for enemy in crowd:
		enemy.queue_free()
	tower.queue_free()
	battle.queue_free()

func _test_transformed_world(test) -> void:
	# Aim/range and hit radii are checked under translation, rotation and both
	# uniform and nonuniform scale. The enemy circles use the projectile's
	# global-pixel radius; the laser width is transformed with the FX layer.
	for world_scale in [Vector2(1.2, 1.2), Vector2(0.6, 0.6), Vector2(1.2, 0.7)]:
		var world := Node2D.new()
		world.position = Vector2(135, 55)
		world.rotation = 0.27
		world.scale = world_scale
		test.add_child(world)
		var battle := TestBattle.new()
		world.add_child(battle)
		var unit_group := Node2D.new()
		unit_group.position = Vector2(25, 10)
		world.add_child(unit_group)
		var tower := Tower.new()
		unit_group.add_child(tower)
		tower.set_process(false)
		tower.position = Vector2(375, 290)
		tower.setup(Weapons.by_id("laser"), battle)
		tower._laser_visual.aim_at(Vector2.RIGHT, 100.0, 1.0)
		var axis_world: Vector2 = tower._laser_visual.pivot_global()
		var forward: Vector2 = tower._laser_visual.forward_global()
		var normal := Vector2(-forward.y, forward.x)
		var transformed_normal := battle.to_global(Vector2.DOWN) - battle.to_global(Vector2.ZERO)
		var edge_radius := 12.0 + tower.laser_width() * 0.5 * absf(forward.cross(transformed_normal))
		var enemy := _enemy(unit_group, axis_world + forward * (70.0 * world_scale.x))
		var graze := _enemy(unit_group, axis_world + forward * (150.0 * world_scale.x) + normal * (edge_radius - 0.2))
		var outside := _enemy(unit_group, axis_world + forward * (150.0 * world_scale.x) + normal * (edge_radius + 0.2))
		battle.enemies = [enemy, graze, outside]
		tower.target = enemy
		tower._fire()
		var beams := _beams(battle)
		if beams.size() == 1:
			var beam: LaserBeam = beams[0]
			var expected_edge := battle.to_global(Vector2(Balance.BUILD_RECT.end.x, beam.finish.y))
			test.check(enemy.calls == 1 and graze.calls == 1 and outside.calls == 0
				and beam.to_global(beam.start).distance_to(tower._laser_visual.muzzle_global()) < 0.01
				and beam.to_global(beam.finish).distance_to(expected_edge) < 0.01,
				"مرز و شعاع واقعی برخورد در دنیای چرخیده و مقیاس‌شده باید درست باشند: %s" % [world_scale])
			world.position += normal * 80.0 # A camera/world shake moves everything together.
			beam._process(0.1)
			test.check(enemy.calls == 1 and graze.calls == 1 and outside.calls == 0,
				"لرزش مشترک دنیا نباید عبور یا آسیب ساختگی ایجاد کند")
		else:
			test.check(false, "پرتو در دنیای تبدیل‌شده ساخته نشد")
		battle.enemies = []
		enemy.queue_free()
		graze.queue_free()
		outside.queue_free()
		world.queue_free()

## A tower built in the new margin must still emit from its own muzzle and
## damage an armored, shielded enemy inside the original map.
func _test_margin_shot(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	tower.position = Vector2(-70.0, 300.0)
	var axis: Vector2 = tower._laser_visual.pivot_global()
	var enemy := _enemy(test, axis + Vector2(120.0, 0.0), 100.0, 500.0, 0.9)
	battle.enemies = [enemy]
	tower.target = enemy
	tower._laser_visual.aim_at(Vector2.RIGHT, 100.0, 1.0)
	tower._fire()
	var beams := _beams(battle)
	if beams.size() == 1:
		var beam: LaserBeam = beams[0]
		test.check(beam.start.distance_to(tower._laser_visual.muzzle_global()) < 0.01
			and beam.start.x < 0.0 and is_equal_approx(beam.finish.x, Balance.BUILD_RECT.end.x)
			and enemy.calls == 1 and is_equal_approx(enemy.hp, 74.0),
			"لیزر حاشیه باید از دهانهٔ خود شلیک کند و آسیب کامل به داخل نقشه برساند")
	else:
		test.check(false, "لیزر در حاشیه شلیک نشد")
	battle.enemies = []
	enemy.queue_free()
	tower.queue_free()
	battle.queue_free()

func _test_real_battle(test) -> void:
	var saved_map: int = Game.pending_map
	var saved_last_map: int = Game.last_map
	var saved_unlocked: Array = Game.unlocked_weapons.duplicate()
	Game.pending_map = 0
	Game.unlocked_weapons = Weapons.ids().duplicate()
	var battle = load("res://scenes/Battle.tscn").instantiate()
	test.add_child(battle)
	battle.set_process(false)
	battle.coins = 5000
	for y in range(140, 560, 40):
		for x in range(160, 900, 40):
			var point := Vector2(x, y)
			if battle.map.build_issue(point, battle.occupied).is_empty():
				battle.selected_weapon = "laser"
				battle._try_build(point)
				break
		if not battle.towers.is_empty():
			break
	test.check(battle.towers.size() == 1, "لیزر باید از مسیر ساخت واقعی میدان ساخته شود")
	if not battle.towers.is_empty():
		var tower: Tower = battle.towers[0]
		tower.set_process(false)
		for index in 4:
			battle._spawn_enemy("soldier")
		var enemies: Array = battle.enemies.duplicate()
		var axis: Vector2 = tower._laser_visual.pivot_global()
		for index in enemies.size():
			var enemy: Enemy = enemies[index]
			enemy.set_process(false)
			# Full-strength laser kills armored, shielded enemies through the
			# actual battle/death/reward path; an ordinary shot remains mitigated.
			enemy.hp = tower.damage() - 0.1 if index < 3 else 1.0
			enemy.armor = 999.0 if index < 3 else 0.0
			enemy.shield = 0.95 if index < 3 else 0.0
			var offset := Vector2(60 + 35 * index, 0) if index < 3 else Vector2(90, 60)
			enemy.global_position = axis + offset + Vector2(0, 12.0 * enemy.scale_mult)
		for frame in 240:
			if tower._shot_count > 0:
				break
			tower._process(1.0 / 60.0)
		test.check(tower._shot_count == 1 and not enemies[0].alive and not enemies[1].alive
			and not enemies[2].alive and enemies[3].alive and battle.kills == 3
			and battle.enemies.size() == 1 and _beams(battle.fx_layer()).size() == 1
			and _projectiles(battle.fx_layer()).is_empty(),
			"در نبرد واقعی پرتو باید سه دشمن روی خط را بکشد و دیگری را سالم بگذارد")
		var death_blasts := 0
		for effect in battle.fx_layer().get_children():
			if effect is AnimatedSprite2D:
				death_blasts += 1
		test.check(death_blasts == 0 and not enemies[0].has_meta(LaserBeam.DEATH_FX_CONTEXT),
			"مرگ با لیزر نباید انفجار نارنجی یا نشانهٔ موقت باقی بگذارد")
		enemies[3].take_damage(2.0)
		for effect in battle.fx_layer().get_children():
			if effect is AnimatedSprite2D:
				death_blasts += 1
		test.check(death_blasts == 1, "مرگ عادی دشمن باید افکت قبلی را نگه دارد")
		battle.select_tower(tower)
		battle.hud.refresh()
		test.check(battle.hud._tower_stats.text.contains("بارگذاری") and battle.hud._tower_stats.text.contains("مدت پرتو"),
			"پنل نبرد باید مدت پرتو و زمان بارگذاری را نمایش دهد")
		var coins_before: int = battle.coins
		battle.upgrade_selected()
		test.check(tower.level == 2 and battle.coins == coins_before - 170
			and tower._pending_laser_level == 2, "ارتقای واقعی لیزر باید ۱۷۰ سکه باشد و شلیک فعال را تغییر ندهد")
		var live_beams := _beams(battle.fx_layer())
		if live_beams.size() == 1:
			var live_beam: LaserBeam = live_beams[0]
			live_beam.set_process(false)
			battle._spawn_enemy("soldier")
			var entrant: Enemy = battle.enemies[0]
			entrant.set_process(false)
			entrant.hp = 1.0
			entrant.armor = 0.0
			entrant.shield = 0.0
			entrant.global_position = axis + Vector2(135, 12.0 * entrant.scale_mult)
			var refund := tower.sell_value()
			coins_before = battle.coins
			battle.sell_selected()
			test.check(tower.is_queued_for_deletion() and battle.towers.is_empty()
				and battle.coins == coins_before + refund and not live_beam.is_queued_for_deletion(),
				"فروش واقعی باید هزینه را برگرداند ولی پرتو فعال را نگه دارد")
			live_beam._process(0.1)
			test.check(not entrant.alive and battle.kills == 5 and battle.enemies.is_empty(),
				"پرتوی برج فروخته‌شده باید دشمن تازه را در نبرد واقعی از بین ببرد")
		else:
			test.check(false, "پرتو برای آزمون فروش در نبرد واقعی باقی نماند")
	battle.queue_free()
	Game.pending_map = saved_map
	Game.last_map = saved_last_map
	Game.unlocked_weapons = saved_unlocked
