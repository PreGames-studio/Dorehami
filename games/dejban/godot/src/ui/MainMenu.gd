## صفحهٔ اصلی: عنوان بازی، دکمه‌های ورود و خلاصهٔ پیشرفت.
extends Control

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	if Style.theme != null:
		theme = Style.theme
	Ui.backdrop(self, false, "res://assets/sprites/ui/main_menu_military.png")
	_build()
	Audio.play_music("music_menu")
	Ui.fade_in(self, 0.35)

func _build() -> void:
	var root := Ui.margin(48, 28, 48, 28)
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(root)

	var row := Ui.row(48)
	root.add_child(row)

	# ستون معرفی: فقط نام بازی.
	var intro := Ui.column(4)
	intro.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	intro.alignment = BoxContainer.ALIGNMENT_CENTER
	row.add_child(intro)

	var title := Ui.title("دژبان", 92)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_color_override("font_color", Color("#e4eaee"))
	title.add_theme_color_override("font_outline_color", Color("#2a3238"))
	title.add_theme_constant_override("outline_size", 10)
	title.add_theme_color_override("font_shadow_color", Color(0.3, 0.7, 1.0, 0.45))
	title.add_theme_constant_override("shadow_offset_y", 4)
	intro.add_child(title)

	var best_row := Ui.row(10)
	best_row.alignment = BoxContainer.ALIGNMENT_CENTER
	best_row.add_child(Ui.icon_label("icons/star_small", Ui.fa(Game.stars) + " ستاره", 20, 26.0))
	best_row.add_child(Ui.icon_label("icons/flag", "%s از %s نقشه باز" % [Ui.fa(Game.unlocked_maps.size()), Ui.fa(Maps.count())], 20, 26.0))
	intro.add_child(best_row)

	# ستون دکمه‌ها
	var menu := Ui.column(12)
	menu.custom_minimum_size = Vector2(360, 0)
	menu.alignment = BoxContainer.ALIGNMENT_CENTER
	row.add_child(menu)

	var single := Ui.button("بازی تک‌نفره", 26, 66.0)
	single.pressed.connect(_on_start)
	menu.add_child(single)

	var online := Ui.button("بازی گروهی", 26, 66.0, "online")
	online.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	online.pressed.connect(_on_multiplayer)
	var online_row := Ui.row(10)
	online_row.alignment = BoxContainer.ALIGNMENT_CENTER
	online_row.add_child(Ui.wifi_icon(30.0))
	online_row.add_child(online)
	menu.add_child(Ui.glow_frame(online_row, Style.accent))

	var armory := Ui.button("اسلحه‌ها", 22, 54.0)
	armory.pressed.connect(func(): Ui.goto_scene("res://scenes/Armory.tscn"))
	menu.add_child(armory)

	var market := Ui.button("بازار", 22, 54.0)
	market.pressed.connect(func(): Ui.goto_scene("res://scenes/Market.tscn"))
	menu.add_child(market)

	var settings := Ui.button("تنظیمات", 22, 54.0)
	settings.pressed.connect(func(): Ui.goto_scene("res://scenes/Settings.tscn"))
	menu.add_child(settings)

	var quit := Ui.button("خروج از بازی", 20, 48.0, "ghost")
	quit.pressed.connect(_request_quit)
	menu.add_child(quit)

	# پنل پیشرفت
	var progress := Ui.panel(Color(Style.bg_panel.r, Style.bg_panel.g, Style.bg_panel.b, 0.92), 16, 16.0)
	progress.custom_minimum_size = Vector2(330, 0)
	menu.add_child(progress)
	var column := Ui.column(8)
	progress.add_child(column)
	column.add_child(Ui.label("نگهبان: %s" % Game.nickname, 20, Style.text))
	column.add_child(Ui.hline())
	column.add_child(Ui.label("ستاره‌های نقشه‌ها: %s" % Ui.fa(Game.total_map_stars()), 18, Style.text_dim))
	column.add_child(Ui.label("آخرین نقشه: %s" % _last_map_title(), 18, Style.text_dim))
	column.add_child(Ui.label("سلاح‌های باز: %s از %s" % [Ui.fa(Game.unlocked_weapons.size()), Ui.fa(Weapons.count())], 18, Style.text_dim))

func _last_map_title() -> String:
	var index := Game.last_map
	if not Game.is_map_unlocked(index):
		return "—"
	return String(Maps.def(index).get("name", "—"))

func _on_start() -> void:
	Audio.ui_click()
	get_tree().change_scene_to_file("res://scenes/MapSelect.tscn")

func _on_multiplayer() -> void:
	Audio.ui_click()
	var notice := Ui.label("حالت بازی گروهی به‌زودی فعال می‌شود", 20, Style.accent, HORIZONTAL_ALIGNMENT_CENTER)
	notice.set_anchors_preset(Control.PRESET_CENTER_BOTTOM)
	notice.offset_left = -300
	notice.offset_right = 300
	notice.offset_top = -60
	notice.offset_bottom = -24
	add_child(notice)
	var tween := notice.create_tween()
	tween.tween_interval(1.4)
	tween.tween_property(notice, "modulate:a", 0.0, 0.4)
	tween.tween_callback(notice.queue_free)

## خروج از بازی فقط بعد از تأیید کاربر انجام می‌شود.
func _request_quit() -> void:
	Audio.ui_click()
	Ui.confirm(self, "خروج از بازی؟", "بازی بسته می‌شود و پیشرفت شما ذخیره می‌شود.", _on_quit)

func _on_quit() -> void:
	Audio.ui_back()
	Game.save()
	get_tree().quit()
