#!/usr/bin/env python3
"""Create the five-level artillery tower in Dezhban's tank/ink style.

The user's artillery picture supplies the silhouette: a large steel cannon,
bronze collars, red shield, ammunition rack and orange level-five glow.  The
base and banner are related to the already implemented machine-gun tower.
Each azimuth is actually redrawn, not a rotation of an upright flat sprite.
No pixels from the reference's checkerboard are used.

    python3 games/dejban/godot/tools/generate_artillery_art.py
    python3 games/dejban/godot/tools/generate_artillery_art.py --output /tmp/artillery

Pillow is needed only to regenerate the PNGs, not to run the game.
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw

import generate_machinegun_art as m

DEST = Path(__file__).resolve().parents[1] / "assets/sprites/towers_artillery"
PREVIEW_ANGLE = -math.tau / 8  # the same 3/4 upward-right view as the reference
BRONZE = (190, 113, 34, 255)
BRONZE_L = (243, 178, 73, 255)
BRONZE_D = (102, 62, 30, 255)
WOOD = (150, 92, 43, 255)
WOOD_L = (200, 139, 71, 255)
WOOD_D = (83, 50, 28, 255)
OLIVE = (93, 112, 45, 255)
OLIVE_L = (151, 170, 76, 255)
OLIVE_D = (46, 58, 31, 255)


def _panel(p, coords, light, dark, width=2.0):
    m.poly(p, coords, light, dark, width)


def _shell(p, x, y, scale=1.0):
    """A single upright artillery shell, not a machine-gun ammunition belt."""
    def pt(a, b):
        return (x + a * scale, y + b * scale)
    _panel(p, [pt(-5.3, -17), pt(-3.7, -24), pt(0, -29), pt(3.7, -24),
               pt(5.3, -17), pt(5.3, 9), pt(3.7, 14), pt(-3.7, 14), pt(-5.3, 9)],
           OLIVE_L, OLIVE_D, 1.8)
    _panel(p, [pt(-3.7, -24), pt(0, -29), pt(3.7, -24), pt(4.8, -20),
               pt(-4.8, -20)], m.CREAM_L, m.CREAM_D, 1.1)
    p.line([pt(-3.8, -15), pt(-3.8, 7)], m.iso.alpha(m.WHITE, 105), 1.1)
    _panel(p, [pt(-5.2, 7), pt(5.2, 7), pt(5.2, 11), pt(-5.2, 11)],
           BRONZE_L, BRONZE_D, 1.1)


def base_art(level: int) -> Image.Image:
    """Identical footprint/anchor; level details stay on the stationary base."""
    p = m.ArtPen(ss=3)
    # Start with the same pedestal geometry, sandbags and red flag as the
    # previous machine-gun. Color/armor additions are never on the rotating head.
    p.img.alpha_composite(m.fixed_base().resize(p.img.size, Image.Resampling.LANCZOS))

    # Timber struts and brass braces evoke the construction in the photo while
    # retaining the prior tower's steel feet and banner.
    _panel(p, [(75, 136), (85, 137), (73, 185), (64, 183)], WOOD_L, WOOD_D, 2.1)
    _panel(p, [(171, 137), (181, 135), (192, 183), (182, 185)], WOOD_L, WOOD_D, 2.1)
    p.line([(77, 141), (68, 178)], m.iso.alpha(m.CREAM, 110), 1.0)
    p.line([(176, 140), (186, 178)], m.iso.alpha(m.CREAM, 110), 1.0)
    for x, y in ((73, 145), (67, 174), (178, 145), (186, 174)):
        m.bolt(p, x, y, 2.0, m.STEEL)
    _panel(p, [(99, 127), (157, 127), (157, 136), (99, 136)], BRONZE_L, BRONZE_D, 2.0)
    p.line([(103, 129), (153, 129)], m.iso.alpha(m.YELLOW, 200), 1.1)

    if level >= 3:
        # Racked shells are part of the pedestal, not attached to the rotating
        # breech. Two shells at level 3; larger racks as armor grows.
        _panel(p, [(37, 153), (72, 145), (81, 150), (81, 191), (42, 197), (37, 192)],
               m.IRON_L, m.DEEP, 2.8)
        shells = [(49, 176, .78), (61, 174, .78)] if level == 3 else [
            (46, 176, .78), (57, 175, .86), (69, 173, .82)]
        if level == 5:
            shells = [(45, 172, .96), (58, 171, 1.02), (71, 170, .98)]
        for x, y, s in shells:
            _shell(p, x, y, s)
        _panel(p, [(38, 176), (79, 169), (80, 176), (39, 183)],
               BRONZE_L, BRONZE_D, 2.0)
        _panel(p, [(40, 186), (80, 180), (80, 187), (42, 194)], m.IRON_L, m.DARK, 2.0)
        for x, y in ((44, 178), (76, 173), (46, 189), (76, 184)):
            m.bolt(p, x, y, 1.7, m.CREAM)
    if level >= 4:
        # Additional heavy steel foot plating and rivets on the right.
        _panel(p, [(177, 184), (198, 181), (205, 200), (197, 207), (173, 207)],
               m.CREAM, m.IRON_L, 2.6)
        for x, y in ((184, 192), (196, 196), (181, 202)):
            m.bolt(p, x, y, 1.9, m.IRON_L)
    if level == 5:
        # Small self-lit windows; opaque cores and short transparent halos avoid
        # a costly PointLight2D on every artillery tower.
        for x, y in ((106, 131), (148, 131), (186, 198)):
            p.dot(x, y, 6.0, BRONZE_D, None)
            m.box(p, [x - 4.8, y - 2.9, x + 4.8, y + 2.9], m.YELLOW, m.ORANGE, 1.6, 1.2)
            p.line([(x - 2.3, y - 1), (x + 2.3, y - 1)], m.WHITE, .8)
    return p.out()


def _plane_ring(p, angle, side, u, y, radius, outer, inner):
    """A circular trunnion projected onto the upright side of the breech."""
    def circle(r):
        return [m.point(angle, u + math.cos(t) * r, side, y + math.sin(t) * r)
                for t in (i * math.tau / 28 for i in range(28))]
    _panel(p, circle(radius + 2.4), m.DARK, m.DEEP, 2.2)
    _panel(p, circle(radius), outer, BRONZE_D, 1.8)
    _panel(p, circle(radius * .65), inner, m.DARK, 1.5)
    _panel(p, circle(radius * .28), m.CREAM_L, m.IRON_L, .8)


def _side_plate(p, angle, side, level):
    """The distinctive red side shield and white insignia from levels 3–5."""
    if level < 3:
        return
    # Heights in local world space (larger y is higher). Angled edges produce
    # a believable beveled steel backing rather than a floating flat sticker.
    hi = 23 + level
    shape = [(-31, hi - 3), (-25, hi + 1), (-8, hi + 1), (-3, hi - 4),
             (-3, -7), (-11, -13), (-27, -13), (-33, -7)]
    m.plane_poly(p, angle, side, shape, m.STEEL, m.DARK, 2.8)
    shape = [(-29, hi - 4), (-24, hi - 1), (-9, hi - 1), (-6, hi - 5),
             (-6, -5), (-12, -10), (-25, -10), (-29, -6)]
    m.plane_poly(p, angle, side * 1.015, shape, m.RED_L, m.RED_D, 2.2)
    # The insignia stays on the projected armor face at every azimuth.
    mark = [(-18, hi - 4), (-11, hi - 13), (-14, hi - 15), (-16, hi - 12),
            (-16, -1), (-13, -4), (-18, -8), (-23, -4), (-20, -1),
            (-20, hi - 12), (-22, hi - 15), (-25, hi - 13)]
    m.plane_poly(p, angle, side * 1.03, mark, m.WHITE, m.CREAM, .5)
    for u, y in ((-29, hi - 2), (-8, hi - 2), (-29, -7), (-8, -7)):
        x, yy = m.point(angle, u, side * 1.04, y)
        m.bolt(p, x, yy, 1.7, m.CREAM)


def _rear_plate(p, angle, width, height):
    """Shield on the visible rear face when the muzzle points away from camera."""
    if math.sin(angle) >= -.16:
        return
    u = -35.4
    def plane(coords):
        return [m.point(angle, u, v, y) for v, y in coords]
    outer = [(-width + 2, height - 3), (width - 2, height - 3),
             (width - 1, -5), (0, -13), (-width + 1, -5)]
    _panel(p, plane(outer), m.STEEL, m.IRON, 2.4)
    inner = [(-width + 5, height - 6), (width - 5, height - 6),
             (width - 4, -3), (0, -10), (-width + 4, -3)]
    _panel(p, plane(inner), m.RED_L, m.RED_D, 1.7)
    mark = [(0, height - 8), (6, height - 17), (4, height - 19),
            (2, height - 16), (2, -2), (5, -4), (0, -8),
            (-5, -4), (-2, -2), (-2, height - 16),
            (-4, height - 19), (-6, height - 17)]
    m.poly(p, plane(mark), m.WHITE, m.CREAM, .5, outline=None)
    for v, h in ((-width + 4, height - 4), (width - 4, height - 4),
                 (-width + 3, -4), (width - 3, -4)):
        x, y = m.point(angle, u - .2, v, h)
        m.bolt(p, x, y, 1.6, m.CREAM)


def head_art(level: int, angle: float, size: int = m.SIZE) -> Image.Image:
    """Gun carriage: the large barrel is its own independently recoiling layer."""
    p = m.ArtPen(size, ss=3 if size == m.SIZE else 2)
    width = [15, 17, 20, 22, 24][level - 1]
    height = [14, 17, 20, 23, 26][level - 1]
    # Rotating bearing, cradle and armored breech all use the same prism
    # renderer as the game's isometric tanks.
    m.tank.prism(p, angle, m.plan(-20, 20, 14, 4), -20, -10, m.DARK,
                 w=2.5, top_color=m.IRON_L)
    m.tank.prism(p, angle, m.plan(-35, 23, width, 6), -12, height, m.NAVY_D,
                 w=3.0, top_color=m.IRON, top_boost=.10)
    m.tank.prism(p, angle, m.plan(-30, 14, width - 4, 4), height - 3, height + 3,
                 m.IRON, w=2.0, top_color=m.IRON_L)
    m.top_poly(p, angle, height + 3.5,
               [(-25, -width + 7), (-8, -width + 7), (-8, width - 7), (-25, width - 7)],
               BRONZE_L, BRONZE_D, 1.5)

    facing = math.cos(angle)
    if abs(facing) > .12:
        side = (1 if facing >= 0 else -1) * (width + .5)
        m.plane_poly(p, angle, side,
                     [(-34, height - 4), (12, height - 4), (18, height - 9),
                      (18, -6), (12, -11), (-31, -11)], m.IRON_L, m.DEEP, 2.2)
        # Thick arched bronze/steel housing is characteristic of the photo.
        m.plane_poly(p, angle, side * 1.01,
                     [(-33, height - 3), (-26, height + 1), (-18, height + 1),
                      (-13, height - 3), (-13, -10), (-21, -12), (-31, -9)],
                     BRONZE_L, BRONZE_D, 2.2)
        if level <= 2:
            _plane_ring(p, angle, side * 1.04, -23, 1, 8 + level,
                        m.RED if level == 2 else m.IRON_L, m.NAVY_D)
        else:
            _side_plate(p, angle, side * 1.04, level)
        if level >= 4:
            m.plane_poly(p, angle, side * 1.06,
                         [(2, height - 3), (14, height - 4), (19, height - 9),
                          (19, -7), (13, -11), (2, -10)], m.STEEL, m.IRON, 2.2)
            for u, y in ((5, height - 7), (14, height - 9), (14, -6), (5, -7)):
                x, yy = m.point(angle, u, side * 1.07, y)
                m.bolt(p, x, yy, 1.6, m.CREAM)
        if level == 5:
            # Orange rear energy cylinder with bounded decorative glow.
            _plane_ring(p, angle, side * 1.1, -35, 5, 8, m.ORANGE, m.GOLD_D)
            x, yy = m.point(angle, -35, side * 1.12, 5)
            p.dot(x, yy, 3.7, m.YELLOW, None)
    if level >= 3:
        _rear_plate(p, angle, width, height)
    # A steel coupling, not a second hollow muzzle: only the main barrel has a bore.
    # The collar stays with the head while only the barrel recoils.
    m.axis_ring(p, angle, 20, 14 + level, m.NAVY_L,
                rim=BRONZE if level >= 2 else m.STEEL)
    return p.out()


def _axis_disc(p, angle, u, radius, color, width, length_factor=.45):
    x, y = m.point(angle, u, 0, 0)
    dx, dy = math.cos(angle), math.sin(angle) * m.DEPTH
    mag = math.hypot(dx, dy) or 1
    dx, dy = dx / mag, dy / mag
    nx, ny = -dy, dx
    pts = [(x + dx * radius * length_factor * math.cos(t) + nx * radius * math.sin(t),
            y + dy * radius * length_factor * math.cos(t) + ny * radius * math.sin(t))
           for t in (i * math.tau / 32 for i in range(32))]
    _panel(p, pts, color, m.iso.shade(color, -.24), width)


def barrel_art(level: int, angle: float, size: int = m.SIZE) -> Image.Image:
    p = m.ArtPen(size, ss=3 if size == m.SIZE else 2)
    r = [10.5, 12.0, 14.0, 16.0, 18.0][level - 1]
    start = [34, 31, 27, 24, 21][level - 1]
    m.iso.barrel(p, angle, 0, 90, r, start=start, body=m.NAVY,
                 hi=m.NAVY_L, lo=m.NAVY_D, bands=2, muzzle_brake=False)
    dx, dy = math.cos(angle), math.sin(angle) * m.DEPTH
    mag = math.hypot(dx, dy) or 1
    dx, dy = dx / mag, dy / mag
    nx, ny = -dy, dx
    # Broad metal sleeves; the bands have volume rather than looking like lines.
    rings = [(47, 5.0), (75, 3.2), (91, 5.8)] if level >= 3 else [(52, 4.0), (91, 5.0)]
    for dist, thick in rings:
        x, y = m.point(angle, dist, 0, 0)
        reach = r + (1.0 if dist < 90 else 1.6)
        pts = [(x - dx * thick / 2 + nx * reach, y - dy * thick / 2 + ny * reach),
               (x + dx * thick / 2 + nx * reach, y + dy * thick / 2 + ny * reach),
               (x + dx * thick / 2 - nx * reach, y + dy * thick / 2 - ny * reach),
               (x - dx * thick / 2 - nx * reach, y - dy * thick / 2 - ny * reach)]
        col = m.STEEL if level == 1 and dist > 90 else BRONZE_L
        _panel(p, pts, col, m.IRON if col == m.STEEL else BRONZE_D, 2.0)
        p.line([(x - dx * thick * .15 - nx * reach * .82, y - dy * thick * .15 - ny * reach * .82),
                (x + dx * thick * .15 - nx * reach * .82, y + dy * thick * .15 - ny * reach * .82)],
               m.iso.alpha(m.WHITE, 180), 1.1)
    if level == 5:
        for dist in (45, 73):
            x, y = m.point(angle, dist, 0, 0)
            p.line([(x + nx * r * .9, y + ny * r * .9), (x - nx * r * .9, y - ny * r * .9)],
                   m.ORANGE, 2.8)
            p.line([(x + nx * r * .6, y + ny * r * .6), (x - nx * r * .6, y - ny * r * .6)],
                   m.YELLOW, 1.0)
    # Broad visible bore: level 5 glows orange, the others remain dark.
    _axis_disc(p, angle, 100, r + 2.0, m.IRON if level == 1 else BRONZE, 2.4)
    _axis_disc(p, angle, 100, r * .77, m.DEEP if level < 5 else m.RED_D, 1.8)
    if level == 5:
        _axis_disc(p, angle, 100, r * .53, m.ORANGE, 1.1)
        _axis_disc(p, angle, 100, r * .27, m.YELLOW, .7)
    else:
        _axis_disc(p, angle, 100, r * .45, (12, 17, 22, 255), .5)
    return p.out()


def shell_art() -> Image.Image:
    """A transparent right-facing shell for the actual in-flight projectile."""
    ss = 4
    image = Image.new("RGBA", (40 * ss, 20 * ss), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    def shape(points, fill):
        draw.polygon([(int(x * ss), int(y * ss)) for x, y in points], fill=fill)
    # Dark cartoon outline and rounded, tapered olive shell body.
    shape([(1, 5), (4, 3), (25, 3), (30, 4), (38, 9), (39, 10),
           (38, 11), (30, 16), (25, 17), (4, 17), (1, 15)], m.INK)
    shape([(4, 5), (25, 5), (29, 6), (35, 10), (29, 14),
           (25, 15), (4, 15)], OLIVE)
    shape([(5, 5), (25, 5), (28, 6), (31, 8), (5, 8)], OLIVE_L)
    shape([(5, 12), (31, 12), (28, 14), (5, 15)], OLIVE_D)
    # The metallic nose and two gold bands echo the mounted ammunition.
    shape([(29, 5), (37, 9), (38, 10), (37, 11), (29, 15), (30, 10)], BRONZE_L)
    shape([(32, 10), (37, 10), (29, 15), (30, 10)], BRONZE_D)
    for x in (6, 23):
        shape([(x, 4), (x + 3, 4), (x + 3, 16), (x, 16)], m.INK)
        shape([(x + .7, 5), (x + 2.2, 5), (x + 2.2, 15), (x + .7, 15)], BRONZE_L)
    return image.resize((40, 20), Image.Resampling.LANCZOS)


def generate(dest: Path) -> None:
    dest.mkdir(parents=True, exist_ok=True)
    shell_art().save(dest / "shell.png", optimize=True)
    for level in range(1, 6):
        base = base_art(level)
        head = head_art(level, PREVIEW_ANGLE)
        barrel = barrel_art(level, PREVIEW_ANGLE)
        base.save(dest / f"base_{level}.png", optimize=True)
        head.save(dest / f"head_{level}.png", optimize=True)
        barrel.save(dest / f"barrel_{level}.png", optimize=True)
        m.composite(base, head, barrel).save(dest / f"artillery_{level}.png", optimize=True)
        heads = [head_art(level, i * math.tau / m.DIRECTIONS, m.DIRECTION_SIZE)
                 for i in range(m.DIRECTIONS)]
        barrels = [barrel_art(level, i * math.tau / m.DIRECTIONS, m.DIRECTION_SIZE)
                   for i in range(m.DIRECTIONS)]
        m.make_sheet(heads, 8, 6).save(dest / f"head_{level}_directions.png", optimize=True)
        m.make_sheet(barrels, 8, 6).save(dest / f"barrel_{level}_directions.png", optimize=True)
        print(f"Artillery level {level}: PNG and 48 views generated", flush=True)
    manifest = {
        "canvas": [256, 256], "ground_anchor": [128, 206],
        "head_pivot": [128, 100], "barrel_pivot": [128, 100],
        "muzzle_from_pivot": [100, 0], "preview_world_angle_degrees": -45,
        "projection_y": .68, "direction_cell": [128, 128], "directions": 48,
        "direction_grid": [8, 6], "runtime_scale": .39,
        "base": "fixed during aiming and recoil; same footprint with level-specific armor",
        "background": "transparent RGBA",
    }
    (dest / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEST)
    args = parser.parse_args()
    generate(args.output)


if __name__ == "__main__":
    main()
