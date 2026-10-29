#!/usr/bin/env python3
"""Machine-gun tower art, in the existing isometric enemy-tank drawing style.

Reference: the user's five-level tower (tapered pedestal, red pennant, steel,
gold ammunition, red housing and an orange-lit Gatling at level five).  Its
checkerboard is NOT an alpha channel and is never copied into these files.

The tank and the other isometric sprites already share the gradient/ink Pen and
prism renderer.  We use those same helpers here, including their 2.5D geometry,
rather than rotating a flattened picture of a gun.  Runtime interpolates nearby
azimuth views while the mathematically continuous aim and recoil stay separate.
The base is one immutable image for every level.

    python3 games/dejban/godot/tools/generate_machinegun_art.py
    python3 games/dejban/godot/tools/generate_machinegun_art.py --preview-only /tmp/tower-previews

Requires Pillow; no source reference image or network connection is required.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[4]
sys.path.insert(0, str(ROOT / "tools"))
import generate_iso_tower_sprites as iso  # noqa: E402
import generate_iso_enemy_tank_sprites as tank  # noqa: E402

# The same virtual projection as the tank, with the tower's documented pivot.
SIZE = 256
DIRECTION_SIZE = 128
DIRECTIONS = 48
SPIN_FRAMES = 8
DEPTH = 0.68
GROUND = (128, 206)
PIVOT = (128, 100)
MUZZLE = (100, 0)
PREVIEW_ANGLE = 0.0
DEST = Path(__file__).resolve().parents[1] / "assets/sprites/towers_machinegun"
iso.CX = PIVOT[0]
iso.GROUND = PIVOT[1]
iso.DEPTH = DEPTH

INK = iso.INK
DARK = (39, 45, 53, 255)
DEEP = (23, 31, 39, 255)
IRON = (68, 79, 88, 255)
IRON_L = (111, 127, 133, 255)
STEEL = (143, 155, 155, 255)
CREAM = tank.CREAM
CREAM_L = tank.CREAM_L
CREAM_D = tank.CREAM_D
NAVY = (47, 69, 82, 255)
NAVY_L = (79, 109, 120, 255)
NAVY_D = (29, 44, 53, 255)
RED = (210, 52, 41, 255)
RED_L = (247, 83, 53, 255)
RED_D = (119, 32, 30, 255)
GOLD = (221, 145, 44, 255)
GOLD_L = (255, 200, 80, 255)
GOLD_D = (142, 91, 38, 255)
ORANGE = (255, 135, 34, 255)
YELLOW = (255, 218, 104, 255)
SAND = (159, 121, 75, 255)
WHITE = (255, 247, 220, 255)


class ArtPen(iso.Pen):
    """Tank's Pen, with outline widths scaled for the compact atlas frames."""
    def __init__(self, size: int = SIZE, ss: int = 3):
        super().__init__(size=size, ss=ss, scale=size / SIZE, anchor=(0, 0))

    def _w(self, w):
        return max(1, int(round(w * self.s * self.scale)))


def poly(pen, points, light, dark=None, w=2.6, outline=INK):
    pen.poly(points, [(0.0, light), (1.0, dark or light)], outline, w)


def bolt(pen, x, y, radius=2.4, light=CREAM):
    pen.dot(x, y, radius, iso.shade(DARK, -0.22), INK, 0.8)
    pen.dot(x - .3, y - .3, radius * .58, iso.shade(light, -0.12), None)
    pen.dot(x - radius * .35, y - radius * .37, radius * .22, WHITE, None)


def box(pen, bounds, light, dark=None, radius=2.5, w=2.5):
    pen.rect(bounds, [(0.0, light), (1.0, dark or light)], radius=radius, outline=INK, w=w)


