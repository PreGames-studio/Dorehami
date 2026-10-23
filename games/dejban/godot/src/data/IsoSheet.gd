## بارگذار مشترک شیت‌های شبکه‌ای ایزومتریک (پک‌های towers_iso و units_iso_tank).
##
## هر شیت شبکه‌ای از فریم‌های ۱۲۸×۱۲۸ است: ردیف = جهت (۰..۷) و ستون = فریم.
## جهت‌ها روی صفحه ساعت‌گرد از شرق‌اند:
##   ۰=E  ۱=SE  ۲=S (رو به دوربین)  ۳=SW  ۴=W  ۵=NW  ۶=N  ۷=NE
## برجک‌ها و تانک فقط از همین فایل فریم و جهت می‌گیرند تا برش و جهت در کل بازی یکی باشد:
##
##     var sheet := IsoSheet.open("towers_iso/machinegun_idle")
##     sprite.texture = sheet.frame(IsoSheet.dir_from_vector(to_target), 0)
##
## اسپرایت هیچ‌وقت پیوسته نمی‌چرخد؛ فقط ردیف شیت در گام‌های ۴۵ درجه عوض می‌شود.
class_name IsoSheet
extends RefCounted

const FRAME := 128
const DIRS := 8
## فشردگی عمق دوربین ایزومتریک ۲:۱؛ پیش از گرفتن زاویه، y بر آن تقسیم می‌شود.
const DEPTH_SQUASH := 0.5
## جهت پیش‌فرض: رو به دوربین (جنوب).
const DIR_S := 2

## چهار برجک پک و سه حالتشان (README پک towers_iso).
const TOWER_KINDS := ["machinegun", "laser", "rocket", "cannon"]
const TOWER_ACTIONS := {
	"idle": {"frames": 4, "fps": 10.0, "loop": true},
	"aim": {"frames": 2, "fps": 12.0, "loop": true},
	"fire": {"frames": 4, "fps": 14.0, "loop": false},
}
## محور چرخش سر برجک در فریم (مرکز حلقهٔ گردان) و خط زمین زیر پایه.
const TOWER_PIVOT := Vector2(64.0, 75.0)
const TOWER_GROUND_Y := 97.0
## نوک لوله/دهانهٔ هر برجک در هر جهت، نسبت به TOWER_PIVOT (پیکسل فریم). این عددها
## از مرکز شعلهٔ خودِ فریم‌های fire اندازه گرفته شده‌اند تا شعله و گلولهٔ بازی دقیقاً
## از دهانهٔ همان جهت بیرون بیایند.
const TOWER_MUZZLE := {
	"machinegun": [Vector2(42, -44), Vector2(30, -28), Vector2(-2, -16), Vector2(-33, -30),
		Vector2(-44, -46), Vector2(-33, -61), Vector2(0, -60), Vector2(33, -61)],
	"laser": [Vector2(45, -40), Vector2(36, -23), Vector2(0, -11), Vector2(-37, -24),
		Vector2(-45, -40), Vector2(-36, -59), Vector2(0, -59), Vector2(35, -59)],
	"rocket": [Vector2(22, -33), Vector2(18, -25), Vector2(-1, -20), Vector2(-18, -25),
		Vector2(-23, -34), Vector2(-18, -43), Vector2(0, -48), Vector2(18, -42)],
	"cannon": [Vector2(25, -54), Vector2(19, -47), Vector2(0, -46), Vector2(-19, -47),
		Vector2(-25, -54), Vector2(-18, -62), Vector2(0, -63), Vector2(18, -62)],
}

## خانواده‌های تانک ایزومتریک و سه حالت مشترکشان. فریم‌های attack خودشان پرتو انرژی را دارند.
const TANK_FAMILIES := ["tank", "tank_armored", "tank_heavy"]
const TANK_ACTIONS := {
	"idle": {"frames": 4, "fps": 8.0, "loop": true},
	"move": {"frames": 6, "fps": 12.0, "loop": true},
	"attack": {"frames": 6, "fps": 14.0, "loop": false},
}
## نقطهٔ تماس بدنهٔ تانک با زمین در فریم.
const TANK_PIVOT := Vector2(64.0, 99.0)

