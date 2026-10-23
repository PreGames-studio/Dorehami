## تست خودکار بدون رابط گرافیکی.
## ساختار داده‌ها، درستی نقشه‌ها، ترکیب موج‌ها و کامل بودن دارایی‌ها را بررسی
## می‌کند و در پایان با کد خروج ۰ (موفق) یا ۱ (ناموفق) خارج می‌شود تا در CI
## بتوان به نتیجه اعتماد کرد:
##
##   godot --headless --path godot res://tests/headless_test.tscn
extends Node

const REQUIRED_SOUNDS := [
	"combat/rifle-semi", "combat/rifle-bolt", "combat/field-gun-fire", "combat/tank-gun-fire",
	"combat/soldier-defeat", "combat/cannon-wreck", "combat/aircraft-hit",
	"build", "upgrade", "sell", "deny", "coin", "star", "ui_click", "ui_hover", "ui_back",
	"wave_start", "wave_clear", "keep_hit", "tower_destroyed", "victory", "defeat", "unlock",
	"music_menu", "music_battle",
]

const RETIRED_COMBAT_SOUNDS := [
	"shot_arrow", "shot_bolt", "shot_stone", "shot_fire", "shot_frost", "shot_spark",
	"shot_poison", "shot_heavy", "hit", "enemy_death", "enemy_scream",
	"explosion_small", "explosion_big",
]

const REQUIRED_UI_TEXTURES := [
	"structures/keep", "structures/gate", "ui/sky", "towers/base",
	"ui/victory_background", "ui/defeat_background",
	"fx/ring", "fx/star", "fx/coin", "fx/heal", "fx/spark", "fx/select",
	"icons/coin", "icons/heart", "icons/star_small", "icons/speed", "icons/lock",
	"icons/check", "icons/flag", "icons/wave", "icon/app",
]

const SCENES := [
	"res://scenes/Splash.tscn",
	"res://scenes/MainMenu.tscn",
	"res://scenes/MapSelect.tscn",
	"res://scenes/Armory.tscn",
	"res://scenes/Market.tscn",
	"res://scenes/Settings.tscn",
	"res://scenes/Battle.tscn",
]

const LAUNCHER_ICONS := [
	{"path": "res://icon_192.png", "size": Vector2i(192, 192)},
	{"path": "res://icon_512.png", "size": Vector2i(512, 512)},
	{"path": "res://icon_foreground_432.png", "size": Vector2i(432, 432)},
	{"path": "res://icon_background_432.png", "size": Vector2i(432, 432)},
]

const LAUNCHER_KEYS := [
	"launcher_icons/main_192x192",
	"launcher_icons/adaptive_foreground_432x432",
	"launcher_icons/adaptive_background_432x432",
]

const FRAME_PX := 64          ## عرض هر فریمِ شیت‌های دویدن و مرگ
const VALID_KINDS := ["single", "splash", "chain", "aura"]
const VALID_TRAITS := ["flying", "heal", "buff", "ranged", "boss"]

var failures: Array = []
var checks := 0


## باتل جعلی فقط برای سنجش برد برجک؛ برجک تنها فهرست دشمنان را از باتل می‌خواند.
class StubBattle extends Node:
	var enemies: Array = []

	func fx_layer() -> Node:
		return self

## پروفایل جدا برای آزمودن پاداش‌ها، بدون دست‌زدن به ذخیرهٔ واقعی بازیکن.
class RunTestProfile extends "res://src/autoload/Game.gd":
	func save() -> void:
		pass

func _ready() -> void:
	print("=== تست خودکار دژبان ===")
	print("— بررسی آیکون‌های اپ…")
	_test_app_icons()
	print("— بررسی دارایی‌ها…")
	_test_assets()
	print("— بررسی شیت‌های افکت…")
	await _test_fx_sheets()
	print("— بررسی سلاح‌ها…")
	_test_weapons()
	print("— بررسی دشمنان…")
	_test_enemies()
	print("— بررسی شیت‌های دویدن و مرگ…")
	_test_unit_sheets()
	print("— بررسی شیت‌های ایزومتریک برجک و تانک…")
	_test_iso_sheets()
	print("— بررسی نقشه‌ها…")
	_test_maps()
	_test_map_progression()
	_test_repeatable_run_stars()
	_test_prestige_fixed_cost()
	print("— بررسی موج‌ها…")
	_test_waves()
	print("— بررسی تعادل…")
	_test_balance()
	print("— بررسی برد شلیک برجک…")
	_test_tower_range()
	print("— بررسی برجک‌های ایزومتریک…")
	_test_iso_towers()
	print("— بررسی تانک و تک‌شلیک به دژ…")
	await _test_tank()
	print("— بررسی صفحهٔ آغازین…")
	_test_launch_presentation()
	print("— بررسی پنجرهٔ تأییدیه…")
	_test_confirm_dialog()
	print("— ساخت صحنه‌ها…")
	await _smoke_scenes()
	print("— بررسی انتخاب و ارتقای برجک با لمس واقعی…")
	await _test_touch_upgrade_flow()
	_report()

