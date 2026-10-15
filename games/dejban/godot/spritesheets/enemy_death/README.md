# Enemy Death — Top-Down Death Sprite Sheets

One-shot **death** animation for the eight enemy units of *Dezhban*.
Every sheet is a horizontal strip of **six frames** (64×64 px, healer 64×72) with a
fully transparent background, drawn as clean, hard-edged pixel art — no motion
blur, no anti-aliasing. The canvas, the four-way rig, the colour ramps, the
outline / shadow pass and the character designs are the very same code as the
**run** pack (`../enemy_run/`) and the **military soldier** pack
(`../military_soldier/`), so all three sit together seamlessly.

The six frames tell one story: `hit → recoil → knee → fall → ground impact →
settle`. The weapon is let go in mid-fall and ends up lying on the ground next
to the body; a dust ring puffs out on impact and the last frame is held.

```
spritesheets/enemy_death/
├── <unit>_death_<dir>.png      horizontal sheet, 6 frames, 64×64 transparent
├── <unit>_death_<dir>.json     TexturePacker JSON Hash (frames + pivot + tag)
├── death_pack.json             pack index: frame size, ground line, fps, files
├── index.html                  HTML player (speed / zoom / background)
└── preview/                    contact sheet + animated GIF
```

## Units

| unit | label | frame | notes |
|------|-------|-------|-------|
| `grunt` | سرباز پیاده | 64×64 | — |
| `runner` | دونده | 64×64 | — |
| `armored` | زره‌پوش | 64×64 | خودرو از کار می‌افتد (از هم نمی‌پاشد) |
| `shield` | سپردار | 64×64 | — |
| `archer` | کمانگیر | 64×64 | شنلِ گیلی روی زمین پهن می‌شود |
| `healer` | درمانگر | 64×72 | فریم 64x72 — ردای بلندتر |
| `commander` | فرمانده | 64×64 | پرچم روی زمین کاشته می‌شود |
| `brute` | غول | 64×64 | — |

## Playing it

**Play it once — do not loop it.** `death_pack.json` says `"framesPerSheet": 6`,
`"fps": 8`, `"loop": false`. Run frames `0..5` a single time and then hold the
last frame (the corpse). Every sheet JSON also carries a TexturePacker
`frameTags` entry with `"repeat": "once"` for the same reason.

## Alignment

`groundY` in `death_pack.json` (and the `pivot.y = groundY / frameH` in every
sheet JSON) is the ground-contact line: the same distance from the frame bottom
as the **run** sheet of that unit, so swapping run → death does not make the
enemy jump. The ground line sits `PAD_BOTTOM = 5 px` above the lowest drawn
pixel (impact dust / shadow). Frame origin is the strip's left edge;
`frame w` and `frame h` are identical all the way through a sheet.

## Same look as the game

The infantry keep the green fatigues, olive helmet, rifle and brown boots of the
walk / run sheets, the vehicles and creatures keep their original designs. The
one deliberate liberty is the armoured vehicle — a tank does not topple like a
man, so it stops, sinks on its suspension, droops its barrel, stops its treads
and starts smoking instead of falling apart.

These sheets are also shipped inside the game: a copy of every file lives in
`assets/sprites/units/death/` (and the run sheets in `assets/sprites/units/run/`),
`src/data/UnitSheets.gd` holds their ground lines and `src/game/Enemy.gd` plays
the six frames once and holds the last one. To refresh the copies after a
redraw, run the generator and copy the sheets over again.

---
ساخته‌شده با `python3 tools/gen_enemy_sprites.py --anim death`
(دوباره‌سازی: `python3 tools/gen_enemy_sprites.py --anim both`؛ بررسی: `--check`)
