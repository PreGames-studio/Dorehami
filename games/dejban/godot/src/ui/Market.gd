## بازار: دریافت «ستاره رایگان» با دیدن ۲ تبلیغ (+۵ ستاره) و خرید بسته‌های ستاره
## (۲۰، ۸۰ و ۱۵۰ ستاره) از طریق پرداخت درون‌برنامه‌ای.
extends Control

## تنظیمات «ستاره رایگان» با مشاهدهٔ تبلیغ — لینک تبلیغ را بعداً در FREE_STAR_AD_URL (یا به تفکیک
## تبلیغ اول و دوم در FREE_STAR_AD_URLS) قرار دهید تا هنگام زدن دکمه خودکار باز شود.
const FREE_STAR_REWARD := 5
const FREE_STAR_REQUIRED_ADS := 2
const FREE_STAR_AD_URL := ""
const FREE_STAR_AD_URLS := ["", ""]

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
		"desc": "انتخاب اقتصادی فرماندهان برای آزادسازی اسلحه‌های پیشرفته.",
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
var _free_progress_label: Label = null
var _free_watch_btn: Button = null

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	if Style.theme != null:
		theme = Style.theme
	_build()
	Audio.play_music("music_menu")
	Ui.fade_in(self, 0.25)

func _build() -> void:
	Ui.backdrop(self, false, "res://assets/sprites/ui/armory_background.png")
	var root := Ui.margin(24, 18, 24, 18)
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(root)

	var column := Ui.column(14)
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

	var hint := Ui.label("ستارهٔ رایگان با مشاهدهٔ تبلیغ بگیرید یا بستهٔ ستارهٔ دلخواه را انتخاب کنید", 18, Style.text_dim)
	column.add_child(hint)

	# ردیف ۴ کارت بازار (ستاره رایگان + ۳ بستهٔ خریدنی) کاملاً جا شده در ۱۲۸۰×۷۲۰
	_cards_row = Ui.row(14)
	_cards_row.alignment = BoxContainer.ALIGNMENT_CENTER
	_cards_row.size_flags_vertical = Control.SIZE_EXPAND_FILL
	column.add_child(_cards_row)

	_cards_row.add_child(_build_free_star_card())
	for i in STAR_PACKS.size():
		_cards_row.add_child(_build_pack_card(STAR_PACKS[i], i))

	_notice_label = Ui.label("", 18, Style.gold, HORIZONTAL_ALIGNMENT_CENTER)
	_notice_label.custom_minimum_size.y = 30
	column.add_child(_notice_label)
	_refresh_market_ui()

func _build_free_star_card() -> PanelContainer:
	var panel := Ui.panel(Style.bg_panel, 18, 16.0)
	panel.name = "FreeStarCard"
	panel.custom_minimum_size = Vector2(276, 412)
	panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	panel.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	panel.add_theme_stylebox_override("panel", Style.box(Style.bg_panel, 18, Style.good, 2, 16.0))

	var col := Ui.column(10)
	col.alignment = BoxContainer.ALIGNMENT_CENTER
	panel.add_child(col)

	var icon_row := Ui.row(6)
	icon_row.alignment = BoxContainer.ALIGNMENT_CENTER
	icon_row.add_child(Ui.tex("icons/star_small", 38))
	col.add_child(icon_row)

	var title := Ui.title("ستاره رایگان", 30)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(title)

	var sub := Ui.label("%s ستاره با دیدن %s تبلیغ" % [Ui.fa(FREE_STAR_REWARD), Ui.fa(FREE_STAR_REQUIRED_ADS)],
		17, Style.good, HORIZONTAL_ALIGNMENT_CENTER)
	col.add_child(sub)

	col.add_child(Ui.hline())

	var desc := Ui.label("با دیدن ۲ تبلیغ، ۵ ستارهٔ رایگان دریافت کنید و برجک‌های خود را ارتقا دهید.",
		15, Style.text_dim, HORIZONTAL_ALIGNMENT_CENTER)
	desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	desc.custom_minimum_size.y = 56
	col.add_child(desc)

	var info_box := Ui.panel(Style.bg_panel_light, 12, 10.0)
	col.add_child(info_box)
	var info_col := Ui.column(4)
	info_box.add_child(info_col)
	info_col.add_child(Ui.label("پاداش: %s ستارهٔ رایگان" % Ui.fa(FREE_STAR_REWARD), 17, Style.good, HORIZONTAL_ALIGNMENT_CENTER))
	_free_progress_label = Ui.label("", 17, Style.gold, HORIZONTAL_ALIGNMENT_CENTER)
	info_col.add_child(_free_progress_label)

	_free_watch_btn = Ui.button("", 18, 52.0, "online")
	_free_watch_btn.name = "FreeStarButton"
	_free_watch_btn.pressed.connect(_on_watch_free_star_ad)
	col.add_child(_free_watch_btn)

	return panel

