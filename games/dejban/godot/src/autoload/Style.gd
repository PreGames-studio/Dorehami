## ظاهر یکدست بازی: فونت فارسی وزیرمتن، رنگ‌ها و پوستهٔ واسط.
## همهٔ صفحه‌ها از همین تم و رنگ‌ها استفاده می‌کنند تا بازی یکپارچه دیده شود.
extends Node

var bg_deep := Color("#0b0f11")
var bg_panel := Color("#161c20")
var bg_panel_light := Color("#262e35")
var bg_hover := Color("#333d46")
var line := Color("#4a555e")
var gold := Color("#e2b04a")
var gold_dark := Color("#a97a2c")
var text := Color("#eef3f5")
var text_dim := Color("#9aa8b0")
var good := Color("#7bd48a")
var bad := Color("#e06a54")
var accent := Color("#3fb8ff")
## رنگ‌های فولاد برای دکمه‌های فلزی.
var steel := Color("#6b7781")
var steel_light := Color("#8e9aa4")
var steel_dark := Color("#3d464e")
var shadow := Color(0.0, 0.0, 0.0, 0.35)

var theme: Theme
var font_regular: FontFile
var font_latin: FontFile
var font_medium: FontFile
var font_bold: FontFile
var grabber: Texture2D

func _ready() -> void:
	_load_fonts()
	_build_theme()

func _load_fonts() -> void:
	font_regular = load("res://assets/fonts/Vazirmatn-FD-Regular.ttf")
	font_latin = load("res://assets/fonts/Vazirmatn-Regular.ttf")
	font_medium = load("res://assets/fonts/Vazirmatn-FD-Medium.ttf")
	font_bold = load("res://assets/fonts/Vazirmatn-FD-Bold.ttf")
	if font_regular == null:
		push_warning("Style: فونت وزیرمتن پیدا نشد؛ فونت پیش‌فرض موتور استفاده می‌شود.")

func box(bg: Color, radius := 12, border := Color(0, 0, 0, 0), border_width := 0, padding := 0.0) -> StyleBoxFlat:
	var style := StyleBoxFlat.new()
	style.bg_color = bg
	style.corner_radius_top_left = radius
	style.corner_radius_top_right = radius
	style.corner_radius_bottom_left = radius
	style.corner_radius_bottom_right = radius
	style.border_color = border
	style.border_width_left = border_width
	style.border_width_right = border_width
	style.border_width_top = border_width
	style.border_width_bottom = border_width
	if padding > 0.0:
		style.content_margin_left = padding
		style.content_margin_right = padding
		style.content_margin_top = padding
		style.content_margin_bottom = padding
	style.anti_aliasing = true
	return style

func _font_or(font: FontFile, fallback: FontFile) -> FontFile:
	return font if font != null else fallback

