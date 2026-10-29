## زرادخانه (اسلحه‌ها):
## کارت اول صفحه آیتم «افزایش اعتبار» (PRESTIGE BOOST) است که در هر سطح ارتقا ۱۰ سکه به سکهٔ شروع
## هر نقشه اضافه می‌کند، و پس از آن تمام اسلحه‌ها به ترتیب با همان ظاهر کارت نظامی-طلایی (با عکس اسلحه
## جای ستاره در مرکز مدال بال‌دار طلایی) قرار دارند.
extends Control

const PRESTIGE_ID := "prestige_boost"

var _stars_label: Label = null
var _list: GridContainer = null
var _overlay: Control = null
var _panel: PanelContainer = null
var _detail: VBoxContainer = null
var _selected_id := ""

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	if Style.theme != null:
		theme = Style.theme
	_build()
	Audio.play_music("music_menu")
	Ui.fade_in(self, 0.25)

func _build() -> void:
	Ui.backdrop(self, false, "res://assets/sprites/ui/armory_background.png")
	var root := Ui.margin(22, 16, 22, 16)
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(root)
	var column := Ui.column(12)
	root.add_child(column)
	var top := Ui.row(12)
	column.add_child(top)
	var back := Ui.button("بازگشت", 20, 48.0, "ghost")
	back.custom_minimum_size = Vector2(130, 48)
	back.pressed.connect(func(): Ui.goto_scene("res://scenes/MainMenu.tscn"))
	top.add_child(back)
	top.add_child(Ui.title("اسلحه‌ها", 32))
	top.add_child(Ui.spacer())
	var stars := Ui.icon_label("icons/star_small", "", 22, 28.0)
	_stars_label = stars.get_child(1) as Label
	top.add_child(stars)
	var hint := Ui.label("کارت «افزایش اعتبار» در ابتدای فهرست، سکهٔ شروع هر نقشه را افزایش می‌دهد؛ برای ارتقا کارت را لمس کنید", 17, Style.text_dim)
	column.add_child(hint)
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_ALWAYS
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	column.add_child(scroll)
	_list = Ui.grid(3, 14)
	_list.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(_list)
	_refresh_cards()
	_build_overlay()

func _refresh_cards() -> void:
	for child in _list.get_children():
		_list.remove_child(child)
		child.queue_free()
	# ۱) آیتم «افزایش اعتبار» (PRESTIGE BOOST) در ابتدای صفحه
	_list.add_child(_prestige_card())
	# ۲) تمامی اسلحه‌ها به ترتیب با همان ظاهر کارت ارتقا (عکس اسلحه جای ستاره داخل مدال)
	for weapon in Weapons.LIST:
		_list.add_child(_weapon_card(weapon))
	_stars_label.text = "%s ستاره" % Ui.fa(Game.stars)


