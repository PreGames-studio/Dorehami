## One ephemeral, blue-white chain-lightning effect for one direct Tesla shot.
## Damage is applied by Tower synchronously; this node is visual only and draws
## at most five segments, progressively revealing the same hit points in order.
class_name TeslaArc
extends Node2D

## Suppresses the generic orange blast for deaths caused by this electric hit.
const DEATH_FX_CONTEXT := "_tesla_electric_death"
const HOP_DELAY := 0.035
const REVEAL_TIME := 0.045
const HOLD_TIME := 0.17
const FADE_TIME := 0.14

var points := PackedVector2Array()
var level := 1
var elapsed := 0.0
var lifetime := 0.0

static func stroke_width(value: int) -> float:
	return 1.8 + 0.65 * float(clampi(value, 1, 5) - 1)

static func brightness(value: int) -> float:
	return 0.6 + 0.1 * float(clampi(value, 1, 5) - 1)

func setup(global_points: PackedVector2Array, firing_level: int) -> void:
	level = clampi(firing_level, 1, 5)
	points.clear()
	for point in global_points:
		points.append(to_local(point))
	var segments := maxi(0, points.size() - 1)
	lifetime = maxf(0.0, float(segments - 1) * HOP_DELAY) + REVEAL_TIME + HOLD_TIME + FADE_TIME
	z_index = 2
	queue_redraw()

func _process(delta: float) -> void:
	elapsed += maxf(delta, 0.0)
	if elapsed >= lifetime:
		queue_free()
		return
	queue_redraw()

## Progress of each sequential segment, exposed for deterministic animation tests.
func segment_reveal(index: int) -> float:
	return clampf((elapsed - float(index) * HOP_DELAY) / REVEAL_TIME, 0.0, 1.0)

func _draw() -> void:
	var fade := clampf((lifetime - elapsed) / FADE_TIME, 0.0, 1.0)
	var tick := floori(elapsed * 30.0)
	for segment in range(points.size() - 1):
		var reveal := segment_reveal(segment)
		if reveal <= 0.0:
			continue
		var start: Vector2 = points[segment]
		var finish: Vector2 = start.lerp(points[segment + 1], reveal)
		var vector := finish - start
		if vector.length_squared() < 0.0001:
			continue
		var normal := vector.normalized().orthogonal()
		var jitter := minf(9.0 + float(level), vector.length() * 0.15)
		var bolt := PackedVector2Array()
		bolt.append(start)
		for bend in range(1, 7):
			var t := float(bend) / 7.0
			var wobble := sin(float(segment * 23 + bend * 17 + tick * 11)) * jitter
			bolt.append(start.lerp(finish, t) + normal * wobble)
		bolt.append(finish)
		var width := stroke_width(level)
		var light := brightness(level)
		draw_polyline(bolt, Color(0.02, 0.37, 1.0, (0.08 + 0.12 * light) * fade), width + 7.0, true)
		draw_polyline(bolt, Color(0.12, 0.75, 1.0, (0.50 + 0.35 * light) * fade), width + 2.0, true)
		draw_polyline(bolt, Color(0.92, 1.0, 1.0, (0.72 + 0.28 * light) * fade), 0.9 + float(level) * 0.22, true)
		if level >= 3 and reveal >= 0.95:
			var branch_start: Vector2 = bolt[3]
			var branch_end := branch_start + normal * (7.0 + float(level) * 1.5) + vector.normalized() * 8.0
			draw_line(branch_start, branch_end, Color(0.32, 0.88, 1.0, 0.65 * fade), width * 0.55, true)
		if reveal >= 0.99:
			draw_circle(finish, 2.5 + float(level) * 0.45, Color(0.45, 0.88, 1.0, 0.75 * fade))