## اطلس‌های پیش‌نمایش پک‌ها: فقط فریم ۰ هر جهت؛ ستون = برجک×حالت (یا حالت تانک).
const ATLAS_COLUMNS := {
	"towers_iso/atlas_towers_iso": 12,
	"units_iso_tank/atlas_tank_iso": 3,
	"units_iso_tank_armored/atlas_tank_armored_iso": 3,
	"units_iso_tank_heavy/atlas_tank_heavy_iso": 3,
}

static var _sheets: Dictionary = {}
static var _bases: Dictionary = {}

var key := ""
var texture: Texture2D = null
## تعداد ستون (فریم) و ردیف (جهت) واقعیِ بافت؛ با پشتوانهٔ Assets هم درست می‌ماند.
var frames := 0
var rows := 0
var _cache: Dictionary = {}


static func tower_key(kind: String, action: String) -> String:
	return "towers_iso/%s_%s" % [kind, action]


static func tank_key(action: String, family := "tank") -> String:
	return "units_iso_%s/%s_%s" % [family, family, action]


## همهٔ کلیدهای شیت انیمیشنی پک‌های برجک و تانک‌ها (بدون اطلس‌های پیش‌نمایش) همراه با تعداد فریمِ مورد انتظار.
static func expected_sheets() -> Dictionary:
	var out := {}
	for kind in TOWER_KINDS:
		for action in TOWER_ACTIONS.keys():
			out[tower_key(String(kind), String(action))] = int(TOWER_ACTIONS[action]["frames"])
	for family in TANK_FAMILIES:
		for action in TANK_ACTIONS.keys():
			out[tank_key(String(action), String(family))] = int(TANK_ACTIONS[action]["frames"])
	return out


## شیت یک کلید Assets (با حافظهٔ نهان). کلید گم‌شده از FALLBACKS همان دسته پر می‌شود.
static func open(sheet_key: String) -> IsoSheet:
	if _sheets.has(sheet_key):
		return _sheets[sheet_key]
	var sheet := IsoSheet.new()
	sheet.key = sheet_key
	sheet.texture = Assets.tex(sheet_key)
	if sheet.texture != null:
		sheet.frames = maxi(1, floori(float(sheet.texture.get_width()) / FRAME))
		sheet.rows = maxi(1, floori(float(sheet.texture.get_height()) / FRAME))
	_sheets[sheet_key] = sheet
	return sheet


## ناحیهٔ فریم f از ردیف جهت dir: Rect2(f*128, dir*128, 128, 128).
static func region(dir: int, f: int) -> Rect2:
	return Rect2(f * FRAME, dir * FRAME, FRAME, FRAME)


## فریم f از ردیف جهت dir به‌صورت AtlasTexture؛ هر جفت (جهت، فریم) یک‌بار ساخته می‌شود.
func frame(dir: int, f: int) -> AtlasTexture:
	if texture == null:
		return null
	var row := posmod(dir, maxi(1, rows))
	var column := posmod(f, maxi(1, frames))
	var id := row * 1000 + column
	if _cache.has(id):
		return _cache[id]
	var atlas := AtlasTexture.new()
	atlas.atlas = texture
	atlas.region = region(row, column)
	_cache[id] = atlas
	return atlas


## جهت شیت (۰..۷) برای یک بردار روی صفحه. y پیش از گرفتن زاویه از فشردگی عمق باز
## می‌شود و باقی‌مانده همیشه مثبت است (٪ در GDScript برای زاویهٔ منفی عدد منفی می‌دهد).
static func dir_from_vector(v: Vector2, fallback := DIR_S) -> int:
	var d := Vector2(v.x, v.y / DEPTH_SQUASH)
	if d.length_squared() < 0.000001:
		return fallback
	return posmod(int(round(rad_to_deg(d.angle()) / 45.0)), DIRS)


