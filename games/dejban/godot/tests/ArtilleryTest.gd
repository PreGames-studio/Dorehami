## Behavioral tests for the actual five-level artillery tower and shell.
## The live Battle scene test also verifies multiple lethal hits while the battle
## synchronously removes dead enemies from its array.
extends RefCounted

class TestBattle extends Node2D:
	var enemies: Array = []
	func fx_layer() -> Node:
		return self

func _tower(test, battle, weapon := {}) -> Tower:
	var tower := Tower.new()
	test.add_child(tower)
	tower.set_process(false)
	tower.position = Vector2(400, 300)
	tower.setup(Weapons.by_id("cannon") if weapon.is_empty() else weapon, battle)
	return tower

func _enemy(parent: Node, pos: Vector2) -> Enemy:
	var enemy := Enemy.new()
	parent.add_child(enemy)
	enemy.set_process(false)
	enemy.position = pos
	enemy.alive = true
	enemy.hp = 1000.0
	enemy.max_hp = 1000.0
	enemy.armor = 0.0
	enemy.shield = 0.0
	return enemy

func _shells(parent: Node) -> Array:
	var result: Array = []
	for child in parent.get_children():
		if child is ArtilleryShell and not child.is_queued_for_deletion():
			result.append(child)
	return result

func run(test) -> void:
	var saved_levels: Dictionary = Game.weapon_levels.duplicate(true)
	Game.weapon_levels["cannon"] = 0
	_test_art_and_aim(test)
	_test_targeting_and_flight(test)
	_test_ground_radius_and_falloff(test)
	_test_first_contact_and_sold_tower(test)
	_test_moving_enemy_sweep(test)
	_test_world_and_muzzle_edge_cases(test)
	_test_effects(test)
	_test_crowd_budget(test)
	_test_real_battle(test)
	Game.weapon_levels = saved_levels

