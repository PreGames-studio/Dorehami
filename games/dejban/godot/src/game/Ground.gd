## زمین میدان: کاشی‌های زمین، مسیر دشمن و دکور نقشه.
## همه‌چیز یک‌بار کشیده می‌شود (نقشهٔ ایستا) تا هر فریم هزینه‌ای نداشته باشد.
class_name Ground
extends Node2D

var map: GameMap = null
var _decor: Array = []
var _route_steps := PackedVector2Array()

func setup(game_map: GameMap) -> void:
	map = game_map
	_decor = map.decor.duplicate()
	_decor.sort_custom(func(a, b): return float((a["pos"] as Vector2).y) < float((b["pos"] as Vector2).y))
	_build_route_steps()
	queue_redraw()

## بخشی از مسیر که داخل میدان است را جدا می‌کند تا بیرون میدان کشیده نشود.
func _build_route_steps() -> void:
	_route_steps = PackedVector2Array()
	if map == null:
		return
	var length := map.total_length
	var steps := maxi(2, int(length / 14.0))
	for i in range(steps + 1):
		var point := map.point_at(length * float(i) / float(steps))
		if GameMap.in_terrain(point, 0.0):
			_route_steps.append(point)

func _draw() -> void:
	if map == null:
		return
	if not map.bg_key.is_empty() and Assets.has_key(map.bg_key):
		var bg := Assets.tex(map.bg_key)
		if bg != null:
			var vp_size := get_viewport_rect().size
			var target_size := Vector2(
				maxf(Balance.WORLD_SIZE.x, vp_size.x),
				maxf(Balance.WORLD_SIZE.y, vp_size.y)
			)
			draw_texture_rect(bg, Rect2(Vector2.ZERO, target_size), false)
			return
	_draw_tiles()
	_draw_route()
	_draw_decor()

func _draw_tiles() -> void:
	for y in Balance.ROWS:
		for x in Balance.COLS:
			var key: String = String(map.ground_tiles.get(Vector2i(x, y), String(map.biome["ground"])))
			var texture := Assets.tex(key)
			if texture == null:
				continue
			var origin := Vector2(x * Balance.TILE, Balance.TERRAIN_TOP + y * Balance.TILE)
			draw_texture_rect(texture, Rect2(origin, Vector2(Balance.TILE, Balance.TILE)), false)

func _draw_route() -> void:
	if map.route.size() < 2:
		return
	# بستر خاکی راه با چند لایه تا لبه‌هایش نرم دیده شود
	draw_polyline(map.route, Color(0.07, 0.11, 0.11, 0.9), 56.0, true)
	draw_polyline(map.route, Color(0.33, 0.27, 0.2, 1.0), 48.0, true)
	draw_polyline(map.route, Color(0.44, 0.36, 0.26, 1.0), 40.0, true)
	draw_polyline(map.route, Color(0.52, 0.43, 0.31, 0.9), 26.0, true)
	if _route_steps.size() > 1:
		var dash_count := maxi(1, int(map.total_length / 70.0))
		for i in dash_count:
			var point := map.point_at((i + 0.5) * (map.total_length / float(dash_count)))
			if GameMap.in_terrain(point, 12.0):
				draw_circle(point, 2.6, Color(0.88, 0.82, 0.6, 0.45))

func _draw_decor() -> void:
	for item in _decor:
		var key := String(item.get("key", ""))
		var texture := Assets.tex(key)
		if texture == null:
			continue
		var pos: Vector2 = item.get("pos", Vector2.ZERO)
		var scale_value := float(item.get("scale", 1.0))
		var flip := bool(item.get("flip", false))
		var rot := float(item.get("rot", 0.0))
		var width := float(texture.get_width())
		var height := float(texture.get_height())
		draw_set_transform(pos, rot, Vector2(-scale_value if flip else scale_value, scale_value))
		draw_texture_rect(texture, Rect2(-width * 0.5, -height, width, height), false)
		draw_set_transform(Vector2.ZERO, 0.0, Vector2.ONE)