## هر صحنه را می‌سازد، دو فریم زنده نگه می‌دارد و آزاد می‌کند.
func _smoke_scenes() -> void:
	for path in SCENES:
		print("  · ساخت %s…" % path.get_file())
		var packed = load(path)
		check(packed != null, "صحنه بارگذاری نشد: %s" % path)
		if packed == null:
			continue
		var instance = packed.instantiate()
		check(instance != null, "صحنه ساخته نشد: %s" % path)
		if instance == null:
			continue
		add_child(instance)
		await get_tree().process_frame
		await get_tree().process_frame
		if path.ends_with("Splash.tscn"):
			# لوگوی استودیو باید پنج ثانیه بماند (SHOW_SECONDS در src/ui/Splash.gd).
			var constants: Dictionary = instance.get_script().get_script_constant_map()
			var seconds := float(constants.get("SHOW_SECONDS", 0.0))
			check(is_equal_approx(seconds, 5.0), "زمان نمایش لوگو باید ۵ ثانیه باشد، هست %s" % seconds)
		if path.ends_with("MainMenu.tscn"):
			var texts: Array = []
			_collect_labels(instance, texts)
			check(texts.has("دژبان"), "نام بازی در منوی اصلی نوشته نشده است")
			for removed in ["TOWER DEFENSE", "دژبان · نگهبانان آخرین دژ"]:
				check(not texts.has(removed), "متن حذف‌شدهٔ منو هنوز نمایش داده می‌شود: %s" % removed)
			check(_find_label(texts, "سازنده") < 0, "نام سازنده نباید در منوی اصلی باشد")
			check(_find_label(texts, "gf2227d") < 0, "نشانی سازنده نباید در منوی اصلی باشد")
			var btn_texts: Array = []
			_collect_buttons(instance, btn_texts)
			var market_idx := btn_texts.find("بازار")
			var settings_idx := btn_texts.find("تنظیمات")
			check(market_idx >= 0 and settings_idx == market_idx + 1,
				"دکمهٔ «بازار» باید در صفحهٔ اصلی دقیقاً بالای «تنظیمات» باشد")
		if path.ends_with("MapSelect.tscn"):
			var texts: Array = []
			_collect_labels(instance, texts)
			if Game.unlocked_maps.size() < Maps.count():
				check(_find_label(texts, "با پیروزی در") >= 0, "قفل نقشه باید به پیروزی قبلی وابسته باشد")
			check(_find_label(texts, "باز کردن —") < 0, "قیمت خرید نقشه نباید نمایش داده شود")
		if path.ends_with("Armory.tscn"):
			check(instance._list.get_child_count() == Weapons.count() + 1,
				"کارت افزایش اعتبار + همهٔ اسلحه‌ها باید نمایش داده شوند")
			# کارت اول باید آیتم «افزایش اعتبار» (prestige_boost) باشد
			var prestige_card: Button = instance._list.get_child(0)
			prestige_card.pressed.emit()
			check(instance._overlay.visible and instance._selected_id == "prestige_boost",
				"کارت اول صفحهٔ اسلحه‌ها باید آیتم افزایش اعتبار باشد")
			var p_texts: Array = []
			_collect_labels(instance._detail, p_texts)
			check(_find_label(p_texts, "افزایش اعتبار") >= 0 and _find_label(p_texts, "۱۰ سکه") >= 0,
				"جزئیات آیتم افزایش اعتبار و افزودن ۱۰ سکه در پنجرهٔ جزئیات نیست")
			instance._close_detail()

			# کارت‌های بعدی اسلحه‌ها به ترتیب هستند
			var first_id := String(Weapons.LIST[0]["id"])
			var card: Button = instance._list.get_child(1)
			card.pressed.emit()
			check(instance._overlay.visible and instance._selected_id == first_id,
				"پنجرهٔ خرید و ارتقای اسلحه باز نشد")
			var texts: Array = []
			_collect_labels(instance._detail, texts)
			check(_find_label(texts, "هزینهٔ ساخت در نبرد") >= 0, "قیمت در جزئیات سلاح نیست")
			if Game.can_upgrade_weapon(first_id):
				check(_find_label(texts, "با ارتقا به سطح") >= 0, "پیش‌نمایش ارتقا در جزئیات سلاح نیست")
				check(_find_label(texts, "قدرت شلیک") >= 0, "فاکتورهای ارتقا در جزئیات سلاح نیستند")
				# فقط مقدار افزایش هر فاکتور (سبز و با نشان «+») باید بیاید.
				var plus := 0
				for text in texts:
					if String(text).begins_with("+"):
						plus += 1
				check(plus == 3, "سه فاکتور ارتقا با مقدار افزایش نمایش داده نشده‌اند: %d" % plus)
			instance._close_detail()
			check(not instance._overlay.visible, "پنجرهٔ اسلحه بسته نشد")
			# کارت مسلسل سنگین (برجک رایگان) باید در زرادخانه باز و بدون قفل ستاره باشد
			check(Game.is_weapon_unlocked("machinegun"), "مسلسل سنگین باید در زرادخانه از ابتدا باز باشد")

			# تست کارکرد آیتم افزایش اعتبار (+۱۰ سکه به سکهٔ شروع نبرد به ازای هر سطح)
			var old_p_lvl := Game.prestige_level
			var old_stars := Game.stars
			Game.prestige_level = 0
			Game.stars = 10
			check(Game.prestige_starting_coins() == 0, "در سطح ۰ افزایش اعتبار باید ۰ سکه اضافه کند")
			check(Game.upgrade_prestige() and Game.prestige_level == 1 and Game.prestige_starting_coins() == 10,
				"هر سطح ارتقای افزایش اعتبار باید ۱۰ سکه به سکهٔ شروع اضافه کند")
			Game.prestige_level = old_p_lvl
			Game.stars = old_stars
		if path.ends_with("Market.tscn"):
			var texts: Array = []
			_collect_labels(instance, texts)
			check(_find_label(texts, "بازار") >= 0, "عنوان بازار در صحنهٔ بازار نیست")
			check(_find_label(texts, "ستاره رایگان") >= 0 and _find_label(texts, "۵ ستاره با دیدن ۲ تبلیغ") >= 0,
				"گزینهٔ «ستاره رایگان» (۵ ستاره با دیدن ۲ تبلیغ) در بازار نیست")
			check(_find_label(texts, "۲۰ ستاره") >= 0 and _find_label(texts, "۱۰ هزار تومان") >= 0,
				"بستهٔ ۲۰ ستاره به قیمت ۱۰ هزار تومان در بازار نیست")
			check(_find_label(texts, "۸۰ ستاره") >= 0 and _find_label(texts, "۵۰ هزار تومان") >= 0,
				"بستهٔ ۸۰ ستاره به قیمت ۵۰ هزار تومان در بازار نیست")
			check(_find_label(texts, "۱۵۰ ستاره") >= 0 and _find_label(texts, "۱۰۰ هزار تومان") >= 0,
				"بستهٔ ۱۵۰ ستاره به قیمت ۱۰۰ هزار تومان در بازار نیست")
			var market_screen := Rect2(Vector2.ZERO, get_viewport().get_visible_rect().size)
			check(instance._cards_row != null and instance._cards_row.get_child_count() == 4,
				"ردیف بازار باید ۴ کارت (ستاره رایگان + ۳ بسته) داشته باشد")
			for cidx in instance._cards_row.get_child_count():
				var card_ctrl := instance._cards_row.get_child(cidx) as Control
				check(card_ctrl != null and market_screen.encloses(card_ctrl.get_global_rect()),
					"کارت شمارهٔ %d بازار از صفحهٔ ۱۲۸۰×۷۲۰ بیرون زده است: %s" % [cidx, card_ctrl.get_global_rect() if card_ctrl != null else Rect2()])
			check(instance._notice_label != null and market_screen.encloses(instance._notice_label.get_global_rect()),
				"نوار پیام پایین بازار از صفحهٔ ۱۲۸۰×۷۲۰ بیرون زده است")
			var saved_mkt_stars: int = Game.stars
			var saved_mkt_ads: int = Game.free_star_ads_watched
			Game.stars = 10
			Game.free_star_ads_watched = 0
			instance._refresh_market_ui()
			var free_btn := instance.find_child("FreeStarButton", true, false) as Button
			check(free_btn != null, "دکمهٔ مشاهدهٔ تبلیغ ستاره رایگان پیدا نشد")
			if free_btn != null:
				free_btn.pressed.emit()
				check(Game.stars == 10 and Game.free_star_ads_watched == 1,
					"پس از دیدن تبلیغ اول هنوز نباید ستاره داده شود و شمارنده باید ۱ از ۲ شود")
				free_btn.pressed.emit()
				check(Game.stars == 15 and Game.free_star_ads_watched == 0,
					"پس از دیدن تبلیغ دوم باید دقیقاً ۵ ستارهٔ رایگان اضافه شود و شمارنده صفر شود")
			Game.stars = saved_mkt_stars
			Game.free_star_ads_watched = saved_mkt_ads
			Game.save()
		if path.ends_with("Settings.tscn"):
			var texts: Array = []
			_collect_labels(instance, texts)
			check(_find_label(texts, "سازندگان: مهدی زارعی و گروه PreGames Studio") >= 0,
				"خط سازندگان در شناسنامهٔ تنظیمات نیست")
			check(_find_label(texts, "ارتباط با ما : gf2227@gmail.com") >= 0,
				"خط ارتباط با ما در شناسنامهٔ تنظیمات نیست")
			var about_lbl := instance.find_child("AboutText", true, false) as Label
			check(about_lbl != null and about_lbl.get_theme_font("font") == Style.font_latin and Style.font_latin != null,
				"برچسب شناسنامه باید از فونت با ارقام انگلیسی (font_latin) استفاده کند تا gf2227@gmail.com انگلیسی دیده شود")
			if about_lbl != null and Style.font_latin != null:
				var ts := TextServerManager.get_primary_interface()
				var rid: RID = Style.font_latin.get_rids()[0]
				check(ts.font_get_glyph_index(rid, 17, "2".unicode_at(0), 0) != ts.font_get_glyph_index(rid, 17, "۲".unicode_at(0), 0)
					and ts.font_get_glyph_index(rid, 17, "7".unicode_at(0), 0) != ts.font_get_glyph_index(rid, 17, "۷".unicode_at(0), 0),
					"گلیف ارقام 2 و 7 در ایمیل تنظیمات نباید با گلیف فارسی ۲ و ۷ یکی باشد")
			check(_find_label(texts, "موتور: Godot") < 0, "خط موتور باید از شناسنامه پاک شده باشد")
			check(_find_label(texts, "فونت: وزیرمتن") < 0, "خط فونت باید از شناسنامه پاک شده باشد")
		if path.ends_with("Battle.tscn"):
			check(instance.hud != null and instance.hud._coins_label is Button and instance.hud._lives_label is Button
				and instance.hud._wave_label is Button and instance.hud._enemies_label is Button,
				"اطلاعات نوار بالای نبرد باید به‌صورت دکمه نمایش داده شوند")
			check(instance.hud != null and instance.hud.size.y >= 700.0,
				"ابعاد HUD نبرد نادرست است: %s" % [instance.hud.size if instance.hud != null else Vector2.ZERO])
			check(instance.hud != null and instance.hud._weapon_buttons.size() == Game.unlocked_weapons.size(),
				"برجک‌های خریداری‌شده در نوار پایین‌چپ نمایش داده نشدند")
			var first_button: Button = instance.hud._weapon_buttons[instance.selected_weapon]
			var price: Label = first_button.find_child("price", true, false)
			var build_cost := int(Weapons.by_id(instance.selected_weapon)["cost"])
			check(price != null and price.text == Ui.fa(build_cost), "قیمت کنار آیکن سلاح نیست")
			var saved_coins: int = instance.coins
			instance.coins = build_cost - 1
			instance.hud.refresh()
			check(not first_button.get_meta("affordable", true), "سلاح گران نباید رنگ قابل‌خرید داشته باشد")
			instance.coins = build_cost
			instance.hud.refresh()
			check(first_button.get_meta("affordable", false), "سلاح پس از رسیدن سکه باید رنگی شود")
			instance.coins = saved_coins
			check(not instance.selected_weapon.is_empty(), "سلاح پیش‌فرض در شروع نبرد انتخاب نشده است")
			var built := false
			var built_pos := Vector2.ZERO
			for y in range(120, 620, 40):
				for x in range(160, 1120, 40):
					var pt := Vector2(x, y)
					if instance.map.build_issue(pt, instance.occupied).is_empty():
						instance.begin_weapon_drag(instance.selected_weapon, Vector2(180.0, 660.0))
						check(instance.ghost != null and instance.ghost.visible,
							"برجک در لحظهٔ شروع کشیدن از روی نوار ابزار باید مرئی باشد")
						check(instance.ghost._base != null and instance.ghost._base.texture != null,
							"تصویر برجک در لحظهٔ کشیدن باید بارگذاری شده باشد")
						instance._update_ghost(pt)
						check(instance.ghost.visible and instance.ghost.ok,
							"برجک در حین کشیدن روی نقطهٔ مجاز باید مرئی و سبز باشد")
						instance._finish_drag(pt)
						built = instance.towers.size() == 1
						built_pos = pt
						break
				if built:
					break
			check(built, "کشیدن و رهاکردن برجک در جای آزاد نقشه انجام نشد")
			# بررسی جلوگیری از رهاکردن در محدودهٔ برج دیگر و داخل جاده
			instance.coins = 500
			instance.begin_weapon_drag(instance.selected_weapon)
			instance._finish_drag(built_pos + Vector2(12.0, 0.0))
			check(instance.towers.size() == 1, "نباید اجازهٔ رهاکردن برجک داخل محدودهٔ برج دیگر داده شود")
			var road_pt: Vector2 = instance.map.point_at(instance.map.total_length * 0.5)
			instance.begin_weapon_drag(instance.selected_weapon)
			instance._finish_drag(road_pt)
			check(instance.towers.size() == 1, "نباید اجازهٔ رهاکردن برجک داخل محدودهٔ جاده داده شود")

			# لمس ساده روی برجک موجود (حتی وقتی سلاح آماده است) باید همان برجک را انتخاب و پنل ارتقا را باز کند
			var armed_id: String = instance.selected_weapon
			var first_tower = instance.towers[0]
			instance.coins = 500
			instance._handle_build_press(true, built_pos + Vector2(0.0, -24.0))
			instance._handle_build_press(false, built_pos + Vector2(0.0, -24.0))
			check(instance.selected_tower == first_tower and instance.selected_weapon.is_empty() and instance.hud._tower_panel.visible,
				"لمس ساده روی برجک کاشته‌شده باید همان برجک را انتخاب و پنل ارتقا را باز کند")
			check(instance.towers.size() == 1, "لمس روی برجک موجود نباید برجک تازه بکارد")
			# لمس روی فضای آزاد هنگامی که پنل برجک باز است باید انتخاب برجک را ببندد
			var free_pt := Vector2.ZERO
			for y in range(120, 620, 40):
				for x in range(160, 920, 40):
					var pt := Vector2(x, y)
					if pt.distance_to(built_pos) > 120.0 and instance.map.build_issue(pt, instance.occupied).is_empty() and instance.map.build_issue(pt + Vector2(30.0, 0.0), instance.occupied).is_empty():
						free_pt = pt
						break
				if free_pt != Vector2.ZERO:
					break
			check(free_pt != Vector2.ZERO, "جای آزاد برای آزمون کاشت پیدا نشد")
			instance._handle_build_press(true, free_pt)
			instance._handle_build_press(false, free_pt)
			check(instance.selected_tower == null and not instance.hud._tower_panel.visible,
				"لمس فضای خالی باید پنل برجک انتخاب‌شده را ببندد")
			# لمس ساده در جای آزاد هنگام آماده بودن سلاح، کاشت نمی‌کند
			instance.selected_weapon = armed_id
			instance._handle_build_press(true, free_pt)
			instance._handle_build_press(false, free_pt)
			check(instance.towers.size() == 1 and instance.selected_weapon == armed_id, "لمس ساده در فضای آزاد نباید برجک بکارد")
			# کاشت با گرفتن، کشیدن و رهاکردن در جای مجاز
			instance._handle_build_press(true, free_pt)
			instance._handle_build_press(false, free_pt + Vector2(30.0, 0.0))
			check(instance.towers.size() == 2 and instance.selected_weapon == armed_id, "کاشت با کشیدن و رهاکردن انجام نشد")
			# لغو با رها کردن روی دکمهٔ آمادهٔ نوار
			var bar_center: Vector2 = instance.hud._weapon_panel.get_global_rect().get_center()
			instance.begin_weapon_drag(armed_id, bar_center)
			instance._finish_drag(bar_center)
			check(instance.selected_weapon.is_empty(), "رهاکردن روی دکمهٔ آمادهٔ نوار باید کاشت را لغو کند")
			instance.selected_weapon = armed_id
			# کشیدن روی برجک کاشته‌شده هم همچنان همان برجک را انتخاب می‌کند
			var tower = instance.towers[0]
			instance._handle_build_press(true, built_pos + Vector2(0.0, -24.0))
			instance._handle_build_press(false, built_pos + Vector2(40.0, -24.0))
			check(instance.selected_tower == tower and instance.selected_weapon.is_empty(), "گرفتن و کشیدن برجک باید آن را انتخاب کند")
			# ارتقا از همان مسیر واقعی انتخاب (برجک انتخاب‌شده) انجام می‌شود
			var level_before: int = tower.level
			instance.coins = 500
			instance.upgrade_selected()
			check(tower.level == level_before + 1 and instance.coins == 500 - int(tower.weapon["cost"]),
				"ارتقای برجک انتخاب‌شده باید سطح و سکه را تغییر دهد")
			# لمس ساده روی برجک بدون سلاح آماده نیز آن را انتخاب می‌کند
			instance.select_tower(null)
			instance.selected_weapon = ""
			instance._handle_build_press(true, built_pos + Vector2(0.0, -24.0))
			instance._handle_build_press(false, built_pos + Vector2(0.0, -24.0))
			check(instance.selected_tower == tower and instance.hud._tower_panel.visible,
				"لمس ساده روی برجک بدون سلاح آماده باید پنل ارتقا را باز کند")
			instance.select_tower(null)
			instance.selected_weapon = armed_id
			# بررسی نمایش کارت شکست هنگام تمام‌شدن جان دژ و کارت پیروزی پس از دفع ۳۰ موج
			var saved_best := Game.best.duplicate(true)
			var saved_maps := Game.unlocked_maps.duplicate()
			var saved_stars := Game.stars
			var saved_last_map := Game.last_map
			instance.keep.take_damage(instance.lives)
			check(instance.state == "lost" and not instance.running,
				"با تمام‌شدن خون قلعه باید وضعیت شکست ثبت شود")
			check(instance.hud._result_overlay.visible,
				"لایهٔ نتیجه پس از شکست باید نمایش داده شود")
			check(instance.hud._result_image != null and instance.hud._result_image.texture == Assets.tex("ui/defeat_background"),
				"در حالت شکست باید تصویر شکست نمایش داده شود")
			check(instance.hud._result_card != null and instance.hud._result_card.size.x < Balance.WORLD_SIZE.x and instance.hud._result_card.size.y < Balance.WORLD_SIZE.y,
				"کارت نتیجه باید به ابعاد کوچک‌تر در وسط صفحه نمایش داده شود")
			check(instance.hud._result_card.pressed.is_connected(instance.hud._on_result_card_pressed),
				"کلیک روی کارت نتیجه باید به بازگشت به صفحهٔ نقشه‌ها وصل باشد")
			instance.state = "wave"
			instance.running = true
			instance.lives = Balance.START_LIVES
			instance.wave_index = Balance.WAVES
			instance.spawn_queue.clear()
			instance.enemies.clear()
			instance._end_wave()
			check(instance.state == "won" and not instance.running,
				"پس از دفع ۳۰ موج دشمن باید وضعیت پیروزی ثبت شود")
			check(instance.hud._result_overlay.visible and instance.hud._result_image.texture == Assets.tex("ui/victory_background"),
				"در حالت پیروزی باید تصویر پیروزی نمایش داده شود")
			Game.best = saved_best
			Game.unlocked_maps = saved_maps
			Game.stars = saved_stars
			Game.last_map = saved_last_map
			Game.save()
		print("صحنه ساخته شد: %s" % path.get_file())
		instance.queue_free()
		await get_tree().process_frame

func check(condition: bool, message: String) -> void:
	checks += 1
	if not condition:
		failures.append(message)

