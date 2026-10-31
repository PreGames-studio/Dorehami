## Raster, route and real square-footprint checks for all twelve maps.
extends RefCounted

# Facing zigzag lanes only: expansive U-shaped fields are intentionally left
# open. The third and tenth atlas cells use map_10 and map_03 respectively.
const GAPS := [
	{"bg": "maps/map_01", "axis": "h", "lo": 148.0, "hi": 361.0, "at": 600.0, "houses": 4},
	{"bg": "maps/map_01", "axis": "h", "lo": 361.0, "hi": 574.0, "at": 600.0, "houses": 4},
	{"bg": "maps/map_02", "axis": "h", "lo": 143.0, "hi": 356.0, "at": 600.0, "houses": 4},
	{"bg": "maps/map_04", "axis": "h", "lo": 45.5, "hi": 187.5, "at": 500.0, "houses": 2},
	{"bg": "maps/map_04", "axis": "h", "lo": 187.5, "hi": 329.5, "at": 530.0, "houses": 2},
	{"bg": "maps/map_04", "axis": "h", "lo": 329.5, "hi": 542.5, "at": 530.0, "houses": 4},
	{"bg": "maps/map_04", "axis": "h", "lo": 542.5, "hi": 684.5, "at": 640.0, "houses": 2},
	{"bg": "maps/map_04", "axis": "v", "lo": 214.0, "hi": 427.0, "at": 436.0, "houses": 4},
	{"bg": "maps/map_04", "axis": "v", "lo": 853.0, "hi": 1066.0, "at": 300.0, "houses": 4},
	{"bg": "maps/map_07", "axis": "h", "lo": 110.0, "hi": 252.0, "at": 500.0, "houses": 2},
	{"bg": "maps/map_07", "axis": "h", "lo": 252.0, "hi": 394.0, "at": 500.0, "houses": 2},
	{"bg": "maps/map_07", "axis": "h", "lo": 394.0, "hi": 536.0, "at": 750.0, "houses": 2},
	{"bg": "maps/map_07", "axis": "h", "lo": 394.0, "hi": 607.0, "at": 500.0, "houses": 4},
	{"bg": "maps/map_08", "axis": "v", "lo": 214.0, "hi": 427.0, "at": 300.0, "houses": 4},
	{"bg": "maps/map_08", "axis": "v", "lo": 427.0, "hi": 640.0, "at": 300.0, "houses": 4},
	{"bg": "maps/map_08", "axis": "v", "lo": 640.0, "hi": 853.0, "at": 300.0, "houses": 4},
	{"bg": "maps/map_08", "axis": "v", "lo": 853.0, "hi": 1066.0, "at": 400.0, "houses": 4},
	{"bg": "maps/map_10", "axis": "h", "lo": 76.0, "hi": 218.0, "at": 600.0, "houses": 2},
	{"bg": "maps/map_10", "axis": "h", "lo": 218.0, "hi": 360.0, "at": 600.0, "houses": 2},
	{"bg": "maps/map_10", "axis": "h", "lo": 360.0, "hi": 502.0, "at": 600.0, "houses": 2},
	{"bg": "maps/map_10", "axis": "h", "lo": 502.0, "hi": 644.0, "at": 600.0, "houses": 2},
	{"bg": "maps/map_11", "axis": "v", "lo": 214.0, "hi": 356.0, "at": 300.0, "houses": 2},
	{"bg": "maps/map_11", "axis": "v", "lo": 356.0, "hi": 498.0, "at": 300.0, "houses": 2},
	{"bg": "maps/map_11", "axis": "v", "lo": 498.0, "hi": 640.0, "at": 300.0, "houses": 2},
	{"bg": "maps/map_11", "axis": "v", "lo": 640.0, "hi": 782.0, "at": 300.0, "houses": 2},
	{"bg": "maps/map_11", "axis": "v", "lo": 782.0, "hi": 924.0, "at": 300.0, "houses": 2},
	{"bg": "maps/map_11", "axis": "v", "lo": 924.0, "hi": 1066.0, "at": 300.0, "houses": 2},
]

func run(test) -> void:
	_test_all_rasters_and_pads(test)
	await _test_real_battle_builds(test)

