## واسط میدان نبرد: ردیف دکمه‌های بالا (سکه، جان، موج، سرعت، توقف، منو)، نوار پایین
## (انتخاب سلاح برای ساخت)، پنل برجک انتخاب‌شده (ارتقا و فروش) و لایه‌های
## پایان نبرد و توقف. این گره فقط نمایش می‌دهد؛ همهٔ کارها را Battle انجام می‌دهد.
extends Control

const RESULT_CARD_SIZE := Vector2(640.0, 360.0)

var battle = null

var _coins_label: Button = null
var _lives_label: Button = null
var _wave_label: Button = null
var _enemies_label: Button = null
var _speed_button: Button = null
var _countdown_label: Button = null
var _start_button: Button = null
var _hint_label: Label = null
var _banner: Label = null
var _toast_label: Label = null
var _weapon_panel: PanelContainer = null
var _weapon_bar: HBoxContainer = null
var _weapon_title: Label = null
var _weapon_buttons: Dictionary = {}
var _tower_panel: PanelContainer = null
var _tower_title: Label = null
var _tower_stats: Label = null
var _upgrade_button: Button = null
var _sell_button: Button = null
var _danger: ColorRect = null
var _pause_overlay: Control = null
var _result_overlay: Control = null
var _result_card: Button = null
var _result_image: TextureRect = null
var _result_title: Label = null
var _result_body: Label = null
var _result_stars: HBoxContainer = null
var _banner_tween: Tween = null
var _toast_tween: Tween = null
var _danger_tween: Tween = null

func _ready() -> void:
	set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	custom_minimum_size = Balance.WORLD_SIZE
	mouse_filter = Control.MOUSE_FILTER_IGNORE
	if Style.theme != null:
		theme = Style.theme
	_build_top_bar()
	_build_weapon_bar()
	_build_tower_panel()
	_build_center()
	_build_danger()
	_build_pause_overlay()
	_build_result_overlay()

# ------------------------------------------------------------------ ساخت اجزا

func _anchor(node: Control, preset: int) -> void:
	add_child(node)
	node.set_anchors_preset(preset)

func _build_top_bar() -> void:
	# ردیف بالا پس‌زمینه ندارد؛ هر آیتم خودش یک دکمه است. خود ردیف ورودی نمی‌گیرد
	# تا فاصلهٔ بین دکمه‌ها لمس را به نقشه بدهد.
	var row := Ui.row(8)
	row.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_anchor(row, Control.PRESET_TOP_WIDE)
	row.offset_left = 12
	row.offset_right = -12
	row.offset_top = 8
	row.offset_bottom = 46

	_coins_label = _info_button("icons/coin", Ui.fa(0))
	row.add_child(_coins_label)

	_lives_label = _info_button("icons/heart", Ui.fa(0))
	row.add_child(_lives_label)

	_wave_label = _info_button("icons/wave", "")
	row.add_child(_wave_label)

	_enemies_label = _info_button("", "")
	row.add_child(_enemies_label)

	var sp1 := Ui.spacer()
	sp1.mouse_filter = Control.MOUSE_FILTER_IGNORE
	row.add_child(sp1)

	_countdown_label = _info_button("", "")
	_countdown_label.visible = false
	row.add_child(_countdown_label)

	_start_button = Ui.button("شروع موج", 17, 38.0, "primary")
	_start_button.custom_minimum_size = Vector2(140, 38)
	_start_button.pressed.connect(_on_start_wave)
	row.add_child(_start_button)

	var sp2 := Ui.spacer()
	sp2.mouse_filter = Control.MOUSE_FILTER_IGNORE
	row.add_child(sp2)

	_speed_button = Ui.button("سرعت ۱×", 17, 38.0, "ghost")
	_speed_button.custom_minimum_size = Vector2(104, 38)
	_speed_button.pressed.connect(_on_speed)
	row.add_child(_speed_button)

	var pause_button := Ui.button("توقف", 17, 38.0, "ghost")
	pause_button.custom_minimum_size = Vector2(78, 38)
	pause_button.pressed.connect(_on_pause)
	row.add_child(pause_button)

	var exit_button := Ui.button("منو", 17, 38.0, "ghost")
	exit_button.custom_minimum_size = Vector2(78, 38)
	exit_button.pressed.connect(_request_exit)
	row.add_child(exit_button)

