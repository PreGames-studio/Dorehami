## فهرست دشمنان: سرباز قرمز کارتونی و تانک ایزومتریک. جان و سرعت در Balance با شمارهٔ نقشه و موج مقیاس می‌شوند.
##
##   hp      جان پایه        speed   سرعت پایه (پیکسل بر ثانیه)
##   armor   کاهش ثابت آسیب  shield  درصد کاهش آسیب (۰ تا ۱)
##   reward  سکهٔ هر کشتن     traits  ویژگی‌های ویژه
##   iso     خانوادهٔ شیت ایزومتریک (پک units_iso_tank)؛ چنین دشمنی با IsoEnemy ساخته می‌شود
##   attack  نوع حمله به دژ: «reach» = رسیدن و کم‌کردن جان (سرباز)،
##           «laser» = ایستادن کنار دژ و شلیک لیزر هر attack_interval ثانیه با keep_damage جان
class_name Enemies
extends RefCounted

const LIST := [
	{
		"id": "soldier", "name": "سرباز", "sprite": "units/soldier",
		"hp": 60.0, "speed": 46.0, "armor": 0.0, "shield": 0.0, "reward": 2, "scale": 1.0,
		"traits": [], "attack": "reach",
	},
	{
		"id": "tank", "name": "تانک", "iso": "tank",
		"hp": 420.0, "speed": 28.0, "armor": 4.0, "shield": 0.0, "reward": 12, "scale": 1.6,
		"traits": [], "attack": "laser", "attack_interval": 2.0, "keep_damage": 1,
	},
]

## تانک‌ها از موج TANK_FIRST_WAVE می‌آیند و هر TANK_STEP موج یکی بیشتر می‌شوند.
const TANK_FIRST_WAVE := 5
const TANK_STEP := 5
const TANK_DELAY := 3.0
const TANK_INTERVAL := 5.0

static func by_id(id: String) -> Dictionary:
	for enemy in LIST:
		if enemy["id"] == id:
			return enemy
	return {}

## ترکیب موج: سربازها مثل قبل، و از موج ۵ تانک‌ها جدا از آن‌ها. الگو قطعی است تا هر
## نقشه و موج همیشه یک شکل داشته باشد.
static func wave_groups(map_index: int, wave: int) -> Array:
	var rng := RandomNumberGenerator.new()
	rng.seed = 7000 + map_index * 131 + wave * 17
	var budget := Balance.wave_count(wave)
	var groups: Array = []
	var available: Array = _available(map_index, wave)
	if available.is_empty():
		available = ["soldier"]

	var per_group := maxi(1, int(round(float(budget))))
	groups.append({"enemy": "soldier", "count": per_group, "interval": 1.0, "delay": 0.0})
	var tanks := tank_count(wave)
	if tanks > 0 and available.has("tank"):
		groups.append({"enemy": "tank", "count": tanks, "interval": TANK_INTERVAL, "delay": TANK_DELAY})
	return groups


## تعداد تانک یک موج: ۰ پیش از موج ۵، سپس ۱ + (موج − ۵) ÷ ۵ (تقسیم صحیح) — موج ۳۰ = ۶ تانک.
static func tank_count(wave: int) -> int:
	if wave < TANK_FIRST_WAVE:
		return 0
	return 1 + int(float(wave - TANK_FIRST_WAVE) / float(TANK_STEP))


## دشمنانی که تا این نقشه و موج مجاز هستند.
static func _available(map_index: int, wave: int) -> Array:
	if wave >= TANK_FIRST_WAVE:
		return ["soldier", "tank"]
	return ["soldier"]
