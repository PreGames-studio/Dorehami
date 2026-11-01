## Restored source maps, measured roads, road-free borders and local build grids.
extends RefCounted

func run(test) -> void:
	_test_restored_rasters(test)
	_test_original_desert_lanes(test)

func _road_colour(colour: Color) -> bool:
	var high := maxf(colour.r, maxf(colour.g, colour.b))
	var low := minf(colour.r, minf(colour.g, colour.b))
	return high < 0.65 and high - low < 0.25

func _test_restored_rasters(test) -> void:
	for index in Maps.count():
		var map := GameMap.build(index)
		var image := Assets.tex(map.bg_key).get_image()
		if image.is_compressed():
			image.decompress()
		var margin_key := map.bg_key.replace("maps/", "map_margins/")
		var margin := Assets.tex(margin_key)
		test.check(image.get_size() == Vector2i(1280, 720)
			and margin != null and margin.get_size() == Vector2(1580, 1280),
			"تصویر اصلی و حاشیهٔ نقشهٔ %d باید کامل باشند" % (index + 1))
		test.check(is_equal_approx(map.road_half_width * 2.0, float(Maps.ROAD_WIDTHS[map.bg_key])),
			"عرض جادهٔ نقشهٔ %d باید مطابق تصویر اصلی باشد" % (index + 1))
		# Check actual pixels on the middle of every long route section. Sampling
		# away from the dashed centre avoids treating white markings as asphalt.
		for i in range(1, map.route.size()):
			var a := map.route[i - 1]
			var b := map.route[i]
			if a.distance_to(b) < 110.0:
				continue
			var normal := Vector2(-(b - a).normalized().y, (b - a).normalized().x)
			var road_samples := 0
			var total := 0
			for fraction in [0.3, 0.5, 0.7]:
				for offset in [-10.0, 10.0]:
					var point: Vector2 = a.lerp(b, fraction) + normal * offset
					if GameMap.in_terrain(point):
						total += 1
						var colour := image.get_pixel(clampi(roundi(point.x), 0, 1279), clampi(roundi(point.y), 0, 719))
						if _road_colour(colour):
							road_samples += 1
			test.check(total > 0 and road_samples >= total - 2,
				"مسیر قطعهٔ %d نقشهٔ %d باید با آسفالت اصلی هم‌راستا باشد" % [i, index + 1])

func _test_original_desert_lanes(test) -> void:
	# Atlas slot 3 is map_10. Its original 59px roads and ~132px spacing
	# cannot be handled by the old globally aligned 71px road/grid assumptions.
	var map := GameMap.build(2)
	test.check(map.bg_key == "maps/map_10" and is_equal_approx(map.road_half_width, 29.5),
		"نقشهٔ کویری باید عرض جادهٔ تصویر اولیه را نگه دارد")
	for y in [162.25, 294.0, 426.0, 558.5]:
		var point := Vector2(600.0, y)
		var cell := map.snap_build_cell(point, {})
		test.check(map.build_issue(cell, {}).is_empty() and cell.distance_to(point) <= Balance.GRID_STEP,
			"ردیف قدیمی کویر در y=%s باید محل کاشت ۲×۲ داشته باشد" % y)
		test.check(not map.build_issue(Vector2(600.0, y - 65.75), {}).is_empty(),
			"کاشت داخل جادهٔ قدیمی کویر مجاز نیست")
