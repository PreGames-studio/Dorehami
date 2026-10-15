## کارخانهٔ واسط: ساخت دکمه، پنل، برچسب و ردیف‌های آماده با ظاهر یکدست،
## به‌همراه تبدیل عددها به رقم‌های فارسی.
extends Node

const PERSIAN_DIGITS := ["۰", "۱", "۲", "۳", "۴", "۵", "۶", "۷", "۸", "۹"]

# ------------------------------------------------------------------ عدد فارسی

func fa(value) -> String:
	var number := float(value)
	var negative := number < 0.0
	var text := str(int(round(absf(number))))
	var grouped := ""
	var counter := 0
	for i in range(text.length() - 1, -1, -1):
		grouped = text[i] + grouped
		counter += 1
		if counter % 3 == 0 and i > 0:
			grouped = "٬" + grouped
	var out := ""
	for i in grouped.length():
		var ch := grouped[i]
		if ch >= "0" and ch <= "9":
			out += PERSIAN_DIGITS[int(ch)]
		else:
			out += ch
	return ("−" if negative else "") + out

func percent(value: float) -> String:
	return fa(round(value * 100.0)) + "٪"

## عدد اعشاری با ممیز فارسی: ۰٫۶۲
func fa_decimal(value: float, digits := 1) -> String:
	var factor := pow(10.0, digits)
	var rounded: float = round(value * factor) / factor
	var parts := String.num(rounded, digits).split(".")
	var out := fa(int(parts[0]))
	if parts.size() > 1:
		var decimals := ""
		var text := parts[1]
		for i in text.length():
			var ch := text[i]
			decimals += PERSIAN_DIGITS[int(ch)] if (ch >= "0" and ch <= "9") else ch
		out += "٫" + decimals
	return out

# ------------------------------------------------------------------ چیدمان

func row(separation := 10) -> HBoxContainer:
	var box := HBoxContainer.new()
	box.add_theme_constant_override("separation", separation)
	return box

func column(separation := 10) -> VBoxContainer:
	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", separation)
	return box

func grid(columns := 4, separation := 12) -> GridContainer:
	var box := GridContainer.new()
	box.columns = columns
	box.add_theme_constant_override("h_separation", separation)
	box.add_theme_constant_override("v_separation", separation)
	return box

func spacer(expand := true, size := 0.0) -> Control:
	var node := Control.new()
	if expand:
		node.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	if size > 0.0:
		node.custom_minimum_size = Vector2(size, 0)
	return node

func margin(left: int, top: int, right: int, bottom: int) -> MarginContainer:
	var node := MarginContainer.new()
	node.add_theme_constant_override("margin_left", left)
	node.add_theme_constant_override("margin_top", top)
	node.add_theme_constant_override("margin_right", right)
	node.add_theme_constant_override("margin_bottom", bottom)
	return node

# ------------------------------------------------------------------ اجزا

## فونت را فقط وقتی روی گره می‌گذارد که واقعاً بارگذاری شده باشد.
func apply_font(node: Control, font, size: int) -> void:
	if font != null:
		node.add_theme_font_override("font", font)
	node.add_theme_font_size_override("font_size", size)

func label(text: String, size := 20, color := Color.TRANSPARENT, align := HORIZONTAL_ALIGNMENT_RIGHT) -> Label:
	var node := Label.new()
	node.text = text
	apply_font(node, Style.font_regular, size)
	node.add_theme_color_override("font_color", color if color.a > 0.0 else Style.text)
	node.horizontal_alignment = align
	node.autowrap_mode = TextServer.AUTOWRAP_OFF
	return node

func title(text: String, size := 34) -> Label:
	var node := label(text, size, Style.gold)
	apply_font(node, Style.font_bold, size)
	return node

func dim(text: String, size := 16) -> Label:
	return label(text, size, Style.text_dim)

func panel(bg := Color.TRANSPARENT, radius := 16, padding := 14.0) -> PanelContainer:
	var node := PanelContainer.new()
	node.add_theme_stylebox_override("panel", Style.box(bg if bg.a > 0.0 else Style.bg_panel, radius, Style.line, 1, padding))
	return node

func flat_panel(bg: Color, radius := 12, padding := 10.0) -> PanelContainer:
	var node := PanelContainer.new()
	node.add_theme_stylebox_override("panel", Style.box(bg, radius, Color(0, 0, 0, 0), 0, padding))
	return node

