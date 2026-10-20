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
	# تنها نقشهٔ نخست گوشهٔ کاملاً شکسته دارد؛ دیگر نقشه‌ها همچنان نرم می‌مانند.
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
			if (other as Vector2).distance_to(pad) < 26.0:
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
			if (other as Vector2).distance_to(pad) < 26.0:
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

## بازیکن می‌تواند برجک را در هر جای نقشه رها کند؛ فقط داخل محدودهٔ جاده
## و داخل محدودهٔ برجک‌های دیگر مجاز نیست.
func build_issue(pos: Vector2, occupied: Dictionary) -> String:
	if not in_terrain(pos, 18.0):
		return "بیرون از میدان نبرد"
	if distance_to_route(pos) < Balance.ROUTE_CLEARANCE:
		return "داخل محدودهٔ جاده"
	for other in occupied.keys():
		if (other as Vector2).distance_to(pos) < Balance.TOWER_MIN_GAP:
			return "داخل محدودهٔ برج دیگر"
	if pos.distance_to(keep_pos) < Balance.KEEP_CLEARANCE:
		return "نزدیک دژ"
	return ""
