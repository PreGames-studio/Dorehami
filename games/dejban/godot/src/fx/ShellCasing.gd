## A small, short-lived cartridge ejected independently from the stationary base.
class_name ShellCasing
extends Node2D

const DURATION := 0.42
var velocity := Vector2.ZERO
var spin := 0.0
var age := 0.0

func launch(forward: Vector2, sequence: int) -> void:
	var side := 1.0 if sequence % 2 == 0 else -1.0
	velocity = (forward.rotated(side * PI * 0.5) - forward * 0.3).normalized() * (55.0 + 4.0 * (sequence % 3))
	spin = side * 12.0
	rotation = forward.angle()
	z_index = 32

func _process(delta: float) -> void:
	age += delta
	global_position += velocity * delta
	velocity.y += 90.0 * delta
	rotation += spin * delta
	modulate.a = clampf((DURATION - age) / 0.12, 0.0, 1.0)
	if age >= DURATION:
		queue_free()

func _draw() -> void:
	draw_rect(Rect2(-3.0, -1.6, 6.0, 3.2), Color(0.29, 0.20, 0.11))
	draw_rect(Rect2(-2.4, -1.2, 4.8, 2.3), Color(0.94, 0.66, 0.23))
	draw_line(Vector2(-1.9, -0.8), Vector2(1.8, -0.8), Color(1.0, 0.88, 0.48), 0.7)
