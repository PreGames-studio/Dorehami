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
	print("— بررسی سربازان سنگر…")
	_test_sangar_soldiers()
	print("— بررسی صفحهٔ آغازین…")
	_test_launch_presentation()
	print("— بررسی پنجرهٔ تأییدیه…")
	_test_confirm_dialog()
	print("— ساخت صحنه‌ها…")
	await _smoke_scenes()
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
			# کارت سنگر باید در زرادخانه باز و بدون قفل ستاره باشد
			check(Game.is_weapon_unlocked("sangar"), "سنگر باید در زرادخانه از ابتدا باز باشد")

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
			check(_find_label(texts, "۲۰ ستاره") >= 0 and _find_label(texts, "۱۰ هزار تومان") >= 0,
				"بستهٔ ۲۰ ستاره به قیمت ۱۰ هزار تومان در بازار نیست")
			check(_find_label(texts, "۸۰ ستاره") >= 0 and _find_label(texts, "۵۰ هزار تومان") >= 0,
				"بستهٔ ۸۰ ستاره به قیمت ۵۰ هزار تومان در بازار نیست")
			check(_find_label(texts, "۱۵۰ ستاره") >= 0 and _find_label(texts, "۱۰۰ هزار تومان") >= 0,
				"بستهٔ ۱۵۰ ستاره به قیمت ۱۰۰ هزار تومان در بازار نیست")
		if path.ends_with("Settings.tscn"):
			var texts: Array = []
			_collect_labels(instance, texts)
			check(_find_label(texts, "سازندگان: مهدی زارعی و گروه PreGames Studio") >= 0,
				"خط سازندگان در شناسنامهٔ تنظیمات نیست")
			check(_find_label(texts, "ارتباط با ما : gf2227@gmail.com") >= 0,
				"خط ارتباط با ما در شناسنامهٔ تنظیمات نیست")
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
							"تصویر سنگر در لحظهٔ کشیدن باید بارگذاری شده باشد")
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

			# لمس روی برجک موجود در حالت آماده نباید سلاح را پاک کند یا کاشت کند
			var armed_id: String = instance.selected_weapon
			instance.coins = 500
			instance._handle_build_press(true, built_pos + Vector2(0.0, -14.0))
			instance._handle_build_press(false, built_pos + Vector2(0.0, -14.0))
			check(instance.selected_tower == null and instance.selected_weapon == armed_id,
				"لمس روی برجک موجود نباید سلاح آماده را پاک کند یا برجک را انتخاب کند")
			check(instance.towers.size() == 1, "کاشت روی برجک موجود مجاز نیست")
			# لمس ساده در جای آزاد کاشت نمی‌کند
			var free_pt := Vector2.ZERO
			for y in range(120, 620, 40):
				for x in range(160, 1120, 40):
					var pt := Vector2(x, y)
					if pt.distance_to(built_pos) > 120.0 and instance.map.build_issue(pt, instance.occupied).is_empty() and instance.map.build_issue(pt + Vector2(30.0, 0.0), instance.occupied).is_empty():
						free_pt = pt
						break
				if free_pt != Vector2.ZERO:
					break
			check(free_pt != Vector2.ZERO, "جای آزاد برای آزمون کاشت پیدا نشد")
			instance._handle_build_press(true, free_pt)
			instance._handle_build_press(false, free_pt)
			check(instance.towers.size() == 1 and instance.selected_weapon == armed_id, "لمس ساده در فضای آزاد نباید سنگر بکارد")
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
			# گرفتن و کشیدن برجک، حتی وقتی سلاح آماده است، همان را انتخاب می‌کند و آمادگی را لغو می‌کند
			var tower = instance.towers[0]
			instance._handle_build_press(true, built_pos + Vector2(0.0, -14.0))
			instance._handle_build_press(false, built_pos + Vector2(40.0, -14.0))
			check(instance.selected_tower == tower and instance.selected_weapon.is_empty(), "گرفتن و کشیدن برجک باید آن را انتخاب کند")
			# ارتقا از همان مسیر واقعی انتخاب (برجک انتخاب‌شده) انجام می‌شود
			var level_before: int = tower.level
			instance.coins = 500
			instance.upgrade_selected()
			check(tower.level == level_before + 1 and instance.coins == 500 - int(Weapons.by_id("sangar")["cost"]),
				"ارتقای سنگر از برجک انتخاب‌شده باید سطح و سکه را تغییر دهد")
			# لمس ساده روی برجک، بدون سلاح آماده، آن را انتخاب نمی‌کند
			instance.select_tower(null)
			instance.selected_weapon = ""
			instance._handle_build_press(true, built_pos + Vector2(0.0, -14.0))
			instance._handle_build_press(false, built_pos + Vector2(0.0, -14.0))
			check(instance.selected_tower == null, "لمس ساده روی برجک نباید آن را انتخاب کند")
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
	check(Weapons.count() == 1, "فقط یک برجک دفاعی (سنگر) باید تعریف شده باشد: %d" % Weapons.count())
	check(Weapons.ids() == ["sangar"], "شناسهٔ تنها برجک باید sangar باشد")
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
		check(Assets.has_key(Weapons.art_key(id)), "تصویر سنگر %s گم شده" % id)
		check(Assets.has_key("towers/sangar_soldier"), "تصویر سرباز داخل سنگر گم شده")
		check(Assets.has_key("towers/muzzle_1") and Assets.has_key("towers/muzzle_2"), "شعلهٔ دهانهٔ سنگر گم شده")
		if kind == "splash":
			check(float(weapon.get("splash", 0.0)) > 0.0, "شعاع ناحیه‌ای در %s صفر است" % id)
		if kind == "chain":
			check(int(weapon.get("chain", 0)) >= 2, "زنجیرهٔ %s کمتر از دو است" % id)
		if weapon.has("bullet"):
			check(Assets.has_key("bullets/%s" % String(weapon["bullet"])), "گلولهٔ %s گم شده" % id)
	check(Weapons.by_id("sangar").size() > 0, "سنگر پیدا نشد")
	check(Weapons.by_id("machine_gun_lv1").is_empty(), "سلاح‌های قدیمی باید به‌طور کامل حذف شده باشند")
	check(Weapons.by_id("omega").is_empty(), "ابرسلاح قدیمی omega باید حذف شده باشد")
	check(Weapons.by_id("nope").is_empty(), "شناسهٔ ناشناخته باید دیکشنری خالی بدهد")
	check(Weapons.FREE_IDS == ["sangar"], "سنگر باید تنها برجک رایگان باشد")
	check(String(Weapons.art_key("sangar")) == "towers/sangar", "کلید تصویر سنگر اشتباه است")
	check(String(Weapons.by_id("sangar").get("kind", "")) == "single", "سنگر باید تک‌تیرانداز باشد")
	check(Game.is_weapon_unlocked("sangar"), "سنگر باید به‌صورت پیش‌فرض باز باشد")
	check(int(Weapons.by_id("sangar")["cost"]) <= Balance.START_COINS,
		"سنگر باید با سکهٔ شروع نبرد قابل خرید باشد؛ وگرنه نبرد بدون برجک گیر می‌کند")


