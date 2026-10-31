## نقشهٔ زندهٔ یک مرحله: مسیر دشمن، کاشی‌های زمین، دکور و محدودهٔ ساخت‌وساز.
## همه‌چیز قطعی است: با یک بذر، هر بار همان نقشه ساخته می‌شود.
class_name GameMap
extends RefCounted

var index := 0
var title := ""
var biome_key := "meadow"
var bg_key := ""
var biome: Dictionary = {}
var seed := 1
var keep_tile := Vector2i.ZERO
var keep_pos := Vector2.ZERO
var entry_side := "left"
var entry_pos := Vector2.ZERO
var entry_rot := 0.0
var route := PackedVector2Array()
var route_dist := PackedFloat32Array()
var total_length := 0.0
var pads := PackedVector2Array()
var ground_tiles: Dictionary = {}
var decor: Array = []
var blocked_tiles: Dictionary = {}
## مانع‌های واقعی (سنگ و درخت) با شعاع برخورد؛ ملاک ساخت‌وساز این‌هاست.
var blockers: Array = []
var ambient := Color.WHITE
## مبدأ شبکهٔ کاشت ۲×۲: با محور جادهٔ همین نقشه هم‌تراز می‌شود تا لبهٔ هر برج
## دقیقاً به لبهٔ جاده بچسبد.
var grid_origin := Vector2.ZERO
var rng := RandomNumberGenerator.new()

static func build(map_index: int) -> GameMap:
	var map := GameMap.new()
	map.setup(map_index)
	return map

static func tile_center(tile: Vector2i) -> Vector2:
	var row_h := Balance.TERRAIN_RECT.size.y / float(Balance.ROWS)
	return Vector2((tile.x + 0.5) * Balance.TILE, Balance.TERRAIN_TOP + (tile.y + 0.5) * row_h)

static func tile_of(pos: Vector2) -> Vector2i:
	var row_h := Balance.TERRAIN_RECT.size.y / float(Balance.ROWS)
	var x := int(floor(pos.x / Balance.TILE))
	var y := int(floor((pos.y - Balance.TERRAIN_TOP) / row_h))
	return Vector2i(clampi(x, 0, Balance.COLS - 1), clampi(y, 0, Balance.ROWS - 1))

static func in_terrain(pos: Vector2, margin := 0.0) -> bool:
	return Balance.TERRAIN_RECT.grow(-margin).has_point(pos)

## حاشیه هم زمین ساخت است، اما اندازهٔ اصلی تصویر و مسیر تغییر نمی‌کند.
static func in_build_bounds(pos: Vector2, margin := 0.0) -> bool:
	return Balance.BUILD_RECT.grow(-margin).has_point(pos)

func setup(map_index: int) -> void:
	index = clampi(map_index, 0, Maps.count() - 1)
	var def := Maps.def(index)
	title = String(def["name"])
	biome_key = String(def["biome"])
	bg_key = String(def.get("bg", ""))
	biome = Maps.BIOMES.get(biome_key, Maps.BIOMES["meadow"])
	seed = int(def["seed"])
	ambient = biome["ambient"]
	rng.seed = seed
	keep_tile = def["keep"]
	keep_pos = tile_center(keep_tile)
	entry_side = String(def["entry"])
	if def.has("waypoints"):
		_build_custom_route(def["waypoints"], bool(def.get("square_corners", false)))
	else:
		_build_route(int(def["complexity"]))
	_build_pads(def.get("pads", []))
	_build_grid_axis()
	_build_ground()
	_build_decor(float(def["decor"]))

# ------------------------------------------------------------------ مسیر و جایگاه‌ها

func _build_pads(raw_pads: Array) -> void:
	pads = PackedVector2Array()
	var row_h := Balance.TERRAIN_RECT.size.y / float(Balance.ROWS)
	for item in raw_pads:
		var g := item as Vector2
		pads.append(Vector2(g.x * Balance.TILE, Balance.TERRAIN_TOP + g.y * row_h))