func _build_theme() -> void:
	theme = Theme.new()
	var regular := _font_or(font_regular, null)
	if regular != null:
		theme.default_font = regular
	theme.default_font_size = 20

	var base := _font_or(font_medium, regular)
	if base != null:
		theme.set_font("font", "Button", base)
		theme.set_font("font", "Label", regular)
		theme.set_font("font", "LineEdit", base)
		theme.set_font("font", "OptionButton", base)
		theme.set_font("font", "CheckBox", base)

	# --- دکمه‌ها
	theme.set_stylebox("normal", "Button", metal_box(steel, steel_light))
	theme.set_stylebox("hover", "Button", metal_box(steel_light, accent))
	theme.set_stylebox("pressed", "Button", metal_box(steel_dark, gold))
	theme.set_stylebox("disabled", "Button", metal_box(Color(0.3, 0.33, 0.36), line))
	theme.set_stylebox("focus", "Button", box(Color(0, 0, 0, 0), 12, accent, 1, 0.0))
	theme.set_color("font_color", "Button", text)
	theme.set_color("font_hover_color", "Button", Color.WHITE)
	theme.set_color("font_pressed_color", "Button", gold)
	theme.set_color("font_disabled_color", "Button", Color(text_dim.r, text_dim.g, text_dim.b, 0.6))
	theme.set_font_size("font_size", "Button", 20)

	# --- پنل‌ها
	theme.set_stylebox("panel", "PanelContainer", box(Color(bg_panel.r, bg_panel.g, bg_panel.b, 0.94), 16, line, 1, 14.0))
	theme.set_stylebox("panel", "Panel", box(Color(bg_panel.r, bg_panel.g, bg_panel.b, 0.94), 16, line, 1, 14.0))
	theme.set_stylebox("panel", "PopupPanel", box(bg_panel, 12, line, 1, 10.0))

	# --- برچسب‌ها
	theme.set_color("font_color", "Label", text)
	theme.set_font_size("font_size", "Label", 20)

	# --- کادر متن
	theme.set_stylebox("normal", "LineEdit", box(bg_panel, 10, line, 1, 10.0))
	theme.set_stylebox("focus", "LineEdit", box(bg_panel, 10, accent, 2, 10.0))
	theme.set_color("font_color", "LineEdit", text)
	theme.set_color("caret_color", "LineEdit", gold)
	theme.set_color("selection_color", "LineEdit", Color(accent.r, accent.g, accent.b, 0.35))
	theme.set_font_size("font_size", "LineEdit", 20)

	# --- نوار پیشرفت
	theme.set_stylebox("background", "ProgressBar", box(bg_deep, 8, line, 1, 0.0))
	theme.set_stylebox("fill", "ProgressBar", box(gold, 8, Color(0, 0, 0, 0), 0, 0.0))
	theme.set_color("font_color", "ProgressBar", text)

	# --- نوار کشویی
	theme.set_stylebox("slider", "HSlider", box(bg_deep, 8, line, 1, 0.0))
	theme.set_stylebox("grabber_area", "HSlider", box(Color(accent.r, accent.g, accent.b, 0.5), 8, Color(0, 0, 0, 0), 0, 0.0))
	theme.set_stylebox("grabber_area_highlight", "HSlider", box(Color(gold.r, gold.g, gold.b, 0.6), 8, Color(0, 0, 0, 0), 0, 0.0))
	grabber = _make_grabber()
	if grabber != null:
		theme.set_icon("grabber", "HSlider", grabber)
		theme.set_icon("grabber_highlight", "HSlider", grabber)

	# --- نوارهای اسکرول
	# حاشیهٔ نوار عمودی بزرگ‌تر است تا گرفتن و کشیدن آن با لمس یا ماوس راحت‌تر باشد.
	# اندازهٔ نوار افقی دست‌نخورده می‌ماند تا فضای نوار سلاح‌های نبرد کم نشود.
	var vertical_scroll_box := box(Color(bg_deep.r, bg_deep.g, bg_deep.b, 0.6), 8, Color(0, 0, 0, 0), 0, 12.0)
	var horizontal_scroll_box := box(Color(bg_deep.r, bg_deep.g, bg_deep.b, 0.6), 8, Color(0, 0, 0, 0), 0, 5.0)
	theme.set_stylebox("scroll", "VScrollBar", vertical_scroll_box)
	theme.set_stylebox("scroll", "HScrollBar", horizontal_scroll_box)
	theme.set_stylebox("grabber", "VScrollBar", box(Color(gold.r, gold.g, gold.b, 0.85), 8, Color(0, 0, 0, 0), 0, 12.0))
	theme.set_stylebox("grabber", "HScrollBar", box(Color(gold.r, gold.g, gold.b, 0.85), 8, Color(0, 0, 0, 0), 0, 5.0))
	theme.set_stylebox("grabber_highlight", "VScrollBar", box(gold, 8, Color(0, 0, 0, 0), 0, 12.0))
	theme.set_stylebox("grabber_highlight", "HScrollBar", box(gold, 8, Color(0, 0, 0, 0), 0, 5.0))

## دستگیرهٔ نوار کشویی را بدون فایل تصویری می‌سازد.
func _make_grabber() -> Texture2D:
	var size := 26
	var image := Image.create(size, size, false, Image.FORMAT_RGBA8)
	var center := Vector2(size / 2.0, size / 2.0)
	for y in size:
		for x in size:
			var d := Vector2(x + 0.5, y + 0.5).distance_to(center)
			if d <= 11.0:
				var edge := clampf((11.0 - d) / 2.0, 0.0, 1.0)
				var color := gold.lerp(Color.WHITE, 0.25)
				color.a = edge
				image.set_pixel(x, y, color)
			else:
				image.set_pixel(x, y, Color(0, 0, 0, 0))
	return ImageTexture.create_from_image(image)

# ------------------------------------------------------------------ بافت‌های تولیدی (بدون فایل تصویری)

var _metal_cache := {}
var _military_tex: Texture2D = null
var _wifi_tex: Texture2D = null

