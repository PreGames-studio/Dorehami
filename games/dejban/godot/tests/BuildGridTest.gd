## شبکهٔ کاشت ۲×۲: هر برج باید دقیقاً داخل یک بلوک چهارخانه‌ای بنشیند و برج دوم
## همیشه جای کاشتِ دقیقاً زیر (یا کنار) برج اول را داشته باشد.
##
## این تست همان شکایتی را قفل می‌کند که با ساخت آزاد پیش می‌آمد: کاشتن برج با
## فاصلهٔ یک خانه، جای برج بعدی را می‌بست.
extends RefCounted

func run(test) -> void:
	_test_lattice_alignment(test)
	await _test_two_towers_stack_exactly(test)
	_test_forbidden_spot_does_not_teleport(test)
	_test_every_map_has_a_cell(test)

func _test_lattice_alignment(test) -> void:
	var map := GameMap.build(0)
	var step: float = Balance.GRID_STEP
	test.check(is_equal_approx(step, Balance.HOUSE_SIZE * 2.0),
		"گام شبکهٔ کاشت باید دو خانه باشد")
	# هر نقطهٔ دلخواه به مرکز یک بلوک کامل گرد می‌شود.
	var probe := Vector2(603.0, 227.0)
	var cell: Vector2 = map.grid_cell(probe)
	test.check(is_zero_approx(fposmod(cell.x - map.grid_origin.x, step))
		and is_zero_approx(fposmod(cell.y - map.grid_origin.y, step)),
		"مرکز بلوک باید دقیقاً روی شبکهٔ نقشه بیفتد")
	test.check(cell.distance_to(probe) <= step, "گردکردن نباید بیشتر از یک بلوک جابه‌جا کند")
	# همهٔ رأس‌های جادهٔ افقی این نقشه روی همین شبکه‌اند، پس لبهٔ برج به لبهٔ جاده می‌چسبد.
	for i in range(1, map.route.size()):
		if absf(map.route[i].y - map.route[i - 1].y) < 0.01:
			var line_y: float = map.route[i].y
			test.check(is_zero_approx(fposmod(line_y - map.grid_origin.y, step)),
				"محور جادهٔ افقی باید روی شبکهٔ کاشت باشد")
			break

func _test_two_towers_stack_exactly(test) -> void:
	var battle = load("res://scenes/Battle.tscn").instantiate()
	test.add_child(battle)
	await test.get_tree().process_frame
	await test.get_tree().process_frame
	battle.coins = 9999
	# همان نقشه‌ای که صحنهٔ نبرد ساخته است (آزمون روی نقشهٔ واقعی اجرا می‌شود).
	var map: GameMap = battle.map
	var road_y: float = map.route[0].y
	var first := Vector2(600.0, road_y + Balance.GRID_STEP * 1.5)
	var cell_a: Vector2 = map.snap_build_cell(first, {})
	test.check(map.build_issue(cell_a, {}).is_empty(), "بلوک اول باید قابل ساخت باشد")
	# برج دوم: دقیقاً زیر برج اول، به‌علاوهٔ یک برج (نه یک خانه).
	var below: Vector2 = cell_a + Vector2(0.0, Balance.GRID_STEP)
	var cell_b: Vector2 = map.snap_build_cell(below + Vector2(6.0, -9.0), {cell_a: true})
	test.check(cell_b.distance_to(cell_a + Vector2(0.0, Balance.GRID_STEP)) < 0.01,
		"برج دوم باید دقیقاً یک برج پایین‌تر از برج اول بنشیند")
	test.check(map.build_issue(cell_b, {cell_a: true}).is_empty(),
		"برج دوم نباید با برج اول هم‌پوشانی داشته باشد")
	# کاشت واقعی از همان مسیر بازی: دو برج پشت‌سرهم ساخته می‌شوند.
	battle.selected_weapon = "machinegun"
	battle._try_build(first)
	var built_a: Vector2 = battle.towers[0].position if battle.towers.size() > 0 else Vector2.INF
	battle._try_build(built_a + Vector2(0.0, Balance.GRID_STEP) + Vector2(5.0, -7.0))
	test.check(battle.towers.size() == 2, "دو برج پشت‌سرهم باید هر دو کاشته شوند")
	if battle.towers.size() == 2:
		var delta: Vector2 = battle.towers[1].position - battle.towers[0].position
		test.check(absf(delta.x) < 0.01 and is_equal_approx(absf(delta.y), Balance.GRID_STEP),
			"فاصلهٔ دو برج باید دقیقاً یک برج (دو خانه) باشد")
	battle.queue_free()

func _test_forbidden_spot_does_not_teleport(test) -> void:
	var map := GameMap.build(0)
	var cell: Vector2 = map.snap_build_cell(Vector2(600.0, map.route[0].y + Balance.GRID_STEP * 1.5), {})
	var occupied := {cell: true}
	# رهاکردن داخل برج موجود نباید برج تازه‌ای در بلوک کنار بسازد.
	test.check(map.snap_build_cell(cell + Vector2(10.0, 6.0), occupied).distance_to(cell) < 0.01,
		"رهاکردن روی برج موجود نباید به بلوک دیگری بپرد")
	# رهاکردن وسط جاده هم نباید با پرتاب به بلوک مجاز، کاشت را مجاز کند.
	var on_road: Vector2 = map.point_at(map.total_length * 0.5)
	var snapped: Vector2 = map.snap_build_cell(on_road, {})
	test.check(not map.build_issue(snapped, {}).is_empty(),
		"شبکه نباید کاشت روی جاده را با پرتاب به بلوک کنار مجاز کند")

func _test_every_map_has_a_cell(test) -> void:
	for index in Maps.count():
		var map := GameMap.build(index)
		var found := Vector2.INF
		for y in range(120, 640, 20):
			for x in range(120, 1160, 20):
				var candidate: Vector2 = map.snap_build_cell(Vector2(x, y), {})
				if map.build_issue(candidate, {}).is_empty():
					found = candidate
					break
			if found != Vector2.INF:
				break
		test.check(found != Vector2.INF, "نقشهٔ %d باید دست‌کم یک بلوک ۲×۲ آزاد داشته باشد" % (index + 1))
		test.check(is_zero_approx(fposmod(found.x - map.grid_origin.x, Balance.GRID_STEP))
			and is_zero_approx(fposmod(found.y - map.grid_origin.y, Balance.GRID_STEP)),
			"بلوک آزاد نقشهٔ %d باید روی شبکه باشد" % (index + 1))