## دکمهٔ نمایشی برای اطلاعات نوار بالا (سکه، جان، موج...). لمس نمی‌گیرد و فقط نشان می‌دهد.
func _info_button(icon_key: String, text: String) -> Button:
	var node := Ui.button(text, 17, 38.0, "ghost")
	node.custom_minimum_size = Vector2(0, 38)
	node.mouse_filter = Control.MOUSE_FILTER_IGNORE
	if not icon_key.is_empty():
		node.icon = Assets.tex(icon_key)
		node.expand_icon = true
		node.add_theme_constant_override("icon_max_width", 22)
	return node

func _build_weapon_bar() -> void:
	_weapon_panel = PanelContainer.new()
	_weapon_panel.add_theme_stylebox_override("panel", Style.box(Color(Style.bg_panel.r, Style.bg_panel.g, Style.bg_panel.b, 0.92), 14, Style.gold_dark, 2, 8.0))
	_weapon_panel.layout_direction = Control.LAYOUT_DIRECTION_LTR
	_anchor(_weapon_panel, Control.PRESET_BOTTOM_LEFT)
	_weapon_panel.offset_left = 12
	_weapon_panel.offset_right = 660
	_weapon_panel.offset_top = -102
	_weapon_panel.offset_bottom = -8

	var row := Ui.row(8)
	row.layout_direction = Control.LAYOUT_DIRECTION_LTR
	_weapon_panel.add_child(row)

	var title := Ui.label("برجک‌های فعال", 16, Style.gold, HORIZONTAL_ALIGNMENT_CENTER)
	title.custom_minimum_size = Vector2(82, 0)
	title.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	row.add_child(title)
	_weapon_title = title

	var scroll := ScrollContainer.new()
	scroll.layout_direction = Control.LAYOUT_DIRECTION_LTR
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_AUTO
	scroll.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.custom_minimum_size = Vector2(0, 82)
	row.add_child(scroll)

	_weapon_bar = Ui.row(8)
	_weapon_bar.layout_direction = Control.LAYOUT_DIRECTION_LTR
	_weapon_bar.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	scroll.add_child(_weapon_bar)

	_hint_label = Ui.label("", 16, Style.text_dim, HORIZONTAL_ALIGNMENT_LEFT)
	_hint_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_anchor(_hint_label, Control.PRESET_BOTTOM_LEFT)
	_hint_label.offset_left = 16
	_hint_label.offset_right = 660
	_hint_label.offset_top = -132
	_hint_label.offset_bottom = -106

	_populate_weapon_buttons()

func _populate_weapon_buttons() -> void:
	if _weapon_bar == null:
		return
	for child in _weapon_bar.get_children():
		child.queue_free()
	_weapon_buttons.clear()
	for weapon in Weapons.LIST:
		var weapon_id := String(weapon["id"])
		if not Game.is_weapon_unlocked(weapon_id):
			continue
		var button := Ui.weapon_button(weapon)
		button.action_mode = BaseButton.ACTION_MODE_BUTTON_PRESS
		button.button_down.connect(_on_weapon_button_down.bind(weapon_id, button))
		button.gui_input.connect(_on_weapon_button_gui_input.bind(weapon_id, button))
		_weapon_bar.add_child(button)
		_weapon_buttons[weapon_id] = button
	# عرض پنل از اندازهٔ واقعی عنوان و دکمه‌ها (فاصلهٔ ۸ و حاشیهٔ ۲×۸) تا هر پنج برجک بدون اسکرول دیده شوند.
	var title_w := 82.0
	if _weapon_title != null:
		title_w = maxf(title_w, _weapon_title.get_combined_minimum_size().x)
	var bar_w := 0.0
	for button in _weapon_buttons.values():
		bar_w += maxf(108.0, (button as Control).get_combined_minimum_size().x) + 8.0
	bar_w = maxf(108.0, bar_w - 8.0)
	var desired_w := clampf(16.0 + title_w + 8.0 + bar_w + 6.0, 260.0, 800.0)
	if _weapon_panel != null:
		_weapon_panel.offset_left = 12
		_weapon_panel.offset_right = 12.0 + desired_w

