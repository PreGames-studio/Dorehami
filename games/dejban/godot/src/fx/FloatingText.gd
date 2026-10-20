## متن شناوری که بالا می‌رود و محو می‌شود: پاداش سکه، آسیب، هشدار.
class_name FloatingText
extends Node2D

var content := ""
var color := Color.WHITE
var font_size_value := 20
var rise := 52.0
var life := 0.9
var outline := Color(0.03, 0.06, 0.07, 0.9)

var _age := 0.0
var _drift := 0.0

func _ready() -> void:
	z_index = 34
	_drift = randf_range(-14.0, 14.0)

func _process(delta: float) -> void:
	_age += delta
	position += Vector2(_drift * delta, -rise * delta)
	modulate.a = clampf(1.0 - _age / life, 0.0, 1.0)
	queue_redraw()
	if _age >= life:
		queue_free()

func _draw() -> void:
	var font: Font = Style.font_bold
	if font == null:
		font = ThemeDB.fallback_font
	var text := content
	var width := font.get_string_size(text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size_value).x
	var origin := Vector2(-width * 0.5, 0.0)
	# سایهٔ دور متن تا روی هر زمینی خوانا بماند
	for offset in [Vector2(-1, 0), Vector2(1, 0), Vector2(0, -1), Vector2(0, 1), Vector2(1, 1), Vector2(-1, -1)]:
		draw_string(font, origin + offset, text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size_value, outline)
	draw_string(font, origin, text, HORIZONTAL_ALIGNMENT_LEFT, -1, font_size_value, color)