## ساخت کارت مربعی به سبک PRESTIGE BOOST (قاب فلزی سرمه‌ای و طلایی + مدال بال‌دار بالا + نوار پیشرفت + دکمهٔ طلایی پایین).
func _make_styled_card_shell(
	card_id: String,
	is_prestige: bool,
	weapon_id: String,
	title_text: String,
	level_text: String,
	progress_ratio: float,
	bonus_text: String,
	cost_text: String,
	action_text: String,
	unlocked: bool
) -> Button:
	var card := Button.new()
	card.text = ""
	card.flat = true
	card.custom_minimum_size = Vector2(380, 380)
	card.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	var empty_sb := StyleBoxEmpty.new()
	for st_name in ["normal", "hover", "pressed", "focus", "disabled"]:
		card.add_theme_stylebox_override(st_name, empty_sb)
	card.pressed.connect(_open_detail.bind(card_id))

	var stage := Control.new()
	stage.custom_minimum_size = Vector2(380, 380)
	stage.set_anchors_preset(Control.PRESET_CENTER)
	stage.offset_left = -190
	stage.offset_right = 190
	stage.offset_top = -190
	stage.offset_bottom = 190
	stage.mouse_filter = Control.MOUSE_FILTER_IGNORE
	card.add_child(stage)

	# تصویر پس‌زمینهٔ کارت (مدال ستاره‌دار برای افزایش اعتبار، و مدال آمادهٔ عکس اسلحه برای اسلحه‌ها)
	var bg := TextureRect.new()
	bg.texture = Assets.tex("ui/card_prestige" if is_prestige else "ui/card_weapon_base")
	bg.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	bg.stretch_mode = TextureRect.STRETCH_SCALE
	bg.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	if not unlocked:
		bg.modulate = Color(0.82, 0.85, 0.90, 1.0)
	stage.add_child(bg)

	# کارت اسلحه‌ها قاب مدالِ برگ‌دارِ تصویر پس‌زمینه را می‌پوشاند تا تصویر خود برجک
	# بزرگ و بدون تزئینات اضافه دیده شود. کارت افزایش اعتبار همان نشان ستاره‌دارش را حفظ می‌کند.
	if not is_prestige:
		var weapon_display := Panel.new()
		weapon_display.position = Vector2(98.0, 18.0)
		weapon_display.size = Vector2(184.0, 128.0)
		weapon_display.mouse_filter = Control.MOUSE_FILTER_IGNORE
		var display_style := Style.box(Color("#0b1830"), 16, Color("#d6ad50"), 2, 0.0)
		display_style.shadow_color = Color(0.08, 0.48, 0.9, 0.24)
		display_style.shadow_size = 8
		weapon_display.add_theme_stylebox_override("panel", display_style)
		if not unlocked:
			weapon_display.modulate = Color(0.82, 0.85, 0.90, 1.0)
		stage.add_child(weapon_display)

		var turret_tex := TextureRect.new()
		turret_tex.texture = Weapons.icon(weapon_id)
		turret_tex.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
		turret_tex.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
		turret_tex.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
		turret_tex.position = Vector2(112.0, 25.0)
		turret_tex.size = Vector2(156.0, 114.0)
		if not unlocked:
			turret_tex.modulate = Color(0.65, 0.70, 0.76, 1.0)
		stage.add_child(turret_tex)

	# ۱. نام آیتم / اسلحه زیر تصویر
	var title_size := 20 if is_prestige else 16
	var title_y := 136.0 if is_prestige else 148.0
	var title_height := 30.0 if is_prestige else 24.0
	var title_lbl := Ui.label(title_text, title_size, Color("#ffd858"), HORIZONTAL_ALIGNMENT_CENTER)
	title_lbl.position = Vector2(40.0, title_y)
	title_lbl.size = Vector2(300.0, title_height)
	stage.add_child(title_lbl)

	# ۲. نمایش سطح فعلی زیر نام اسلحه
	var level_size := 17 if is_prestige else 14
	var level_y := 170.0 if is_prestige else 174.0
	var level_height := 26.0 if is_prestige else 22.0
	var level_color := Color("#ffe27a") if unlocked else Style.gold
	var lvl_lbl := Ui.label(level_text, level_size, level_color, HORIZONTAL_ALIGNMENT_CENTER)
	lvl_lbl.position = Vector2(40.0, level_y)
	lvl_lbl.size = Vector2(300.0, level_height)
	stage.add_child(lvl_lbl)

	# ۳. نوار پیشرفت آبی درخشان داخل شیار طلایی کارت
	var ratio := clampf(progress_ratio, 0.0, 1.0)
	if ratio > 0.0:
		var max_bar_w := 268.0
		var fill_w := maxf(8.0, max_bar_w * ratio)
		var bar_fill := ColorRect.new()
		bar_fill.color = Color(0.14, 0.68, 1.0, 0.92)
		bar_fill.position = Vector2(56.0, 220.0)
		bar_fill.size = Vector2(fill_w, 23.0)
		stage.add_child(bar_fill)

		var bar_hi := ColorRect.new()
		bar_hi.color = Color(0.62, 0.92, 1.0, 0.75)
		bar_hi.position = Vector2(56.0, 220.0)
		bar_hi.size = Vector2(fill_w, 8.0)
		stage.add_child(bar_hi)

	# ۴. متن سبز بونوس / مشخصات زیر نوار پیشرفت
	var bonus_lbl := Ui.label(bonus_text, 15, Color("#42f584"), HORIZONTAL_ALIGNMENT_CENTER)
	bonus_lbl.position = Vector2(42.0, 253.0)
	bonus_lbl.size = Vector2(296.0, 26.0)
	stage.add_child(bonus_lbl)

	# ۵. نمایش هزینه در کادر هشت‌ضلعی پایین-چپ
	var cost_lbl := Ui.label(cost_text, 16, Color("#ffe88a"), HORIZONTAL_ALIGNMENT_CENTER)
	cost_lbl.position = Vector2(54.0, 296.0)
	cost_lbl.size = Vector2(86.0, 32.0)
	stage.add_child(cost_lbl)

	# ۶. متن دکمهٔ طلایی پایین-راست (UPGRADE / ارتقا / خرید)
	var btn_lbl := Ui.label(action_text, 19, Color(0.12, 0.07, 0.02, 1.0), HORIZONTAL_ALIGNMENT_CENTER)
	btn_lbl.position = Vector2(168.0, 295.0)
	btn_lbl.size = Vector2(148.0, 34.0)
	stage.add_child(btn_lbl)

	_ignore_card_input(card)
	return card