func is_over_weapon_bar(world: Vector2) -> bool:
	if _weapon_panel == null or not _weapon_panel.visible:
		return false
	return _weapon_panel.get_global_rect().has_point(world)

func _build_tower_panel() -> void:
	_tower_panel = Ui.panel(Color(Style.bg_panel.r, Style.bg_panel.g, Style.bg_panel.b, 0.95), 16, 14.0)
	_tower_panel.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_anchor(_tower_panel, Control.PRESET_CENTER_RIGHT)
	_tower_panel.offset_left = -252
	_tower_panel.offset_right = -12
	_tower_panel.offset_top = -128
	_tower_panel.offset_bottom = 128
	_tower_panel.visible = false

	var column := Ui.column(8)
	column.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_tower_panel.add_child(column)

	_tower_title = Ui.label("", 22, Style.gold)
	column.add_child(_tower_title)

	_tower_stats = Ui.label("", 17, Style.text)
	_tower_stats.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	column.add_child(_tower_stats)

	column.add_child(Ui.hline())

	_upgrade_button = Ui.button("ارتقا", 18, 46.0, "primary")
	_upgrade_button.pressed.connect(_on_upgrade)
	column.add_child(_upgrade_button)

	_sell_button = Ui.button("فروش", 18, 44.0, "danger")
	_sell_button.pressed.connect(_on_sell)
	column.add_child(_sell_button)

	var keys := Ui.label("میان‌بر: U ارتقا · S فروش", 14, Style.text_dim, HORIZONTAL_ALIGNMENT_CENTER)
	column.add_child(keys)

## وسط میدان هیچ پنلی ندارد تا همهٔ زمین برای ساخت آزاد بماند؛ فقط نوشته‌ها
## آن‌جا نشان داده می‌شوند و ورودی نمی‌گیرند.
func _build_center() -> void:
	_banner = Ui.label("", 40, Style.gold, HORIZONTAL_ALIGNMENT_CENTER)
	_banner.add_theme_font_override("font", Style.font_bold)
	_banner.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_banner.visible = false
	_anchor(_banner, Control.PRESET_CENTER)
	_banner.offset_left = -320
	_banner.offset_right = 320
	_banner.offset_top = -60
	_banner.offset_bottom = 20

	_toast_label = Ui.label("", 20, Style.text, HORIZONTAL_ALIGNMENT_CENTER)
	_toast_label.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_toast_label.visible = false
	_anchor(_toast_label, Control.PRESET_CENTER_TOP)
	_toast_label.offset_left = -360
	_toast_label.offset_right = 360
	_toast_label.offset_top = 190
	_toast_label.offset_bottom = 224

func _build_danger() -> void:
	_danger = ColorRect.new()
	_danger.color = Color(Style.bad.r, Style.bad.g, Style.bad.b, 0.0)
	_danger.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_anchor(_danger, Control.PRESET_FULL_RECT)
	move_child(_danger, 0)

func _build_pause_overlay() -> void:
	_pause_overlay = _make_overlay("توقف نبرد")
	var column: VBoxContainer = _pause_overlay.get_meta("column")
	var resume := Ui.button("ادامه", 22, 54.0, "primary")
	resume.pressed.connect(_on_pause)
	column.add_child(resume)
	var restart := Ui.button("شروع دوباره", 20, 48.0, "ghost")
	restart.pressed.connect(func(): _on_restart())
	column.add_child(restart)
	var menu := Ui.button("بازگشت به اطلس نقشه‌ها", 20, 48.0, "ghost")
	menu.pressed.connect(_request_exit)
	column.add_child(menu)
	_pause_overlay.visible = false

