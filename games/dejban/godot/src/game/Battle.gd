## میدان نبرد: نقشه را می‌سازد، موج‌ها را می‌فرستد، ساخت‌وساز و ارتقا را
## مدیریت می‌کند و نتیجهٔ نبرد را ثبت می‌کند.
##
## چرخهٔ بازی: prep (شمارش پیش از موج) → wave (موج در جریان) → prep …
## و در پایان یا won یا lost.
extends Node2D

const HUD_SCRIPT := "res://src/ui/Hud.gd"
const HUMANOID_ENEMIES := ["soldier"]

var map: GameMap = null
var keep: Keep = null
var ghost: BuildGhost = null
var guide: BuildGuide = null
var hud = null

var coins := 0
var lives := Balance.START_LIVES
var wave_index := 0
var state := "prep"
var countdown := 0.0
var speed_index := 0
var kills := 0
var enemies: Array = []
var towers: Array = []
var occupied: Dictionary = {}
var spawn_queue: Array = []
var wave_time := 0.0
var selected_weapon := ""
var selected_tower = null
var running := true
var paused := false
var _drag_active := false
## اگر سلاح از قبل آماده بود و کشیدن از روی دکمهٔ نوار شروع شد، رهاکردن روی نوار آن را لغو می‌کند.
var _disarm_on_release := false
## کشیدن برجک برای انتخاب: لمس روی برجک، کشیدن حداقل به اندازهٔ این فاصله، و رهاکردن.
## لمس ساده روی برجک آن را انتخاب نمی‌کند.
const GRAB_DRAG_DISTANCE := 14.0
var _grab_tower = null
var _grab_start := Vector2.ZERO
## نقطهٔ شروع کشیدن برای کاشت؛ رهاکردنِ نزدیک به آن کاشت نیست (لمس ساده).
var _drag_start := Vector2.ZERO

var _world: Node2D = null
var _ground: Ground = null
var _units: Node2D = null
var _fx_layer: Node2D = null
var _hud_layer: CanvasLayer = null
var _ghost_layer: CanvasLayer = null
var _aura_timer := 0.0
var _aura_ticks := 0
var _rng := RandomNumberGenerator.new()

# ------------------------------------------------------------------ راه‌اندازی

func _ready() -> void:
	_rng.randomize()
	map = GameMap.build(Game.pending_map)
	Game.last_map = map.index
	coins = Balance.START_COINS + Game.prestige_starting_coins()
	lives = Balance.START_LIVES
	_build_layers()
	_build_keep()
	guide = BuildGuide.new()
	guide.z_index = 18
	_world.add_child(guide)
	_hud_layer = CanvasLayer.new()
	_hud_layer.name = "HudLayer"
	_hud_layer.layer = 10
	add_child(_hud_layer)
	hud = load(HUD_SCRIPT).new()
	hud.battle = self
	hud.process_mode = Node.PROCESS_MODE_ALWAYS
	_hud_layer.add_child(hud)
	# لایهٔ پیش‌نمای برجک در حال کشیدن بالای نوار ابزار قرار می‌گیرد تا از لحظهٔ لمس تا رها شدن کاملاً مرئی باشد
	_ghost_layer = CanvasLayer.new()
	_ghost_layer.name = "GhostLayer"
	_ghost_layer.layer = 15
	add_child(_ghost_layer)
	ghost = BuildGhost.new()
	ghost.z_index = 20
	ghost.visible = false
	_ghost_layer.add_child(ghost)
	# به طور پیش‌فرض اولین برجک باز را انتخاب می‌کنیم تا بازیکن بلافاصله بتواند بکارد
	if not Game.unlocked_weapons.is_empty():
		selected_weapon = String(Game.unlocked_weapons[0])
	_refresh_ghost()
	_refresh_guide()
	countdown = Balance.FIRST_WAVE_DELAY
	state = "prep"
	set_speed(Game.speed_index)
	Audio.play_music("music_battle")

## میدان در یک گرهٔ جدا («دنیا») نگه داشته می‌شود تا لرزش صفحه فقط بازی را
## بلرزاند و نوار واسط تکان نخورد. برجک‌ها، دشمنان و دژ در یک لایهٔ مرتب‌شده
## بر پایهٔ Y هستند تا عمق درست داشته باشند.
func _build_layers() -> void:
	_world = Node2D.new()
	_world.name = "World"
	_world.y_sort_enabled = true
	add_child(_world)

	_ground = Ground.new()
	_ground.z_index = -20
	_world.add_child(_ground)
	_ground.setup(map)

	_units = Node2D.new()
	_units.name = "Units"
	_units.y_sort_enabled = true
	_world.add_child(_units)

	_fx_layer = Node2D.new()
	_fx_layer.name = "Fx"
	_fx_layer.z_index = 30
	_world.add_child(_fx_layer)

