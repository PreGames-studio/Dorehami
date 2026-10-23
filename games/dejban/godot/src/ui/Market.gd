## بازار: خرید بسته‌های ستاره (۲۰، ۸۰ و ۱۵۰ ستاره) از طریق پرداخت درون‌برنامه‌ای.
extends Control

const STAR_PACKS := [
	{
		"id": "stars_20",
		"stars": 20,
		"title": "۲۰ ستاره",
		"subtitle": "بستهٔ پایه — ۱۰ هزار تومان",
		"desc": "مناسب برای ارتقای سریع برجک‌ها در زرادخانه.",
		"price_toman": 10000,
		"price_text": "۱۰٬۰۰۰ تومان",
		"price_label": "۱۰ هزار تومان",
	},
	{
		"id": "stars_80",
		"stars": 80,
		"title": "۸۰ ستاره",
		"subtitle": "بستهٔ محبوب — ۵۰ هزار تومان",
		"desc": "انتخاب اقتصادی فرماندهان برای آزادسازی اسلحه‌های پیشرفته و حماسی.",
		"price_toman": 50000,
		"price_text": "۵۰٬۰۰۰ تومان",
		"price_label": "۵۰ هزار تومان",
	},
	{
		"id": "stars_150",
		"stars": 150,
		"title": "۱۵۰ ستاره",
		"subtitle": "بستهٔ طلایی — ۱۰۰ هزار تومان",
		"desc": "بیشترین حجم ستاره برای ارتقای کامل برجک‌ها در زرادخانه.",
		"price_toman": 100000,
		"price_text": "۱۰۰٬۰۰۰ تومان",
		"price_label": "۱۰۰ هزار تومان",
	},
]

var _stars_label: Label = null
var _cards_row: HBoxContainer = null
var _notice_label: Label = null

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	if Style.theme != null:
		theme = Style.theme
	_build()
	Audio.play_music("music_menu")
	Ui.fade_in(self, 0.25)

func _build() -> void:
	Ui.backdrop(self, false, "res://assets/sprites/ui/armory_background.png")
	var root := Ui.margin(32, 20, 32, 20)
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(root)

	var column := Ui.column(16)
	column.size_flags_vertical = Control.SIZE_EXPAND_FILL
	root.add_child(column)

	# نوار بالا: دکمهٔ بازگشت، عنوان بازار و موجودی ستاره
	var top := Ui.row(12)
	column.add_child(top)
	var back := Ui.button("بازگشت", 20, 48.0, "ghost")
	back.custom_minimum_size = Vector2(130, 48)
	back.pressed.connect(func(): Ui.goto_scene("res://scenes/MainMenu.tscn"))
	top.add_child(back)
	top.add_child(Ui.title("بازار", 34))
	top.add_child(Ui.spacer())
	var stars := Ui.icon_label("icons/star_small", "%s ستاره" % Ui.fa(Game.stars), 22, 28.0)
	_stars_label = stars.get_child(1) as Label
	top.add_child(stars)

	var hint := Ui.label("بستهٔ ستارهٔ مورد نظر خود را برای خرید درون‌برنامه‌ای انتخاب کنید", 18, Style.text_dim)
	column.add_child(hint)

	# ردیف ۳ بستهٔ ستاره در وسط صفحه
	_cards_row = Ui.row(20)
	_cards_row.alignment = BoxContainer.ALIGNMENT_CENTER
	_cards_row.size_flags_vertical = Control.SIZE_EXPAND_FILL
	column.add_child(_cards_row)

	for i in STAR_PACKS.size():
		_cards_row.add_child(_build_pack_card(STAR_PACKS[i], i))

	_notice_label = Ui.label("", 18, Style.gold, HORIZONTAL_ALIGNMENT_CENTER)
	_notice_label.custom_minimum_size.y = 32
	column.add_child(_notice_label)

func _build_pack_card(pack: Dictionary, index: int) -> PanelContainer:
	var border_col := Style.line
	if index == 1:
		border_col = Style.accent
	elif index == 2:
		border_col = Style.gold

	var panel := Ui.panel(Style.bg_panel, 18, 20.0)
	panel.custom_minimum_size = Vector2(360, 420)
	panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	panel.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	panel.add_theme_stylebox_override("panel", Style.box(Style.bg_panel, 18, border_col, 2, 20.0))

	var col := Ui.column(12)
	col.alignment = BoxContainer.ALIGNMENT_CENTER
	panel.add_child(col)

	# آیکن ستاره و تعداد ستارهٔ بسته
	var icon_row := Ui.row(8)
	icon_row.alignment = BoxContainer.ALIGNMENT_CENTER
	var star_count_icons := mini(3, index + 1)
	for _s in star_count_icons:
		icon_row.add_child(Ui.tex("icons/star_small", 42))
	col.add_child(icon_row)

	var title := Ui.title(String(pack["title"]), 34)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(title)

	var sub := Ui.label(String(pack["subtitle"]), 18, Style.gold, HORIZONTAL_ALIGNMENT_CENTER)
	col.add_child(sub)

	col.add_child(Ui.hline())

	var desc := Ui.label(String(pack["desc"]), 16, Style.text_dim, HORIZONTAL_ALIGNMENT_CENTER)
	desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	desc.custom_minimum_size.y = 58
	col.add_child(desc)

	var price_box := Ui.panel(Style.bg_panel_light, 12, 12.0)
	col.add_child(price_box)
	var price_col := Ui.column(4)
	price_box.add_child(price_col)
	price_col.add_child(Ui.label("مقدار: %s ستاره" % Ui.fa(int(pack["stars"])), 18, Style.good, HORIZONTAL_ALIGNMENT_CENTER))
	price_col.add_child(Ui.label("قیمت: %s (%s)" % [String(pack["price_label"]), String(pack["price_text"])],
		18, Style.gold, HORIZONTAL_ALIGNMENT_CENTER))

	var buy_btn := Ui.button("خرید — %s" % String(pack["price_text"]), 20, 54.0, "primary")
	buy_btn.pressed.connect(_on_buy_pack.bind(pack))
	col.add_child(buy_btn)

	return panel

func _on_buy_pack(pack: Dictionary) -> void:
	# آمادهٔ اتصال به درگاه پرداخت درون‌برنامه‌ای
	Audio.play("deny")
	if _notice_label != null:
		_notice_label.text = "خرید «%s» به مبلغ %s — درگاه پرداخت به‌زودی متصل می‌شود" % [
			String(pack["title"]), String(pack["price_text"])
		]

func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventKey and event.pressed and not event.echo and event.keycode == KEY_ESCAPE:
		Ui.goto_scene("res://scenes/MainMenu.tscn")