func _build_result_overlay() -> void:
	_result_overlay = Control.new()
	_result_overlay.mouse_filter = Control.MOUSE_FILTER_STOP
	_anchor(_result_overlay, Control.PRESET_FULL_RECT)

	var dim := ColorRect.new()
	dim.color = Color(0.02, 0.05, 0.06, 0.65)
	dim.mouse_filter = Control.MOUSE_FILTER_STOP
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	_result_overlay.add_child(dim)

	# ظرف مرکزچین، کارت نتیجه را در اندازه‌های مختلف صفحه وسط نگه می‌دارد.
	var result_center := CenterContainer.new()
	result_center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	result_center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_result_overlay.add_child(result_center)

	_result_card = Button.new()
	_result_card.focus_mode = Control.FOCUS_NONE
	_result_card.mouse_default_cursor_shape = Control.CURSOR_POINTING_HAND
	_result_card.custom_minimum_size = RESULT_CARD_SIZE
	_result_card.pressed.connect(_on_result_card_pressed)
	result_center.add_child(_result_card)

	# تصویر پایان نبرد باید کل کادر را پر کند؛ لنگرهای کامل مانع از چسبیدن
	# آن به گوشهٔ بالا-چپ می‌شوند.
	var image_frame := Control.new()
	image_frame.mouse_filter = Control.MOUSE_FILTER_IGNORE
	image_frame.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	image_frame.offset_left = 4.0
	image_frame.offset_top = 4.0
	image_frame.offset_right = -4.0
	image_frame.offset_bottom = -4.0
	_result_card.add_child(image_frame)

	_result_image = TextureRect.new()
	_result_image.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_result_image.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_result_image.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	_result_image.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	_result_image.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	image_frame.add_child(_result_image)

	var top_bar := Ui.margin(18, 12, 18, 8)
	top_bar.mouse_filter = Control.MOUSE_FILTER_IGNORE
	top_bar.set_anchors_preset(Control.PRESET_TOP_WIDE)
	top_bar.offset_bottom = 96
	_result_card.add_child(top_bar)

	var top_col := Ui.column(4)
	top_col.mouse_filter = Control.MOUSE_FILTER_IGNORE
	top_col.alignment = BoxContainer.ALIGNMENT_CENTER
	top_bar.add_child(top_col)

	_result_title = Ui.title("", 38)
	_result_title.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_result_title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_result_title.add_theme_color_override("font_outline_color", Color(0.04, 0.06, 0.08, 0.95))
	_result_title.add_theme_constant_override("outline_size", 8)
	top_col.add_child(_result_title)

	_result_stars = Ui.row(6)
	_result_stars.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_result_stars.alignment = BoxContainer.ALIGNMENT_CENTER
	top_col.add_child(_result_stars)

	var footer := PanelContainer.new()
	footer.mouse_filter = Control.MOUSE_FILTER_IGNORE
	footer.add_theme_stylebox_override("panel", Style.box(Color(0.03, 0.06, 0.08, 0.78), 0, Color(0, 0, 0, 0), 0, 8.0))
	footer.set_anchors_preset(Control.PRESET_BOTTOM_WIDE)
	footer.offset_left = 4
	footer.offset_right = -4
	footer.offset_top = -54
	footer.offset_bottom = -4
	_result_card.add_child(footer)

	_result_body = Ui.label("", 17, Style.text, HORIZONTAL_ALIGNMENT_CENTER)
	_result_body.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_result_body.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	footer.add_child(_result_body)

	_result_overlay.visible = false

## لایهٔ تاریک تمام‌صفحه با پنل میانی؛ ستون و عنوان پنل در برچسب‌های گره ذخیره می‌شوند.
func _make_overlay(title_text: String) -> Control:
	var overlay := Control.new()
	overlay.mouse_filter = Control.MOUSE_FILTER_STOP
	_anchor(overlay, Control.PRESET_FULL_RECT)
	var dim := ColorRect.new()
	dim.color = Color(0.02, 0.05, 0.06, 0.72)
	dim.mouse_filter = Control.MOUSE_FILTER_STOP
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	overlay.add_child(dim)

	var panel := Ui.panel(Color(Style.bg_panel.r, Style.bg_panel.g, Style.bg_panel.b, 0.98), 18, 20.0)
	panel.set_anchors_preset(Control.PRESET_CENTER)
	panel.offset_left = -290
	panel.offset_right = 290
	panel.offset_top = -190
	panel.offset_bottom = 190
	overlay.add_child(panel)

	var column := Ui.column(12)
	column.alignment = BoxContainer.ALIGNMENT_CENTER
	panel.add_child(column)

	var title := Ui.title(title_text, 34)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	column.add_child(title)

	overlay.set_meta("column", column)
	overlay.set_meta("title", title)
	return overlay

