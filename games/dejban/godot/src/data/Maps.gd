## دوازده نقشهٔ بازی با پس‌زمینه‌های دست‌کشیدهٔ نمای بالا (تم‌های چمن/برف/کویر)،
## مسیر دقیق وسط جاده (همه پیچ‌های ۹۰ درجه) و سکوهای پیشنهادی کنار جاده (pads).
## مسیرها و سکوها با tools/extract_map_routes.py از خود تصویرهای نقشه استخراج شده‌اند
## (tools/art_source/maps_v2/routes_extracted.json → tools/apply_map_routes.py)؛
## جهت مسیر با نشانگر قرمز/آبیز روی تصویر تطبیق داده شده است.
## ساخت‌وساز آزاد است: بازیکن هر جای زمین بیرون از محدودهٔ جاده می‌تواند برج بگذارد
## (GameMap.build_issue) و pads فقط دادهٔ پیشنهادی‌اند.
## ترتیب این فهرست همان ترتیب نمایش در اطلس نقشه‌ها و همان شمارهٔ نقشه در ذخیرهٔ بازی
## است. هر خانه تصویر (bg)، مسیر و سکوهای خودش را یک‌جا دارد — این سه را هرگز از هم
## جدا نکنید — برای همین شمارهٔ خانه لزوماً با شمارهٔ تصویر یکی نیست: خانه‌های سوم و
## دهم (map_10 و map_03) با هم جابه‌جا شده‌اند.
class_name Maps
extends RefCounted
const BIOMES := {
	"meadow": {
		"title": "چمنزار",
		"ground": "ground/grass",
		"alt": "ground/grass_dark",
		"props": ["props/tree_round", "props/bush", "props/rock_small", "props/flower_1", "props/flower_2", "props/grass_tuft", "props/pond"],
		"blockers": ["props/tree_round", "props/pond"],
		"ambient": Color(1.0, 1.0, 1.0),
	},
	"forest": {
		"title": "جنگل",
		"ground": "ground/grass_dark",
		"alt": "ground/grass",
		"props": ["props/tree_pine", "props/tree_round", "props/bush", "props/rock_big", "props/rock_small", "props/grass_tuft"],
		"blockers": ["props/tree_pine", "props/tree_round", "props/rock_big"],
		"ambient": Color(1.0, 1.0, 1.0),
	},
	"desert": {
		"title": "بیابان",
		"ground": "ground/sand",
		"alt": "ground/dirt",
		"props": ["props/cactus", "props/tree_palm", "props/rock_big", "props/rock_small", "props/ruins", "props/tent"],
		"blockers": ["props/tree_palm", "props/rock_big", "props/ruins", "props/tent"],
		"ambient": Color(1.0, 1.0, 1.0),
	},
	"snow": {
		"title": "برفستان",
		"ground": "ground/snow",
		"alt": "ground/rock",
		"props": ["props/tree_pine", "props/rock_big", "props/rock_small", "props/statue", "props/barrel", "props/crate"],
		"blockers": ["props/tree_pine", "props/rock_big", "props/statue"],
		"ambient": Color(1.0, 1.0, 1.0),
	},
}

