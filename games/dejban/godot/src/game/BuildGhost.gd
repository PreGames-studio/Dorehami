## پیش‌نمای ساخت هنگام کشیدن برجک:
## خودِ برجک از لحظهٔ گرفتن تا زمان رها شدن زیر انگشت/نشانگر کاملاً مرئی است و دایرهٔ برد آن
## در نواحی مجاز به رنگ سبز و در محدودهٔ ممنوعه به رنگ قرمز دیده می‌شود. همان فریمی نشان داده
## می‌شود که برجک پس از کاشت با آن شروع می‌کند (idle، جهت Weapons.ICON_DIR) و با همان نقطهٔ
## زمین و اندازهٔ Tower.
class_name BuildGhost
extends Node2D

const PIVOT_OFFSET := Vector2(0.0, -18.0)

var weapon: Dictionary = {}
var ok := true
var reach := 160.0
var issue := ""

var _base: Sprite2D = null

func _ready() -> void:
	_ensure_sprites()

func _ensure_sprites() -> void:
	if _base == null:
		_base = Sprite2D.new()
		_base.position = PIVOT_OFFSET
		_base.z_index = 1
		add_child(_base)

func set_weapon(weapon_def: Dictionary) -> void:
	_ensure_sprites()
	weapon = weapon_def
	if weapon.is_empty():
		visible = false
		_base.texture = null
		queue_redraw()
		return
	var wid := String(weapon.get("id", ""))
	_base.texture = Weapons.icon(wid)
	_base.centered = false
	if String(weapon.get("visual", "")) == "laser_layers":
		# Armory uses a cropped icon; the ghost uses the real full-frame anchor.
		_base.texture = Assets.tex(Weapons.art_key(wid))
		_base.offset = -LaserVisual.GROUND
		_base.scale = Vector2.ONE * LaserVisual.IMAGE_SCALE
		_base.position = LaserVisual.FOOTPRINT_OFFSET
		_base.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	elif String(weapon.get("visual", "")) == "tesla_layers":
		# The armory icon is cropped for legibility, but the ghost uses the exact
		# full-frame ground anchor and scale of the live Tesla.
		_base.texture = Assets.tex(Weapons.art_key(wid))
		_base.offset = -TeslaVisual.GROUND
		_base.scale = Vector2.ONE * TeslaVisual.IMAGE_SCALE
		_base.position = TeslaVisual.FOOTPRINT_OFFSET
		_base.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	elif String(weapon.get("visual", "")) in ["machinegun_layers", "artillery_layers"]:
		_base.offset = -MachinegunVisual.GROUND
		_base.scale = Vector2.ONE * MachinegunVisual.IMAGE_SCALE
		_base.position = MachinegunVisual.FOOTPRINT_OFFSET
		_base.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR
	else:
		_base.texture_filter = CanvasItem.TEXTURE_FILTER_PARENT_NODE
		_base.offset = -Vector2(IsoSheet.FRAME * 0.5, IsoSheet.TOWER_GROUND_Y)
		_base.scale = Vector2.ONE * Tower.ISO_SCALE
		_base.position = Vector2.ZERO
	_update_sprite_modulate()
	queue_redraw()

func update_preview(pos: Vector2, buildable: bool, range_value: float, problem := "") -> void:
	_ensure_sprites()
	position = pos
	ok = buildable
	reach = range_value
	issue = problem
	visible = not weapon.is_empty()
	_update_sprite_modulate()
	queue_redraw()

func _update_sprite_modulate() -> void:
	var mod := Color(1.0, 1.0, 1.0, 0.96) if ok else Color(1.0, 0.62, 0.62, 0.94)
	if _base != null:
		_base.modulate = mod

func _draw() -> void:
	if weapon.is_empty():
		return
	var green := Color(0.20, 0.96, 0.45, 0.95)
	var red := Color(1.0, 0.22, 0.22, 0.96)
	var tint := green if ok else red

	# دایرهٔ برد برجک (سبز در محدودهٔ مجاز، قرمز در محدودهٔ جاده یا برج دیگر)
	draw_circle(Vector2.ZERO, reach, Color(tint.r, tint.g, tint.b, 0.16 if ok else 0.22))
	draw_arc(Vector2.ZERO, reach, 0.0, TAU, 72, Color(tint.r, tint.g, tint.b, 0.90), 2.8, true)

	# همان مربع دو خانه در دو خانه که قانون ساخت بررسی می‌کند.
	var half: float = Balance.TOWER_SIZE * 0.5
	var footprint := Rect2(-half, -half, Balance.TOWER_SIZE, Balance.TOWER_SIZE)
	draw_rect(footprint, Color(tint.r, tint.g, tint.b, 0.28), true)
	draw_rect(footprint, tint, false, 2.2, true)

	draw_circle(Vector2.ZERO, 3.5, tint)
