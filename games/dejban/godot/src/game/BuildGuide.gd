## راهنمای محدوده‌های ممنوعه هنگام کشیدن برجک:
## حریم برجک‌های کاشته‌شده را با حلقهٔ قرمز ملایم نشان می‌دهد تا بازیکن ببیند
## کجا حریم برج دیگر است.
class_name BuildGuide
extends Node2D

var spots: PackedVector2Array = PackedVector2Array()
var _pulse := 0.0

func show_spots(list: PackedVector2Array) -> void:
	spots = list
	queue_redraw()

func clear() -> void:
	if spots.is_empty():
		return
	spots = PackedVector2Array()
	queue_redraw()

func _process(delta: float) -> void:
	if spots.is_empty():
		return
	_pulse += delta * 3.6
	queue_redraw()

func _draw() -> void:
	if spots.is_empty():
		return
	var wave := 0.5 + 0.5 * sin(_pulse)
	var radius := Balance.TOWER_MIN_GAP
	var fill_col := Color(1.0, 0.25, 0.25, 0.10 + wave * 0.06)
	var line_col := Color(1.0, 0.35, 0.35, 0.45 + wave * 0.20)
	for spot in spots:
		draw_circle(spot, radius, fill_col)
		draw_arc(spot, radius, 0.0, TAU, 48, line_col, 1.8, true)
