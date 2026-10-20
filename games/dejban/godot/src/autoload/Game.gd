## وضعیت پایدار بازی: ستاره‌ها، نقشه‌ها و سلاح‌های بازشده، تنظیمات و رکوردها.
## این گره خودکار راه‌اندازی می‌شود و روی دستگاه ذخیره می‌کند (user://).
extends Node

const SAVE_PATH := "user://dezhban_save.json"
const SAVE_VERSION := 1

signal changed

var stars := 0
var unlocked_maps: Array = []
var unlocked_weapons: Array = []
## سطح ارتقای هر سلاح در زرادخانه (شناسه → سطح ۰ تا ۳۰).
var weapon_levels: Dictionary = {}
## سطح آیتم «افزایش اعتبار» (PRESTIGE BOOST — از ۰ تا ۵۰؛ هر سطح +۱۰ سکهٔ شروع نبرد).
var prestige_level := 0
var nickname := "نگهبان"
var music_volume := 0.7
var sfx_volume := 0.9
var best: Dictionary = {}
var last_map := 0
## نقشه‌ای که در انتظار ورود به میدان نبرد است (توسط اطلس نقشه‌ها پر می‌شود).
var pending_map := 0
## ضریب سرعت انتخاب‌شده در نبرد (۱ تا ۳).
var speed_index := 0

func _ready() -> void:
	load_profile()
	Audio.apply_settings(music_volume, sfx_volume)

# ------------------------------------------------------------------ ذخیره و بارگذاری

func default_unlocked_weapons() -> Array:
	return Weapons.FREE_IDS.duplicate()

func reset_progress() -> void:
	stars = 0
	unlocked_maps = [0]
	unlocked_weapons = default_unlocked_weapons()
	weapon_levels = {}
	prestige_level = 0
	best = {}
	last_map = 0
	save()
	changed.emit()

func fresh_profile() -> Dictionary:
	return {
		"version": SAVE_VERSION,
		"stars": 0,
		"unlocked_maps": [0],
		"unlocked_weapons": default_unlocked_weapons(),
		"weapon_levels": {},
		"prestige_level": 0,
		"nickname": "نگهبان",
		"music_volume": 0.7,
		"sfx_volume": 0.9,
		"best": {},
		"last_map": 0,
	}

func load_profile() -> void:
	var data := fresh_profile()
	if FileAccess.file_exists(SAVE_PATH):
		var file := FileAccess.open(SAVE_PATH, FileAccess.READ)
		if file != null:
			var text := file.get_as_text()
			file.close()
			var parsed = JSON.parse_string(text)
			if parsed is Dictionary:
				data = parsed
	_apply(data)

func _apply(data: Dictionary) -> void:
	stars = maxi(0, int(data.get("stars", 0)))
	# فهرست قدیمیِ خریدهای ستاره‌ای معتبر نیست؛ بازشدن فقط از پیروزی‌ها به‌دست می‌آید.
	unlocked_weapons = _string_array(data.get("unlocked_weapons", default_unlocked_weapons()), Weapons.ids())
	for free_id in Weapons.FREE_IDS:
		if not unlocked_weapons.has(free_id):
			unlocked_weapons.append(free_id)
	weapon_levels = {}
	var raw_levels = data.get("weapon_levels", {})
	if raw_levels is Dictionary:
		for key in raw_levels.keys():
			var level := clampi(int(raw_levels[key]), 0, Balance.ARMORY_MAX_LEVEL)
			if level > 0 and not Weapons.by_id(String(key)).is_empty():
				weapon_levels[String(key)] = level
	prestige_level = clampi(int(data.get("prestige_level", 0)), 0, Balance.PRESTIGE_MAX_LEVEL)
	nickname = String(data.get("nickname", "نگهبان")).substr(0, 16)
	if nickname.strip_edges().is_empty():
		nickname = "نگهبان"
	music_volume = clampf(float(data.get("music_volume", 0.7)), 0.0, 1.0)
	sfx_volume = clampf(float(data.get("sfx_volume", 0.9)), 0.0, 1.0)
	last_map = clampi(int(data.get("last_map", 0)), 0, Maps.count() - 1)
	best = {}
	var raw_best = data.get("best", {})
	if raw_best is Dictionary:
		for key in raw_best.keys():
			var entry = raw_best[key]
			if entry is Dictionary:
				best[String(key)] = {
					"wave": int(entry.get("wave", 0)),
					"stars": int(entry.get("stars", 0)),
					"won": bool(entry.get("won", false)),
				}
	_sync_unlocked_maps()
	changed.emit()

func save() -> void:
	var data := {
		"version": SAVE_VERSION,
		"stars": stars,
		"unlocked_maps": unlocked_maps,
		"unlocked_weapons": unlocked_weapons,
		"weapon_levels": weapon_levels,
		"prestige_level": prestige_level,
		"nickname": nickname,
		"music_volume": music_volume,
		"sfx_volume": sfx_volume,
		"best": best,
		"last_map": last_map,
	}
	var file := FileAccess.open(SAVE_PATH, FileAccess.WRITE)
	if file == null:
		push_warning("ذخیرهٔ بازی ممکن نشد: %s" % SAVE_PATH)
		return
	file.store_string(JSON.stringify(data, "  "))
	file.close()

static func _string_array(value, allowed: Array) -> Array:
	var out: Array = []
	if value is Array:
		for item in value:
			var key := String(item)
			if allowed.has(key) and not out.has(key):
				out.append(key)
	return out

# ------------------------------------------------------------------ پیشرفت

