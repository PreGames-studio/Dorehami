## Behavioral tests of the five-level Tesla: real HP/armor, bounded unique
## lightning hops, continuous aim and the live build/upgrade/death pipeline.
extends RefCounted

class TestBattle extends Node2D:
	var enemies: Array = []
	func fx_layer() -> Node:
		return self
	func remove_dead(enemy) -> void:
		enemies.erase(enemy)

class CountingEnemy extends Enemy:
	var calls := 0
	var amounts: Array = []
	func take_damage(amount: float, pierce_armor := false) -> void:
		calls += 1
		amounts.append(amount)
		super.take_damage(amount, pierce_armor)

class TestProfile extends "res://src/autoload/Game.gd":
	func save() -> void:
		pass

func _tower(test, battle, start_level := 1) -> Tower:
	var tower := Tower.new()
	test.add_child(tower)
	tower.set_process(false)
	tower.position = Vector2(400, 300)
	tower.setup(Weapons.by_id("tesla"), battle, start_level)
	return tower

func _enemy(parent: Node, pos: Vector2, hp_value := 1000.0, armor_value := 0.0, shield_value := 0.0) -> CountingEnemy:
	var enemy := CountingEnemy.new()
	parent.add_child(enemy)
	enemy.set_process(false)
	enemy.position = pos
	enemy.hp = hp_value
	enemy.max_hp = hp_value
	enemy.armor = armor_value
	enemy.shield = shield_value
	enemy.alive = true
	return enemy

func _arcs(parent: Node) -> Array:
	var result: Array = []
	for child in parent.get_children():
		if child is TeslaArc and not child.is_queued_for_deletion():
			result.append(child)
	return result

func _projectiles(parent: Node) -> Array:
	var result: Array = []
	for child in parent.get_children():
		if child is Projectile and not child.is_queued_for_deletion():
			result.append(child)
	return result

func run(test) -> void:
	var saved_levels: Dictionary = Game.weapon_levels.duplicate(true)
	Game.weapon_levels["tesla"] = 0
	_test_economy(test)
	_test_art_levels_and_aim(test)
	_test_nearest_target_and_firing(test)
	_test_chain_at_every_level(test)
	_test_large_wave_budget(test)
	_test_range_boundaries_and_armor(test)
	_test_death_list_mutation(test)
	_test_transformed_world(test)
	_test_real_battle(test)
	Game.weapon_levels = saved_levels

func _test_economy(test) -> void:
	var weapon := Weapons.by_id("tesla")
	test.check(not weapon.is_empty() and int(weapon["cost"]) == 200 and int(weapon["star_cost"]) == 11,
		"تسلا باید سلاح مستقل با هزینهٔ ۲۰۰ سکه و ۱۱ ستاره باشد")
	test.check(not weapon.has("bullet") and String(weapon["kind"]) == "chain"
		and int(weapon["chain"]) == 5, "تسلا نباید گلوله داشته باشد و سقف اهداف آن باید پنج باشد")
	var profile := TestProfile.new()
	profile._apply(profile.fresh_profile())
	profile.stars = 10
	test.check(not profile.is_weapon_unlocked("tesla") and not profile.unlock_weapon("tesla")
		and profile.stars == 10, "تسلا نباید با ستارهٔ ناکافی باز شود")
	profile.stars = 11
	test.check(profile.unlock_weapon("tesla") and profile.is_weapon_unlocked("tesla") and profile.stars == 0
		and profile.is_weapon_unlocked("machinegun"), "بازکردن تسلا باید تنها ۱۱ ستاره کم کند و مسلسل را نگه دارد")
	profile.free()

