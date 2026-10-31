## Real geometry, all directional sprites, and touch/mouse interaction after pan.
extends RefCounted

func run(test) -> void:
	_test_first_map_geometry(test)
	_test_all_tower_art(test)
	await _test_all_map_margins(test)
	await _test_pan_and_build(test)

func _test_first_map_geometry(test) -> void:
	var map := GameMap.build(0)
	var h: float = Balance.HOUSE_SIZE
	test.check(is_equal_approx(Balance.TOWER_SIZE, h * 2.0)
		and is_equal_approx(Balance.MAP_MARGIN, h * 4.0),
		"برج باید ۲×۲ خانه و حاشیه چهار خانه باشد")
	var first_y: float = map.route[0].y
	var middle_y: float = map.route[2].y
	var last_y: float = map.route[4].y
	test.check(absf((middle_y - first_y) - h * 6.0) < 0.01
		and absf((last_y - middle_y) - h * 6.0) < 0.01,
		"میان دو جادهٔ دوخانه‌ای نقشهٔ اول باید چهار خانهٔ خالی باشد")
	test.check(absf(first_y - 148.0) < 0.01 and absf(middle_y - 361.0) < 0.01
		and absf(last_y - 574.0) < 0.01,
		"خط حرکت دشمن باید با مرکز جاده‌های تصویر هماهنگ باشد")

	# Two ２×２ towers fit exactly in a four-cell gap, but neither may
	# overlap a road or another tower, including diagonal overlap.
	for upper_y in [first_y, middle_y]:
		var a := Vector2(600.0, upper_y + h * 2.0)
		var b := a + Vector2(0.0, Balance.TOWER_SIZE)
		var occupied := {a: true}
		test.check(map.build_issue(a, {}).is_empty() and map.build_issue(b, occupied).is_empty(),
			"دو برج مجاور باید دقیقاً در فاصلهٔ چهارخانه‌ای جا شوند")
		test.check(not map.build_issue(a - Vector2(0.0, 0.1), {}).is_empty(),
			"مربع برج نباید حتی کمی وارد جاده شود")
		test.check(not map.build_issue(b - Vector2(0.0, 0.1), occupied).is_empty(),
			"برج‌های کنار هم نباید هم‌پوشانی داشته باشند")
		test.check(not map.build_issue(a + Vector2(70.0, 70.0), occupied).is_empty(),
			"هم‌پوشانی گوشهٔ دو برج هم باید ممنوع باشد")
		test.check(map.build_issue(a + Vector2(Balance.TOWER_SIZE, 0.0), occupied).is_empty(),
			"تماس لبهٔ دو برج، بدون هم‌پوشانی، باید مجاز بماند")

	# Dense maps must not become impossible merely because the larger square
	# is combined with an old centre-point clearance a second time.
	var dense := GameMap.build(2)
	test.check(dense.build_issue(Vector2(800.0, 289.0), {}).is_empty(),
		"نقشهٔ پرپیچ‌وخم باید همچنان جای ساخت داخل خود نقشه داشته باشد")

	var image := Assets.tex("maps/map_01").get_image()
	if image.is_compressed():
		image.decompress()
	for center in [148, 361, 574]:
		for dy in [-28, 28]:
			var road := image.get_pixel(600, center + dy)
			test.check(maxf(road.r, maxf(road.g, road.b)) < 0.65,
				"جادهٔ تصویر باید در هر سوی خط میانی نزدیک به یک خانه عرض داشته باشد")
	for y in [220, 290, 432, 503]:
		var ground := image.get_pixel(600, y)
		test.check(ground.g > 0.6 and ground.g > ground.b,
			"فاصلهٔ چهارخانه‌ای تصویر باید زمین خالی میان جاده‌ها باشد")