func add_stars(amount: int) -> void:
	stars = maxi(0, stars + amount)
	save()
	changed.emit()

## فقط نقشهٔ اول رایگان است؛ زنجیرهٔ پیروزی‌ها باید تا نقشهٔ قبلی کامل باشد.
func is_map_unlocked(index: int) -> bool:
	return index >= 0 and index < Maps.count() and unlocked_maps.has(index)

func _sync_unlocked_maps() -> void:
	unlocked_maps = [0]
	for index in range(1, Maps.count()):
		if not bool(map_best(index - 1).get("won", false)):
			break
		unlocked_maps.append(index)

func is_weapon_unlocked(id: String) -> bool:
	return unlocked_weapons.has(id)

func is_weapon_iap(id: String) -> bool:
	return bool(Weapons.by_id(id).get("iap_only", false))

func unlock_weapon(id: String) -> bool:
	if is_weapon_unlocked(id):
		return true
	var weapon := Weapons.by_id(id)
	if weapon.is_empty():
		return false
	if bool(weapon.get("iap_only", false)):
		return false
	var cost := int(weapon.get("star_cost", 0))
	if stars < cost:
		return false
	stars -= cost
	unlocked_weapons.append(id)
	save()
	changed.emit()
	return true

## بازگشایی سلاح ویژهٔ خرید درون‌برنامه‌ای پس از تأیید درگاه پرداخت.
func unlock_iap_weapon(id: String) -> bool:
	if is_weapon_unlocked(id):
		return true
	var weapon := Weapons.by_id(id)
	if weapon.is_empty() or not bool(weapon.get("iap_only", false)):
		return false
	unlocked_weapons.append(id)
	save()
	changed.emit()
	return true

## سطح ارتقای سلاح در زرادخانه (۰ یعنی ارتقا نخورده).
func weapon_level(id: String) -> int:
	return int(weapon_levels.get(id, 0))

## هزینهٔ ستاره برای ارتقای بعدی: از ۱ شروع و در هر سطح یک ستاره بیشتر.
func weapon_upgrade_cost(id: String) -> int:
	return weapon_level(id) + 1

func can_upgrade_weapon(id: String) -> bool:
	return is_weapon_unlocked(id) and weapon_level(id) < Balance.ARMORY_MAX_LEVEL

func upgrade_weapon(id: String) -> bool:
	if not can_upgrade_weapon(id):
		return false
	var cost := weapon_upgrade_cost(id)
	if stars < cost:
		return false
	stars -= cost
	weapon_levels[id] = weapon_level(id) + 1
	save()
	changed.emit()
	return true

## سکهٔ اضافی شروع نبرد از آیتم «افزایش اعتبار» (هر سطح +۱۰ سکه).
func prestige_starting_coins() -> int:
	return prestige_level * Balance.PRESTIGE_COINS_PER_LEVEL

## قیمت ثابت هر سطح افزایش اعتبار: ۱۰ ستاره.
func prestige_upgrade_cost() -> int:
	return 10

func can_upgrade_prestige() -> bool:
	return prestige_level < Balance.PRESTIGE_MAX_LEVEL

func upgrade_prestige() -> bool:
	if not can_upgrade_prestige():
		return false
	var cost := prestige_upgrade_cost()
	if stars < cost:
		return false
	stars -= cost
	prestige_level += 1
	save()
	changed.emit()
	return true

## ضریب‌های نبرد: آسیب و برد بزرگ‌تر می‌شوند و فاصلهٔ شلیک کوچک‌تر (شلیک تندتر).
func weapon_damage_mult(id: String) -> float:
	return 1.0 + Balance.ARMORY_STEP * float(weapon_level(id))

func weapon_range_mult(id: String) -> float:
	return 1.0 + Balance.ARMORY_STEP * float(weapon_level(id))

func weapon_interval_mult(id: String) -> float:
	return 1.0 / (1.0 + Balance.ARMORY_STEP * float(weapon_level(id)))

func map_best(index: int) -> Dictionary:
	return best.get(str(index), {"wave": 0, "stars": 0, "won": false})

func map_stars(index: int) -> int:
	return int(map_best(index).get("stars", 0))

func total_map_stars() -> int:
	var total := 0
	for i in Maps.count():
		total += map_stars(i)
	return total

func next_unlocked_map(from_index: int) -> int:
	var index := from_index + 1
	while index < Maps.count():
		if is_map_unlocked(index):
			return index
		index += 1
	return from_index

## پایان یک نبرد: بهترین نتیجه حفظ می‌شود، ولی پاداش کامل هر دور دوباره قابل دریافت است.
func record_run(map_index: int, reached_wave: int, won: bool) -> Dictionary:
	var earned := Balance.stars_for(map_index, reached_wave, won)
	var previous := map_best(map_index)
	var kept_stars: int = maxi(int(previous.get("stars", 0)), earned)
	var best_wave: int = maxi(int(previous.get("wave", 0)), reached_wave)
	best[str(map_index)] = {"wave": best_wave, "stars": kept_stars, "won": bool(previous.get("won", false)) or won}
	var gained: int = earned
	stars += gained
	last_map = map_index
	_sync_unlocked_maps()
	save()
	changed.emit()
	return {"stars": earned, "gained": gained, "wave": best_wave}

func set_audio(music: float, sfx: float) -> void:
	music_volume = clampf(music, 0.0, 1.0)
	sfx_volume = clampf(sfx, 0.0, 1.0)
	Audio.apply_settings(music_volume, sfx_volume)
	save()

func set_nickname(value: String) -> void:
	nickname = value.substr(0, 16)
	save()
	changed.emit()
