## Cheap, animated electricity around the Tesla head; no physics or projectiles.
class_name TeslaCharge
extends Node2D

const DEPTH_SQUASH := 0.68

var level := 1
var radius := 15.0
var arm_span := 39.0
var yaw := 0.0
var width_factor := 1.0
var phase := 0.0
var flash := 0.0

func configure(value: int, core_radius: float, span: float) -> void:
	level = clampi(value, 1, 5)
	radius = core_radius
	arm_span = span
	queue_redraw()

## Bright rotating emission point is the same projected point the real shot uses.
func emitter_offset() -> Vector2:
	return Vector2(cos(yaw), sin(yaw) * DEPTH_SQUASH) * radius * 0.86

func _draw() -> void:
	var pulse := 0.5 + 0.5 * sin(phase * 4.4)
	var alpha := 0.07 + 0.012 * float(level) + 0.07 * pulse + 0.12 * flash
	draw_circle(Vector2.ZERO, radius + 6.0 + float(level), Color(0.05, 0.58, 1.0, alpha))
	draw_arc(Vector2.ZERO, radius + 2.5, 0.0, TAU, 24,
		Color(0.22, 0.78, 1.0, 0.26 + 0.25 * pulse + 0.35 * flash), 1.4 + 0.24 * level, true)
	var tip := emitter_offset()
	draw_circle(tip, 3.2 + 0.30 * float(level) + flash * 1.8, Color(0.18, 0.75, 1.0, 0.40 + 0.30 * flash))
	draw_circle(tip, 1.5 + 0.15 * float(level), Color(0.90, 1.0, 1.0, 0.92))
	var tick := floori(phase * 14.0)
	for side in [-1.0, 1.0]:
		var start := Vector2(side * radius * 0.80, -1.0)
		var end := Vector2(side * arm_span * width_factor, sin(yaw) * 2.2)
		var mid := start.lerp(end, 0.5) + Vector2(0.0, sin(float(tick) * 2.7 + side) * (2.0 + level * 0.3))
		var points := PackedVector2Array([start, start.lerp(mid, 0.55) + Vector2(0.0, -2.0), mid,
			mid.lerp(end, 0.55) + Vector2(0.0, 2.0), end])
		var width := 0.9 + float(level) * 0.34 + flash * 1.3
		draw_polyline(points, Color(0.05, 0.50, 1.0, 0.20 + 0.20 * flash), width + 4.0, true)
		draw_polyline(points, Color(0.23, 0.84, 1.0, 0.75 + 0.22 * flash), width + 1.1, true)
		draw_polyline(points, Color(0.91, 1.0, 1.0, 0.95), maxf(0.6, width * 0.48), true)
		if level >= 3:
			var fork := mid + Vector2(side * 4.0, -4.0 - float(level))
			draw_line(mid, fork, Color(0.40, 0.89, 1.0, 0.9), width * 0.55, true)