func _prestige_card() -> Button:
	var lvl := Game.prestige_level
	var max_lvl := Balance.PRESTIGE_MAX_LEVEL
	var bonus_coins := Game.prestige_starting_coins()
	var can_up := Game.can_upgrade_prestige()
	var cost_str := "%s ⭐" % Ui.fa(Game.prestige_upgrade_cost()) if can_up else "کامل"
	var act_str := "ارتقا" if can_up else "حداکثر"
	return _make_styled_card_shell(
		PRESTIGE_ID,
		true,
		"",
		"افزایش اعتبار (PRESTIGE BOOST)",
		"سطح %s / %s" % [Ui.fa(lvl), Ui.fa(max_lvl)],
		float(lvl) / float(max_lvl),
		"+%s سکهٔ شروع نبرد (هر سطح +۱۰ سکه)" % Ui.fa(bonus_coins),
		cost_str,
		act_str,
		true
	)


func _weapon_card(weapon: Dictionary) -> Button:
	var id := String(weapon["id"])
	var unlocked := Game.is_weapon_unlocked(id)
	var lvl := Game.weapon_level(id)
	var max_lvl := Balance.ARMORY_MAX_LEVEL

	var level_str := "سطح %s / %s" % [Ui.fa(lvl), Ui.fa(max_lvl)] if unlocked else "قفل · %s ستاره" % Ui.fa(int(weapon["star_cost"]))
	var ratio := (float(lvl) / float(max_lvl)) if unlocked else 0.0
	var cur_damage := float(weapon["damage"]) * Game.weapon_damage_mult(id)
	var cur_range := float(weapon["range"]) * Game.weapon_range_mult(id)
	var dmg_text := Ui.fa(round(cur_damage)) if is_equal_approx(cur_damage, round(cur_damage)) else Ui.fa_decimal(cur_damage, 1)
	var bonus_str := "آسیب %s · برد %s · ساخت %s سکه" % [
		dmg_text,
		Ui.fa(round(cur_range)),
		Ui.fa(int(weapon["cost"])),
	]
	var cost_str := ""
	var act_str := ""
	if unlocked:
		if Game.can_upgrade_weapon(id):
			cost_str = "%s ⭐" % Ui.fa(Game.weapon_upgrade_cost(id))
			act_str = "ارتقا"
		else:
			cost_str = "کامل"
			act_str = "حداکثر"
	else:
		cost_str = "%s ⭐" % Ui.fa(int(weapon["star_cost"]))
		act_str = "خرید"

	return _make_styled_card_shell(
		id,
		false,
		id,
		String(weapon["name"]),
		level_str,
		ratio,
		bonus_str,
		cost_str,
		act_str,
		unlocked
	)


## متن و تصویر کارت نباید لمس دکمهٔ زیرشان را بگیرند.
func _ignore_card_input(node: Node) -> void:
	for child in node.get_children():
		if child is Control:
			child.mouse_filter = Control.MOUSE_FILTER_IGNORE
		_ignore_card_input(child)