const LIST := [
	{
		"name": "پیچ اس چمنزار", "biome": "meadow", "bg": "maps/map_01",
		"seed": 101, "complexity": 3, "decor": 0.0, "entry": "left", "keep": Vector2i(15, 9),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		"waypoints": [
			Vector2(0.05, 2.06), Vector2(14.28, 2.06), Vector2(14.28, 5.25),
			Vector2(5.69, 5.25), Vector2(5.69, 7.74), Vector2(15.77, 7.74),
			Vector2(15.77, 9.94),
		],
		"pads": [
			Vector2(3.85, 3.62), Vector2(3.85, 6.10), Vector2(3.85, 9.37),
			Vector2(4.59, 4.28), Vector2(4.59, 8.71), Vector2(7.13, 3.97),
			Vector2(7.13, 9.02), Vector2(12.44, 3.62), Vector2(12.84, 0.77),
			Vector2(13.93, 9.37), Vector2(15.38, 1.08), Vector2(15.38, 6.22),
		],
	},
	{
		"name": "گذر زد سبز", "biome": "meadow", "bg": "maps/map_02",
		"seed": 102, "complexity": 2, "decor": 0.0, "entry": "bottom", "keep": Vector2i(0, 1),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		"waypoints": [
			Vector2(2.66, 9.88), Vector2(3.58, 9.88), Vector2(3.58, 5.29),
			Vector2(17.66, 5.29), Vector2(17.66, 2.19), Vector2(0.07, 2.19),
			Vector2(0.07, 1.67),
		],
		"pads": [
			Vector2(1.51, 0.91), Vector2(1.51, 3.48), Vector2(1.74, 6.93),
			Vector2(1.74, 8.24), Vector2(2.48, 4.32), Vector2(5.02, 4.01),
			Vector2(5.02, 6.57), Vector2(5.02, 8.59), Vector2(15.82, 0.56),
			Vector2(15.82, 3.66), Vector2(15.82, 6.93), Vector2(18.75, 1.22),
		],
	},
	# خانهٔ دهم اطلس: تصویر map_03 با مسیر و سکوهایش اینجا نشسته (جابه‌جا با خانهٔ سوم).
	{
		"name": "درازپیچ کویر", "biome": "desert", "bg": "maps/map_10",
		"seed": 110, "complexity": 8, "decor": 0.0, "entry": "bottom", "keep": Vector2i(19, 8),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		"waypoints": [
			Vector2(0.17, 1.54), Vector2(18.12, 1.54), Vector2(18.12, 2.96),
			Vector2(1.86, 2.96), Vector2(1.86, 5.19), Vector2(18.12, 5.19),
			Vector2(18.12, 6.62), Vector2(1.86, 6.62), Vector2(1.86, 8.86),
			Vector2(19.81, 8.86),
		],
		"pads": [
			Vector2(19.22, 0.57), Vector2(19.22, 3.93),
		],
	},
	{
		"name": "مارپیچ چمنزار", "biome": "meadow", "bg": "maps/map_04",
		"seed": 104, "complexity": 4, "decor": 0.0, "entry": "bottom", "keep": Vector2i(11, 5),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		"waypoints": [
			Vector2(0.08, 0.42), Vector2(17.58, 0.42), Vector2(17.58, 9.46),
			Vector2(3.03, 9.46), Vector2(3.03, 2.83), Vector2(13.62, 2.83),
			Vector2(13.62, 7.33), Vector2(6.59, 7.33), Vector2(6.59, 4.85),
			Vector2(10.31, 4.85), Vector2(10.31, 5.43), Vector2(11.00, 5.43),
		],
		"pads": [
			Vector2(1.19, 4.47), Vector2(1.19, 7.82), Vector2(1.94, 1.86),
			Vector2(4.47, 4.12), Vector2(4.47, 8.18), Vector2(4.76, 5.70),
			Vector2(11.75, 4.15), Vector2(14.72, 1.86), Vector2(15.07, 4.12),
			Vector2(15.07, 6.05), Vector2(15.74, 2.06), Vector2(15.74, 7.82),
		],
	},
	{
		"name": "پلکان سبز", "biome": "meadow", "bg": "maps/map_05",
		"seed": 105, "complexity": 5, "decor": 0.0, "entry": "left", "keep": Vector2i(16, 0),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		"waypoints": [
			Vector2(0.06, 9.21), Vector2(4.67, 9.21), Vector2(4.67, 7.04),
			Vector2(8.64, 7.04), Vector2(8.64, 4.58), Vector2(12.59, 4.58),
			Vector2(12.59, 2.11), Vector2(16.55, 2.11), Vector2(16.55, 0.05),
		],
		"pads": [
			Vector2(2.83, 5.41), Vector2(2.83, 7.57), Vector2(3.58, 6.07),
			Vector2(6.12, 5.76), Vector2(6.12, 8.32), Vector2(6.80, 2.95),
			Vector2(7.20, 5.76), Vector2(7.20, 8.32), Vector2(7.55, 3.61),
			Vector2(9.73, 8.01), Vector2(10.08, 3.30), Vector2(10.08, 5.87),
		],
	},
	{
		"name": "دوخم برفی", "biome": "snow", "bg": "maps/map_06",
		"seed": 106, "complexity": 4, "decor": 0.0, "entry": "bottom", "keep": Vector2i(16, 9),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		# نقشهٔ نخست گوشهٔ کاملاً شکسته دارد (گردکردن پیچ‌ها خاموش).
		"waypoints": [
			Vector2(2.17, 0.07), Vector2(2.17, 7.33), Vector2(10.38, 7.33),
			Vector2(10.38, 1.69), Vector2(16.00, 1.69), Vector2(16.00, 9.89),
		],
		"pads": [
			Vector2(0.73, 6.05), Vector2(0.73, 8.62), Vector2(3.62, 6.05),
			Vector2(3.62, 8.62), Vector2(8.54, 3.33), Vector2(8.54, 5.70),
			Vector2(8.54, 8.97), Vector2(9.28, 0.72), Vector2(11.47, 8.31),
			Vector2(11.82, 2.98), Vector2(11.82, 6.05), Vector2(12.21, 8.97),
		],
	},
	{
		"name": "مار برفی", "biome": "snow", "bg": "maps/map_07",
		"seed": 107, "complexity": 6, "decor": 0.0, "entry": "left", "keep": Vector2i(0, 8),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		"waypoints": [
			Vector2(0.00, 2.39), Vector2(9.98, 2.39), Vector2(9.98, 3.56),
			Vector2(5.55, 3.56), Vector2(5.55, 5.26), Vector2(14.08, 5.26),
			Vector2(14.08, 6.61), Vector2(9.22, 6.61), Vector2(9.22, 8.10),
			Vector2(0.00, 8.10),
		],
		"pads": [
			Vector2(3.71, 5.19), Vector2(4.10, 3.97), Vector2(4.10, 6.54),
			Vector2(6.99, 6.54), Vector2(7.78, 9.38), Vector2(8.15, 0.76),
			Vector2(10.31, 9.07), Vector2(10.66, 7.89), Vector2(11.08, 1.42),
			Vector2(11.43, 2.27), Vector2(11.43, 3.67), Vector2(11.82, 0.76),
		],
	},
	{
		"name": "زیگزاگ کولاک", "biome": "snow", "bg": "maps/map_08",
		"seed": 108, "complexity": 6, "decor": 0.0, "entry": "bottom", "keep": Vector2i(19, 5),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		"waypoints": [
			Vector2(0.12, 5.04), Vector2(3.14, 5.04), Vector2(3.14, 3.03),
			Vector2(6.70, 3.03), Vector2(6.70, 7.19), Vector2(10.42, 7.19),
			Vector2(10.42, 3.03), Vector2(13.75, 3.03), Vector2(13.75, 7.12),
			Vector2(16.93, 7.12), Vector2(16.93, 5.00), Vector2(19.91, 5.00),
		],
		"pads": [
			Vector2(1.30, 1.39), Vector2(1.30, 3.41), Vector2(1.30, 6.68),
			Vector2(2.05, 2.06), Vector2(4.23, 6.01), Vector2(4.58, 1.74),
			Vector2(4.58, 4.31), Vector2(4.87, 8.83), Vector2(4.98, 6.68),
			Vector2(5.61, 8.17), Vector2(7.80, 2.06), Vector2(8.15, 4.31),
		],
	},
	{
		"name": "حلقهٔ شکستهٔ یخی", "biome": "snow", "bg": "maps/map_09",
		"seed": 109, "complexity": 3, "decor": 0.0, "entry": "top", "keep": Vector2i(7, 1),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		"waypoints": [
			Vector2(4.63, 0.02), Vector2(4.63, 8.56), Vector2(15.25, 8.56),
			Vector2(15.25, 1.46), Vector2(7.16, 1.46),
		],
		"pads": [
			Vector2(2.79, 6.92), Vector2(3.54, 9.53), Vector2(6.07, 7.27),
			Vector2(13.41, 3.09), Vector2(13.41, 6.92), Vector2(16.34, 0.49),
			Vector2(16.34, 9.53), Vector2(16.69, 2.74), Vector2(16.69, 7.27),
			Vector2(0.88, 1.00), Vector2(0.88, 1.83), Vector2(0.88, 2.67),
		],
	},
	# خانهٔ سوم اطلس: تصویر map_10 با مسیر و سکوهایش اینجا نشسته (جابه‌جا با خانهٔ دهم).
	{
		"name": "خم یو چمن", "biome": "meadow", "bg": "maps/map_03",
		"seed": 103, "complexity": 2, "decor": 0.0, "entry": "bottom", "keep": Vector2i(17, 0),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		"waypoints": [
			Vector2(2.59, 0.08), Vector2(3.59, 0.08), Vector2(3.59, 7.25),
			Vector2(16.41, 7.25), Vector2(16.41, 0.15), Vector2(17.42, 0.15),
		],
		"pads": [
			Vector2(1.76, 1.72), Vector2(1.76, 5.62), Vector2(1.76, 8.88),
			Vector2(2.50, 8.22), Vector2(5.04, 1.37), Vector2(5.04, 5.97),
			Vector2(5.04, 8.53), Vector2(14.57, 1.79), Vector2(14.57, 5.62),
			Vector2(14.57, 8.88), Vector2(17.50, 8.22), Vector2(17.85, 5.97),
		],
	},
	{
		"name": "هزارتوی شن", "biome": "desert", "bg": "maps/map_11",
		"seed": 111, "complexity": 8, "decor": 0.0, "entry": "bottom", "keep": Vector2i(18, 0),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		"waypoints": [
			Vector2(1.91, 9.83), Vector2(1.91, 2.07), Vector2(4.14, 2.07),
			Vector2(4.14, 7.74), Vector2(6.95, 7.74), Vector2(6.95, 2.07),
			Vector2(9.69, 2.07), Vector2(9.69, 5.49), Vector2(10.30, 5.49),
			Vector2(10.30, 7.74), Vector2(13.03, 7.74), Vector2(13.03, 2.07),
			Vector2(15.22, 2.07), Vector2(15.22, 6.54), Vector2(15.84, 6.54),
			Vector2(15.84, 7.74), Vector2(18.36, 7.74), Vector2(18.36, 0.13),
		],
		"pads": [
			Vector2(0.47, 0.79), Vector2(0.47, 3.35), Vector2(2.70, 0.79),
			Vector2(5.12, 9.38), Vector2(5.23, 1.10), Vector2(8.05, 8.72),
			Vector2(8.24, 0.79), Vector2(8.40, 6.46), Vector2(8.79, 9.38),
			Vector2(10.78, 1.10), Vector2(11.13, 3.35), Vector2(11.13, 4.20),
		],
	},
	{
		"name": "ساعت شنی", "biome": "desert", "bg": "maps/map_12",
		"seed": 112, "complexity": 4, "decor": 0.0, "entry": "bottom", "keep": Vector2i(19, 9),
		# جاده‌های جدید فقط پیچ ۹۰ درجه دارند؛ گردکردن گوشه‌ها خاموش.
		"square_corners": true,
		"waypoints": [
			Vector2(0.41, 0.76), Vector2(10.00, 0.76), Vector2(10.00, 4.92),
			Vector2(14.75, 4.92), Vector2(14.75, 9.15), Vector2(19.11, 9.15),
		],
		"pads": [
			Vector2(8.16, 2.39), Vector2(8.16, 3.28), Vector2(8.16, 6.55),
			Vector2(8.91, 5.89), Vector2(11.44, 2.04), Vector2(11.44, 3.63),
			Vector2(11.44, 6.20), Vector2(12.91, 3.28), Vector2(12.91, 6.55),
			Vector2(12.91, 7.52), Vector2(15.84, 3.94), Vector2(16.19, 6.20),
		],
	},
]

static func count() -> int:
	return LIST.size()

static func def(index: int) -> Dictionary:
	return LIST[clampi(index, 0, LIST.size() - 1)]
