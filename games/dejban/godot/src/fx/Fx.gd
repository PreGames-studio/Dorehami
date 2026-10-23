## جلوه‌های بصری: انفجار، جرقه، حلقهٔ موج و لرزش صفحه.
## همهٔ توابع ایستا هستند و گره‌ها را زیر پدرِ داده‌شده می‌سازند.
##
## دو خانوادهٔ هنری این‌جا کنار هم‌اند:
##   ۱) فریم‌های قدیمی جدا-جدا (`fx/explosion_1…6`) که `frames_for` سرهم می‌کند.
##   ۲) هشت شیت افکت کارتونی افقی با فریم‌های ۶۴×۶۴ (tools/gen_fx_sheets.py) که
##      `sheet_frames` آن‌ها را با AtlasTexture برش می‌دهد: explosion_small/medium/large،
##      muzzle_flash، laser_beam، tesla_bolt، smoke_cloud و fire_flame.
class_name Fx
extends RefCounted

## اندازهٔ هر فریم در شیت‌های افقیِ افکت.
const FRAME_PX := 64
## تعداد فریم هر شیت افکت؛ همان ترتیبی که مولد در docstring خودش می‌نویسد.
const SHEETS := {
	"fx/explosion_small": {"frames": 6, "fps": 22.0},
	"fx/explosion_medium": {"frames": 8, "fps": 20.0},
	"fx/explosion_large": {"frames": 10, "fps": 18.0},
	"fx/muzzle_flash": {"frames": 4, "fps": 30.0},
	"fx/laser_beam": {"frames": 6, "fps": 18.0},
	"fx/tesla_bolt": {"frames": 6, "fps": 20.0},
	"fx/smoke_cloud": {"frames": 6, "fps": 14.0},
	"fx/fire_flame": {"frames": 6, "fps": 16.0},
}

static var _frames: Dictionary = {}
static var _sheets: Dictionary = {}

## فریم‌های یک شیت افقی را با AtlasTexture می‌شکند و یک SpriteFrames می‌سازد.
## نتیجه برای هر (کلید، تعداد فریم) یک‌بار ساخته و کش می‌شود.
static func sheet_frames(key: String, count := 0, fps := 0.0, loop := false) -> SpriteFrames:
	var info: Dictionary = SHEETS.get(key, {})
	var total := count if count > 0 else int(info.get("frames", 6))
	var speed := fps if fps > 0.0 else float(info.get("fps", 20.0))
	var memo := "%s#%d#%.2f#%s" % [key, total, speed, str(loop)]
	if _sheets.has(memo):
		return _sheets[memo]
	var texture := Assets.tex(key)
	if texture == null:
		return null
	var frames := SpriteFrames.new()
	frames.add_animation("play")
	frames.set_animation_speed("play", speed)
	frames.set_animation_loop("play", loop)
	var width := texture.get_width()
	# اگر عرض تصویر با تعداد فریم نخواند، تعداد واقعی را از عرض درمی‌آوریم تا
	# هیچ فریمی نصفه یا خالی نمایش داده نشود.
	if total * FRAME_PX != width:
		total = maxi(1, width / FRAME_PX)
	for i in total:
		var slice := AtlasTexture.new()
		slice.atlas = texture
		slice.region = Rect2(i * FRAME_PX, 0.0, FRAME_PX, FRAME_PX)
		frames.add_frame("play", slice)
	_sheets[memo] = frames
	return frames

## یک شیت افکت را یک‌بار زیر پدر می‌گذارد و پس از پایان آزادش می‌کند.
## `stretch` طول و ضخامت را برای پرتوها جدا می‌کند؛ برای افکت‌های نقطه‌ای همان اندازهٔ همسان است.
static func sheet_at(parent: Node, key: String, pos: Vector2, opts := {}) -> AnimatedSprite2D:
	if parent == null or not is_instance_valid(parent):
		return null
	var frames := sheet_frames(key)
	if frames == null:
		return null
	var sprite := AnimatedSprite2D.new()
	sprite.sprite_frames = frames
	sprite.animation = "play"
	sprite.position = pos
	sprite.rotation = float(opts.get("angle", 0.0))
	var size := float(opts.get("size", 1.0))
	var stretch: Vector2 = opts.get("stretch", Vector2.ONE)
	sprite.scale = stretch * size
	sprite.modulate = opts.get("tint", Color.WHITE)
	sprite.z_index = int(opts.get("z", 31))
	sprite.flip_h = bool(opts.get("flip_h", false))
	sprite.flip_v = bool(opts.get("flip_v", false))
	parent.add_child(sprite)
	sprite.play("play")
	var hold := float(opts.get("fade", 0.0))
	if hold <= 0.0:
		sprite.animation_finished.connect(sprite.queue_free)
	elif sprite.sprite_frames.get_frame_count("play") > 0:
		# فریم آخر کمی می‌ماند و محو می‌شود؛ برای دود و شعله.
		var speed := maxf(1.0, sprite.sprite_frames.get_animation_speed("play"))
		var total := float(sprite.sprite_frames.get_frame_count("play")) / speed
		var tween := sprite.create_tween()
		tween.tween_interval(maxf(0.0, total - hold))
		tween.tween_property(sprite, "modulate:a", 0.0, hold)
		tween.tween_callback(sprite.queue_free)
	return sprite