func _build_overlay() -> void:
	_overlay = Control.new()
	_overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	_overlay.mouse_filter = Control.MOUSE_FILTER_STOP
	add_child(_overlay)
	var dim := ColorRect.new()
	dim.color = Color(0.02, 0.04, 0.06, 0.82)
	dim.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	dim.mouse_filter = Control.MOUSE_FILTER_STOP
	dim.gui_input.connect(_on_dim_input)
	_overlay.add_child(dim)
	# ظرف مرکزچین تضمین می‌کند پنجرهٔ جزئیات و دکمهٔ ارتقا همیشه کامل داخل صفحهٔ ۱۲۸۰×۷۲۰ بمانند.
	var center := CenterContainer.new()
	center.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	center.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_overlay.add_child(center)
	_panel = Ui.panel(Style.bg_panel, 18, 14.0)
	_panel.custom_minimum_size = Vector2(540, 0)
	_panel.mouse_filter = Control.MOUSE_FILTER_STOP
	_panel.add_theme_stylebox_override("panel", Style.box(Style.bg_panel, 18, Style.gold_dark, 2, 14.0))
	center.add_child(_panel)
	_detail = Ui.column(6)
	_panel.add_child(_detail)
	_overlay.visible = false

func _on_dim_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		_close_detail()
	elif event is InputEventScreenTouch and event.pressed:
		_close_detail()

func _open_detail(id: String) -> void:
	_selected_id = id
	_overlay.visible = true
	_show_detail()
	Audio.ui_click()

func _close_detail() -> void:
	_overlay.visible = false
	_selected_id = ""

func _show_detail() -> void:
	for child in _detail.get_children():
		_detail.remove_child(child)
		child.queue_free()
	if _selected_id == PRESTIGE_ID:
		_show_prestige_detail()
		return
	var weapon := Weapons.by_id(_selected_id)
	if weapon.is_empty():
		return
	var id := _selected_id
	var unlocked := Game.is_weapon_unlocked(id)
	var header := Ui.row(12)
	_detail.add_child(header)
	var preview_frame := PanelContainer.new()
	preview_frame.custom_minimum_size = Vector2(78, 66)
	var preview_style := Style.box(Color("#0b1830"), 12, Style.gold_dark, 1, 4.0)
	preview_frame.add_theme_stylebox_override("panel", preview_style)
	var preview := TextureRect.new()
	preview.texture = Weapons.icon(id)
	preview.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	preview.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	preview.texture_filter = CanvasItem.TEXTURE_FILTER_NEAREST
	preview.custom_minimum_size = Vector2(70, 58)
	if not unlocked:
		preview.modulate = Color(0.65, 0.70, 0.76, 1.0)
	preview_frame.add_child(preview)
	header.add_child(preview_frame)
	var heading := Ui.column(2)
	heading.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(heading)
	heading.add_child(Ui.title(String(weapon["name"]), 25))
	heading.add_child(Ui.label("در دسترس" if unlocked else "هنوز باز نشده", 15,
		Style.good if unlocked else Style.gold))
	var close := Ui.button("×", 22, 38.0, "ghost")
	close.custom_minimum_size.x = 42
	close.pressed.connect(_close_detail)
	header.add_child(close)
	_detail.add_child(Ui.hline())
	var desc := Ui.label(String(weapon.get("desc", "")), 16, Style.text_dim)
	desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_detail.add_child(desc)
	var stats := Ui.panel(Style.bg_panel_light, 12, 10.0)
	_detail.add_child(stats)
	var lines := Ui.column(4)
	stats.add_child(lines)
	var eff_dmg := float(weapon["damage"]) * Game.weapon_damage_mult(id)
	var eff_rng := float(weapon["range"]) * Game.weapon_range_mult(id)
	var eff_cd := float(weapon["cooldown"]) * Game.weapon_interval_mult(id)
	var eff_dmg_str := Ui.fa(round(eff_dmg)) if is_equal_approx(eff_dmg, round(eff_dmg)) else Ui.fa_decimal(eff_dmg, 1)
	lines.add_child(Ui.label("آسیب: %s       برد: %s" % [eff_dmg_str,
		Ui.fa(round(eff_rng))], 16, Style.text))
	if id == "laser":
		lines.add_child(Ui.label("بارگذاری مجدد: %s ثانیه" % Ui.fa_decimal(eff_cd, 2), 16, Style.text))
		lines.add_child(Ui.label("مدت پرتو: %s ثانیه" % Ui.fa_decimal(float(weapon.get("beam_duration", 0.32)), 2), 16, Style.text))
	else:
		lines.add_child(Ui.label("فاصلهٔ شلیک: %s ثانیه" % Ui.fa_decimal(eff_cd, 2), 16, Style.text))
	lines.add_child(Ui.label("هزینهٔ ساخت در نبرد: %s سکه" % Ui.fa(int(weapon["cost"])), 16, Style.gold))
	_detail.add_child(Ui.hline())
	if unlocked:
		var level := Game.weapon_level(id)
		_detail.add_child(Ui.label("سطح زرادخانه: %s از %s" % [Ui.fa(level),
			Ui.fa(Balance.ARMORY_MAX_LEVEL)], 17, Style.accent))
		var bar := ProgressBar.new()
		bar.max_value = Balance.ARMORY_MAX_LEVEL
		bar.value = level
		bar.show_percentage = false
		bar.custom_minimum_size.y = 10
		_detail.add_child(bar)
		if Game.can_upgrade_weapon(id):
			_detail.add_child(_upgrade_preview(weapon, level))
			var cost := Game.weapon_upgrade_cost(id)
			var upgrade := Ui.button("ارتقا به سطح %s — %s ستاره" % [Ui.fa(level + 1), Ui.fa(cost)],
				18, 48.0, "primary")
			upgrade.disabled = Game.stars < cost
			upgrade.pressed.connect(_upgrade.bind(id))
			_detail.add_child(upgrade)
		else:
			_detail.add_child(Ui.label("به بالاترین سطح رسیده است", 18, Style.good, HORIZONTAL_ALIGNMENT_CENTER))
	else:
		var cost := int(weapon["star_cost"])
		var unlock := Ui.button("خرید سلاح — %s ستاره" % Ui.fa(cost), 18, 48.0, "primary")
		unlock.disabled = Game.stars < cost
		unlock.pressed.connect(_unlock.bind(id))
		_detail.add_child(unlock)
	if (not unlocked) or (unlocked and Game.can_upgrade_weapon(id)):
		var cost := Game.weapon_upgrade_cost(id) if unlocked else int(weapon["star_cost"])
		if Game.stars < cost:
			_detail.add_child(Ui.label("ستارهٔ کافی ندارید", 15, Style.bad, HORIZONTAL_ALIGNMENT_CENTER))


