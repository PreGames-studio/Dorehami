#!/usr/bin/env python3
"""Build the five transparent, independently aimed Tesla tower images.

The supplied five-level picture has been carefully cut out into the transparent
``tesla_source/reference_cutout.png``: it is a derivative of the user's
reference, not a new license or an unrelated art pack. Checkerboard and level
captions were removed; the extreme level-five electrical glow was rebuilt so
no pieces of the reference canvas appear in the game.

Each tower uses exactly the same pedestal and ground anchor. The fixed central
body, projected upper head and core have separate 384px RGBA canvases. Each
head also has an 8×6 atlas of 192px projected directions with safe margins. The
runtime cross-fades adjacent views without rotating the base, leaves the sphere round, and
animates charging/chain lightning independently. Pillow is only a build-time
dependency. Reproducible with:

    python3 games/dejban/godot/tools/generate_tesla_art.py
    python3 games/dejban/godot/tools/generate_tesla_art.py --output /tmp/tesla
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = Path(__file__).resolve().parent / "tesla_source/reference_cutout.png"
DEST = ROOT / "assets/sprites/towers_tesla"
SIZE = (384, 384)
GROUND = (192, 345)
SOURCE_GROUND = 811
SOURCE_SCALE = 0.43
CELL = 192
DIRECTIONS = 48
COLUMNS = 8
ROWS = 6
# x bounds, original ground centre, head/body split, source globe (x,y,r).
LEVELS = [
    (0, 286, 151, 485, (151, 450, 35)),
    (286, 581, 438, 495, (437, 380, 38)),
    (581, 876, 734, 425, (733, 327, 39)),
    (876, 1200, 1047, 431, (1047, 280, 41)),
    (1200, 1536, 1370, 292, (1372, 215, 43)),
]


def blank() -> Image.Image:
    return Image.new("RGBA", SIZE, (0, 0, 0, 0))


def mapped_x(x: float, center: int) -> float:
    return GROUND[0] + (x - center) * SOURCE_SCALE


def mapped_y(y: float) -> float:
    return GROUND[1] + (y - SOURCE_GROUND) * SOURCE_SCALE


def place(source: Image.Image, bounds: tuple[int, int, int, int], center: int) -> Image.Image:
    """Map all pieces through one physical origin, never stretch per level."""
    x0, y0, x1, y1 = bounds
    image = source.crop(bounds)
    width = max(1, round((x1 - x0) * SOURCE_SCALE))
    height = max(1, round((y1 - y0) * SOURCE_SCALE))
    image = image.resize((width, height), Image.Resampling.LANCZOS)
    result = blank()
    result.alpha_composite(image, (round(mapped_x(x0, center)), round(mapped_y(y0))))
    return result


def save(image: Image.Image, path: Path) -> None:
    image.save(path, optimize=True)


def core_layer(head: Image.Image, pos: tuple[float, float], radius: float) -> Image.Image:
    # Draw the mask at 4x to preserve the blue globe's soft, circular edge.
    factor = 4
    mask = Image.new("L", (SIZE[0] * factor, SIZE[1] * factor), 0)
    draw = ImageDraw.Draw(mask)
    x, y = pos
    r = radius + 0.7
    draw.ellipse(((x-r)*factor, (y-r)*factor, (x+r)*factor, (y+r)*factor), fill=255)
    mask = mask.resize(SIZE, Image.Resampling.LANCZOS)
    core = head.copy()
    core.putalpha(ImageChops.multiply(head.getchannel("A"), mask))
    return core


def head_view(head: Image.Image, center_x: float, seam_y: float, angle: float) -> Image.Image:
    """Project a head azimuth while preserving the seam to its fixed shaft.

    The reference is upright and largely rotationally symmetric: applying a
    flat 2-D rotation would tip it over. Instead, taper the horizontal yaw
    projection smoothly to the original width at the neck. This keeps the
    photo's electric supports connected while the top changes perspective.
    A small signed parallax distinguishes opposite azimuths. The independent
    globe is drawn round on top at runtime.
    """
    def width_and_shift(y: float) -> tuple[float, float]:
        t = max(0.0, min(1.0, (seam_y - y) / 32.0))
        weight = t * t * (3.0 - 2.0 * t)
        projected_width = 0.72 + 0.28 * abs(math.cos(angle))
        parallax = 2.0 * math.sin(angle) + 0.9 * math.sin(2.0 * angle) + 0.3 * (1.0 - math.cos(angle))
        return 1.0 - (1.0 - projected_width) * weight, parallax * weight

    mesh = []
    for top in range(0, CELL, 4):
        bottom = min(CELL, top + 4)
        y0, y1 = float(top * 2), float(bottom * 2)
        f0, shift0 = width_and_shift(y0)
        f1, shift1 = width_and_shift(y1)
        left0 = center_x + (0.0 - center_x - shift0) / f0
        left1 = center_x + (0.0 - center_x - shift1) / f1
        right0 = center_x + (SIZE[0] - center_x - shift0) / f0
        right1 = center_x + (SIZE[0] - center_x - shift1) / f1
        # Pillow MESH maps output (UL,LL,LR,UR) to a source quadrilateral.
        mesh.append(((0, top, CELL, bottom), (left0, y0, left1, y1, right1, y1, right0, y0)))
    return head.transform((CELL, CELL), Image.Transform.MESH, mesh, Image.Resampling.BICUBIC)


def direction_atlas(head: Image.Image, center_x: float, seam_y: float) -> Image.Image:
    atlas = Image.new("RGBA", (COLUMNS * CELL, ROWS * CELL), (0, 0, 0, 0))
    for direction in range(DIRECTIONS):
        view = head_view(head, center_x, seam_y, direction * math.tau / DIRECTIONS)
        atlas.alpha_composite(view, ((direction % COLUMNS) * CELL, (direction // COLUMNS) * CELL))
    return atlas


def generate(destination: Path) -> None:
    destination.mkdir(parents=True, exist_ok=True)
    source = Image.open(SOURCE).convert("RGBA")
    # The source's first pedestal is reused literally, so it is exactly the
    # same footprint and banner in all five independent pictures.
    base = place(source, (0, 590, 286, 820), LEVELS[0][2])
    save(base, destination / "base.png")
    centers: list[list[float]] = []
    radii: list[float] = []
    for level, (left, right, center_x, cut, globe) in enumerate(LEVELS, 1):
        # Include a small overlap so the fixed shaft meets the pedestal without
        # a seam; head parts turn independently from both fixed layers.
        body = place(source, (left, cut, right, 628), center_x)
        head = place(source, (left, 0, right, cut), center_x)
        x, y, radius = globe
        core_center = (mapped_x(x, center_x), mapped_y(y))
        core_radius = radius * SOURCE_SCALE
        core = core_layer(head, core_center, core_radius)
        whole = base.copy()
        whole.alpha_composite(body)
        whole.alpha_composite(head)
        save(body, destination / f"body_{level}.png")
        save(head, destination / f"head_{level}.png")
        save(core, destination / f"core_{level}.png")
        save(whole, destination / f"tesla_{level}.png")
        save(direction_atlas(head, core_center[0], mapped_y(cut)), destination / f"head_{level}_directions.png")
        centers.append([round(v, 2) for v in core_center])
        radii.append(round(core_radius, 2))

    # The fixed-footprint in-game frame has deliberate transparent headroom for
    # later upgrades. An enlarged, transparent level-one portrait is useful in
    # the armory/build bar; the build ghost uses tesla_1 with the true anchor.
    level1 = Image.open(destination / "tesla_1.png").convert("RGBA")
    box = level1.getchannel("A").getbbox()
    assert box is not None
    portrait = level1.crop(box)
    icon_scale = min(210 / portrait.width, 220 / portrait.height)
    portrait = portrait.resize((round(portrait.width * icon_scale), round(portrait.height * icon_scale)),
                               Image.Resampling.LANCZOS)
    icon = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    icon.alpha_composite(portrait, ((256-portrait.width)//2, (256-portrait.height)//2))
    save(icon, destination / "icon.png")
    manifest = {
        "source": "user-provided reference, checkerboard removed",
        "canvas": list(SIZE),
        "ground_anchor": list(GROUND),
        "image_scale": 0.46,
        "core_centers": centers,
        "core_radii": radii,
        "source_scale": SOURCE_SCALE,
        "head_seams": [round(mapped_y(row[3]), 2) for row in LEVELS],
        "directions": DIRECTIONS,
        "direction_cell": [CELL, CELL],
        "direction_grid": [COLUMNS, ROWS],
        "levels": 5,
    }
    (destination / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEST)
    args = parser.parse_args()
    generate(args.output)
    print(f"Tesla: 27 transparent PNGs and manifest written to {args.output}")


if __name__ == "__main__":
    main()
