## این فایل با tools/gen_assets_manifest.py ساخته می‌شود؛ دستی ویرایش نکنید.
##
## MANIFEST کلید منطقی هر دارایی («پوشه/نام») را به مسیر واقعی فایل نگاشت می‌کند.
## FALLBACKS هم پشتوانهٔ دسته‌هاست: اگر کلیدی در ساخت نبود، نزدیک‌ترین هنر همان
## خانواده استفاده می‌شود تا میدان نبرد هرگز جای خالی یا رنگ سرخ نگیرد؛ تست
## بدون رابط هم کمبود کلید را با صدا اعلام می‌کند.
class_name Assets
extends RefCounted

const MANIFEST := {
	"bullets/arrow": "res://assets/sprites/bullets/arrow.png",
	"bullets/bolt": "res://assets/sprites/bullets/bolt.png",
	"bullets/dart": "res://assets/sprites/bullets/dart.png",
	"bullets/fire": "res://assets/sprites/bullets/fire.png",
	"bullets/frost": "res://assets/sprites/bullets/frost.png",
	"bullets/poison": "res://assets/sprites/bullets/poison.png",
	"bullets/scythe": "res://assets/sprites/bullets/scythe.png",
	"bullets/shell": "res://assets/sprites/bullets/shell.png",
	"bullets/spark": "res://assets/sprites/bullets/spark.png",
	"bullets/stone": "res://assets/sprites/bullets/stone.png",
	"fx/coin": "res://assets/sprites/fx/coin.png",
	"fx/explosion_1": "res://assets/sprites/fx/explosion_1.png",
	"fx/explosion_2": "res://assets/sprites/fx/explosion_2.png",
	"fx/explosion_3": "res://assets/sprites/fx/explosion_3.png",
	"fx/explosion_4": "res://assets/sprites/fx/explosion_4.png",
	"fx/explosion_5": "res://assets/sprites/fx/explosion_5.png",
	"fx/explosion_6": "res://assets/sprites/fx/explosion_6.png",
	"fx/explosion_large": "res://assets/sprites/fx/explosion_large.png",
	"fx/explosion_medium": "res://assets/sprites/fx/explosion_medium.png",
	"fx/explosion_small": "res://assets/sprites/fx/explosion_small.png",
	"fx/fire_flame": "res://assets/sprites/fx/fire_flame.png",
	"fx/flame_1": "res://assets/sprites/fx/flame_1.png",
	"fx/flame_2": "res://assets/sprites/fx/flame_2.png",
	"fx/flame_3": "res://assets/sprites/fx/flame_3.png",
	"fx/flame_4": "res://assets/sprites/fx/flame_4.png",
	"fx/heal": "res://assets/sprites/fx/heal.png",
	"fx/laser_beam": "res://assets/sprites/fx/laser_beam.png",
	"fx/muzzle_flash": "res://assets/sprites/fx/muzzle_flash.png",
	"fx/ring": "res://assets/sprites/fx/ring.png",
	"fx/select": "res://assets/sprites/fx/select.png",
	"fx/smoke_1": "res://assets/sprites/fx/smoke_1.png",
	"fx/smoke_2": "res://assets/sprites/fx/smoke_2.png",
	"fx/smoke_3": "res://assets/sprites/fx/smoke_3.png",
	"fx/smoke_cloud": "res://assets/sprites/fx/smoke_cloud.png",
	"fx/spark": "res://assets/sprites/fx/spark.png",
	"fx/star": "res://assets/sprites/fx/star.png",
	"fx/tesla_bolt": "res://assets/sprites/fx/tesla_bolt.png",
	"ground/ash": "res://assets/sprites/ground/ash.png",
	"ground/dirt": "res://assets/sprites/ground/dirt.png",
	"ground/grass": "res://assets/sprites/ground/grass.png",
	"ground/grass_dark": "res://assets/sprites/ground/grass_dark.png",
	"ground/rock": "res://assets/sprites/ground/rock.png",
	"ground/sand": "res://assets/sprites/ground/sand.png",
	"ground/snow": "res://assets/sprites/ground/snow.png",
	"maps/map_01": "res://assets/sprites/maps/map_01.jpg",
	"maps/map_02": "res://assets/sprites/maps/map_02.jpg",
	"maps/map_03": "res://assets/sprites/maps/map_03.jpg",
	"maps/map_04": "res://assets/sprites/maps/map_04.jpg",
	"maps/map_05": "res://assets/sprites/maps/map_05.jpg",
	"maps/map_06": "res://assets/sprites/maps/map_06.jpg",
	"maps/map_07": "res://assets/sprites/maps/map_07.jpg",
	"maps/map_08": "res://assets/sprites/maps/map_08.jpg",
	"maps/map_09": "res://assets/sprites/maps/map_09.jpg",
	"maps/map_10": "res://assets/sprites/maps/map_10.jpg",
	"maps/map_11": "res://assets/sprites/maps/map_11.jpg",
	"maps/map_12": "res://assets/sprites/maps/map_12.jpg",
	"icons/check": "res://assets/sprites/icons/check.png",
	"icons/close": "res://assets/sprites/icons/close.png",
	"icons/coin": "res://assets/sprites/icons/coin.png",
	"icons/flag": "res://assets/sprites/icons/flag.png",
	"icons/heart": "res://assets/sprites/icons/heart.png",
	"icons/lock": "res://assets/sprites/icons/lock.png",
	"icons/pause": "res://assets/sprites/icons/pause.png",
	"icons/skull": "res://assets/sprites/icons/skull.png",
	"icons/speed": "res://assets/sprites/icons/speed.png",
	"icons/star_small": "res://assets/sprites/icons/star_small.png",
	"icons/trophy": "res://assets/sprites/icons/trophy.png",
	"icons/wave": "res://assets/sprites/icons/wave.png",
	"props/barrel": "res://assets/sprites/props/barrel.png",
	"props/bush": "res://assets/sprites/props/bush.png",
	"props/cactus": "res://assets/sprites/props/cactus.png",
	"props/crate": "res://assets/sprites/props/crate.png",
	"props/flower_1": "res://assets/sprites/props/flower_1.png",
	"props/flower_2": "res://assets/sprites/props/flower_2.png",
	"props/grass_tuft": "res://assets/sprites/props/grass_tuft.png",
	"props/pond": "res://assets/sprites/props/pond.png",
	"props/rock_big": "res://assets/sprites/props/rock_big.png",
	"props/rock_small": "res://assets/sprites/props/rock_small.png",
	"props/ruins": "res://assets/sprites/props/ruins.png",
	"props/statue": "res://assets/sprites/props/statue.png",
	"props/tent": "res://assets/sprites/props/tent.png",
	"props/torch": "res://assets/sprites/props/torch.png",
	"props/tree_palm": "res://assets/sprites/props/tree_palm.png",
	"props/tree_pine": "res://assets/sprites/props/tree_pine.png",
	"props/tree_round": "res://assets/sprites/props/tree_round.png",
	"structures/gate": "res://assets/sprites/structures/gate.png",
	"structures/keep": "res://assets/sprites/structures/keep.png",
	"towers/base": "res://assets/sprites/towers/base.png",
	"towers/muzzle_1": "res://assets/sprites/towers/muzzle_1.png",
	"towers/muzzle_2": "res://assets/sprites/towers/muzzle_2.png",
	"towers_iso/atlas_towers_iso": "res://assets/sprites/towers_iso/atlas_towers_iso.png",
	"towers_iso/cannon_aim": "res://assets/sprites/towers_iso/cannon_aim.png",
	"towers_iso/cannon_fire": "res://assets/sprites/towers_iso/cannon_fire.png",
	"towers_iso/cannon_idle": "res://assets/sprites/towers_iso/cannon_idle.png",
	"towers_iso/laser_aim": "res://assets/sprites/towers_iso/laser_aim.png",
	"towers_iso/laser_fire": "res://assets/sprites/towers_iso/laser_fire.png",
	"towers_iso/laser_idle": "res://assets/sprites/towers_iso/laser_idle.png",
	"towers_iso/machinegun_aim": "res://assets/sprites/towers_iso/machinegun_aim.png",
	"towers_iso/machinegun_fire": "res://assets/sprites/towers_iso/machinegun_fire.png",
	"towers_iso/machinegun_idle": "res://assets/sprites/towers_iso/machinegun_idle.png",
	"towers_iso/rocket_aim": "res://assets/sprites/towers_iso/rocket_aim.png",
	"towers_iso/rocket_fire": "res://assets/sprites/towers_iso/rocket_fire.png",
	"towers_iso/rocket_idle": "res://assets/sprites/towers_iso/rocket_idle.png",
	"ui/armory_background": "res://assets/sprites/ui/armory_background.png",
	"ui/card_prestige": "res://assets/sprites/ui/card_prestige.png",
	"ui/card_weapon_base": "res://assets/sprites/ui/card_weapon_base.png",
	"ui/defeat_background": "res://assets/sprites/ui/defeat_background.png",
	"ui/main_menu_military": "res://assets/sprites/ui/main_menu_military.png",
	"ui/map_select_background": "res://assets/sprites/ui/map_select_background.png",
	"ui/sky": "res://assets/sprites/ui/sky.png",
	"ui/victory_background": "res://assets/sprites/ui/victory_background.png",
	"units/commando": "res://assets/sprites/units/commando.png",
	"units/commando_walk": "res://assets/sprites/units/commando_walk.png",
	"units/soldier": "res://assets/sprites/units/soldier.png",
	"units/soldier_walk": "res://assets/sprites/units/soldier_walk.png",
	"units/death/commando_back": "res://assets/sprites/units/death/commando_back.png",
	"units/death/commando_front": "res://assets/sprites/units/death/commando_front.png",
	"units/death/commando_left": "res://assets/sprites/units/death/commando_left.png",
	"units/death/commando_right": "res://assets/sprites/units/death/commando_right.png",
	"units/death/soldier_back": "res://assets/sprites/units/death/soldier_back.png",
	"units/death/soldier_front": "res://assets/sprites/units/death/soldier_front.png",
	"units/death/soldier_left": "res://assets/sprites/units/death/soldier_left.png",
	"units/death/soldier_right": "res://assets/sprites/units/death/soldier_right.png",
	"units_iso_tank/atlas_tank_iso": "res://assets/sprites/units/iso_tank/atlas_tank_iso.png",
	"units_iso_tank/tank_attack": "res://assets/sprites/units/iso_tank/tank_attack.png",
	"units_iso_tank/tank_idle": "res://assets/sprites/units/iso_tank/tank_idle.png",
	"units_iso_tank/tank_move": "res://assets/sprites/units/iso_tank/tank_move.png",
	"units/run/commando_back": "res://assets/sprites/units/run/commando_back.png",
	"units/run/commando_front": "res://assets/sprites/units/run/commando_front.png",
	"units/run/commando_left": "res://assets/sprites/units/run/commando_left.png",
	"units/run/commando_right": "res://assets/sprites/units/run/commando_right.png",
	"units/run/soldier_back": "res://assets/sprites/units/run/soldier_back.png",
	"units/run/soldier_front": "res://assets/sprites/units/run/soldier_front.png",
	"units/run/soldier_left": "res://assets/sprites/units/run/soldier_left.png",
	"units/run/soldier_right": "res://assets/sprites/units/run/soldier_right.png",
	"units_iso_tank_armored/atlas_tank_armored_iso": "res://assets/sprites/units/iso_tank_armored/atlas_tank_armored_iso.png",
	"units_iso_tank_armored/tank_armored_attack": "res://assets/sprites/units/iso_tank_armored/tank_armored_attack.png",
	"units_iso_tank_armored/tank_armored_idle": "res://assets/sprites/units/iso_tank_armored/tank_armored_idle.png",
	"units_iso_tank_armored/tank_armored_move": "res://assets/sprites/units/iso_tank_armored/tank_armored_move.png",
	"units_iso_tank_heavy/atlas_tank_heavy_iso": "res://assets/sprites/units/iso_tank_heavy/atlas_tank_heavy_iso.png",
	"units_iso_tank_heavy/tank_heavy_attack": "res://assets/sprites/units/iso_tank_heavy/tank_heavy_attack.png",
	"units_iso_tank_heavy/tank_heavy_idle": "res://assets/sprites/units/iso_tank_heavy/tank_heavy_idle.png",
	"units_iso_tank_heavy/tank_heavy_move": "res://assets/sprites/units/iso_tank_heavy/tank_heavy_move.png",
	"icons/app_512": "res://assets/icons/app_512.png",
	"icons/prestige_boost": "res://assets/icons/prestige_boost.png",
	"icon/app": "res://icon_512.png",
	"icon/foreground": "res://icon_foreground_432.png",
	"icon/background": "res://icon_background_432.png",
}