func _test_all_rasters_and_pads(test) -> void:
	var cases_checked := 0
	for index in Maps.count():
		var map := GameMap.build(index)
		var texture := Assets.tex(map.bg_key)
		if texture == null:
			test.check(false, "تصویر نقشهٔ %d در آزمون مقیاس وجود ندارد" % (index + 1))
			continue
		var image := texture.get_image()
		if image.is_compressed():
			image.decompress()
		test.check(image.get_width() == 1280 and image.get_height() == 720,
			"اندازهٔ تصویر %s باید با میدان یکی باشد" % map.bg_key)
		_check_raster_segments(test, map, image)
		for pad in map.pads:
			test.check(map.build_issue(pad, {}).is_empty(),
				"سکوی %s باید با ابعاد واقعی برج قابل ساخت باشد" % map.bg_key)
		for spec in GAPS:
			if spec["bg"] == map.bg_key:
				_check_gap(test, map, image, spec)
				cases_checked += 1
	test.check(cases_checked == GAPS.size(), "همهٔ فاصله‌های زیگزاگی باید بررسی شوند")
	var first_map := GameMap.build(0)
	var free := Vector2(500.0, 245.0)
	var deep_in_road := Vector2(500.0, 148.0)
	test.check(first_map.build_issue(free, {}).is_empty()
		and first_map.snap_build_position(free, {}) == free,
		"ساخت در زمین آزاد نباید بی‌دلیل به شبکه یا جاده بچسبد")
	test.check(first_map.snap_build_position(deep_in_road, {}) == deep_in_road
		and not first_map.build_issue(deep_in_road, {}).is_empty(),
		"کمک لمسی نباید کاشت داخل خود جاده را مجاز کند")
	var mirrored_edge := Vector2(600.0, -77.0)
	var near_mirror := Vector2(600.0, -83.0)
	test.check(not first_map.build_issue(near_mirror, {}).is_empty()
		and first_map.build_issue(mirrored_edge, {}).is_empty()
		and first_map.snap_build_position(near_mirror, {}).distance_to(mirrored_edge) < 0.01,
		"کمک لمس و حریم جادهٔ بازتاب‌شده در حاشیه نیز باید هماهنگ باشند")
	var tight_map := GameMap.build(2)
	var too_far := Vector2(600.0, 134.0)
	test.check(not tight_map.build_issue(too_far, {}).is_empty()
		and tight_map.snap_build_position(too_far, {}) == too_far,
		"کمک لمس نباید جای رهاشدن را بیش از دوازده پیکسل جابه‌جا کند")

func _has_segment(map: GameMap, horizontal: bool, fixed: float, along: float) -> bool:
	for i in range(1, map.route.size()):
		var a := map.route[i - 1]
		var b := map.route[i]
		if horizontal and absf(a.y - fixed) < 0.01 and absf(b.y - fixed) < 0.01:
			if along >= minf(a.x, b.x) and along <= maxf(a.x, b.x):
				return true
		if not horizontal and absf(a.x - fixed) < 0.01 and absf(b.x - fixed) < 0.01:
			if along >= minf(a.y, b.y) and along <= maxf(a.y, b.y):
				return true
	return false

func _at(horizontal: bool, along: float, normal: float) -> Vector2:
	return Vector2(along, normal) if horizontal else Vector2(normal, along)

func _road_colour(colour: Color) -> bool:
	var high := maxf(colour.r, maxf(colour.g, colour.b))
	var low := minf(colour.r, minf(colour.g, colour.b))
	return high < 0.65 and high - low < 0.25

func _pixel(image: Image, pos: Vector2) -> Color:
	return image.get_pixel(clampi(roundi(pos.x), 0, image.get_width() - 1),
		clampi(roundi(pos.y), 0, image.get_height() - 1))

