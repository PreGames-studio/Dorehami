#!/usr/bin/env python3
"""Generate an original chibi red enemy soldier sprite pack for tower defense.

Requires Pillow: python3 -m pip install Pillow
Run: python3 tools/generate_cartoon_soldier_sprites.py
Output: assets/enemy_soldier_cartoon/ (five transparent horizontal PNG strips).
"""
from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Iterable, Sequence

from PIL import Image, ImageDraw

FRAME = 64
SCALE = 4
OUT = Path(__file__).resolve().parents[1] / "assets" / "enemy_soldier_cartoon"

# A warm, readable palette with a deep plum-brown cartoon ink line.
INK = (47, 36, 47, 255)
INK_SOFT = (69, 48, 51, 255)
RED = (205, 48, 57, 255)
RED_LIGHT = (244, 91, 73, 255)
RED_HI = (255, 139, 97, 255)
RED_DARK = (137, 34, 47, 255)
RED_DEEP = (104, 29, 41, 255)
SKIN = (242, 178, 128, 255)
SKIN_LIGHT = (255, 215, 164, 255)
SKIN_SHADOW = (192, 107, 82, 255)
GOLD = (255, 195, 72, 255)
GOLD_LIGHT = (255, 228, 134, 255)
OLIVE = (113, 134, 73, 255)
OLIVE_LIGHT = (159, 176, 103, 255)
OLIVE_DARK = (64, 79, 51, 255)
BOOT = (83, 53, 48, 255)
BOOT_LIGHT = (133, 86, 58, 255)
METAL = (111, 119, 128, 255)
METAL_LIGHT = (190, 202, 203, 255)
METAL_DARK = (48, 53, 64, 255)
WOOD = (117, 72, 53, 255)


def px(v: float) -> int:
    return int(round(v * SCALE))