const FALLBACKS := {
	"ground": "ground/grass",
	"maps": "maps/map_01",
	"props": "props/rock_small",
	"units": "units/soldier",
	"structures": "structures/keep",
	"towers": "towers/base",
	"bullets": "bullets/stone",
	"fx": "fx/explosion_3",
	"icons": "icons/coin",
	"icon": "icon/app",
	"towers_iso": "towers_iso/machinegun_idle",
	"units_iso_tank": "units_iso_tank/tank_idle",
	"units_iso_tank_armored": "units_iso_tank_armored/tank_armored_idle",
	"units_iso_tank_heavy": "units_iso_tank_heavy/tank_heavy_idle",
}

const SOUND_MANIFEST := {
	"build": "res://assets/sounds/build.wav",
	"coin": "res://assets/sounds/coin.wav",
	"defeat": "res://assets/sounds/defeat.wav",
	"deny": "res://assets/sounds/deny.wav",
	"keep_hit": "res://assets/sounds/keep_hit.wav",
	"music_battle": "res://assets/sounds/music_battle.wav",
	"music_menu": "res://assets/sounds/music_menu.wav",
	"sell": "res://assets/sounds/sell.wav",
	"star": "res://assets/sounds/star.wav",
	"tower_destroyed": "res://assets/sounds/tower_destroyed.wav",
	"ui_back": "res://assets/sounds/ui_back.wav",
	"ui_click": "res://assets/sounds/ui_click.wav",
	"ui_hover": "res://assets/sounds/ui_hover.wav",
	"unlock": "res://assets/sounds/unlock.wav",
	"upgrade": "res://assets/sounds/upgrade.wav",
	"victory": "res://assets/sounds/victory.wav",
	"wave_clear": "res://assets/sounds/wave_clear.wav",
	"wave_start": "res://assets/sounds/wave_start.wav",
	"combat/aircraft-hit": "res://assets/sounds/combat/aircraft-hit.ogg",
	"combat/cannon-wreck": "res://assets/sounds/combat/cannon-wreck.ogg",
	"combat/field-gun-fire": "res://assets/sounds/combat/field-gun-fire.ogg",
	"combat/rifle-bolt": "res://assets/sounds/combat/rifle-bolt.ogg",
	"combat/rifle-semi": "res://assets/sounds/combat/rifle-semi.ogg",
	"combat/soldier-defeat": "res://assets/sounds/combat/soldier-defeat.ogg",
	"combat/tank-gun-fire": "res://assets/sounds/combat/tank-gun-fire.ogg",
}

static var _cache: Dictionary = {}


## بافت یک کلید را برمی‌گرداند (با حافظهٔ نهان) و در صورت نبودِ کلید هشدار می‌دهد.
static func tex(key: String) -> Texture2D:
	if _cache.has(key):
		return _cache[key]
	var path: String = MANIFEST.get(key, "")
	if path.is_empty():
		var fallback: String = FALLBACKS.get(key.get_slice("/", 0), "")
		if fallback.is_empty() or not MANIFEST.has(fallback):
			push_error("Assets: کلید ناشناخته «%s»" % key)
			return null
		push_warning("Assets: کلید «%s» موجود نبود؛ «%s» استفاده شد" % [key, fallback])
		path = MANIFEST[fallback]
	var res: Texture2D = load(path)
	_cache[key] = res
	return res


static func sound(key: String) -> AudioStream:
	var path: String = SOUND_MANIFEST.get(key, "")
	if path.is_empty():
		push_error("Assets: صدای ناشناخته «%s»" % key)
		return null
	return load(path)


static func has_key(key: String) -> bool:
	return MANIFEST.has(key)


static func keys() -> Array:
	return MANIFEST.keys()


static func sound_keys() -> Array:
	return SOUND_MANIFEST.keys()
