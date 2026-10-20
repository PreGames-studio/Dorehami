## تنظیمات: صدای موسیقی و جلوه‌ها، نام نگهبان، بازنشانی پیشرفت و شناسنامهٔ بازی.
extends Control

var _music_slider: HSlider = null
var _sfx_slider: HSlider = null
var _music_value: Label = null
var _sfx_value: Label = null
var _name_edit: LineEdit = null
var _confirm_row: HBoxContainer = null

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	if Style.theme != null:
		theme = Style.theme
	Ui.backdrop(self, false)
	_build()
	Audio.play_music("music_menu")
	Ui.fade_in(self, 0.25)

func _build() -> void:
	var root := Ui.margin(22, 16, 22, 16)
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(root)

	var column := Ui.column(14)
	root.add_child(column)

	var top := Ui.row(12)
	column.add_child(top)
	var back := Ui.button("بازگشت", 20, 48.0, "ghost")
	back.custom_minimum_size = Vector2(130, 48)
	back.pressed.connect(func(): Ui.goto_scene("res://scenes/MainMenu.tscn"))
	top.add_child(back)
	var title := Ui.title("تنظیمات", 32)
	title.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	top.add_child(title)
	top.add_child(Ui.spacer())

	var body := Ui.row(20)
	column.add_child(body)

	# --- صدا و نام
	var left := Ui.column(10)
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	body.add_child(left)

	var sound_panel := Ui.panel(Color(Style.bg_panel.r, Style.bg_panel.g, Style.bg_panel.b, 0.94), 16, 16.0)
	left.add_child(sound_panel)
	var sound_column := Ui.column(10)
	sound_panel.add_child(sound_column)
	sound_column.add_child(Ui.title("صدا", 24))

	var music_row := Ui.row(10)
	sound_column.add_child(music_row)
	music_row.add_child(Ui.label("موسیقی", 19))
	_music_slider = HSlider.new()
	_music_slider.min_value = 0.0
	_music_slider.max_value = 1.0
	_music_slider.step = 0.05
	_music_slider.value = Game.music_volume
	_music_slider.custom_minimum_size = Vector2(220, 24)
	_music_slider.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_music_slider.value_changed.connect(_on_music_changed)
	music_row.add_child(_music_slider)
	_music_value = Ui.label("", 18, Style.text_dim)
	_music_value.custom_minimum_size = Vector2(64, 0)
	music_row.add_child(_music_value)

	var sfx_row := Ui.row(10)
	sound_column.add_child(sfx_row)
	sfx_row.add_child(Ui.label("جلوه‌ها", 19))
	_sfx_slider = HSlider.new()
	_sfx_slider.min_value = 0.0
	_sfx_slider.max_value = 1.0
	_sfx_slider.step = 0.05
	_sfx_slider.value = Game.sfx_volume
	_sfx_slider.custom_minimum_size = Vector2(220, 24)
	_sfx_slider.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_sfx_slider.value_changed.connect(_on_sfx_changed)
	sfx_row.add_child(_sfx_slider)
	_sfx_value = Ui.label("", 18, Style.text_dim)
	_sfx_value.custom_minimum_size = Vector2(64, 0)
	sfx_row.add_child(_sfx_value)

	var name_panel := Ui.panel(Color(Style.bg_panel.r, Style.bg_panel.g, Style.bg_panel.b, 0.94), 16, 16.0)
	left.add_child(name_panel)
	var name_column := Ui.column(10)
	name_panel.add_child(name_column)
	name_column.add_child(Ui.title("نام نگهبان", 24))
	var name_row := Ui.row(10)
	name_column.add_child(name_row)
	_name_edit = LineEdit.new()
	_name_edit.text = Game.nickname
	_name_edit.max_length = 16
	_name_edit.custom_minimum_size = Vector2(240, 42)
	_name_edit.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	name_row.add_child(_name_edit)
	var save_name := Ui.button("ذخیره", 19, 42.0)
	save_name.custom_minimum_size = Vector2(120, 42)
	save_name.pressed.connect(_on_save_name)
	name_row.add_child(save_name)

	# --- پیشرفت و شناسنامه
	var right := Ui.column(10)
	right.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	body.add_child(right)

	var progress_panel := Ui.panel(Color(Style.bg_panel.r, Style.bg_panel.g, Style.bg_panel.b, 0.94), 16, 16.0)
	right.add_child(progress_panel)
	var progress_column := Ui.column(8)
	progress_panel.add_child(progress_column)
	progress_column.add_child(Ui.title("پیشرفت", 24))
	progress_column.add_child(Ui.label("ستاره‌ها: %s" % Ui.fa(Game.stars), 19))
	progress_column.add_child(Ui.label("نقشه‌های باز: %s از %s" % [Ui.fa(Game.unlocked_maps.size()), Ui.fa(Maps.count())], 19))
	progress_column.add_child(Ui.label("سلاح‌های باز: %s از %s" % [Ui.fa(Game.unlocked_weapons.size()), Ui.fa(Weapons.count())], 19))
	progress_column.add_child(Ui.hline())
	var reset := Ui.button("بازنشانی پیشرفت", 18, 46.0, "danger")
	reset.pressed.connect(_on_reset_pressed)
	progress_column.add_child(reset)
	_confirm_row = Ui.row(8)
	progress_column.add_child(_confirm_row)
	_confirm_row.visible = false

	var about := Ui.panel(Color(Style.bg_panel.r, Style.bg_panel.g, Style.bg_panel.b, 0.94), 16, 16.0)
	right.add_child(about)
	var about_column := Ui.column(6)
	about.add_child(about_column)
	about_column.add_child(Ui.title("شناسنامه", 24))
	var about_text := Ui.label(
		"دژبان — نسخهٔ ۱٫۰٫۰\nسازندگان: مهدی زارعی و گروه PreGames Studio\nارتباط با ما : gf2227@gmail.com",
		17, Style.text_dim)
	about_text.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	about_column.add_child(about_text)

	_refresh_labels()

func _refresh_labels() -> void:
	_music_value.text = Ui.percent(Game.music_volume)
	_sfx_value.text = Ui.percent(Game.sfx_volume)

func _on_music_changed(value: float) -> void:
	Game.music_volume = value
	Audio.apply_settings(Game.music_volume, Game.sfx_volume)
	Game.save()
	_refresh_labels()

func _on_sfx_changed(value: float) -> void:
	Game.sfx_volume = value
	Audio.apply_settings(Game.music_volume, Game.sfx_volume)
	Game.save()
	_refresh_labels()
	Audio.play("ui_click")

func _on_save_name() -> void:
	Game.set_nickname(_name_edit.text.strip_edges())
	_name_edit.text = Game.nickname
	Audio.play("unlock")

func _on_reset_pressed() -> void:
	Audio.ui_click()
	_confirm_row.visible = true
	for child in _confirm_row.get_children():
		child.queue_free()
	var sure := Ui.button("مطمئنم، پاک کن", 18, 46.0, "danger")
	sure.pressed.connect(_on_reset_confirmed)
	_confirm_row.add_child(sure)
	var cancel := Ui.button("بی‌خیال", 18, 46.0, "ghost")
	cancel.pressed.connect(func():
		Audio.ui_back()
		_confirm_row.visible = false)
	_confirm_row.add_child(cancel)

func _on_reset_confirmed() -> void:
	Game.reset_progress()
	Audio.play("deny")
	_confirm_row.visible = false
	get_tree().reload_current_scene()

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_ESCAPE:
		Ui.goto_scene("res://scenes/MainMenu.tscn")
