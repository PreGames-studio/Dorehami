## فهرست نیروهای دشمن: دو نیروی پیاده (سرباز و تکاور) و سه تانک ایزومتریک (تانک، تانک زرهی و تانک سنگین).
## جان و سرعت در Balance با شمارهٔ نقشه و موج مقیاس می‌شوند.
##
##   hp      جان پایه        speed   سرعت پایه (پیکسل بر ثانیه)
##   armor   کاهش ثابت آسیب  shield  درصد کاهش آسیب (۰ تا ۱)
##   reward  سکهٔ هر کشتن     traits  ویژگی‌های ویژه
##   iso     خانوادهٔ شیت ایزومتریک؛ چنین دشمنی با IsoEnemy ساخته می‌شود
##   attack  نوع حمله به دژ: «reach» = رسیدن و کم‌کردن جان،
##           «laser» = ایستادن کنار دژ، شلیک تنها یک پرتو با keep_damage جان و حذف فوری از میدان
class_name Enemies
extends RefCounted

const LIST := [
	{
		"id": "soldier", "name": "سرباز", "sprite": "units/soldier",
		"hp": 60.0, "speed": 46.0, "armor": 0.0, "shield": 0.0, "reward": 2, "scale": 1.0,
		"traits": [], "attack": "reach",
	},
	{
		"id": "commando", "name": "تکاور", "sprite": "units/commando",
		"hp": 100.0, "speed": 46.0, "armor": 0.0, "shield": 0.0, "reward": 2, "scale": 1.0,
		"traits": [], "attack": "reach",
	},
	{
		"id": "tank", "name": "تانک", "iso": "tank",
		"hp": 420.0, "speed": 28.0, "armor": 4.0, "shield": 0.0, "reward": 12, "scale": 1.6,
		"traits": [], "attack": "laser", "attack_interval": 2.0, "keep_damage": 1,
	},
	{
		"id": "tank_armored", "name": "تانک زرهی", "iso": "tank_armored",
		"hp": 600.0, "speed": 28.0, "armor": 4.0, "shield": 0.0, "reward": 12, "scale": 1.6,
		"traits": [], "attack": "laser", "attack_interval": 2.0, "keep_damage": 1,
	},
	{
		"id": "tank_heavy", "name": "تانک سنگین", "iso": "tank_heavy",
		"hp": 800.0, "speed": 28.0, "armor": 4.0, "shield": 0.0, "reward": 12, "scale": 1.6,
		"traits": [], "attack": "laser", "attack_interval": 2.0, "keep_damage": 1,
	},
]

const INFANTRY_IDS := ["soldier", "commando"]
const TANK_IDS := ["tank", "tank_armored", "tank_heavy"]

const COMMANDO_FIRST_WAVE := 3
## تانک‌ها از موج TANK_FIRST_WAVE می‌آیند و هر TANK_STEP موج یکی بیشتر می‌شوند.
const TANK_FIRST_WAVE := 5
const ARMORED_TANK_FIRST_WAVE := 8
const HEAVY_TANK_FIRST_WAVE := 12
const TANK_STEP := 5
const TANK_DELAY := 3.0
const TANK_INTERVAL := 5.0

static func by_id(id: String) -> Dictionary:
	for enemy in LIST:
		if enemy["id"] == id:
			return enemy
	return {}

## ترکیب قطعی موج‌ها:
## - شمار کل پیاده‌نظام همان Balance.wave_count(wave) است؛ از موج ۳ «تکاور» هم به شکل یک‌درمیان
##   بدون هم‌پوشانی زمانی وارد صف می‌شود.
## - شمار کل تانک‌ها همان tank_count(wave) است؛ با پیشروی موج‌ها «تانک زرهی» (از موج ۸) و
##   «تانک سنگین» (از موج ۱۲) با فاصلهٔ منظم TANK_INTERVAL به صف تانک‌ها می‌پیوندند.
static func wave_groups(_map_index: int, wave: int) -> Array:
	var budget := Balance.wave_count(wave)
	var groups: Array = []
	var total_infantry := maxi(1, int(round(float(budget))))

	if wave < COMMANDO_FIRST_WAVE:
		groups.append({"enemy": "soldier", "count": total_infantry, "interval": 1.0, "delay": 0.0})
	else:
		var commando_count := int(float(total_infantry) / 2.0)
		var soldier_count := total_infantry - commando_count
		if soldier_count > 0:
			groups.append({"enemy": "soldier", "count": soldier_count, "interval": 2.0, "delay": 0.0})
		if commando_count > 0:
			groups.append({"enemy": "commando", "count": commando_count, "interval": 2.0, "delay": 1.0})

	var tanks := tank_count(wave)
	for slot in tanks:
		groups.append({
			"enemy": _tank_for_slot(wave, slot),
			"count": 1,
			"interval": TANK_INTERVAL,
			"delay": TANK_DELAY + float(slot) * TANK_INTERVAL,
		})
	return groups


## نوع تانک در جایگاه slot از یک موج: تانک زرهی از موج ۸ و تانک سنگین از موج ۱۲ وارد می‌شوند.
static func _tank_for_slot(wave: int, slot: int) -> String:
	if wave < ARMORED_TANK_FIRST_WAVE:
		return "tank"
	if wave < HEAVY_TANK_FIRST_WAVE:
		if tank_count(wave) == 1:
			return "tank_armored"
		return "tank" if (slot % 2) == 0 else "tank_armored"
	if tank_count(wave) == 2:
		return "tank_armored" if slot == 0 else "tank_heavy"
	var order := ["tank", "tank_armored", "tank_heavy"]
	return String(order[slot % order.size()])


## تعداد کل تانک‌های یک موج: ۰ پیش از موج ۵، سپس ۱ + (موج − ۵) ÷ ۵ (تقسیم صحیح) — موج ۳۰ = ۶ تانک.
static func tank_count(wave: int) -> int:
	if wave < TANK_FIRST_WAVE:
		return 0
	return 1 + int(float(wave - TANK_FIRST_WAVE) / float(TANK_STEP))