## همهٔ متن‌های برچسب‌های یک صحنه را جمع می‌کند تا بتوان وجود یا نبود یک نوشته را سنجید.
func _collect_labels(node: Node, out: Array) -> void:
	if node is Label:
		out.append((node as Label).text)
	for child in node.get_children():
		_collect_labels(child, out)

func _collect_buttons(node: Node, out: Array) -> void:
	if node is Button:
		out.append((node as Button).text)
	for child in node.get_children():
		_collect_buttons(child, out)

## نمایهٔ نخستین برچسبی که نوشتهٔ خواسته‌شده را در خود دارد؛ ‎−۱ یعنی پیدا نشد.
func _find_label(texts: Array, needle: String) -> int:
	for index in texts.size():
		if String(texts[index]).contains(needle):
			return index
	return -1

func _report() -> void:
	print("بررسی‌ها: %d" % checks)
	if failures.is_empty():
		print("نتیجه: موفق")
		get_tree().quit(0)
	else:
		printerr("خطاها: %d" % failures.size())
		for item in failures:
			printerr("  ✗ %s" % item)
		get_tree().quit(1)

# ------------------------------------------------------------------ پنجرهٔ تأییدیه

## پنجرهٔ تأییدیهٔ خروج: «بله» اجرا و بسته می‌شود، «انصراف» فقط بسته و on_cancel اجرا می‌شود.
func _test_confirm_dialog() -> void:
	var host := Control.new()
	add_child(host)
	var asked := [false]
	var cancelled := [false]
	var overlay := Ui.confirm(host, "تست", "متن", func(): asked[0] = true, func(): cancelled[0] = true)
	check(host.get_node_or_null("ConfirmOverlay") == overlay, "پنجرهٔ تأییدیه باید به میزبان اضافه شود")
	check(Ui.confirm(host, "تست", "متن", func(): pass) == overlay, "پنجرهٔ تأییدیه نباید دوبار باز شود")
	var accept_button := _find_button(overlay, "بله")
	var cancel_button := _find_button(overlay, "انصراف")
	check(accept_button != null and cancel_button != null, "دکمه‌های «بله» و «انصراف» در تأییدیه نیستند")
	if accept_button != null and cancel_button != null:
		cancel_button.pressed.emit()
		check(cancelled[0] and not asked[0] and overlay.is_queued_for_deletion(),
			"«انصراف» باید پنجره را ببندد و کاری را اجرا نکند")
		var second_host := Control.new()
		add_child(second_host)
		overlay = Ui.confirm(second_host, "تست", "متن", func(): asked[0] = true)
		accept_button = _find_button(overlay, "بله")
		accept_button.pressed.emit()
		check(asked[0] and overlay.is_queued_for_deletion(), "«بله» باید کار را اجرا و پنجره را ببندد")
		second_host.queue_free()
	host.queue_free()

func _find_button(node: Node, text: String) -> Button:
	if node is Button and (node as Button).text == text:
		return node as Button
	for child in node.get_children():
		var found := _find_button(child, text)
		if found != null:
			return found
	return null

# ------------------------------------------------------------------ دارایی‌ها

func _test_assets() -> void:
	check(Assets.keys().size() > 100, "فهرست تصویرها کمتر از حد انتظار است: %d" % Assets.keys().size())
	for key in Assets.keys():
		var texture := Assets.tex(String(key))
		check(texture != null, "تصویر بارگذاری نشد: %s" % key)
	for key in REQUIRED_UI_TEXTURES:
		check(Assets.has_key(key), "تصویر واسط گم شده: %s" % key)
	for sound in REQUIRED_SOUNDS:
		check(Assets.sound(sound) != null, "صدا گم شده: %s" % sound)
	for category in Audio.WEAPON_SOUND_VARIANTS.keys():
		var variants: Array = Audio.WEAPON_SOUND_VARIANTS[category]
		check(not variants.is_empty(), "گروه شلیک خالی است: %s" % category)
		for sound in variants:
			check(Assets.sound_keys().has(sound), "نمونهٔ شلیک گم شده: %s" % sound)
	for sound in RETIRED_COMBAT_SOUNDS:
		check(not Assets.sound_keys().has(sound), "صدای رزمی قدیمی هنوز در مانیفست است: %s" % sound)
	for biome_key in Maps.BIOMES.keys():
		var biome: Dictionary = Maps.BIOMES[biome_key]
		check(Assets.has_key(String(biome["ground"])), "زمین زیست‌بوم %s گم شده" % biome_key)
		check(Assets.has_key(String(biome["alt"])), "زمین جایگزین %s گم شده" % biome_key)
		for prop in biome["props"]:
			check(Assets.has_key(String(prop)), "دکور گم شده: %s" % prop)

## شیت‌های افکت کارتونی (tools/gen_fx_sheets.py): هشت شیت افقی با فریم‌های ۶۴×۶۴
## که Fx.sheet_frames آن‌ها را برش می‌دهد. اگر یکی گم شود یا ابعادش با جدول
## نخواند، جلوه‌های شلیک و انفجار بی‌صدا ناپدید می‌شوند؛ پس این‌جا صریح بررسی می‌شود.
func _test_fx_sheets() -> void:
	check(Fx.SHEETS.size() == 8, "باید هشت شیت افکت باشد، هست: %d" % Fx.SHEETS.size())
	for key in Fx.SHEETS.keys():
		var sheet_key := String(key)
		var info: Dictionary = Fx.SHEETS[key]
		check(Assets.has_key(sheet_key), "شیت افکت در مانیفست نیست: %s" % sheet_key)
		var texture := Assets.tex(sheet_key)
		check(texture != null, "شیت افکت بارگذاری نشد: %s" % sheet_key)
		if texture == null:
			continue
		check(texture.get_height() == Fx.FRAME_PX,
			"ارتفاع شیت %s باید %d پیکسل باشد، هست %d" % [sheet_key, Fx.FRAME_PX, texture.get_height()])
		check(texture.get_width() == int(info["frames"]) * Fx.FRAME_PX,
			"عرض شیت %s با %d فریم نمی‌خواند" % [sheet_key, int(info["frames"])])
		var frames := Fx.sheet_frames(sheet_key)
		check(frames != null, "فریم‌های شیت ساخته نشد: %s" % sheet_key)
		if frames != null:
			check(frames.get_frame_count("play") == int(info["frames"]),
				"تعداد فریم ساخته‌شدهٔ %s با جدول نمی‌خواند" % sheet_key)
	# هر جلوه باید واقعاً گره بسازد؛ وگرنه شلیک و انفجار در عمل بی‌جلوه است.
	var stage := Node2D.new()
	add_child(stage)
	Fx.blast(stage, Vector2.ZERO, 1.2)
	Fx.muzzle(stage, Vector2.ZERO, 0.3, 0.8)
	Fx.smoke(stage, Vector2.ZERO, 0.6)
	Fx.flame(stage, Vector2.ZERO, 0.6)
	Fx.beam(stage, "fx/laser_beam", Vector2.ZERO, Vector2(120.0, 0.0), 0.8)
	Fx.bolt(stage, Vector2.ZERO, Vector2(80.0, 40.0), 0.8)
	await get_tree().process_frame
	check(stage.get_child_count() == 6,
		"جلوه‌های شیت ساخته نشدند؛ تعداد گره: %d" % stage.get_child_count())
	stage.queue_free()

# ------------------------------------------------------------------ آیکون‌های اپ

## آیکون‌های لانچر هم باید موجود و هم در تنظیمات خروجی ارجاع شده باشند؛
## بی‌آن‌ها اندروید آیکون پیش‌فرض خودش را نشان می‌دهد.
func _test_app_icons() -> void:
	for icon in LAUNCHER_ICONS:
		var path := String(icon["path"])
		var texture = load(path)
		check(texture != null, "آیکون اپ بارگذاری نشد: %s" % path)
		if texture != null:
			# اندازهٔ بافت Vector2 است و اندازهٔ انتظاری Vector2i؛ پیش از مقایسه
			# باید یکی شوند، وگرنه موتور خطای عملوند می‌دهد.
			var actual := Vector2i(texture.get_size())
			var expected := Vector2i(icon["size"])
			check(actual == expected,
				"اندازهٔ آیکون %s باید %s باشد، هست %s" % [path, expected, actual])

	var config := ConfigFile.new()
	if config.load("res://export_presets.cfg") != OK:
		check(false, "export_presets.cfg خوانده نشد")
		return
	for key in LAUNCHER_KEYS:
		var value := String(config.get_value("preset.0.options", key, ""))
		check(not value.is_empty(), "آیکون اپ در تنظیمات خروجی خالی است: %s" % key)
		if not value.is_empty():
			check(ResourceLoader.exists(value), "فایل آیکون تنظیمات پیدا نشد: %s" % value)

# ------------------------------------------------------------------ صفحهٔ آغازین

## پیش از لوگوی استودیو نباید تصویر پیش‌فرض موتور دیده شود.
func _test_launch_presentation() -> void:
	check(not bool(ProjectSettings.get_setting("application/boot_splash/show_image", true)),
		"تصویر پیش‌فرض موتور پیش از لوگوی استودیو باید خاموش باشد")

# ------------------------------------------------------------------ سلاح‌ها

func _test_weapons() -> void:
	check(Weapons.count() == 4, "باید چهار برجک ایزومتریک تعریف شده باشد (سنگر حذف شده): %d" % Weapons.count())
	check(Weapons.ids() == ["machinegun", "cannon", "rocket", "laser"],
		"ترتیب برجک‌ها باید مسلسل، خمپاره، موشک و لیزر باشد: %s" % [Weapons.ids()])
	var iso_kinds: Array = []
	var seen: Array = []
	for weapon in Weapons.LIST:
		var id := String(weapon["id"])
		check(not seen.has(id), "شناسهٔ تکراری سلاح: %s" % id)
		seen.append(id)
		check(int(weapon.get("cost", 0)) > 0, "قیمت نامعتبر در سلاح %s" % id)
		check(int(weapon.get("star_cost", -1)) >= 0, "هزینهٔ ستارهٔ نامعتبر در سلاح %s" % id)
		check(float(weapon.get("damage", 0.0)) > 0.0, "آسیب نامعتبر در سلاح %s" % id)
		check(float(weapon.get("range", 0.0)) > 40.0, "برد نامعتبر در سلاح %s" % id)
		check(float(weapon.get("cooldown", 0.0)) > 0.0, "فاصلهٔ شلیک نامعتبر در سلاح %s" % id)
		var kind := String(weapon.get("kind", ""))
		check(VALID_KINDS.has(kind), "نوع نامعتبر در سلاح %s: %s" % [id, kind])
		check(Assets.has_key(Weapons.art_key(id)), "تصویر برجک %s گم شده" % id)
		check(Assets.has_key("towers/muzzle_1") and Assets.has_key("towers/muzzle_2"), "شعلهٔ دهانهٔ پشتیبان برجک گم شده")
		if kind == "splash":
			check(float(weapon.get("splash", 0.0)) > 0.0, "شعاع ناحیه‌ای در %s صفر است" % id)
		if kind == "chain":
			check(int(weapon.get("chain", 0)) >= 2, "زنجیرهٔ %s کمتر از دو است" % id)
		if weapon.has("bullet"):
			check(Assets.has_key("bullets/%s" % String(weapon["bullet"])), "گلولهٔ %s گم شده" % id)
		check(Audio.WEAPON_SOUND_VARIANTS.has(String(weapon.get("sound", "shot_bolt"))),
			"صدای شلیک %s در گروه‌های صدا نیست" % id)
		var kind_iso := Weapons.iso_kind(id)
		check(not kind_iso.is_empty(), "همهٔ برجک‌ها باید ایزومتریک باشند: %s" % id)
		if kind_iso.is_empty():
			continue
		# برجک ایزومتریک: رایگان (فقط مسلسل) یا خریدنی با ستاره، با هر سه شیت پک و تصویر نمایشیِ برش‌خورده
		iso_kinds.append(kind_iso)
		check(IsoSheet.TOWER_KINDS.has(kind_iso), "برجک %s به برجکی از پک towers_iso اشاره نمی‌کند" % id)
		check((int(weapon.get("star_cost", 0)) == 0) == Weapons.FREE_IDS.has(id),
			"فقط برجک رایگان بدون ستاره است و بقیه با ستاره خریدنی‌اند: %s" % id)
		check(Weapons.art_key(id) == IsoSheet.tower_key(kind_iso, "idle"), "کلید تصویر %s باید شیت idle پک باشد" % id)
		for action in IsoSheet.TOWER_ACTIONS.keys():
			check(Assets.has_key(IsoSheet.tower_key(kind_iso, String(action))),
				"شیت %s برجک %s گم شده" % [action, id])
		var icon := Weapons.icon(id)
		check(icon is AtlasTexture and (icon as AtlasTexture).region == IsoSheet.region(Weapons.ICON_DIR, 0),
			"تصویر نمایشی %s باید فریم ۰ یک جهت باشد، نه کل شیت" % id)
	iso_kinds.sort()
	var pack_kinds: Array = IsoSheet.TOWER_KINDS.duplicate()
	pack_kinds.sort()
	check(iso_kinds == pack_kinds, "هر چهار برجک پک باید دقیقاً یک بار خریدنی باشند: %s" % [iso_kinds])
	check(Weapons.by_id("sangar").is_empty(), "سنگر باید از فهرست برجک‌ها حذف شده باشد")
	check(not Assets.has_key("towers/sangar") and not Assets.has_key("towers/sangar_soldier"),
		"تصویرهای سنگر و سرباز سنگر باید از پروژه حذف شده باشند")
	check(Weapons.by_id("machine_gun_lv1").is_empty(), "سلاح‌های قدیمی باید به‌طور کامل حذف شده باشند")
	check(Weapons.by_id("omega").is_empty(), "ابرسلاح قدیمی omega باید حذف شده باشد")
	check(Weapons.by_id("nope").is_empty(), "شناسهٔ ناشناخته باید دیکشنری خالی بدهد")
	check(Weapons.FREE_IDS == ["machinegun"], "مسلسل سنگین باید تنها برجک رایگان باشد")
	check(int(Weapons.by_id("machinegun")["star_cost"]) == 0 and int(Weapons.by_id("machinegun")["cost"]) == 100,
		"مسلسل سنگین باید بدون ستاره و با همان قیمت ۱۰۰ سکه باشد")
	check(int(Weapons.by_id("cannon")["star_cost"]) == 5 and int(Weapons.by_id("rocket")["star_cost"]) == 7
		and int(Weapons.by_id("laser")["star_cost"]) == 9, "ستارهٔ خمپاره، موشک و لیزر نباید عوض شود (۵، ۷، ۹)")
	check(Game.is_weapon_unlocked("machinegun"), "مسلسل سنگین باید به‌صورت پیش‌فرض باز باشد")
	check(Game.default_unlocked_weapons() == ["machinegun"], "برجک‌های باز پیش‌فرض باید فقط مسلسل باشد")
	check(int(Weapons.by_id("machinegun")["cost"]) <= Balance.START_COINS,
		"برجک رایگان باید با سکهٔ شروع نبرد قابل خرید باشد؛ وگرنه نبرد بدون برجک گیر می‌کند")


