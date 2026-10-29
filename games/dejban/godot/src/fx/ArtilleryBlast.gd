## A bounded, short-lived local blast. The cached explosion sheet provides the
## animated fire; one node draws a fading light, smoke, six sparks and a subtle
## visual jitter. It never shakes the battlefield or the tower's fixed base.
class_name ArtilleryBlast
extends Node2D

const DURATION := 0.58
const PARTICLES := 6
var elapsed := 0.0
var blast_radius := 42.0
var level := 1
var flash: AnimatedSprite2D = null

func setup(tower_level: int, radius: float) -> void:
	level = clampi(tower_level, 1, 5)
	blast_radius = maxf(radius, 0.0)
	z_index = 32
	var key := "fx/explosion_large" if level >= 4 else "fx/explosion_medium"
	flash = Fx.sheet_at(self, key, Vector2.ZERO, {
		"size": 0.62 + 0.08 * float(level - 1), "z": 0,
	})
	queue_redraw()

func _process(delta: float) -> void:
	elapsed += maxf(delta, 0.0)
	if elapsed >= DURATION:
		queue_free()
		return
	# A sub-two-pixel jolt belongs to the explosion sprite, not the world.
	if is_instance_valid(flash):
		var shake := maxf(0.0, 1.0 - elapsed / 0.12) * (0.9 + 0.18 * float(level - 1))
		flash.position = Vector2(sin(elapsed * 67.0), cos(elapsed * 83.0)) * shake
	queue_redraw()

func _draw() -> void:
	var t := clampf(elapsed / DURATION, 0.0, 1.0)
	var light := pow(1.0 - t, 4.0) * 0.36
	draw_circle(Vector2.ZERO, 8.0 + float(level) * 2.0, Color(1.0, 0.62, 0.16, light))
	var ring_alpha := maxf(0.0, 1.0 - t * 2.6) * 0.45
	if ring_alpha > 0.0:
		draw_arc(Vector2.ZERO, minf(blast_radius, 62.0) * (0.25 + 0.75 * t * 2.0),
			0.0, TAU, 32, Color(1.0, 0.75, 0.32, ring_alpha), 1.5, true)
	# Smoke is translucent, small and short lived to avoid blocking the road.
	var smoke_alpha := sin(t * PI) * 0.22
	for i in 3:
		var pos := Vector2(float(i - 1) * (5.0 + t * 5.0), -3.0 - t * (10.0 + float(i) * 2.0))
		draw_circle(pos, 3.5 + t * 4.0, Color(0.38, 0.39, 0.40, smoke_alpha))
	# Fixed particle count and no particle nodes keep crowded waves inexpensive.
	var spark_alpha := maxf(0.0, 1.0 - t * 2.1)
	for i in PARTICLES:
		var angle := TAU * (float(i) / float(PARTICLES)) + 0.35
		var direction := Vector2.from_angle(angle)
		var distance := (8.0 + float(level)) * (0.4 + t * 2.0)
		var start := direction * distance + Vector2(0.0, -t * 4.0)
		draw_line(start, start + direction * (3.0 + float(level) * 0.4),
			Color(1.0, 0.72, 0.25, spark_alpha), 1.5, true)