func _build_keep() -> void:
	keep = Keep.new()
	keep.position = map.keep_pos
	keep.setup(lives)
	keep.damaged.connect(_on_keep_damaged)
	keep.destroyed.connect(_on_keep_destroyed)
	_world.add_child(keep)

func fx_layer() -> Node2D:
	return _fx_layer

func _exit_tree() -> void:
	Engine.time_scale = 1.0
	if get_tree() != null:
		get_tree().paused = false

# ------------------------------------------------------------------ چرخهٔ موج‌ها

func _process(delta: float) -> void:
	if not running:
		return
	if state == "prep":
		countdown -= delta
		if countdown <= 0.0:
			start_wave()
	elif state == "wave":
		wave_time += delta
		_spawn_due()
		if spawn_queue.is_empty() and enemies.is_empty():
			_end_wave()
	_aura_timer -= delta
	if _aura_timer <= 0.0:
		_aura_timer = 0.4
		_apply_auras()

func start_wave() -> void:
	if state != "prep" or not running:
		return
	wave_index += 1
	state = "wave"
	wave_time = 0.0
	spawn_queue.clear()
	for group in Enemies.wave_groups(map.index, wave_index):
		var count := int(group.get("count", 1))
		var interval := float(group.get("interval", 1.0))
		var delay := float(group.get("delay", 0.0))
		for i in count:
			spawn_queue.append({"enemy": String(group["enemy"]), "time": delay + i * interval})
	spawn_queue.sort_custom(_sort_spawn)
	Audio.play("wave_start")
	if hud != null:
		hud.banner("موج %s از %s" % [Ui.fa(wave_index), Ui.fa(Balance.WAVES)])

func _sort_spawn(a: Dictionary, b: Dictionary) -> bool:
	return float(a["time"]) < float(b["time"])

func _spawn_due() -> void:
	while not spawn_queue.is_empty() and float(spawn_queue[0]["time"]) <= wave_time:
		var entry: Dictionary = spawn_queue.pop_front()
		_spawn_enemy(String(entry["enemy"]))

func _spawn_enemy(id: String) -> void:
	var def := Enemies.by_id(id)
	if def.is_empty():
		return
	# دشمن‌های پک ایزومتریک (تانک) با IsoEnemy ساخته می‌شوند؛ سرباز مثل قبل.
	var enemy: Enemy = IsoEnemy.new() if def.has("iso") else Enemy.new()
	_units.add_child(enemy)
	enemy.setup(def, map, Balance.enemy_hp_scale(map.index, wave_index),
		Balance.enemy_speed_scale(map.index, wave_index), wave_index, _rng.randf_range(-4.0, 4.0))
	enemy.died.connect(_on_enemy_died)
	enemy.reached_keep.connect(_on_enemy_reached)
	enemy.keep_attacked.connect(_on_keep_attacked)
	enemies.append(enemy)

func _end_wave() -> void:
	if not running or lives <= 0 or state == "won" or state == "lost":
		return
	Audio.play("wave_clear")
	var bonus := 6 + int(wave_index / 2.0)
	coins += bonus
	Fx.text(_fx_layer, map.keep_pos + Vector2(0.0, -140.0), "+" + Ui.fa(bonus) + " سکه", Style.gold, 22)
	if wave_index >= Balance.WAVES:
		_win()
		return
	state = "prep"
	countdown = Balance.WAVE_DELAY
	if hud != null:
		hud.banner("موج %s پاک شد" % Ui.fa(wave_index))
		hud.toast("پاداش پایان موج: %s سکه" % Ui.fa(bonus))

func _cancel_build_preview() -> void:
	_drag_active = false
	selected_weapon = ""
	if selected_tower != null and is_instance_valid(selected_tower):
		selected_tower.set_selected(false)
	selected_tower = null
	if ghost != null:
		ghost.visible = false
	if guide != null:
		guide.clear()
	if hud != null:
		hud.refresh()

