## The rendered head must settle to one clear frame without changing physical
## aim, including after a real enemy dies. Covers every weapon, level and angle.
extends RefCounted

class TestBattle extends Node2D:
	var enemies: Array = []
	func fx_layer() -> Node:
		return self

func run(test) -> void:
	_test_all_views(test)
	_test_enemy_death_and_retarget(test)
	_test_laser_beam_lock(test)

func _tower(test, battle, id: String, level := 1) -> Tower:
	var tower := Tower.new()
	test.add_child(tower)
	tower.set_process(false)
	tower.position = Vector2(400.0, 300.0)
	tower.setup(Weapons.by_id(id), battle, level)
	return tower

func _visual(tower: Tower):
	if tower.is_laser():
		return tower._laser_visual
	if tower.is_tesla():
		return tower._tesla_visual
	return tower._machinegun_visual

func _aim_vector(angle: float) -> Vector2:
	return Vector2(cos(angle), sin(angle) * 0.68)

func _is_crisp(visual) -> bool:
	return (is_equal_approx(visual.head.modulate.a, 1.0) and is_zero_approx(visual.head_next.modulate.a)) \
		or (is_zero_approx(visual.head.modulate.a) and is_equal_approx(visual.head_next.modulate.a, 1.0))

func _visible_frame(visual) -> int:
	return visual.head.frame if visual.head.modulate.a > 0.5 else visual.head_next.frame