func button(text: String, size := 20, height := 52.0, variant := "normal") -> Button:
	var node := Button.new()
	node.text = text
	node.custom_minimum_size = Vector2(0, height)
	node.focus_mode = Control.FOCUS_NONE
	node.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	apply_font(node, Style.font_medium, size)
	if variant == "primary":
		node.add_theme_stylebox_override("normal", Style.metal_box(Style.gold_dark, Style.gold))
		node.add_theme_stylebox_override("hover", Style.metal_box(Style.gold, Color.WHITE))
		node.add_theme_stylebox_override("pressed", Style.metal_box(Style.gold_dark.darkened(0.3), Style.gold))
		node.add_theme_color_override("font_color", Color("#12232a"))
		node.add_theme_color_override("font_hover_color", Color("#12232a"))
		node.add_theme_color_override("font_pressed_color", Color("#12232a"))
		node.add_theme_color_override("font_disabled_color", Color(0.2, 0.25, 0.25, 0.7))
		node.add_theme_stylebox_override("disabled", Style.box(Style.gold.lerp(Style.bg_panel, 0.65), 12, Style.line, 1, 12.0))
	elif variant == "danger":
		node.add_theme_stylebox_override("normal", Style.metal_box(Style.bad.darkened(0.55), Style.bad))
		node.add_theme_color_override("font_color", Style.bad.lerp(Color.WHITE, 0.35))
		node.add_theme_stylebox_override("hover", Style.metal_box(Style.bad.darkened(0.35), Style.bad.lerp(Color.WHITE, 0.3)))
	elif variant == "online":
		node.add_theme_stylebox_override("normal", Style.metal_box(Style.steel, Style.accent))
		node.add_theme_stylebox_override("hover", Style.metal_box(Style.steel_light, Color.WHITE))
		node.add_theme_stylebox_override("pressed", Style.metal_box(Style.steel_dark, Style.accent))
		node.add_theme_color_override("font_color", Color.WHITE)
		node.add_theme_color_override("font_hover_color", Color.WHITE)
	elif variant == "ghost":
		node.add_theme_stylebox_override("normal", Style.box(Color(0, 0, 0, 0), 12, Style.line, 1, 12.0))
		node.add_theme_stylebox_override("hover", Style.box(Color(1, 1, 1, 0.06), 12, Style.accent, 1, 12.0))
	return node

func icon_button(texture: Texture2D, size := 46.0, variant := "normal") -> Button:
	var node := button("", 20, size, variant)
	node.custom_minimum_size = Vector2(size, size)
	node.icon = texture
	node.expand_icon = true
	node.add_theme_constant_override("icon_max_width", int(size * 0.62))
	return node

func tex(key: String, size := 34.0, keep_aspect := true) -> TextureRect:
	var node := TextureRect.new()
	node.texture = Assets.tex(key)
	node.custom_minimum_size = Vector2(size, size)
	node.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	node.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED if keep_aspect else TextureRect.STRETCH_SCALE
	node.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	return node

func sprite(tex_key: String, size: Vector2) -> TextureRect:
	var node := tex(tex_key, size.x)
	node.custom_minimum_size = size
	return node

## برچسب با آیکون کنارش، مثل «۱۲ سکه» یا «موج ۳ از ۳۰».
func icon_label(icon_key: String, text: String, size := 20, icon_size := 26.0, color := Color.TRANSPARENT) -> HBoxContainer:
	var node := row(6)
	node.add_child(tex(icon_key, icon_size))
	var text_node := label(text, size, color)
	text_node.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	node.add_child(text_node)
	return node

func stars_row(count: int, total := 0, size := 22.0) -> HBoxContainer:
	var node := row(3)
	var filled := Assets.tex("icons/star_small")
	for i in count:
		var star := TextureRect.new()
		star.texture = filled
		star.custom_minimum_size = Vector2(size, size)
		star.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		star.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		node.add_child(star)
	for i in maxi(0, total - count):
		var star := TextureRect.new()
		star.texture = filled
		star.modulate = Color(1, 1, 1, 0.22)
		star.custom_minimum_size = Vector2(size, size)
		star.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		star.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		node.add_child(star)
	return node

func hline() -> Panel:
	var node := Panel.new()
	node.custom_minimum_size = Vector2(0, 1)
	var style := StyleBoxFlat.new()
	style.bg_color = Style.line
	node.add_theme_stylebox_override("panel", style)
	return node

## هاله‌ی نور آبی یا رنگی دور یک دکمه (برای دکمهٔ برجسته، مثل بازی گروهی).
func glow_frame(child: Control, color: Color) -> PanelContainer:
	var frame := PanelContainer.new()
	var glow := Style.box(Color(0, 0, 0, 0), 18, Color(0, 0, 0, 0), 0, 12.0)
	glow.shadow_color = Color(color.r, color.g, color.b, 0.7)
	glow.shadow_size = 18
	frame.add_theme_stylebox_override("panel", glow)
	frame.add_child(child)
	return frame

