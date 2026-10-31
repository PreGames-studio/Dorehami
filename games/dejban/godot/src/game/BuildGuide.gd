## راهنمای محدوده‌های ممنوعه هنگام کشیدن برجک:
## حریم برجک‌های کاشته‌شده را با مربع قرمز ملایم نشان می‌دهد تا بازیکن ببیند
## کجا حریم برج دیگر است.
class_name BuildGuide
extends Node2D

var spots: PackedVector2Array = PackedVector2Array()
## شبکهٔ کاشت ۲×۲ که هنگام کشیدن برجک دیده می‌شود.
var show_grid := false
var grid_origin := Vector2.ZERO
var _pulse := 0.0

func show_spots(list: PackedVector2Array) -> void:
	spots = list
	queue_redraw()

## خطوط شبکهٔ کاشت را با مبدأ همین نقشه نشان می‌دهد تا بازیکن بلوک‌های ۲×۲ را ببیند.
func show_lattice(origin: Vector2) -> void:
	grid_origin = origin
	show_grid = true
	queue_redraw()

func clear() -> void:
	if spots.is_empty() and not show_grid:
		return
	spots = PackedVector2Array()
	show_grid = false
	queue_redraw()

func _process(delta: float) -> void:
	if spots.is_empty() and not show_grid:
		return
	_pulse += delta * 3.6
	queue_redraw()

func _draw() -> void:
	if spots.is_empty() and not show_grid:
		return
	var wave := 0.5 + 0.5 * sin(_pulse)
	if show_grid:
		_draw_lattice(wave)
	var half: float = Balance.TOWER_SIZE * 0.5
	var fill_col := Color(1.0, 0.25, 0.25, 0.10 + wave * 0.06)
	var line_col := Color(1.0, 0.35, 0.35, 0.45 + wave * 0.20)
	for spot in spots:
		var footprint := Rect2(spot - Vector2.ONE * half, Vector2.ONE * Balance.TOWER_SIZE)
		draw_rect(footprint, fill_col, true)
		draw_rect(footprint, line_col, false, 1.8, true)


## خطوط نازک شبکهٔ ۲×۲ خانه؛ هر خط دقیقاً روی مرز بلوک‌ها است، پس برج‌ها همیشه
## داخل همان چهارخانه می‌نشینند.
func _draw_lattice(wave: float) -> void:
	var step: float = Balance.GRID_STEP
	var rect: Rect2 = Balance.BUILD_RECT
	var col := Color(0.85, 0.95, 1.0, 0.10 + wave * 0.05)
	var x := grid_origin.x + roundf((rect.position.x - grid_origin.x) / step) * step
	while x <= rect.end.x:
		draw_line(Vector2(x, rect.position.y), Vector2(x, rect.end.y), col, 1.0, true)
		x += step
	var y := grid_origin.y + roundf((rect.position.y - grid_origin.y) / step) * step
	while y <= rect.end.y:
		draw_line(Vector2(rect.position.x, y), Vector2(rect.end.x, y), col, 1.0, true)
		y += step