def fixed_base() -> Image.Image:
    """Tall, fixed steel pedestal with sandbags and an actual red pennant."""
    p = ArtPen(ss=3)
    p.ellipse([25, 194, 232, 226], (10, 13, 17, 64), None, 0)
    p.ellipse([39, 198, 218, 219], (10, 13, 17, 70), None, 0)

    # Broad six-sided foundation; the camera and palette match the tank.
    poly(p, [(34, 183), (67, 169), (188, 169), (222, 183), (221, 202),
             (187, 220), (69, 220), (35, 202)], IRON, DEEP, 3.6)
    poly(p, [(35, 183), (69, 198), (69, 218), (35, 201)], NAVY_L, NAVY_D, 2.7)
    poly(p, [(69, 198), (187, 198), (187, 218), (69, 218)], IRON, DARK, 2.6)
    poly(p, [(187, 198), (221, 183), (221, 201), (187, 218)], IRON, DEEP, 2.7)
    p.line([(39, 183), (69, 196), (187, 196), (216, 183)], iso.shade(STEEL, .14), 2.2)

    # Sloped wall: a noticeably tall tower, not the previous low octagonal platform.
    poly(p, [(75, 118), (181, 118), (202, 182), (188, 201), (68, 201), (53, 182)],
         NAVY_L, NAVY_D, 4.2)
    poly(p, [(75, 124), (102, 130), (91, 194), (56, 182)], NAVY, DEEP, 2.8)
    poly(p, [(102, 130), (154, 130), (166, 198), (90, 198)], NAVY_L, NAVY_D, 2.6)
    poly(p, [(154, 130), (181, 124), (200, 182), (166, 194)], IRON, DEEP, 2.8)
    for points in [[(80, 132), (65, 182)], [(92, 136), (78, 190)],
                   [(112, 139), (106, 187)], [(146, 139), (152, 187)],
                   [(166, 135), (178, 189)], [(177, 133), (192, 180)]]:
        p.line(points, iso.alpha(INK, 205), 2.0)
    for points in [[(67, 155), (89, 160)], [(62, 174), (84, 179)],
                   [(99, 160), (112, 162)], [(149, 163), (161, 160)],
                   [(168, 160), (187, 155)], [(172, 178), (193, 173)]]:
        p.line(points, iso.alpha(STEEL, 115), 1.4)

    # Plump sandbag stacks sit IN FRONT of the tower wall and behind the banner.
    for x, y in [(49, 176), (58, 188), (70, 197), (184, 176), (177, 188), (164, 197)]:
        box(p, [x - 13, y - 8, x + 13, y + 8], iso.shade(SAND, .20),
            iso.shade(SAND, -.30), radius=5.0, w=2.3)
        p.line([(x - 7, y - 5), (x + 5, y - 5)], iso.alpha(CREAM, 135), 1.4)

    # Four angled braces and cream-coloured bolted steel feet.
    poly(p, [(75, 129), (91, 132), (77, 192), (61, 187)], STEEL, IRON, 3.0)
    poly(p, [(165, 132), (181, 129), (195, 187), (179, 192)], STEEL, IRON, 3.0)
    poly(p, [(57, 187), (80, 190), (85, 207), (58, 207), (51, 202)], CREAM, IRON_L, 3.0)
    poly(p, [(176, 190), (198, 187), (205, 202), (198, 207), (171, 207)], CREAM, IRON_L, 3.0)
    poly(p, [(47, 203), (84, 206), (84, 219), (50, 219), (41, 213)], IRON, DARK, 2.5)
    poly(p, [(172, 206), (209, 203), (215, 213), (206, 219), (172, 219)], IRON, DARK, 2.5)
    for x, y in [(70, 198), (185, 198), (56, 212), (76, 213), (180, 213), (200, 212)]:
        bolt(p, x, y, 2.6, STEEL)

    # Mechanical ledge and orange/gold mounting ring.
    poly(p, [(61, 113), (90, 103), (166, 103), (195, 113), (193, 132),
             (166, 142), (90, 142), (63, 132)], IRON_L, DARK, 3.5)
    poly(p, [(62, 116), (91, 126), (91, 142), (63, 132)], STEEL, IRON, 2.5)
    poly(p, [(91, 126), (165, 126), (165, 142), (91, 142)], STEEL, IRON, 2.4)
    poly(p, [(165, 126), (194, 116), (193, 132), (165, 142)], IRON_L, DARK, 2.5)
    poly(p, [(68, 113), (91, 105), (165, 105), (188, 113), (165, 122), (91, 122)],
         CREAM, IRON, 2.6)
    poly(p, [(98, 127), (158, 127), (158, 137), (98, 137)], GOLD_L, GOLD_D, 2)
    p.line([(102, 129), (154, 129)], YELLOW, 1.5)
    for x, y in [(72, 124), (86, 130), (170, 130), (184, 124)]:
        bolt(p, x, y, 2.5, CREAM)

    # Bearing at a fixed point, no part of it inherits the aim rotation.
    p.ellipse([81, 91, 175, 127], [(0, iso.shade(IRON, .22)), (1, DEEP)], INK, 3.5)
    p.ellipse([88, 91, 168, 120], [(0, STEEL), (1, IRON)], INK, 2.6)
    p.ellipse([96, 92, 160, 114], [(0, iso.shade(DARK, .18)), (1, DEEP)], INK, 2.0)
    p.line([(98, 114), (111, 118), (146, 118), (159, 114)], iso.shade(GOLD, .18), 2.0)

    # Cloth banner, white upward insignia, shadow, hem and tiny rivets.
    poly(p, [(102, 140), (154, 140), (154, 189), (128, 207), (102, 189)],
         RED_L, RED_D, 3.0)
    poly(p, [(106, 145), (150, 145), (150, 187), (128, 201), (106, 187)],
         RED, iso.shade(RED_D, -.1), 1.5, outline=GOLD_D)
    p.line([(108, 148), (108, 184), (128, 197), (148, 184)], iso.shade(RED_L, .26), 1.5)
    # This is a graphic symbol, not a font: it stays legible at game scale.
    poly(p, [(128, 150), (139, 165), (135, 169), (131, 163), (131, 180),
             (135, 184), (128, 191), (121, 184), (125, 180), (125, 163),
             (121, 169), (117, 165)], WHITE, iso.shade(CREAM_L, -.08), 0.8, outline=None)
    for x in (106, 150):
        bolt(p, x, 142, 1.8, GOLD_L)
    return p.out()