## Validate every level, every directional frame and every Gatling animation
## frame, not just the first static preview. Allow room for actual recoil.
func _test_all_tower_art(test) -> void:
	var groups: Array = []
	var gun: Array = [{"key": "towers_machinegun/base", "cols": 1, "rows": 1}]
	var cannon: Array = []
	var laser: Array = []
	var tesla: Array = [{"key": "towers_tesla/base", "cols": 1, "rows": 1}]
	for level in range(1, 6):
		gun.append({"key": "towers_machinegun/machinegun_%d" % level, "cols": 1, "rows": 1})
		gun.append({"key": "towers_machinegun/head_%d_directions" % level, "cols": 8, "rows": 6})
		gun.append({"key": "towers_machinegun/barrels_%d_directions" % level,
			"cols": 16 if level == 5 else 8, "rows": 24 if level == 5 else 6})
		for part in ["artillery", "base"]:
			cannon.append({"key": "towers_artillery/%s_%d" % [part, level], "cols": 1, "rows": 1})
		for part in ["head", "barrel"]:
			cannon.append({"key": "towers_artillery/%s_%d_directions" % [part, level], "cols": 8, "rows": 6})
		for part in ["laser", "base"]:
			laser.append({"key": "towers_laser/%s_%d" % [part, level], "cols": 1, "rows": 1})
		laser.append({"key": "towers_laser/head_%d_directions" % level, "cols": 8, "rows": 6})
		for part in ["tesla", "body", "core"]:
			tesla.append({"key": "towers_tesla/%s_%d" % [part, level], "cols": 1, "rows": 1})
		tesla.append({"key": "towers_tesla/head_%d_directions" % level, "cols": 8, "rows": 6})
	groups.append({"name": "مسلسل", "files": gun, "ground": MachinegunVisual.GROUND,
		"scale": MachinegunVisual.IMAGE_SCALE, "offset": MachinegunVisual.FOOTPRINT_OFFSET,
		"recoil": MachinegunVisual.RECOIL_PIXELS * MachinegunVisual.IMAGE_SCALE})
	groups.append({"name": "توپخانه", "files": cannon, "ground": MachinegunVisual.GROUND,
		"scale": MachinegunVisual.IMAGE_SCALE, "offset": MachinegunVisual.FOOTPRINT_OFFSET,
		"recoil": MachinegunVisual.RECOIL_PIXELS * MachinegunVisual.IMAGE_SCALE})
	groups.append({"name": "لیزر", "files": laser, "ground": LaserVisual.GROUND,
		"scale": LaserVisual.IMAGE_SCALE, "offset": LaserVisual.FOOTPRINT_OFFSET, "recoil": 0.0})
	groups.append({"name": "تسلا", "files": tesla, "ground": TeslaVisual.GROUND,
		"scale": TeslaVisual.IMAGE_SCALE, "offset": TeslaVisual.FOOTPRINT_OFFSET, "recoil": 0.0})

	for group in groups:
		var errors: Array = []
		for spec in group["files"]:
			_check_texture_frames(spec, group, errors)
		test.check(errors.is_empty(), "همهٔ نماها و سطح‌های %s باید داخل مربع چهارخانه‌ای باشند: %s" % [group["name"], errors.slice(0, 4)])

func _check_texture_frames(spec: Dictionary, group: Dictionary, errors: Array) -> void:
	var key := String(spec["key"])
	var texture := Assets.tex(key)
	if texture == null:
		errors.append("missing " + key)
		return
	var image := texture.get_image()
	if image == null or image.is_empty():
		errors.append("empty " + key)
		return
	if image.is_compressed():
		image.decompress()
	var cols := int(spec["cols"])
	var rows := int(spec["rows"])
	var width := image.get_width() / cols
	var height := image.get_height() / rows
	var multiplier := 1.0 if cols == 1 and rows == 1 else 2.0
	var anchor: Vector2 = group["ground"]
	var scale_value := float(group["scale"])
	var offset: Vector2 = group["offset"]
	var recoil := float(group["recoil"])
	var half: float = Balance.TOWER_SIZE * 0.5 + 0.01
	for frame in cols * rows:
		var region := Rect2i((frame % cols) * width, (frame / cols) * height, width, height)
		var used := image.get_region(region).get_used_rect()
		if used.size == Vector2i.ZERO:
			continue
		var left := (used.position.x * multiplier - anchor.x) * scale_value + offset.x - recoil
		var top := (used.position.y * multiplier - anchor.y) * scale_value + offset.y - recoil
		var right := (used.end.x * multiplier - anchor.x) * scale_value + offset.x + recoil
		var bottom := (used.end.y * multiplier - anchor.y) * scale_value + offset.y + recoil
		if left < -half or right > half or top < -half or bottom > half:
			errors.append("%s frame %d [%.1f,%.1f,%.1f,%.1f]" % [key, frame, left, top, right, bottom])
			return