func _build_custom_route(waypoints: Array, square_corners := false) -> void:
	var pts: Array[Vector2] = []
	var row_h := Balance.TERRAIN_RECT.size.y / float(Balance.ROWS)
	for item in waypoints:
		var g := item as Vector2
		pts.append(Vector2(g.x * Balance.TILE, Balance.TERRAIN_TOP + g.y * row_h))
	if pts.size() >= 2:
		entry_pos = pts[0]
		keep_pos = pts[pts.size() - 1]
		keep_tile = tile_of(keep_pos)
	entry_rot = PI * 0.5
	# مسیرهای تصویریِ فعلی گوشهٔ شکسته دارند؛ نقشه‌های آینده می‌توانند نرم باشند.
	route = PackedVector2Array(pts) if square_corners else _round_corners(pts, 12.0)
	route_dist = PackedFloat32Array()
	total_length = 0.0
	route_dist.append(0.0)
	for i in range(1, route.size()):
		total_length += route[i].distance_to(route[i - 1])
		route_dist.append(total_length)

func _build_route(complexity: int) -> void:
	var left := 130.0
	var right := Balance.WORLD_SIZE.x - 130.0
	var top := Balance.TERRAIN_TOP + 74.0
	var bottom := Balance.WORLD_SIZE.y - 74.0
	var lanes := clampi(complexity, 2, 5)
	var pts: Array[Vector2] = []

	if entry_side == "left" or entry_side == "right":
		var from_left := entry_side == "left"
		var y0 := rng.randf_range(top, bottom)
		pts.append(Vector2(-90.0 if from_left else Balance.WORLD_SIZE.x + 90.0, y0))
		entry_pos = Vector2(30.0 if from_left else Balance.WORLD_SIZE.x - 30.0, y0)
		entry_rot = PI * 0.5
		var xs: Array[float] = []
		for i in lanes:
			var k := (i + 0.7) / (lanes + 0.4)
			var x := lerpf(left, right, k) if from_left else lerpf(right, left, k)
			xs.append(clampf(x + rng.randf_range(-30.0, 30.0), left, right))
		xs.sort()
		if not from_left:
			xs.reverse()
		var y := y0
		for x in xs:
			pts.append(Vector2(x, y))
			y = rng.randf_range(top, bottom)
			pts.append(Vector2(x, y))
		pts.append(Vector2(keep_pos.x, y))
		pts.append(keep_pos)
	else:
		var from_top := entry_side == "top"
		var x0 := rng.randf_range(left, right)
		pts.append(Vector2(x0, Balance.TERRAIN_TOP - 90.0 if from_top else Balance.WORLD_SIZE.y + 90.0))
		entry_pos = Vector2(x0, Balance.TERRAIN_TOP + 26.0 if from_top else Balance.WORLD_SIZE.y - 26.0)
		entry_rot = 0.0
		var ys: Array[float] = []
		for i in lanes:
			var k := (i + 0.7) / (lanes + 0.4)
			var y := lerpf(top, bottom, k) if from_top else lerpf(bottom, top, k)
			ys.append(clampf(y + rng.randf_range(-30.0, 30.0), top, bottom))
		ys.sort()
		if not from_top:
			ys.reverse()
		var x := x0
		for y in ys:
			pts.append(Vector2(x, y))
			x = rng.randf_range(left, right)
			pts.append(Vector2(x, y))
		pts.append(Vector2(x, keep_pos.y))
		pts.append(keep_pos)

	route = _round_corners(pts, 30.0)
	route_dist = PackedFloat32Array()
	total_length = 0.0
	route_dist.append(0.0)
	for i in range(1, route.size()):
		total_length += route[i].distance_to(route[i - 1])
		route_dist.append(total_length)

