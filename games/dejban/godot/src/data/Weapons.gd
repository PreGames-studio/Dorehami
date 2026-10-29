## چهار برجک دفاعی دژبان: مسلسل، توپخانه، لیزر و تسلا.
##
## «مسلسل سنگین» برجک رایگانِ آغاز بازی است؛ بقیه با ستاره در زرادخانه باز می‌شوند.
## هر چهار برج پایهٔ ثابت و سر لایه‌ای دارند. پیشرفت برج موشکی سابق هنگام
## بارگذاری ذخیره به تسلا منتقل می‌شود؛ قیمت برج‌های قابل خرید تغییر نمی‌کند.
##
## «قدرت» هر برجک از همین ورودی خوانده می‌شود: damage، range، cooldown و shots
## (به‌علاوهٔ splash برای انفجار ناحیه‌ای و armor_pierce برای زره‌شکافی). بقیهٔ بازی
## (برجک، نوار ساخت، زرادخانه و ارتقا) با شناسهٔ همین فهرست کار می‌کند.
class_name Weapons
extends RefCounted

const LIST := [
	{
		"id": "machinegun", "family": "machinegun", "tier_level": 1, "tier_name": "مسلسل",
		"name": "مسلسل سنگین", "iso": "machinegun", "visual": "machinegun_layers",
		"desc": "برج مسلسل پنج‌سطحی با پایهٔ ثابت؛ نزدیک‌ترین دشمن را با رگبار دنبال می‌کند.",
		"cost": 100, "star_cost": 0,
		"damage": 8.0, "range": 170.0, "cooldown": 0.16,
		"kind": "single", "shots": 1, "bullet": "dart", "bullet_speed": 820.0,
		"targeting": "close", "sound": "shot_arrow",
		# Each row is the effective in-battle level before armory bonuses. Speeds
		# are radians/second, cooldowns seconds; this is the tuning table.
		"levels": [
			{"damage": 8.0,  "range": 170.0, "cooldown": 0.16,  "turn_speed": 3.6},
			{"damage": 10.0, "range": 180.0, "cooldown": 0.145, "turn_speed": 4.1},
			{"damage": 12.0, "range": 192.0, "cooldown": 0.13,  "turn_speed": 4.6},
			{"damage": 14.0, "range": 205.0, "cooldown": 0.115, "turn_speed": 5.2},
			{"damage": 16.0, "range": 220.0, "cooldown": 0.10,  "turn_speed": 5.8},
		],
	},
	{
		"id": "cannon", "family": "cannon", "tier_level": 1, "tier_name": "توپخانه",
		"name": "توپخانهٔ سنگین", "iso": "cannon", "visual": "artillery_layers",
		"desc": "توپخانهٔ پنج‌سطحی؛ گلوله در محدوده‌ای کوچک منفجر می‌شود و به چند دشمن آسیب می‌زند.",
		"cost": 130, "star_cost": 5,
		"damage": 34.0, "range": 200.0, "cooldown": 1.5,
		"kind": "splash", "shots": 1, "splash": 42.0, "bullet": "shell", "bullet_speed": 380.0,
		"targeting": "close", "sound": "shot_heavy",
		# Reload is cooldown in seconds. Falloff is the fraction at the edge.
		"edge_damage_fraction": 0.5,
		"levels": [
			{"damage": 34.0, "range": 200.0, "cooldown": 1.50, "splash": 42.0, "turn_speed": 2.2},
			{"damage": 42.0, "range": 210.0, "cooldown": 1.38, "splash": 46.0, "turn_speed": 2.4},
			{"damage": 51.0, "range": 220.0, "cooldown": 1.26, "splash": 50.0, "turn_speed": 2.6},
			{"damage": 62.0, "range": 230.0, "cooldown": 1.14, "splash": 54.0, "turn_speed": 2.8},
			{"damage": 75.0, "range": 240.0, "cooldown": 1.02, "splash": 58.0, "turn_speed": 3.0},
		],
	},
	{
		"id": "laser", "family": "laser", "tier_level": 1, "tier_name": "لیزر",
		"name": "توپ لیزر", "iso": "laser", "visual": "laser_layers",
		"desc": "پرتو تا مرز میدان از دشمنان عبور می‌کند و به هر دشمن، حتی با زره و سپر، یک‌بار آسیب کامل می‌زند.",
		"cost": 170, "star_cost": 9,
		"damage": 26.0, "range": 190.0, "cooldown": 0.6,
		"kind": "beam", "beam_duration": 0.32, "beam_width": 3.6,
		"armor_pierce": true, "targeting": "strong", "sound": "shot_laser",
		# cooldown is reload *after* beam_duration; range is target acquisition.
		# The fired beam itself continues to the battlefield boundary.
		"levels": [
			{"damage": 26.0, "range": 190.0, "cooldown": 0.60, "beam_duration": 0.32, "beam_width": 3.6,  "turn_speed": 2.8},
			{"damage": 34.0, "range": 200.0, "cooldown": 0.56, "beam_duration": 0.36, "beam_width": 5.2,  "turn_speed": 3.0},
			{"damage": 43.0, "range": 210.0, "cooldown": 0.52, "beam_duration": 0.40, "beam_width": 6.8,  "turn_speed": 3.2},
			{"damage": 53.0, "range": 220.0, "cooldown": 0.48, "beam_duration": 0.44, "beam_width": 8.8,  "turn_speed": 3.4},
			{"damage": 65.0, "range": 230.0, "cooldown": 0.44, "beam_duration": 0.48, "beam_width": 12.0, "turn_speed": 3.6},
		],
	},
	{
		"id": "tesla", "family": "tesla", "tier_level": 1, "tier_name": "تسلا",
		"name": "برج تسلا", "visual": "tesla_layers",
		"desc": "برق از جلوترین دشمنِ داخل برد تا پنج هدف زنجیره می‌زند؛ هر انتقال ۲۰٪ ضعیف‌تر است.",
		"cost": 200, "star_cost": 11,
		"damage": 20.0, "range": 180.0, "cooldown": 1.40,
		"kind": "chain", "chain": 5, "chain_range": 70.0, "chain_falloff": 0.8,
		"targeting": "first", "sound": "shot_tesla",
		# No bullet or projectile: each hop is resolved against the actual HP and armor.
		"levels": [
			{"damage": 20.0, "range": 180.0, "cooldown": 1.40, "chain_range": 70.0,  "turn_speed": 2.4},
			{"damage": 27.0, "range": 192.0, "cooldown": 1.25, "chain_range": 78.0,  "turn_speed": 2.6},
			{"damage": 34.0, "range": 204.0, "cooldown": 1.12, "chain_range": 86.0,  "turn_speed": 2.8},
			{"damage": 42.0, "range": 216.0, "cooldown": 1.00, "chain_range": 94.0,  "turn_speed": 3.0},
			{"damage": 52.0, "range": 230.0, "cooldown": 0.88, "chain_range": 102.0, "turn_speed": 3.2},
		],
	},
]