func _show_prestige_detail() -> void:
	var lvl := Game.prestige_level
	var max_lvl := Balance.PRESTIGE_MAX_LEVEL
	var header := Ui.row(12)
	_detail.add_child(header)
	header.add_child(Ui.tex("icons/prestige_boost", 64))
	var heading := Ui.column(2)
	heading.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	header.add_child(heading)
	heading.add_child(Ui.title("افزایش اعتبار (PRESTIGE BOOST)", 23))
	heading.add_child(Ui.label("پاداش دائم شروع نبرد در تمام نقشه‌ها", 15, Style.good))
	var close := Ui.button("×", 22, 38.0, "ghost")
	close.custom_minimum_size.x = 42
	close.pressed.connect(_close_detail)
	header.add_child(close)
	_detail.add_child(Ui.hline())
	var desc := Ui.label("با هر سطح ارتقای این آیتم، ۱۰ سکه به سکه‌های اولیهٔ شروع بازی در ابتدای هر نقشه اضافه می‌شود.", 16, Style.text_dim)
	desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_detail.add_child(desc)
	var stats := Ui.panel(Style.bg_panel_light, 12, 10.0)
	_detail.add_child(stats)
	var lines := Ui.column(4)
	stats.add_child(lines)
	lines.add_child(Ui.label("بونوس فعلی: +%s سکهٔ شروع نبرد" % Ui.fa(Game.prestige_starting_coins()), 16, Style.good))
	lines.add_child(Ui.label("سکهٔ شروع هر نقشه: %s سکه" % Ui.fa(Balance.START_COINS + Game.prestige_starting_coins()), 16, Style.gold))
	_detail.add_child(Ui.hline())
	_detail.add_child(Ui.label("سطح فعلی: %s از %s" % [Ui.fa(lvl), Ui.fa(max_lvl)], 17, Style.accent))
	var bar := ProgressBar.new()
	bar.max_value = max_lvl
	bar.value = lvl
	bar.show_percentage = false
	bar.custom_minimum_size.y = 10
	_detail.add_child(bar)
	if Game.can_upgrade_prestige():
		var box := Ui.panel(Style.bg_panel_light, 12, 10.0)
		box.add_theme_stylebox_override("panel", Style.box(Style.bg_panel_light, 12, Style.good, 1, 10.0))
		var p_lines := Ui.column(4)
		box.add_child(p_lines)
		p_lines.add_child(Ui.label("با ارتقا به سطح %s:" % Ui.fa(lvl + 1), 16, Style.accent))
		p_lines.add_child(_upgrade_row("سکهٔ شروع هر نقشه", "+%s" % Ui.fa(Balance.PRESTIGE_COINS_PER_LEVEL)))
		_detail.add_child(box)
		var cost := Game.prestige_upgrade_cost()
		var up_btn := Ui.button("ارتقا به سطح %s — %s ستاره" % [Ui.fa(lvl + 1), Ui.fa(cost)],
			18, 48.0, "primary")
		up_btn.disabled = Game.stars < cost
		up_btn.pressed.connect(_upgrade_prestige_item)
		_detail.add_child(up_btn)
		if Game.stars < cost:
			_detail.add_child(Ui.label("ستارهٔ کافی ندارید", 15, Style.bad, HORIZONTAL_ALIGNMENT_CENTER))
	else:
		_detail.add_child(Ui.label("به بالاترین سطح رسیده است", 18, Style.good, HORIZONTAL_ALIGNMENT_CENTER))