## گوشه‌های تیز مسیر را با منحنی بزیه چندگامی نرم می‌کند تا حرکت سربازها در پیچ‌ها کاملاً روان باشد.
func _round_corners(points: Array[Vector2], radius: float) -> PackedVector2Array:
	var out := PackedVector2Array()
	if points.size() < 3:
		for p in points:
			out.append(p)
		return out
	out.append(points[0])
	for i in range(1, points.size() - 1):
		var prev := points[i - 1]
		var cur := points[i]
		var next := points[i + 1]
		var d1 := (cur - prev)
		var d2 := (next - cur)
		if d1.length() < 1.0 or d2.length() < 1.0:
			continue
		var r1: float = minf(radius, d1.length() * 0.45)
		var r2: float = minf(radius, d2.length() * 0.45)
		var p_in := cur - d1.normalized() * r1
		var p_out := cur + d2.normalized() * r2
		for step_i in range(5):
			var u := float(step_i) / 4.0
			var q0 := p_in.lerp(cur, u)
			var q1 := cur.lerp(p_out, u)
			out.append(q0.lerp(q1, u))
	out.append(points[points.size() - 1])
	return out

## نقطهٔ مسیر در فاصلهٔ t پیکسل از آغاز.
func point_at(t: float) -> Vector2:
	if route.is_empty():
		return keep_pos
	t = clampf(t, 0.0, total_length)
	var lo := 0
	var hi := route.size() - 1
	while lo < hi:
		var mid := (lo + hi + 1) / 2
		if route_dist[mid] <= t:
			lo = mid
		else:
			hi = mid - 1
	if lo >= route.size() - 1:
		return route[route.size() - 1]
	var seg := route_dist[lo + 1] - route_dist[lo]
	var k := 0.0 if seg <= 0.001 else (t - route_dist[lo]) / seg
	return route[lo].lerp(route[lo + 1], k)

func direction_at(t: float) -> Vector2:
	var a := point_at(maxf(t - 8.0, 0.0))
	var b := point_at(minf(t + 8.0, total_length))
	var d := b - a
	if d.length() < 0.01:
		return Vector2.RIGHT
	return d.normalized()

## کوتاه‌ترین فاصلهٔ یک نقطه تا محور مسیر.
func distance_to_route(pos: Vector2) -> float:
	var best := 1.0e9
	for i in range(route.size() - 1):
		var a := route[i]
		var b := route[i + 1]
		var ab := b - a
		var length_sq := ab.length_squared()
		var t := 0.0
		if length_sq > 0.001:
			t = clampf((pos - a).dot(ab) / length_sq, 0.0, 1.0)
		var point := a + ab * t
		best = minf(best, point.distance_to(pos))
	return best

# ------------------------------------------------------------------ زمین و دکور

func _build_ground() -> void:
	ground_tiles.clear()
	var main_key := String(biome["ground"])
	var alt_key := String(biome["alt"])
	for y in Balance.ROWS:
		for x in Balance.COLS:
			var key := alt_key if rng.randf() < 0.24 else main_key
			ground_tiles[Vector2i(x, y)] = key

func _build_decor(density: float) -> void:
	decor.clear()
	blocked_tiles.clear()
	blockers.clear()
	if not bg_key.is_empty():
		return
	var props: Array = biome["props"]
	var blocker_keys: Array = biome["blockers"]
	for y in Balance.ROWS:
		for x in Balance.COLS:
			var tile := Vector2i(x, y)
			var center := tile_center(tile)
			if center.distance_to(keep_pos) < Balance.KEEP_CLEARANCE + 46.0:
				continue
			if distance_to_route(center) < Balance.ROUTE_CLEARANCE + 24.0:
				continue
			if rng.randf() > density:
				continue
			var key := String(props[rng.randi_range(0, props.size() - 1)])
			var pos := center + Vector2(rng.randf_range(-15.0, 15.0), rng.randf_range(-13.0, 13.0))
			var blocking := blocker_keys.has(key)
			decor.append({
				"key": key, "pos": pos, "rot": 0.0,
				"scale": rng.randf_range(0.9, 1.14), "flip": rng.randf() < 0.5,
				"blocking": blocking,
			})
			if blocking:
				blocked_tiles[tile] = true
				blockers.append({"pos": pos, "radius": 26.0 * float(rng.randf_range(0.9, 1.1))})
	_route_torches()
	decor.append({
		"key": "structures/gate", "pos": entry_pos, "rot": entry_rot,
		"scale": 1.0, "flip": entry_side == "right", "blocking": false,
	})