func _win() -> void:
	if state == "won" or state == "lost":
		return
	state = "won"
	running = false
	_cancel_build_preview()
	Engine.time_scale = 1.0
	Audio.play("victory")
	var result := Game.record_run(map.index, wave_index, true)
	if hud != null:
		hud.show_result(true, result, wave_index)

func _lose() -> void:
	if state == "won" or state == "lost":
		return
	state = "lost"
	running = false
	_cancel_build_preview()
	Engine.time_scale = 1.0
	Audio.play("defeat")
	var result := Game.record_run(map.index, wave_index, false)
	if hud != null:
		hud.show_result(false, result, wave_index)

# ------------------------------------------------------------------ رویداد دشمنان

func _on_enemy_died(enemy) -> void:
	enemies.erase(enemy)
	if not is_instance_valid(enemy):
		return
	kills += 1
	var prize := Balance.enemy_reward(int(enemy.reward), wave_index)
	coins += prize
	Fx.blast(_fx_layer, enemy.position, clampf(enemy.scale_mult, 0.7, 1.7))
	Fx.text(_fx_layer, enemy.position + Vector2(0.0, -20.0), "+" + Ui.fa(prize), Style.gold, 18)
	var enemy_id := String(enemy.def.get("id", ""))
	if enemy_id in HUMANOID_ENEMIES:
		Audio.play_combat_limited("combat/soldier-defeat", 300, 0.04, -10.0)
	else:
		Audio.play_combat_limited("combat/cannon-wreck", 180, 0.05, -13.0)
	# The body is not freed right away: it plays its death animation and frees
	# itself (Enemy._advance_death). It was already removed from `enemies`
	# above, so wave-clear timing and tower targeting are unchanged.

func _on_enemy_reached(enemy) -> void:
	enemies.erase(enemy)
	if not is_instance_valid(enemy):
		return
	var damage: int = 5 if enemy.has_trait("boss") else 1
	keep.take_damage(damage)
	Audio.play("keep_hit")
	Fx.shake(_world, 8.0, 0.32)
	Fx.text(_fx_layer, keep.position + Vector2(0.0, -118.0), "-" + Ui.fa(damage), Style.bad, 22)
	enemy.queue_free()

## تانکِ در حال محاصره با هر شلیک لیزر جان دژ را کم می‌کند، ولی خودش در میدان می‌ماند
## (برخلاف سربازی که با رسیدن به دژ حذف می‌شود)؛ پس موج تا نابودی‌اش تمام نمی‌شود.
func _on_keep_attacked(enemy, amount: int) -> void:
	if not running or keep == null or not is_instance_valid(enemy) or not enemy.alive:
		return
	# پرتو قرمز از نوک لیزرِ فریم attack تانک تا بدنهٔ دژ.
	var hit_point := keep.position + Vector2(0.0, -26.0)
	if enemy.has_method("laser_tip"):
		Fx.beam(_fx_layer, "fx/laser_beam", enemy.laser_tip(), hit_point, 0.55, IsoEnemy.LASER_TINT)
	Fx.impact(_fx_layer, hit_point, 0.5)
	keep.take_damage(amount)
	Audio.play("keep_hit")
	Fx.shake(_world, 5.0, 0.22)
	Fx.text(_fx_layer, keep.position + Vector2(0.0, -118.0), "-" + Ui.fa(amount), Style.bad, 22)

func _on_keep_damaged(lives_after: int) -> void:
	lives = lives_after
	if hud != null:
		hud.flash_danger()

func _on_keep_destroyed() -> void:
	_lose()

## درمانگرها هم‌نوعان را درمان و فرمانده‌ها آن‌ها را تندتر می‌کنند.
func _apply_auras() -> void:
	_aura_ticks += 1
	for enemy in enemies:
		if is_instance_valid(enemy):
			enemy.speed_mult = 1.0
	for enemy in enemies:
		if not is_instance_valid(enemy) or not enemy.alive:
			continue
		if enemy.has_trait("healer"):
			var healed := false
			for other in enemies:
				if other == enemy or not is_instance_valid(other) or not other.alive:
					continue
				if other.hp >= other.max_hp:
					continue
				if other.position.distance_to(enemy.position) <= 118.0:
					other.heal(9.0 * 0.4)
					healed = true
			if healed and _aura_ticks % 4 == 0:
				Fx.burst(_fx_layer, "fx/heal", enemy.position + Vector2(0.0, -26.0), 0.6, 0.5, 1.3)
		if enemy.has_trait("buff"):
			for other in enemies:
				if not is_instance_valid(other) or not other.alive:
					continue
				if other.position.distance_to(enemy.position) <= 132.0:
					other.speed_mult = maxf(other.speed_mult, 1.25)

