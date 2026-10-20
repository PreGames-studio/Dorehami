## دژ: هدف نهایی دشمنان. هر دشمنِ رسیده یک جان (یا برای سردار پنج جان) می‌گیرد.
class_name Keep
extends Node2D

signal damaged(lives_after)
signal destroyed

const BAR_WIDTH := 150.0
const BAR_HEIGHT := 8.0

var lives := 20
var max_lives := 20

var _sprite: Sprite2D = null
var _flash := 0.0

func setup(start_lives: int) -> void:
	lives = start_lives
	max_lives = start_lives
	_sprite = Sprite2D.new()
	_sprite.texture = Assets.tex("structures/keep")
	_sprite.centered = false
	if _sprite.texture != null:
		_sprite.offset = Vector2(-_sprite.texture.get_width() * 0.5, -_sprite.texture.get_height() * 0.88)
	add_child(_sprite)
	queue_redraw()

func take_damage(amount := 1) -> void:
	lives = maxi(0, lives - amount)
	_flash = 1.0
	damaged.emit(lives)
	queue_redraw()
	if lives <= 0:
		destroyed.emit()

func _process(delta: float) -> void:
	if _flash <= 0.0:
		return
	_flash = maxf(0.0, _flash - delta * 3.0)
	if _sprite != null:
		var hurt := 1.0 - clampf(float(lives) / maxf(1.0, float(max_lives)), 0.0, 1.0)
		var tint := Color.WHITE.lerp(Color(0.72, 0.66, 0.66), hurt * 0.8)
		_sprite.modulate = tint.lerp(Color(2.0, 0.9, 0.8), _flash * 0.8)
	queue_redraw()

func _draw() -> void:
	var ratio := clampf(float(lives) / maxf(1.0, float(max_lives)), 0.0, 1.0)
	var origin := Vector2(-BAR_WIDTH * 0.5, 12.0)
	draw_rect(Rect2(origin + Vector2(-2, -2), Vector2(BAR_WIDTH + 4, BAR_HEIGHT + 4)), Color(0.02, 0.05, 0.06, 0.75))
	var color := Style.good
	if ratio < 0.3:
		color = Style.bad
	elif ratio < 0.6:
		color = Style.gold
	draw_rect(Rect2(origin, Vector2(BAR_WIDTH * ratio, BAR_HEIGHT)), color)