func _test_all_views(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	for id in ["machinegun", "cannon", "tesla", "laser"]:
		for level in range(1, 6):
			var tower := _tower(test, battle, id, level)
			var visual = _visual(tower)
			var base_transform: Transform2D = visual.base.transform
			var image_scale: float = MachinegunVisual.IMAGE_SCALE if visual is MachinegunVisual else LaserVisual.IMAGE_SCALE
			var expected_view_scale := image_scale * 2.0 * 1.3
			var actual_view_scale: Vector2 = visual.art.scale if visual is MachinegunVisual else visual.head.scale
			var pivot_in_frame: Vector2
			if visual is MachinegunVisual:
				pivot_in_frame = MachinegunVisual.HEAD_PIVOT * 0.5
			elif visual is TeslaVisual:
				pivot_in_frame = TeslaVisual.CORE_CENTERS[level - 1] * 0.5
			else:
				pivot_in_frame = LaserVisual.AXIS_CENTERS[level - 1] * 0.5
			test.check(actual_view_scale.is_equal_approx(Vector2.ONE * expected_view_scale)
				and visual.projection.scale.is_equal_approx(Vector2(image_scale, image_scale * 0.68) * 1.3)
				and visual.head.offset.is_equal_approx(-pivot_in_frame)
				and visual.head_next.offset.is_equal_approx(-pivot_in_frame)
				and visual.head.to_global(Vector2.ZERO).distance_to(visual.pivot_global()) < 0.01
				and visual.head_next.to_global(Vector2.ZERO).distance_to(visual.pivot_global()) < 0.01,
				"سر %s سطح %d باید ۳۰٪ بزرگ‌تر و حول محور اصلی باشد" % [id, level])
			if visual is TeslaVisual:
				test.check(visual.core.scale.is_equal_approx(Vector2.ONE * image_scale * 1.3)
					and visual.charge.scale.is_equal_approx(visual.core.scale),
					"کره و افکت تسلا باید با سر بزرگ‌شده هم‌اندازه باشند")
			for frame in range(48):
				# Alternate sides of each boundary, including the 47 → 0 seam.
				var fraction := 0.35 if frame % 2 == 0 else 0.65
				var angle := TAU * (float(frame) + fraction) / 48.0
				visual.mount.rotation = TAU * float(frame) / 48.0
				visual.aim_at(_aim_vector(angle), 100.0, 1.0)
				var forward: Vector2 = visual.forward_global()
				visual.advance(0.11)
				var expected := posmod(frame + (1 if fraction > 0.5 else 0), 48)
				var aligned := true
				if visual is MachinegunVisual:
					var barrel_frame: int = visual.barrels.frame if visual.barrels.modulate.a > 0.5 else visual.barrels_next.frame
					aligned = barrel_frame % 48 == expected \
						and is_equal_approx(visual.barrels.modulate.a, visual.head.modulate.a) \
						and is_equal_approx(visual.barrels_next.modulate.a, visual.head_next.modulate.a)
				test.check(_is_crisp(visual) and _visible_frame(visual) == expected and aligned
					and visual.forward_global().distance_to(forward) < 0.00001
					and absf(angle_difference(angle, visual.mount.rotation)) < 0.00001
					and visual.base.transform == base_transform,
					"سر %s سطح %d و جهت %d باید واضح، هم‌راستا و با پایهٔ ثابت باشد" % [id, level, frame])

			# At the midpoint, rotation still uses a smooth blend. Over 0.1 s of
			# no rotation it resolves gradually, without moving the aim axis.
			var midpoint := TAU * 7.5001 / 48.0
			visual.mount.rotation = TAU * 7.0 / 48.0
			visual.aim_at(_aim_vector(midpoint), 100.0, 1.0)
			var moving_alpha: float = visual.head_next.modulate.a
			var moving_forward: Vector2 = visual.forward_global()
			test.check(absf(moving_alpha - 0.5) < 0.002,
				"نمای متحرک %s سطح %d باید در گذر بین دو جهت نرم باشد" % [id, level])
			visual.advance(0.05)
			var halfway_alpha: float = visual.head_next.modulate.a
			test.check(halfway_alpha > moving_alpha and halfway_alpha < 1.0,
				"نمای %s باید به‌تدریج و بدون پرش واضح شود" % id)
			visual.advance(0.05)
			test.check(_is_crisp(visual) and _visible_frame(visual) == 8
				and visual.forward_global().distance_to(moving_forward) < 0.00001,
				"توقف %s نباید دو تصویر تار یا تغییر جهت شلیک باقی بگذارد" % id)
			tower.queue_free()
	battle.queue_free()

## Use the Tower processing/target-acquisition path, not just the helper, so a
## dead or removed target cannot leave a permanently blended head behind.
func _test_enemy_death_and_retarget(test) -> void:
	for id in ["machinegun", "cannon", "laser"]:
		var battle := TestBattle.new()
		test.add_child(battle)
		var tower := _tower(test, battle, id)
		var visual = _visual(tower)
		var angle := TAU * 7.5001 / 48.0
		visual.mount.rotation = angle - 0.01
		var enemy := Enemy.new()
		test.add_child(enemy)
		enemy.set_process(false)
		enemy.hp = 100.0
		enemy.max_hp = 100.0
		var hit_point: Vector2 = visual.pivot_global() + _aim_vector(angle) * 90.0
		enemy.global_position = hit_point if id == "cannon" else hit_point + Vector2(0.0, 12.0)
		battle.enemies = [enemy]
		tower.cooldown_left = 100.0
		tower._process(0.02)
		var stopped_angle: float = visual.mount.rotation
		var stopped_forward: Vector2 = visual.forward_global()
		test.check(tower.target == enemy and absf(visual.head_next.modulate.a - 0.5) < 0.01,
			"هدف‌گیری %s پیش از مرگ دشمن باید واقعاً بین دو نما باشد" % id)
		enemy.take_damage(1000.0, true, true)
		for step in 10:
			tower._process(1.0 / 60.0)
		test.check(not enemy.alive and tower.target == null and _is_crisp(visual)
			and absf(angle_difference(stopped_angle, visual.mount.rotation)) < 0.00001
			and visual.forward_global().distance_to(stopped_forward) < 0.00001,
			"پس از شکست دشمن، سر ثابت %s باید واضح و جهت شلیک بدون تغییر باشد" % id)

		# A new enemy must still start a smooth, bounded physical turn.
		var next_angle := angle + 0.5
		var next_enemy := Enemy.new()
		test.add_child(next_enemy)
		next_enemy.set_process(false)
		var next_hit: Vector2 = visual.pivot_global() + _aim_vector(next_angle) * 90.0
		next_enemy.global_position = next_hit if id == "cannon" else next_hit + Vector2(0.0, 12.0)
		battle.enemies = [next_enemy]
		tower._process(0.01)
		var turn: float = angle_difference(stopped_angle, visual.mount.rotation)
		test.check(tower.target == next_enemy and turn > 0.0 and turn <= tower.turn_speed() * 0.01 + 0.0001,
			"هدف تازه باید چرخش نرم و محدود %s را دوباره آغاز کند" % id)
		enemy.queue_free()
		next_enemy.queue_free()
		tower.queue_free()
		battle.queue_free()

## An active laser freezes its physical head and beam. Its artwork can still
## settle, without changing the ray's start, end, damage or duration.
func _test_laser_beam_lock(test) -> void:
	var battle := TestBattle.new()
	test.add_child(battle)
	var tower := _tower(test, battle, "laser")
	var visual = tower._laser_visual
	var angle := TAU * 7.5001 / 48.0
	visual.mount.rotation = angle - 0.01
	var enemy := Enemy.new()
	test.add_child(enemy)
	enemy.set_process(false)
	enemy.hp = 1000.0
	enemy.max_hp = 1000.0
	enemy.global_position = visual.pivot_global() + _aim_vector(angle) * 90.0 + Vector2(0.0, 12.0)
	battle.enemies = [enemy]
	tower._process(0.02)
	var beam: LaserBeam = tower._laser_beam
	if beam == null or not is_instance_valid(beam):
		test.check(false, "پرتوی لیزر برای بررسی توقف ظاهر ساخته نشد")
	else:
		beam.set_process(false)
		var first_start: Vector2 = beam.start
		var first_finish: Vector2 = beam.finish
		var first_angle: float = visual.mount.rotation
		var damage: float = beam.damage_value
		tower._process(0.11)
		test.check(_is_crisp(visual) and visual.mount.rotation == first_angle
			and beam.start == first_start and beam.finish == first_finish
			and beam.damage_value == damage and not beam.finished,
			"واضح‌شدن سر لیزر نباید شلیک یا جهت پرتو فعال را تغییر دهد")
		beam.queue_free()
	enemy.queue_free()
	tower.queue_free()
	battle.queue_free()