func _refresh_detail_deferred() -> void:
	_refresh_cards()
	if _overlay != null and _overlay.visible and not _selected_id.is_empty():
		_show_detail()

func _upgrade_prestige_item() -> void:
	if Game.upgrade_prestige():
		Audio.play("upgrade")
		_refresh_detail_deferred.call_deferred()
	else:
		Audio.play("deny")


## پیش‌نمایش ارتقا: هر فاکتور و مقدار افزایشش با یک سطح بالاتر.
func _upgrade_preview(weapon: Dictionary, level: int) -> Control:
	var step := Balance.ARMORY_STEP
	var box := Ui.panel(Style.bg_panel_light, 12, 10.0)
	box.add_theme_stylebox_override("panel", Style.box(Style.bg_panel_light, 12, Style.good, 1, 10.0))
	var lines := Ui.column(4)
	box.add_child(lines)
	lines.add_child(Ui.label("با ارتقا به سطح %s:" % Ui.fa(level + 1), 16, Style.accent))
	lines.add_child(_upgrade_row("قدرت شلیک", "+" + Ui.fa(Balance.ARMORY_DAMAGE_PER_LEVEL)))
	lines.add_child(_upgrade_row("برد", "+" + Ui.fa_decimal(float(weapon["range"]) * step, 1)))
	lines.add_child(_upgrade_row("سرعت شلیک", "+" + Ui.fa_decimal(step * 100.0, 1) + "٪"))
	return box

func _upgrade_row(title: String, delta: String) -> Control:
	var row := Ui.row(8)
	row.add_child(Ui.spacer())
	row.add_child(Ui.label(title, 16, Style.text))
	row.add_child(Ui.label(delta, 16, Style.good))
	return row

func _upgrade(id: String) -> void:
	if Game.upgrade_weapon(id):
		Audio.play("upgrade")
		_refresh_detail_deferred.call_deferred()
	else:
		Audio.play("deny")

func _unlock(id: String) -> void:
	if Game.unlock_weapon(id):
		Audio.play("unlock")
		_refresh_detail_deferred.call_deferred()
	else:
		Audio.play("deny")

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_ESCAPE:
		if _overlay.visible:
			_close_detail()
		else:
			Ui.goto_scene("res://scenes/MainMenu.tscn")
