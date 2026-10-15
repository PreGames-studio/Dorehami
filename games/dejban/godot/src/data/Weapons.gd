## مشخصات ۲۶ سلاح برجک دفاعی دژبان:
## - ۵ خانوادهٔ سلاح (مسلسل، توپ سنگین، موشک‌انداز، لیزر، تسلا) در ۵ سطح ارتقا از ضعیف تا افسانه‌ای (۲۵ سلاح)
## - ۱ ابرسلاح ویژهٔ خرید درون‌برنامه‌ای («ابرسلاح ایزدی سیمرغ» — omega) با ۱۰ برابر قدرت قوی‌ترین سلاح بازی،
##   قفل‌شده و با قیمت نمایشی ۱۰۰٬۰۰۰ تومان (بدون فروش با ستارهٔ درون بازی، آمادهٔ اتصال به درگاه پرداخت).
class_name Weapons
extends RefCounted

const LIST := [
	# ۱. خانوادهٔ مسلسل (Machine Gun): سطح ۱ ضعیف تا سطح ۵ افسانه‌ای
	{
		"id": "machine_gun_lv1", "family": "machine_gun", "tier_level": 1, "tier_name": "ضعیف",
		"name": "مسلسل سبک (ضعیف)", "desc": "تک‌لول ساده با شلیک گلوله‌های زرد کوچک.",
		"cost": 55, "star_cost": 0, "damage": 10.0, "range": 150.0, "cooldown": 0.42,
		"kind": "single", "shots": 1, "bullet": "dart", "bullet_speed": 620.0, "targeting": "first",
	},
	{
		"id": "machine_gun_lv2", "family": "machine_gun", "tier_level": 2, "tier_name": "معمولی",
		"name": "مسلسل دولول (معمولی)", "desc": "دو لولهٔ هم‌زمان با پرتاب پوکه و جرقهٔ دهانه.",
		"cost": 90, "star_cost": 3, "damage": 15.0, "range": 165.0, "cooldown": 0.36,
		"kind": "single", "shots": 2, "bullet": "dart", "bullet_speed": 660.0, "targeting": "first",
	},
	{
		"id": "machine_gun_lv3", "family": "machine_gun", "tier_level": 3, "tier_name": "پیشرفته",
		"name": "مسلسل زرهی (پیشرفته)", "desc": "مجهز به سپر فولادی و رگبار سریع گلوله‌های زرد.",
		"cost": 140, "star_cost": 7, "damage": 22.0, "range": 180.0, "cooldown": 0.30,
		"kind": "single", "shots": 2, "bullet": "dart", "bullet_speed": 720.0, "targeting": "first",
	},
	{
		"id": "machine_gun_lv4", "family": "machine_gun", "tier_level": 4, "tier_name": "حماسی",
		"name": "گتلینگ سه‌لول (حماسی)", "desc": "سه‌لول چرخان با طوقهٔ طلایی و رگبار کوبندهٔ زره‌شکاف.",
		"cost": 210, "star_cost": 12, "damage": 32.0, "range": 198.0, "cooldown": 0.24,
		"kind": "single", "shots": 3, "bullet": "dart", "bullet_speed": 780.0,
		"armor_pierce": true, "targeting": "first",
	},
	{
		"id": "machine_gun_lv5", "family": "machine_gun", "tier_level": 5, "tier_name": "افسانه‌ای",
		"name": "گتلینگ طلایی (افسانه‌ای)", "desc": "پنج لولهٔ چرخان طلایی؛ طوفانی از گلوله‌های زرد زره‌شکاف.",
		"cost": 310, "star_cost": 18, "damage": 48.0, "range": 220.0, "cooldown": 0.18,
		"kind": "single", "shots": 4, "bullet": "dart", "bullet_speed": 860.0,
		"armor_pierce": true, "targeting": "first",
	},

	# ۲. خانوادهٔ توپ سنگین (Cannon): سطح ۱ ضعیف تا سطح ۵ افسانه‌ای
	{
		"id": "cannon_lv1", "family": "cannon", "tier_level": 1, "tier_name": "ضعیف",
		"name": "توپ صحرایی (ضعیف)", "desc": "پرتاب گلولهٔ سیاه با آسیب ناحیه‌ای پایه.",
		"cost": 110, "star_cost": 0, "damage": 32.0, "range": 175.0, "cooldown": 1.55,
		"kind": "splash", "shots": 1, "splash": 48.0, "bullet": "shell", "bullet_speed": 360.0, "targeting": "strong",
	},
	{
		"id": "cannon_lv2", "family": "cannon", "tier_level": 2, "tier_name": "معمولی",
		"name": "توپ دژکوب (معمولی)", "desc": "گلولهٔ سیاه سنگین با دنبالهٔ آتشین و ابر دود.",
		"cost": 165, "star_cost": 4, "damage": 48.0, "range": 192.0, "cooldown": 1.42,
		"kind": "splash", "shots": 1, "splash": 56.0, "bullet": "shell", "bullet_speed": 390.0, "targeting": "strong",
	},
	{
		"id": "cannon_lv3", "family": "cannon", "tier_level": 3, "tier_name": "پیشرفته",
		"name": "هویتزر زرهی (پیشرفته)", "desc": "ترمز دهانهٔ برنجی و موج انفجار گسترده و سوزان.",
		"cost": 240, "star_cost": 8, "damage": 72.0, "range": 212.0, "cooldown": 1.30,
		"kind": "splash", "shots": 1, "splash": 66.0, "bullet": "shell", "bullet_speed": 420.0,
		"burn": 8.0, "burn_time": 2.5, "targeting": "strong",
	},
	{
		"id": "cannon_lv4", "family": "cannon", "tier_level": 4, "tier_name": "حماسی",
		"name": "توپ دوقلو (حماسی)", "desc": "شلیک هم‌زمان دو گلولهٔ سیاه آتشین زره‌شکاف.",
		"cost": 340, "star_cost": 14, "damage": 105.0, "range": 232.0, "cooldown": 1.20,
		"kind": "splash", "shots": 2, "splash": 76.0, "bullet": "shell", "bullet_speed": 450.0,
		"burn": 14.0, "burn_time": 3.0, "armor_pierce": true, "targeting": "strong",
	},
	{
		"id": "cannon_lv5", "family": "cannon", "tier_level": 5, "tier_name": "افسانه‌ای",
		"name": "ابرتوپ اژدها (افسانه‌ای)", "desc": "گلولهٔ شهاب‌سنگی عظیم با دنبالهٔ آتش و انفجار مهیب.",
		"cost": 480, "star_cost": 20, "damage": 160.0, "range": 258.0, "cooldown": 1.08,
		"kind": "splash", "shots": 2, "splash": 92.0, "bullet": "shell", "bullet_speed": 490.0,
		"burn": 22.0, "burn_time": 4.0, "armor_pierce": true, "targeting": "strong",
	},

	# ۳. خانوادهٔ موشک‌انداز (Missile Launcher): سطح ۱ ضعیف تا سطح ۵ افسانه‌ای
	{
		"id": "missile_lv1", "family": "missile", "tier_level": 1, "tier_name": "ضعیف",
		"name": "موشک‌انداز دوتیوب (ضعیف)", "desc": "پرتاب موشک سفید با دنبالهٔ دود سفید.",
		"cost": 125, "star_cost": 0, "damage": 24.0, "range": 210.0, "cooldown": 1.35,
		"kind": "splash", "shots": 1, "splash": 42.0, "bullet": "bolt", "bullet_speed": 480.0, "targeting": "first",
	},
	{
		"id": "missile_lv2", "family": "missile", "tier_level": 2, "tier_name": "معمولی",
		"name": "موشک‌انداز چهارتیوب (معمولی)", "desc": "شلیک دو موشک سفید پیاپی به سمت هدف.",
		"cost": 185, "star_cost": 5, "damage": 34.0, "range": 232.0, "cooldown": 1.22,
		"kind": "splash", "shots": 2, "splash": 48.0, "bullet": "bolt", "bullet_speed": 520.0, "targeting": "first",
	},
	{
		"id": "missile_lv3", "family": "missile", "tier_level": 3, "tier_name": "پیشرفته",
		"name": "موشک‌انداز راداردار (پیشرفته)", "desc": "مجهز به رادار هدف‌گیری و موشک‌های کلاهک‌سرخ.",
		"cost": 265, "star_cost": 9, "damage": 48.0, "range": 258.0, "cooldown": 1.10,
		"kind": "splash", "shots": 2, "splash": 56.0, "bullet": "bolt", "bullet_speed": 560.0, "targeting": "first",
	},
	{
		"id": "missile_lv4", "family": "missile", "tier_level": 4, "tier_name": "حماسی",
		"name": "سامانهٔ شش‌تیوب (حماسی)", "desc": "شلیک رگباری سه موشک هدایت‌شونده با دود سفید.",
		"cost": 370, "star_cost": 15, "damage": 68.0, "range": 286.0, "cooldown": 0.98,
		"kind": "splash", "shots": 3, "splash": 64.0, "bullet": "bolt", "bullet_speed": 610.0,
		"burn": 10.0, "burn_time": 2.5, "targeting": "first",
	},
	{
		"id": "missile_lv5", "family": "missile", "tier_level": 5, "tier_name": "افسانه‌ای",
		"name": "سکوی موشکی طوفان (افسانه‌ای)", "desc": "چهار موشک کلاهک‌طلایی در هر نوبت با برد سراسر میدان.",
		"cost": 520, "star_cost": 22, "damage": 96.0, "range": 320.0, "cooldown": 0.86,
		"kind": "splash", "shots": 4, "splash": 76.0, "bullet": "bolt", "bullet_speed": 670.0,
		"burn": 18.0, "burn_time": 3.5, "armor_pierce": true, "targeting": "first",
	},

	# ۴. خانوادهٔ لیزر (Laser): سطح ۱ ضعیف تا سطح ۵ افسانه‌ای
	{
		"id": "laser_lv1", "family": "laser", "tier_level": 1, "tier_name": "ضعیف",
		"name": "عدسی لیزر (ضعیف)", "desc": "پرتو آبی متمرکز که زره دشمن را می‌شکافد.",
		"cost": 135, "star_cost": 2, "damage": 22.0, "range": 178.0, "cooldown": 0.78,
		"kind": "single", "shots": 1, "bullet": "spark", "bullet_speed": 950.0,
		"armor_pierce": true, "targeting": "strong",
	},
	{
		"id": "laser_lv2", "family": "laser", "tier_level": 2, "tier_name": "معمولی",
		"name": "لیزر متمرکز (معمولی)", "desc": "هستهٔ انرژی آبی با پرتو درخشان و ذرات انرژی.",
		"cost": 195, "star_cost": 6, "damage": 35.0, "range": 198.0, "cooldown": 0.68,
		"kind": "single", "shots": 1, "bullet": "spark", "bullet_speed": 1000.0,
		"armor_pierce": true, "targeting": "strong",
	},
	{
		"id": "laser_lv3", "family": "laser", "tier_level": 3, "tier_name": "پیشرفته",
		"name": "لیزر پلاسما (پیشرفته)", "desc": "خنک‌کننده‌های جانبی و پرتو پهن آبی با سوزاندن پلاسما.",
		"cost": 280, "star_cost": 10, "damage": 54.0, "range": 220.0, "cooldown": 0.58,
		"kind": "single", "shots": 1, "bullet": "spark", "bullet_speed": 1050.0,
		"armor_pierce": true, "burn": 10.0, "burn_time": 2.5, "targeting": "strong",
	},
	{
		"id": "laser_lv4", "family": "laser", "tier_level": 4, "tier_name": "حماسی",
		"name": "منشور آرکین (حماسی)", "desc": "پرتو لیزر آبی-فیروزه‌ای با نفوذ کامل در زره و سپر.",
		"cost": 390, "star_cost": 16, "damage": 82.0, "range": 246.0, "cooldown": 0.48,
		"kind": "single", "shots": 2, "bullet": "spark", "bullet_speed": 1100.0,
		"armor_pierce": true, "burn": 16.0, "burn_time": 3.0, "targeting": "strong",
	},
	{
		"id": "laser_lv5", "family": "laser", "tier_level": 5, "tier_name": "افسانه‌ای",
		"name": "چشم خورشید (افسانه‌ای)", "desc": "ابَرپرتو کریستالی آبی-سفید با ذرات پلاسما و آسیب ویرانگر.",
		"cost": 540, "star_cost": 23, "damage": 128.0, "range": 276.0, "cooldown": 0.40,
		"kind": "single", "shots": 2, "bullet": "spark", "bullet_speed": 1200.0,
		"armor_pierce": true, "burn": 26.0, "burn_time": 3.5, "targeting": "strong",
	},

	# ۵. خانوادهٔ تسلا (Tesla): سطح ۱ ضعیف تا سطح ۵ افسانه‌ای
	{
		"id": "tesla_lv1", "family": "tesla", "tier_level": 1, "tier_name": "ضعیف",
		"name": "سیم‌پیچ جرقه (ضعیف)", "desc": "صاعقهٔ بنفش که میان ۲ دشمن زنجیره می‌شود.",
		"cost": 145, "star_cost": 3, "damage": 18.0, "range": 156.0, "cooldown": 1.05,
		"kind": "chain", "shots": 1, "chain": 2, "chain_range": 84.0,
		"bullet": "spark", "bullet_speed": 900.0, "slow": 0.20, "slow_time": 1.2, "targeting": "first",
	},
	{
		"id": "tesla_lv2", "family": "tesla", "tier_level": 2, "tier_name": "معمولی",
		"name": "برجک تسلا (معمولی)", "desc": "قوس الکتریکی بنفش میان ۳ دشمن پیاپی.",
		"cost": 210, "star_cost": 7, "damage": 28.0, "range": 172.0, "cooldown": 0.94,
		"kind": "chain", "shots": 1, "chain": 3, "chain_range": 96.0,
		"bullet": "spark", "bullet_speed": 920.0, "slow": 0.28, "slow_time": 1.5, "targeting": "first",
	},
	{
		"id": "tesla_lv3", "family": "tesla", "tier_level": 3, "tier_name": "پیشرفته",
		"name": "تسلای خازن‌دار (پیشرفته)", "desc": "سیم‌پیچ سه‌حلقه با صاعقهٔ زنجیره‌ای ۴ هدفه و شوک کندکننده.",
		"cost": 295, "star_cost": 11, "damage": 42.0, "range": 190.0, "cooldown": 0.84,
		"kind": "chain", "shots": 1, "chain": 4, "chain_range": 108.0,
		"bullet": "spark", "bullet_speed": 960.0, "slow": 0.36, "slow_time": 1.8, "targeting": "first",
	},
	{
		"id": "tesla_lv4", "family": "tesla", "tier_level": 4, "tier_name": "حماسی",
		"name": "برج صاعقهٔ آرکین (حماسی)", "desc": "گوی پلاسمای بنفش؛ ۵ دشمن را با رعد زنجیره‌ای می‌زند.",
		"cost": 410, "star_cost": 17, "damage": 64.0, "range": 210.0, "cooldown": 0.74,
		"kind": "chain", "shots": 1, "chain": 5, "chain_range": 122.0,
		"bullet": "spark", "bullet_speed": 1000.0, "slow": 0.45, "slow_time": 2.2,
		"armor_pierce": true, "targeting": "first",
	},
	{
		"id": "tesla_lv5", "family": "tesla", "tier_level": 5, "tier_name": "افسانه‌ای",
		"name": "تاج تندر (افسانه‌ای)", "desc": "طوفان صاعقه‌های بنفش-طلایی میان ۶ دشمن با فلج الکتریکی.",
		"cost": 560, "star_cost": 24, "damage": 98.0, "range": 236.0, "cooldown": 0.64,
		"kind": "chain", "shots": 1, "chain": 6, "chain_range": 140.0,
		"bullet": "spark", "bullet_speed": 1080.0, "slow": 0.58, "slow_time": 2.6,
		"armor_pierce": true, "targeting": "first",
	},

	# ۶. ابرسلاح ویژهٔ خرید درون‌برنامه‌ای (۱۰ برابر قوی‌ترین سلاح بازی = 10 × 160 = 1600 آسیب)
	{
		"id": "omega", "family": "omega", "tier_level": 6, "tier_name": "ایزدی",
		"name": "ابرسلاح ایزدی سیمرغ",
		"desc": "تاج کیهانی دژبان با ۱۰ برابر قدرت قوی‌ترین سلاح بازی؛ هم‌زمان ابرپرتو کیهانی، شهاب‌سنگ‌های آتشین و رعد زنجیره‌ای ۸ هدفه شلیک می‌کند.",
		"cost": 650, "star_cost": 0,
		"iap_only": true, "iap_price_toman": 100000, "iap_price_text": "۱۰۰٬۰۰۰ تومان",
		"damage": 1600.0, "range": 360.0, "cooldown": 0.45,
		"kind": "splash", "shots": 4, "splash": 110.0, "chain": 8, "chain_range": 150.0,
		"bullet": "fire", "bullet_speed": 980.0,
		"armor_pierce": true, "burn": 100.0, "burn_time": 4.5, "slow": 0.65, "slow_time": 3.0,
		"targeting": "strong",
	},
]

const FREE_IDS := ["machine_gun_lv1", "cannon_lv1", "missile_lv1"]


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
