## تعریف پانزده نقشهٔ بازی با پس‌زمینه‌های اختصاصی نمای بالا (Top-Down)،
## مسیر دقیق وسط جاده (از پایین‌چپ به بالا‌راست) و مختصات دقیق سکوهای بتنی کنار جاده (pads).
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
	# مسیر و سکوهای نقشه‌ها از روی جادهٔ تصویر خودشان استخراج شده‌اند
	# (tools/extract_map_routes.py → tools/apply_map_routes.py).
	# ۱: جنگل الوارها — جای این نقشه با نقشهٔ ۱ عوض شد تا نقشهٔ آسان‌تر (۶)
	# اول بیاید؛ سختی با شمارهٔ نقشه بالا می‌رود.
	{
		"name": "جنگل الوارها", "biome": "forest", "bg": "maps/map_06",
		"seed": 201, "complexity": 4, "decor": 0.0, "entry": "left", "keep": Vector2i(19, 1),
		# نقشهٔ نخست گوشهٔ کاملاً شکسته دارد (گردکردن پیچ‌ها خاموش).
		"square_corners": true,
		"waypoints": [
			Vector2(0.02, 7.76), Vector2(16.83, 7.76), Vector2(16.83, 4.68),
			Vector2(5.39, 4.68), Vector2(5.39, 1.68), Vector2(19.78, 1.68),
		],
		"pads": [
			Vector2(3.55, 3.05), Vector2(3.55, 6.31), Vector2(4.30, 0.71),
			Vector2(4.30, 5.65), Vector2(6.83, 2.96), Vector2(6.83, 5.96),
			Vector2(14.99, 3.05), Vector2(14.99, 6.13), Vector2(14.99, 9.40),
			Vector2(17.92, 3.71), Vector2(17.92, 8.74), Vector2(18.27, 5.96),
		],
	},
	# ۲ تا ۵: چمنزار (Meadow)
	{
		"name": "پیچ دشت سبز", "biome": "meadow", "bg": "maps/map_02",
		"seed": 102, "complexity": 3, "decor": 0.0, "entry": "left", "keep": Vector2i(13, 0),
		"waypoints": [
			Vector2(6.31, 9.97), Vector2(6.31, 7.67), Vector2(9.02, 7.67),
			Vector2(9.02, 5.00), Vector2(10.63, 5.00), Vector2(10.63, 2.35),
			Vector2(13.64, 2.35), Vector2(13.64, 0.00),
		],
		"pads": [
			Vector2(4.87, 6.38), Vector2(4.87, 8.95), Vector2(7.18, 3.36),
			Vector2(7.18, 6.03), Vector2(7.76, 8.95), Vector2(7.93, 4.02),
			Vector2(8.79, 0.71), Vector2(8.79, 3.36), Vector2(9.53, 1.38),
			Vector2(10.12, 8.64), Vector2(10.47, 6.28), Vector2(10.86, 9.30),
		],
	},
	{
		"name": "گذرگاه سنگربندی", "biome": "meadow", "bg": "maps/map_03",
		"seed": 103, "complexity": 3, "decor": 0.0, "entry": "left", "keep": Vector2i(13, 1),
		"waypoints": [
			Vector2(6.86, 9.86), Vector2(6.86, 3.08), Vector2(9.89, 3.08),
			Vector2(9.89, 6.89), Vector2(12.58, 6.89), Vector2(12.58, 1.37),
			Vector2(13.71, 1.37),
		],
		"pads": [
			Vector2(5.02, 1.45), Vector2(5.02, 4.72), Vector2(5.77, 2.11),
			Vector2(8.05, 1.45), Vector2(8.30, 4.37), Vector2(8.45, 5.61),
			Vector2(8.45, 8.17), Vector2(10.74, 8.52), Vector2(10.98, 2.11),
			Vector2(11.73, 8.52), Vector2(13.67, 7.86), Vector2(14.02, 5.61),
		],
	},
	{
		"name": "پیچ‌وخم چمنزار", "biome": "meadow", "bg": "maps/map_04",
		"seed": 104, "complexity": 4, "decor": 0.0, "entry": "left", "keep": Vector2i(13, 0),
		"waypoints": [
			Vector2(5.89, 9.99), Vector2(5.89, 7.11), Vector2(11.09, 7.11),
			Vector2(11.09, 5.36), Vector2(8.89, 5.36), Vector2(8.89, 2.98),
			Vector2(13.64, 2.98), Vector2(13.64, 0.19),
		],
		"pads": [
			Vector2(4.80, 6.14), Vector2(7.05, 1.34), Vector2(7.05, 3.72),
			Vector2(7.05, 4.61), Vector2(7.33, 5.83), Vector2(7.33, 8.40),
			Vector2(7.80, 2.01), Vector2(9.26, 8.75), Vector2(10.33, 1.69),
			Vector2(11.80, 1.34), Vector2(12.19, 4.39), Vector2(12.19, 8.09),
		],
	},
	{
		"name": "دشت مارپیچ", "biome": "meadow", "bg": "maps/map_05",
		"seed": 105, "complexity": 4, "decor": 0.0, "entry": "left", "keep": Vector2i(4, 5),
		"waypoints": [
			Vector2(5.41, 8.76), Vector2(14.36, 8.76), Vector2(14.36, 1.39),
			Vector2(12.31, 1.39), Vector2(12.31, 4.93), Vector2(4.32, 4.93),
			Vector2(4.32, 5.82),
		],
		"pads": [
			Vector2(5.76, 3.65), Vector2(6.16, 6.56), Vector2(10.47, 3.02),
			Vector2(10.47, 6.56), Vector2(11.22, 0.42), Vector2(12.52, 7.13),
			Vector2(5.02, 1.00), Vector2(5.02, 1.83), Vector2(5.02, 2.67),
			Vector2(5.95, 1.00), Vector2(5.95, 1.83), Vector2(5.95, 2.67),
		],
	},
	# ۶: چمنزار سپیده — پیش‌تر نقشهٔ ۱ بود و جایش با نقشهٔ ۶ عوض شد
	{
		"name": "چمنزار سپیده", "biome": "meadow", "bg": "maps/map_01",
		"seed": 101, "complexity": 3, "decor": 0.0, "entry": "left", "keep": Vector2i(19, 0),
		"square_corners": true,
		"waypoints": [
			Vector2(0.02, 9.12), Vector2(6.33, 9.12), Vector2(6.33, 4.31),
			Vector2(12.84, 4.31), Vector2(12.84, 0.85), Vector2(19.97, 0.85),
		],
		"pads": [
			Vector2(4.49, 2.67), Vector2(4.49, 5.94), Vector2(4.49, 7.49),
			Vector2(5.23, 3.33), Vector2(7.77, 3.02), Vector2(7.77, 5.59),
			Vector2(7.77, 7.84), Vector2(11.01, 2.48), Vector2(11.01, 5.94),
			Vector2(13.94, 5.28), Vector2(14.29, 2.13), Vector2(14.29, 3.02),
		],
	},
	# ۷ تا ۱۰: جنگل (Forest)
	{
		"name": "کورهه‌های سنگی جنگل", "biome": "forest", "bg": "maps/map_07",
		"seed": 202, "complexity": 4, "decor": 0.0, "entry": "left", "keep": Vector2i(15, 1),
		"waypoints": [
			Vector2(6.30, 9.72), Vector2(6.30, 7.00), Vector2(11.86, 7.00),
			Vector2(11.86, 4.81), Vector2(8.56, 4.81), Vector2(8.56, 3.32),
			Vector2(13.98, 3.32), Vector2(13.98, 1.39), Vector2(15.63, 1.39),
		],
		"pads": [
			Vector2(4.85, 5.72), Vector2(4.85, 8.29), Vector2(6.72, 1.69),
			Vector2(6.72, 3.18), Vector2(6.72, 4.95), Vector2(7.46, 2.35),
			Vector2(7.74, 8.29), Vector2(10.00, 2.04), Vector2(10.02, 8.64),
			Vector2(12.15, 1.69), Vector2(12.89, 0.42), Vector2(12.95, 7.98),
		],
	},
	{
		"name": "خم بزرگ صنوبر", "biome": "forest", "bg": "maps/map_08",
		"seed": 203, "complexity": 4, "decor": 0.0, "entry": "left", "keep": Vector2i(15, 1),
		"waypoints": [
			Vector2(5.06, 7.03), Vector2(5.06, 7.60), Vector2(11.98, 7.60),
			Vector2(11.98, 4.65), Vector2(7.61, 4.65), Vector2(7.61, 1.32),
			Vector2(15.37, 1.32),
		],
		"pads": [
			Vector2(5.77, 2.95), Vector2(6.17, 5.94), Vector2(6.90, 9.23),
			Vector2(9.05, 3.37), Vector2(9.05, 5.94), Vector2(10.14, 3.02),
			Vector2(10.14, 5.96), Vector2(10.53, 8.88), Vector2(13.07, 3.68),
			Vector2(13.07, 8.57), Vector2(13.81, 3.02), Vector2(13.81, 5.96),
		],
	},
	{
		"name": "هزارتوی سبز", "biome": "forest", "bg": "maps/map_09",
		"seed": 204, "complexity": 5, "decor": 0.0, "entry": "left", "keep": Vector2i(19, 1),
		"waypoints": [
			Vector2(0.28, 8.81), Vector2(5.34, 8.81), Vector2(5.34, 7.88),
			Vector2(9.52, 7.88), Vector2(9.52, 3.69), Vector2(14.67, 3.69),
			Vector2(14.67, 1.12), Vector2(19.51, 1.12),
		],
		"pads": [
			Vector2(3.51, 6.24), Vector2(3.51, 7.17), Vector2(6.79, 6.59),
			Vector2(6.79, 9.16), Vector2(7.68, 2.06), Vector2(7.68, 5.33),
			Vector2(7.68, 6.24), Vector2(7.68, 9.51), Vector2(8.42, 2.72),
			Vector2(10.61, 8.85), Vector2(10.96, 2.41), Vector2(10.96, 4.98),
		],
	},
	{
		"name": "بزرگراه جنگلی", "biome": "forest", "bg": "maps/map_10",
		"seed": 205, "complexity": 4, "decor": 0.0, "entry": "left", "keep": Vector2i(19, 1),
		"waypoints": [
			Vector2(0.85, 7.97), Vector2(0.85, 8.58), Vector2(3.98, 8.58),
			Vector2(3.98, 3.54), Vector2(10.02, 3.54), Vector2(10.02, 8.58),
			Vector2(11.45, 8.58), Vector2(11.45, 6.38), Vector2(15.88, 6.38),
			Vector2(15.88, 1.35), Vector2(19.97, 1.35),
		],
		"pads": [
			Vector2(2.15, 1.91), Vector2(2.15, 5.18), Vector2(2.15, 6.95),
			Vector2(2.89, 2.57), Vector2(5.08, 9.56), Vector2(5.43, 2.26),
			Vector2(5.43, 4.82), Vector2(5.43, 7.30), Vector2(8.18, 5.18),
			Vector2(8.18, 6.95), Vector2(8.57, 2.26), Vector2(8.92, 9.56),
		],
	},
	# ۱۱ و ۱۲: بیابان (Desert)
	{
		"name": "پیچ‌وخم کویر", "biome": "desert", "bg": "maps/map_11",
		"seed": 301, "complexity": 5, "decor": 0.0, "entry": "left", "keep": Vector2i(18, 1),
		"waypoints": [
			Vector2(0.70, 8.90), Vector2(6.67, 8.90), Vector2(6.67, 6.75),
			Vector2(9.19, 6.75), Vector2(9.19, 4.67), Vector2(11.59, 4.67),
			Vector2(11.59, 2.51), Vector2(13.67, 2.51), Vector2(13.67, 3.24),
			Vector2(15.80, 3.24), Vector2(15.80, 1.08), Vector2(18.84, 1.08),
		],
		"pads": [
			Vector2(4.83, 5.12), Vector2(4.83, 7.27), Vector2(5.58, 5.78),
			Vector2(7.35, 3.03), Vector2(7.35, 5.12), Vector2(8.09, 3.69),
			Vector2(8.12, 8.03), Vector2(9.76, 0.88), Vector2(9.76, 3.03),
			Vector2(10.28, 7.72), Vector2(10.50, 1.54), Vector2(10.63, 5.95),
		],
	},
	{
		"name": "درهٔ سرخ کویر", "biome": "desert", "bg": "maps/map_12",
		"seed": 302, "complexity": 4, "decor": 0.0, "entry": "left", "keep": Vector2i(19, 1),
		"waypoints": [
			Vector2(3.23, -0.01), Vector2(3.23, 8.03), Vector2(5.33, 8.03),
			Vector2(5.33, 2.52), Vector2(7.19, 2.52), Vector2(7.19, 8.03),
			Vector2(9.02, 8.03), Vector2(9.02, 2.53), Vector2(10.95, 2.53),
			Vector2(10.95, 8.03), Vector2(12.81, 8.03), Vector2(12.81, 2.53),
			Vector2(14.62, 2.53), Vector2(14.62, 8.04), Vector2(16.77, 8.04),
			Vector2(16.77, 1.19), Vector2(19.94, 1.19),
		],
		"pads": [
			Vector2(1.40, 6.39), Vector2(1.79, 9.31), Vector2(3.88, 9.31),
			Vector2(5.35, 0.89), Vector2(5.74, 9.31), Vector2(6.77, 1.24),
			Vector2(6.77, 9.31), Vector2(8.63, 1.24), Vector2(8.63, 9.31),
			Vector2(10.46, 1.24), Vector2(10.46, 9.31), Vector2(11.37, 1.24),
		],
	},
	# ۱۳ تا ۱۵: برفستان (Snow)
	{
		"name": "گردنهٔ برفی", "biome": "snow", "bg": "maps/map_13",
		"seed": 401, "complexity": 3, "decor": 0.0, "entry": "left", "keep": Vector2i(17, 1),
		"waypoints": [
			Vector2(1.2, 9.7), Vector2(2.3, 8.9), Vector2(2.9, 7.8), Vector2(3.4, 6.6),
			Vector2(4.1, 5.7), Vector2(5.0, 5.3), Vector2(6.0, 5.4), Vector2(7.0, 6.0),
			Vector2(7.9, 6.6), Vector2(8.9, 6.8), Vector2(9.8, 6.5), Vector2(10.5, 5.7),
			Vector2(11.0, 4.6), Vector2(11.5, 3.6), Vector2(12.3, 3.1), Vector2(13.4, 3.1),
			Vector2(14.6, 3.2), Vector2(15.6, 2.9), Vector2(16.4, 2.1), Vector2(17.3, 0.6),
		],
		"pads": [
			Vector2(2.3, 6.3), Vector2(5.3, 4.0), Vector2(9.9, 2.4), Vector2(11.6, 7.7),
			Vector2(15.8, 4.4), Vector2(17.6, 6.8), Vector2(5.3, 7.1), Vector2(8.2, 5.0),
			Vector2(12.8, 5.0), Vector2(13.2, 1.6), Vector2(15.0, 1.5), Vector2(9.2, 8.2),
		],
	},
	{
		"name": "قلعهٔ یخ‌زده", "biome": "snow", "bg": "maps/map_14",
		"seed": 402, "complexity": 4, "decor": 0.0, "entry": "left", "keep": Vector2i(18, 1),
		"waypoints": [
			Vector2(0.3, 8.2), Vector2(3.0, 8.2), Vector2(5.1, 8.2), Vector2(5.8, 7.9),
			Vector2(6.0, 7.2), Vector2(6.0, 5.5), Vector2(6.0, 4.1), Vector2(6.3, 3.6),
			Vector2(7.0, 3.5), Vector2(9.2, 3.5), Vector2(11.4, 3.5), Vector2(12.0, 3.7),
			Vector2(12.2, 4.3), Vector2(12.2, 6.4), Vector2(12.5, 6.9), Vector2(13.2, 7.0),
			Vector2(13.8, 7.0), Vector2(14.4, 6.8), Vector2(14.6, 6.2), Vector2(14.6, 3.8),
			Vector2(14.6, 1.8), Vector2(14.9, 1.3), Vector2(15.6, 1.2), Vector2(18.2, 1.2),
		],
		"pads": [
			Vector2(3.4, 7.0), Vector2(3.5, 9.2), Vector2(6.8, 9.0), Vector2(4.8, 6.1),
			Vector2(4.7, 3.9), Vector2(7.3, 6.1), Vector2(7.3, 4.7), Vector2(9.3, 4.7),
			Vector2(11.0, 4.8), Vector2(11.0, 6.2), Vector2(7.2, 2.3), Vector2(9.2, 2.3),
			Vector2(11.3, 2.3), Vector2(13.4, 4.1), Vector2(13.4, 5.7), Vector2(12.4, 7.9),
			Vector2(14.4, 7.9), Vector2(16.0, 6.6), Vector2(16.1, 4.7), Vector2(16.0, 2.3),
			Vector2(13.4, 1.9), Vector2(16.8, 0.3),
		],
	},
	{
		"name": "تنگهٔ کولاک", "biome": "snow", "bg": "maps/map_15",
		"seed": 403, "complexity": 4, "decor": 0.0, "entry": "left", "keep": Vector2i(17, 1),
		"waypoints": [
			Vector2(1.6, 9.7), Vector2(2.7, 8.5), Vector2(3.5, 7.3), Vector2(4.2, 6.5),
			Vector2(5.2, 6.2), Vector2(6.4, 6.1), Vector2(7.3, 5.7), Vector2(7.8, 4.8),
			Vector2(8.2, 3.6), Vector2(8.8, 2.8), Vector2(9.6, 2.5), Vector2(10.4, 2.8),
			Vector2(11.0, 3.7), Vector2(11.5, 4.9), Vector2(12.1, 5.9), Vector2(12.9, 6.3),
			Vector2(13.8, 6.2), Vector2(14.6, 5.6), Vector2(15.2, 4.5), Vector2(15.7, 3.0),
			Vector2(16.3, 1.6), Vector2(17.2, 0.5),
		],
		"pads": [
			Vector2(6.4, 4.8), Vector2(9.4, 1.4), Vector2(11.5, 1.9), Vector2(9.6, 4.0),
			Vector2(12.8, 4.5), Vector2(14.7, 2.6), Vector2(6.0, 7.3), Vector2(10.5, 7.5),
			Vector2(14.3, 7.3), Vector2(16.4, 5.5), Vector2(3.6, 5.2), Vector2(4.3, 8.1),
		],
	},
]

static func count() -> int:
	return LIST.size()

static func def(index: int) -> Dictionary:
	return LIST[clampi(index, 0, LIST.size() - 1)]
