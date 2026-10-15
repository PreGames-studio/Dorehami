# Enemy Run — Top-Down Run Sprite Sheets

Ready-to-import **run** animation pack for the eight enemy units of *Dezhban*.
Every sheet is a horizontal strip of **eight 64×64 px frames** with a fully
transparent background, drawn as clean, hard-edged pixel art (no motion blur, no
anti-aliasing). The canvas, the four-way rig, the colour ramps and the outline /
shadow pass are the very same code as the **military soldier** pack
(`../military_soldier/`), so the two packs sit together seamlessly.

The infantry units keep their original look — green fatigues, olive helmet,
rifle, brown boots — and the vehicles / creatures keep their original designs,
so the existing walk sheets in `assets/sprites/units/` stay as they are.
A copy of every sheet here is also shipped inside the game
(`assets/sprites/units/run/`); `src/game/Enemy.gd` uses it for the fast units
(the runner) and walks everybody else with the older walk sheet.

```
spritesheets/enemy_run/
├── <unit>_run_<dir>.png       horizontal sheet, 8 unique frames, 64×64 (healer 64×66)
├── <unit>_run_<dir>.json      TexturePacker JSON Hash (frames + animation + pivot)
├── run_pack.json              pack index: frame size, ground line, fps, files
├── index.html                 HTML player (speed / zoom / background / all 32 sheets)
└── preview/                   contact sheet + animated GIF
```

## Units

| unit | label | in game | notes |
|------|-------|---------|-------|
| `grunt`     | سرباز پیاده | foot soldier     | **the military soldier** of `../military_soldier/` — green uniform, helmet, rifle |
| `runner`    | دونده       | fast attacker    | light gear, maroon helmet, neck scarf trailing behind |
| `armored`   | زره‌پوش     | armoured vehicle | rolling tracks, turret, engine smoke |
| `shield`    | سپردار     | shielded trooper | riot shield carried in front |
| `archer`    | کمانگیر     | long-range       | ghillie cloak with flapping hem, long rifle |
| `healer`    | درمانگر     | support caster   | hooded robe, hovering, glowing staff and aura |
| `commander` | فرمانده     | buff aura        | steel armour, sash, command flag streaming behind |
| `brute`     | غول         | boss             | hunched orc, spiked pauldrons, clawed fists |

`grunt` **is** the character of the previous sprite-sheet pack: that pack's
`soldier_run_*.png` and this pack's `grunt_run_*.png` come out of the same body
model. The difference is that here **all eight frames are unique** (the older
soldier sheets repeat frame 1 as frame 8 so that `frame % count` loops).

## Spec

| property | value |
|----------|-------|
| frame size | **64×64 px** (the healer is 64×**66** because of its under-foot aura) |
| layout | horizontal, **8 frames side by side** |
| frames | 8 unique per sheet — no duplicated loop frame |
| directions | `front` (moving down) · `back` (up) · `left` · `right` |
| fps | 14 |
| background | fully transparent (RGBA) |
| padding | ≥ 3 px on the sides, ≥ 2 px top, ground line 5 px above the bottom edge |
| pivot | frame centre; the ground-contact line is `groundY` in `run_pack.json` |
| art | hard-edged pixels, one outline pass + elliptical contact shadow |

## Run cycle

Each frame is generated from a parameterised cycle rather than hand-drawn, so the
eight poses are evenly spaced and read as one continuous stride:

* **legs** — a full stride (≈1.2 body widths), with the knee lifted at the front
  of the cycle and the foot planted at the back; the contact foot is drawn last
  so it overlaps correctly;
* **body** — vertical bob at *twice* the leg frequency, lateral sway at the leg
  frequency, plus a forward lean;
* **arms** — counter-swing to the legs (left arm forward with right leg), swinging
  wider than in the walk sheets;
* **gear** — cloth, cloaks, scarves, flags and tank tracks all animate off the
  same phase, so no two frames are byte-identical;
* **dust** — dithered ground puffs behind the feet for the running units.

## Generator

`tools/gen_enemy_sprites.py` (Pillow only):

```bash
python3 tools/gen_enemy_sprites.py                    # rebuild the whole pack (~13 s)
python3 tools/gen_enemy_sprites.py --only grunt       # rebuild one unit, keep the index
python3 tools/gen_enemy_sprites.py --check            # validate size, alpha, padding, uniqueness
python3 tools/gen_enemy_sprites.py --measure          # print the frame box of each unit
python3 tools/gen_enemy_sprites.py --debug brute left 3 --zoom 6   # single frame, zoomed
```

The generator is deterministic: the same command always produces byte-identical
PNGs, so `--check` diffs cleanly in CI.

## Using a sheet in Godot

```gdscript
var tex := load("res://spritesheets/enemy_run/grunt_run_front.png")
var sprite := Sprite2D.new()
sprite.texture = tex
sprite.hframes = 8          # 8 frames in one strip
sprite.frame = int(cycle * 8.0) % 8   # loop the cycle
# the ground contact line sits 5 px above the bottom of the 64 px frame
```

## Scope

Art and tooling only — no game code, scene, balance or asset-manifest changes.
`.gdignore` keeps the folder out of the Godot import pipeline; delete it and
re-run `tools/gen_assets_manifest.py` if you want the sheets inside the project.