func _check_gap(test, map: GameMap, image: Image, spec: Dictionary) -> void:
	var horizontal: bool = String(spec["axis"]) == "h"
	var lo := float(spec["lo"])
	var hi := float(spec["hi"])
	var along := float(spec["at"])
	var houses := int(spec["houses"])
	var label := "%s %s %.1f–%.1f" % [map.bg_key, spec["axis"], lo, hi]
	var free_width := hi - lo - 2.0 * Balance.HOUSE_SIZE
	test.check(absf(free_width - houses * Balance.HOUSE_SIZE) < 0.01
		and _has_segment(map, horizontal, lo, along) and _has_segment(map, horizontal, hi, along),
		"فاصلهٔ آزاد %s باید دقیقاً %d خانه و مسیرها موازی باشند" % [label, houses])

	# Sample the actual bitmap, including both asphalt interiors and both
	# edges of the gap; waypoint numbers alone cannot prove the art changed.
	var first_road := _road_colour(_pixel(image, _at(horizontal, along, lo + 31.0)))
	var second_road := _road_colour(_pixel(image, _at(horizontal, along, hi - 31.0)))
	var edge_a := _road_colour(_pixel(image, _at(horizontal, along, lo + 38.0)))
	var edge_b := _road_colour(_pixel(image, _at(horizontal, along, hi - 38.0)))
	test.check(first_road and second_road and not edge_a and not edge_b,
		"لبهٔ جادهٔ تصویر %s باید با محور و فاصلهٔ مسیر هماهنگ باشد" % label)

	var normal := Vector2.DOWN if horizontal else Vector2.RIGHT
	var first := _at(horizontal, along, lo + Balance.TOWER_SIZE)
	var occupied := {first: true}
	test.check(map.build_issue(first, {}).is_empty()
		and not _road_colour(_pixel(image, first)),
		"یک برج باید در فاصلهٔ آزاد %s واقعاً جا شود" % label)
	test.check(map.build_issue(first - normal * 0.1, {}) == "داخل محدودهٔ جاده",
		"برج نباید حتی اندکی وارد جادهٔ اول %s شود" % label)
	var near_first := first - normal * 6.0
	test.check(not map.build_issue(near_first, {}).is_empty()
		and map.snap_build_position(near_first, {}).distance_to(first) < 0.01,
		"پیش‌نمای کاشت باید خطای کوچک لمس را در فاصلهٔ %s به محل مجاز برساند" % label)
	if houses == 4:
		var second := first + normal * Balance.TOWER_SIZE
		test.check(map.build_issue(second, occupied).is_empty()
			and not _road_colour(_pixel(image, second)),
			"دو برج چسبیده باید در فاصلهٔ چهارخانه‌ای %s جا شوند" % label)
		test.check(map.build_issue(second - normal * 0.1, occupied) == "داخل محدودهٔ برج دیگر"
			and map.build_issue(second + normal * 0.1, {}) == "داخل محدودهٔ جاده",
			"هم‌پوشانی برج‌ها یا ورود به جادهٔ دوم %s باید رد شود" % label)
		test.check(map.snap_build_position(second - normal * 6.0, occupied).distance_to(second) < 0.01,
			"لمس نزدیک برج دوم %s باید به جای معتبر چسبیده هدایت شود" % label)
	else:
		test.check(map.build_issue(first + normal * 0.1, {}) == "داخل محدودهٔ جاده"
			and not map.build_issue(first + normal * Balance.TOWER_SIZE, occupied).is_empty(),
			"در فاصلهٔ دوخانه‌ای %s فقط یک ردیف برج جا می‌شود" % label)

