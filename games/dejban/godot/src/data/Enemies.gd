## فهرست ۱ دشمن (فقط سرباز قرمز کارتونی). جان و پاداش‌ها در Balance با شمارهٔ نقشه و موج مقیاس می‌شوند.
##
##   hp      جان پایه        speed   سرعت پایه (پیکسل بر ثانیه)
##   armor   کاهش ثابت آسیب  shield  درصد کاهش آسیب (۰ تا ۱)
##   reward  سکهٔ هر کشتن     traits  ویژگی‌های ویژه
class_name Enemies
extends RefCounted

const LIST := [
	{
		"id": "soldier", "name": "سرباز", "sprite": "units/soldier",
		"hp": 60.0, "speed": 46.0, "armor": 0.0, "shield": 0.0, "reward": 2, "scale": 1.0,
		"traits": [],
	},
]

static func by_id(id: String) -> Dictionary:
	for enemy in LIST:
		if enemy["id"] == id:
			return enemy
	return {}

## ترکیب موج: فقط سرباز می‌آید. الگو قطعی است تا هر نقشه و موج همیشه یک شکل داشته باشد.
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
	return groups


## دشمنانی که تا این نقشه و موج مجاز هستند.
static func _available(map_index: int, wave: int) -> Array:
	return ["soldier"]
