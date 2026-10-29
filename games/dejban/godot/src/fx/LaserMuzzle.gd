## Reusable blue-white lens light and firing flash, independent of the beam.
class_name LaserMuzzle
extends Node2D

var beam_width := 3.0
var level := 1
var progress := 0.0
var glow: Texture2D = null
var flash: Texture2D = null

func configure(width: float, firing_level: int) -> void:
	beam_width = maxf(0.5, width)
	level = clampi(firing_level, 1, 5)
	glow = Assets.tex("towers_laser/muzzle_glow")
	flash = Assets.tex("towers_laser/muzzle_flash")
	z_index = 2
	queue_redraw()

func update_progress(value: float) -> void:
	progress = clampf(value, 0.0, 1.0)
	queue_redraw()

func _draw() -> void:
	var fade := minf(1.0, (1.0 - progress) * 8.0)
	var pulse := 0.87 + 0.13 * sin(progress * 33.0)
	var radius := (7.0 + beam_width * 1.4) * pulse
	if glow != null:
		draw_texture_rect(glow, Rect2(Vector2(-radius, -radius), Vector2.ONE * radius * 2.0), false,
			Color(1.0, 1.0, 1.0, fade))
	if flash != null:
		var flash_radius := radius * (1.05 + 0.08 * float(level - 1))
		draw_texture_rect(flash, Rect2(Vector2(-flash_radius, -flash_radius), Vector2.ONE * flash_radius * 2.0),
			false, Color(1.0, 1.0, 1.0, fade * 0.9))