func _test_all_map_margins(test) -> void:
	var saved_map: int = Game.pending_map
	var saved_last_map: int = Game.last_map
	for index in Maps.count():
		Game.pending_map = index
		var battle = load("res://scenes/Battle.tscn").instantiate()
		test.add_child(battle)
		battle.set_process(false)
		await test.get_tree().process_frame
		var initial_entry: Vector2 = battle._world_to_screen(battle.map.entry_pos)
		if battle.map.entry_pos.y < Balance.HUD_HEIGHT + 16.0 or battle.map.entry_pos.y > Balance.WORLD_SIZE.y - 125.0:
			test.check(initial_entry.y > Balance.HUD_HEIGHT and initial_entry.y < Balance.WORLD_SIZE.y - 125.0,
				"ورودی نقشهٔ %d باید از پشت نوارها بیرون بیاید" % (index + 1))
		var hud_pos: Vector2 = battle.hud._coins_label.global_position
		battle._set_pan_offset(Vector2(Balance.MAP_MARGIN * 2.0, -Balance.MAP_MARGIN * 2.0))
		test.check(battle._pan_root.position == Vector2(Balance.MAP_MARGIN, -Balance.MAP_MARGIN)
			and battle.hud._coins_label.global_position == hud_pos
			and battle._ground.map.bg_key == battle.map.bg_key and Assets.tex(battle.map.bg_key) != null,
			"حاشیه و حرکت نقشهٔ %d باید با تصویر خودش و HUD ثابت کار کند" % (index + 1))
		battle._set_pan_offset(Vector2(-Balance.MAP_MARGIN * 2.0, Balance.MAP_MARGIN * 2.0))
		test.check(battle._pan_root.position == Vector2(-Balance.MAP_MARGIN, Balance.MAP_MARGIN),
			"حرکت نقشهٔ %d نباید از حاشیه بیرون برود" % (index + 1))
		battle.queue_free()
		await test.get_tree().process_frame
	Game.pending_map = saved_map
	Game.last_map = saved_last_map

func _mouse_button(pressed: bool, point: Vector2) -> InputEventMouseButton:
	var event := InputEventMouseButton.new()
	event.button_index = MOUSE_BUTTON_LEFT
	event.pressed = pressed
	event.position = point
	return event