# ------------------------------------------------------------------ به‌روزرسانی

func _process(_delta: float) -> void:
	refresh()

func refresh() -> void:
	if battle == null or not is_instance_valid(battle):
		return
	if _weapon_buttons.size() != Game.unlocked_weapons.size():
		_populate_weapon_buttons()
	_coins_label.text = Ui.fa(battle.coins)
	_lives_label.text = Ui.fa(battle.lives)
	_lives_label.add_theme_color_override("font_color", Style.bad if battle.lives <= 5 else Style.text)
	_wave_label.text = "موج %s از %s" % [Ui.fa(maxi(1, battle.wave_index)), Ui.fa(Balance.WAVES)]
	_enemies_label.text = "دشمنان در میدان: %s" % Ui.fa(battle.enemies.size())
	_speed_button.text = "سرعت %s×" % Ui.fa(int(Balance.SPEEDS[battle.speed_index]))

	var preparing: bool = battle.state == "prep"
	_start_button.visible = preparing and battle.running
	_start_button.disabled = not preparing
	if preparing and battle.running:
		_countdown_label.text = "موج بعدی در %s ثانیه" % Ui.fa(ceilf(maxf(0.0, battle.countdown)))
		_start_button.text = "شروع موج %s" % Ui.fa(battle.wave_index + 1)
	elif battle.state == "wave":
		_countdown_label.text = "موج %s در جریان است" % Ui.fa(battle.wave_index)
	else:
		_countdown_label.text = ""
	_countdown_label.visible = not _countdown_label.text.is_empty()

	if not battle.selected_weapon.is_empty():
		_hint_label.text = "سنگر را بگیر، تا جای مجاز بکش و رها کن · برای لغو، روی همین دکمه بزن"
	elif battle.selected_tower != null and is_instance_valid(battle.selected_tower):
		_hint_label.text = "میان‌بر: U ارتقا · S فروش"
	else:
		_hint_label.text = "برای ساخت، یک برجک را از نوار پایین بگیر و در نقشه رها کن"

	for id in _weapon_buttons.keys():
		var button: Button = _weapon_buttons[id]
		var weapon := Weapons.by_id(id)
		var affordable: bool = battle.coins >= int(weapon.get("cost", 0))
		var chosen: bool = battle.selected_weapon == id
		# فقط هنگام تغییر موجودی یا انتخاب، پوسته را بازسازی می‌کنیم.
		if not button.has_meta("affordable") or button.get_meta("affordable") != affordable or button.get_meta("chosen") != chosen:
			button.set_meta("affordable", affordable)
			button.set_meta("chosen", chosen)
			var border: Color = Style.gold if chosen else (Style.good if affordable else Style.line)
			var fill: Color = Color(Style.gold.r, Style.gold.g, Style.gold.b, 0.22) if chosen else (
				Color(Style.good.r, Style.good.g, Style.good.b, 0.17) if affordable else Style.bg_panel)
			button.add_theme_stylebox_override("normal", Style.box(fill, 12, border, 2 if chosen or affordable else 1, 0.0))
			button.add_theme_stylebox_override("hover", Style.box(fill.lightened(0.13), 12, border, 2, 0.0))
			var price := button.find_child("price", true, false) as Label
			if price != null:
				price.add_theme_color_override("font_color", Style.gold if affordable else Style.text_dim)

	var tower = battle.selected_tower
	if tower != null and is_instance_valid(tower):
		_tower_panel.visible = true
		var weapon: Dictionary = tower.weapon
		_tower_title.text = "%s — سطح %s" % [String(weapon.get("name", "")), Ui.fa(tower.level)]
		var lines := [
			"آسیب هر شلیک: %s" % Ui.fa(round(tower.damage())),
			"برد: %s" % Ui.fa(round(tower.attack_range())),
			"فاصلهٔ شلیک: %s ثانیه" % Ui.fa_decimal(tower.interval(), 2),
			String(weapon.get("desc", "")),
		]
		var effects: Array = []
		if weapon.has("burn"):
			effects.append("سوزاندن")
		if weapon.has("slow"):
			effects.append("کندی")
		if weapon.has("poison"):
			effects.append("زهر")
		if weapon.has("chain"):
			effects.append("زنجیره")
		if weapon.has("splash"):
			effects.append("ناحیه‌ای")
		if bool(weapon.get("armor_pierce", false)):
			effects.append("زره‌شکاف")
		if not effects.is_empty():
			lines.append("ویژگی: " + "، ".join(PackedStringArray(effects)))
		_tower_stats.text = "\n".join(PackedStringArray(lines))
		if tower.max_level():
			_upgrade_button.text = "بالاترین سطح"
			_upgrade_button.disabled = true
		else:
			var cost: int = tower.upgrade_cost()
			_upgrade_button.text = "ارتقا به سطح %s — %s سکه" % [Ui.fa(tower.level + 1), Ui.fa(cost)]
			_upgrade_button.disabled = battle.coins < cost
		_sell_button.text = "فروش — بازگشت %s سکه" % Ui.fa(tower.sell_value())
	else:
		_tower_panel.visible = false