func _test_art_levels_and_aim(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var visual := tower._tesla_visual
	test.check(visual != null and tower.level_cap() == 5 and tower.is_tesla(),
		"تسلا باید ظاهر مستقل و پنج سطح نبرد داشته باشد")
	if visual == null:
		tower.queue_free()
		battle.queue_free()
		return
	var fixed_texture: Texture2D = visual.base.texture
	var fixed_transform: Transform2D = visual.base.transform
	var idle_angle: float = visual.mount.rotation
	tower._process(0.25)
	test.check(absf(angle_difference(idle_angle, visual.mount.rotation) - TeslaVisual.IDLE_TURN_SPEED * 0.25) < 0.001
		and visual.base.transform == fixed_transform, "در حالت بی‌هدف فقط سر باید آرام بچرخد و پایه ثابت بماند")
	var manifest_text := FileAccess.get_file_as_string("res://assets/sprites/towers_tesla/manifest.json")
	var manifest = JSON.parse_string(manifest_text)
	test.check(manifest is Dictionary and manifest.get("core_centers", []).size() == 5,
		"مانیفست باید محور هر پنج هسته را ثبت کند")
	var previous_damage := 0.0
	var previous_range := 0.0
	var previous_radius := 0.0
	var previous_interval := INF
	var previous_speed := 0.0
	var previous_width := 0.0
	var previous_light := 0.0
	for level in range(1, 6):
		var image := Assets.tex("towers_tesla/tesla_%d" % level)
		test.check(image != null and image.get_size() == Vector2(384, 384), "تصویر سطح %d تسلا گم است" % level)
		if image != null:
			var data := image.get_image()
			test.check(data != null and data.get_pixel(0, 0).a == 0.0 and data.get_pixel(383, 383).a == 0.0,
				"تصویر تسلای سطح %d باید آلفای واقعی داشته باشد" % level)
		test.check(visual.base.texture == fixed_texture and visual.base.transform == fixed_transform
			and visual.body.texture == Assets.tex("towers_tesla/body_%d" % level)
			and visual.head.texture == Assets.tex("towers_tesla/head_%d_directions" % level)
			and visual.head_next.texture == visual.head.texture
			and visual.head.hframes == 8 and visual.head.vframes == 6
			and visual.core.texture == Assets.tex("towers_tesla/core_%d" % level),
			"در سطح %d پایه باید ثابت و سر و هسته لایه‌ای باشند" % level)
		var center: Vector2 = TeslaVisual.CORE_CENTERS[level - 1]
		test.check(visual.projection.position.distance_to((center - TeslaVisual.GROUND) * TeslaVisual.IMAGE_SCALE) < 0.001
			and visual.core.position == visual.projection.position and visual.charge.position == visual.projection.position,
			"محور هسته، سر و محل خروج برق سطح %d باید دقیقاً هم‌تراز باشند" % level)
		if manifest is Dictionary and manifest.get("core_centers", []).size() == 5:
			var recorded: Array = manifest["core_centers"][level - 1]
			test.check(center.distance_to(Vector2(float(recorded[0]), float(recorded[1]))) < 0.001,
				"محور سطح %d باید با مانیفست تصاویر یکسان باشد" % level)
		test.check(tower.damage() > previous_damage and tower.attack_range() > previous_range
			and tower.chain_radius() > previous_radius and tower.interval() < previous_interval
			and tower.turn_speed() > previous_speed and tower.tesla_chain_limit() == 5,
			"آمار تسلا باید رشد کند ولی سقف زنجیره در سطح %d پنج بماند" % level)
		test.check(TeslaArc.stroke_width(level) > previous_width and TeslaArc.brightness(level) > previous_light,
			"برق در سطح %d باید ضخیم‌تر و درخشان‌تر شود" % level)
		previous_width = TeslaArc.stroke_width(level)
		previous_light = TeslaArc.brightness(level)
		previous_damage = tower.damage()
		previous_range = tower.attack_range()
		previous_radius = tower.chain_radius()
		previous_interval = tower.interval()
		previous_speed = tower.turn_speed()
		if level < 5:
			test.check(tower.upgrade_cost() == 200, "هر ارتقای تسلا باید ۲۰۰ سکه باشد")
			tower.upgrade()
	var invested: int = tower.invested
	tower.upgrade()
	test.check(tower.level == 5 and tower.max_level() and tower.invested == invested,
		"تسلا نباید سطح ششم یا هزینهٔ اضافه داشته باشد")
	var ghost := BuildGhost.new()
	test.add_child(ghost)
	ghost.set_weapon(Weapons.by_id("tesla"))
	test.check(ghost._base.texture == Assets.tex("towers_tesla/tesla_1")
		and ghost._base.offset == visual.base.offset and ghost._base.scale == visual.base.scale,
		"پیش‌نمای کاشت باید اندازه و لنگر واقعی تسلا را حفظ کند")
	ghost.queue_free()

	# Projected head follows the full azimuth, takes the shorter path around
	# ±180°, and never rotates or moves the pedestal.
	for degrees in range(0, 360, 15):
		var angle := deg_to_rad(float(degrees))
		var direction := Vector2(cos(angle), sin(angle) * TeslaVisual.DEPTH_SQUASH)
		visual.aim_at(direction, 100.0, 1.0)
		test.check(visual.forward_global().dot(direction.normalized()) > 0.9999
			and visual.base.transform == fixed_transform and is_zero_approx(visual.base.rotation)
			and visual.charge.to_global(visual.charge.emitter_offset()).distance_to(visual.emitter_global()) < 0.01,
			"سر و نقطهٔ خروج برق در زاویهٔ %d باید هم‌جهت هدف و پایه بی‌حرکت باشند" % degrees)
	visual.mount.rotation = deg_to_rad(179.0)
	var wrap_angle := deg_to_rad(-179.0)
	visual.aim_at(Vector2(cos(wrap_angle), sin(wrap_angle) * TeslaVisual.DEPTH_SQUASH), 1.0, 0.01)
	test.check(angle_difference(deg_to_rad(179.0), visual.mount.rotation) > 0.0
		and angle_difference(deg_to_rad(179.0), visual.mount.rotation) <= 0.0101,
		"چرخش تسلا باید نرم و از کوتاه‌ترین مسیر باشد")
	visual.mount.rotation = 0.0
	visual._refresh_head()
	var front_width: float = visual.charge.width_factor
	var front_frame: int = visual.head.frame
	visual.mount.rotation = PI / 2.0
	visual._refresh_head()
	test.check(visual.charge.width_factor < front_width and visual.head.frame != front_frame
		and visual.base.transform == fixed_transform,
		"الکترودهای سر هنگام چرخش باید پرسپکتیو بگیرند ولی پایه نچرخد")
	visual.mount.rotation = TAU - 0.001
	visual._refresh_head()
	test.check(visual.head.frame == 47 and visual.head_next.frame == 0
		and visual.head_next.modulate.a > 0.0, "نمای چرخش باید از جهت آخر به اول نرم عبور کند")
	var color_before: Color = visual.core.modulate
	visual.advance(0.23)
	test.check(visual.charge.phase > 0.0 and visual.core.modulate != color_before,
		"شارژ و درخشش هسته باید حتی پیش از شلیک متحرک باشند")
	visual.fired()
	test.check(visual.firing_left > 0.0 and visual.charge.flash > 0.0, "شلیک تسلا باید هسته را شارژ کند")
	visual.advance(0.25)
	test.check(is_zero_approx(visual.firing_left) and visual.base.transform == fixed_transform,
		"انیمیشن شلیک باید تمام شود و پایه ثابت بماند")
	tower.queue_free()
	battle.queue_free()

func _test_nearest_target_and_firing(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var near := _enemy(test, tower.position + Vector2(95, 0))
	var far := _enemy(test, tower.position + Vector2(150, 0))
	var outside := _enemy(test, tower.position + Vector2(250, 0))
	battle.enemies = [far, outside, near]
	tower.cooldown_left = 100.0
	tower._process(0.02)
	test.check(tower.target == near, "تسلا باید نزدیک‌ترین دشمن زندهٔ داخل برد را پیدا کند")
	far.position = tower.position + Vector2(55, 0)
	tower._process(0.02)
	test.check(tower.target == far, "با نزدیک‌تر شدن دشمن دیگر، هدف تسلا باید عوض شود")
	far.alive = false
	tower._process(0.02)
	test.check(tower.target == near, "دشمن مرده نباید هدف تسلا باشد")
	near.position = tower.position + Vector2(181, 0)
	tower._process(0.02)
	test.check(tower.target == null and tower._shot_count == 0,
		"با خروج دشمن از برد باید هدف تسلا قطع شود و شلیکی رخ ندهد")
	battle.enemies = []
	tower.cooldown_left = 0.0
	tower._process(0.1)
	test.check(tower.target == null and tower._shot_count == 0, "بدون هدف تسلا نباید شلیک کند")
	battle.enemies = [near]
	near.position = tower.position + Vector2(-100, 0)
	tower._tesla_visual.mount.rotation = 0.0
	tower._process(0.01)
	test.check(tower._shot_count == 0, "تسلا پیش از هم‌راستاشدن سر نباید شلیک کند")
	for frame in 240:
		if tower._shot_count > 0:
			break
		tower._process(1.0 / 60.0)
	var arcs := _arcs(battle)
	test.check(tower._shot_count == 1 and near.calls == 1 and is_equal_approx(near.hp, 980.0)
		and arcs.size() == 1 and _projectiles(battle).is_empty() and tower._flash == null,
		"تسلا باید مستقیماً به HP آسیب بزند، بدون گلوله، موشک یا شعلهٔ دهانه")
	if arcs.size() == 1:
		var arc: TeslaArc = arcs[0]
		var axis: Vector2 = tower._tesla_visual.forward_global()
		var to_enemy := (Projectile.target_point_global(near) - tower._tesla_visual.pivot_global()).normalized()
		test.check(arc.points.size() == 2 and arc.to_global(arc.points[0]).distance_to(tower._tesla_visual.emitter_global()) < 0.01
			and arc.to_global(arc.points[1]).distance_to(Projectile.target_point_global(near)) < 0.01
			and axis.dot(to_enemy) > 0.999, "برق باید از لبهٔ هسته در جهت واقعی هدف شلیک شود")
		arc._process(arc.lifetime + 0.01)
		test.check(arc.is_queued_for_deletion(), "جلوهٔ برق باید پس از مدت کوتاه پاک شود")
	battle.enemies = []
	for node in [tower, near, far, outside, battle]:
		node.queue_free()

func _test_chain_at_every_level(test) -> void:
	for level in range(1, 6):
		var battle := TestBattle.new()
		test.add_child(battle)
		var tower := _tower(test, battle, level)
		var enemies: Array = []
		for index in range(6):
			enemies.append(_enemy(test, tower.position + Vector2(20 + 25 * index, 0)))
		# Deliberately unordered and with a duplicate: nearest-hop selection must
		# visit five distinct enemies exactly once, not follow array order.
		battle.enemies = [enemies[5], enemies[3], enemies[1], enemies[4], enemies[2], enemies[0], enemies[1]]
		tower.target = enemies[0]
		var amount: float = tower.damage()
		if level == 1:
			# Even a mistaken tuning value must not exceed five unique victims.
			tower.weapon = tower.weapon.duplicate(true)
			tower.weapon["chain"] = 99
		tower._fire()
		var arcs := _arcs(battle)
		test.check(arcs.size() == 1 and _projectiles(battle).is_empty(),
			"سطح %d یک صاعقه و هیچ گلوله‌ای باید بسازد" % level)
		for index in range(6):
			var enemy: CountingEnemy = enemies[index]
			var expected := amount * pow(0.8, index) if index < 5 else 0.0
			test.check(enemy.calls == (1 if index < 5 else 0)
				and is_equal_approx(enemy.hp, 1000.0 - expected),
				"زنجیرهٔ سطح %d دشمن %d باید دقیقاً یک ضربه یا هیچ ضربه بگیرد" % [level, index + 1])
		if arcs.size() == 1:
			var arc: TeslaArc = arcs[0]
			arc._process(0.02)
			test.check(arc.segment_reveal(0) > 0.0 and is_zero_approx(arc.segment_reveal(1)),
				"انتقال برق باید به‌ترتیب و نه هم‌زمان نمایش داده شود")
			arc._process(0.10)
			test.check(arc.segment_reveal(1) > 0.0 and not arc.is_queued_for_deletion(),
				"انتقال بعدی برق باید پس از هدف اول متحرک شود")
			test.check(arc.level == level and arc.points.size() == 6,
				"صاعقهٔ سطح %d باید شامل هسته و دقیقاً پنج هدف باشد" % level)
			if arc.points.size() == 6:
				for index in range(5):
					test.check(arc.to_global(arc.points[index + 1]).distance_to(Projectile.target_point_global(enemies[index])) < 0.01,
						"ترتیب ظاهری و آسیب زنجیره باید یکی باشد: سطح %d، انتقال %d" % [level, index + 1])
		battle.enemies = []
		for enemy in enemies:
			enemy.queue_free()
		tower.queue_free()
		battle.queue_free()

func _test_large_wave_budget(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var crowd: Array = []
	for index in 100:
		var x := 60.0 + float(index % 10) * 2.0
		var y := float(floori(float(index) / 10.0)) * 2.0
		crowd.append(_enemy(test, tower.position + Vector2(x, y)))
	battle.enemies = crowd.duplicate()
	tower.target = crowd[0]
	tower._fire()
	var damaged := 0
	var duplicates := false
	for enemy in crowd:
		damaged += 1 if enemy.calls > 0 else 0
		duplicates = duplicates or enemy.calls > 1
	var labels := 0
	for child in battle.get_children():
		if child is FloatingText:
			labels += 1
	test.check(damaged == 5 and not duplicates and labels <= 5 and _arcs(battle).size() == 1
		and _projectiles(battle).is_empty(),
		"در جمعیت ۱۰۰تایی تسلا باید فقط پنج ضربه و یک افکت زنجیره بسازد")
	battle.enemies = []
	for enemy in crowd:
		enemy.queue_free()
	tower.queue_free()
	battle.queue_free()

func _test_range_boundaries_and_armor(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var main := _enemy(test, tower.position + Vector2(20, 0), 100.0, 4.0, 0.25)
	var edge := _enemy(test, tower.position + Vector2(90, 0), 100.0, 2.0, 0.5)
	var outside := _enemy(test, tower.position + Vector2(20, 70.01), 100.0)
	var dead := _enemy(test, tower.position + Vector2(30, 0), 100.0)
	dead.alive = false
	battle.enemies = [outside, dead, edge, main, main]
	tower.target = main
	tower._fire()
	test.check(is_equal_approx(main.hp, 88.0) and main.calls == 1
		and is_equal_approx(edge.hp, 93.0) and edge.calls == 1,
		"برق باید زره و سپر واقعی را در هر ضربه اعمال کند و مرز شعاع انتقال را شامل شود")
	test.check(outside.calls == 0 and dead.calls == 0,
		"دشمن مرده یا اندکی بیرون شعاع انتقال نباید آسیب بگیرد")
	# Enemy within hop reach but outside the tower's firing circle is also safe.
	main.position = tower.position + Vector2(180.0, 0.0)
	outside.position = tower.position + Vector2(180.01, 0.0)
	main.hp = 100.0
	main.calls = 0
	outside.calls = 0
	battle.enemies = [outside, main]
	tower.target = main
	test.check(tower._pick_target() == main, "لبهٔ برد برج باید شامل هدف باشد، بیرون آن نه")
	tower._fire()
	test.check(main.calls == 1 and outside.calls == 0, "زنجیره نباید از دایرهٔ برد برج بیرون برود")
	battle.enemies = [outside]
	tower.target = outside
	var shots_before: int = tower._shot_count
	tower._fire()
	test.check(outside.calls == 0 and tower._shot_count == shots_before,
		"تسلا نباید روی هدف اصلی خارج برد شلیک کند")
	# Zero falloff must not call Enemy.take_damage(0), whose legacy minimum is 1.
	main.position = tower.position + Vector2(20, 0)
	edge.position = tower.position + Vector2(40, 0)
	main.calls = 0
	edge.calls = 0
	battle.enemies = [main, edge]
	tower.weapon = tower.weapon.duplicate(true)
	tower.weapon["chain_falloff"] = 0.0
	tower.target = main
	tower._fire()
	test.check(main.calls == 1 and edge.calls == 0, "ضریب صفر نباید حتی یک واحد آسیب ناخواسته در انتقال بعدی بدهد")
	battle.enemies = []
	for node in [tower, main, edge, outside, dead, battle]:
		node.queue_free()

func _test_death_list_mutation(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var a := _enemy(test, tower.position + Vector2(20, 0), 1.0)
	var b := _enemy(test, tower.position + Vector2(40, 0), 1.0)
	var c := _enemy(test, tower.position + Vector2(60, 0), 1.0)
	var outside := _enemy(test, tower.position + Vector2(60, 90), 1.0)
	for enemy in [a, b, c]:
		enemy.died.connect(battle.remove_dead)
	battle.enemies = [a, b, c, outside]
	tower.target = a
	tower._fire()
	test.check(not a.alive and not b.alive and not c.alive and outside.alive
		and a.calls == 1 and b.calls == 1 and c.calls == 1 and outside.calls == 0
		and battle.enemies.size() == 1 and battle.enemies[0] == outside,
		"مرگ هر هدف و تغییر هم‌زمان آرایه نباید زنجیره را قطع یا هدف بیرون شعاع را زخمی کند")
	# If a death signal removes another still-living enemy, the cached candidate
	# must be revalidated and skipped before searching for the next live target.
	var first := _enemy(test, tower.position + Vector2(20, 0), 1.0)
	var removed := _enemy(test, tower.position + Vector2(40, 0), 100.0)
	var remaining := _enemy(test, tower.position + Vector2(60, 0), 100.0)
	battle.enemies = [first, removed, remaining]
	first.died.connect(func(victim):
		battle.enemies.erase(victim)
		battle.enemies.erase(removed)
	)
	tower.target = first
	tower._fire()
	test.check(not first.alive and removed.alive and removed.calls == 0 and remaining.calls == 1,
		"دشمن زنده‌ای که با سیگنال از میدان حذف شده نباید از snapshot آسیب بگیرد")
	battle.enemies = []
	for node in [tower, a, b, c, outside, first, removed, remaining, battle]:
		node.queue_free()

func _test_transformed_world(test) -> void:
	var world := Node2D.new()
	world.position = Vector2(83, -46)
	test.add_child(world)
	var battle := TestBattle.new()
	world.add_child(battle)
	var group := Node2D.new()
	group.position = Vector2(38, 12)
	world.add_child(group)
	var tower := Tower.new()
	group.add_child(tower)
	tower.set_process(false)
	tower.position = Vector2(400, 300)
	tower.setup(Weapons.by_id("tesla"), battle)
	var enemy := _enemy(world, Vector2(0, 0))
	enemy.global_position = tower.global_position + Vector2(100, 0)
	battle.enemies = [enemy]
	test.check(tower._pick_target() == enemy, "برد تسلا باید در مختصات مشترک دنیا سنجیده شود")
	tower.target = enemy
	tower._fire()
	var arcs := _arcs(battle)
	test.check(arcs.size() == 1 and enemy.calls == 1, "در دنیای جابه‌جاشده تسلا باید درست اصابت کند")
	if arcs.size() == 1:
		var arc: TeslaArc = arcs[0]
		test.check(arc.to_global(arc.points[0]).distance_to(tower._tesla_visual.emitter_global()) < 0.01
			and arc.to_global(arc.points[1]).distance_to(Projectile.target_point_global(enemy)) < 0.01,
			"مختصات افکت باید با مکان واقعی سر و دشمن در والدهای متفاوت یکی باشد")
	battle.enemies = []
	world.queue_free()

func _test_real_battle(test) -> void:
	var saved_map: int = Game.pending_map
	var saved_last_map: int = Game.last_map
	var saved_unlocked: Array = Game.unlocked_weapons.duplicate()
	Game.pending_map = 0
	Game.unlocked_weapons = Weapons.ids().duplicate()
	var battle = load("res://scenes/Battle.tscn").instantiate()
	test.add_child(battle)
	battle.set_process(false)
	battle.coins = 9999
	for y in range(140, 560, 40):
		for x in range(160, 900, 40):
			var point := Vector2(x, y)
			if battle.map.build_issue(point, battle.occupied).is_empty():
				battle.selected_weapon = "tesla"
				battle._try_build(point)
				break
		if not battle.towers.is_empty():
			break
	test.check(battle.towers.size() == 1, "تسلا باید از مسیر ساخت واقعی میدان ساخته شود")
	if not battle.towers.is_empty():
		var tower: Tower = battle.towers[0]
		tower.set_process(false)
		for index in 4:
			battle._spawn_enemy("soldier")
		var primary: Enemy = battle.enemies[0]
		var second: Enemy = battle.enemies[1]
		var third: Enemy = battle.enemies[2]
		var outside: Enemy = battle.enemies[3]
		for enemy in battle.enemies:
			enemy.set_process(false)
			enemy.hp = 1.0
			enemy.armor = 0.0
			enemy.shield = 0.0
		primary.position = tower.position + Vector2(60, 0)
		second.position = tower.position + Vector2(80, 0)
		third.position = tower.position + Vector2(100, 0)
		outside.position = tower.position + Vector2(60, 100)
		for frame in 240:
			if tower._shot_count > 0:
				break
			tower._process(1.0 / 60.0)
		test.check(tower._shot_count == 1 and not primary.alive and not second.alive and not third.alive
			and outside.alive and battle.kills == 3 and battle.enemies.size() == 1
			and battle.enemies[0] == outside and _arcs(battle.fx_layer()).size() == 1
			and _projectiles(battle.fx_layer()).is_empty(),
			"در نبرد واقعی یک برق باید سه دشمن نزدیک را بکشد و دشمن خارج شعاع را سالم بگذارد")
		var death_blasts := 0
		for effect in battle.fx_layer().get_children():
			if effect is AnimatedSprite2D:
				death_blasts += 1
		test.check(death_blasts == 0 and not primary.has_meta(TeslaArc.DEATH_FX_CONTEXT),
			"کشتن با تسلا نباید انفجار نارنجی یا نشانهٔ موقت باقی بگذارد")
		outside.take_damage(2.0)
		for effect in battle.fx_layer().get_children():
			if effect is AnimatedSprite2D:
				death_blasts += 1
		test.check(death_blasts == 1, "مرگ عادیِ غیرتسلا باید افکت پیشین را حفظ کند")
		battle.select_tower(tower)
		battle.hud.refresh()
		test.check(battle.hud._tower_stats.text.contains(Ui.fa(70))
			and battle.hud._tower_stats.text.contains(Ui.fa(5)), "پنل تسلا باید شعاع و سقف زنجیره را نشان دهد")
		var coins_before: int = battle.coins
		battle.upgrade_selected()
		test.check(tower.level == 2 and is_equal_approx(tower.chain_radius(), 78.0)
			and battle.coins == coins_before - 200 and battle.hud._tower_stats.text.contains(Ui.fa(78)),
			"ارتقای تسلا در نبرد باید ۲۰۰ سکه کم کند و آمار پنل را عوض کند")
	battle.queue_free()
	Game.pending_map = saved_map
	Game.last_map = saved_last_map
	Game.unlocked_weapons = saved_unlocked