func _test_pan_and_build(test) -> void:
	var saved_map: int = Game.pending_map
	var saved_last_map: int = Game.last_map
	var saved_unlocked: Array = Game.unlocked_weapons.duplicate()
	var saved_emulation := bool(ProjectSettings.get_setting("input_devices/pointing/emulate_mouse_from_touch", false))
	Game.pending_map = 0
	Game.unlocked_weapons = Weapons.ids().duplicate()
	var battle = load("res://scenes/Battle.tscn").instantiate()
	test.add_child(battle)
	battle.set_process(false)
	battle.coins = 9999
	await test.get_tree().process_frame

	var hud_pos: Vector2 = battle.hud._coins_label.global_position
	# Exercise the real touch → emulated-mouse → GUI/unhandled input path,
	# including the on-screen mode button, rather than only calling helpers.
	var pan_button_center: Vector2 = battle.hud._pan_button.get_global_rect().get_center()
	await test._touch(pan_button_center, true)
	await test._touch(pan_button_center, false)
	test.check(battle.pan_mode, "لمس واقعی دکمه باید حالت حرکت را روشن کند")
	await test._touch_drag(Vector2(800, 300), Vector2(860, 350))
	test.check(battle._pan_root.position.distance_to(Vector2(60, 50)) < 0.1
		and battle.hud._coins_label.global_position == hud_pos and battle.towers.is_empty(),
		"کشیدن واقعی انگشت باید نقشه را با HUD ثابت جابه‌جا کند")
	await test._touch(pan_button_center, true)
	await test._touch(pan_button_center, false)
	test.check(not battle.pan_mode, "لمس دوبارهٔ دکمه باید حرکت را پایان دهد")
	battle._set_pan_offset(Vector2.ZERO)
	battle.hud._pan_button.button_pressed = true
	test.check(battle.pan_mode and battle.hud._pan_button.text == "پایان حرکت", "دکمهٔ حرکت باید حالت نقشه را فعال کند")
	battle._unhandled_input(_mouse_button(true, Vector2(500, 300)))
	var motion := InputEventMouseMotion.new()
	motion.position = Vector2(590, 220)
	battle._input(motion)
	battle._input(_mouse_button(false, motion.position))
	test.check(not battle._panning and battle._pan_root.position == Vector2(90, -80)
		and battle.hud._coins_label.global_position == hud_pos and battle.towers.is_empty(),
		"کشیدن موس باید فقط نقشه را حرکت دهد، نه HUD یا برج‌ها را")

	# A drag on the toolbar must not pan the map.
	var panel_center: Vector2 = battle.hud._weapon_panel.get_global_rect().get_center()
	battle._begin_pan(panel_center)
	test.check(not battle._panning, "لمس HUD نباید حرکت نقشه را شروع کند")

	# Touch without mouse emulation tracks only the finger that began the pan.
	ProjectSettings.set_setting("input_devices/pointing/emulate_mouse_from_touch", false)
	var touch := InputEventScreenTouch.new()
	touch.index = 3
	touch.position = Vector2(500, 300)
	touch.pressed = true
	battle._unhandled_input(touch)
	var second_touch := InputEventScreenTouch.new()
	second_touch.index = 4
	second_touch.position = Vector2(520, 330)
	second_touch.pressed = true
	battle._input(second_touch)
	battle._unhandled_input(second_touch)
	test.check(battle._pan_touch_id == 3, "انگشت دوم نباید کنترل کشیدن را از انگشت اول بگیرد")
	var drag := InputEventScreenDrag.new()
	drag.index = 4
	drag.position = Vector2(520, 330)
	battle._input(drag)
	test.check(battle._pan_root.position == Vector2(90, -80), "انگشت دوم نباید جای نقشه را عوض کند")
	drag.index = 3
	battle._input(drag)
	touch.pressed = false
	touch.position = drag.position
	battle._input(touch)
	test.check(not battle._panning and battle._pan_root.position == Vector2(110, -50),
		"کشیدن لمسی باید نقشه را حرکت دهد و با رها کردن متوقف شود")

	# Shake animates the child world only. Moving the parent while shaking must
	# survive both the shake's cached base and its final reset.
	Fx.shake(battle._world, 5.0, 0.04)
	battle._set_pan_offset(Vector2(90, -80))
	await test.get_tree().create_timer(0.16).timeout
	test.check(battle._pan_root.position == Vector2(90, -80) and battle._world.position.length() < 0.01,
		"لرزش میدان نباید جابه‌جایی نقشه را برگرداند")

	battle.hud._pan_button.button_pressed = false
	battle._world.position = Vector2(3, -2) # also exercise the live shake transform
	# The drop snaps to the nearest 2×2 house block, so both the preview and the
	# built tower sit on the lattice cell under the pointer.
	var desired: Vector2 = battle.map.snap_build_cell(Vector2(500, 219), {})
	var screen: Vector2 = battle._world_to_screen(desired)
	battle.begin_weapon_drag("machinegun", Vector2(200, 660))
	battle._update_ghost(screen)
	test.check(battle.ghost.visible and battle.ghost.ok and battle.ghost.position.distance_to(screen) < 0.01,
		"پیش‌نمای برج پس از حرکت باید زیر اشاره‌گر و در نقطهٔ معتبر باشد")
	battle._finish_drag(screen)
	test.check(battle.towers.size() == 1 and battle.towers[0].position.distance_to(desired) < 0.01,
		"رهاکردن پس از حرکت باید برج را در همان خانهٔ شبکهٔ ۲×۲ بسازد")
	battle._handle_build_press(true, screen)
	battle._handle_build_press(false, screen)
	test.check(battle.selected_tower == battle.towers[0], "انتخاب برج ساخته‌شده پس از حرکت باید درست باشد")

	battle.select_tower(null)
	battle._world.position = Vector2.ZERO
	battle._set_pan_offset(Vector2(0, Balance.MAP_MARGIN))
	battle.begin_weapon_drag("machinegun", Vector2(200, 660))
	var margin_world := Vector2(500, -60)
	var margin_cell: Vector2 = battle.map.snap_build_cell(margin_world, battle.occupied)
	var margin_screen: Vector2 = battle._world_to_screen(margin_cell)
	battle._update_ghost(margin_screen)
	test.check(battle.ghost.ok and not GameMap.in_terrain(margin_world),
		"بخشی از حاشیهٔ هم‌رنگ باید محل ساخت واقعی باشد")
	battle._finish_drag(margin_screen)
	test.check(battle.towers.size() == 2 and battle.towers[1].position.distance_to(margin_cell) < 0.01,
		"ساخت و مختصات برج در حاشیه باید روی خانهٔ شبکهٔ ۲×۲ باشد")

	# A raw touch drag still builds after panning, rather than moving the map.
	battle._set_pan_offset(Vector2(-90, 40))
	var end_world := Vector2(680, 219)
	# Start far enough from the target cell that this counts as a drag, not a tap
	# (the drop snaps to the 2×2 cell, so the raw pointer distance is what matters).
	var start_world: Vector2 = battle.map.snap_build_cell(end_world, battle.occupied) + Vector2(-70.0, 50.0)
	var end_cell: Vector2 = battle.map.snap_build_cell(end_world, battle.occupied)
	touch.index = 2
	touch.position = battle._world_to_screen(start_world)
	touch.pressed = true
	battle._unhandled_input(touch)
	drag.index = 2
	drag.position = battle._world_to_screen(end_cell)
	battle._input(drag)
	touch.pressed = false
	touch.position = drag.position
	battle._input(touch)
	var last_pos: Vector2 = battle.towers[2].position if battle.towers.size() > 2 else Vector2(-1.0, -1.0)
	test.check(battle.towers.size() == 3 and last_pos.distance_to(end_cell) < 0.01,
		"کشیدن لمسی برای ساخت باید پس از حرکت نقشه همچنان کار کند (n=%d cell=%s tower=%s)"
			% [battle.towers.size(), str(end_cell), str(last_pos)])

	ProjectSettings.set_setting("input_devices/pointing/emulate_mouse_from_touch", saved_emulation)
	battle.queue_free()
	Game.pending_map = saved_map
	Game.last_map = saved_last_map
	Game.unlocked_weapons = saved_unlocked