## آیکون وای‌فای برای دکمه‌ها و نشانه‌ها.
func wifi_icon(size := 34.0) -> TextureRect:
	var node := TextureRect.new()
	node.texture = Style.wifi_texture()
	node.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	node.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	node.custom_minimum_size = Vector2(size, size)
	node.mouse_filter = Control.MOUSE_FILTER_IGNORE
	return node

## قیمت کنار آیکون؛ حالت قابل‌خرید را Hud بر اساس سکه‌های جاری رنگ می‌کند.
func weapon_button(weapon: Dictionary) -> Button:
	var node := button("", 18, 78.0, "ghost")
	node.custom_minimum_size = Vector2(108, 78)
	var content := column(2)
	content.alignment = BoxContainer.ALIGNMENT_CENTER
	content.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var icon_and_price := row(3)
	icon_and_price.mouse_filter = Control.MOUSE_FILTER_IGNORE
	icon_and_price.alignment = BoxContainer.ALIGNMENT_CENTER
	content.add_child(icon_and_price)
	# در نوار نبرد خودِ تصویر برجک را نشان می‌دهیم، نه آیکون مدالیِ برگ‌دار.
	var icon := tex("towers/head_%s" % weapon["id"], 46.0)
	icon.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	icon.mouse_filter = Control.MOUSE_FILTER_IGNORE
	icon_and_price.add_child(icon)
	var price := label(fa(int(weapon["cost"])), 17, Style.gold, HORIZONTAL_ALIGNMENT_CENTER)
	price.mouse_filter = Control.MOUSE_FILTER_IGNORE
	price.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	price.name = "price"
	icon_and_price.add_child(price)
	var name_label := label(String(weapon["name"]), 14, Style.text, HORIZONTAL_ALIGNMENT_CENTER)
	name_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	name_label.clip_text = true
	content.add_child(name_label)
	var box := margin(4, 4, 4, 4)
	box.mouse_filter = Control.MOUSE_FILTER_IGNORE
	box.set_anchors_preset(Control.PRESET_FULL_RECT)
	box.add_child(content)
	node.add_child(box)
	node.tooltip_text = "%s سکه برای ساخت" % fa(int(weapon["cost"]))
	return node

## پس‌زمینهٔ تمام‌صفحهٔ منو؛ اگر مسیر تصویر ندهیم از بافت نظامی پیش‌فرض استفاده می‌شود.
func backdrop(parent: Node, with_keep := true, background_path := "") -> Control:
	var layer := Control.new()
	layer.set_anchors_preset(Control.PRESET_FULL_RECT)
	layer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var background := TextureRect.new()
	background.texture = Style.military_texture()
	background.set_anchors_preset(Control.PRESET_FULL_RECT)
	background.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	background.stretch_mode = TextureRect.STRETCH_TILE
	if not background_path.is_empty():
		var custom_background := load(background_path) as Texture2D
		if custom_background != null:
			background.texture = custom_background
			background.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	background.mouse_filter = Control.MOUSE_FILTER_IGNORE
	layer.add_child(background)
	if with_keep:
		var keep := TextureRect.new()
		keep.texture = Assets.tex("structures/keep")
		keep.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		keep.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		keep.custom_minimum_size = Vector2(430, 380)
		keep.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
		keep.position = Vector2(-215, 96)
		keep.mouse_filter = Control.MOUSE_FILTER_IGNORE
		keep.modulate = Color(1, 1, 1, 0.18)
		layer.add_child(keep)
	parent.add_child(layer)
	return layer

func fade_in(node: CanvasItem, duration := 0.25) -> void:
	node.modulate = Color(1, 1, 1, 0)
	var tween := node.create_tween()
	tween.tween_property(node, "modulate:a", 1.0, duration)

func pop_in(node: Control, duration := 0.22) -> void:
	node.pivot_offset = node.size * 0.5
	node.scale = Vector2(0.92, 0.92)
	node.modulate = Color(1, 1, 1, 0)
	var tween := node.create_tween()
	tween.set_parallel(true)
	tween.tween_property(node, "scale", Vector2.ONE, duration).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	tween.tween_property(node, "modulate:a", 1.0, duration)

## پیوند میان‌بر: دکمه‌ای که صدا و گذر به صحنهٔ دیگر را هم انجام می‌دهد.
func goto_scene(path: String) -> void:
	Audio.ui_click()
	get_tree().change_scene_to_file(path)