func _test_art_and_aim(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var visual := tower._machinegun_visual as ArtilleryVisual
	test.check(visual != null and tower.is_artillery() and tower.level_cap() == 5,
		"توپخانه باید ظاهر لایه‌ای و پنج سطح داشته باشد")
	if visual == null:
		tower.queue_free()
		battle.queue_free()
		return
	var base_transform: Transform2D = visual.base.transform
	test.check(is_equal_approx(visual.mount.rotation, -TAU / 8.0),
		"نمای شروع میدان باید با نمای مایل تصویر و پیش‌نمای کاشت یکسان باشد")
	var previous_damage := 0.0
	var previous_range := 0.0
	var previous_radius := 0.0
	var previous_reload := INF
	var previous_turn := 0.0
	for level in range(1, 6):
		var image := Assets.tex("towers_artillery/artillery_%d" % level)
		test.check(image != null and image.get_size() == Vector2(256, 256), "تصویر مستقل توپخانه سطح %d گم است" % level)
		if image != null:
			var data := image.get_image()
			test.check(data.get_pixel(0, 0).a == 0.0 and data.get_pixel(255, 255).a == 0.0,
				"تصویر سطح %d باید واقعاً شفاف باشد" % level)
		test.check(visual.base.texture == Assets.tex("towers_artillery/base_%d" % level)
			and visual.base.transform == base_transform and tower.position == Vector2(400, 300),
			"در ارتقای %d مختصات و محور پایه نباید تغییر کند" % level)
		test.check(visual.head.texture == Assets.tex("towers_artillery/head_%d_directions" % level)
			and visual.barrels.texture == Assets.tex("towers_artillery/barrel_%d_directions" % level)
			and visual.head.hframes == 8 and visual.head.vframes == 6
			and visual.barrels.hframes == 8 and visual.barrels.vframes == 6,
			"سطح %d باید از اطلس سر و لولهٔ ۴۸جهتی استفاده کند" % level)
		test.check(tower.damage() > previous_damage and tower.attack_range() > previous_range
			and tower.splash_radius() > previous_radius and tower.interval() < previous_reload
			and tower.turn_speed() > previous_turn, "آمار و شعاع انفجار باید در ارتقای %d بهتر شود" % level)
		previous_damage = tower.damage()
		previous_range = tower.attack_range()
		previous_radius = tower.splash_radius()
		previous_reload = tower.interval()
		previous_turn = tower.turn_speed()
		if level < 5:
			tower.upgrade()
	var invested: int = tower.invested
	tower.upgrade()
	test.check(tower.level == 5 and tower.max_level() and tower.invested == invested,
		"توپخانه نباید فراتر از سطح ۵ ارتقا یابد یا هزینهٔ اضافه بگیرد")
	test.check(Weapons.icon("cannon") == Assets.tex("towers_artillery/artillery_1"),
		"آیکون خرید باید طرح سطح اول توپخانه باشد")
	var ghost := BuildGhost.new()
	test.add_child(ghost)
	ghost.set_weapon(Weapons.by_id("cannon"))
	test.check(ghost._base.texture == Weapons.icon("cannon") and ghost._base.offset == visual.base.offset
		and ghost._base.scale == visual.base.scale and ghost._base.position == visual.position,
		"پیش‌نمای ساخت باید همان اندازه و جای پایهٔ توپخانه باشد")
	ghost.queue_free()

	# Whole circle and the 179° -> -179° shortest-route boundary; no rotating base.
	for degrees in range(0, 360, 15):
		var angle := deg_to_rad(float(degrees))
		var direction := Vector2(cos(angle), sin(angle) * MachinegunVisual.DEPTH_SQUASH)
		visual.aim_at(direction, 100.0, 1.0)
		test.check(visual.forward_global().dot(direction.normalized()) > 0.9999,
			"لولهٔ توپخانه در زاویهٔ %d باید واقعاً هم‌جهت هدف باشد" % degrees)
		test.check(visual.base.transform == base_transform and visual.base.rotation == 0.0,
			"پایه در چرخش %d درجه نباید بچرخد یا جابه‌جا شود" % degrees)
	visual.mount.rotation = deg_to_rad(179.0)
	var wrap := deg_to_rad(-179.0)
	visual.aim_at(Vector2(cos(wrap), sin(wrap) * MachinegunVisual.DEPTH_SQUASH), 1.0, 0.01)
	test.check(angle_difference(deg_to_rad(179.0), visual.mount.rotation) > 0.0
		and angle_difference(deg_to_rad(179.0), visual.mount.rotation) <= 0.0101,
		"توپخانه باید از کوتاه‌ترین مسیر و با سرعت محدود بچرخد")
	var angle_before := visual.mount.rotation
	visual.fired()
	test.check(visual.recoil > 0.0 and visual.base.transform == base_transform,
		"پس‌زنی باید لوله را حرکت دهد نه پایه را")
	visual.advance(0.2)
	test.check(is_zero_approx(visual.recoil) and visual.mount.rotation == angle_before and visual.barrels.frame < 48
		and visual.base.transform == base_transform, "پس‌زنی برمی‌گردد و توپ گاتلینگ نمی‌چرخد")
	tower.queue_free()
	battle.queue_free()

func _test_targeting_and_flight(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var visual := tower._machinegun_visual as ArtilleryVisual
	var near := _enemy(test, tower.position + Vector2(90, 0))
	var far := _enemy(test, tower.position + Vector2(150, 0))
	var outside := _enemy(test, tower.position + Vector2(500, 0))
	battle.enemies = [far, outside, near]
	tower.cooldown_left = 100.0
	tower._process(0.02)
	test.check(tower.target == near, "توپخانه باید نزدیک‌ترین دشمن در برد را انتخاب کند")
	far.position = tower.position + Vector2(60, 0)
	tower._process(0.02)
	test.check(tower.target == far, "با نزدیک‌تر شدن دشمن دیگر، توپخانه باید او را انتخاب کند")
	far.alive = false
	tower._process(0.02)
	test.check(tower.target == near, "دشمن مرده هدف معتبر نیست")
	battle.enemies = []
	tower.cooldown_left = 0.0
	var shots_before: int = tower._shot_count
	tower._process(0.3)
	test.check(tower.target == null and tower._shot_count == shots_before, "توپخانهٔ بدون هدف نباید شلیک کند")

	near.position = tower.position + Vector2(-140, 0)
	battle.enemies = [near]
	visual.mount.rotation = 0.0
	tower._process(0.01)
	test.check(tower._shot_count == shots_before, "توپخانه پیش از چرخیدن و آماده‌شدن نباید شلیک کند")
	for frame in 180:
		if not _shells(battle).is_empty():
			break
		tower._process(1.0 / 60.0)
	var shots := _shells(battle)
	test.check(shots.size() == 1 and tower._flash.visible, "توپخانه باید یک گلوله و شعلهٔ دهانه تولید کند")
	if shots.size() == 1:
		var shell: ArtilleryShell = shots[0]
		var destination := near.global_position
		var start := shell.global_position
		var forward := visual.forward_global()
		test.check(shell.ground_destination.distance_to(destination) < 0.01
			and shell.global_position.distance_to(visual.muzzle_global()) < 0.01
			and tower.to_global(tower._flash.position).distance_to(start) < 0.01
			and shell._dir.dot(forward) > 0.99999,
			"گلوله و شعله باید از دهانه و رو به محل واقعی هدف آغاز شوند")
		near.position += Vector2(-500, 0)
		var hp: float = near.hp
		shell._process(0.05)
		var expected := (destination - start).normalized()
		test.check(not shell.is_queued_for_deletion() and shell.ground_destination == destination
			and (shell.global_position - start).normalized().dot(expected) > 0.999,
			"حرکت هدف نباید گلولهٔ شلیک‌شده را هدف‌جو کند")
		shell._process(1.0)
		test.check(shell.is_queued_for_deletion() and shell.impact_global.distance_to(destination) < 0.01 and near.hp == hp,
			"اگر هدف دور شود، گلوله باید در نقطهٔ زمین تعیین‌شده منفجر شود")
		var blasts := 0
		for child in battle.get_children():
			if child is ArtilleryBlast:
				blasts += 1
		test.check(blasts == 1, "برخورد با زمین باید دقیقاً یک افکت انفجار بسازد")
		test.check(visual.base.rotation == 0.0 and visual.base.position == Vector2.ZERO,
			"پایه در شلیک و پس‌زنی باید ثابت بماند")
	battle.enemies = []
	tower.queue_free()
	battle.queue_free()
	near.queue_free()
	far.queue_free()
	outside.queue_free()

func _test_ground_radius_and_falloff(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var ground := Vector2(240, 200)
	var target := _enemy(test, ground)
	var near := _enemy(test, ground + Vector2(0, 12.4))
	var middle := _enemy(test, ground + Vector2(0, 21.0))
	var edge := _enemy(test, ground + Vector2(0, 42.0))
	var outside := _enemy(test, ground + Vector2(0, 42.1))
	battle.enemies = [target, near, middle, edge, outside]
	var shell := ArtilleryShell.new()
	shell.setup(tower, target, 100.0, Vector2.RIGHT)
	battle.add_child(shell)
	shell.global_position = Vector2(60, 200)
	# A shot keeps the level/radius/damage from its firing moment, even if the
	# tower upgrades before the impact. Remove target to guarantee ground impact.
	tower.upgrade()
	battle.enemies.erase(target)
	test.check(is_equal_approx(shell.splash, 42.0) and shell.firing_level == 1 and shell.damage_value == 100.0,
		"آسیب، شعاع و سطح گلولهٔ در پرواز با ارتقای برج نباید تغییر کنند")
	shell._process(1.0)
	var near_damage: float = 1000.0 - near.hp
	var middle_damage: float = 1000.0 - middle.hp
	var edge_damage: float = 1000.0 - edge.hp
	test.check(shell.impact_global.distance_to(ground) < 0.01 and shell.is_queued_for_deletion(),
		"گلوله باید در محل قفل‌شدهٔ زمین منفجر شود")
	test.check(absf(near_damage - 100.0 * (1.0 - 0.5 * 12.4 / 42.0)) < 0.05
		and absf(middle_damage - 75.0) < 0.05 and absf(edge_damage - 50.0) < 0.05,
		"آسیب باید از مرکز تا لبه به‌صورت خطی کاهش یابد")
	test.check(near_damage > middle_damage and middle_damage > edge_damage and outside.hp == 1000.0 and target.hp == 1000.0,
		"چند دشمن داخل انفجار آسیب می‌بینند و خارج از شعاع یا میدان سالم می‌ماند")
	shell._process(1.0)
	test.check(is_equal_approx(1000.0 - middle.hp, middle_damage), "هر گلوله فقط یک بار باید منفجر و آسیب وارد کند")

	# A contact at the exact center receives the full snapshotted damage.
	var center := _enemy(test, ground)
	battle.enemies = [target, center]
	var center_shot := ArtilleryShell.new()
	center_shot.setup(tower, target, 100.0, Vector2.RIGHT)
	battle.add_child(center_shot)
	center_shot.global_position = ground
	battle.enemies.erase(target)
	center_shot._process(0.01)
	test.check(is_equal_approx(center.hp, 900.0), "دشمن دقیقاً در مرکز انفجار باید آسیب کامل بگیرد")
	center.queue_free()

	# Edge multiplier one is the supported constant-damage setting.
	var constant: Dictionary = Weapons.by_id("cannon").duplicate(true)
	constant["edge_damage_fraction"] = 1.0
	var constant_tower := _tower(test, battle, constant)
	near.hp = 1000.0
	edge.hp = 1000.0
	outside.hp = 1000.0
	battle.enemies = [target, near, edge, outside]
	var fixed := ArtilleryShell.new()
	fixed.setup(constant_tower, target, 100.0, Vector2.RIGHT)
	battle.add_child(fixed)
	fixed.global_position = Vector2(60, 200)
	battle.enemies.erase(target)
	fixed._process(1.0)
	test.check(is_equal_approx(near.hp, 900.0) and is_equal_approx(edge.hp, 900.0) and outside.hp == 1000.0,
		"ضریب لبهٔ یک باید آسیب ثابت داخل شعاع و صفر خارج آن بدهد")
	# Zero edge damage must not trigger Enemy.take_damage's minimum-one rule.
	var zero_edge: Dictionary = Weapons.by_id("cannon").duplicate(true)
	zero_edge["edge_damage_fraction"] = 0.0
	var zero_tower := _tower(test, battle, zero_edge)
	near.hp = 1000.0
	edge.hp = 1000.0
	battle.enemies = [target, near, edge]
	var zero_shot := ArtilleryShell.new()
	zero_shot.setup(zero_tower, target, 100.0, Vector2.RIGHT)
	battle.add_child(zero_shot)
	zero_shot.global_position = Vector2(60, 200)
	battle.enemies.erase(target)
	zero_shot._process(1.0)
	test.check(near.hp < 1000.0 and edge.hp == 1000.0, "ضریب صفر نباید حتی حداقل آسیب دشمن را در لبه اعمال کند")
	battle.enemies = []
	for node in [tower, constant_tower, zero_tower, target, near, middle, edge, outside, battle]:
		node.queue_free()

func _test_first_contact_and_sold_tower(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var target := _enemy(test, Vector2(300, 200))
	var first := _enemy(test, Vector2(150, 200))
	var second := _enemy(test, Vector2(205, 200))
	battle.enemies = [target, second, first] # deliberately not in path order
	var shell := ArtilleryShell.new()
	shell.setup(tower, target, 100.0, Vector2.RIGHT)
	battle.add_child(shell)
	shell.global_position = Vector2(50, 200)
	tower.free() # a real sale frees the tower while the shell is still in flight
	shell._process(1.0)
	test.check(shell.is_queued_for_deletion() and shell.impact_global.x > 136.0 and shell.impact_global.x < 140.0,
		"گلوله باید با نخستین دشمن مسیر برخورد جاروبی کند، نه از او عبور کند")
	test.check(first.hp < 1000.0 and second.hp == 1000.0 and target.hp == 1000.0,
		"گلولهٔ برج فروخته‌شده نیز باید منفجر شود، ولی دشمنان خارج از شعاع سالم بمانند")
	battle.enemies = []
	for node in [target, first, second, battle]:
		node.queue_free()

func _test_moving_enemy_sweep(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var target := _enemy(test, Vector2(300, 100))
	var crossing := _enemy(test, Vector2(150, 60))
	battle.enemies = [target, crossing]
	var shell := ArtilleryShell.new()
	shell.setup(tower, target, 100.0, Vector2.RIGHT)
	battle.add_child(shell)
	shell.global_position = Vector2(100, 100)
	crossing.position = Vector2(150, 140)
	shell._process(0.25)
	test.check(shell.is_queued_for_deletion() and crossing.hp < 1000.0 and target.hp == 1000.0,
		"عبور دشمن متحرک از گلوله بین دو فریم باید برخورد و آسیب واقعی داشته باشد")
	battle.enemies = []
	for node in [tower, target, crossing, battle]:
		node.queue_free()

func _test_world_and_muzzle_edge_cases(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := Tower.new()
	battle.add_child(tower)
	tower.set_process(false)
	tower.setup(Weapons.by_id("cannon"), battle)
	var target := _enemy(battle, Vector2(240, 200))
	var neighbor := _enemy(battle, Vector2(240, 218))
	battle.enemies = [target, neighbor]
	var shell := ArtilleryShell.new()
	shell.setup(tower, target, 100.0, Vector2.RIGHT)
	battle.add_child(shell)
	shell.position = Vector2(60, 200)
	battle.enemies.erase(target)
	# Existing keep impacts shake the World parent. The projectile's fixed
	# destination and other actors must move with that parent, not slip in screen space.
	battle.position = Vector2(37, -19)
	shell._process(1.0)
	test.check(shell.impact_global.distance_to(battle.to_global(Vector2(240, 200))) < 0.01
		and neighbor.hp < 1000.0, "لرزش میدان نباید محل اصابت و شعاع آسیب را جابه‌جا کند")

	# If the aimed enemy is between the pivot and the muzzle, never shoot
	# backwards; detonate at the muzzle, where it is still within blast range.
	var close_enemy := _enemy(battle, Vector2(80, 200))
	battle.enemies = [close_enemy]
	var close_shell := ArtilleryShell.new()
	close_shell.setup(tower, close_enemy, 100.0, Vector2.RIGHT)
	battle.add_child(close_shell)
	close_shell.position = Vector2(100, 200)
	close_shell._process(0.1)
	test.check(close_shell.impact_global.distance_to(battle.to_global(Vector2(100, 200))) < 0.01
		and close_enemy.hp < 1000.0, "هدف زیر دهانه باید بدون برگشت گلوله در همان دهانه آسیب ببیند")
	battle.enemies = []
	battle.queue_free()

func _test_effects(test) -> void:
	var parent := Node2D.new()
	test.add_child(parent)
	var small := ArtilleryBlast.new()
	var large := ArtilleryBlast.new()
	parent.add_child(small)
	parent.add_child(large)
	small.setup(1, 42.0)
	large.setup(5, 58.0)
	test.check(small.flash != null and large.flash != null and ArtilleryBlast.PARTICLES == 6,
		"انفجار باید شیت انیمیشن و تعداد محدود ذره داشته باشد")
	if small.flash != null and large.flash != null:
		test.check(large.flash.scale.x > small.flash.scale.x
			and large.flash.sprite_frames.get_frame_count("play") > small.flash.sprite_frames.get_frame_count("play"),
			"انفجار سطح پنجم باید پرقدرت‌تر از سطح اول باشد")
		large._process(0.06)
		test.check(large.flash.position.length() > 0.0 and large.flash.position.length() < 2.0,
			"لرزش باید جزئی و محدود به تصویر انفجار باشد")
	large._process(ArtilleryBlast.DURATION)
	test.check(large.is_queued_for_deletion(), "افکت موقت باید پس از پایان خودکار آزاد شود")
	parent.queue_free()

func _test_crowd_budget(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle)
	var ground := Vector2(240, 200)
	var target := _enemy(test, ground)
	battle.enemies = [target]
	var crowd: Array = []
	for i in 25:
		var enemy := _enemy(test, ground + Vector2(float(i % 5) - 2.0, 18.0 + floorf(float(i) / 5.0)))
		crowd.append(enemy)
		battle.enemies.append(enemy)
	var shell := ArtilleryShell.new()
	shell.setup(tower, target, 100.0, Vector2.RIGHT)
	battle.add_child(shell)
	shell.global_position = Vector2(60, 200)
	battle.enemies.erase(target)
	shell._process(1.0)
	var all_hit := true
	for enemy in crowd:
		all_hit = all_hit and enemy.hp < 1000.0
	var text_count := 0
	var blast_count := 0
	for child in battle.get_children():
		if child is FloatingText:
			text_count += 1
		if child is ArtilleryBlast:
			blast_count += 1
	test.check(all_hit and text_count <= 6 and blast_count == 1,
		"انفجار باید به همهٔ جمعیت آسیب بزند، ولی تعداد متن و افکت محدود بماند")
	battle.enemies = []
	for enemy in crowd:
		enemy.queue_free()
	for node in [tower, target, battle]:
		node.queue_free()

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
				battle.selected_weapon = "cannon"
				battle._try_build(point)
				break
		if not battle.towers.is_empty():
			break
	test.check(battle.towers.size() == 1, "توپخانه باید از مسیر ساخت واقعی میدان ساخته شود")
	if battle.towers.is_empty():
		battle.queue_free()
		Game.pending_map = saved_map
		Game.last_map = saved_last_map
		Game.unlocked_weapons = saved_unlocked
		return
	var tower: Tower = battle.towers[0]
	tower.set_process(false)
	for i in 4:
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
	primary.position = tower.position + Vector2(120, 0)
	second.position = tower.position + Vector2(128, 10)
	third.position = tower.position + Vector2(128, -10)
	outside.position = tower.position + Vector2(120, 100)
	for frame in 180:
		if not _shells(battle.fx_layer()).is_empty():
			break
		tower._process(1.0 / 60.0)
	var shots := _shells(battle.fx_layer())
	test.check(shots.size() == 1 and tower.target == primary,
		"برج واقعی میدان باید نزدیک‌ترین دشمن را انتخاب و گلوله شلیک کند")
	if shots.size() == 1:
		var shell: ArtilleryShell = shots[0]
		shell._process(1.0)
		test.check(not primary.alive and not second.alive and not third.alive and outside.alive
			and battle.kills == 3 and battle.enemies.size() == 1 and battle.enemies[0] == outside,
			"در نبرد واقعی یک انفجار باید هر سه دشمن داخل شعاع را بکشد، بدون آسیب به خارج شعاع")
		var direct_death_blasts := 0
		var shared_blasts := 0
		for effect in battle.fx_layer().get_children():
			if effect is AnimatedSprite2D:
				direct_death_blasts += 1
			if effect is ArtilleryBlast:
				shared_blasts += 1
		test.check(direct_death_blasts == 0 and shared_blasts == 1
			and not primary.has_meta(ArtilleryShell.DEATH_FX_CONTEXT),
			"مرگ‌های یک انفجار نباید چند افکت تکراری بسازند یا نشانهٔ موقت باقی بگذارند")
		outside.take_damage(2.0)
		for effect in battle.fx_layer().get_children():
			if effect is AnimatedSprite2D:
				direct_death_blasts += 1
		test.check(direct_death_blasts == 1, "جلوهٔ مرگ عادی غیرتوپخانه باید مثل قبل باقی بماند")
	battle.select_tower(tower)
	battle.hud.refresh()
	test.check(battle.hud._tower_stats.text.contains(Ui.fa(42)),
		"پنل واقعی نبرد باید شعاع فعلی انفجار را نمایش دهد")
	var coins_before: int = battle.coins
	battle.upgrade_selected()
	test.check(tower.level == 2 and is_equal_approx(tower.splash_radius(), 46.0)
		and battle.coins == coins_before - 130 and battle.hud._tower_stats.text.contains(Ui.fa(46)),
		"ارتقای میدان باید شعاع و پنل را به‌روز و هزینهٔ قبلی را حفظ کند")
	battle.queue_free()
	Game.pending_map = saved_map
	Game.last_map = saved_last_map
	Game.unlocked_weapons = saved_unlocked
