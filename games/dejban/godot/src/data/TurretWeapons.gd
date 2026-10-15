## مشخصات ۵ خانوادهٔ سلاح دفاعی در ۵ سطح ارتقا (از سطح ۱ ضعیف تا سطح ۵ افسانه‌ای — جمعاً ۲۵ سلاح)
## به همراه ابرسلاح ایزدی سیمرغ (omega) با ۱۰ برابر قدرت قوی‌ترین سلاح بازی و خرید درون‌برنامه‌ای ۱۰۰٬۰۰۰ تومان.
class_name TurretWeapons
extends RefCounted

const MAX_TIER := 5

const TIER_NAMES := [
	"ضعیف",
	"معمولی",
	"پیشرفته",
	"حماسی",
	"افسانه‌ای",
]

const TIER_IDS := [
	"weak",
	"common",
	"advanced",
	"epic",
	"legendary",
]

const FAMILIES := [
	{
		"id": "machine_gun",
		"name": "مسلسل",
		"en": "Machine Gun",
		"kind": "single",
		"bullet": "dart",
		"projectile_desc": "small yellow bullets",
		"reload_anim": "reload",
		"sound": "shot_bolt",
		"targeting": "first",
		"levels": [
			{
				"level": 1, "tier_id": "weak", "tier_name": "ضعیف",
				"name": "مسلسل سبک (ضعیف)", "desc": "تک‌لول ساده با شلیک گلوله‌های زرد کوچک.",
				"cost": 55, "star_cost": 0, "damage": 10.0, "range": 150.0, "cooldown": 0.42,
				"shots": 1, "bullet_speed": 620.0, "barrels": 1,
			},
			{
				"level": 2, "tier_id": "common", "tier_name": "معمولی",
				"name": "مسلسل دولول (معمولی)", "desc": "دو لولهٔ هم‌زمان با پرتاب پوکه و جرقهٔ دهانه.",
				"cost": 90, "star_cost": 3, "damage": 15.0, "range": 165.0, "cooldown": 0.36,
				"shots": 2, "bullet_speed": 660.0, "barrels": 2,
			},
			{
				"level": 3, "tier_id": "advanced", "tier_name": "پیشرفته",
				"name": "مسلسل زرهی (پیشرفته)", "desc": "مجهز به سپر فولادی و رگبار سریع گلوله‌های زرد.",
				"cost": 140, "star_cost": 7, "damage": 22.0, "range": 180.0, "cooldown": 0.30,
				"shots": 2, "bullet_speed": 720.0, "barrels": 2,
			},
			{
				"level": 4, "tier_id": "epic", "tier_name": "حماسی",
				"name": "گتلینگ سه‌لول (حماسی)", "desc": "سه‌لول چرخان با طوقهٔ طلایی و رگبار کوبنده.",
				"cost": 210, "star_cost": 12, "damage": 32.0, "range": 198.0, "cooldown": 0.24,
				"shots": 3, "bullet_speed": 780.0, "barrels": 3, "armor_pierce": true,
			},
			{
				"level": 5, "tier_id": "legendary", "tier_name": "افسانه‌ای",
				"name": "گتلینگ طلایی (افسانه‌ای)", "desc": "پنج لولهٔ چرخان طلایی؛ طوفانی از گلوله‌های زره‌شکاف.",
				"cost": 310, "star_cost": 18, "damage": 48.0, "range": 220.0, "cooldown": 0.18,
				"shots": 4, "bullet_speed": 860.0, "barrels": 5, "armor_pierce": true,
			},
		],
	},
	{
		"id": "cannon",
		"name": "توپ سنگین",
		"en": "Cannon",
		"kind": "splash",
		"bullet": "shell",
		"projectile_desc": "large black shell with fire trail",
		"reload_anim": "reload",
		"sound": "shot_stone",
		"targeting": "strong",
		"levels": [
			{
				"level": 1, "tier_id": "weak", "tier_name": "ضعیف",
				"name": "توپ صحرایی (ضعیف)", "desc": "پرتاب گلولهٔ سیاه با آسیب ناحیه‌ای پایه.",
				"cost": 110, "star_cost": 0, "damage": 32.0, "range": 175.0, "cooldown": 1.55,
				"shots": 1, "splash": 48.0, "bullet_speed": 360.0, "barrels": 1,
			},
			{
				"level": 2, "tier_id": "common", "tier_name": "معمولی",
				"name": "توپ دژکوب (معمولی)", "desc": "گلولهٔ سیاه سنگین با دنبالهٔ آتشین و ابر دود.",
				"cost": 165, "star_cost": 4, "damage": 48.0, "range": 192.0, "cooldown": 1.42,
				"shots": 1, "splash": 56.0, "bullet_speed": 390.0, "barrels": 1,
			},
			{
				"level": 3, "tier_id": "advanced", "tier_name": "پیشرفته",
				"name": "هویتزر زرهی (پیشرفته)", "desc": "ترمز دهانهٔ برنجی و موج انفجار گسترده.",
				"cost": 240, "star_cost": 8, "damage": 72.0, "range": 212.0, "cooldown": 1.30,
				"shots": 1, "splash": 66.0, "bullet_speed": 420.0, "barrels": 1,
				"burn": 8.0, "burn_time": 2.5,
			},
			{
				"level": 4, "tier_id": "epic", "tier_name": "حماسی",
				"name": "توپ دوقلو (حماسی)", "desc": "شلیک هم‌زمان دو گلولهٔ سیاه آتشین.",
				"cost": 340, "star_cost": 14, "damage": 105.0, "range": 232.0, "cooldown": 1.20,
				"shots": 2, "splash": 76.0, "bullet_speed": 450.0, "barrels": 2,
				"burn": 14.0, "burn_time": 3.0, "armor_pierce": true,
			},
			{
				"level": 5, "tier_id": "legendary", "tier_name": "افسانه‌ای",
				"name": "ابرتوپ اژدها (افسانه‌ای)", "desc": "گلولهٔ شهاب‌سنگی عظیم با دنبالهٔ آتش و انفجار مهیب.",
				"cost": 480, "star_cost": 20, "damage": 160.0, "range": 258.0, "cooldown": 1.08,
				"shots": 2, "splash": 92.0, "bullet_speed": 490.0, "barrels": 3,
				"burn": 22.0, "burn_time": 4.0, "armor_pierce": true,
			},
		],
	},
	{
		"id": "missile",
		"name": "موشک‌انداز",
		"en": "Missile Launcher",
		"kind": "splash",
		"bullet": "bolt",
		"projectile_desc": "white missile with smoke trail",
		"reload_anim": "reload",
		"sound": "shot_heavy",
		"targeting": "first",
		"levels": [
			{
				"level": 1, "tier_id": "weak", "tier_name": "ضعیف",
				"name": "موشک‌انداز دوتیوب (ضعیف)", "desc": "پرتاب موشک سفید با دنبالهٔ دود سفید.",
				"cost": 125, "star_cost": 0, "damage": 24.0, "range": 210.0, "cooldown": 1.35,
				"shots": 1, "splash": 42.0, "bullet_speed": 480.0, "tubes": 2,
			},
			{
				"level": 2, "tier_id": "common", "tier_name": "معمولی",
				"name": "موشک‌انداز چهارتیوب (معمولی)", "desc": "شلیک دو موشک سفید پیاپی به سمت هدف.",
				"cost": 185, "star_cost": 5, "damage": 34.0, "range": 232.0, "cooldown": 1.22,
				"shots": 2, "splash": 48.0, "bullet_speed": 520.0, "tubes": 4,
			},
			{
				"level": 3, "tier_id": "advanced", "tier_name": "پیشرفته",
				"name": "موشک‌انداز راداردار (پیشرفته)", "desc": "مجهز به رادار هدف‌گیری و موشک‌های کلاهک‌سرخ.",
				"cost": 265, "star_cost": 9, "damage": 48.0, "range": 258.0, "cooldown": 1.10,
				"shots": 2, "splash": 56.0, "bullet_speed": 560.0, "tubes": 4,
			},
			{
				"level": 4, "tier_id": "epic", "tier_name": "حماسی",
				"name": "سامانهٔ شش‌تیوب (حماسی)", "desc": "شلیک رگباری سه موشک هدایت‌شونده با دود سفید.",
				"cost": 370, "star_cost": 15, "damage": 68.0, "range": 286.0, "cooldown": 0.98,
				"shots": 3, "splash": 64.0, "bullet_speed": 610.0, "tubes": 6,
				"burn": 10.0, "burn_time": 2.5,
			},
			{
				"level": 5, "tier_id": "legendary", "tier_name": "افسانه‌ای",
				"name": "سکوی موشکی طوفان (افسانه‌ای)", "desc": "چهار موشک کلاهک‌طلایی در هر نوبت با برد سراسر میدان.",
				"cost": 520, "star_cost": 22, "damage": 96.0, "range": 320.0, "cooldown": 0.86,
				"shots": 4, "splash": 76.0, "bullet_speed": 670.0, "tubes": 6,
				"burn": 18.0, "burn_time": 3.5, "armor_pierce": true,
			},
		],
	},
	{
		"id": "laser",
		"name": "لیزر",
		"en": "Laser",
		"kind": "single",
		"bullet": "spark",
		"projectile_desc": "bright blue beam",
		"reload_anim": "cooldown",
		"sound": "shot_spark",
		"targeting": "strong",
		"levels": [
			{
				"level": 1, "tier_id": "weak", "tier_name": "ضعیف",
				"name": "عدسی لیزر (ضعیف)", "desc": "پرتو آبی متمرکز که زره دشمن را می‌شکافد.",
				"cost": 135, "star_cost": 2, "damage": 22.0, "range": 178.0, "cooldown": 0.78,
				"shots": 1, "bullet_speed": 950.0, "armor_pierce": true,
			},
			{
				"level": 2, "tier_id": "common", "tier_name": "معمولی",
				"name": "لیزر متمرکز (معمولی)", "desc": "هستهٔ انرژی آبی با پرتو درخشان و ذرات انرژی.",
				"cost": 195, "star_cost": 6, "damage": 35.0, "range": 198.0, "cooldown": 0.68,
				"shots": 1, "bullet_speed": 1000.0, "armor_pierce": true,
			},
			{
				"level": 3, "tier_id": "advanced", "tier_name": "پیشرفته",
				"name": "لیزر پلاسما (پیشرفته)", "desc": "خنک‌کننده‌های جانبی و پرتو پهن آبی با سوزاندن پلاسما.",
				"cost": 280, "star_cost": 10, "damage": 54.0, "range": 220.0, "cooldown": 0.58,
				"shots": 1, "bullet_speed": 1050.0, "armor_pierce": true,
				"burn": 10.0, "burn_time": 2.5,
			},
			{
				"level": 4, "tier_id": "epic", "tier_name": "حماسی",
				"name": "منشور آرکین (حماسی)", "desc": "پرتو لیزر آبی-فیروزه‌ای با نفوذ کامل در زره و سپر.",
				"cost": 390, "star_cost": 16, "damage": 82.0, "range": 246.0, "cooldown": 0.48,
				"shots": 2, "bullet_speed": 1100.0, "armor_pierce": true,
				"burn": 16.0, "burn_time": 3.0,
			},
			{
				"level": 5, "tier_id": "legendary", "tier_name": "افسانه‌ای",
				"name": "چشم خورشید (افسانه‌ای)", "desc": "ابَرپرتو کریستالی آبی-سفید با ذرات پلاسما و آسیب ویرانگر.",
				"cost": 540, "star_cost": 23, "damage": 128.0, "range": 276.0, "cooldown": 0.40,
				"shots": 2, "bullet_speed": 1200.0, "armor_pierce": true,
				"burn": 26.0, "burn_time": 3.5,
			},
		],
	},
	{
		"id": "tesla",
		"name": "تسلا",
		"en": "Tesla",
		"kind": "chain",
		"bullet": "spark",
		"projectile_desc": "purple lightning arcs",
		"reload_anim": "cooldown",
		"sound": "shot_spark",
		"targeting": "first",
		"levels": [
			{
				"level": 1, "tier_id": "weak", "tier_name": "ضعیف",
				"name": "سیم‌پیچ جرقه (ضعیف)", "desc": "صاعقهٔ بنفش که میان ۲ دشمن زنجیره می‌شود.",
				"cost": 145, "star_cost": 3, "damage": 18.0, "range": 156.0, "cooldown": 1.05,
				"shots": 1, "chain": 2, "chain_range": 84.0, "bullet_speed": 900.0,
				"slow": 0.20, "slow_time": 1.2,
			},
			{
				"level": 2, "tier_id": "common", "tier_name": "معمولی",
				"name": "برجک تسلا (معمولی)", "desc": "قوس الکتریکی بنفش میان ۳ دشمن پیاپی.",
				"cost": 210, "star_cost": 7, "damage": 28.0, "range": 172.0, "cooldown": 0.94,
				"shots": 1, "chain": 3, "chain_range": 96.0, "bullet_speed": 920.0,
				"slow": 0.28, "slow_time": 1.5,
			},
			{
				"level": 3, "tier_id": "advanced", "tier_name": "پیشرفته",
				"name": "تسلای خازن‌دار (پیشرفته)", "desc": "سیم‌پیچ سه‌حلقه با صاعقهٔ زنجیره‌ای ۴ هدفه و شوک کندکننده.",
				"cost": 295, "star_cost": 11, "damage": 42.0, "range": 190.0, "cooldown": 0.84,
				"shots": 1, "chain": 4, "chain_range": 108.0, "bullet_speed": 960.0,
				"slow": 0.36, "slow_time": 1.8,
			},
			{
				"level": 4, "tier_id": "epic", "tier_name": "حماسی",
				"name": "برج صاعقهٔ آرکین (حماسی)", "desc": "گوی پلاسمای بنفش؛ ۵ دشمن را با رعد زنجیره‌ای می‌زند.",
				"cost": 410, "star_cost": 17, "damage": 64.0, "range": 210.0, "cooldown": 0.74,
				"shots": 1, "chain": 5, "chain_range": 122.0, "bullet_speed": 1000.0,
				"slow": 0.45, "slow_time": 2.2, "armor_pierce": true,
			},
			{
				"level": 5, "tier_id": "legendary", "tier_name": "افسانه‌ای",
				"name": "تاج تندر (افسانه‌ای)", "desc": "طوفان صاعقه‌های بنفش-طلایی میان ۶ دشمن با فلج الکتریکی.",
				"cost": 560, "star_cost": 24, "damage": 98.0, "range": 236.0, "cooldown": 0.64,
				"shots": 1, "chain": 6, "chain_range": 140.0, "bullet_speed": 1080.0,
				"slow": 0.58, "slow_time": 2.6, "armor_pierce": true,
			},
		],
	},
]

