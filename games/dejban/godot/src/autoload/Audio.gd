## مدیر صدا: استخر پخش‌کننده برای جلوه‌ها و یک پخش‌کنندهٔ جدا برای موسیقی.
## نام صداها همان کلیدهای فایل‌های assets/sounds هستند (بدون پسوند).
extends Node

const POOL_SIZE := 16
const COMBAT_POOL_SIZE := 4
const WEAPON_SOUND_MIN_INTERVAL_MS := 80

## هر دسته از سلاح‌ها بین چند ضبط CC0 انتخاب می‌کند تا شلیک‌ها یکسان و خشک تکرار نشوند.
const WEAPON_SOUND_VARIANTS := {
	"shot_arrow": ["combat/rifle-semi", "combat/rifle-bolt"],
	"shot_bolt": ["combat/rifle-bolt", "combat/rifle-semi"],
	"shot_stone": ["combat/field-gun-fire", "combat/tank-gun-fire"],
	"shot_fire": ["combat/field-gun-fire", "combat/tank-gun-fire"],
	"shot_frost": ["combat/rifle-semi", "combat/rifle-bolt"],
	"shot_spark": ["combat/rifle-bolt", "combat/rifle-semi"],
	"shot_poison": ["combat/rifle-semi", "combat/rifle-bolt"],
	"shot_heavy": ["combat/tank-gun-fire", "combat/field-gun-fire"],
}

var _players: Array[AudioStreamPlayer] = []
var _combat_players: Array[AudioStreamPlayer] = []
var _music: AudioStreamPlayer
var _streams: Dictionary = {}
var _current_music := ""
var _next := 0
var _combat_next := 0
var _last_weapon_sound_msec := -1000000
var _last_combat_sound_msec: Dictionary = {}

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	for i in POOL_SIZE:
		var player := AudioStreamPlayer.new()
		player.bus = "SFX"
		add_child(player)
		_players.append(player)
	for i in COMBAT_POOL_SIZE:
		var player := AudioStreamPlayer.new()
		player.bus = "SFX"
		add_child(player)
		_combat_players.append(player)
	_music = AudioStreamPlayer.new()
	_music.bus = "Music"
	_music.process_mode = Node.PROCESS_MODE_ALWAYS
	_music.finished.connect(_on_music_finished)
	add_child(_music)

func _stream(key: String) -> AudioStream:
	if _streams.has(key):
		return _streams[key]
	var stream := Assets.sound(key)
	if stream != null:
		var wav := stream as AudioStreamWAV
		if wav != null:
			# لوپ روان برای موسیقی؛ جلوه‌ها یک‌بار پخش می‌شوند.
			if key.begins_with("music_"):
				wav.loop_mode = AudioStreamWAV.LOOP_FORWARD
				wav.loop_begin = 0
				wav.loop_end = int(wav.data.size() / 2)
		_streams[key] = stream
	return stream

func play(key: String, pitch := 1.0, volume_db := 0.0) -> void:
	var stream := _stream(key)
	if stream == null:
		return
	var player := _players[_next]
	_next = (_next + 1) % _players.size()
	player.stream = stream
	player.pitch_scale = clampf(pitch, 0.4, 2.4)
	player.volume_db = volume_db
	player.play()

## چند صدا با کمی تغییر گام تا پشت‌سرهم تکراری به گوش نرسد.
func play_varied(key: String, spread := 0.08, volume_db := 0.0) -> void:
	play(key, 1.0 + randf_range(-spread, spread), volume_db)

## پخش شلیک از استخر جدا و محدود؛ نرخ و تعداد هم‌زمانی جلوی روی‌هم‌افتادن رگبارها را می‌گیرد.
func play_weapon(key: String, spread := 0.04, volume_db := -10.0) -> void:
	var now := Time.get_ticks_msec()
	if now - _last_weapon_sound_msec < WEAPON_SOUND_MIN_INTERVAL_MS:
		return
	var variants: Array = WEAPON_SOUND_VARIANTS.get(key, [])
	if variants.is_empty():
		push_warning("Audio: دستهٔ صدای سلاح ناشناخته «%s»" % key)
		return
	var actual_key := String(variants[randi_range(0, variants.size() - 1)])
	if _play_combat(actual_key, 1.0 + randf_range(-spread, spread), volume_db):
		_last_weapon_sound_msec = now

## رویدادهای دشمن/برخورد را با محدودکنندهٔ جداگانه پخش می‌کند.
func play_combat_limited(key: String, cooldown_ms: int, spread := 0.05, volume_db := -12.0) -> void:
	var now := Time.get_ticks_msec()
	var last := int(_last_combat_sound_msec.get(key, -1000000))
	if now - last < maxi(cooldown_ms, 0):
		return
	if _play_combat(key, 1.0 + randf_range(-spread, spread), volume_db):
		_last_combat_sound_msec[key] = now

func _play_combat(key: String, pitch: float, volume_db: float) -> bool:
	var stream := _stream(key)
	if stream == null or _combat_players.is_empty():
		return false
	for offset in _combat_players.size():
		var index := (_combat_next + offset) % _combat_players.size()
		var player: AudioStreamPlayer = _combat_players[index]
		if player.playing:
			continue
		_combat_next = (index + 1) % _combat_players.size()
		player.stream = stream
		player.pitch_scale = clampf(pitch, 0.4, 2.4)
		player.volume_db = volume_db
		player.play()
		return true
	return false

func play_music(key: String) -> void:
	if key == _current_music and _music.playing:
		return
	var stream := _stream(key)
	if stream == null:
		return
	_current_music = key
	_music.stream = stream
	_music.play()

func stop_music() -> void:
	_current_music = ""
	_music.stop()

func _on_music_finished() -> void:
	# پشتوانهٔ لوپ: اگر برای هر دلیلی لوپ نشد، از آغاز پخش می‌شود.
	if not _current_music.is_empty():
		_music.play()

func apply_settings(music_volume: float, sfx_volume: float) -> void:
	_set_bus("Music", music_volume)
	_set_bus("SFX", sfx_volume)

func _set_bus(bus_name: String, volume: float) -> void:
	var index := AudioServer.get_bus_index(bus_name)
	if index < 0:
		return
	if volume <= 0.001:
		AudioServer.set_bus_mute(index, true)
	else:
		AudioServer.set_bus_mute(index, false)
		AudioServer.set_bus_volume_db(index, linear_to_db(clampf(volume, 0.001, 1.0)))

func ui_click() -> void:
	play_varied("ui_click", 0.05)

func ui_back() -> void:
	play_varied("ui_back", 0.05)