# ------------------------------------------------------------------ دشمنان

func _test_enemies() -> void:
	check(Enemies.LIST.size() == 5, "باید ۵ نیروی دشمن تعریف شده باشد (۲ پیاده + ۳ تانک): %d" % Enemies.LIST.size())
	check(Enemies.by_id("flyer").is_empty(), "هلیکوپتر (flyer) باید حذف شده باشد")
	check(Enemies.by_id("warlord").is_empty(), "قطار قرمز (warlord) باید حذف شده باشد")
	var seen: Array = []
	for enemy in Enemies.LIST:
		var id := String(enemy["id"])
		check(not seen.has(id), "شناسهٔ تکراری دشمن: %s" % id)
		seen.append(id)
		check(float(enemy.get("hp", 0.0)) > 0.0, "جان نامعتبر در دشمن %s" % id)
		check(float(enemy.get("speed", 0.0)) > 0.0, "سرعت نامعتبر در دشمن %s" % id)
		check(int(enemy.get("reward", 0)) > 0, "پاداش نامعتبر در دشمن %s" % id)
		check(["reach", "laser"].has(String(enemy.get("attack", ""))), "نوع حملهٔ دشمن %s نامعتبر است" % id)
		if enemy.has("iso"):
			for action in IsoSheet.TANK_ACTIONS.keys():
				check(Assets.has_key("units_iso_%s/%s_%s" % [enemy["iso"], enemy["iso"], action]),
					"شیت %s دشمن %s گم شده" % [action, id])
		else:
			check(Assets.has_key(String(enemy.get("sprite", ""))), "اسپرایت دشمن %s گم شده" % id)
			check(Assets.has_key(String(enemy.get("sprite", "")) + "_walk"), "شیت انیمیشن راه رفتن دشمن %s گم شده" % id)
		for feature in enemy.get("traits", []):
			check(VALID_TRAITS.has(String(feature)), "ویژگی ناشناختهٔ %s در دشمن %s" % [feature, id])
	# بررسی دقیق آمار و تمایز دیداری هر ۵ نیرو (۲ سرباز + ۳ تانک)
	var soldier := Enemies.by_id("soldier")
	var commando := Enemies.by_id("commando")
	check(not soldier.is_empty() and not commando.is_empty(), "سرباز و تکاور باید هر دو در جدول دشمنان باشند")
	check(float(soldier["hp"]) == 60.0 and float(commando["hp"]) == 100.0,
		"جان پایهٔ سرباز باید ۶۰ و تکاور ۱۰۰ باشد")
	check(float(commando["speed"]) == float(soldier["speed"])
		and int(commando["reward"]) == int(soldier["reward"])
		and String(commando["attack"]) == "reach",
		"تکاور باید رفتار، سرعت و اسکریپت یکسان با سرباز داشته باشد")
	check(_alpha_diff_pixels("units/run/commando_front", "units/run/soldier_front") > 1500,
		"شکل و سیلوئت تکاور باید واقعاً با سرباز معمولی متفاوت باشد، نه صرفاً تغییر رنگ")

	var expected_tanks := [["tank", 420.0], ["tank_armored", 600.0], ["tank_heavy", 800.0]]
	for spec in expected_tanks:
		var tid := String(spec[0])
		var want_hp := float(spec[1])
		var tdef := Enemies.by_id(tid)
		check(not tdef.is_empty(), "تانک %s در جدول دشمنان نیست" % tid)
		if tdef.is_empty():
			continue
		check(float(tdef["hp"]) == want_hp and float(tdef["speed"]) == 28.0 and float(tdef["armor"]) == 4.0
			and int(tdef["reward"]) == 12,
			"آمار %s باید جان %.0f، سرعت ۲۸، زره ۴ و پاداش ۱۲ باشد" % [tid, want_hp])
		check(String(tdef.get("iso", "")) == tid and String(tdef.get("attack", "")) == "laser",
			"تانک %s باید از شیت ایزومتریک خودش بیاید و با لیزر حمله کند" % tid)
		check(int(tdef.get("keep_damage", 0)) == 1,
			"تانک %s باید در تک‌شلیک خود یک جان از دژ کم کند" % tid)
	check(_alpha_diff_pixels("units_iso_tank/tank_move", "units_iso_tank_armored/tank_armored_move") > 15000
		and _alpha_diff_pixels("units_iso_tank/tank_move", "units_iso_tank_heavy/tank_heavy_move") > 15000
		and _alpha_diff_pixels("units_iso_tank_armored/tank_armored_move", "units_iso_tank_heavy/tank_heavy_move") > 15000,
		"هر سه تانک باید شکل بدنه و سیلوئت ظاهری کاملاً متمایز داشته باشند، نه صرفاً تغییر رنگ")

func _alpha_diff_pixels(key_a: String, key_b: String) -> int:
	var img_a := Assets.tex(key_a).get_image()
	var img_b := Assets.tex(key_b).get_image()
	if img_a == null or img_b == null or img_a.get_size() != img_b.get_size():
		return -1
	var diff := 0
	var w := img_a.get_width()
	var h := img_a.get_height()
	for y in range(h):
		for x in range(w):
			var ma := img_a.get_pixel(x, y).a > 0.12
			var mb := img_b.get_pixel(x, y).a > 0.12
			if ma != mb:
				diff += 1
	return diff

# ------------------------------------------------------------------ نقشه‌ها

# ------------------------------------------------------------------ شیت‌های دویدن و مرگ

func _test_unit_sheets() -> void:
	for enemy in Enemies.LIST:
		if enemy.has("iso"):
			continue  # تانک شیت‌های شبکه‌ای خودش را دارد (_test_iso_sheets)
		var id := String(enemy["id"])
		var speed := float(enemy.get("speed", 0.0))
		for dir in UnitSheets.DIRS:
			var run_key := UnitSheets.tex_key(id, "run", dir)
			var death_key := UnitSheets.tex_key(id, "death", dir)
			check(Assets.has_key(run_key), "شیت دویدن گم شده: %s" % run_key)
			check(Assets.has_key(death_key), "شیت مرگ گم شده: %s" % death_key)
			var run_tex := Assets.tex(run_key)
			var death_tex := Assets.tex(death_key)
			if run_tex == null or death_tex == null:
				continue
			check(run_tex.get_width() == 8 * FRAME_PX,
				"عرض شیت دویدن %s با ۸ فریم ۶۴ پیکسلی نمی‌خواند" % run_key)
			check(run_tex.get_height() == int(UnitSheets.frame_height(id, "run")),
				"ارتفاع شیت دویدن %s با جدول شیت‌ها نمی‌خواند" % run_key)
			check(death_tex.get_width() == UnitSheets.DEATH_FRAMES * FRAME_PX,
				"عرض شیت مرگ %s با ۶ فریم ۶۴ پیکسلی نمی‌خواند" % death_key)
			check(death_tex.get_height() == int(UnitSheets.frame_height(id, "death")),
				"ارتفاع شیت مرگ %s با جدول شیت‌ها نمی‌خواند" % death_key)
			var ground := UnitSheets.ground(id, "death")
			check(ground > 0.0 and ground < death_tex.get_height(),
				"خط زمین شیت مرگ %s بیرون از فریم است" % death_key)
		check(UnitSheets.uses_run(id, speed) == false,
			"تشخیص تندپا برای %s درست نیست" % id)

	# جهت‌ها روی صفحه: پایین = رو به دوربین، راست = رو به راست
	check(UnitSheets.direction_name(Vector2(0.0, 1.0)) == "front", "جهت پایین باید front باشد")
	check(UnitSheets.direction_name(Vector2(0.0, -1.0)) == "back", "جهت بالا باید back باشد")
	check(UnitSheets.direction_name(Vector2(1.0, 0.2)) == "right", "جهت راست باید right باشد")
	check(UnitSheets.direction_name(Vector2(-1.0, 0.2)) == "left", "جهت چپ باید left باشد")

	# سرباز و تکاور هر دو با شیت دویدن (۸ فریم) و شیت مرگ (۶ فریم) کار می‌کنند
	for case in [["soldier", "run"], ["commando", "run"]]:
		var sheet_enemy := Enemy.new()
		add_child(sheet_enemy)
		var enemy_def := Enemies.by_id(String(case[0]))
		sheet_enemy.setup(enemy_def, null, 1.0, 1.0, 1, 0.0)
		check(sheet_enemy.active_anim() == String(case[1]),
			"دشمن %s باید با انیمیشن %s بیاید، هست: %s" % [case[0], case[1], sheet_enemy.active_anim()])
		check(sheet_enemy._sprite.hframes == UnitSheets.RUN_FRAMES, "شیت %s باید ۸ فریم داشته باشد" % case[0])
		# مرگ: شیت ۶ فریمی، فریم‌ها یکی‌یکی جلو می‌روند و آخرین فریم می‌ماند
		sheet_enemy._die()
		check(sheet_enemy.is_dying(), "با مرگ باید حالت افتادن روشن شود")
		check(sheet_enemy._sprite.hframes == UnitSheets.DEATH_FRAMES,
			"شیت مرگ %s باید %d فریم داشته باشد" % [case[0], UnitSheets.DEATH_FRAMES])
		check(sheet_enemy._sprite.frame == 0, "انیمیشن مرگ باید از فریم صفر شروع شود")
		var seen_frames: Array = []
		for step in 40:
			sheet_enemy._advance_death(1.0 / UnitSheets.DEATH_FRAMES)
			seen_frames.append(sheet_enemy._sprite.frame)
			check(sheet_enemy._sprite.frame <= UnitSheets.DEATH_FRAMES - 1,
				"شمارهٔ فریم مرگ از آخرین فریم رد شد")
		check(not seen_frames.is_empty() and int(seen_frames[-1]) == UnitSheets.DEATH_FRAMES - 1,
			"انیمیشن مرگ باید روی آخرین فریم بماند")
		check(sheet_enemy.is_queued_for_deletion(),
			"بعد از محو شدن، پیکر افتاده باید برای آزادسازی صف شود")