static func _hash(x: int, y: int) -> float:
	var n := (x * 374761393 + y * 668265263) & 0x7fffffff
	n = ((n ^ (n >> 13)) * 1274126177) & 0x7fffffff
	n = n ^ (n >> 16)
	return float(n & 0xffff) / 65535.0

## جعبهٔ فلزی گرد: سطح براق مایل به بالا، خطوط برس‌خورده و لبهٔ روشن.
func metal_box(tint: Color, rim: Color, radius := 14) -> StyleBoxTexture:
	var key := "%s|%s|%d" % [tint.to_html(), rim.to_html(), radius]
	if not _metal_cache.has(key):
		_metal_cache[key] = ImageTexture.create_from_image(_metal_image(tint, rim, 96, radius))
	var sb := StyleBoxTexture.new()
	sb.texture = _metal_cache[key]
	sb.texture_margin_left = radius + 2
	sb.texture_margin_right = radius + 2
	sb.texture_margin_top = radius + 2
	sb.texture_margin_bottom = radius + 2
	sb.content_margin_left = 14.0
	sb.content_margin_right = 14.0
	sb.content_margin_top = 6.0
	sb.content_margin_bottom = 6.0
	return sb

func _metal_image(tint: Color, rim: Color, size: int, radius: int) -> Image:
	var img := Image.create(size, size, false, Image.FORMAT_RGBA8)
	var r := float(radius)
	for y in size:
		for x in size:
			var px := x + 0.5
			var py := y + 0.5
			var cx := clampf(px, r, size - r)
			var cy := clampf(py, r, size - r)
			var d := Vector2(px, py).distance_to(Vector2(cx, cy)) - r
			var alpha := clampf(0.5 - d, 0.0, 1.0)
			if alpha <= 0.0:
				img.set_pixel(x, y, Color(0, 0, 0, 0))
				continue
			var t := float(y) / float(size)
			var shade := 1.3 - 0.6 * t
			var streak := (_hash(0, y) - 0.5) * 0.08 + (_hash(x / 6, y) - 0.5) * 0.05
			var c := tint * (shade + streak)
			var inside := -d
			var rim_t := clampf(1.0 - inside / 2.5, 0.0, 1.0) * 0.9
			c = c.lerp(rim, rim_t)
			c.a = alpha
			img.set_pixel(x, y, c)
	return img

## بافت پس‌زمینهٔ نظامی: زمینهٔ تیره، لکه‌های کم‌نور کاموفلاژ، درزهای صفحات و پیچ‌ها.
func military_texture() -> Texture2D:
	if _military_tex == null:
		var s := 256
		var img := Image.create(s, s, false, Image.FORMAT_RGBA8)
		var base := Color("#14191c")
		var olive := Color("#1c2519")
		var seam := Color("#060809")
		var rivet := Color("#4f5960")
		for y in s:
			for x in s:
				var v := _hash((x / 32) % 8, (y / 32) % 8) * 0.6 + _hash((x / 16) % 16 + 50, (y / 16) % 16) * 0.4
				var c := base.lerp(olive, clampf((v - 0.45) * 3.0, 0.0, 1.0))
				var mx := x % 128
				var my := y % 128
				if mx < 2 or my < 2:
					c = seam
				if (mx >= 6 and mx <= 9 and my >= 6 and my <= 9) or (mx >= 118 and mx <= 121 and my >= 118 and my <= 121):
					c = rivet
				img.set_pixel(x, y, c)
		_military_tex = ImageTexture.create_from_image(img)
	return _military_tex

## آیکون وای‌فای با سه کمان و نقطه، با لبهٔ نرم.
func wifi_texture() -> Texture2D:
	if _wifi_tex == null:
		var s := 64
		var img := Image.create(s, s, false, Image.FORMAT_RGBA8)
		var origin := Vector2(32.0, 54.0)
		var color := Color("#dff3ff")
		for y in s:
			for x in s:
				var p := Vector2(x + 0.5, y + 0.5) - origin
				var dist := p.length()
				var ang := atan2(p.y, p.x)
				var a := 0.0
				if dist < 6.0:
					a = clampf(6.0 - dist + 0.5, 0.0, 1.0)
				elif ang >= -3.0 * PI / 4.0 and ang <= -PI / 4.0:
					for radius in [16.0, 28.0, 40.0]:
						var band := clampf(4.5 - absf(dist - radius) + 0.5, 0.0, 1.0)
						a = maxf(a, band)
				img.set_pixel(x, y, Color(color.r, color.g, color.b, a))
		_wifi_tex = ImageTexture.create_from_image(img)
	return _wifi_tex