# ------------------------------------------------------------------ دشمنان

func _test_enemies() -> void:
	check(Enemies.LIST.size() == 1, "تعداد دشمنان باید ۱ (فقط سرباز) باشد")
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
		check(Assets.has_key(String(enemy.get("sprite", ""))), "اسپرایت دشمن %s گم شده" % id)
		check(Assets.has_key(String(enemy.get("sprite", "")) + "_walk"), "شیت انیمیشن راه رفتن دشمن %s گم شده" % id)
		for feature in enemy.get("traits", []):
			check(VALID_TRAITS.has(String(feature)), "ویژگی ناشناختهٔ %s در دشمن %s" % [feature, id])

# ------------------------------------------------------------------ نقشه‌ها

# ------------------------------------------------------------------ شیت‌های دویدن و مرگ

func _test_unit_sheets() -> void:
	for enemy in Enemies.LIST:
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

	# سرباز با شیت دویدن (۸ فریم) می‌رود؛ سر ثابت و پاها حرکت را می‌برند
	for case in [["soldier", "run"]]:
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
		for wave in range(1, Balance.WAVES + 1):
			var groups := Enemies.wave_groups(index, wave)
			check(not groups.is_empty(), "موج %d نقشهٔ %d خالی است" % [wave, index])
			var total := 0
			for group in groups:
				var id := String(group.get("enemy", ""))
				check(not Enemies.by_id(id).is_empty(), "دشمن ناشناخته در موج %d نقشهٔ %d: %s" % [wave, index, id])
				var count := int(group.get("count", 0))
				check(count > 0, "گروه خالی در موج %d نقشهٔ %d" % [wave, index])
				check(float(group.get("interval", 0.0)) > 0.0, "فاصلهٔ نامعتبر در موج %d نقشهٔ %d" % [wave, index])
				total += count
			check(total >= 3, "موج %d نقشهٔ %d کم‌جمعیت است: %d" % [wave, index, total])


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

