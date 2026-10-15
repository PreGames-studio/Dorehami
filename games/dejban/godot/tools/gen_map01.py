#!/usr/bin/env python3
"""Rebuild map 1 artwork from its terrain source and the orthogonal game route.

Keep PATH in sync with Maps.LIST[0].waypoints (grid units: x/64, y/72).
Only writes assets/sprites/maps/map_01.jpg; no other maps are modified.
"""
from pathlib import Path
import random
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools/art_source/map_01_terrain.png"
OUTPUT = ROOT / "assets/sprites/maps/map_01.jpg"
W, H = 1280, 720
PATH = [(0, 540), (224, 540), (224, 396), (608, 396), (608, 540),
        (960, 540), (960, 324), (704, 324), (704, 180), (1168, 180), (1168, 72)]
# Concrete build pads are decorative; actual build rules use the path in GameMap.
PADS = [(105, 440), (370, 535), (92, 320), (365, 300), (495, 617),
        (470, 490), (795, 440), (814, 638), (1100, 610), (1110, 420),
        (850, 410), (550, 270), (810, 75), (1015, 82), (1230, 370),
        (1180, 500), (390, 150), (150, 145)]


def road_rect(a, b, radius):
    x1, y1 = a
    x2, y2 = b
    assert x1 == x2 or y1 == y2, "Every road segment must be axis-aligned"
    return (min(x1, x2) - radius, min(y1, y2) - radius,
            max(x1, x2) + radius, max(y1, y2) + radius)


def road_layer(radius, color):
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    for a, b in zip(PATH, PATH[1:]):
        draw.rectangle(road_rect(a, b, radius), fill=color)
    return layer


source = Image.open(SOURCE).convert("RGB")
# A uniform source allows resizing without moving any baked-in road or buildings.
base = source.resize((W, H), Image.Resampling.LANCZOS).convert("RGBA")
shadow = road_layer(58, (0, 0, 0, 125)).filter(ImageFilter.GaussianBlur(11))
base.alpha_composite(shadow)
for radius, color in [
    (52, (101, 93, 72, 255)),  # hard-edged dirt shoulder
    (46, (171, 155, 122, 255)),  # crushed gravel
    (40, (29, 32, 32, 255)),  # asphalt edge
    (37, (58, 61, 59, 255)),  # asphalt surface
]:
    base.alpha_composite(road_layer(radius, color))

# Subtle asphalt grain remains inside the crisp, square-edged road mask.
rng = random.Random(101)
grain = Image.new("RGBA", (W, H), (0, 0, 0, 0))
g = ImageDraw.Draw(grain)
for _ in range(48000):
    x = rng.randrange(W)
    y = rng.randrange(H)
    v = rng.choice([15, 90, 105])
    g.point((x, y), fill=(v, v, v, rng.randint(8, 28)))
mask = road_layer(36, (255, 255, 255, 255)).getchannel("A")
grain.putalpha(Image.composite(grain.getchannel("A"), Image.new("L", (W, H)), mask))
base.alpha_composite(grain)

draw = ImageDraw.Draw(base, "RGBA")
# Short square dashes: stop before corners, do not round the 90-degree bends.
for a, b in zip(PATH, PATH[1:]):
    x1, y1 = a
    x2, y2 = b
    if x1 == x2:
        lo, hi = sorted((y1, y2))
        for y in range(lo + 54, hi - 35, 42):
            draw.rectangle((x1 - 1, y, x1 + 1, min(y + 17, hi - 34)), fill=(231, 223, 193, 175))
    else:
        lo, hi = sorted((x1, x2))
        for x in range(lo + 54, hi - 35, 42):
            draw.rectangle((x, y1 - 1, min(x + 17, hi - 34), y1 + 1), fill=(231, 223, 193, 175))

# Raised tactical concrete pads and their shadows, safely outside the road.
for x, y in PADS:
    draw.rectangle((x - 28 + 4, y - 26 + 6, x + 28 + 4, y + 26 + 6), fill=(18, 22, 18, 95))
    draw.rectangle((x - 28, y - 26, x + 28, y + 26), fill=(59, 62, 57, 255))
    draw.rectangle((x - 25, y - 23, x + 25, y + 23), fill=(148, 150, 139, 255))
    draw.rectangle((x - 21, y - 19, x + 21, y + 19), fill=(176, 175, 157, 255))
    for dx in (-19, 19):
        for dy in (-17, 17):
            draw.rectangle((x + dx - 1, y + dy - 1, x + dx + 1, y + dy + 1), fill=(92, 93, 81, 255))

base.convert("RGB").save(OUTPUT, quality=91, optimize=True, subsampling=0)
print(OUTPUT)