func banner(text: String) -> void:
	_banner.text = text
	_banner.visible = true
	if _banner_tween != null and _banner_tween.is_valid():
		_banner_tween.kill()
	_banner.modulate = Color(1, 1, 1, 0)
	_banner.scale = Vector2(0.9, 0.9)
	_banner.pivot_offset = _banner.size * 0.5
	_banner_tween = _banner.create_tween()
	_banner_tween.set_parallel(true)
	_banner_tween.tween_property(_banner, "modulate:a", 1.0, 0.22)
	_banner_tween.tween_property(_banner, "scale", Vector2.ONE, 0.28).set_trans(Tween.TRANS_BACK).set_ease(Tween.EASE_OUT)
	_banner_tween.set_parallel(false)
	_banner_tween.tween_interval(1.4)
	_banner_tween.tween_property(_banner, "modulate:a", 0.0, 0.5)
	_banner_tween.tween_callback(func(): _banner.visible = false)

func toast(text: String) -> void:
	_toast_label.text = text
	_toast_label.visible = true
	if _toast_tween != null and _toast_tween.is_valid():
		_toast_tween.kill()
	_toast_label.modulate = Color(1, 1, 1, 0)
	_toast_tween = _toast_label.create_tween()
	_toast_tween.tween_property(_toast_label, "modulate:a", 1.0, 0.2)
	_toast_tween.tween_interval(1.6)
	_toast_tween.tween_property(_toast_label, "modulate:a", 0.0, 0.5)
	_toast_tween.tween_callback(func(): _toast_label.visible = false)

func flash_danger() -> void:
	if _danger == null:
		return
	if _danger_tween != null and _danger_tween.is_valid():
		_danger_tween.kill()
	_danger.color = Color(Style.bad.r, Style.bad.g, Style.bad.b, 0.3)
	_danger_tween = _danger.create_tween()
	_danger_tween.tween_property(_danger, "color:a", 0.0, 0.4)

func set_paused(value: bool) -> void:
	if _pause_overlay != null:
		_pause_overlay.visible = value

func show_result(won: bool, result: Dictionary, wave: int) -> void:
	_result_overlay.visible = true
	var bg_key := "ui/victory_background" if won else "ui/defeat_background"
	if _result_image != null:
		_result_image.texture = Assets.tex(bg_key)
	var border_color: Color = Style.gold if won else Style.bad
	if _result_card != null:
		_result_card.add_theme_stylebox_override("normal", Style.box(Style.bg_panel, 16, border_color, 3, 0.0))
		_result_card.add_theme_stylebox_override("hover", Style.box(Style.bg_panel, 16, border_color.lightened(0.22), 3, 0.0))
		_result_card.add_theme_stylebox_override("pressed", Style.box(Style.bg_panel, 16, border_color.darkened(0.15), 3, 0.0))
	_result_title.text = "پیروزی!" if won else "شکست!"
	_result_title.add_theme_color_override("font_color", Style.gold if won else Style.bad)
	var earned := int(result.get("stars", 0))
	var gained := int(result.get("gained", 0))
	_result_body.text = "موج %s از %s · ستاره‌ها: %s (+%s)  —  برای بازگشت به صفحهٔ نقشه‌ها کلیک کنید" % [
		Ui.fa(wave), Ui.fa(Balance.WAVES), Ui.fa(earned), Ui.fa(gained)
	]
	for child in _result_stars.get_children():
		child.queue_free()
	for i in earned:
		var star := Ui.tex("icons/star_small", 28.0)
		star.mouse_filter = Control.MOUSE_FILTER_IGNORE
		_result_stars.add_child(star)
	if _result_card != null:
		Ui.pop_in(_result_card)