## شعلهٔ دهانهٔ سلاح، رو به هدف.
static func muzzle(parent: Node, pos: Vector2, angle := 0.0, size := 0.7) -> void:
	sheet_at(parent, "fx/muzzle_flash", pos, {"angle": angle, "size": size, "z": 33})

## دود پف‌دار؛ پس از پایان انیمیشن محو می‌شود.
static func smoke(parent: Node, pos: Vector2, size := 0.8, tint := Color.WHITE) -> void:
	sheet_at(parent, "fx/smoke_cloud", pos, {"size": size, "tint": tint, "z": 30, "fade": 0.25})

## شعلهٔ ایستا-نما (پس از پایان می‌ماند و محو می‌شود)؛ برای آتشِ زمین.
static func flame(parent: Node, pos: Vector2, size := 0.7) -> void:
	sheet_at(parent, "fx/fire_flame", pos, {"size": size, "z": 30, "fade": 0.3})

## انفجار نقطه‌ای با شیت مناسب اندازه (auto: کوچک/متوسط/بزرگ).
static func blast(parent: Node, pos: Vector2, size := 1.0, kind := "") -> void:
	var key := "fx/explosion_medium"
	match kind:
		"small":
			key = "fx/explosion_small"
		"large":
			key = "fx/explosion_large"
		"medium":
			key = "fx/explosion_medium"
		_:
			if size < 0.95:
				key = "fx/explosion_small"
			elif size < 1.45:
				key = "fx/explosion_medium"
			else:
				key = "fx/explosion_large"
	sheet_at(parent, key, pos, {"size": size, "z": 32})

## برخورد گلولهٔ سبک: انفجار کوچک به‌جای جرقهٔ تخت.
static func impact(parent: Node, pos: Vector2, size := 0.45, add_spark := true) -> void:
	blast(parent, pos, size, "small")
	if add_spark:
		burst(parent, "fx/spark", pos, size * 1.1, 0.18, 1.4)

## محور ذاتی هر شیت کشیده‌شدنی: پرتو لیزر افقی است و صاعقهٔ تسلا عمودی.
const SHEET_AXIS := {
	"fx/laser_beam": "x",
	"fx/tesla_bolt": "y",
}

## پرتو کش‌آمده بین دو نقطه (لیزر آبی یا صاعقهٔ بنفش)؛ طول از فاصله گرفته می‌شود.
## فریم‌ها وسط‌چین‌اند، پس مرکزِ پاره‌خط وسط دو نقطه می‌نشیند و فقط محورِ طول
## به اندازهٔ فاصله کش می‌آید؛ ضخامت هم همان `width` می‌ماند.
static func beam(parent: Node, key: String, from: Vector2, to: Vector2, width := 1.0, tint := Color.WHITE) -> void:
	var delta := to - from
	var length := delta.length()
	if length <= 1.0:
		return
	var strength := length / float(FRAME_PX)
	var vertical := String(SHEET_AXIS.get(key, "x")) == "y"
	var stretch: Vector2 = Vector2(width, strength) if vertical else Vector2(strength, width)
	sheet_at(parent, key, from + delta * 0.5, {
		"angle": delta.angle() + (PI * 0.5 if vertical else 0.0),
		"stretch": stretch,
		"z": 31,
		"tint": tint,
	})

## صاعقهٔ زنجیره‌ای بین دو دشمن.
static func bolt(parent: Node, from: Vector2, to: Vector2, width := 1.0) -> void:
	beam(parent, "fx/tesla_bolt", from, to, width)

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
