#!/usr/bin/env python3
"""Build the five-level laser tower from the user's cleaned reference.

The PNG supplied to the conversation contained a baked checkerboard and beams.
`laser_source/reference_cutout.png` is the cleaned, transparent derivative:
the level-four beam crossing level five was retouched, idle lenses restored,
and all five white banner emblems preserved. The generated tower frames contain
no baked shots; a reusable beam strip and two separate muzzle sprites animate at
runtime. No generative model or screenshots of the game are used.

All five feet have one normalized width, anchor, and field scale. The artwork of
that fixed pedestal can become more armored, as in the reference; it never turns.
The cannon is a separate 48-direction atlas with a small seam fade and a rounded
front/back view, rather than a flat sprite spinning around its center.

Regeneration dependencies (not runtime dependencies): Pillow, NumPy and OpenCV.
    python3 games/dejban/godot/tools/generate_laser_art.py
    python3 games/dejban/godot/tools/generate_laser_art.py --output /tmp/laser
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/laser_source/reference_cutout.png"
DEST = ROOT / "assets/sprites/towers_laser"
SIZE = 384
GROUND = (192, 345)
FIELD_SCALE = 0.46
DEPTH = 0.68
FOOT_WIDTH = 136
SOURCE_GROUND_Y = 790
SOURCE_SCALE_Y = 0.45
CELL = 192
DIRECTIONS = 48
COLUMNS, ROWS = 8, 6

# x1,x2, original pedestal centre, top, base/head seam, lens centre, lens radii
LEVELS = [
    (8, 263, 135, 404, 489, (185, 448), (21, 26)),
    (275, 546, 411, 353, 435, (480, 402), (23, 28)),
    (559, 835, 700, 302, 412, (777, 355), (27, 34)),
    (842, 1160, 1002, 235, 355, (1084, 300), (31, 36)),
    (1173, 1532, 1352, 155, 340, (1452, 252), (44, 54)),
]


def mapped_x(x: float, left: int, right: int, center: float) -> float:
    return GROUND[0] + (x - center) * FOOT_WIDTH / (right - left)


def mapped_y(y: float) -> float:
    return GROUND[1] + (y - SOURCE_GROUND_Y) * SOURCE_SCALE_Y


def place(source: Image.Image, bounds: tuple[int, int, int, int], row: tuple) -> Image.Image:
    left, right, center, *_ = row
    x1, y1, x2, y2 = bounds
    result = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    w = max(1, round((x2 - x1) * FOOT_WIDTH / (right - left)))
    h = max(1, round((y2 - y1) * SOURCE_SCALE_Y))
    cut = source.crop(bounds).resize((w, h), Image.Resampling.LANCZOS)
    result.alpha_composite(cut, (round(mapped_x(x1, left, right, center)), round(mapped_y(y1))))
    return result


def _smoothstep(value: np.ndarray) -> np.ndarray:
    value = np.clip(value, 0.0, 1.0)
    return value * value * (3.0 - 2.0 * value)


def _mix_rgba(a: np.ndarray, b: np.ndarray, amount: float) -> np.ndarray:
    """Cross-fade with premultiplied alpha so direction changes do not fringe."""
    first = a.astype(np.float32)
    second = b.astype(np.float32)
    first[:, :, :3] *= first[:, :, 3:4] / 255.0
    second[:, :, :3] *= second[:, :, 3:4] / 255.0
    mix = first * amount + second * (1.0 - amount)
    alpha = mix[:, :, 3:4]
    mix[:, :, :3] = np.where(alpha > 0.0, mix[:, :, :3] * 255.0 / np.maximum(alpha, 1e-8), 0.0)
    return np.asarray(np.clip(mix, 0, 255), dtype=np.uint8)


def direction_view(head: Image.Image, angle: float, length: float, seam: float,
                   axis_y: float, lens_rx: float, lens_ry: float) -> Image.Image:
    """Preserve an upright gun while yawing its real bore toward the target.

    A horizontally compressed side projection alone would vanish edge-on. Near
    quarter-turns a rounded, symmetric front/back assembled from the same
    source replaces that projection; its separately masked circular lens stays
    exactly on the physical muzzle. Both views ease into the fixed bearing.
    """
    cos_a, sin_a = math.cos(angle), math.sin(angle)
    side_width = max(0.55, abs(cos_a))
    depth_shift = sin_a * DEPTH
    pixels = np.asarray(head)
    yy_grid, xx_grid = np.indices((SIZE, SIZE), dtype=np.float32)
    taper_size = max(5.0, (seam - axis_y) * 0.65)
    px = float(GROUND[0])

    def taper(y: np.ndarray) -> np.ndarray:
        return _smoothstep((seam - y) / taper_size)

    def side(sign: int) -> np.ndarray:
        factor = side_width * sign
        translation = length * (cos_a - factor)
        y = yy_grid.copy()
        for _ in range(7):
            amount = taper(y)
            x = px + (xx_grid - px - translation * amount) / factor
            y = yy_grid - depth_shift * (x - px) * amount
        amount = taper(y)
        x = px + (xx_grid - px - translation * amount) / factor
        y = yy_grid - depth_shift * (x - px) * amount
        return cv2.remap(pixels, x.astype(np.float32), y.astype(np.float32), cv2.INTER_LINEAR,
                         borderMode=cv2.BORDER_CONSTANT, borderValue=(0, 0, 0, 0))

    def quarter() -> np.ndarray:
        half = pixels[:, :GROUND[0], :]
        symmetric = np.zeros_like(pixels)
        symmetric[:, :GROUND[0], :] = half
        symmetric[:, GROUND[0]:2 * GROUND[0], :] = half[:, ::-1, :]
        y = yy_grid.copy()
        offset_y = depth_shift * length
        for _ in range(8):
            y = yy_grid - offset_y * taper(y)
        view = cv2.remap(symmetric, xx_grid, y.astype(np.float32), cv2.INTER_LINEAR,
                         borderMode=cv2.BORDER_CONSTANT, borderValue=(0, 0, 0, 0))
        # A circular crop from the source follows the mathematical barrel tip,
        # so both the muzzle and a laser ray originate at the same point.
        x0, x1 = int(px + length - lens_rx), int(px + length + lens_rx + 1)
        y0, y1 = int(axis_y - lens_ry), int(axis_y + lens_ry + 1)
        lens = head.crop((x0, y0, x1, y1))
        clip = Image.new("L", lens.size, 0)
        ImageDraw.Draw(clip).ellipse((0, 0, lens.width - 1, lens.height - 1), fill=255)
        lens.putalpha(Image.fromarray(np.minimum(np.asarray(lens.getchannel("A")), np.asarray(clip))))
        view_image = Image.fromarray(view, "RGBA")
        view_image.alpha_composite(lens, (int(px + length * cos_a - lens_rx), int(axis_y + offset_y - lens_ry)))
        return np.asarray(view_image)

    k = max(0.0, min(1.0, (abs(cos_a) - 0.14) / 0.52))
    quarter_weight = 1.0 - k * k * (3.0 - 2.0 * k)
    if quarter_weight >= 0.999:
        return Image.fromarray(quarter(), "RGBA")
    side_image = side(1 if cos_a >= 0 else -1)
    if quarter_weight <= 0.001:
        return Image.fromarray(side_image, "RGBA")
    return Image.fromarray(_mix_rgba(quarter(), side_image, quarter_weight), "RGBA")


def direction_atlas(head: Image.Image, length: float, seam: float, axis_y: float,
                    lens_rx: float, lens_ry: float) -> Image.Image:
    atlas = Image.new("RGBA", (COLUMNS * CELL, ROWS * CELL), (0, 0, 0, 0))
    for index in range(DIRECTIONS):
        angle = index * math.tau / DIRECTIONS
        view = direction_view(head, angle, length, seam, axis_y, lens_rx, lens_ry)
        view = view.resize((CELL, CELL), Image.Resampling.LANCZOS)
        atlas.alpha_composite(view, ((index % COLUMNS) * CELL, (index // COLUMNS) * CELL))
    return atlas


def beam_texture() -> Image.Image:
    """Blue-white continuous segment; width and length are set by the game."""
    im = Image.new("RGBA", (256, 64), (0, 0, 0, 0))
    pix = im.load()
    for y in range(im.height):
        d = abs((y - 31.5) / 31.5)
        for x in range(im.width):
            edge = max(0.0, 1.0 - d)
            alpha = int(255 * (edge ** 2.2) * (0.92 + 0.08 * math.sin(x * 0.11) ** 2))
            core = max(0.0, 1.0 - d * 6.0)
            pix[x, y] = (int(12 + 243 * core), int(92 + 163 * core), 255, alpha)
    return im


def muzzle_texture(star: bool) -> Image.Image:
    """Independent transparent glow/flash; both are reused by LaserMuzzle."""
    side = 128
    im = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    pix = im.load()
    for y in range(side):
        for x in range(side):
            dx, dy = abs(x - 63.5), abs(y - 63.5)
            r = math.sqrt(dx * dx + dy * dy) / 62.0
            falloff = max(0.0, 1.0 - r)
            if star:
                rays = max(max(0.0, 1.0 - dx / 3.0) * max(0.0, 1.0 - dy / 55.0),
                           max(0.0, 1.0 - dy / 3.0) * max(0.0, 1.0 - dx / 55.0))
                alpha = int(200 * max(falloff ** 6 * 0.8, rays * 0.75))
            else:
                alpha = int(155 * falloff ** 2.4)
            bright = max(0.0, 1.0 - r * 4.0)
            pix[x, y] = (int(25 + 230 * bright), int(145 + 110 * bright), 255, alpha)
    return im


def generate(output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    source = Image.open(SOURCE).convert("RGBA")
    axes, lengths, seams, lens_radii = [], [], [], []
    for level, row in enumerate(LEVELS, 1):
        left, right, center, top, cut, (lens_x, lens_y), (rx, ry) = row
        # A 3px source overlap hides tiny rounding seams in the fixed bearing.
        base = place(source, (left, cut, right, SOURCE_GROUND_Y), row)
        head = place(source, (left, top, right, cut + 3), row)
        # The original fifth foot ends one source pixel earlier; normalize the
        # actual opaque bottom as well as the logical ground anchor.
        base_bbox = base.getchannel("A").getbbox()
        if base_bbox is None:
            raise ValueError(f"Empty base for level {level}")
        bottom_shift = GROUND[1] - base_bbox[3]
        if bottom_shift:
            shifted_base = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
            shifted_head = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
            shifted_base.alpha_composite(base, (0, bottom_shift))
            shifted_head.alpha_composite(head, (0, bottom_shift))
            base, head = shifted_base, shifted_head
        axis_y = mapped_y(lens_y) + bottom_shift
        length = mapped_x(lens_x, left, right, center) - GROUND[0]
        radius_x = rx * FOOT_WIDTH / (right - left)
        radius_y = ry * SOURCE_SCALE_Y
        seam = mapped_y(cut) + bottom_shift
        whole = base.copy()
        whole.alpha_composite(head)
        for name, image in [(f"base_{level}.png", base), (f"head_{level}.png", head),
                            (f"laser_{level}.png", whole),
                            (f"head_{level}_directions.png", direction_atlas(head, length, seam, axis_y, radius_x, radius_y))]:
            image.save(output / name, optimize=True)
        axes.append([float(GROUND[0]), round(axis_y, 3)])
        lengths.append(round(length, 3))
        seams.append(round(seam, 3))
        lens_radii.append([round(radius_x, 3), round(radius_y, 3)])

    for name, image in [("beam_strip.png", beam_texture()), ("muzzle_glow.png", muzzle_texture(False)),
                        ("muzzle_flash.png", muzzle_texture(True))]:
        image.save(output / name, optimize=True)
    level_one = Image.open(output / "laser_1.png").convert("RGBA")
    bbox = level_one.getchannel("A").getbbox()
    if bbox is None:
        raise ValueError("Level one is empty")
    portrait = level_one.crop(bbox)
    scale = min(210 / portrait.width, 220 / portrait.height)
    portrait = portrait.resize((round(portrait.width * scale), round(portrait.height * scale)), Image.Resampling.LANCZOS)
    icon = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    icon.alpha_composite(portrait, ((256 - portrait.width) // 2, (256 - portrait.height) // 2))
    icon.save(output / "icon.png", optimize=True)
    manifest = {
        "source": "user-provided laser reference, checkerboard and baked beams removed",
        "canvas": [SIZE, SIZE], "ground_anchor": list(GROUND), "field_scale": FIELD_SCALE,
        "foot_width": FOOT_WIDTH, "depth_squash": DEPTH, "axis_centers": axes,
        "muzzle_lengths": lengths, "head_seams": seams, "lens_radii": lens_radii,
        "directions": DIRECTIONS, "direction_cell": [CELL, CELL], "direction_grid": [COLUMNS, ROWS],
        "levels": 5,
    }
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEST)
    generate(parser.parse_args().output)
    print("Laser: 24 transparent PNGs and one manifest generated.")


if __name__ == "__main__":
    main()