# ------------------------------------------------------------------ ورودی و کشیدن و رهاکردن برجک

## ردیابی کشیدن و رهاکردن برجک در سطح کل صفحه، حتی زمانی که لمس از روی دکمهٔ نوار پایین شروع شده است.
func _input(event: InputEvent) -> void:
	if not running or paused or not _drag_active or selected_weapon.is_empty():
		return
	if event is InputEventMouseMotion:
		_update_ghost(get_global_mouse_position())
	elif event is InputEventScreenDrag:
		var world: Vector2 = get_canvas_transform().affine_inverse() * event.position
		_update_ghost(world)
	elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and not event.pressed:
		_finish_drag(get_global_mouse_position())
	elif event is InputEventScreenTouch and not event.pressed:
		var world: Vector2 = get_canvas_transform().affine_inverse() * event.position
		_finish_drag(world)

func _unhandled_input(event: InputEvent) -> void:
	if not running or paused:
		return
	if event is InputEventMouseMotion:
		if not selected_weapon.is_empty():
			_update_ghost(get_global_mouse_position())
	elif event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT:
		_handle_build_press(event.pressed, get_global_mouse_position())
	elif event is InputEventScreenTouch:
		var world: Vector2 = get_canvas_transform().affine_inverse() * event.position
		_handle_build_press(event.pressed, world)
	elif event is InputEventScreenDrag:
		if not selected_weapon.is_empty():
			var world: Vector2 = get_canvas_transform().affine_inverse() * event.position
			_update_ghost(world)
	elif event is InputEventKey and event.pressed and not event.echo:
		match event.keycode:
			KEY_SPACE:
				if state == "prep":
					start_wave()
			KEY_ESCAPE:
				toggle_pause()
			KEY_U:
				upgrade_selected()
			KEY_S:
				sell_selected()
			KEY_TAB:
				cycle_speed()

## آیا نقطه داخل ناحیهٔ آزاد میدان (بیرون از نوار بالا و نوار پایین‌چپ برجک‌ها) است؟
func _in_build_area(world: Vector2) -> bool:
	if hud != null and hud.has_method("is_over_weapon_bar") and hud.is_over_weapon_bar(world):
		return false
	return world.y >= Balance.HUD_HEIGHT and GameMap.in_terrain(world)

## شروع کشیدن برجک از روی دکمهٔ نوار پایین‌چپ؛ همیشه آن برجک را فعال و از همان لحظه مرئی می‌کند.
func begin_weapon_drag(id: String, start_world := Vector2(-1.0, -1.0)) -> void:
	if not running or paused:
		return
	# لمس اولِ دکمه چند رویداد پشت‌سرهم می‌فرستد (button_down و gui_input)؛ اگر همان سلاح از قبل
	# در حال کشیدن است، فقط شروع دوباره نشود تا حالت آماده‌بودن بی‌دلیل عوض نشود.
	if _drag_active and selected_weapon == id:
		return
	_disarm_on_release = selected_weapon == id
	selected_weapon = id
	if selected_tower != null and is_instance_valid(selected_tower):
		selected_tower.set_selected(false)
	selected_tower = null
	_drag_active = true
	Audio.ui_click()
	_refresh_guide()
	if ghost != null:
		ghost.set_weapon(Weapons.by_id(selected_weapon))
	var initial_pos := start_world if start_world.x >= 0.0 else get_global_mouse_position()
	_drag_start = initial_pos
	_update_ghost(initial_pos)
	if hud != null:
		hud.refresh()

func _finish_drag(world: Vector2) -> void:
	if not _drag_active:
		return
	_drag_active = false
	var disarm := _disarm_on_release
	_disarm_on_release = false
	var dragged := world.distance_to(_drag_start) >= GRAB_DRAG_DISTANCE
	if _in_build_area(world):
		if dragged:
			_try_build(world)
		else:
			# کاشت فقط با گرفتن و کشیدن است؛ لمس ساده در فضای آزاد سنگر نمی‌کارد.
			Fx.text(_fx_layer, world + Vector2(0.0, -26.0), "برای کاشت، سنگر را بگیر و بکش", Style.gold, 16)
	elif disarm and hud != null and hud.is_over_weapon_bar(world):
		# روی همان دکمهٔ آمادهٔ نوار رها شد: لغو کاشت
		_cancel_build_preview()
		return
	if ghost != null:
		ghost.visible = false
	_refresh_guide()

