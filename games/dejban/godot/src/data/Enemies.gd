## فهرست ۸ دشمن. جان و پاداش‌ها در Balance با شمارهٔ نقشه و موج مقیاس می‌شوند.
##
##   hp      جان پایه        speed   سرعت پایه (پیکسل بر ثانیه)
##   armor   کاهش ثابت آسیب  shield  درصد کاهش آسیب (۰ تا ۱)
##   reward  سکهٔ هر کشتن     traits  ویژگی‌های ویژه
class_name Enemies
extends RefCounted

const LIST := [
	{
		"id": "grunt", "name": "سرباز", "sprite": "units/grunt",
		"hp": 60.0, "speed": 46.0, "armor": 0.0, "shield": 0.0, "reward": 2, "scale": 1.0,
		"traits": [],
	},
	{
		"id": "runner", "name": "دونده", "sprite": "units/runner",
		"hp": 42.0, "speed": 84.0, "armor": 0.0, "shield": 0.0, "reward": 3, "scale": 0.95,
		"traits": [],
	},
	{
		"id": "armored", "name": "زره‌پوش", "sprite": "units/armored",
		"hp": 120.0, "speed": 38.0, "armor": 6.0, "shield": 0.0, "reward": 5, "scale": 1.05,
		"traits": [],
	},
	{
		"id": "shield", "name": "سپردار", "sprite": "units/shield",
		"hp": 95.0, "speed": 40.0, "armor": 2.0, "shield": 0.35, "reward": 5, "scale": 1.05,
		"traits": [],
	},
	{
		"id": "archer", "name": "کمانگیر", "sprite": "units/archer",
		"hp": 70.0, "speed": 44.0, "armor": 0.0, "shield": 0.0, "reward": 4, "scale": 1.0,
		"traits": ["ranged"],
	},
	{
		"id": "healer", "name": "درمانگر", "sprite": "units/healer",
		"hp": 85.0, "speed": 42.0, "armor": 0.0, "shield": 0.0, "reward": 6, "scale": 1.0,
		"traits": ["heal"],
	},
	{
		"id": "commander", "name": "فرمانده", "sprite": "units/commander",
		"hp": 150.0, "speed": 44.0, "armor": 4.0, "shield": 0.15, "reward": 9, "scale": 1.1,
		"traits": ["buff"],
	},
	{
		"id": "brute", "name": "غول", "sprite": "units/brute",
		"hp": 420.0, "speed": 30.0, "armor": 10.0, "shield": 0.1, "reward": 16, "scale": 1.25,
		"traits": ["boss"],
	},
]

static func by_id(id: String) -> Dictionary:
	for enemy in LIST:
		if enemy["id"] == id:
			return enemy
	return {}

## ترکیب موج: فهرستی از گروه‌های {enemy, count, interval, delay}.
## الگو قطعی است تا هر نقشه و موج همیشه یک شکل داشته باشد.
static func wave_groups(map_index: int, wave: int) -> Array:
	var rng := RandomNumberGenerator.new()
	rng.seed = 7000 + map_index * 131 + wave * 17
	var budget := Balance.wave_count(wave)
	var groups: Array = []
	var available: Array = _available(map_index, wave)
	if available.is_empty():
		available = ["grunt"]

	# غول زرهی هر ۱۰ موج، همراه با چند همراه
	if wave % 10 == 0:
		groups.append({"enemy": "brute", "count": 1 + int(wave / 15.0), "interval": 2.0, "delay": 1.0})
		budget = int(budget * 0.65)

	var kinds := mini(available.size(), 1 + int(wave / 4.0))
	var picks: Array = []
	for i in kinds:
		var pick = available[rng.randi_range(0, available.size() - 1)]
		if not picks.has(pick):
			picks.append(pick)
	if picks.is_empty():
		picks = ["grunt"]

	var per_group := maxi(1, int(round(float(budget) / picks.size())))
	var delay := 0.0
	for pick in picks:
		var enemy: Dictionary = by_id(pick)
		var weight := 1.0
		if enemy.has("traits") and enemy["traits"].has("boss"):
			weight = 0.4
		var count := maxi(1, int(round(per_group * weight)))
		var interval := clampf(1.25 - 0.02 * wave, 0.35, 1.25)
		groups.append({"enemy": pick, "count": count, "interval": interval, "delay": delay})
		delay += 0.6 + rng.randf() * 1.2
	return groups


## دشمنانی که تا این نقشه و موج مجاز هستند.
static func _available(map_index: int, wave: int) -> Array:
	var out: Array = ["grunt"]
	if wave >= 2:
		out.append("runner")
	if map_index >= 2 or wave >= 4:
		out.append("archer")
	if map_index >= 2 or wave >= 6:
		out.append("armored")
	if map_index >= 4 or wave >= 8:
		out.append("shield")
	if map_index >= 6 or wave >= 10:
		out.append("healer")
	if map_index >= 8 or wave >= 13:
		out.append("commander")
	if map_index >= 11 or wave >= 16:
		out.append("brute")
	return out