func _test_maps() -> void:
	check(Maps.count() == 12, "تعداد نقشه‌ها باید ۱۲ باشد، هست: %d" % Maps.count())
	# هر خانه باید یکی از دوازده تصویرِ دست‌ساز را دقیقاً یک‌بار پس‌زمینه داشته باشد.
	# ترتیب خانه‌ها در اطلس لزوماً با شمارهٔ تصویر یکی نیست (خانه‌های ۳ و ۱۰ جابه‌جا شده‌اند)،
	# پس به‌جای شمارهٔ خانه، یکتا بودن و کامل بودن مجموعهٔ تصویرها بررسی می‌شود.
	var seen_bg := {}
	for i in Maps.count():
		var key := String(Maps.def(i).get("bg", ""))
		check(Assets.has_key(key), "پس‌زمینهٔ نقشهٔ %d ثبت نشده است: %s" % [i, key])
		check(not seen_bg.has(key), "پس‌زمینهٔ %s بیش از یک بار استفاده شده است" % key)
		seen_bg[key] = true
	for n in range(1, Maps.count() + 1):
		check(seen_bg.has("maps/map_%02d" % n), "تصویر map_%02d در هیچ خانه‌ای نیست" % n)
	var min_spots := 999999
	var min_placed := 999999
	for index in Maps.count():
		var map := GameMap.build(index)
		check(not map.bg_key.is_empty() and Assets.has_key(map.bg_key),
			"تصویر پس‌زمینهٔ نقشهٔ %d ثبت نشده است: %s" % [index, map.bg_key])
		check(Assets.tex(map.bg_key) != null, "تصویر پس‌زمینهٔ نقشهٔ %d بارگذاری نشد: %s" % [index, map.bg_key])
		check(map.total_length > 260.0, "مسیر نقشهٔ %d کوتاه است: %.0f" % [index, map.total_length])
		check(GameMap.in_terrain(map.keep_pos, 0.0), "دژ نقشهٔ %d بیرون میدان است" % index)
		check(map.route.size() >= 4, "مسیر نقشهٔ %d نقطهٔ کافی ندارد" % index)
		check(map.route[map.route.size() - 1].distance_to(map.keep_pos) < 1.0,
			"مسیر نقشهٔ %d به دژ نمی‌رسد" % index)
		# همهٔ نقشه‌های جدید شبکه‌ای‌اند: فقط قطعه‌های افقی/عمودی و پیچ‌های دقیقاً ۹۰ درجه.
		var raw_waypoints: Array = Maps.def(index).get("waypoints", [])
		check(map.route.size() == raw_waypoints.size(),
			"مسیر نقشهٔ %d باید فقط رأس‌های شکسته داشته باشد" % index)
		for i in range(1, map.route.size()):
			var delta := map.route[i] - map.route[i - 1]
			check((is_zero_approx(delta.x) and not is_zero_approx(delta.y)) or
				(is_zero_approx(delta.y) and not is_zero_approx(delta.x)),
				"قطعهٔ %d مسیر نقشهٔ %d باید افقی یا عمودی باشد" % [i, index])
		for i in range(1, map.route.size() - 1):
			var before := map.route[i] - map.route[i - 1]
			var after := map.route[i + 1] - map.route[i]
			check(is_zero_approx(before.dot(after)), "پیچ %d نقشهٔ %d باید ۹۰ درجه باشد" % [i, index])
		for pad in map.pads:
			check(map.distance_to_route(pad) >= 80.0, "سکوی نقشهٔ %d روی جاده افتاده است" % index)
		var ground_count := map.ground_tiles.size()
		check(ground_count == Balance.COLS * Balance.ROWS, "کاشی‌های زمین نقشهٔ %d ناقص است" % index)
		for tile in map.blocked_tiles.keys():
			var cell := tile as Vector2i
			check(cell.x >= 0 and cell.x < Balance.COLS and cell.y >= 0 and cell.y < Balance.ROWS,
				"کاشی مسدود بیرون میدان در نقشهٔ %d" % index)
		# بررسی ساخت آزاد در هر نقطهٔ نقشه به جز محدودهٔ جاده و محدودهٔ برج دیگر
		var spots := 0
		var placed := 0
		var occupied := {}
		var gy := Balance.HUD_HEIGHT + 24.0
		while gy < Balance.WORLD_SIZE.y - 24.0:
			var gx := 32.0
			while gx < Balance.WORLD_SIZE.x - 32.0:
				var pt := Vector2(gx, gy)
				if map.build_issue(pt, {}).is_empty():
					spots += 1
				if map.build_issue(pt, occupied).is_empty():
					occupied[pt] = true
					placed += 1
				gx += 48.0
			gy += 48.0
		# نقطهٔ روی خود جاده و نقطهٔ نزدیک برج کاشته‌شده نباید مجاز باشد
		var on_road := map.point_at(map.total_length * 0.5)
		check(not map.build_issue(on_road, {}).is_empty(), "ساخت داخل محدودهٔ جاده در نقشهٔ %d نباید مجاز باشد" % index)
		var first_tower_pos: Vector2 = occupied.keys()[0]
		check(not map.build_issue(first_tower_pos + Vector2(15.0, 0.0), occupied).is_empty(),
			"ساخت داخل محدودهٔ برج دیگر در نقشهٔ %d نباید مجاز باشد" % index)
		min_spots = mini(min_spots, spots)
		min_placed = mini(min_placed, placed)
		check(spots >= 40, "نقاط مجاز ساخت در نقشهٔ %d کم است: %d" % [index, spots])
		check(placed >= 15, "چیدن برجک در نقشهٔ %d ممکن نیست: %d" % [index, placed])
		# ساخت دوباره باید همان نقشه را بدهد (بذر ثابت)
		var again := GameMap.build(index)
		check(absf(again.total_length - map.total_length) < 0.01, "نقشهٔ %d قطعی نیست" % index)
		check(again.decor.size() == map.decor.size(), "دکور نقشهٔ %d قطعی نیست" % index)
	print("  · کمترین نقطهٔ خالی ساخت: %d" % min_spots)
	print("  · کمترین برجک قابل‌چیدن: %d" % min_placed)

## ذخیرهٔ قدیمیِ خرید نقشه نباید قفل پیروزی را دور بزند؛ آزمایش فقط در حافظه است.
func _test_map_progression() -> void:
	var saved_best := Game.best.duplicate(true)
	var saved_maps := Game.unlocked_maps.duplicate()
	var saved_stars := Game.stars
	Game.best = {}
	Game.unlocked_maps = [0, 1, 2, 8]
	Game._sync_unlocked_maps()
	check(Game.unlocked_maps == [0], "فقط نقشهٔ اول باید در آغاز باز باشد")
	check(not Game.is_map_unlocked(-1) and not Game.is_map_unlocked(Maps.count()),
		"شمارهٔ نقشهٔ نامعتبر نباید باز باشد")
	Game.best["0"] = {"wave": 29, "stars": 3, "won": false}
	Game._sync_unlocked_maps()
	check(not Game.is_map_unlocked(1), "رسیدن به موج آخر بدون پیروزی نقشهٔ دوم را باز نمی‌کند")
	Game.best["0"] = {"wave": 30, "stars": 3, "won": true}
	Game._sync_unlocked_maps()
	check(Game.unlocked_maps == [0, 1], "پیروزی اول باید فقط نقشهٔ دوم را باز کند")
	Game.best["2"] = {"won": true}
	Game._sync_unlocked_maps()
	check(not Game.is_map_unlocked(2) and not Game.is_map_unlocked(3),
		"پیروزی در نقشهٔ جلوتر نباید جای خالی زنجیره را دور بزند")
	Game.best["1"] = {"won": true}
	Game._sync_unlocked_maps()
	check(Game.unlocked_maps == [0, 1, 2, 3], "پیروزی‌های متوالی باید نقشهٔ بعدی را باز کنند")
	check(Game.stars == saved_stars, "بازشدن نقشه نباید ستاره خرج کند")
	Game.best = saved_best
	Game.unlocked_maps = saved_maps

## پاداش هر دور مستقل از رکورد قبلی است؛ رکورد و قفل نقشه‌ها نباید پسرفت کنند.
func _test_repeatable_run_stars() -> void:
	var profile := RunTestProfile.new()
	profile._apply(profile.fresh_profile())
	var result := profile.record_run(0, 20, false)
	check(result["gained"] == 2 and profile.stars == 2,
		"دور اول تا موج ۲۰ باید دو ستاره بدهد")
	check(not profile.is_map_unlocked(1), "دو ستاره بدون پیروزی نباید نقشهٔ بعدی را باز کند")
	# بازگشت به بازی با رکورد ذخیره‌شده هم نباید پاداش دور بعد را کم کند.
	var saved_profile := profile.fresh_profile()
	saved_profile["stars"] = profile.stars
	saved_profile["best"] = profile.best.duplicate(true)
	profile._apply(saved_profile)
	result = profile.record_run(0, 30, true)
	check(result["stars"] == 3 and result["gained"] == 3 and profile.stars == 5,
		"پس از دو ستارهٔ قبلی، پیروزی باید سه ستارهٔ کامل تازه بدهد")
	check(profile.is_map_unlocked(1), "پیروزی باید همچنان نقشهٔ بعدی را باز کند")
	result = profile.record_run(0, 30, true)
	check(result["gained"] == 3 and profile.stars == 8,
		"تکرار نتیجهٔ سه‌ستاره هم باید سه ستارهٔ تازه بدهد")
	result = profile.record_run(0, 10, false)
	check(result["stars"] == 1 and result["gained"] == 1 and profile.stars == 9,
		"نتیجهٔ دور ضعیف‌تر باید فقط ستارهٔ همان دور را نشان دهد و جایزه بدهد")
	check(profile.map_stars(0) == 3 and profile.map_best(0)["wave"] == 30
		and profile.map_best(0)["won"] and profile.is_map_unlocked(1),
		"دور ضعیف‌تر نباید رکورد، پیروزی یا نقشهٔ بازشده را پاک کند")
	result = profile.record_run(0, 9, false)
	check(result["stars"] == 0 and result["gained"] == 0 and profile.stars == 9,
		"دور بدون رسیدن به موج شاخص نباید ستاره بدهد")
	for run in 2:
		result = profile.record_run(25, 30, true)
		check(result["gained"] == 4, "پاداش پیروزی نقشه‌های آخر هم باید در هر دور تکرار شود")
	check(profile.stars == 17, "جمع ستاره‌ها باید شامل پاداش کامل تمام دورها باشد")
	profile.free()

## قیمت و کسر موجودی در تمام سطح‌های افزایش اعتبار باید ثابت بماند.
func _test_prestige_fixed_cost() -> void:
	var profile := RunTestProfile.new()
	profile._apply(profile.fresh_profile())
	for level in range(Balance.PRESTIGE_MAX_LEVEL):
		profile.prestige_level = level
		check(profile.prestige_upgrade_cost() == 10,
			"قیمت افزایش اعتبار در سطح %d باید ۱۰ ستاره باشد" % level)
		profile.stars = 9
		check(not profile.upgrade_prestige() and profile.prestige_level == level and profile.stars == 9,
			"با ۹ ستاره نباید افزایش اعتبار ارتقا یابد")
		profile.stars = 10
		check(profile.upgrade_prestige() and profile.prestige_level == level + 1 and profile.stars == 0,
			"هر ارتقای افزایش اعتبار باید دقیقاً ۱۰ ستاره کم کند")
		check(profile.prestige_starting_coins() == (level + 1) * Balance.PRESTIGE_COINS_PER_LEVEL,
			"پاداش سکهٔ افزایش اعتبار نباید تغییر کند")
	profile.stars = 10
	check(not profile.upgrade_prestige() and profile.stars == 10,
		"در سقف سطح افزایش اعتبار نباید ستاره کم شود")
	profile.free()

# ------------------------------------------------------------------ موج‌ها

func _test_waves() -> void:
	for index in [0, 5, 10, 14]:
		var seen_Across_waves := {}
		for wave in range(1, Balance.WAVES + 1):
			var groups := Enemies.wave_groups(index, wave)
			check(not groups.is_empty(), "موج %d نقشهٔ %d خالی است" % [wave, index])
			var total := 0
			var infantry := 0
			var tanks := 0
			for group in groups:
				var id := String(group.get("enemy", ""))
				check(not Enemies.by_id(id).is_empty(), "دشمن ناشناخته در موج %d نقشهٔ %d: %s" % [wave, index, id])
				seen_Across_waves[id] = true
				var count := int(group.get("count", 0))
				check(count > 0, "گروه خالی در موج %d نقشهٔ %d" % [wave, index])
				check(float(group.get("interval", 0.0)) > 0.0, "فاصلهٔ نامعتبر در موج %d نقشهٔ %d" % [wave, index])
				total += count
				if Enemies.INFANTRY_IDS.has(id):
					infantry += count
				elif Enemies.TANK_IDS.has(id):
					tanks += count
			check(total >= 3, "موج %d نقشهٔ %d کم‌جمعیت است: %d" % [wave, index, total])
			check(infantry == maxi(1, int(round(float(Balance.wave_count(wave))))),
				"تعداد پیاده‌نظام موج %d نباید تغییر کند" % wave)
			var expected_tanks := 0 if wave < 5 else 1 + int(float(wave - 5) / 5.0)
			check(tanks == expected_tanks, "موج %d نقشهٔ %d باید %d تانک داشته باشد، دارد %d" % [wave, index, expected_tanks, tanks])
		check(seen_Across_waves.size() == Enemies.LIST.size(),
			"همهٔ ۵ نیروی دشمن باید در طول موج‌های نقشهٔ %d حضور داشته باشند: %s" % [index, seen_Across_waves.keys()])
	check(Enemies.tank_count(4) == 0 and Enemies.tank_count(5) == 1 and Enemies.tank_count(9) == 1
		and Enemies.tank_count(10) == 2 and Enemies.tank_count(30) == 6, "جدول تعداد تانک موج‌ها اشتباه است")