## مشعل‌های کنار مسیر، یک‌درمیان راست و چپ.
func _route_torches() -> void:
	var count := int(total_length / 210.0)
	if count <= 0:
		return
	for i in count:
		var t := (i + 0.5) * (total_length / float(count))
		var base := point_at(t)
		var dir := direction_at(t)
		var perp := Vector2(-dir.y, dir.x) * (1.0 if i % 2 == 0 else -1.0)
		var pos := base + perp * 46.0
		if not in_terrain(pos, 34.0):
			continue
		decor.append({
			"key": "props/torch", "pos": pos, "rot": 0.0,
			"scale": 1.0, "flip": i % 2 != 0, "blocking": false,
		})

# ------------------------------------------------------------------ شبکهٔ کاشت

## محور شبکه از خود مسیر گرفته می‌شود: بلندترین پارهٔ افقی، خط y را و بلندترین
## پارهٔ عمودی، خط x را تعیین می‌کند. چون جاده و حریم ساخت با مقیاس ۷۱ پیکسلی
## هماهنگ‌اند، لبهٔ بلوک‌های ۲×۲ دقیقاً روی لبهٔ جاده می‌افتد.
func _build_grid_axis() -> void:
	var best_h := 0.0
	var best_v := 0.0
	var line_y := keep_pos.y
	var line_x := keep_pos.x
	for i in range(1, route.size()):
		var a := route[i - 1]
		var b := route[i]
		var dx := absf(b.x - a.x)
		var dy := absf(b.y - a.y)
		if dx >= dy:
			if dx > best_h + 0.001:
				best_h = dx
				line_y = a.y
		elif dy > best_v + 0.001:
			best_v = dy
			line_x = a.x
	grid_origin = Vector2(line_x, line_y)

## نزدیک‌ترین بلوک ۲×۲ خانه روی شبکهٔ همین نقشه.
func grid_cell(pos: Vector2) -> Vector2:
	var step: float = Balance.GRID_STEP
	return Vector2(
		grid_origin.x + roundf((pos.x - grid_origin.x) / step) * step,
		grid_origin.y + roundf((pos.y - grid_origin.y) / step) * step
	)

## نقطهٔ نهایی کاشت: نقطهٔ انگشت به نزدیک‌ترین بلوک ۲×۲ گرد می‌شود، پس برج‌ها
## هیچ‌وقت با فاصلهٔ یک خانه کاشته نمی‌شوند و برج دوم دقیقاً زیر برج اول جا می‌شود.
## اگر خود بلوک ممنوع باشد ولی نقطهٔ انگشت روی زمین آزاد باشد، نزدیک‌ترین بلوک مجاز
## تا نیم‌برج آن‌طرف‌تر انتخاب می‌شود؛ داخل جاده یا داخل برج دیگر، جابه‌جایی نمی‌کنیم
## تا خطای واقعی همان‌جا نشان داده شود.
func snap_build_cell(pos: Vector2, occupied: Dictionary, tolerance := -1.0) -> Vector2:
	var step: float = Balance.GRID_STEP
	var cell := grid_cell(pos)
	if build_issue(cell, occupied).is_empty():
		return cell
	if not build_issue(pos, occupied).is_empty():
		return cell
	var limit := tolerance if tolerance > 0.0 else step * 1.5
	var best := cell
	var best_distance := 1.0e9
	for dy in range(-2, 3):
		for dx in range(-2, 3):
			if dx == 0 and dy == 0:
				continue
			var candidate := cell + Vector2(float(dx) * step, float(dy) * step)
			if not build_issue(candidate, occupied).is_empty():
				continue
			var distance := candidate.distance_to(pos)
			if distance < best_distance:
				best_distance = distance
				best = candidate
	if best_distance <= limit:
		return best
	return cell

