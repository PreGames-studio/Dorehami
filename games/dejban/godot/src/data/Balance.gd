## تنظیمات عددی بازی در یک جا: اندازهٔ میدان، اقتصاد، موج‌ها، مقیاس سختی و فرمول‌ها.
## هر تغییری در حس بازی از همین‌جا شروع می‌شود.
class_name Balance
extends RefCounted

# --- میدان بازی (افقی ۱۲۸۰×۷۲۰ تمام‌صفحه)
const TILE := 64
const COLS := 20
const ROWS := 10
const TERRAIN_TOP := 0.0
const WORLD_SIZE := Vector2(1280, 720)
const TERRAIN_RECT := Rect2(0, 0, 1280, 720)
const HUD_HEIGHT := 54.0

# --- اقتصاد و پیشرفت
const START_COINS := 100
const START_LIVES := 20
const SELL_RATIO := 0.6
const MAX_LEVEL := 4

# --- ارتقای سلاح در زرادخانه (با ستاره)
## سقف ارتقای هر سلاح. سطح k با k ستاره خریده می‌شود (جمعاً ۴۶۵ ستاره برای سقف).
const ARMORY_MAX_LEVEL := 30
## افزایش هر سطح: ۱ واحد ثابت قدرت شلیک، ۲٪ سرعت شلیک و ۲٪ برد. چون هزینهٔ هر
## سطح یک ستاره بیشتر از قبلی است، سطح‌های نخست ارزان و محسوس‌اند و سقف ۳۰ سطح
## برای تکمیل کامل همهٔ نقشه‌ها می‌ماند؛ نه آسان‌شونده و نه ناچیز.
const ARMORY_DAMAGE_PER_LEVEL := 1.0
const ARMORY_STEP := 0.02

# --- آیتم «افزایش اعتبار» (PRESTIGE BOOST): هر سطح ۱۰ سکه به سکهٔ شروع هر نقشه اضافه می‌کند.
const PRESTIGE_MAX_LEVEL := 50
const PRESTIGE_COINS_PER_LEVEL := 10

# --- موج‌ها
const WAVES := 30
const MILESTONE_WAVES := [10, 20, 30]
const FIRST_WAVE_DELAY := 14.0
const WAVE_DELAY := 7.0
const SPEEDS := [1.0, 2.0, 3.0]

# --- ساخت‌وساز
const ROUTE_CLEARANCE := 46.0
const KEEP_CLEARANCE := 76.0
const TOWER_RADIUS := 22.0
const TOWER_MIN_GAP := 54.0
## گام شبکهٔ ساخت؛ برجک‌ها روی ضریب‌های این عدد می‌نشینند تا چیدمان مرتب باشد.
const BUILD_SNAP := 32.0
## نسبت فاصلهٔ محور چرخش سرِ برجک از بالای تصویر.
const TOWER_PIVOT := 0.18

## ضریب جان دشمن بر پایهٔ شمارهٔ نقشه و موج.
static func enemy_hp_scale(map_index: int, wave: int) -> float:
	return (1.0 + 0.16 * map_index) * (1.0 + 0.10 * max(0, wave - 1))

## ضریب سرعت دشمن؛ افزایش ملایم تا بازی در نقشه‌های آخر بی‌رحم نشود.
static func enemy_speed_scale(map_index: int, wave: int) -> float:
	return (1.0 + 0.010 * map_index) * (1.0 + 0.005 * max(0, wave - 1))

## پاداش سکهٔ هر کشتن؛ محدود و کنترل‌شده تا ارتقا و خرید برجک چالش‌برانگیز بماند.
static func enemy_reward(reward: int, wave: int) -> int:
	return maxi(1, int(round(reward * (1.0 + 0.005 * max(0, wave - 1)))))

## تعداد دشمنان یک موج.
static func wave_count(wave: int) -> int:
	return int(round(6 + wave * 1.3))

## هزینهٔ هر ارتقا داخل نبرد برابر هزینهٔ کاشت همان برجک است.
static func upgrade_cost(base_cost: int, _level: int) -> int:
	return base_cost

## افزایش آسیب در هر سطح ارتقا (به ازای هر سطح، این کسر از آسیب پایه اضافه می‌شود).
## سطح ۱ = ضریب ۱، سطح ۴ = ۱٫۷۵ برابر پایه.
const DAMAGE_STEP_PER_LEVEL := 0.25

static func level_damage_mult(level: int) -> float:
	return 1.0 + DAMAGE_STEP_PER_LEVEL * float(clampi(level, 1, MAX_LEVEL) - 1)

static func level_range_mult(level: int) -> float:
	return 1.0 + 0.055 * (level - 1)

static func level_rate_mult(level: int) -> float:
	return pow(0.93, level - 1)

## ستاره‌های پایان نقشه: ستارهٔ موج‌های شاخص + پاداش پیروزی.
static func stars_for(map_index: int, reached_wave: int, won: bool) -> int:
	var stars := 0
	for wave in MILESTONE_WAVES:
		if reached_wave >= wave:
			stars += 1
	if won and map_index >= 20:
		stars += 1
	return stars