func _on_result_card_pressed() -> void:
	_on_exit()

# ------------------------------------------------------------------ رویدادها

func _on_weapon_pressed(id: String) -> void:
	if battle != null:
		battle.begin_weapon_drag(id)

func _on_weapon_button_down(id: String, button: Control) -> void:
	if battle != null and is_instance_valid(battle):
		var start_world: Vector2 = button.get_global_rect().get_center()
		battle.begin_weapon_drag(id, start_world)

## امکان کشیدن مستقیم انگشت از روی دکمهٔ برجک در نوار پایین‌چپ و رهاکردن در نقشه
## توجه: در gui_input مختصات event.position محلیِ خود دکمه است و باید با get_global_transform_with_canvas به مختصات صفحه تبدیل شود.
func _on_weapon_button_gui_input(event: InputEvent, id: String, button: Control) -> void:
	if battle == null or not is_instance_valid(battle):
		return
	if event is InputEventScreenTouch:
		var screen_pos: Vector2 = button.get_global_transform_with_canvas() * event.position
		var world: Vector2 = battle.get_canvas_transform().affine_inverse() * screen_pos
		if event.pressed:
			battle.begin_weapon_drag(id, world)
		else:
			battle._finish_drag(world)
	elif event is InputEventScreenDrag:
		var screen_pos: Vector2 = button.get_global_transform_with_canvas() * event.position
		var world: Vector2 = battle.get_canvas_transform().affine_inverse() * screen_pos
		if battle.selected_weapon != id or not battle._drag_active:
			battle.begin_weapon_drag(id, world)
		else:
			battle._update_ghost(world)
	elif event is InputEventMouseMotion and (event.button_mask & MOUSE_BUTTON_MASK_LEFT) != 0:
		var screen_pos: Vector2 = button.get_global_transform_with_canvas() * event.position
		var world: Vector2 = battle.get_canvas_transform().affine_inverse() * screen_pos
		if battle.selected_weapon != id or not battle._drag_active:
			battle.begin_weapon_drag(id, world)
		else:
			battle._update_ghost(world)

func _on_start_wave() -> void:
	if battle != null:
		battle.start_wave()

func _on_speed() -> void:
	if battle != null:
		battle.cycle_speed()

func _on_pause() -> void:
	if battle != null:
		battle.toggle_pause()

func _on_upgrade() -> void:
	if battle != null:
		battle.upgrade_selected()

func _on_sell() -> void:
	if battle != null:
		battle.sell_selected()

## خروج از نبرد فقط بعد از تأیید کاربر انجام می‌شود. نبرد در حین پرسش متوقف می‌ماند.
func _request_exit() -> void:
	if battle == null or not is_instance_valid(battle):
		return
	Audio.ui_click()
	if not battle.paused:
		get_tree().paused = true
	Ui.confirm(self, "خروج از نبرد؟", "نبرد فعلی تمام می‌شود و به اطلس نقشه‌ها برمی‌گردی.", _on_exit, _resume_after_prompt)

func _resume_after_prompt() -> void:
	if battle != null and is_instance_valid(battle) and not battle.paused:
		get_tree().paused = false

func _on_exit() -> void:
	Audio.ui_back()
	if battle != null:
		battle.exit_to_map()

func _on_restart() -> void:
	Audio.ui_click()
	if battle != null:
		battle.replay()