# ------------------------------------------------------------------ تعادل

func _test_balance() -> void:
	check(Balance.START_COINS > 0 and Balance.START_LIVES > 0, "شروع بازی نامعتبر است")
	check(Balance.level_damage_mult(1) == 1.0, "سطح یک باید ضریب یک داشته باشد")
	for level in range(2, Balance.MAX_LEVEL + 1):
		check(is_equal_approx(Balance.level_damage_mult(level) - Balance.level_damage_mult(level - 1), Balance.DAMAGE_STEP_PER_LEVEL),
			"آسیب سطح %d باید به اندازهٔ گام ثابت تعادل بالا برود" % level)
	for level in range(1, Balance.MAX_LEVEL):
		check(Balance.upgrade_cost(65, level) == 65,
			"ارتقای برجک ۶۵ سکه‌ای باید در همهٔ سطح‌ها ۶۵ سکه باشد")
	_test_battle_tower_upgrades()
	check(Balance.stars_for(0, 30, true) == 3, "ستاره‌های نقشهٔ آزاد باید ۳ باشد")
	check(Balance.stars_for(25, 30, true) == 4, "نقشه‌های آخر باید ستارهٔ پیروزی هم بدهند")
	check(Balance.enemy_hp_scale(20, 20) > Balance.enemy_hp_scale(0, 1), "مقیاس جان نقشه‌ها رشد نمی‌کند")
	check(Fx.frames_for("explosion").get_frame_count("explosion") > 3, "فریم‌های انفجار ناقص است")

## قیمت و آسیب واقعی همهٔ برجک‌ها باید از همان قواعد ارتقای داخل نبرد پیروی کنند.
func _test_battle_tower_upgrades() -> void:
	for id in Weapons.ids():
		var tower := Tower.new()
		tower.weapon = Weapons.by_id(String(id))
		var placement_cost := int(tower.weapon["cost"])
		tower.invested = placement_cost
		for level in range(1, Balance.MAX_LEVEL):
			check(tower.upgrade_cost() == placement_cost,
				"هزینهٔ ارتقای %s در سطح %d باید برابر کاشت باشد" % [id, level])
			var previous_damage := tower.damage()
			tower.upgrade()
			var expected_damage := previous_damage * Balance.level_damage_mult(level + 1) / Balance.level_damage_mult(level)
			check(tower.level == level + 1 and is_equal_approx(tower.damage(), expected_damage),
				"ارتقای %s باید آسیب را طبق گام تعادل بالا ببرد" % id)
			check(tower.invested == placement_cost * tower.level,
				"هزینهٔ سرمایه‌گذاری‌شدهٔ %s باید شامل قیمت ثابت ارتقاها باشد" % id)
		var max_damage := tower.damage()
		var max_invested := tower.invested
		tower.upgrade()
		check(tower.max_level() and tower.level == Balance.MAX_LEVEL
			and tower.damage() == max_damage and tower.invested == max_invested,
			"در سقف سطح، ارتقای %s نباید هزینه یا آسیب را تغییر دهد" % id)
		tower.free()

# ------------------------------------------------------------------ برد شلیک برجک

## برجک نباید بیرون از برد خودش شلیک کند: نه هدفی بیرون از برد بگیرد و نه هدفی
## که از برد بیرون رفته را نگه دارد. پیش‌تر هدف تا مرگِ دشمن نگه داشته می‌شد و
## برجک بعد از خروجِ دشمن از دایرهٔ برد هم به او شلیک می‌کرد.
func _test_tower_range() -> void:
	var weapon := Weapons.by_id(String(Weapons.FREE_IDS[0]))
	check(not weapon.is_empty(), "برجک رایگان برای سنجش برد پیدا نشد")
	if weapon.is_empty():
		return

	# برجک بدون setup ساخته می‌شود تا هیچ بافتی بار نشود؛ سنجش برد فقط به
	# weapon و level و position نیاز دارد. گره‌ها به درخت اضافه و با
	# queue_free آزاد می‌شوند تا در پایان اجرا هشدار نشت نمانند.
	var battle := StubBattle.new()
	var tower := Tower.new()
	add_child(battle)
	add_child(tower)
	tower.weapon = weapon
	tower.level = 1
	tower.battle = battle
	tower.position = Vector2(400.0, 300.0)

	# دایرهٔ پیش‌نمایش پیش از ساخت و برد واقعی برجک باید یکی باشند
	check(is_equal_approx(tower.attack_range(), Tower.range_for(weapon, 1)),
		"برد برجک با برد پیش‌نمایش ساخت یکی نیست")
	var grown := Tower.range_for(weapon, 3)
	check(grown > tower.attack_range(), "ضریب برد سطح‌ها در محاسبهٔ برد اعمال نمی‌شود")
	check(is_equal_approx(Tower.range_for(weapon, 1),
			float(weapon["range"]) * Balance.level_range_mult(1) * Game.weapon_range_mult(String(weapon["id"]))),
		"حساب برد برجک با ضریب‌های پروژه یکی نیست")

	var reach := tower.attack_range()
	var near := Enemy.new()
	var far := Enemy.new()
	add_child(near)
	add_child(far)
	near.alive = true
	far.alive = true
	near.position = tower.position + Vector2(reach - 4.0, 0.0)
	far.position = tower.position + Vector2(reach + 24.0, 0.0)

	check(tower._is_in_range(near), "دشمن داخل برد باید در برد شمرده شود")
	check(not tower._is_in_range(far), "دشمن بیرون برد نباید در برد شمرده شود")
	check(not tower._is_in_range(null), "هدف خالی نباید در برد شمرده شود")

	battle.enemies = [far]
	check(tower._pick_target() == null, "دشمن بیرون از برد نباید هدف گرفته شود")

	# هدفِ نگه‌داشته‌شده که از برد بیرون رفته باید رها شود
	tower.target = far
	tower.cooldown_left = 0.0
	tower._process(0.016)
	check(tower.target == null, "دشمنِ بیرون‌رفته از برد باید رها شود و رویش شلیک نشود")

	# با نزدیک شدن دشمن، برجک باید دوباره هدف بگیرد
	battle.enemies = [near]
	tower.cooldown_left = 5.0
	tower._process(0.016)
	check(tower.target == near, "دشمن داخل برد باید دوباره هدف گرفته شود")

	# هدف مرده هم نباید هدف بماند
	near.alive = false
	tower._process(0.016)
	check(tower.target == null, "دشمن مرده نباید هدف بماند")

	# تیرهای اضافی سلاح چندشلیک (نسخهٔ آزمایشی برجک رایگان با ۴ تیر) هم نباید بیرون از برد را بزنند
	var volley: Dictionary = weapon.duplicate()
	volley["shots"] = 4
	check(not volley.is_empty(), "نسخهٔ چندشلیک برجک برای سنجش ساخته نشد")
	if not volley.is_empty():
		var multi := Tower.new()
		add_child(multi)
		multi.weapon = volley
		multi.level = 1
		multi.battle = battle
		multi.position = Vector2(400.0, 300.0)
		var multi_reach := multi.attack_range()
		var edge := Enemy.new()
		var outside := Enemy.new()
		add_child(edge)
		add_child(outside)
		edge.alive = true
		outside.alive = true
		edge.position = multi.position + Vector2(multi_reach - 6.0, 0.0)
		outside.position = multi.position + Vector2(multi_reach * 1.03, 0.0)
		multi.target = edge
		var picks := multi._shot_targets(maxi(2, int(volley.get("shots", 1))))
		check(picks.has(edge) and not picks.has(outside),
			"تیر اضافی سلاح چندشلیک نباید دشمن بیرون از برد را هدف بگیرد")
		multi.queue_free()
		edge.queue_free()
		outside.queue_free()

	# برجک در فریم‌های بعدی (تست صحنه‌ها) شلیک نکند و هیچ گره‌ای جا نماند
	tower.target = null
	tower.cooldown_left = 9999.0
	battle.enemies = []
	tower.queue_free()
	battle.queue_free()
	near.queue_free()
	far.queue_free()

# ------------------------------------------------------------------ شیت‌های ایزومتریک

## همهٔ ۱۷ فایل دو پک ایزومتریک با همان نام در مانیفست‌اند، ابعادشان با جدول IsoSheet
## می‌خواند و هر هشت جهت هر شیت به‌صورت AtlasTexture با ناحیهٔ درست و تصویر غیرخالی بار می‌شود.
func _test_iso_sheets() -> void:
	var folders := {
		"res://assets/sprites/towers_iso": ["towers_iso", 13],
		"res://assets/sprites/units/iso_tank": ["units_iso_tank", 4],
		"res://assets/sprites/units/iso_tank_armored": ["units_iso_tank_armored", 4],
		"res://assets/sprites/units/iso_tank_heavy": ["units_iso_tank_heavy", 4],
	}
	for folder in folders.keys():
		var prefix := String(folders[folder][0])
		var pngs: Array = []
		for file in DirAccess.get_files_at(String(folder)):
			if String(file).ends_with(".png"):
				pngs.append(String(file))
		check(pngs.size() == int(folders[folder][1]),
			"پوشهٔ %s باید %d شیت داشته باشد، دارد %d" % [folder, int(folders[folder][1]), pngs.size()])
		for file in pngs:
			var key := "%s/%s" % [prefix, String(file).get_basename()]
			check(Assets.has_key(key), "شیت ایزومتریک در مانیفست نیست: %s" % key)

	var expected := IsoSheet.expected_sheets()
	check(expected.size() == 21, "دوازده شیت برجک و نه شیت سه تانک انتظار می‌رود: %d" % expected.size())
	for key in expected.keys():
		var sheet_key := String(key)
		var frames := int(expected[key])
		check(Assets.has_key(sheet_key), "شیت ایزومتریک گم شده: %s" % sheet_key)
		var texture := Assets.tex(sheet_key)
		check(texture != null, "شیت ایزومتریک بار نشد: %s" % sheet_key)
		if texture == null:
			continue
		check(texture.get_width() == frames * IsoSheet.FRAME and texture.get_height() == IsoSheet.DIRS * IsoSheet.FRAME,
			"ابعاد %s باید %d×%d باشد، هست %d×%d" % [sheet_key, frames * IsoSheet.FRAME, IsoSheet.DIRS * IsoSheet.FRAME,
				texture.get_width(), texture.get_height()])
		var sheet := IsoSheet.open(sheet_key)
		check(sheet.frames == frames and sheet.rows == IsoSheet.DIRS, "شمار فریم/جهت %s اشتباه خوانده شد" % sheet_key)
		var source := texture.get_image()
		for dir in IsoSheet.DIRS:
			for f in frames:
				var atlas := sheet.frame(dir, f)
				var want := Rect2(f * 128, dir * 128, 128, 128)
				check(atlas != null and atlas.atlas == texture and atlas.region == want,
					"فریم %d جهت %d از %s ناحیهٔ درست ندارد" % [f, dir, sheet_key])
				if f == 0 and atlas != null:
					var img := atlas.get_image()
					check(img != null and img.get_size() == Vector2i(128, 128) and img.get_used_rect().size.x > 16,
						"فریم ۰ جهت %d از %s خالی یا نادرست است" % [dir, sheet_key])
		check(source != null and not source.is_empty(), "تصویر خام %s خوانده نشد" % sheet_key)
	for atlas_key in IsoSheet.ATLAS_COLUMNS.keys():
		var tex := Assets.tex(String(atlas_key))
		check(tex != null and tex.get_width() == int(IsoSheet.ATLAS_COLUMNS[atlas_key]) * IsoSheet.FRAME
			and tex.get_height() == IsoSheet.DIRS * IsoSheet.FRAME, "ابعاد اطلس پیش‌نمایش %s اشتباه است" % atlas_key)
	check(Assets.FALLBACKS.has("towers_iso") and Assets.FALLBACKS.has("units_iso_tank")
		and Assets.FALLBACKS.has("units_iso_tank_armored") and Assets.FALLBACKS.has("units_iso_tank_heavy"),
		"دسته‌های ایزومتریک باید تصویر جایگزین داشته باشند")

	# جهت از بردار: فشردگی عمق ۰٫۵ و باقی‌ماندهٔ مثبت
	var cases := [
		[Vector2(1, 0), 0], [Vector2(1, 0.5), 1], [Vector2(0, 1), 2], [Vector2(-1, 0.5), 3],
		[Vector2(-1, 0), 4], [Vector2(-1, -0.5), 5], [Vector2(0, -1), 6], [Vector2(1, -0.5), 7],
		[Vector2(1, -0.05), 0], [Vector2(1, 0.3), 1], [Vector2(1, -0.3), 7],
	]
	for case in cases:
		var got := IsoSheet.dir_from_vector(case[0])
		check(got == int(case[1]), "جهت %s باید %d باشد، شد %d" % [case[0], int(case[1]), got])
	check(IsoSheet.dir_from_vector(Vector2.ZERO, 5) == 5, "بردار صفر باید جهت قبلی را نگه دارد")
	check(IsoSheet.step_toward(0, 7) == 7 and IsoSheet.step_toward(0, 3) == 1 and IsoSheet.step_toward(6, 1) == 7
		and IsoSheet.step_toward(4, 4) == 4, "گام چرخش ۴۵ درجه باید از کوتاه‌ترین سمت باشد")
	for kind in IsoSheet.TOWER_KINDS:
		check((IsoSheet.TOWER_MUZZLE[kind] as Array).size() == IsoSheet.DIRS, "دهانهٔ برجک %s برای هر هشت جهت نیست" % kind)
		var idle_key := IsoSheet.tower_key(String(kind), "idle")
		var base := IsoSheet.base_texture(idle_key)
		check(base is ImageTexture and base.get_width() == IsoSheet.FRAME, "پایهٔ ثابت برجک %s ساخته نشد" % kind)
		if base is ImageTexture:
			# پایه فقط پیکسل‌های ماتِ مشترک فریم ۰ هر هشت جهت است (نمونه‌برداری یک‌درمیان)
			var base_img := base.get_image()
			var sheet_img := Assets.tex(idle_key).get_image()
			if sheet_img.is_compressed():
				sheet_img.decompress()
			var used := 0
			var mismatched := 0
			for y in range(0, IsoSheet.FRAME, 2):
				for x in range(0, IsoSheet.FRAME, 2):
					var c := base_img.get_pixel(x, y)
					if c.a == 0.0:
						continue
					used += 1
					for d in IsoSheet.DIRS:
						if sheet_img.get_pixel(x, d * IsoSheet.FRAME + y) != c:
							mismatched += 1
			check(used > 400 and used < 2000 and mismatched == 0,
				"پایهٔ ثابت برجک %s باید سکوی مشترک هشت جهت باشد (پیکسل: %d، ناهمخوان: %d)" % [kind, used, mismatched])

