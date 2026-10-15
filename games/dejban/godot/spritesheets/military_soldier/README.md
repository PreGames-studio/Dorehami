# Military Soldier — Top-Down Sprite Sheets

Ready-to-import animation pack for a tower-defense enemy unit. Every sheet is a
horizontal strip of **64×64 px** frames with a fully transparent background,
drawn as clean, hard-edged pixel art (no motion blur, no anti-aliasing).

Unit: **military soldier** — green fatigues, olive helmet, black rifle, brown
boots. Same character design in every frame and every direction.

```
spritesheets/military_soldier/
├── soldier_<anim>_<dir>.png      horizontal sprite sheet (8 frames / 6 for death)
├── soldier_<anim>_<dir>.json     TexturePacker JSON Hash (frames + animations)
├── soldier_all_atlas.png/.json   single atlas with all frames + JSON Hash
├── soldier_pack.json             pack index: fps, loop flags, directions, sizes
├── index.html                    HTML player (speed / zoom / background / all 20 sheets)
└── preview/                      contact sheet + animated GIF
```

## Animations

| Animation | Frames | fps | Loop | Description |
|-----------|--------|-----|------|-------------|
| `idle`    | 8 | 6  | yes | standing guard, breathing, shoulders rise and fall |
| `walk`    | 8 | 10 | yes | patrolling forward, legs cycle, arms counter-swing |
| `run`     | 8 | 14 | yes | charging, forward lean, wide stride, arms pumping |
| `attack`  | 8 | 12 | yes | raise rifle → aim → fire (muzzle flash, smoke, ejected casing) |
| `death`   | 6 | 8  | no  | hit → stagger → knees → fall → sprawl → still on the ground |

## Directions

`front` (moving down) · `back` (moving up) · `left` (moving left) · `right` (moving right)

Each direction is a separate sheet: `soldier_walk_left.png` contains 8 frames of
the soldier walking leftward, etc. The rig is projected from one single character
model, so all four directions share exactly the same proportions, colours and
equipment; the helmet brim, chest rig, backpack and boots are only visible on the
faces where they belong.

## Frame rules

* **Looping sheets** (`idle`, `walk`, `run`, `attack`): the last frame is a
  **byte-identical copy of frame 1**, so `frame % frameCount` loops seamlessly.
  In practice you can set `hframes = 8` and play frames 0‥7 with `frame` wrapping
  automatically — frame 8 never displays and the cycle is still perfect.
* **Death** does not loop: play 0‥5 once and hold the last frame.
* Every frame keeps at least 2 px of transparent padding, so the sheets tile
  without bleeding into neighbours in an atlas.
* The frame **pivot is the ground contact point**: `x = 32, y = 50`
  (`0.5, 0.78125` in normalised JSON coordinates). Use the frame centre if you
  simply want the sprite box to be centred on the tile.

## TexturePacker JSON Hash

```json
{
  "frames": {
    "soldier_walk_left_00": {
      "frame":            { "x": 0, "y": 0, "w": 64, "h": 64 },
      "rotated":          false,
      "trimmed":          false,
      "spriteSourceSize": { "x": 0, "y": 0, "w": 64, "h": 64 },
      "sourceSize":       { "w": 64, "h": 64 },
      "pivot":            { "x": 0.5, "y": 0.7813 }
    }
  },
  "animations": { "soldier_walk_left": ["soldier_walk_left_00", "…"] },
  "meta": { "image": "soldier_walk_left.png", "format": "RGBA8888", "size": { "w": 512, "h": 64 } }
}
```

## Import notes

**Construct 3** — import the PNG as a sprite, set *Animation frames = 8*, and
enable **Image → Sprite sheet → Treat as sprite sheet** (or simply import each
frame after slicing in the *Sprite sheet* editor). Directions map to
`Animation: idle / walk / run / attack / death`; mirror `left` ↔ `right` with the
*Mirror* action if you want to halve the memory cost. The pack JSON can be used by
TexturePacker-aware plugins, or the frames can be named/tagged from `soldier_pack.json`.

**Godot 4** — the sheets are already in the engine's native form:

```gdscript
var tex := load("res://spritesheets/military_soldier/soldier_walk_left.png")
sprite.texture = tex
sprite.hframes = 8        # 6 for death
sprite.frame = int(anim_time * fps) % 8
```

The folder ships a `.gdignore` file so it stays out of the game's import
pipeline; delete it if you want Godot to import the sheets into the project
(and re-run `python3 tools/gen_assets_manifest.py` afterwards).

## Regenerating

```bash
python3 tools/gen_soldier_sprites.py           # rebuild every sheet, JSON and preview
python3 tools/gen_soldier_sprites.py --check   # validate the existing pack
python3 tools/gen_soldier_sprites.py --debug walk front 3 --zoom 8   # inspect one frame
```

The generator is data-driven: the character is modelled once in local body
coordinates (forward / side / height) and a projection rig maps it to the screen
for each direction, pose and animation frame, so consistent design across all
152 frames is guaranteed by construction.

---

## خلاصهٔ فارسی

بستهٔ آمادهٔ اسپرایت «سرباز نظامی» برای دشمنان بازی دفاع از قلعه: پنج انیمیشن
(ایستاده، راه رفتن، دویدن، شلیک، مرگ) در چهار جهت، هر فریم ۶۴×۶۴ پیکسل با
پس‌زمینهٔ شفاف و طرح پیکسلی تمیز. هر جهت یک شیت افقی جداگانه دارد و کنار هر شیت
فایل JSON قالب TexturePacker Hash ساخته شده است. در انیمیشن‌های لوپ، فریم آخر
عیناً همان فریم اول است تا چرخه بدون پرش تکرار شود؛ انیمیشن مرگ ۶ فریم است و لوپ
نمی‌شود. نقطهٔ اتکای هر فریم، محل تماس کف پا با زمین (۳۲ و ۵۰) است.

برای بازسازی خروجی‌ها: `python3 tools/gen_soldier_sprites.py`
برای دیدن پیش‌نمایش متحرک: `spritesheets/military_soldier/index.html`