## لمس روی برجک موجود همیشه ثبت می‌شود: کشیدنِ آن برجک را انتخاب می‌کند (حتی وقتی سلاحی آماده است).
## با سلاح آماده، لمس روی جای آزاد آغاز کشیدن برای کاشت است؛ لمس ساده کاشت نمی‌کند.
func _handle_build_press(pressed: bool, world: Vector2) -> void:
	if pressed:
		_grab_tower = _tower_at(world)
		_grab_start = world
		if selected_weapon.is_empty() or _grab_tower != null:
			return
		_drag_active = true
		_drag_start = world
		_refresh_guide()
		if ghost != null:
			ghost.set_weapon(Weapons.by_id(selected_weapon))
		_update_ghost(world)
		return
	if _grab_tower != null:
		var grabbed = _grab_tower
		_grab_tower = null
		if is_instance_valid(grabbed) and world.distance_to(_grab_start) >= GRAB_DRAG_DISTANCE:
			select_tower(grabbed)
		return
	_finish_drag(world)

## مختصات دقیق نقطهٔ رهاشده در داخل محدودهٔ میدان (بدون مقید کردن به سکو یا شبکه).
func _snap(pos: Vector2) -> Vector2:
	return Vector2(
		clampf(pos.x, 18.0, Balance.WORLD_SIZE.x - 18.0),
		clampf(pos.y, Balance.HUD_HEIGHT + 6.0, Balance.WORLD_SIZE.y - 18.0)
	)

func _tower_at(world: Vector2) -> Variant:
	for tower in towers:
		if is_instance_valid(tower) and (tower.position + Vector2(0.0, -14.0)).distance_to(world) <= 42.0:
			return tower
	return null

func _try_build(world: Vector2) -> void:
	if world.y < Balance.HUD_HEIGHT:
		return
	var cell := _snap(world)
	var weapon := Weapons.by_id(selected_weapon)
	if weapon.is_empty():
		return
	var issue := map.build_issue(cell, occupied)
	if not issue.is_empty():
		Audio.play("deny")
		Fx.text(_fx_layer, cell + Vector2(0.0, -26.0), issue, Style.bad, 18)
		return
	var cost := int(weapon.get("cost", 0))
	if coins < cost:
		Audio.play("deny")
		Fx.text(_fx_layer, cell + Vector2(0.0, -26.0), "سکه کافی نیست", Style.bad, 18)
		return
	coins -= cost
	var tower := Tower.new()
	_units.add_child(tower)
	tower.setup(weapon, self)
	tower.position = cell
	towers.append(tower)
	occupied[cell] = tower
	Audio.play("build")
	Fx.ring(_fx_layer, cell, 44.0, Color(0.7, 1.0, 0.85, 0.9), 0.35)
	_refresh_guide()
	if hud != null:
		hud.refresh()

func select_weapon(id: String) -> void:
	if id == selected_weapon:
		selected_weapon = ""
	else:
		selected_weapon = id
	select_tower(null)
	Audio.ui_click()
	_refresh_ghost()
	_refresh_guide()
	if hud != null:
		hud.refresh()

func select_tower(tower) -> void:
	if selected_tower != null and is_instance_valid(selected_tower):
		selected_tower.set_selected(false)
	selected_tower = tower
	if tower != null and is_instance_valid(tower):
		tower.set_selected(true)
		selected_weapon = ""
	_refresh_ghost()
	_refresh_guide()
	if hud != null:
		hud.refresh()

func _refresh_ghost() -> void:
	if ghost == null:
		return
	if selected_weapon.is_empty():
		ghost.set_weapon({})
		return
	ghost.set_weapon(Weapons.by_id(selected_weapon))
	if _drag_active:
		_update_ghost(get_global_mouse_position())
	else:
		ghost.visible = false

func _update_ghost(world: Vector2) -> void:
	if ghost == null or selected_weapon.is_empty():
		return
	if not _drag_active and not _in_build_area(world):
		ghost.visible = false
		return
	var weapon := Weapons.by_id(selected_weapon)
	if weapon.is_empty():
		ghost.visible = false
		return
	var in_area := _in_build_area(world)
	var cell := _snap(world) if in_area else Vector2(
		clampf(world.x, 18.0, Balance.WORLD_SIZE.x - 18.0),
		clampf(world.y, 18.0, Balance.WORLD_SIZE.y - 18.0)
	)
	var issue := map.build_issue(cell, occupied) if in_area else "خارج از محدودهٔ کاشت"
	ghost.visible = true
	# دایرهٔ پیش‌نمایش با همان حساب بردِ خودِ برجک کشیده می‌شود (برجک تازه سطح ۱ است)
	ghost.update_preview(cell, in_area and issue.is_empty(), Tower.range_for(weapon, 1), issue)

