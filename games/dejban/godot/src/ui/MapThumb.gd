## تصویر بندانگشتی نقشه: زمین، مسیر، دژ و ورودی را کوچک‌شده می‌کشد.
## نقشهٔ ساخته‌شده برای هر شماره یک‌بار نگه داشته می‌شود.
class_name MapThumb
extends Control

const GROUND_COLORS := {
	"ground/grass": Color("#4b7c4d"),
	"ground/grass_dark": Color("#3a673c"),
	"ground/dirt": Color("#7d6040"),
	"ground/sand": Color("#c9aa72"),
	"ground/snow": Color("#dbe6ea"),
	"ground/rock": Color("#6d747e"),
	"ground/ash": Color("#5b4d42"),
}

static var _cache: Dictionary = {}

var map: GameMap = null
var _decor: Array = []

func set_map(index: int) -> void:
	if _cache.has(index):
		map = _cache[index]
	else:
		map = GameMap.build(index)
		_cache[index] = map
	_decor = map.decor
	clip_contents = true
	queue_redraw()

func _to_thumb(point: Vector2) -> Vector2:
	var scale_x := size.x / Balance.WORLD_SIZE.x
	var scale_y := size.y / Balance.TERRAIN_RECT.size.y
	return Vector2(point.x * scale_x, (point.y - Balance.TERRAIN_TOP) * scale_y)

func _draw() -> void:
	draw_rect(Rect2(Vector2.ZERO, size), Color("#0b1c20"))
	if map == null:
		return
	if not map.bg_key.is_empty() and Assets.has_key(map.bg_key):
		var bg := Assets.tex(map.bg_key)
		if bg != null:
			draw_texture_rect(bg, Rect2(Vector2.ZERO, size), false)
			var keep_pt := _to_thumb(map.keep_pos)
			draw_circle(keep_pt, 5.5, Color("#dcae4b"))
			draw_circle(keep_pt, 2.5, Color("#fff0c2"))
			draw_circle(_to_thumb(map.entry_pos), 4.5, Color("#b4402f"))
			return
	var tile := Vector2(size.x / float(Balance.COLS), size.y / float(Balance.ROWS))
	for y in Balance.ROWS:
		for x in Balance.COLS:
			var key := String(map.ground_tiles.get(Vector2i(x, y), ""))
			var color: Color = GROUND_COLORS.get(key, Color("#4b7c4d"))
			draw_rect(Rect2(Vector2(x, y) * tile, tile + Vector2(0.6, 0.6)), color)

	for item in _decor:
		if not bool(item.get("blocking", false)):
			continue
		var point := _to_thumb(item.get("pos", Vector2.ZERO))
		if point.x >= 0.0 and point.y >= 0.0:
			draw_circle(point, 2.0, Color(0.09, 0.15, 0.15, 0.8))

	var points := PackedVector2Array()
	for point in map.route:
		var small := _to_thumb(point)
		points.append(Vector2(clampf(small.x, 0.0, size.x), clampf(small.y, 0.0, size.y)))
	if points.size() >= 2:
		draw_polyline(points, Color("#2b2117"), 7.0, true)
		draw_polyline(points, Color("#6b5638"), 4.5, true)

	var keep := _to_thumb(map.keep_pos)
	draw_circle(keep, 5.5, Color("#dcae4b"))
	draw_circle(keep, 2.5, Color("#fff0c2"))
	draw_circle(_to_thumb(map.entry_pos), 4.5, Color("#b4402f"))
	draw_rect(Rect2(Vector2.ZERO, size), Color(0, 0, 0, 0), false, 1.0)
