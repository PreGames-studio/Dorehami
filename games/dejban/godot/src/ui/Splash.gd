## صفحهٔ آغازین: لوگوی استودیو را پنج ثانیه نشان می‌دهد و سپس به منوی اصلی می‌رود.
## اگر فایل لوگو در assets/splash نباشد، مستقیم به منو می‌رود.
extends Control

const LOGO_PATH := "res://assets/splash/pregames_logo.jpg"
const MENU_PATH := "res://scenes/MainMenu.tscn"
const SHOW_SECONDS := 5.0

var _done := false

func _ready() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)

	var bg := ColorRect.new()
	bg.color = Color.BLACK
	bg.set_anchors_preset(Control.PRESET_FULL_RECT)
	bg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(bg)

	if not ResourceLoader.exists(LOGO_PATH):
		push_warning("لوگوی آغازین پیدا نشد: %s" % LOGO_PATH)
		call_deferred("_go_next")
		return

	var logo := TextureRect.new()
	logo.texture = load(LOGO_PATH)
	logo.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	logo.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
	logo.set_anchors_preset(Control.PRESET_FULL_RECT)
	logo.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(logo)

	get_tree().create_timer(SHOW_SECONDS).timeout.connect(_go_next)

func _go_next() -> void:
	if _done:
		return
	_done = true
	get_tree().change_scene_to_file(MENU_PATH)
