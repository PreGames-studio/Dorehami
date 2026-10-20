## اطلس نقشه‌ها: کارت هر نقشه با تصویر بندانگشتی، سختی، ستاره‌ها و وضعیت قفل.
extends Control

var _stars_label: Label = null

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	if Style.theme != null:
		theme = Style.theme
	_build()
	Audio.play_music("music_menu")
	Ui.fade_in(self, 0.25)

func _build() -> void:
	Ui.backdrop(self, false, "res://assets/sprites/ui/map_select_background.png")

	var root := Ui.margin(22, 16, 22, 16)
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(root)

	var column := Ui.column(12)
	root.add_child(column)

	# نوار بالا
	var top := Ui.row(12)
	column.add_child(top)
	var back := Ui.button("بازگشت", 20, 48.0, "ghost")
	back.custom_minimum_size = Vector2(130, 48)
	back.pressed.connect(func(): Ui.goto_scene("res://scenes/MainMenu.tscn"))
	top.add_child(back)
	var title := Ui.title("اطلس نقشه‌ها", 32)
	title.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	top.add_child(title)
	top.add_child(Ui.spacer())
	var stars := Ui.icon_label("icons/star_small", "", 22, 28.0)
	_stars_label = stars.get_child(1) as Label
	top.add_child(stars)
	var armory := Ui.button("اسلحه‌ها", 18, 46.0, "ghost")
	armory.custom_minimum_size = Vector2(140, 46)
	armory.pressed.connect(func(): Ui.goto_scene("res://scenes/Armory.tscn"))
	top.add_child(armory)

	# شبکهٔ نقشه‌ها
	var scroll := ScrollContainer.new()
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	scroll.vertical_scroll_mode = ScrollContainer.SCROLL_MODE_SHOW_ALWAYS
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	column.add_child(scroll)

	var grid := Ui.grid(4, 14)
	grid.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(grid)

	for index in Maps.count():
		grid.add_child(_map_card(index))

	_refresh_stars()

func _map_card(index: int) -> Control:
	var unlocked := Game.is_map_unlocked(index)
	var card := Ui.panel(Color(Style.bg_panel.r, Style.bg_panel.g, Style.bg_panel.b, 0.94), 16, 10.0)
	card.custom_minimum_size = Vector2(292, 250)

	var column := Ui.column(6)
	card.add_child(column)

	var thumb := MapThumb.new()
	thumb.custom_minimum_size = Vector2(272, 122)
	thumb.set_map(index)
	if not unlocked:
		thumb.modulate = Color(0.55, 0.6, 0.62, 1.0)
	column.add_child(thumb)

	var head := Ui.row(8)
	column.add_child(head)
	var name := Ui.label(String(Maps.def(index).get("name", "")), 20, Style.text if unlocked else Style.text_dim)
	name.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	head.add_child(name)
	if not unlocked:
		head.add_child(Ui.tex("icons/lock", 24.0))

	var difficulty := 1 + int(round(index * 0.32))
	var info := Ui.label("سختی %s از ۱۰ · دشمنان تا موج %s" % [Ui.fa(mini(difficulty, 10)), Ui.fa(Balance.WAVES)], 15, Style.text_dim)
	column.add_child(info)

	var stars_row := Ui.row(8)
	column.add_child(stars_row)
	stars_row.add_child(Ui.stars_row(Game.map_stars(index), 4, 20.0))
	var best_wave := int(Game.map_best(index).get("wave", 0))
	if best_wave > 0:
		stars_row.add_child(Ui.dim("بهترین موج: %s" % Ui.fa(best_wave), 15))

	column.add_child(Ui.spacer())

	if unlocked:
		var play := Ui.button("ورود به میدان", 19, 48.0, "primary")
		play.pressed.connect(_play.bind(index))
		column.add_child(play)
	else:
		var previous := String(Maps.def(index - 1).get("name", ""))
		var locked := Ui.label("با پیروزی در «%s» باز می‌شود" % previous, 15,
			Style.text_dim, HORIZONTAL_ALIGNMENT_CENTER)
		locked.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		column.add_child(locked)
	return card

func _play(index: int) -> void:
	if not Game.is_map_unlocked(index):
		return
	Audio.ui_click()
	Game.pending_map = index
	get_tree().change_scene_to_file("res://scenes/Battle.tscn")

func _refresh_stars() -> void:
	if _stars_label != null:
		_stars_label.text = "%s ستاره" % Ui.fa(Game.stars)

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_ESCAPE:
		Ui.goto_scene("res://scenes/MainMenu.tscn")