class Art:
    """Small vector-style painter rendered supersampled for clean 64px output."""

    def __init__(self) -> None:
        self.image = Image.new("RGBA", (FRAME * SCALE, FRAME * SCALE), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.image)

    @staticmethod
    def p(point: tuple[float, float]) -> tuple[int, int]:
        return (px(point[0]), px(point[1]))

    @staticmethod
    def b(box: tuple[float, float, float, float]) -> tuple[int, int, int, int]:
        return tuple(px(v) for v in box)  # type: ignore[return-value]

    def ellipse(self, box, fill, outline=INK, width=1.8):
        self.d.ellipse(self.b(box), fill=fill, outline=outline,
                       width=max(1, px(width)) if outline is not None else 1)

    def roundrect(self, box, radius, fill, outline=INK, width=1.8):
        self.d.rounded_rectangle(self.b(box), radius=max(1, px(radius)), fill=fill,
                                 outline=outline,
                                 width=max(1, px(width)) if outline is not None else 1)

    def polygon(self, points: Sequence[tuple[float, float]], fill,
                outline=INK, width=1.8):
        coords = [self.p(p) for p in points]
        self.d.polygon(coords, fill=fill)
        if outline is not None and width > 0:
            self.d.line(coords + [coords[0]], fill=outline, width=max(1, px(width)), joint="curve")

    def line(self, points: Sequence[tuple[float, float]], fill, width=1.5,
             round_caps=True):
        coords = [self.p(p) for p in points]
        w = max(1, px(width))
        self.d.line(coords, fill=fill, width=w, joint="curve")
        if round_caps:
            r = max(1, w // 2)
            for x, y in (coords[0], coords[-1]):
                self.d.ellipse((x-r, y-r, x+r, y+r), fill=fill)

    def outlined_limb(self, points, fill, outer=7.2, inner=4.7):
        self.line(points, INK, outer)
        self.line(points, fill, inner)

    def star(self, cx, cy, r_outer, r_inner, fill, outline=INK, width=0.8, tips=5):
        points = []
        for i in range(tips * 2):
            angle = -math.pi / 2 + i * math.pi / tips
            radius = r_outer if i % 2 == 0 else r_inner
            points.append((cx + math.cos(angle) * radius, cy + math.sin(angle) * radius))
        self.polygon(points, fill, outline, width)

    def finish(self) -> Image.Image:
        return self.image.resize((FRAME, FRAME), Image.Resampling.LANCZOS)


def add(point, dx=0.0, dy=0.0):
    return (point[0] + dx, point[1] + dy)


def draw_shadow(a: Art, cx=32.0, y=57.0, width=13.0, alpha=72):
    a.ellipse((cx-width, y-2.2, cx+width, y+2.0), (35, 29, 42, alpha), None, 0)
    a.ellipse((cx-width*0.63, y-1.3, cx+width*0.63, y+1.1), (39, 31, 39, alpha//2), None, 0)


def draw_backpack(a: Art, dx=0.0, dy=0.0):
    # Small olive canvas pack; it remains the same shape in every animation.
    a.roundrect((16.5+dx, 28.5+dy, 24.2+dx, 41.2+dy), 2.1, OLIVE, INK, 2.0)
    a.polygon([(17.7+dx, 31.0+dy), (19.0+dx, 29.5+dy),
               (23.0+dx, 30.0+dy), (23.3+dx, 33.0+dy),
               (18.1+dx, 33.0+dy)], OLIVE_LIGHT, INK, 1.0)
    a.roundrect((17.1+dx, 35.2+dy, 20.4+dx, 39.3+dy), 1.0, OLIVE_DARK, INK, 0.9)
    a.line([(18.0+dx, 36.1+dy), (19.5+dx, 36.1+dy)], GOLD, 0.8)
    a.line([(22.0+dx, 34.1+dy), (22.4+dx, 39.5+dy)], (191, 178, 112, 255), 1.0)
    # Shoulder strap visible across the red coat.
    a.line([(23.3+dx, 30.5+dy), (27.6+dx, 43.6+dy)], INK, 2.2)
    a.line([(23.3+dx, 30.5+dy), (27.6+dx, 43.6+dy)], (224, 185, 100, 255), 1.0)


def draw_boot(a: Art, x, y, toe=1.0):
    pts = [(x-3.5, y-2.5), (x+1.2, y-3.1), (x+3.4*toe, y-1.5),
           (x+5.1*toe, y-0.4), (x+4.4*toe, y+1.2), (x-3.0, y+1.2)]
    a.polygon(pts, BOOT, INK, 1.4)
    a.line([(x-2.1, y-0.7), (x+1.5*toe, y-0.8)], BOOT_LIGHT, 0.8)
    a.line([(x-1.1, y-2.3), (x-0.7, y-0.2)], GOLD, 0.45)


def draw_leg(a: Art, hip, stride=0.0, lift=0.0, near=False, dying=0.0):
    hx, hy = hip
    # Knee bends and boot pitch are exaggerated just enough to read at 64px.
    knee = (hx + stride*0.48 - dying*1.2, hy + 5.0 - lift*0.28)
    ankle = (hx + stride - dying*2.4, hy + 10.2 - lift)
    pants = RED_DARK if near else (157, 43, 52, 255)
    a.outlined_limb([hip, knee, ankle], pants, 7.3, 4.8)
    a.ellipse((knee[0]-2.0, knee[1]-1.8, knee[0]+2.0, knee[1]+1.7), RED, INK, 0.9)
    a.line([(knee[0]-0.8, knee[1]-0.4), (knee[0]+0.9, knee[1]+0.2)], RED_LIGHT, 0.6)
    draw_boot(a, ankle[0]+0.3, ankle[1]+1.4, 1.0 if near else 0.82)


def draw_torso(a: Art, dx=0.0, dy=0.0, lean=0.0):
    def q(x, y):
        # Slight forward lean: shoulder line shifts toward the rifle.
        return (x + dx + lean*(34.0-y)*0.12, y + dy)
    body = [q(23.0, 30.5), q(29.0, 28.9), q(38.0, 30.6), q(41.3, 35.0),
            q(39.8, 44.8), q(35.1, 48.2), q(26.1, 46.6), q(21.8, 39.8)]
    a.polygon(body, RED, INK, 2.2)
    # Bright painted shoulder/chest panels and dark side shading.
    a.polygon([q(24.0, 32.0), q(29.1, 30.3), q(31.2, 34.3),
               q(28.6, 38.6), q(24.0, 37.7)], RED_LIGHT, None, 0)
    a.polygon([q(37.2, 32.5), q(40.1, 35.8), q(39.2, 43.4),
               q(35.9, 45.4), q(35.8, 38.5)], RED_DARK, None, 0)
    # Collar, chest panels and stylized gold insignia.
    a.polygon([q(28.0, 29.7), q(34.3, 30.0), q(32.1, 33.6), q(28.7, 33.4)], RED_DEEP, INK, 0.8)
    a.line([q(26.3, 32.5), q(30.1, 35.1), q(34.9, 32.1)], GOLD, 1.0)
    a.roundrect((25.0+dx, 36.0+dy, 29.2+dx, 40.3+dy), 0.8, RED_DARK, INK, 0.9)
    a.line([(25.9+dx, 36.8+dy), (28.2+dx, 36.8+dy)], RED_HI, 0.7)
    a.roundrect((32.0+dx, 35.6+dy, 36.5+dx, 40.2+dy), 0.8, RED, INK, 0.9)
    a.line([(32.8+dx, 36.5+dy), (35.6+dx, 36.5+dy)], RED_HI, 0.7)
    # Waist belt, pouches, and bright buckle.
    a.polygon([q(23.5, 42.1), q(39.9, 42.8), q(39.3, 46.3),
               q(26.1, 45.0), q(23.7, 44.5)], BOOT, INK, 1.2)
    a.roundrect((30.3+dx, 42.8+dy, 34.6+dx, 45.7+dy), 0.55, GOLD, INK, 0.9)
    a.roundrect((24.0+dx, 40.4+dy, 27.7+dx, 43.2+dy), 0.7, OLIVE, INK, 0.8)
    a.roundrect((36.0+dx, 40.6+dy, 39.0+dx, 43.5+dy), 0.7, OLIVE_DARK, INK, 0.8)
    a.line([(24.7+dx, 41.3+dy), (26.8+dx, 41.3+dy)], OLIVE_LIGHT, 0.7)
    a.star(37.6+dx, 32.8+dy, 1.6, 0.7, GOLD_LIGHT, INK, 0.5, 4)
    # Hand-painted seam/highlight strokes.
    a.line([q(27.2, 32.1), q(28.8, 33.6)], RED_HI, 0.75)
    a.line([q(37.8, 38.6), q(37.1, 41.0)], (255, 123, 93, 255), 0.65)


def draw_helmet(a: Art, dx=0.0, dy=0.0, detached=False, tilt=0.0):
    # Rounded red helmet with a little raised crown, broad brim and simple badge.
    cx = 31.1 + dx
    cy = 15.2 + dy
    if detached:
        cx, cy = dx, dy
    cap = [(cx-12.5, cy+1.7), (cx-11.5, cy-4.5-tilt), (cx-7.1, cy-8.3-tilt),
           (cx-1.0, cy-9.6-tilt), (cx+6.6, cy-7.3-tilt), (cx+11.5, cy-2.5),
           (cx+12.6, cy+2.3), (cx+8.5, cy+4.0), (cx-8.7, cy+3.5)]
    a.polygon(cap, RED_LIGHT, INK, 1.9)
    a.polygon([(cx-8.3, cy-4.7-tilt), (cx-2.2, cy-7.4-tilt),
               (cx+5.4, cy-5.9-tilt), (cx+9.0, cy-2.0),
               (cx+3.5, cy-1.0), (cx-6.0, cy-1.2)], RED_HI, None, 0)
    a.roundrect((cx-14.2, cy+0.3, cx+14.0, cy+4.7), 1.6, RED_DARK, INK, 1.7)
    a.line([(cx-10.6, cy+3.8), (cx+10.4, cy+4.1)], RED_LIGHT, 0.8)
    a.roundrect((cx-2.6, cy-4.8, cx+2.9, cy+0.1), 1.0, GOLD, INK, 0.7)
    a.star(cx+0.1, cy-2.2, 1.7, 0.75, GOLD_LIGHT, INK, 0.45, 5)
    # Tiny rivets make the helmet feel hand-built rather than generic.
    a.ellipse((cx-9.7, cy+1.5, cx-8.5, cy+2.8), GOLD_LIGHT, INK, 0.45)
    a.ellipse((cx+8.5, cy+1.5, cx+9.7, cy+2.8), GOLD_LIGHT, INK, 0.45)


def draw_head(a: Art, dx=0.0, dy=0.0, helmet=True):
    # Oversized, friendly three-quarter head.
    a.ellipse((21.5+dx, 14.0+dy, 40.6+dx, 33.3+dy), SKIN, INK, 2.0)
    a.ellipse((21.7+dx, 23.0+dy, 27.0+dx, 29.0+dy), SKIN_SHADOW, INK, 1.1)
    a.ellipse((23.0+dx, 22.6+dy, 26.1+dx, 27.0+dy), SKIN_LIGHT, None, 0)
    # Helmet sits over the forehead; eyes and expression are painted on top.
    if helmet:
        draw_helmet(a, dx, dy)
    else:
        # A tousled hair tuft is visible once the helmet has fallen off.
        a.polygon([(23.0+dx, 18.6+dy), (24.5+dx, 14.4+dy),
                   (28.4+dx, 15.8+dy), (30.0+dx, 13.6+dy),
                   (33.1+dx, 16.0+dy), (37.3+dx, 15.4+dy),
                   (38.8+dx, 20.0+dy)], (99, 54, 47, 255), INK, 1.0)
    # Large bright eyes, small nose and confident friendly grin.
    a.ellipse((27.0+dx, 22.0+dy, 31.4+dx, 27.0+dy), (255, 250, 226, 255), INK, 0.8)
    a.ellipse((33.2+dx, 21.5+dy, 37.3+dx, 26.7+dy), (255, 250, 226, 255), INK, 0.8)
    a.ellipse((29.0+dx, 23.6+dy, 31.0+dx, 26.4+dy), INK, None, 0)
    a.ellipse((35.0+dx, 23.0+dy, 36.9+dx, 26.1+dy), INK, None, 0)
    a.ellipse((29.1+dx, 23.7+dy, 29.9+dx, 24.6+dy), (255, 255, 255, 255), None, 0)
    a.ellipse((35.1+dx, 23.2+dy, 35.8+dx, 24.1+dy), (255, 255, 255, 255), None, 0)
    a.line([(26.9+dx, 20.8+dy), (30.7+dx, 20.3+dy)], INK, 1.1)
    a.line([(33.0+dx, 20.4+dy), (36.7+dx, 20.8+dy)], INK, 1.1)
    a.ellipse((38.0+dx, 26.1+dy, 40.1+dx, 28.8+dy), SKIN_LIGHT, INK, 0.7)
    a.line([(32.5+dx, 29.5+dy), (34.4+dx, 30.1+dy), (36.7+dx, 29.6+dy)], INK, 0.85)
    a.ellipse((25.7+dx, 28.0+dy, 28.0+dx, 29.5+dy), (245, 132, 102, 150), None, 0)
    # Chin strap is on top of the cheek but below the helmet rim.
    if helmet:
        a.line([(23.0+dx, 19.1+dy), (23.1+dx, 28.2+dy), (26.0+dx, 31.4+dy)], INK, 0.9)
        a.ellipse((24.7+dx, 29.5+dy, 26.0+dx, 30.8+dy), GOLD, INK, 0.45)


def draw_carried_rifle(a: Art, dx=0.0, dy=0.0, sway=0.0):
    """Compact friendly-looking carbine, carried diagonally at low ready."""
    # The slight angle change with the gait gives the hands something to swing with.
    y0 = dy + sway
    # Wooden butt and receiver.
    a.polygon([(28.7+dx, 36.0+y0), (31.0+dx, 34.1+y0),
               (35.5+dx, 34.7+y0), (36.5+dx, 37.3+y0),
               (32.7+dx, 39.1+y0), (29.1+dx, 38.5+y0)], WOOD, INK, 1.5)
    a.polygon([(34.0+dx, 33.3+y0), (41.9+dx, 31.1+y0),
               (46.2+dx, 32.4+y0), (45.4+dx, 36.4+y0),
               (38.4+dx, 38.2+y0), (34.7+dx, 36.7+y0)], METAL_DARK, INK, 1.6)
    a.polygon([(36.2+dx, 33.7+y0), (41.3+dx, 32.3+y0),
               (44.4+dx, 33.2+y0), (43.7+dx, 34.6+y0),
               (37.1+dx, 36.2+y0)], METAL, None, 0)
    a.line([(44.3+dx, 32.6+y0), (55.2+dx, 29.6+y0)], INK, 4.2)
    a.line([(44.4+dx, 32.4+y0), (55.2+dx, 29.6+y0)], METAL, 2.0)
    a.line([(48.6+dx, 31.1+y0), (52.3+dx, 30.1+y0)], METAL_LIGHT, 0.7)
    a.roundrect((53.3+dx, 28.8+y0, 56.0+dx, 30.7+y0), 0.5, METAL_DARK, INK, 0.7)
    # Front sight, rail, handguard, magazine and grip.
    a.roundrect((39.0+dx, 30.1+y0, 41.0+dx, 31.9+y0), 0.45, METAL, INK, 0.7)
    a.polygon([(40.0+dx, 36.4+y0), (43.4+dx, 35.6+y0),
               (44.5+dx, 41.6+y0), (41.1+dx, 42.3+y0)], METAL_DARK, INK, 1.0)
    a.line([(41.0+dx, 37.3+y0), (43.2+dx, 41.0+y0)], METAL, 0.65)
    a.polygon([(36.2+dx, 36.9+y0), (38.4+dx, 36.3+y0),
               (38.4+dx, 40.4+y0), (36.5+dx, 41.0+y0)], WOOD, INK, 1.0)
    a.line([(35.2+dx, 32.4+y0), (43.9+dx, 30.0+y0)], INK, 1.4)
    a.line([(35.6+dx, 32.1+y0), (43.9+dx, 29.8+y0)], METAL_LIGHT, 0.55)
    a.line([(33.0+dx, 40.0+y0), (35.2+dx, 41.0+y0)], GOLD, 0.75)


def draw_carried_arms(a: Art, dx=0.0, dy=0.0, swing=0.0, gun_sway=0.0):
    # Far arm swings freely; near hand keeps the carbine low and follows the gait.
    far = [(24.0+dx, 33.5+dy), (20.3+dx+swing*0.85, 37.2+dy),
           (21.8+dx+swing*1.35, 41.8+dy)]
    a.outlined_limb(far, RED_DARK, 7.0, 4.8)
    a.ellipse((far[-1][0]-2.3, far[-1][1]-2.1, far[-1][0]+2.2, far[-1][1]+2.0),
              (88, 57, 51, 255), INK, 1.0)
    a.line([(far[-1][0]-0.5, far[-1][1]-0.8), (far[-1][0]+0.6, far[-1][1]+0.1)], GOLD, 0.55)
    # Near arm reaches the grip; a small swing makes the held weapon bounce naturally.
    near = [(37.4+dx, 33.3+dy), (40.7+dx+swing*0.35, 35.6+dy),
            (41.4+dx+gun_sway, 35.0+dy+gun_sway*0.35)]
    a.outlined_limb(near, RED_LIGHT, 7.0, 4.8)
    a.ellipse((near[-1][0]-2.3, near[-1][1]-1.9, near[-1][0]+2.0, near[-1][1]+1.9),
              (92, 59, 51, 255), INK, 1.0)
    # Support hand reaches the fore-end, with fingers indicated by two ink strokes.
    support = (36.2+dx, 34.3+dy+gun_sway*0.45)
    a.ellipse((support[0]-2.2, support[1]-1.8, support[0]+2.0, support[1]+1.7),
              (91, 58, 51, 255), INK, 0.9)
    a.line([(support[0]-0.8, support[1]-0.2), (support[0]+0.7, support[1]-0.5)],
           SKIN_LIGHT, 0.45)


def draw_aim_rifle(a: Art, dx=0.0, dy=0.0, recoil=0.0):
    """Rifle raised to aim; recoil shifts the whole weapon left a few pixels."""
    r = recoil
    a.polygon([(31.2-r, 30.0+dy), (34.2-r, 28.4+dy), (39.2-r, 29.0+dy),
               (39.4-r, 32.4+dy), (35.2-r, 33.8+dy), (32.0-r, 32.3+dy)], WOOD, INK, 1.5)
    a.polygon([(37.4-r, 27.4+dy), (45.3-r, 27.0+dy), (48.0-r, 29.0+dy),
               (47.3-r, 33.0+dy), (40.0-r, 34.2+dy), (36.8-r, 31.4+dy)], METAL_DARK, INK, 1.7)
    a.polygon([(39.0-r, 28.4+dy), (44.8-r, 28.0+dy), (46.2-r, 29.0+dy),
               (45.6-r, 30.4+dy), (39.3-r, 31.5+dy)], METAL, None, 0)
    a.line([(46.0-r, 29.0+dy), (58.0-r, 28.0+dy)], INK, 4.0)
    a.line([(46.2-r, 28.7+dy), (58.0-r, 28.0+dy)], METAL_LIGHT, 1.75)
    a.roundrect((56.4-r, 27.0+dy, 59.0-r, 29.2+dy), 0.4, METAL_DARK, INK, 0.7)
    a.roundrect((39.7-r, 25.5+dy, 42.0-r, 27.7+dy), 0.5, METAL, INK, 0.7)
    a.line([(40.2-r, 26.0+dy), (41.5-r, 26.0+dy)], METAL_LIGHT, 0.5)
    a.polygon([(41.0-r, 33.0+dy), (44.6-r, 32.6+dy),
               (45.0-r, 38.3+dy), (41.8-r, 39.2+dy)], METAL_DARK, INK, 1.0)
    a.polygon([(36.9-r, 32.4+dy), (39.2-r, 32.1+dy),
               (39.5-r, 36.8+dy), (37.3-r, 37.5+dy)], WOOD, INK, 1.0)
    a.line([(38.2-r, 27.4+dy), (46.8-r, 26.8+dy)], INK, 1.35)
    a.line([(38.5-r, 27.1+dy), (46.8-r, 26.5+dy)], METAL_LIGHT, 0.55)


def draw_aim_arms(a: Art, dx=0.0, dy=0.0, recoil=0.0):
    # Both red sleeves brace the rifle; hands are layered on top of the receiver.
    far = [(24.5+dx, 33.6+dy), (30.0+dx, 35.5+dy), (38.0-recoil, 31.7+dy)]
    near = [(37.8+dx, 33.1+dy), (42.0-recoil, 34.0+dy), (44.5-recoil, 30.8+dy)]
    a.outlined_limb(far, RED_DARK, 7.2, 4.8)
    a.outlined_limb(near, RED_LIGHT, 7.2, 4.8)
    a.ellipse((36.2-recoil, 30.4+dy, 40.0-recoil, 33.4+dy), (91, 58, 50, 255), INK, 1.0)
    a.ellipse((42.6-recoil, 29.4+dy, 46.3-recoil, 32.4+dy), (91, 58, 50, 255), INK, 1.0)
    a.line([(37.1-recoil, 31.5+dy), (38.6-recoil, 31.0+dy)], SKIN_LIGHT, 0.5)


def draw_standing(a: Art, *, phase=0.0, stride=0.0, lift=0.0, arm_swing=0.0,
                  bob=0.0, lean=0.0, mode="carry", recoil=0.0, flinch=0.0,
                  helmet=True, draw_weapon=True):
    dx = lean
    dy = bob
    draw_shadow(a, 32.0, 57.0, 12.2 + abs(stride)*0.22, 70)
    # Legs alternate, with the near leg slightly larger to reinforce the three-quarter view.
    leg0 = math.sin(phase)
    leg1 = math.sin(phase + math.pi)
    lift0 = max(0.0, math.cos(phase)) * lift
    lift1 = max(0.0, math.cos(phase + math.pi)) * lift
    draw_leg(a, (28.2+lean*0.15, 44.2+bob*0.24), leg0*stride, lift0, False, flinch*0.5)
    draw_leg(a, (35.0+lean*0.16, 44.5+bob*0.24), leg1*stride, lift1, True, -flinch*0.4)
    draw_backpack(a, dx*0.65, dy*0.75)
    draw_torso(a, dx, dy, lean=lean*0.7)
    if mode == "aim":
        draw_aim_arms(a, dx, dy, recoil)
        if draw_weapon:
            draw_aim_rifle(a, dx, dy, recoil)
    else:
        swing = arm_swing + flinch*2.1
        draw_carried_arms(a, dx, dy, swing, math.sin(phase)*0.7)
        if draw_weapon:
            draw_carried_rifle(a, dx, dy, math.sin(phase)*0.65)
    draw_head(a, dx*0.86, dy*0.68, helmet=helmet)


def draw_muzzle_flash(a: Art, x, y, power=1.0):
    # Short, angular, cheerful flash; never a blurred glow.
    scale = 0.72 + 0.28*power
    pts = [(x-1.0, y-2.2*scale), (x+1.2, y-1.3*scale),
           (x+4.4*scale, y-2.4*scale), (x+2.9*scale, y+0.1),
           (x+4.6*scale, y+1.6*scale), (x+1.2, y+1.2*scale),
           (x-0.8, y+3.0*scale), (x-1.1, y+0.7), (x-3.1*scale, y-0.2)]
    a.polygon(pts, GOLD, INK, 0.7)
    a.polygon([(x-0.1, y-1.1*scale), (x+2.2*scale, y-0.5),
               (x+1.1*scale, y+0.4), (x+2.4*scale, y+1.0),
               (x-0.1, y+1.5*scale)], (255, 249, 207, 255), None, 0)


def draw_smoke(a: Art, x, y, strength=1.0):
    col = (204, 210, 211, int(210*strength))
    light = (239, 235, 221, int(190*strength))
    r = 1.6 + 0.8*strength
    a.ellipse((x-r, y-r*0.55, x+r, y+r*0.55), col, INK, 0.45)
    a.ellipse((x-1.3, y-2.1, x+1.2, y+0.2), light, None, 0)
    a.ellipse((x+1.2, y-1.7, x+3.4, y+0.3), col, INK, 0.4)


def draw_shell(a: Art, x, y, angle=0.0, alpha=255):
    # Tiny brass cartridge rendered as a clean tapered capsule.
    ca, sa = math.cos(angle), math.sin(angle)
    pts = []
    for ox, oy in [(-2.0,-0.8),(1.5,-0.8),(2.2,0.0),(1.5,0.8),(-2.0,0.8)]:
        pts.append((x+ox*ca-oy*sa, y+ox*sa+oy*ca))
    color = (255, 201, 69, alpha)
    a.polygon(pts, color, INK, 0.55)
    a.line([(x-0.8*ca, y-0.8*sa), (x+0.7*ca, y-0.8*sa)], GOLD_LIGHT, 0.45)


def draw_falling_helmet(a: Art, cx, cy, angle=0.0):
    # Detached helmet is deliberately simplified so the silhouette stays readable.
    a.ellipse((cx-7.0, cy-4.8, cx+7.0, cy+3.2), (104, 31, 43, 255), INK, 1.4)
    a.polygon([(cx-6.0, cy-0.5), (cx-4.2, cy-4.4), (cx, cy-5.3-angle),
               (cx+4.8, cy-3.5), (cx+6.0, cy+0.1)], RED_LIGHT, INK, 1.3)
    a.line([(cx-5.0, cy-0.1), (cx+5.1, cy+0.4)], GOLD, 0.6)


def draw_prone(a: Art, stage=0):
    """Last three death poses: a gentle, non-graphic collapse and resting pose."""
    draw_shadow(a, 32.0, 57.0, 17.0, 80)
    if stage == 0:
        # Still tumbling diagonally; helmet and rifle are both airborne.
        draw_backpack(a, 5.0, 7.5)
        a.ellipse((17.5, 36.8, 31.6, 50.5), SKIN, INK, 1.8)
        a.ellipse((22.1, 41.0, 30.2, 47.2), SKIN_LIGHT, None, 0)
        a.ellipse((24.3, 40.6, 26.9, 43.9), (255, 250, 226, 255), INK, 0.6)
        a.ellipse((25.2, 41.6, 26.4, 43.5), INK, None, 0)
        a.line([(24.0, 46.0), (26.3, 46.5), (28.0, 45.7)], INK, 0.7)
        a.polygon([(27.5, 40.5), (32.0, 38.0), (39.0, 42.0), (42.0, 48.4),
                   (36.0, 53.4), (28.4, 50.5), (24.2, 46.0)], RED, INK, 1.8)
        a.polygon([(29.0, 41.0), (33.0, 39.7), (35.0, 42.4), (32.0, 45.0)], RED_LIGHT, None, 0)
        # Belt and chest details survive the tumble.
        a.line([(28.0, 49.5), (38.0, 50.6)], BOOT, 2.4)
        a.roundrect((32.0, 49.1, 35.2, 51.5), 0.4, GOLD, INK, 0.6)
        # Arms thrown outward.
        a.outlined_limb([(29.0, 42.5), (25.0, 39.5), (21.5, 40.0)], RED_DARK, 6.0, 3.9)
        a.ellipse((19.7, 38.3, 23.4, 41.7), (91, 58, 51, 255), INK, 0.8)
        a.outlined_limb([(38.0, 44.0), (43.0, 40.3), (45.2, 37.5)], RED_LIGHT, 6.0, 3.8)
        a.ellipse((43.6, 35.8, 47.2, 39.1), (91, 58, 51, 255), INK, 0.8)
        # Bent legs and boots.
        a.outlined_limb([(37.0, 49.5), (42.0, 51.3), (47.0, 51.6)], RED_DARK, 6.4, 4.1)
        a.outlined_limb([(40.0, 50.5), (44.0, 48.7), (48.5, 49.2)], RED, 6.0, 3.8)
        draw_boot(a, 49.0, 52.0)
        draw_boot(a, 49.2, 49.5)
        draw_falling_helmet(a, 40.3, 30.0, 0.3)
        draw_carried_rifle(a, dx=7.5, dy=8.4, sway=-1.0)
        # A few dust puffs, no blood or violent detail.
        a.ellipse((11.0, 52.3, 16.0, 56.0), (194, 165, 121, 120), None, 0)
        a.ellipse((46.5, 53.0, 51.0, 56.5), (194, 165, 121, 105), None, 0)
    else:
        # Final still pose: head to the left, backpack behind, boots to the right.
        draw_backpack(a, 0.0, 8.0)
        a.ellipse((14.5, 39.0, 28.5, 52.0), SKIN, INK, 1.7)
        a.ellipse((17.1, 40.8, 26.7, 48.8), SKIN_LIGHT, None, 0)
        a.ellipse((22.0, 41.8, 24.7, 45.1), (255, 250, 226, 255), INK, 0.6)
        a.ellipse((23.2, 42.6, 24.4, 44.4), INK, None, 0)
        a.line([(21.0, 48.4), (23.3, 48.7), (25.0, 48.0)], INK, 0.7)
        # Jacket on its back; the red front remains visible to read as the same unit.
        a.polygon([(23.5, 42.0), (29.5, 39.8), (37.6, 41.7), (43.0, 46.4),
                   (42.0, 52.6), (34.5, 55.2), (26.2, 52.7), (22.1, 48.1)], RED, INK, 1.8)
        a.polygon([(27.0, 42.0), (31.8, 41.0), (34.0, 43.7), (30.2, 46.0)], RED_LIGHT, None, 0)
        a.line([(27.0, 50.2), (40.4, 51.5)], BOOT, 2.5)
        a.roundrect((32.2, 49.9, 35.1, 52.3), 0.4, GOLD, INK, 0.6)
        a.roundrect((28.5, 45.0, 32.1, 48.2), 0.5, RED_DARK, INK, 0.8)
        a.roundrect((35.3, 44.7, 38.8, 48.0), 0.5, RED, INK, 0.8)
        # One arm rests above the body, the other beside it.
        a.outlined_limb([(28.0, 43.7), (24.0, 40.4), (21.3, 40.5)], RED_DARK, 6.1, 4.0)
        a.ellipse((19.4, 39.0, 22.7, 42.2), (91, 58, 51, 255), INK, 0.8)
        a.outlined_limb([(37.2, 44.2), (40.4, 47.0), (43.3, 48.0)], RED_LIGHT, 6.1, 4.0)
        a.ellipse((41.8, 46.6, 45.1, 49.6), (91, 58, 51, 255), INK, 0.8)
        # Two short trouser legs with visible soles.
        a.outlined_limb([(38.5, 51.0), (44.2, 52.5), (49.8, 53.2)], RED_DARK, 6.5, 4.1)
        a.outlined_limb([(36.2, 52.0), (42.0, 54.0), (48.2, 55.0)], RED, 6.2, 3.9)
        draw_boot(a, 51.2, 53.5)
        draw_boot(a, 49.2, 55.2)
        # Fallen helmet and dropped carbine remain beside the soldier.
        draw_falling_helmet(a, 11.0, 54.0, 0.0)
        draw_carried_rifle(a, dx=4.0, dy=18.0, sway=0.0)
        # A tiny settled dust mark fades into the transparent ground.
        a.ellipse((10.0, 55.2, 18.0, 58.0), (194, 165, 121, 74), None, 0)


def render_frame(animation: str, index: int, count: int) -> Image.Image:
    a = Art()
    if animation == "idle":
        phase = math.tau * index / count
        draw_standing(a, phase=phase, stride=0.28, lift=0.1,
                      arm_swing=0.3*math.sin(phase), bob=0.45*math.sin(phase),
                      lean=0.35*math.sin(phase), mode="carry")
    elif animation == "walk":
        phase = math.tau * index / 7.0
        bob = -0.75 + 0.85*math.cos(2.0*phase)
        draw_standing(a, phase=phase, stride=4.0, lift=1.9,
                      arm_swing=2.1*math.sin(phase), bob=bob,
                      lean=0.25*math.sin(phase), mode="carry")
    elif animation == "run":
        phase = math.tau * index / count
        bob = -1.2 + 1.7*math.cos(2.0*phase)
        draw_standing(a, phase=phase, stride=6.5, lift=4.0,
                      arm_swing=3.4*math.sin(phase), bob=bob,
                      lean=1.5 + 0.5*math.sin(phase), mode="carry")
    elif animation == "shoot":
        # Ready → aim → bright muzzle flash/recoil → smoke → settle.
        recoil = {0: 0.0, 1: 0.0, 2: 1.0, 3: 2.1, 4: 1.0, 5: 0.0}[index]
        bob = {0: 0.0, 1: -0.2, 2: 0.4, 3: 1.0, 4: 0.45, 5: 0.0}[index]
        draw_standing(a, phase=0.0, stride=0.15, lift=0.0, bob=bob,
                      lean=0.7, mode="aim", recoil=recoil)
        if index in (2, 3):
            draw_muzzle_flash(a, 58.1-recoil, 28.1+bob, 1.0 if index == 2 else 0.66)
        if index in (2, 3, 4):
            shell_points = {2:(43.0, 27.4, -0.65), 3:(46.0, 24.5, -0.25),
                            4:(49.0, 25.5, 0.25)}
            draw_shell(a, *shell_points[index])
        if index in (3, 4, 5):
            strength = {3:0.62, 4:0.82, 5:0.34}[index]
            draw_smoke(a, 59.0-recoil + (index-3)*0.8, 27.8-(index-3)*1.0, strength)
    elif animation == "death":
        if index == 0:
            draw_standing(a, phase=0.4, stride=0.9, lift=0.6,
                          arm_swing=-1.2, bob=0.0, lean=-0.4,
                          mode="carry", flinch=0.3)
        elif index == 1:
            draw_standing(a, phase=1.2, stride=1.4, lift=0.8,
                          arm_swing=-1.8, bob=0.7, lean=-1.8,
                          mode="carry", flinch=0.85, helmet=False)
            # Helmet pops up and the weapon starts to slip from the hand.
            draw_falling_helmet(a, 40.2, 9.0, 0.35)
            draw_carried_rifle(a, dx=2.0, dy=-2.8, sway=-2.0)
        elif index == 2:
            draw_prone(a, stage=0)
        else:
            draw_prone(a, stage=1)
    else:
        raise ValueError(f"Unknown animation: {animation}")
    return a.finish()


def build_sheet(animation: str, count: int, fps: int, loop: bool) -> dict:
    frames = [render_frame(animation, i, count) for i in range(count)]
    if animation == "walk":
        # User requested the first and last walk frames to match exactly.
        frames[-1] = frames[0].copy()
    sheet = Image.new("RGBA", (FRAME*count, FRAME), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        sheet.alpha_composite(frame, (FRAME*i, 0))
    filename = f"soldier_{animation}.png"
    sheet.save(OUT / filename, optimize=True)
    return {
        "file": filename,
        "animation": animation,
        "frames": count,
        "frameWidth": FRAME,
        "frameHeight": FRAME,
        "sheetWidth": FRAME*count,
        "sheetHeight": FRAME,
        "layout": "horizontal",
        "alpha": True,
        "fps": fps,
        "loop": loop,
        "notes": {
            "walk": "Frame 8 duplicates frame 1 for the requested seam.",
            "shoot": "One-shot; frames 3-4 show muzzle flash, then shell casing and smoke.",
            "death": "One-shot; hold frame 6 after it finishes.",
            "idle": "Breathing/sway loop.",
            "run": "Loop using frame order 1-8; do not duplicate endpoints.",
        }.get(animation, ""),
    }


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    spec = [
        ("idle", 4, 5, True),
        ("walk", 8, 9, True),
        ("run", 8, 12, True),
        ("shoot", 6, 10, False),
        ("death", 6, 8, False),
    ]
    sheets = [build_sheet(*entry) for entry in spec]
    manifest = {
        "pack": "cartoon_red_enemy_soldier",
        "version": 1,
        "description": "Original chibi tower-defense enemy infantryman; side three-quarter view facing right.",
        "character": {
            "teamColor": "bright red",
            "features": ["oversized friendly face", "red helmet", "small olive backpack", "carbine", "thick dark outlines"],
            "styleNote": "Original colorful hand-painted tower-defense cartoon; not a reproduction of any named game character.",
        },
        "frame": {"width": FRAME, "height": FRAME, "format": "PNG RGBA", "background": "transparent"},
        "sheets": sheets,
        "construct3": {
            "import": "Import each PNG as a sprite and set the animation frame size to 64x64.",
            "frameOrder": "Frames are arranged left-to-right, starting at zero in the image editor.",
            "holdAfterOneShot": {"soldier_shoot.png": "last frame optional", "soldier_death.png": "last frame"},
        },
    }
    (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Generated {len(sheets)} sprite sheets in {OUT}")


if __name__ == "__main__":
    main()