# ------------------------------------------------------------------ برجک‌های ایزومتریک

## هر برجک ایزومتریک: پایهٔ ثابت و سرِ گردان؛ سر گام‌به‌گام (۴۵ درجه) رو به هدف می‌چرخد،
## بدون هدف idle، با هدف aim، با هر شلیک یک‌بار fire و سپس برگشت؛ گلوله و شعلهٔ دهانه
## همان سامانهٔ قبلی‌اند و از دهانهٔ همان جهت بیرون می‌آیند.
func _test_iso_towers() -> void:
	for id in Weapons.ids():
		var weapon := Weapons.by_id(String(id))
		var kind := Weapons.iso_kind(String(id))
		if kind.is_empty():
			continue
		var battle := StubBattle.new()
		add_child(battle)
		var tower := Tower.new()
		add_child(tower)
		tower.position = Vector2(400.0, 300.0)
		tower.setup(weapon, battle)
		check(tower.is_iso() and tower.iso_kind == kind, "برجک %s باید ایزومتریک ساخته شود" % id)
		check(tower._base != null and tower._base.texture != null and tower._head != null,
			"برجک %s پایه و سر ندارد" % id)
		check(tower.head_action() == "idle", "برجک %s باید با idle شروع کند" % id)
		var base_tex: Texture2D = tower._base.texture
		var base_pos: Vector2 = tower._base.position

		# دشمن در غرب (جهت ۴): سر از جهت شروع گام‌به‌گام می‌چرخد
		var enemy := Enemy.new()
		add_child(enemy)
		enemy.alive = true
		enemy.hp = 9999.0
		enemy.max_hp = 9999.0
		enemy.position = tower.position + Vector2(-120.0, 0.0)
		battle.enemies = [enemy]
		tower.cooldown_left = 99.0
		var dirs: Array = [tower.head_dir]
		for step in 12:
			tower._process(0.05)
			if int(dirs[-1]) != tower.head_dir:
				dirs.append(tower.head_dir)
		check(tower.head_dir == 4, "سر برجک %s باید رو به غرب (۴) بچرخد، هست %d" % [id, tower.head_dir])
		var stepwise := true
		for i in range(1, dirs.size()):
			if not [1, 7].has(posmod(int(dirs[i]) - int(dirs[i - 1]), 8)):
				stepwise = false
		check(stepwise and dirs.size() >= 2, "چرخش سر %s باید گام‌های ۴۵ درجه باشد: %s" % [id, dirs])
		check(tower.head_action() == "aim", "برجک %s با هدف در برد باید aim باشد، هست %s" % [id, tower.head_action()])
		var head_tex := tower._head.texture as AtlasTexture
		check(head_tex != null and head_tex.region.position.y == 4 * IsoSheet.FRAME
			and head_tex.atlas == Assets.tex(IsoSheet.tower_key(kind, "aim")),
			"تصویر سر %s باید ردیف جهت ۴ از شیت aim باشد" % id)
		check(tower._head.rotation == 0.0 and tower._base.rotation == 0.0, "اسپرایت برجک %s نباید پیوسته بچرخد" % id)

		# شلیک: fire یک‌بار، گلوله‌ها از دهانه و شعلهٔ دهانه روشن
		var before := battle.get_child_count()
		tower.cooldown_left = 0.0
		tower._process(0.016)
		check(tower.head_action() == "fire", "برجک %s هنگام شلیک باید fire باشد" % id)
		var shots := maxi(1, int(weapon.get("shots", 1)))
		check(battle.get_child_count() == before + shots, "برجک %s باید %d گلوله بزند" % [id, shots])
		check(tower._flash != null and tower._flash.visible, "شعلهٔ دهانهٔ %s باید روشن شود" % id)
		if battle.get_child_count() > before:
			var shot := battle.get_child(before) as Node2D
			var tip4: Vector2 = (IsoSheet.TOWER_MUZZLE[kind] as Array)[4]
			var muzzle: Vector2 = tower.position + (tip4 + Vector2(0.0, IsoSheet.TOWER_PIVOT.y - IsoSheet.TOWER_GROUND_Y)) * Tower.ISO_SCALE
			check(shot is Projectile and shot.global_position.distance_to(muzzle) <= Tower.ISO_BARREL_SPREAD + 1.0,
				"گلولهٔ %s باید از دهانهٔ جهت ۴ بیرون بیاید" % id)
		tower.cooldown_left = 99.0
		for step in 6:
			tower._process(0.1)
		check(tower.head_action() == "aim", "پس از fire برجک %s باید به aim برگردد" % id)
		enemy.alive = false
		tower._process(0.05)
		check(tower.head_action() == "idle", "بدون هدف برجک %s باید idle شود" % id)
		check(tower._base.texture == base_tex and tower._base.position == base_pos, "پایهٔ برجک %s باید ثابت بماند" % id)
		tower.upgrade()
		check(tower.level == 2, "ارتقای %s باید سطح برجک را بالا ببرد" % id)
		battle.enemies = []
		tower.queue_free()
		enemy.queue_free()
		battle.queue_free()

# ------------------------------------------------------------------ تانک

## تانک: در مسیر move، جهت از مسیر در گام‌های ۴۵ درجه؛ کنار دژ می‌ایستد، رو به دژ می‌چرخد،
## تنها **یک بار** به دژ شلیک می‌کند (یک جان کم می‌شود) و بلافاصله بدون تکرار شلیک حذف می‌شود.
func _test_tank() -> void:
	for tid in Enemies.TANK_IDS:
		_test_single_tank(String(tid))
	await _test_tank_battle_single_shot()

func _test_single_tank(tid: String) -> void:
	var def := Enemies.by_id(tid)
	if def.is_empty():
		check(false, "تانک %s برای آزمون پیدا نشد" % tid)
		return
	var map := GameMap.build(0)
	var tank := IsoEnemy.new()
	add_child(tank)
	tank.setup(def, map, 1.0, 1.0, 5, 0.0)
	check(tank.active_anim() == "move", "تانک %s باید با move حرکت کند، هست %s" % [tid, tank.active_anim()])
	check(is_equal_approx(tank.max_hp, float(def["hp"])) and is_equal_approx(tank.armor, 4.0) and tank.reward == 12,
		"آمار تانک %s از جدول دشمنان خوانده نشد" % tid)
	var frame := tank._sprite.texture as AtlasTexture
	check(frame != null and frame.atlas == Assets.tex(IsoSheet.tank_key("move", tid))
		and frame.region.position.y == tank.dir * IsoSheet.FRAME,
		"تصویر تانک %s باید ردیف جهتش از شیت move خودش باشد" % tid)

	# جهت روی هر تکهٔ مسیر از خود مسیر می‌آید (۸ جهت) و اسپرایت هرگز نمی‌چرخد
	var seen := {}
	var s := 10.0
	while s < tank.siege_progress - 10.0:
		tank.progress = s
		tank._update_transform(0.0)
		var want := IsoSheet.dir_from_vector(map.direction_at(s))
		check(tank.dir == want, "جهت تانک در فاصلهٔ %.0f باید %d باشد، هست %d" % [s, want, tank.dir])
		var tex := tank._sprite.texture as AtlasTexture
		check(tex != null and int(tex.region.position.y) == want * IsoSheet.FRAME, "ردیف شیت تانک با جهت نمی‌خواند")
		seen[want] = true
		s += 24.0
	check(seen.size() >= 2, "مسیر نقشه باید تانک را در بیش از یک جهت ببرد")
	check(tank._sprite.rotation == 0.0, "تانک باید فقط با ردیف شیت بچرخد، نه چرخش پیوسته")

	# حرکت واقعی با _process و انیمیشن move + بررسی کاهش آسیب با زره ۴
	tank.progress = 0.0
	var start := tank.progress
	for step in 10:
		tank._process(0.1)
	check(is_equal_approx(snappedf(tank.progress - start, 0.01), snappedf(tank.base_speed, 0.01)),
		"تانک باید در یک ثانیه به اندازهٔ سرعتش جلو برود")
	check(tank.active_anim() == "move" and not tank.sieging, "تانک در مسیر باید move باشد")
	var hp_before := tank.hp
	tank.take_damage(10.0)
	check(is_equal_approx(hp_before - tank.hp, 6.0), "زره ۴ تانک باید از آسیب کم شود")

	# رسیدن به دژ: می‌ایستد، فقط **یک بار** شلیک می‌کند و بلافاصله بدون تکرار شلیک حذف می‌شود
	var hits := [0]
	var reached := [false]
	var finished := [false]
	var died_on_shot := [false]
	tank.keep_attacked.connect(func(_enemy, amount): hits[0] += int(amount))
	tank.reached_keep.connect(func(_enemy): reached[0] = true)
	tank.finished_attack.connect(func(_enemy): finished[0] = true)
	tank.died.connect(func(_enemy): died_on_shot[0] = true)
	tank.progress = tank.siege_progress - 0.5
	tank._process(0.05)
	check(tank.sieging and tank.active_anim() == "idle", "تانک کنار دژ باید بایستد و پیش از شلیک idle شود")
	check(tank.position.distance_to(map.keep_pos) > 60.0, "تانک باید پیش از دژ بایستد، نه روی آن")
	check(tank.dir == IsoSheet.dir_from_vector(map.keep_pos - tank.position), "تانک هنگام شلیک باید رو به دژ باشد")
	var laser := tank.laser_tip()
	check((laser - tank.position).dot(map.keep_pos - tank.position) > 0.0
		and laser.is_equal_approx(tank.position + IsoEnemy.laser_tip_offset(tank.family, tank.dir) * IsoEnemy.SPRITE_SCALE),
		"نوک لیزر تانک %s باید سرِ پرتوِ همان جهت و رو به دژ باشد" % tid)
	var anims := {}
	for step in 120:   # شش ثانیه کامل
		tank._process(0.05)
		anims[tank.active_anim()] = true
	check(hits[0] == 1 and tank.shots_fired == 1,
		"تانک %s باید دقیقاً یک تیر شلیک کند و دیگر شلیک را تکرار نکند، شلیک شد: %d" % [tid, hits[0]])
	check(anims.has("attack"), "تانک %s باید انیمیشن شلیک attack را پخش کند" % tid)
	check(finished[0] and not tank.alive and not died_on_shot[0] and not reached[0] and tank.is_queued_for_deletion(),
		"تانک %s باید بلافاصله پس از همان یک شلیک، بدون سیگنال مرگ حذف شود" % tid)

	# نابودی در مسیر با شلیک برجک‌ها (پیش از رسیدن به دژ)
	var doomed := IsoEnemy.new()
	add_child(doomed)
	doomed.setup(def, map, 1.0, 1.0, 5, 0.0)
	var died := [false]
	doomed.died.connect(func(_enemy): died[0] = true)
	doomed.take_damage(99999.0, true)
	check(died[0] and not doomed.alive and doomed.is_dying(), "تانک با تمام شدن جان در مسیر باید نابود شود")
	for step in 20:
		doomed._process(0.05)
	check(doomed.is_queued_for_deletion(), "لاشهٔ تانک باید پس از محو شدن آزاد شود")