# ------------------------------------------------------------------ ساخت‌وساز

## آیا می‌توان روی این نقطه برجک ساخت؟
## نزدیک‌ترین مانع طبیعی به این نقطه چقدر است؟
func _blocker_issue(pos: Vector2) -> bool:
	for blocker in blockers:
		if pos.distance_to(blocker["pos"]) < Balance.TOWER_RADIUS + float(blocker["radius"]):
			return true
	return false

## فهرست جایگاه‌های بتنی خالی که هنوز برجکی روی آن‌ها کاشته نشده است.
func available_pads(occupied: Dictionary) -> PackedVector2Array:
	var out := PackedVector2Array()
	for pad in pads:
		var taken := false
		for other in occupied.keys():
			if _tower_footprints_overlap(other as Vector2, pad):
				taken = true
				break
		if not taken:
			out.append(pad)
	return out

## نزدیک‌ترین جایگاه بتنی تاییدشده به نقطهٔ لمس‌شده را برمی‌گرداند.
func nearest_pad(pos: Vector2, occupied: Dictionary = {}, max_dist := 88.0) -> Vector2:
	if pads.is_empty():
		return pos
	var best := Vector2(-9999.0, -9999.0)
	var best_d := max_dist
	for pad in pads:
		var taken := false
		for other in occupied.keys():
			if _tower_footprints_overlap(other as Vector2, pad):
				taken = true
				break
		if taken:
			continue
		var d := pad.distance_to(pos)
		if d < best_d:
			best_d = d
			best = pad
	if best.x > -9000.0:
		return best
	# اگر جایگاه خالی در شعاع نبود، نزدیک‌ترین جایگاه کلی را برگردان تا دلیل خطا درست نمایش داده شود
	best_d = 999999.0
	for pad in pads:
		var d := pad.distance_to(pos)
		if d < best_d:
			best_d = d
			best = pad
	return best if best_d <= max_dist else pos

func _is_on_approved_pad(pos: Vector2) -> bool:
	if pads.is_empty():
		return true
	for pad in pads:
		if pad.distance_to(pos) <= 22.0:
			return true
	return false

func is_buildable(pos: Vector2, occupied: Dictionary) -> bool:
	return build_issue(pos, occupied).is_empty()

## برخورد دو حریم ۲×۲ خانه‌ای؛ تماس لبه‌ها مجاز است، هم‌پوشانی نیست.
static func _tower_footprints_overlap(a: Vector2, b: Vector2) -> bool:
	return absf(a.x - b.x) < Balance.TOWER_SIZE - 0.001 \
		and absf(a.y - b.y) < Balance.TOWER_SIZE - 0.001

## تقاطع پاره‌خط با مستطیل، شامل قطعه‌های مورب نقشه‌های احتمالی آینده.
static func _segment_intersects_rect(a: Vector2, b: Vector2, rect: Rect2) -> bool:
	var delta := b - a
	var enter := 0.0
	var leave := 1.0
	for axis in 2:
		if absf(delta[axis]) < 0.000001:
			if a[axis] < rect.position[axis] or a[axis] > rect.end[axis]:
				return false
			continue
		var t0 := (rect.position[axis] - a[axis]) / delta[axis]
		var t1 := (rect.end[axis] - a[axis]) / delta[axis]
		enter = maxf(enter, minf(t0, t1))
		leave = minf(leave, maxf(t0, t1))
		if enter > leave:
			return false
	return true

## تصویر حاشیه بازتاب لبه‌های همان نقشه است. حریم راه‌های بازتاب‌شده را هم
## بررسی می‌کنیم تا بازیکن در حاشیه روی راهِ دیده‌شده برج نکارد.
static func _mirrored_point(point: Vector2, mx: int, my: int) -> Vector2:
	var x: float = -point.x if mx < 0 else (2.0 * Balance.WORLD_SIZE.x - point.x if mx > 0 else point.x)
	var y: float = -point.y if my < 0 else (2.0 * Balance.WORLD_SIZE.y - point.y if my > 0 else point.y)
	return Vector2(x, y)