def point(a: float, u: float, v: float, y: float):
    """Local forward/side/height to screen, using exactly the tank projection."""
    wx, wz = iso.rot2(v, u, a)
    return iso.proj(wx, y, wz)


def plan(rear, front, half_width, bevel=6.0):
    w = half_width
    return [(-w + bevel, rear), (w - bevel, rear), (w, rear + bevel),
            (w, front - bevel), (w - bevel, front), (-w + bevel, front),
            (-w, front - bevel), (-w, rear + bevel)]


def plane_poly(pen, a, side, coords, color, end=None, w=2.0):
    pts = [point(a, u, side, y) for u, y in coords]
    poly(pen, pts, color, end, w)


def top_poly(pen, a, h, coords, color, end=None, w=2.0):
    pts = [point(a, u, v, h) for u, v in coords]
    poly(pen, pts, color, end, w)


def axis_ring(pen, a, u, radius, core, rim=IRON_L):
    """Stylised metal sleeve, oriented across the visible gun axis."""
    c = point(a, u, 0, 0)
    x, y = math.cos(a), math.sin(a) * DEPTH
    length = math.hypot(x, y) or 1.0
    ax, ay = x / length, y / length
    nx, ny = -ay, ax
    def ellipse(rx, ry):
        return [(c[0] + ax * rx * math.cos(t) + nx * ry * math.sin(t),
                 c[1] + ay * rx * math.cos(t) + ny * ry * math.sin(t))
                for t in (i * math.tau / 24 for i in range(24))]
    poly(pen, ellipse(8.5, radius + 2), iso.shade(DARK, .2), DEEP, 2.6)
    poly(pen, ellipse(6.8, radius), iso.shade(rim, .18), iso.shade(rim, -.25), 1.8)
    poly(pen, ellipse(4.3, radius * .73), iso.shade(core, .12), iso.shade(core, -.36), 1.4)
    pen.line([(c[0] - nx * radius * .7 - ax * 1.5, c[1] - ny * radius * .7 - ay * 1.5),
              (c[0] - nx * radius * .1 - ax * 1.5, c[1] - ny * radius * .1 - ay * 1.5)],
             iso.alpha(CREAM_L, 190), 1.2)


