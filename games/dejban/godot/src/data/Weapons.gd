## برجک‌های دفاعی دژبان.
##
## «سنگر» برجک رایگانِ آغاز بازی است: یک سرباز انسانی داخل سنگرِ کیسه‌شنی به ازای هر سطح.
## چهار برجک ایزومتریک (مسلسل، خمپاره‌انداز، موشک، لیزر) با ستاره در زرادخانه باز می‌شوند؛
## ظاهرشان از پک towers_iso می‌آید (پایهٔ ثابت + سرِ گردان، حالت‌های idle/aim/fire) و کلید
## «iso» نام برجک در همان پک است. شلیک، گلوله و شعلهٔ دهانه همان سامانهٔ مشترک Tower است.
##
## «قدرت» هر برجک از همین ورودی خوانده می‌شود: damage، range، cooldown و shots
## (به‌علاوهٔ splash برای انفجار ناحیه‌ای و armor_pierce برای زره‌شکافی). بقیهٔ بازی
## (برجک، نوار ساخت، زرادخانه و ارتقا) با شناسهٔ همین فهرست کار می‌کند.
class_name Weapons
extends RefCounted

const LIST := [
	{
		"id": "sangar", "family": "sangar", "tier_level": 1, "tier_name": "سنگر",
		"name": "سنگر سرباز",
		"desc": "سنگر کیسه‌شنی با یک سرباز تیرانداز که دشمنان داخل برد را یکی‌یکی هدف می‌گیرد.",
		"cost": 90, "star_cost": 0,
		"damage": 20.0, "range": 180.0, "cooldown": 0.45,
		"kind": "single", "shots": 1, "bullet": "dart", "bullet_speed": 700.0,
		"targeting": "first",
	},
	{
		"id": "machinegun", "family": "machinegun", "tier_level": 1, "tier_name": "مسلسل",
		"name": "مسلسل سنگین", "iso": "machinegun",
		"desc": "دو لولهٔ سنگین روی سپر زرهی؛ رگبار تند گلوله روی یک هدف. در برابر زره ضعیف است.",
		"cost": 100, "star_cost": 3,
		"damage": 8.0, "range": 170.0, "cooldown": 0.16,
		"kind": "single", "shots": 1, "bullet": "dart", "bullet_speed": 820.0,
		"targeting": "first", "sound": "shot_arrow",
	},
	{
		"id": "cannon", "family": "cannon", "tier_level": 1, "tier_name": "خمپاره",
		"name": "خمپاره‌انداز سنگین", "iso": "cannon",
		"desc": "لولهٔ کوتاه و کلفت؛ گلولهٔ سنگینی که با موج انفجار، دشمنانِ کنار هدف را هم می‌زند.",
		"cost": 130, "star_cost": 5,
		"damage": 34.0, "range": 200.0, "cooldown": 1.5,
		"kind": "splash", "shots": 1, "splash": 56.0, "bullet": "shell", "bullet_speed": 380.0,
		"targeting": "strong", "sound": "shot_heavy",
	},
	{
		"id": "rocket", "family": "rocket", "tier_level": 1, "tier_name": "موشک",
		"name": "سکوی موشک", "iso": "rocket",
		"desc": "چهار لولهٔ موشک با رادار؛ هر نوبت دو موشک با انفجار ناحیه‌ای و برد بلند.",
		"cost": 150, "star_cost": 7,
		"damage": 24.0, "range": 235.0, "cooldown": 1.25,
		"kind": "splash", "shots": 2, "splash": 44.0, "bullet": "bolt", "bullet_speed": 520.0,
		"targeting": "first", "sound": "shot_fire",
	},
	{
		"id": "laser", "family": "laser", "tier_level": 1, "tier_name": "لیزر",
		"name": "توپ لیزر", "iso": "laser",
		"desc": "لنز سبز با سیم‌پیچ انرژی؛ پرتوی زره‌شکاف که قوی‌ترین دشمن، مثل تانک، را نشانه می‌رود.",
		"cost": 170, "star_cost": 9,
		"damage": 26.0, "range": 190.0, "cooldown": 0.6,
		"kind": "single", "shots": 1, "bullet": "spark", "bullet_speed": 1100.0,
		"armor_pierce": true, "targeting": "strong", "sound": "shot_spark",
	},
]

## سنگر از ابتدا برای همه باز است و نیازی به خرید یا ستاره ندارد.
const FREE_IDS := ["sangar"]

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


## نام برجک در پک ایزومتریک («machinegun»…) یا رشتهٔ خالی برای سنگر.
static func iso_kind(id: String) -> String:
	return String(by_id(id).get("iso", ""))


## کلید تصویر یک سلاح در Assets: «towers/<id>» برای سنگر و شیت idle پک ایزومتریک
## («towers_iso/<kind>_idle») برای برجک‌های جدید.
static func art_key(id: String) -> String:
	var kind := iso_kind(id)
	if not kind.is_empty():
		return IsoSheet.tower_key(kind, "idle")
	return "towers/%s" % id


## تصویر نمایشی سلاح برای نوار ساخت، زرادخانه و پیش‌نمایش کاشت. برای برجک ایزومتریک
## فقط فریم ۰ جهت ICON_DIR برش داده می‌شود، نه کل شیت.
static func icon(id: String) -> Texture2D:
	var kind := iso_kind(id)
	if not kind.is_empty():
		return IsoSheet.open(art_key(id)).frame(ICON_DIR, 0)
	return Assets.tex(art_key(id))