func _road_overlaps_footprint(pos: Vector2) -> bool:
	# The road art and route use the same two-house width on every map.
	# ROUTE_CLEARANCE is a centre-point exclusion, not the road half-width;
	# adding it to the square footprint would make dense maps unbuildable.
	var road_half: float = Balance.HOUSE_SIZE
	var half: float = Balance.TOWER_SIZE * 0.5
	var footprint := Rect2(pos - Vector2.ONE * half, Vector2.ONE * Balance.TOWER_SIZE)
	# کاهش ناچیز برای مجاز بودنِ تماس دقیق لبهٔ برج و لبهٔ جاده.
	var forbidden := footprint.grow(road_half - 0.001)
	for mx in range(-1, 2):
		for my in range(-1, 2):
			for i in range(1, route.size()):
				if _segment_intersects_rect(_mirrored_point(route[i - 1], mx, my),
						_mirrored_point(route[i], mx, my), forbidden):
					return true
	return false

## ساخت آزاد است، ولی تمام مربع چهارخانه‌ای باید در میدان یا حاشیه جا شود
## و هیچ قسمت آن روی جاده یا برج دیگر نباشد.
func build_issue(pos: Vector2, occupied: Dictionary) -> String:
	if not in_build_bounds(pos, Balance.TOWER_SIZE * 0.5):
		return "بیرون از میدان نبرد"
	if _road_overlaps_footprint(pos):
		return "داخل محدودهٔ جاده"
	for other in occupied.keys():
		if _tower_footprints_overlap(other as Vector2, pos):
			return "داخل محدودهٔ برج دیگر"
	if pos.distance_to(keep_pos) < Balance.KEEP_CLEARANCE:
		return "نزدیک دژ"
	return ""

## فاصلهٔ دوخانه‌ای دقیقاً هم‌اندازهٔ یک برج است؛ بدون کمک، انگشت باید روی
## یک خط بی‌ضخامت رها شود. فقط اگر نقطهٔ فعلی نامعتبر و نزدیک لبهٔ راه باشد،
## نزدیک‌ترین نقطهٔ واقعاً آزاد را پیشنهاد می‌کنیم؛ زمین آزاد همچنان آزاد است.
## حریم راه، دژ و برج‌های دیگر در مقصد دوباره به‌طور کامل بررسی می‌شود.
func snap_build_position(pos: Vector2, occupied: Dictionary, tolerance := 12.0) -> Vector2:
	var problem := build_issue(pos, occupied)
	if problem != "داخل محدودهٔ جاده" and problem != "داخل محدودهٔ برج دیگر":
		return pos
	var best := pos
	var best_distance := tolerance + 0.001
	for mx in range(-1, 2):
		for my in range(-1, 2):
			for i in range(1, route.size()):
				var a := _mirrored_point(route[i - 1], mx, my)
				var b := _mirrored_point(route[i], mx, my)
				var horizontal := is_equal_approx(a.y, b.y)
				var vertical := is_equal_approx(a.x, b.x)
				if not horizontal and not vertical:
					continue
				# A distant segment on the same horizontal/vertical line must not
				# attract the pointer from the other side of the map.
				if horizontal and (pos.x < minf(a.x, b.x) - Balance.TOWER_SIZE
						or pos.x > maxf(a.x, b.x) + Balance.TOWER_SIZE):
					continue
				if vertical and (pos.y < minf(a.y, b.y) - Balance.TOWER_SIZE
						or pos.y > maxf(a.y, b.y) + Balance.TOWER_SIZE):
					continue
				for side in [-1.0, 1.0]:
					var candidate := Vector2(pos.x, a.y + float(side) * Balance.TOWER_SIZE) if horizontal \
						else Vector2(a.x + float(side) * Balance.TOWER_SIZE, pos.y)
					var distance := candidate.distance_to(pos)
					if distance < best_distance and build_issue(candidate, occupied).is_empty():
						best = candidate
						best_distance = distance
	return best
