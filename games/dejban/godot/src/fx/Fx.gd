## جلوه‌های بصری: انفجار، جرقه، حلقهٔ موج و لرزش صفحه.
## همهٔ توابع ایستا هستند و گره‌ها را زیر پدرِ داده‌شده می‌سازند.
class_name Fx
extends RefCounted

static var _frames: Dictionary = {}

## فریم‌های انیمیشن هر جلوه را یک‌بار می‌سازد.
static func frames_for(kind: String) -> SpriteFrames:
	if _frames.has(kind):
		return _frames[kind]
	var count := 6
	if kind == "smoke":
		count = 3
	elif kind == "flame":
		count = 4
	var frames := SpriteFrames.new()
	frames.add_animation(kind)
	frames.set_animation_speed(kind, 20.0)
	frames.set_animation_loop(kind, false)
	for i in count:
		var texture := Assets.tex("fx/%s_%d" % [kind, i + 1])
		if texture != null:
			frames.add_frame(kind, texture)
	_frames[kind] = frames
	return frames

## انفجار انیمیشنی یک‌باره.
static func explosion(parent: Node, pos: Vector2, size := 1.0, kind := "explosion") -> void:
	if parent == null or not is_instance_valid(parent):
		return
	var sprite := AnimatedSprite2D.new()
	sprite.sprite_frames = frames_for(kind)
	sprite.position = pos
	sprite.scale = Vector2.ONE * size
	sprite.z_index = 30
	sprite.animation = kind
	parent.add_child(sprite)
	sprite.play(kind)
	sprite.animation_finished.connect(sprite.queue_free)

## جلوهٔ ساده که کمی بزرگ می‌شود و محو می‌گردد (جرقه، سکه، ستاره، حلقه…).
static func burst(parent: Node, key: String, pos: Vector2, size := 1.0, duration := 0.35,
		grow := 1.6, tint := Color.WHITE, fade := true) -> void:
	if parent == null or not is_instance_valid(parent):
		return
	var texture := Assets.tex(key)
	if texture == null:
		return
	var sprite := Sprite2D.new()
	sprite.texture = texture
	sprite.position = pos
	sprite.scale = Vector2.ONE * size
	sprite.modulate = tint
	sprite.z_index = 32
	parent.add_child(sprite)
	var tween := sprite.create_tween()
	tween.set_parallel(true)
	tween.tween_property(sprite, "scale", Vector2.ONE * size * grow, duration).set_trans(Tween.TRANS_SINE)
	if fade:
		tween.tween_property(sprite, "modulate:a", 0.0, duration).set_trans(Tween.TRANS_SINE)
	tween.chain().tween_callback(sprite.queue_free)

## حلقهٔ گشادشونده؛ برای آسیب ناحیه‌ای و شفا.
static func ring(parent: Node, pos: Vector2, radius: float, tint := Color.WHITE, duration := 0.4) -> void:
	if parent == null or not is_instance_valid(parent):
		return
	var texture := Assets.tex("fx/ring")
	if texture == null:
		return
	var sprite := Sprite2D.new()
	sprite.texture = texture
	sprite.position = pos
	sprite.modulate = tint
	sprite.z_index = 29
	var base := float(texture.get_width())
	sprite.scale = Vector2.ONE * (radius * 2.0 / base) * 0.4
	parent.add_child(sprite)
	var tween := sprite.create_tween()
	tween.set_parallel(true)
	tween.tween_property(sprite, "scale", Vector2.ONE * (radius * 2.0 / base), duration).set_trans(Tween.TRANS_CUBIC).set_ease(Tween.EASE_OUT)
	tween.tween_property(sprite, "modulate:a", 0.0, duration)
	tween.chain().tween_callback(sprite.queue_free)

## متن شناور (آسیب، سکه، هشدار). متن باید از پیش با رقم‌های فارسی آماده شده باشد.
static func text(parent: Node, pos: Vector2, content: String, color := Color.WHITE, size := 20) -> void:
	if parent == null or not is_instance_valid(parent):
		return
	var node := FloatingText.new()
	node.position = pos
	node.content = content
	node.color = color
	node.font_size_value = size
	parent.add_child(node)

## لرزش صفحه با تویین؛ موقعیت اصلی گره حفظ می‌شود.
static func shake(node: Node2D, amount := 6.0, duration := 0.3) -> void:
	if node == null or not is_instance_valid(node):
		return
	var key := "_shake_base"
	if not node.has_meta(key):
		node.set_meta(key, node.position)
	var base: Vector2 = node.get_meta(key)
	var tween := node.create_tween()
	var steps := 7
	for i in steps:
		var k := 1.0 - float(i) / float(steps)
		var offset := Vector2(randf_range(-1.0, 1.0), randf_range(-1.0, 1.0)) * amount * k
		tween.tween_property(node, "position", base + offset, duration / float(steps + 1))
	tween.tween_property(node, "position", base, 0.06)