func _build_pack_card(pack: Dictionary, index: int) -> PanelContainer:
	var border_col := Style.line
	if index == 1:
		border_col = Style.accent
	elif index == 2:
		border_col = Style.gold

	var panel := Ui.panel(Style.bg_panel, 18, 16.0)
	panel.custom_minimum_size = Vector2(276, 412)
	panel.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	panel.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	panel.add_theme_stylebox_override("panel", Style.box(Style.bg_panel, 18, border_col, 2, 16.0))

	var col := Ui.column(10)
	col.alignment = BoxContainer.ALIGNMENT_CENTER
	panel.add_child(col)

	var icon_row := Ui.row(6)
	icon_row.alignment = BoxContainer.ALIGNMENT_CENTER
	var star_count_icons := mini(3, index + 1)
	for _s in star_count_icons:
		icon_row.add_child(Ui.tex("icons/star_small", 38))
	col.add_child(icon_row)

	var title := Ui.title(String(pack["title"]), 30)
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	col.add_child(title)

	var sub := Ui.label(String(pack["subtitle"]), 17, Style.gold, HORIZONTAL_ALIGNMENT_CENTER)
	col.add_child(sub)

	col.add_child(Ui.hline())

	var desc := Ui.label(String(pack["desc"]), 15, Style.text_dim, HORIZONTAL_ALIGNMENT_CENTER)
	desc.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	desc.custom_minimum_size.y = 56
	col.add_child(desc)

	var price_box := Ui.panel(Style.bg_panel_light, 12, 10.0)
	col.add_child(price_box)
	var price_col := Ui.column(4)
	price_box.add_child(price_col)
	price_col.add_child(Ui.label("مقدار: %s ستاره" % Ui.fa(int(pack["stars"])), 17, Style.good, HORIZONTAL_ALIGNMENT_CENTER))
	price_col.add_child(Ui.label("قیمت: %s (%s)" % [String(pack["price_label"]), String(pack["price_text"])],
		16, Style.gold, HORIZONTAL_ALIGNMENT_CENTER))

	var buy_btn := Ui.button("خرید — %s" % String(pack["price_text"]), 18, 52.0, "primary")
	buy_btn.pressed.connect(_on_buy_pack.bind(pack))
	col.add_child(buy_btn)

	return panel

func _ad_url_for_step(step: int) -> String:
	if step >= 0 and step < FREE_STAR_AD_URLS.size():
		var indexed := String(FREE_STAR_AD_URLS[step]).strip_edges()
		if not indexed.is_empty():
			return indexed
	return FREE_STAR_AD_URL.strip_edges()

func _refresh_market_ui() -> void:
	if _stars_label != null:
		_stars_label.text = "%s ستاره" % Ui.fa(Game.stars)
	var watched := clampi(Game.free_star_ads_watched, 0, FREE_STAR_REQUIRED_ADS - 1)
	if _free_progress_label != null:
		_free_progress_label.text = "تبلیغ دیده شده: %s از %s" % [Ui.fa(watched), Ui.fa(FREE_STAR_REQUIRED_ADS)]
	if _free_watch_btn != null:
		if watched == 0:
			_free_watch_btn.text = "مشاهدهٔ تبلیغ (%s از %s)" % [Ui.fa(0), Ui.fa(FREE_STAR_REQUIRED_ADS)]
		else:
			_free_watch_btn.text = "مشاهدهٔ تبلیغ دوم (%s از %s)" % [Ui.fa(watched), Ui.fa(FREE_STAR_REQUIRED_ADS)]

func _on_watch_free_star_ad() -> void:
	var ad_url := _ad_url_for_step(Game.free_star_ads_watched)
	if not ad_url.is_empty():
		OS.shell_open(ad_url)
	var result := Game.record_free_star_ad_watch(FREE_STAR_REQUIRED_ADS, FREE_STAR_REWARD)
	_refresh_market_ui()
	if bool(result["rewarded"]):
		Audio.play("star")
		if _notice_label != null:
			_notice_label.text = "تبریک! با دیدن %s تبلیغ، %s ستارهٔ رایگان به حساب شما اضافه شد" % [
				Ui.fa(FREE_STAR_REQUIRED_ADS), Ui.fa(int(result["stars_granted"]))
			]
	else:
		Audio.ui_click()
		if _notice_label != null:
			_notice_label.text = "تبلیغ %s از %s ثبت شد — یک تبلیغ دیگر ببینید تا %s ستاره بگیرید" % [
				Ui.fa(int(result["watched"])), Ui.fa(FREE_STAR_REQUIRED_ADS), Ui.fa(FREE_STAR_REWARD)
			]

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
