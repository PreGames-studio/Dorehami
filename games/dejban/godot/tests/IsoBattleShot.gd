## عکس نبرد برای بررسی چشمی پک‌های ایزومتریک (ابزار توسعه؛ بخشی از روند بازی نیست).
##
## خود صحنهٔ نبرد بازی (scenes/Battle.tscn) با نوار ساخت کامل باز می‌شود؛ چهار برجک ایزومتریک
## از همان مسیر ساخت بازی (Battle._try_build) کنار جاده کاشته می‌شوند، چند تانک و
## سرباز با Battle._spawn_enemy روی مسیر قرار می‌گیرند (یک تانک در حال محاصرهٔ دژ) و پس از
## چند لحظه بازیِ واقعی، از viewport خود بازی عکس PNG گرفته می‌شود؛ پیش از عکس آخر برجک مسلسل
## انتخاب می‌شود تا پنل ارتقا و فروش هم دیده شود.
##
##   godot --path games/dejban/godot --fixed-fps 30 res://tests/iso_battle_shot.tscn -- out.png [map]
##
## به راه‌انداز گرافیکی واقعی نیاز دارد؛ با --headless تصویری رندر نمی‌شود.
extends Node

const BATTLE_SCENE := "res://scenes/Battle.tscn"
## لحظه‌های عکس (ثانیه پس از شروع محاصره). اولین شلیک لیزر تانک حدود ۰٫۷۴ ثانیه است.
const SHOT_TIMES := [0.80, 0.86, 1.40]
const ISO_ORDER := ["machinegun", "cannon", "rocket", "laser"]

var _battle = null


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	var out := "user://iso_battle.png" if args.is_empty() else String(args[0])
	var map_index := 0 if args.size() < 2 else int(args[1])
	Game.pending_map = map_index
	Game.speed_index = 0
	Game.unlocked_weapons = Weapons.ids().duplicate()
	_battle = load(BATTLE_SCENE).instantiate()
	add_child(_battle)
	await get_tree().process_frame
	await get_tree().process_frame

	var map: GameMap = _battle.map
	_battle.coins = 99999
	var spots := _tower_spots(map)
	var order: Array = ISO_ORDER.duplicate()
	for i in mini(order.size(), spots.size()):
		_battle.selected_weapon = String(order[i])
		_battle._try_build(spots[i])
	_battle._cancel_build_preview()
	print("towers: ", _battle.towers.size(), " at ", spots)

	# دشمنان: یک تانک در محاصره، دو تانک و چند سرباز روی مسیر؛ جانشان زیاد تا تا عکس بمانند
	var siege_tank = _spawn("tank", -1.0)
	_spawn("tank", 0.62)
	_spawn("tank", 0.40)
	for f in [0.30, 0.48, 0.55, 0.70, 0.78]:
		_spawn("soldier", f)
	_battle.coins = 240
	_battle.hud.refresh()

	var t := 0.0
	var index := 0
	var stem := out.get_basename()
	var panel_opened := false
	while index < SHOT_TIMES.size():
		await get_tree().process_frame
		t += get_process_delta_time()
		# پیش از عکس آخر، برجک مسلسل انتخاب می‌شود تا پنل ارتقا/فروش هم در عکس دیده شود
		if not panel_opened and index == SHOT_TIMES.size() - 1 and t >= float(SHOT_TIMES[index]) - 0.2 and not _battle.towers.is_empty():
			_battle.select_tower(_battle.towers[0])
			panel_opened = true
		if t >= float(SHOT_TIMES[index]):
			await RenderingServer.frame_post_draw
			var image := get_viewport().get_texture().get_image()
			var path := out if index == 0 else "%s_%d.png" % [stem, index]
			var err := image.save_png(path)
			print("shot %d at %.2fs -> %s (%s) siege=%s laser_shots=%d" % [index, t, path, error_string(err),
				siege_tank.sieging if is_instance_valid(siege_tank) else false,
				siege_tank.shots_fired if is_instance_valid(siege_tank) else -1])
			index += 1
	get_tree().quit()


## نقطه‌های ساخت مجاز نزدیک جاده، پراکنده در نیمهٔ دوم مسیر (نزدیک دژ) و جدا از هم.
func _tower_spots(map: GameMap) -> Array:
	var out: Array = []
	var occupied: Dictionary = _battle.occupied
	var fractions := [0.56, 0.72, 0.84, 0.64, 0.46, 0.92, 0.36]
	for frac in fractions:
		var at: float = map.total_length * float(frac)
		var p := map.point_at(at)
		var d := map.direction_at(at)
		var n := Vector2(-d.y, d.x)
		for side in [1.0, -1.0]:
			var found := false
			for dist in [70.0, 84.0, 98.0, 112.0]:
				var cell: Vector2 = _battle._snap(p + n * side * dist)
				if not map.build_issue(cell, occupied).is_empty():
					continue
				# بیرون از نوار بالا و پنل ساخت پایین تا برجک در عکس دیده شود
				if cell.y < 140.0 or cell.y > 575.0 or cell.x < 50.0 or cell.x > 1230.0:
					continue
				var clear := true
				for other in out:
					if (other as Vector2).distance_to(cell) < 110.0:
						clear = false
				if clear:
					out.append(cell)
					found = true
					break
			if found:
				break
	return out


func _spawn(id: String, fraction: float):
	_battle._spawn_enemy(id)
	var enemy = _battle.enemies[-1]
	enemy.max_hp *= 30.0
	enemy.hp = enemy.max_hp * 0.82
	if fraction < 0.0:
		enemy.progress = enemy.siege_progress - 0.01
	else:
		enemy.progress = _battle.map.total_length * fraction
	enemy._update_transform(0.0)
	return enemy