def head_art(level: int, angle: float, size: int = SIZE) -> Image.Image:
    """Rerender a 3D head at one azimuth; the pivot never leaves (128,100)."""
    p = ArtPen(size, ss=3 if size == SIZE else 2)
    half = [18, 20, 22, 24, 26][level - 1]
    rear = [-39, -42, -43, -46, -47][level - 1]
    front = [22, 24, 25, 27, 29][level - 1]
    top = [19, 22, 24, 27, 30][level - 1]
    # Pedestal neck and receiver use the exact prisms used by the tank's hull.
    tank.prism(p, angle, plan(-17, 18, 12, 3), -18, -6, DARK, w=2.7,
               top_color=IRON, top_boost=.08)
    tank.prism(p, angle, plan(rear - 7, rear + 15, half - 2, 5), -9, top - 5,
               NAVY_D, w=2.8, top_color=IRON, top_boost=.12)
    tank.prism(p, angle, plan(rear, front, half, 6), -12, top,
               NAVY_D, w=3.5, top_color=IRON, top_boost=.10, bottom_boost=-.3)
    # The red/orange cowl is a raised bevel over a dark mechanical receiver,
    # not a solid red box: this makes the silhouette read like the reference.
    tank.prism(p, angle, plan(rear + 2, front - 8, half - 4, 6), top - 5, top + 3,
               GOLD if level == 1 else RED, w=2.5,
               top_color=GOLD_L if level == 1 else RED_L, top_boost=.04, bottom_boost=-.30)
    top_poly(p, angle, top + 3.6,
             [(rear + 5, -half + 8), (rear + 23, -half + 8),
              (rear + 23, half - 8), (rear + 5, half - 8)],
             GOLD_L if level == 1 else RED_L, GOLD if level == 1 else RED_D, 1.8)
    top_poly(p, angle, top + 3.9,
             [(rear + 26, -half + 6), (rear + 33, -half + 6),
              (rear + 33, half - 6), (rear + 26, half - 6)], GOLD_L, GOLD_D, 1.7)
    tank.prism(p, angle, plan(rear + 7, rear + 26, half * .50, 3), top + 3, top + 7,
               IRON, w=2.0, top_color=IRON_L, top_boost=.08)
    # Inset plates and bright fasteners break up the cowl, as on the tank.
    top_poly(p, angle, top + 4.2,
             [(rear + 36, -half + 7), (front - 12, -half + 7),
              (front - 12, half - 7), (rear + 36, half - 7)],
             GOLD_L if level == 1 else iso.shade(RED_L, .07),
             GOLD if level == 1 else RED, 1.2)
    for u in (rear + 38, front - 14):
        for v in (-half + 10, half - 10):
            x, yy = point(angle, u, v, top + 4.5)
            bolt(p, x, yy, 1.35, CREAM_L)
    # Small top hatch with seams and bolts, drawn on the shared top plane.
    top_poly(p, angle, top + 7.4,
             [(rear + 10, -half * .34), (rear + 23, -half * .34),
              (rear + 23, half * .34), (rear + 10, half * .34)],
             iso.shade(IRON_L, .12), IRON, 1.2)
    for u in (rear + 14, rear + 22):
        p0, p1 = point(angle, u, -half * .26, top + 7.7), point(angle, u, half * .26, top + 7.7)
        p.line([p0, p1], iso.alpha(INK, 190), 1.0)

    # On the near face the plate, golden fasteners and ammo belt remain vertical
    # as the head turns, rather than being flattened into a top-down sticker.
    facing = math.cos(angle)
    if abs(facing) > .12:
        near = (1 if facing >= 0 else -1) * (half + .35)
        # Raised, segmented side mechanics fill the area behind the red cowl.
        plane_poly(p, angle, near,
                   [(rear + 4, top - 5), (front - 4, top - 5), (front - 2, top - 9),
                    (front - 2, -8), (front - 7, -12), (rear + 5, -12), (rear + 2, -8)],
                   IRON_L, DEEP, 2.4)
        plane_poly(p, angle, near * 1.01,
                   [(rear + 27, top - 8), (front - 9, top - 8),
                    (front - 8, -6), (rear + 27, -6)], NAVY_L, NAVY_D, 1.5)
        for u in (rear + 30, rear + 35, rear + 40):
            if u < front - 9:
                p.line([point(angle, u, near * 1.03, top - 6),
                        point(angle, u, near * 1.03, -7)], iso.alpha(INK, 220), 1.5)
        # The red side armor is a separate, chamfered plate like the picture.
        plane_poly(p, angle, near * 1.04,
                   [(rear + 6, top - 2), (rear + 24, top - 2), (rear + 29, top - 7),
                    (rear + 27, -5), (rear + 23, -10), (rear + 6, -10),
                    (rear + 2, -6), (rear + 2, top - 7)],
                   RED_L, RED_D, 2.4)
        p.line([point(angle, rear + 7, near * 1.06, top - 4),
                point(angle, rear + 20, near * 1.06, top - 4)], iso.alpha(WHITE, 165), 1.3)
        # Tiny pale insignia echoes the banner without obscuring the mechanism.
        mark = [(rear + 15, top - 3), (rear + 20, top - 9), (rear + 18, top - 11),
                (rear + 17, top - 9), (rear + 17, -4), (rear + 14, -4),
                (rear + 14, top - 9), (rear + 13, top - 11), (rear + 11, top - 9)]
        plane_poly(p, angle, near * 1.07, mark, CREAM_L, CREAM, .7)
        for u, y in [(rear + 7, top - 5), (rear + 23, top - 5),
                     (rear + 7, -5), (rear + 23, -5), (front - 7, top - 8), (front - 7, -7)]:
            x, yy = point(angle, u, near * 1.075, y)
            bolt(p, x, yy, 1.6, CREAM)
        if level >= 2:
            # Articulated ammunition belt, not a flat decorative stripe.
            for i in range(10 if level >= 3 else 7):
                t = -1.30 + i * 2.15 / (9 if level >= 3 else 6)
                u = rear + 31 + 9 * math.cos(t)
                y = 1 - 13 * math.sin(t)
                x, yy = point(angle, u, near * 1.08, y)
                p.dot(x, yy, 3.4, GOLD, INK, 1.0, ry=2.8)
                p.dot(x - .7, yy - .7, 1.6, GOLD_L, None, ry=1.3)
        # Bolted circular gear on the rear/near side, increasing with the level.
        x, yy = point(angle, rear + 2, near * 1.11, 0)
        gear = 5.0 + level * .55
        p.dot(x, yy, gear + 1.6, DARK, INK, 1.7)
        p.dot(x, yy, gear, IRON_L, INK, 1.2)
        p.dot(x, yy, gear * .58, IRON, INK, .8)
        p.dot(x, yy, gear * .25, GOLD_L if level >= 2 else CREAM, None)
        if level >= 4:
            # Additional angular armor surrounds the near side of the receiver.
            plane_poly(p, angle, near * 1.10,
                       [(front - 16, top - 5), (front - 7, top - 5),
                        (front - 4, top - 9), (front - 4, -6),
                        (front - 8, -10), (front - 16, -10)], STEEL, IRON, 1.9)
            plane_poly(p, angle, near * 1.10,
                       [(rear + 1, -2), (rear + 11, -3), (rear + 12, -12), (rear + 1, -12)],
                       STEEL, DARK, 1.8)
            for u, y in ((rear + 6, -7), (front - 10, top - 8), (front - 10, -6)):
                x, yy = point(angle, u, near * 1.12, y)
                bolt(p, x, yy, 1.6, CREAM)
    # Front collar sits over the barrel roots; barrel tips remain unobscured.
    axis_ring(p, angle, front, 15 + level * .55, NAVY_D)
    if level >= 4:
        axis_ring(p, angle, front + 4, 12 + level * .5, NAVY_D, rim=GOLD_D)
    if level == 5:
        # Raised orange optical sight; faces, outline and highlights use the tank Pen.
        tank.prism(p, angle, plan(rear + 9, rear + 30, 10, 2), top + 7, top + 23,
                   DARK, w=2.3, top_color=IRON_L, top_boost=.12)
        if abs(facing) > .12:
            near_sight = (1 if facing >= 0 else -1) * 10.4
            plane_poly(p, angle, near_sight,
                       [(rear + 11, top + 21), (rear + 27, top + 21),
                        (rear + 27, top + 10), (rear + 11, top + 10)], RED_L, RED_D, 1.6)
            plane_poly(p, angle, near_sight * 1.025,
                       [(rear + 13, top + 19), (rear + 25, top + 19),
                        (rear + 25, top + 12), (rear + 13, top + 12)],
                       YELLOW, ORANGE, 1.0)
            x, yy = point(angle, rear + 19, near_sight * 1.04, top + 15.5)
            p.dot(x, yy, 2.8, WHITE, None)
            p.dot(x, yy, 1.2, (255, 255, 235, 255), None)
        # Orange status panels on both visible sides.
        if abs(facing) > .12:
            near = (1 if facing >= 0 else -1) * (half + 1)
            plane_poly(p, angle, near,
                       [(rear + 2, 0), (rear + 9, 0), (rear + 9, -7), (rear + 2, -7)],
                       YELLOW, ORANGE, 1.2)
    return p.out()