func _check_raster_segments(test, map: GameMap, image: Image) -> void:
	for i in range(1, map.route.size()):
		var a := map.route[i - 1]
		var b := map.route[i]
		var delta := b - a
		var direction := delta.normalized()
		var normal := Vector2(-direction.y, direction.x)
		if delta.length() < 110.0:
			# At elbows and the coloured finish marker, test only the interior;
			# an edge sample could legitimately land on the adjacent road.
			var short_count := 0
			var short_road := 0
			for t in [0.25, 0.5, 0.75]:
				var center := a.lerp(b, t)
				for offset in [-20.0, -10.0, 10.0, 20.0]:
					var pos: Vector2 = center + normal * float(offset)
					if GameMap.in_terrain(pos):
						short_count += 1
						if _road_colour(_pixel(image, pos)):
							short_road += 1
			test.check(short_count >= 8 and short_road >= short_count - 4,
				"پیچ کوتاه %d در %s باید روی جادهٔ تصویر باشد" % [i, map.bg_key])
			continue
		var inner_count := 0
		var inner_road := 0
		var outer_count := 0
		var outer_clear := 0
		for t in [0.25, 0.5, 0.75]:
			var center := a.lerp(b, t)
			for offset in [-28.0, -20.0, 20.0, 28.0]:
				var pos: Vector2 = center + normal * float(offset)
				if GameMap.in_terrain(pos):
					inner_count += 1
					if _road_colour(_pixel(image, pos)):
						inner_road += 1
			for offset in [-43.0, 43.0]:
				var pos: Vector2 = center + normal * float(offset)
				if GameMap.in_terrain(pos) and map.distance_to_route(pos) > 41.0:
					outer_count += 1
					if not _road_colour(_pixel(image, pos)):
						outer_clear += 1
		# One decorative rock or tree is allowed outside a road, and map_09
		# deliberately has a pale outline slightly inside its asphalt.
		test.check(inner_count >= 8 and inner_road >= inner_count - 4
			and outer_clear >= outer_count - 1,
			"تصویر و خط حرکت قطعهٔ %d در %s باید جاده‌ای نزدیک به دو خانه داشته باشند" % [i, map.bg_key])

func _test_real_battle_builds(test) -> void:
	var saved_map: int = Game.pending_map
	var saved_last_map: int = Game.last_map
	var saved_unlocked: Array = Game.unlocked_weapons.duplicate()
	Game.unlocked_weapons = Weapons.ids().duplicate()
	# Build through the same drag/ghost/release path used in a live battle.
	for scenario in [
		{"index": 2, "releases": [Vector2(600, 295)], "points": [Vector2(600, 289)]},
		{"index": 7, "releases": [Vector2(492, 300), Vector2(563, 300)],
			"points": [Vector2(498, 300), Vector2(569, 300)]},
	]:
		Game.pending_map = int(scenario["index"])
		var battle = load("res://scenes/Battle.tscn").instantiate()
		test.add_child(battle)
		battle.set_process(false)
		battle.coins = 9999
		await test.get_tree().process_frame
		var cells: Array[Vector2] = []
		for i in range(scenario["releases"].size()):
			var screen: Vector2 = battle._world_to_screen(scenario["releases"][i])
			# The drop snaps to the nearest 2×2 house block of this map.
			var cell: Vector2 = battle.map.snap_build_cell(scenario["releases"][i], battle.occupied)
			cells.append(cell)
			var expected: Vector2 = battle._world_to_screen(cell)
			battle.begin_weapon_drag("machinegun", Vector2(200, 660))
			battle._update_ghost(screen)
			test.check(battle.ghost.visible and battle.ghost.ok
				and battle.ghost.position.distance_to(expected) < 0.01,
				"پیش‌نمای ساخت در زیگزاگ نقشهٔ %d باید به خانهٔ شبکهٔ ۲×۲ برود" % (int(scenario["index"]) + 1))
			battle._finish_drag(screen)
		test.check(battle.towers.size() == scenario["points"].size(),
			"برج‌های زیگزاگ نقشهٔ %d باید واقعاً ساخته شوند" % (int(scenario["index"]) + 1))
		for i in range(mini(battle.towers.size(), cells.size())):
			test.check(battle.towers[i].position.distance_to(cells[i]) < 0.01,
				"مختصات برج زیگزاگ باید همان خانهٔ معتبر پیش‌نمایش باشد")
		if int(scenario["index"]) == 2:
			# Inject real touch events in another tight row, not just helper calls.
			var touch_cell: Vector2 = battle.map.snap_build_cell(Vector2(750, 431), battle.occupied)
			await test._touch_drag(battle._world_to_screen(Vector2(650, 425)),
				battle._world_to_screen(touch_cell + Vector2(4.0, 5.0)))
			test.check(battle.towers.size() == 2
				and battle.towers[1].position.distance_to(touch_cell) < 0.01,
				"کشیدن واقعی انگشت در فاصلهٔ دوخانه‌ای باید برج را در خانهٔ مجاز بکارد")
		battle.queue_free()
		await test.get_tree().process_frame
	Game.pending_map = saved_map
	Game.last_map = saved_last_map
	Game.unlocked_weapons = saved_unlocked