## یک گام ۴۵ درجه‌ای از جهت فعلی به سمت جهت خواسته‌شده، از کوتاه‌ترین سمت.
static func step_toward(current: int, wanted: int) -> int:
	var diff := posmod(wanted - current, DIRS)
	if diff == 0:
		return posmod(current, DIRS)
	return posmod(current + (1 if diff * 2 <= DIRS else -1), DIRS)


## پایهٔ ثابت برجک از فریم ۰ هر هشت جهت: فقط پیکسل‌های کاملاً ماتی که در همهٔ
## جهت‌ها یکسان‌اند (سکوی هشت‌ضلعی). سرِ گردان و سایهٔ نیمه‌شفاف در آن نیست؛ پس
## فریم‌های سر که رویش کشیده می‌شوند نه شبحی از سرِ فریم ۰ باقی می‌گذارند و نه سایه را
## دوبار تیره می‌کنند (پیکسل‌های پایه در همهٔ فریم‌ها مات و یکسان‌اند).
static func base_texture(sheet_key: String) -> Texture2D:
	if _bases.has(sheet_key):
		return _bases[sheet_key]
	var sheet := open(sheet_key)
	var result: Texture2D = sheet.frame(DIR_S, 0)
	var img: Image = null
	if sheet.texture != null:
		img = sheet.texture.get_image()
	if img != null and not img.is_empty() and sheet.rows >= DIRS:
		if img.is_compressed():
			img.decompress()
		if img.get_format() != Image.FORMAT_RGBA8:
			img.convert(Image.FORMAT_RGBA8)
		# مقایسهٔ خامِ ۳۲ بیتیِ هر پیکسل (به‌جای get_pixel) تا روی گوشی هم سریع باشد.
		var data := img.get_data()
		var stride := img.get_width() * 4
		var dir_step := FRAME * stride
		var out := PackedByteArray()
		out.resize(FRAME * FRAME * 4)
		out.fill(0)
		for y in FRAME:
			var row := y * stride
			for x in FRAME:
				var o := row + x * 4
				if data[o + 3] != 255:
					continue
				var c := data.decode_u32(o)
				var same := true
				for d in range(1, DIRS):
					if data.decode_u32(o + d * dir_step) != c:
						same = false
						break
				if same:
					out.encode_u32((y * FRAME + x) * 4, c)
		result = ImageTexture.create_from_image(Image.create_from_data(FRAME, FRAME, false, Image.FORMAT_RGBA8, out))
	_bases[sheet_key] = result
	return result


## پخش یک حالت از شیت روی یک Sprite2D: ستون با FPS جلو می‌رود و ردیف با جهت عوض
## می‌شود. حالت‌های حلقه‌ای تکرار می‌شوند و حالت یک‌باره روی فریم آخر می‌ماند.
class Player extends RefCounted:
	var sprite: Sprite2D = null
	var sheet: IsoSheet = null
	var action := ""
	var fps := 10.0
	var loop := true
	var dir := 2
	var time := 0.0
	var frame_index := 0

	func _init(target: Sprite2D) -> void:
		sprite = target

	func play(sheet_key: String, action_name: String, spec: Dictionary, restart := false) -> void:
		if not restart and action == action_name and sheet != null and sheet.key == sheet_key:
			return
		sheet = IsoSheet.open(sheet_key)
		action = action_name
		fps = float(spec.get("fps", 10.0))
		loop = bool(spec.get("loop", true))
		time = 0.0
		_apply()

	func set_dir(value: int) -> void:
		var wanted := posmod(value, IsoSheet.DIRS)
		if wanted != dir:
			dir = wanted
			_apply()

	func advance(delta: float) -> void:
		time += delta
		_apply()

	## آیا پخشِ یک‌بارهٔ این حالت به پایان رسیده است؟
	func finished() -> bool:
		return not loop and sheet != null and time * fps >= float(sheet.frames)

	func _apply() -> void:
		if sheet == null or sprite == null:
			return
		var count := maxi(1, sheet.frames)
		var f := int(floor(time * fps))
		frame_index = posmod(f, count) if loop else mini(f, count - 1)
		var tex := sheet.frame(dir, frame_index)
		if sprite.texture != tex:
			sprite.texture = tex