def draw_gun(p, angle: float, lateral: float, height: float, radius: float,
             level: int, end: float = 100.0):
    """One cylindrical tube, using the tank's 2.5D projection and heavy ink."""
    iso.barrel(p, angle, height, end, radius, lateral=lateral, start=18,
               body=NAVY, hi=NAVY_L, lo=NAVY_D, bands=2, muzzle_brake=False)
    ax, ay = math.cos(angle), math.sin(angle) * DEPTH
    l = math.hypot(ax, ay) or 1
    ax, ay = ax / l, ay / l
    nx, ny = -ay, ax
    for distance in ((63, 88) if level >= 3 else (69,)):
        x, y = point(angle, distance, lateral, height)
        k = radius + .3
        p.line([(x + nx * k, y + ny * k), (x - nx * k, y - ny * k)], INK, 4.0)
        p.line([(x + nx * k, y + ny * k), (x - nx * k, y - ny * k)],
               ORANGE if level == 5 else (GOLD if level >= 3 else IRON_L), 2.1)
        if level == 5:
            p.line([(x - nx * k * .8, y - ny * k * .8), (x - nx * k * .1, y - ny * k * .1)],
                   YELLOW, 1.0)


def draw_muzzle(p, angle, lateral, height, radius, end, level):
    # Draw muzzles after ALL tube bodies. Otherwise a near tube's long body can
    # paint over a far tube's bore and make a six-tube cluster look single-row.
    x, y = point(angle, end, lateral, height)
    p.dot(x, y, radius + .8, ORANGE if level == 5 else (GOLD_L if level >= 3 else STEEL), INK, 1.4)
    p.dot(x, y, radius * .62, DEEP, INK, .6)
    p.dot(x - radius * .25, y - radius * .25, radius * .18, iso.alpha(CREAM_L, 160), None)