## هنگام کشیدن برجک، حریم برجک‌های کاشته‌شده را نشان می‌دهد.
func _refresh_guide() -> void:
	if guide == null:
		return
	if not _drag_active or selected_weapon.is_empty() or map == null:
		guide.clear()
		return
	var tower_positions := PackedVector2Array()
	for pos in occupied.keys():
		tower_positions.append(pos as Vector2)
	guide.show_spots(tower_positions)

# ------------------------------------------------------------------ ارتقا و فروش

func upgrade_selected() -> void:
	if selected_tower == null or not is_instance_valid(selected_tower):
		return
	if selected_tower.max_level():
		Audio.play("deny")
		Fx.text(_fx_layer, selected_tower.position + Vector2(0.0, -44.0), "به بالاترین سطح رسیده", Style.gold, 18)
		return
	var cost: int = selected_tower.upgrade_cost()
	if coins < cost:
		Audio.play("deny")
		Fx.text(_fx_layer, selected_tower.position + Vector2(0.0, -44.0), "سکه کافی نیست", Style.bad, 18)
		return
	coins -= cost
	selected_tower.upgrade()
	Audio.play("upgrade")
	Fx.burst(_fx_layer, "fx/star", selected_tower.position + Vector2(0.0, -18.0), 0.7, 0.5, 1.5)
	Fx.text(_fx_layer, selected_tower.position + Vector2(0.0, -44.0), "سطح " + Ui.fa(selected_tower.level), Style.good, 20)
	if hud != null:
		hud.refresh()

func sell_selected() -> void:
	if selected_tower == null or not is_instance_valid(selected_tower):
		return
	var refund: int = selected_tower.sell_value()
	coins += refund
	towers.erase(selected_tower)
	var key = _occupied_key(selected_tower.position)
	if key != null:
		occupied.erase(key)
	Fx.text(_fx_layer, selected_tower.position + Vector2(0.0, -30.0), "+" + Ui.fa(refund), Style.gold, 20)
	Fx.burst(_fx_layer, "fx/coin", selected_tower.position + Vector2(0.0, -10.0), 0.8, 0.5, 1.4)
	Audio.play("sell")
	_refresh_guide()
	selected_tower.queue_free()
	selected_tower = null
	if hud != null:
		hud.refresh()

func _occupied_key(pos: Vector2) -> Variant:
	for key in occupied.keys():
		if (key as Vector2).distance_to(pos) < 1.0:
			return key
	return null

# ------------------------------------------------------------------ سرعت و توقف

func cycle_speed() -> void:
	set_speed((speed_index + 1) % Balance.SPEEDS.size())
	Audio.ui_click()

func set_speed(index: int) -> void:
	speed_index = clampi(index, 0, Balance.SPEEDS.size() - 1)
	Game.speed_index = speed_index
	Engine.time_scale = float(Balance.SPEEDS[speed_index])
	if hud != null:
		hud.refresh()

func toggle_pause() -> void:
	if state == "won" or state == "lost":
		return
	paused = not paused
	get_tree().paused = paused
	if hud != null:
		hud.set_paused(paused)

func restart() -> void:
	Engine.time_scale = 1.0
	get_tree().paused = false
	get_tree().reload_current_scene()

## بازگشت به اطلس نقشه‌ها؛ اگر نبرد در جریان بوده، بهترین موج ثبت می‌شود.
func exit_to_map(record := true) -> void:
	if record and running and wave_index > 0:
		Game.record_run(map.index, maxi(0, wave_index - 1), false)
	Engine.time_scale = 1.0
	get_tree().paused = false
	get_tree().change_scene_to_file("res://scenes/MapSelect.tscn")

func next_map() -> void:
	var index := Game.next_unlocked_map(map.index)
	Game.pending_map = index
	Engine.time_scale = 1.0
	get_tree().paused = false
	get_tree().change_scene_to_file("res://scenes/Battle.tscn")

func replay() -> void:
	Game.pending_map = map.index
	restart()