## مسلسل سنگین از ابتدا برای همه باز است و نیازی به خرید یا ستاره ندارد.
const FREE_IDS := ["machinegun"]

## جهت تصویرِ نمایشی برجک‌های ایزومتریک در نوار ساخت و زرادخانه (جنوب‌شرق، مثل پیش‌نمایش پک).
const ICON_DIR := 1


static func by_id(id: String) -> Dictionary:
	for weapon in LIST:
		if weapon["id"] == id:
			return weapon
	return {}


static func ids() -> Array:
	var out: Array = []
	for weapon in LIST:
		out.append(weapon["id"])
	return out


static func count() -> int:
	return LIST.size()


## نام برجک در پک ایزومتریک («machinegun»…) یا رشتهٔ خالی برای شناسهٔ ناشناخته.
static func iso_kind(id: String) -> String:
	return String(by_id(id).get("iso", ""))


## کلید تصویر یک سلاح در Assets؛ برای برجک‌های لایه‌ای PNG مستقل سطح ۱ است.
static func art_key(id: String) -> String:
	if id == "machinegun":
		return "towers_machinegun/machinegun_1"
	if id == "cannon":
		return "towers_artillery/artillery_1"
	if id == "tesla":
		return "towers_tesla/tesla_1"
	if id == "laser":
		return "towers_laser/laser_1"
	var kind := iso_kind(id)
	if not kind.is_empty():
		return IsoSheet.tower_key(kind, "idle")
	return "towers/%s" % id


## تصویر نمایشی نوار ساخت و زرادخانه. تسلا و لیزر آیکون برش‌خورده دارند؛
## پیش‌نمای کاشت از تصویر کامل هم‌لنگر استفاده می‌کند. شاخهٔ ایزومتریک
## پایینی فقط برای سازگاری با داده‌های پک قدیمی باقی مانده است.
static func icon(id: String) -> Texture2D:
	if id == "machinegun" or id == "cannon":
		return Assets.tex(art_key(id))
	if id == "tesla":
		return Assets.tex("towers_tesla/icon")
	if id == "laser":
		return Assets.tex("towers_laser/icon")
	var kind := iso_kind(id)
	if not kind.is_empty():
		return IsoSheet.open(art_key(id)).frame(ICON_DIR, 0)
	return Assets.tex(art_key(id))