def barrels_art(level: int, angle: float, spin: int = 0, size: int = SIZE) -> Image.Image:
    p = ArtPen(size, ss=3 if size == SIZE else 2)
    if level == 1:
        tubes = [(0.0, 0.0, 7.0, 100.0)]
    elif level == 2:
        tubes = [(0.0, 0.0, 8.0, 100.0)]
    elif level == 3:
        tubes = [(0, 0, 4.5, 100), (-8, 6, 4.5, 95), (8, 6, 4.5, 105), (0, -8, 4.5, 100)]
    elif level == 4:
        tubes = [(0, 0, 4.6, 100), (-10, 7, 4.6, 94), (10, 7, 4.6, 106),
                 (-8, -8, 4.6, 95), (8, -8, 4.6, 105)]
    else:
        # Six outer tubes rotate around a central tube, not around the aim axis.
        # Six identical outer tubes repeat after one sixth of a revolution.
        # Sample that entire period, so every one of the eight frames differs.
        phase = spin * math.tau / (6 * SPIN_FRAMES)
        tubes = [(0, 0, 4.8, 100.0)]
        for i in range(6):
            t = phase + i * math.tau / 6
            v, h = 15 * math.cos(t), 12 * math.sin(t)
            tubes.append((v, h, 4.8, 100.0 + v * .55))
    # Far pieces first; the centre tube's real muzzle is the shot origin.
    tubes.sort(key=lambda t: math.cos(angle) * t[0] + math.sin(angle) * t[3])
    for lateral, h, radius, end in tubes:
        draw_gun(p, angle, lateral, h, radius, level, end)
    for lateral, h, radius, end in tubes:
        draw_muzzle(p, angle, lateral, h, radius, end, level)
    return p.out()


