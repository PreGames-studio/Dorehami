## شیت‌های دویدن و مرگ دشمن (ساخته‌شده با tools/gen_enemy_sprites.py).
##
## هر رکورد «ارتفاع فریم» و «خط زمین» (فاصلهٔ لبهٔ بالای فریم تا نقطهٔ تماسِ
## پیکر با زمین) را نگه می‌دارد. بدون این عددها پیکر با عوض‌شدن انیمیشن
## می‌پرد، چون ارتفاع فریم‌ها با شیت راه‌رفتنِ فعلی فرق دارد.
class_name UnitSheets
extends RefCounted

## تعداد فریم هر شیت مرگ (یک‌بار پخش می‌شود و روی فریم آخر می‌ماند).
const DEATH_FRAMES := 6

## نیروهایی که سرعت پایه‌شان از این حد بیشتر است، با انیمیشن دویدن می‌روند.
## امروز فقط «دونده» (۸۴) از این حد رد می‌شود؛ بقیه با راه‌رفتن می‌روند.
const RUN_SPEED := 60.0

## نام جهت‌ها = نام فایل‌های شیت.
const DIRS := ["front", "back", "left", "right"]

## ارتفاع فریم و خط زمینِ هر نیرو برای انیمیشن‌های دویدن و مرگ.
const GALLERY := {
	"soldier": {"run": [64, 45], "death": [64, 45]},
}



## کلید دارایی یک شیت («units/run/runner_left»).
static func tex_key(unit_id: String, anim: String, dir: String) -> String:
	return "units/%s/%s_%s" % [anim, unit_id, dir]


## خط زمینِ شیت (از لبهٔ بالای فریم) یا ۰ اگر آن شیت نباشد.
static func ground(unit_id: String, anim: String, fallback := 0.0) -> float:
	var row: Dictionary = GALLERY.get(unit_id, {})
	var pair: Array = row.get(anim, [])
	return float(pair[1]) if pair.size() == 2 else fallback


## ارتفاع فریمِ شیت یا ۰ اگر آن شیت نباشد.
static func frame_height(unit_id: String, anim: String, fallback := 0.0) -> float:
	var row: Dictionary = GALLERY.get(unit_id, {})
	var pair: Array = row.get(anim, [])
	return float(pair[0]) if pair.size() == 2 else fallback


## آیا این نیرو تندپا است و باید با انیمیشن دویدن حرکت کند؟
static func uses_run(unit_id: String, base_speed: float) -> bool:
	return GALLERY.has(unit_id) and base_speed >= RUN_SPEED


## جهتِ حرکت (روی صفحه) به نام شیت: پایین = رو به دوربین، بالا = پشت به دوربین.
static func direction_name(dir: Vector2) -> String:
	if dir.length_squared() < 0.0001:
		return "front"
	if absf(dir.y) > absf(dir.x) * 1.1:
		return "front" if dir.y > 0.0 else "back"
	return "right" if dir.x > 0.0 else "left"