## آزمون یکپارچه در خود صحنهٔ نبرد: هر یک از سه تانک پس از یک شلیک به دژ، فقط ۱ جان از قلعه کم می‌کند،
## بلافاصله از فهرست دشمنان میدان حذف می‌شود، سکهٔ کشتن نمی‌دهد و هرگز شلیک دومی انجام نمی‌دهد.
func _test_tank_battle_single_shot() -> void:
	var battle = load("res://scenes/Battle.tscn").instantiate()
	add_child(battle)
	await get_tree().process_frame
	battle.state = "wave"
	battle.wave_index = 12
	battle.spawn_queue.clear()
	battle.enemies.clear()
	for tid in Enemies.TANK_IDS:
		var lives_before: int = battle.lives
		var coins_before: int = battle.coins
		var kills_before: int = battle.kills
		battle._spawn_enemy(String(tid))
		check(battle.enemies.size() == 1, "تانک %s در نبرد ساخته نشد" % tid)
		var tank: IsoEnemy = battle.enemies[0]
		tank.progress = tank.siege_progress - 0.5
		for step in 120:
			if is_instance_valid(tank):
				tank._process(0.05)
		check(battle.lives == lives_before - 1,
			"تانک %s در نبرد باید فقط یک بار به قلعه شلیک کند (جان قلعه پیش: %d، پس: %d)"
			% [tid, lives_before, battle.lives])
		check(battle.enemies.is_empty() and tank.is_queued_for_deletion(),
			"تانک %s باید پس از تک‌شلیک از فهرست دشمنان میدان حذف شود" % tid)
		check(battle.kills == kills_before and battle.coins == coins_before,
			"حذف تانک %s پس از شلیک به قلعه نباید سکه یا شمارش کشتن بدهد" % tid)
	battle.queue_free()
	await get_tree().process_frame


# ------------------------------------------------------------------ لمس واقعی گوشی

## یک رویداد لمس مثل اندروید؛ Godot از آن کلیک موس هم می‌سازد (emulate_mouse_from_touch).
func _touch(pos: Vector2, pressed: bool) -> void:
	var ev := InputEventScreenTouch.new()
	ev.index = 0
	ev.pressed = pressed
	ev.position = get_viewport().get_final_transform() * pos
	Input.parse_input_event(ev)
	await get_tree().process_frame
	await get_tree().process_frame

func _touch_drag(from: Vector2, to: Vector2, steps := 4) -> void:
	await _touch(from, true)
	var last := from
	for i in range(1, steps + 1):
		var p := from.lerp(to, float(i) / float(steps))
		var ev := InputEventScreenDrag.new()
		ev.index = 0
		ev.position = get_viewport().get_final_transform() * p
		ev.relative = p - last
		last = p
		Input.parse_input_event(ev)
		await get_tree().process_frame
	await _touch(to, false)

## انتخاب برجک و دکمه‌های ارتقا/فروش در نبرد و زرادخانه از همان مسیر رویدادهای لمسی گوشی
## (نه صدا زدن مستقیم توابع). لمس ساده و کشیدن روی برجک هر دو پنل را باز می‌کنند، پنل و
## دکمه‌ها کامل داخل صفحهٔ ۱۲۸۰×۷۲۰ می‌مانند، و لمس داخل پنل به میدان پشتش نشت نمی‌کند.
func _test_touch_upgrade_flow() -> void:
	var saved_unlocked: Array = Game.unlocked_weapons.duplicate()
	var saved_levels: Dictionary = Game.weapon_levels.duplicate(true)
	var saved_stars: int = Game.stars
	Game.unlocked_weapons = Weapons.ids().duplicate()
	var battle = load("res://scenes/Battle.tscn").instantiate()
	add_child(battle)
	await get_tree().process_frame
	await get_tree().process_frame
	var screen := Rect2(Vector2.ZERO, get_viewport().get_visible_rect().size)
	var hud = battle.hud
	battle.coins = 9000
	for id in Weapons.ids():
		var built_before: int = battle.towers.size()
		for y in range(140, 560, 40):
			for x in range(160, 900, 40):
				var pt := Vector2(x, y)
				if battle.map.build_issue(pt, battle.occupied).is_empty():
					battle.selected_weapon = String(id)
					battle._try_build(pt)
					break
			if battle.towers.size() > built_before:
				break
		check(battle.towers.size() == built_before + 1, "برجک %s برای آزمون لمس ساخته نشد" % id)
		if battle.towers.size() == built_before:
			continue
		var tower = battle.towers[-1]
		var grip: Vector2 = tower.position + Vector2(0.0, -24.0)
		# ۱) حالت پیش‌فرض بازی: سلاح در نوار پایین آماده است و بازیکن مستقیم روی برجک می‌زند
		battle.selected_weapon = String(id)
		await get_tree().process_frame
		await _touch(grip, true)
		await _touch(grip, false)
		check(battle.selected_tower == tower and hud._tower_panel.visible and battle.selected_weapon.is_empty(),
			"لمس سادهٔ برجک %s هنگام آماده بودن سلاح باید پنل ارتقا را باز کند" % id)
		# ۲) بستن پنل با لمس فضای خالی و باز کردن دوباره با لمس ساده بدون سلاح آماده
		await _touch(Vector2(620.0, 60.0), true)
		await _touch(Vector2(620.0, 60.0), false)
		check(battle.selected_tower == null and not hud._tower_panel.visible,
			"لمس فضای خالی باید پنل برجک %s را ببندد" % id)
		await _touch(grip, true)
		await _touch(grip, false)
		check(battle.selected_tower == tower and hud._tower_panel.visible,
			"لمس سادهٔ برجک %s بدون سلاح آماده باید پنل ارتقا را باز کند" % id)
		# ۳) کشیدن روی برجک نیز آن را انتخاب می‌کند
		battle.select_tower(null)
		await _touch_drag(grip, grip + Vector2(36.0, 0.0))
		check(battle.selected_tower == tower and hud._tower_panel.visible,
			"کشیدن برجک %s با لمس نیز باید پنل ارتقا را باز کند" % id)
		if battle.selected_tower != tower:
			continue
		# ۴) لمس ناحیهٔ خالی داخل پنل برجک نباید پنل را ببندد
		var panel_top: Vector2 = hud._tower_panel.get_global_rect().position + Vector2(40.0, 24.0)
		await _touch(panel_top, true)
		await _touch(panel_top, false)
		check(battle.selected_tower == tower and hud._tower_panel.visible,
			"لمس داخل پنل برجک %s نباید پنل را ببندد" % id)
		for step in range(1, Balance.MAX_LEVEL):
			await get_tree().process_frame
			var panel_rect: Rect2 = hud._tower_panel.get_global_rect()
			var up_rect: Rect2 = hud._upgrade_button.get_global_rect()
			var sell_rect: Rect2 = hud._sell_button.get_global_rect()
			if step == 1:
				print("  %s: panel=%s up=%s sell=%s" % [id, panel_rect, up_rect, sell_rect])
			check(screen.encloses(panel_rect) and screen.encloses(up_rect) and screen.encloses(sell_rect),
				"پنل و دکمه‌های برجک %s در سطح %d باید کامل داخل صفحه باشند: پنل %s ارتقا %s فروش %s"
				% [id, tower.level, panel_rect, up_rect, sell_rect])
			check(not hud._upgrade_button.disabled, "دکمهٔ ارتقای %s با سکهٔ کافی نباید غیرفعال باشد" % id)
			var level_before: int = tower.level
			var coins_before: int = battle.coins
			await _touch(up_rect.get_center(), true)
			await _touch(up_rect.get_center(), false)
			check(tower.level == level_before + 1 and battle.coins == coins_before - int(tower.weapon["cost"]),
				"زدن دکمهٔ ارتقا با لمس باید برجک %s را از سطح %d یک سطح بالا ببرد" % [id, level_before])
		await get_tree().process_frame
		check(tower.max_level() and hud._upgrade_button.disabled and hud._upgrade_button.text == "بالاترین سطح",
			"در بیشترین سطح دکمهٔ ارتقای %s باید «بالاترین سطح» و غیرفعال باشد" % id)
		var max_sell: Rect2 = hud._sell_button.get_global_rect()
		check(screen.encloses(hud._tower_panel.get_global_rect()) and screen.encloses(max_sell),
			"پنل برجک %s در بیشترین سطح باید کامل داخل صفحه باشد" % id)
		# فروش با لمس روی دکمهٔ «فروش»
		var refund: int = tower.sell_value()
		var coins_now: int = battle.coins
		var count_before: int = battle.towers.size()
		await _touch(max_sell.get_center(), true)
		await _touch(max_sell.get_center(), false)
		check(battle.towers.size() == count_before - 1 and battle.coins == coins_now + refund
			and battle.selected_tower == null and not hud._tower_panel.visible,
			"زدن دکمهٔ فروش با لمس باید برجک %s را بفروشد و پنل را ببندد" % id)
	battle.queue_free()
	await get_tree().process_frame

	# ۵) بررسی لمس و جاشدن کامل پنجرهٔ ارتقای زرادخانه (اسلحه‌ها) در صفحهٔ ۱۲۸۰×۷۲۰
	# (هم در حالت بدون ستاره با پیام «ستارهٔ کافی ندارید» و هم در حالت ارتقای واقعی با لمس)
	Game.weapon_levels = {}
	Game.stars = 0
	var armory = load("res://scenes/Armory.tscn").instantiate()
	add_child(armory)
	await get_tree().process_frame
	await get_tree().process_frame
	for idx in range(Weapons.count()):
		var wid := String(Weapons.LIST[idx]["id"])
		var card: Button = armory._list.get_child(idx + 1)
		card.pressed.emit()
		await get_tree().process_frame
		await get_tree().process_frame
		check(screen.encloses(armory._panel.get_global_rect()),
			"پنجرهٔ جزئیات زرادخانه در حالت بدون ستاره نیز باید داخل صفحه باشد (%s): %s"
			% [wid, armory._panel.get_global_rect()])
		armory._close_detail()
	Game.stars = 50
	armory._refresh_cards()
	await get_tree().process_frame
	for idx in range(Weapons.count()):
		var wid := String(Weapons.LIST[idx]["id"])
		var card: Button = armory._list.get_child(idx + 1)
		card.pressed.emit()
		await get_tree().process_frame
		await get_tree().process_frame
		var armory_panel_rect: Rect2 = armory._panel.get_global_rect()
		var armory_up_btn := _find_button_prefix(armory._detail, "ارتقا به سطح")
		check(armory_up_btn != null, "دکمهٔ ارتقای زرادخانه برای %s ساخته نشد" % wid)
		if armory_up_btn != null:
			var armory_btn_rect: Rect2 = armory_up_btn.get_global_rect()
			print("  armory %s: panel=%s up=%s" % [wid, armory_panel_rect, armory_btn_rect])
			check(screen.encloses(armory_panel_rect) and screen.encloses(armory_btn_rect),
				"پنجره و دکمهٔ ارتقای زرادخانه برای %s باید کامل داخل صفحهٔ ۱۲۸۰×۷۲۰ باشند: پنل %s دکمه %s"
				% [wid, armory_panel_rect, armory_btn_rect])
			await _touch(armory_btn_rect.get_center(), true)
			await _touch(armory_btn_rect.get_center(), false)
			check(Game.weapon_level(wid) == 1, "زدن دکمهٔ ارتقای زرادخانه با لمس باید سطح %s را به ۱ برساند" % wid)
		armory._close_detail()
	armory.queue_free()
	await get_tree().process_frame

	Game.unlocked_weapons = saved_unlocked
	Game.weapon_levels = saved_levels
	Game.stars = saved_stars
	Game.save()

func _find_button_prefix(node: Node, prefix: String) -> Button:
	if node is Button and (node as Button).text.begins_with(prefix):
		return node as Button
	for child in node.get_children():
		var found := _find_button_prefix(child, prefix)
		if found != null:
			return found
	return null