def composite(base: Image.Image, head: Image.Image, barrels: Image.Image) -> Image.Image:
    image = base.copy()
    image.alpha_composite(barrels)
    image.alpha_composite(head)
    return image


def write_preview(path: Path):
    path.mkdir(parents=True, exist_ok=True)
    base = fixed_base()
    for level in range(1, 6):
        head = head_art(level, PREVIEW_ANGLE)
        barrels = barrels_art(level, PREVIEW_ANGLE)
        composite(base, head, barrels).save(path / f"machinegun_{level}.png")
    for index in range(8):
        angle = index * math.tau / 8
        composite(base, head_art(5, angle), barrels_art(5, angle)).save(path / f"direction_{index}.png")
    print(f"Preview files written to {path}")


def make_sheet(frames, columns, rows, size=DIRECTION_SIZE):
    sheet = Image.new("RGBA", (columns * size, rows * size), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        sheet.alpha_composite(frame, ((i % columns) * size, (i // columns) * size))
    return sheet


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--preview-only", type=Path, help="write previews outside the project, without changing game assets")
    args = parser.parse_args()
    if args.preview_only:
        write_preview(args.preview_only)
        return
    DEST.mkdir(parents=True, exist_ok=True)
    base = fixed_base()
    base.save(DEST / "base.png", optimize=True)
    for level in range(1, 6):
        head = head_art(level, PREVIEW_ANGLE)
        head.save(DEST / f"head_{level}.png", optimize=True)
        barrel_frames = [barrels_art(level, PREVIEW_ANGLE, f) for f in range(SPIN_FRAMES if level == 5 else 1)]
        if level == 5:
            make_sheet(barrel_frames, SPIN_FRAMES, 1, SIZE).save(DEST / "barrels_5_spin.png", optimize=True)
        else:
            barrel_frames[0].save(DEST / f"barrels_{level}.png", optimize=True)
        composite(base, head, barrel_frames[0]).save(DEST / f"machinegun_{level}.png", optimize=True)
        print(f"Level {level}: generating {DIRECTIONS} head directions", flush=True)
        heads = [head_art(level, i * math.tau / DIRECTIONS, DIRECTION_SIZE) for i in range(DIRECTIONS)]
        make_sheet(heads, 8, 6).save(DEST / f"head_{level}_directions.png", optimize=True)
        spins = SPIN_FRAMES if level == 5 else 1
        print(f"Level {level}: generating {DIRECTIONS * spins} barrel directions/spins", flush=True)
        barrels = [barrels_art(level, i * math.tau / DIRECTIONS, spin, DIRECTION_SIZE)
                   for spin in range(spins) for i in range(DIRECTIONS)]
        columns, rows = (16, 24) if level == 5 else (8, 6)
        make_sheet(barrels, columns, rows).save(DEST / f"barrels_{level}_directions.png", optimize=True)
    manifest = {
        "canvas": [SIZE, SIZE], "ground_anchor": list(GROUND), "base_pivot": list(PIVOT),
        "head_layer_pivot": list(PIVOT), "barrel_layer_pivot": list(PIVOT),
        "muzzle_from_pivot": list(MUZZLE), "direction": "+X; clockwise in the unprojected ground plane",
        "preview_angle_degrees": 0, "projection_y": DEPTH, "runtime_scale": .39,
        "direction_cell": [DIRECTION_SIZE, DIRECTION_SIZE], "directions": DIRECTIONS,
        "direction_grid": [8, 6], "gatling_grid": [16, 24], "gatling_frames": SPIN_FRAMES,
        "direction_interpolation": "cross-fade adjacent 3D-rendered angles",
        "background": "transparent RGBA", "fixed_base_across_levels": True,
    }
    (DEST / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"Generated five transparent towers and directional animation layers in {DEST}")


if __name__ == "__main__":
    main()