## ابرسلاح ویژهٔ خرید درون‌برنامه‌ای (۱۰ برابر قوی‌ترین سلاح بازی = 10 × 160 = 1600 آسیب در هر شلیک).
const OMEGA_WEAPON := {
	"id": "omega",
	"family": "omega",
	"family_name": "سیمرغ",
	"level": 6,
	"tier_id": "mythic",
	"tier_name": "ایزدی",
	"name": "ابرسلاح ایزدی سیمرغ",
	"desc": "تاج کیهانی دژبان با ۱۰ برابر قدرت قوی‌ترین سلاح بازی؛ هم‌زمان ابرپرتو کیهانی، شهاب‌سنگ‌های آتشین و رعد زنجیره‌ای ۸ هدفه شلیک می‌کند.",
	"cost": 650,
	"star_cost": 0,
	"iap_only": true,
	"iap_price_toman": 100000,
	"iap_price_text": "۱۰۰٬۰۰۰ تومان",
	"damage": 1600.0,
	"range": 360.0,
	"cooldown": 0.45,
	"kind": "splash",
	"shots": 4,
	"splash": 110.0,
	"chain": 8,
	"chain_range": 150.0,
	"bullet": "fire",
	"bullet_speed": 980.0,
	"armor_pierce": true,
	"burn": 100.0,
	"burn_time": 4.5,
	"slow": 0.65,
	"slow_time": 3.0,
	"reload_anim": "cooldown",
	"sound": "shot_fire",
	"targeting": "strong",
	"projectile_desc": "cosmic beam + meteor shells + lightning crown",
	"sheets": {
		"idle": "res://spritesheets/turrets/tiers/omega/omega_idle.png",
		"aim": "res://spritesheets/turrets/tiers/omega/omega_aim.png",
		"fire": "res://spritesheets/turrets/tiers/omega/omega_fire.png",
		"cooldown": "res://spritesheets/turrets/tiers/omega/omega_cooldown.png",
	},
}


