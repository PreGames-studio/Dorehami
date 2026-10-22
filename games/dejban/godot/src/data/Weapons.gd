## سنگر: تنها برجک دفاعی دژبان.
## یک سرباز انسانی داخل سنگرِ کیسه‌شنی ایستاده و دشمنان داخل برد را هدف می‌گیرد.
##
## «قدرت» سنگر از همین ورودی خوانده می‌شود: damage، range، cooldown و shots.
## برای تنظیم قدرت یا جایگزینی سنگر، فقط همین مقادیر و (در صورت طراحی تصویر جدید)
## فایل assets/sprites/towers/<id>.png را عوض کنید؛ بقیهٔ بازی (برجک، نوار ساخت،
## زرادخانه و ارتقا) با شناسهٔ همین فهرست کار می‌کند.
class_name Weapons
extends RefCounted

const LIST := [
	{
		"id": "sangar", "family": "sangar", "tier_level": 1, "tier_name": "سنگر",
		"name": "سنگر سرباز",
		"desc": "سنگر کیسه‌شنی با یک سرباز تیرانداز که دشمنان داخل برد را یکی‌یکی هدف می‌گیرد.",
		"cost": 120, "star_cost": 0,
		"damage": 20.0, "range": 180.0, "cooldown": 0.45,
		"kind": "single", "shots": 1, "bullet": "dart", "bullet_speed": 700.0,
		"targeting": "first",
	},
]

## سنگر از ابتدا برای همه باز است و نیازی به خرید یا ستاره ندارد.
const FREE_IDS := ["sangar"]


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


## کلید تصویر یک سلاح/سنگر در Assets («towers/<id>»). نوار ساخت، زرادخانه،
## پیش‌نمایش و خود برجک همگی از همین کلید استفاده می‌کنند.
static func art_key(id: String) -> String:
	return "towers/%s" % id