# ------------------------------------------------------------------ سربازان سنگر

## هر سطح ارتقای سنگر یک سرباز تازه داخل سنگر اضافه می‌کند: سطح ۱ یک سرباز و بیشترین سطح چهار سرباز.
## سربازها باید در جای متفاوت و قرینه چیده شوند و با سطح بالاتر هم حذف نشوند.
func _test_sangar_soldiers() -> void:
	var tower := Tower.new()
	add_child(tower)
	tower.setup(Weapons.by_id("sangar"), null)
	check(tower.soldier_count() == 1 and tower._soldiers.size() == 1,
		"سنگر در سطح ۱ باید یک سرباز داشته باشد")
	for expected in range(2, Balance.MAX_LEVEL + 1):
		tower.upgrade()
		check(tower.level == expected and tower._soldiers.size() == expected,
			"سنگر در سطح %d باید %d سرباز داشته باشد" % [expected, expected])
	check(tower._soldiers.size() == Balance.MAX_LEVEL, "سنگر در بیشترین سطح باید چهار سرباز داشته باشد")
	var distinct := true
	for a in range(tower._soldiers.size()):
		for b in range(a + 1, tower._soldiers.size()):
			if tower._soldiers[a].position.is_equal_approx(tower._soldiers[b].position):
				distinct = false
	check(distinct, "سربازهای داخل سنگر نباید روی هم قرار بگیرند")
	# خود سنگر باید ثابت بماند؛ فقط سربازها نفس می‌کشند و تکان می‌خورند
	tower._process(1.0)
	check(tower._base.scale.is_equal_approx(Vector2.ONE) and tower._base.position.is_equal_approx(Tower.PIVOT_OFFSET),
		"خود سنگر باید پس از کاشته‌شدن ثابت بماند")
	var soldier_before: Vector2 = tower._soldiers[0].scale
	tower._process(0.3)
	check(not soldier_before.is_equal_approx(tower._soldiers[0].scale), "سربازهای داخل سنگر باید حرکت داشته باشند")
	check(tower._base.scale.is_equal_approx(Vector2.ONE), "حرکت سرباز نباید سنگر را بزرگ یا کوچک کند")
	tower.upgrade()
	check(tower._soldiers.size() == Balance.MAX_LEVEL, "ارتقا در سقف سطح نباید سرباز اضافه کند")
	tower.queue_free()

# ------------------------------------------------------------------ برد شلیک برجک

## برجک نباید بیرون از برد خودش شلیک کند: نه هدفی بیرون از برد بگیرد و نه هدفی
## که از برد بیرون رفته را نگه دارد. پیش‌تر هدف تا مرگِ دشمن نگه داشته می‌شد و
## برجک بعد از خروجِ دشمن از دایرهٔ برد هم به او شلیک می‌کرد.
func _test_tower_range() -> void:
	var weapon := Weapons.by_id("sangar")
	check(not weapon.is_empty(), "سنگر برای سنجش برد پیدا نشد")
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

	# تیرهای اضافی سلاح چندشلیک (نسخهٔ آزمایشی سنگر با ۴ تیر) هم نباید بیرون از برد را بزنند
	var volley: Dictionary = weapon.duplicate()
	volley["shots"] = 4
	check(not volley.is_empty(), "نسخهٔ چندشلیک سنگر برای سنجش ساخته نشد")
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