static func family_ids() -> Array:
	var out: Array = []
	for fam in FAMILIES:
		out.append(String(fam["id"]))
	return out


static func family_count() -> int:
	return FAMILIES.size()


static func total_tier_count() -> int:
	var total := 0
	for fam in FAMILIES:
		total += (fam["levels"] as Array).size()
	return total


static func max_regular_damage() -> float:
	var best := 0.0
	for fam in FAMILIES:
		for entry in fam["levels"]:
			best = maxf(best, float(entry.get("damage", 0.0)))
	return best


static func omega_def() -> Dictionary:
	return OMEGA_WEAPON.duplicate(true)


static func family_by_id(family_id: String) -> Dictionary:
	if family_id == "omega":
		return {
			"id": "omega",
			"name": "سیمرغ",
			"en": "Simorgh Omega",
			"kind": "splash",
			"bullet": "fire",
			"projectile_desc": OMEGA_WEAPON["projectile_desc"],
			"reload_anim": "cooldown",
			"sound": "shot_fire",
			"targeting": "strong",
			"levels": [OMEGA_WEAPON],
		}
	for fam in FAMILIES:
		if String(fam["id"]) == family_id:
			return fam
	return {}


## دریافت مشخصات کامل یک سلاح در سطح ۱ تا ۵ (یا omega).
static func tier_def(family_id: String, level: int) -> Dictionary:
	if family_id == "omega":
		return omega_def()
	var fam := family_by_id(family_id)
	if fam.is_empty():
		return {}
	var lvl := clampi(level, 1, MAX_TIER)
	var levels: Array = fam["levels"]
	var entry: Dictionary = (levels[lvl - 1] as Dictionary).duplicate(true)
	entry["family"] = family_id
	entry["id"] = "%s_lv%d" % [family_id, lvl]
	entry["family_name"] = fam["name"]
	entry["kind"] = fam["kind"]
	entry["bullet"] = fam["bullet"]
	entry["projectile_desc"] = fam["projectile_desc"]
	entry["reload_anim"] = fam["reload_anim"]
	entry["sound"] = fam["sound"]
	entry["targeting"] = fam["targeting"]
	entry["sheets"] = {
		"idle": sheet_path(family_id, lvl, "idle"),
		"aim": sheet_path(family_id, lvl, "aim"),
		"fire": sheet_path(family_id, lvl, "fire"),
		String(fam["reload_anim"]): sheet_path(family_id, lvl, String(fam["reload_anim"])),
	}
	return entry


## فهرست مسطح تمام ۲۵ سلاح سطح‌بندی‌شده (۵ خانواده × ۵ سطح).
static func all_tiers() -> Array:
	var out: Array = []
	for fam in FAMILIES:
		var fid := String(fam["id"])
		for lvl in range(1, MAX_TIER + 1):
			out.append(tier_def(fid, lvl))
	return out


## فهرست کامل ۲۶ سلاح بازی (۲۵ سلاح سطح‌بندی‌شده + ابرسلاح ایزدی سیمرغ).
static func all_weapons_with_omega() -> Array:
	var out := all_tiers()
	out.append(omega_def())
	return out


static func sheet_path(family_id: String, level: int, anim: String) -> String:
	if family_id == "omega":
		return "res://spritesheets/turrets/tiers/omega/omega_%s.png" % anim
	var lvl := clampi(level, 1, MAX_TIER)
	return "res://spritesheets/turrets/tiers/lv%d/%s_lv%d_%s.png" % [lvl, family_id, lvl, anim]
