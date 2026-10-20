#!/usr/bin/env python3
"""Generate an original cartoon enemy-Jeep sprite pack for tower defense.

Requires Pillow: python3 -m pip install Pillow
Run: python3 tools/generate_cartoon_jeep_sprites.py
Output: assets/enemy_jeep_cartoon/ (five transparent horizontal PNG strips).
"""
from __future__ import annotations

import json
import math
from pathlib import Path
from typing import Sequence

from PIL import Image, ImageDraw

FRAME = 64
SCALE = 4
OUT = Path(__file__).resolve().parents[1] / "assets" / "enemy_jeep_cartoon"

# Shared visual language with the chibi infantry pack: saturated red, slate metal,
# warm highlights and a heavy plum-charcoal ink outline.
INK = (43, 35, 45, 255)
INK_SOFT = (67, 54, 63, 255)
RED = (207, 48, 58, 255)
RED_LIGHT = (244, 83, 72, 255)
RED_HI = (255, 143, 101, 255)
RED_DARK = (135, 35, 49, 255)
RED_DEEP = (92, 33, 43, 255)
CHARCOAL = (58, 63, 73, 255)
SLATE = (91, 100, 112, 255)
SLATE_LIGHT = (157, 171, 181, 255)
METAL = (122, 137, 146, 255)
METAL_HI = (215, 224, 218, 255)
RUBBER = (40, 42, 49, 255)
RUBBER_HI = (84, 87, 91, 255)
GLASS = (100, 157, 169, 255)
GLASS_HI = (193, 226, 217, 255)
GOLD = (255, 192, 65, 255)
GOLD_HI = (255, 231, 133, 255)
FIRE_RED = (220, 65, 40, 255)
FIRE_ORANGE = (255, 123, 42, 255)
FIRE_YELLOW = (255, 207, 73, 255)
SMOKE_DARK = (48, 45, 52, 245)
SMOKE_MID = (81, 78, 84, 230)


def px(v: float) -> int:
    return int(round(v * SCALE))


class Art:
    """Simple vector painter, supersampled then reduced to a crisp 64px frame."""

    def __init__(self) -> None:
        self.image = Image.new("RGBA", (FRAME*SCALE, FRAME*SCALE), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.image)

    @staticmethod
    def p(point):
        return (px(point[0]), px(point[1]))

    @staticmethod
    def b(box):
        return tuple(px(v) for v in box)

    def ellipse(self, box, fill, outline=INK, width=1.7):
        self.d.ellipse(self.b(box), fill=fill, outline=outline,
                       width=max(1, px(width)) if outline is not None else 1)

    def roundrect(self, box, radius, fill, outline=INK, width=1.7):
        self.d.rounded_rectangle(self.b(box), radius=max(1, px(radius)), fill=fill,
                                 outline=outline,
                                 width=max(1, px(width)) if outline is not None else 1)

    def polygon(self, points: Sequence[tuple[float, float]], fill,
                outline=INK, width=1.7):
        coords = [self.p(p) for p in points]
        self.d.polygon(coords, fill=fill)
        if outline is not None and width > 0:
            self.d.line(coords + [coords[0]], fill=outline, width=max(1, px(width)), joint="curve")

    def line(self, points: Sequence[tuple[float, float]], fill, width=1.35, round_caps=True):
        coords = [self.p(p) for p in points]
        w = max(1, px(width))
        self.d.line(coords, fill=fill, width=w, joint="curve")
        if round_caps:
            r = max(1, w // 2)
            for x, y in (coords[0], coords[-1]):
                self.d.ellipse((x-r, y-r, x+r, y+r), fill=fill)

    def finish(self) -> Image.Image:
        return self.image.resize((FRAME, FRAME), Image.Resampling.LANCZOS)


def blend(a: tuple[int, int, int, int], b: tuple[int, int, int, int], t: float):
    t = max(0.0, min(1.0, t))
    return tuple(round(a[i]*(1-t)+b[i]*t) for i in range(4))


def draw_smoke_puff(a: Art, cx, cy, rx, ry, strength=1.0, dark=False):
    base = SMOKE_DARK if dark else (141, 148, 151, 190)
    edge = INK if strength > 0.38 else None
    color = (base[0], base[1], base[2], int(base[3]*strength))
    a.ellipse((cx-rx, cy-ry, cx+rx, cy+ry), color, edge, 0.8 if edge else 0)
    if not dark and strength > 0.42:
        a.ellipse((cx-rx*0.45, cy-ry*0.72, cx+rx*0.15, cy-ry*0.1),
                  (226, 226, 211, int(110*strength)), None, 0)


def draw_dust(a: Art, frame, speed=1.0, dy=0.0):
    # Dust trails stay sharply drawn; no blurred pixels are used.
    phase = math.tau * frame / 8.0
    if speed < 1.5:
        for x, shift in ((19.0, 0.0), (45.0, 0.55)):
            y = 58.0 + dy + 1.5*math.sin(phase + shift)
            s = 0.85 + 0.30*(0.5+0.5*math.sin(phase+shift))
            draw_smoke_puff(a, x-1.8*math.sin(phase+shift), y, 2.5*s, 1.65*s,
                            0.78+0.16*math.sin(phase+shift), dark=False)
    else:
        for j, x in enumerate((19.0, 32.0, 45.0)):
            drift = (frame*1.25 + j*3.3) % 8.0
            y = 56.2 + drift*0.72 + dy
            strength = 0.86 - drift*0.035
            draw_smoke_puff(a, x + math.sin(phase+j)*1.6, y,
                            3.0 + (j%2)*0.6, 2.0, strength, dark=False)
        # Crisp speed dashes, rather than motion blur, preserve the pixel edges.
        for x in (7.0, 56.0):
            for j in range(2):
                y = 30.0 + j*5.0 + 1.4*math.sin(phase+j)
                a.line([(x, y), (x+(-2.0 if x>32 else 2.0), y+1.8)],
                       (210, 214, 203, 185), 0.9)


def draw_wheel(a: Art, cx, cy, phase=0.0, damaged=False, fast=False, dy=0.0):
    cy += dy
    rubber = (31, 32, 39, 255) if damaged else RUBBER
    tread = (89, 87, 90, 255) if damaged else RUBBER_HI
    rim = (76, 75, 79, 255) if damaged else SLATE_LIGHT
    redhub = (91, 48, 51, 255) if damaged else RED_LIGHT
    # Squat, oversized tire with separate sidewall, rotating tread bars and hub.
    a.roundrect((cx-5.3, cy-7.6, cx+5.3, cy+7.6), 3.3, rubber, INK, 1.8)
    a.roundrect((cx-4.0, cy-6.3, cx+4.0, cy+6.3), 2.4, (59, 61, 68, 255) if not damaged else rubber,
                (25, 26, 33, 255), 0.85)
    phase_px = (phase/(math.tau))*12.2
    for k in range(5):
        yy = cy-6.4 + ((k*3.05+phase_px) % 12.2)
        a.line([(cx-3.4, yy), (cx+3.4, yy)], tread, 0.85)
    # A bold hubcap and four tiny lug dots make each wheel readable at game scale.
    a.ellipse((cx-2.75, cy-3.1, cx+2.75, cy+3.1), rim, INK, 0.9)
    a.ellipse((cx-1.55, cy-1.8, cx+1.55, cy+1.8), redhub, INK, 0.65)
    for dx, yy in ((-1.55,0),(1.55,0),(0,-1.85),(0,1.85)):
        a.ellipse((cx+dx-0.35, cy+yy-0.35, cx+dx+0.35, cy+yy+0.35), GOLD_HI if not damaged else METAL, None, 0)
    if fast and not damaged:
        # Three hard-edged highlights imply a faster tread spin, without blurring.
        for off in (-3.2, 0.0, 3.2):
            yy = cy + off + 1.0*math.sin(phase*2.0+off)
            a.line([(cx-4.3, yy), (cx-2.8, yy)], (214, 218, 207, 220), 0.6)


def draw_bumper(a: Art, y, front=True, damaged=False, dy=0.0):
    y += dy
    col = (43, 44, 51, 255) if damaged else CHARCOAL
    a.roundrect((17.3, y-2.4, 46.7, y+2.5), 1.5, col, INK, 1.6)
    a.line([(20.0, y-0.9), (44.0, y-0.9)], SLATE_LIGHT if not damaged else (88,83,83,255), 0.75)
    for x in (20.0, 44.0):
        a.ellipse((x-1.0, y-1.0, x+1.0, y+1.0), GOLD if front and not damaged else RED_LIGHT, INK, 0.55)
    if not front:
        for x in (23.0, 41.0):
            a.ellipse((x-1.0, y-0.9, x+1.0, y+0.9), RED_LIGHT if not damaged else FIRE_RED, INK, 0.45)


def draw_machine_gun(a: Art, phase=0.0, recoil=0.0, damaged=False, aim=0.0):
    """Rear-mounted open-top gun; in the top view it points toward the hood."""
    ring = (65, 70, 79, 255) if not damaged else (44, 43, 49, 255)
    mount = (106, 117, 125, 255) if not damaged else (61, 56, 60, 255)
    metal = (111, 122, 133, 255) if not damaged else (68, 65, 68, 255)
    y0 = recoil
    # Swivel ring is at the rear of the open cabin.
    a.ellipse((26.0, 37.0, 38.0, 49.0), ring, INK, 1.65)
    a.ellipse((28.2, 39.1, 35.8, 46.8), mount, INK, 1.0)
    a.ellipse((30.0, 40.7, 34.0, 44.9), (177, 187, 184, 255) if not damaged else (80,75,74,255), INK, 0.7)
    # Receiver and barrel sit above the ring, leaving the open cabin visible around it.
    a.roundrect((28.0+aim, 27.8+y0, 36.0+aim, 40.8+y0), 1.5, metal, INK, 1.5)
    a.polygon([(28.9+aim, 29.0+y0), (35.0+aim, 29.0+y0),
               (34.5+aim, 34.5+y0), (29.4+aim, 34.5+y0)], SLATE, None, 0)
    a.line([(31.8+aim, 30.5+y0), (32.0+aim, 17.6+y0)], INK, 4.1)
    a.line([(32.0+aim, 30.3+y0), (32.0+aim, 17.8+y0)], METAL_HI if not damaged else (120,112,108,255), 1.8)
    a.roundrect((30.1+aim, 17.4+y0, 33.9+aim, 20.2+y0), 0.55,
                (66, 70, 79, 255) if not damaged else (42,39,43,255), INK, 0.8)
    # Rear grip, trigger loop, and a small ammo box.
    a.roundrect((35.0+aim, 31.6+y0, 38.0+aim, 36.2+y0), 0.6, CHARCOAL if not damaged else (48,45,49,255), INK, 0.8)
    a.line([(35.2+aim, 37.0+y0), (36.5+aim, 39.1+y0)], INK, 1.45)
    a.roundrect((26.3+aim, 32.2+y0, 29.4+aim, 36.8+y0), 0.6,
                GOLD if not damaged else (91,72,53,255), INK, 0.7)
    if not damaged:
        for i in range(3):
            y = 33.0 + i*1.1 + 0.18*math.sin(phase+i)
            a.line([(26.8+aim, y), (28.9+aim, y)], GOLD_HI, 0.38)
    else:
        a.line([(32.2+aim, 23.0), (35.0+aim, 21.6)], FIRE_ORANGE, 0.75)


def draw_jeep(a: Art, *, phase=0.0, dy=0.0, speed=0.0, damaged=False,
              recoil=0.0, gun_phase=0.0, dust_frame=None):
    """Draw the same top-down rounded jeep for all five animation states."""
    if dust_frame is not None:
        draw_dust(a, dust_frame, speed=max(1.0, speed), dy=dy)

    # Four individually animated, exaggerated tires sit under the rounded fenders.
    for cx, cy, offset in ((14.4,20.5,0.0),(49.6,20.5,0.4),
                           (14.4,44.1,0.7),(49.6,44.1,1.0)):
        draw_wheel(a, cx, cy, phase+offset, damaged=damaged,
                   fast=speed>1.5, dy=dy)

    chassis = (43, 46, 54, 255) if not damaged else (37, 37, 43, 255)
    body = RED if not damaged else (75, 49, 52, 255)
    body_hi = RED_LIGHT if not damaged else (105, 64, 61, 255)
    body_dark = RED_DARK if not damaged else (52, 43, 49, 255)
    a.roundrect((13.4, 7.4+dy, 50.6, 56.5+dy), 8.2, chassis, INK, 2.3)
    a.roundrect((15.1, 9.2+dy, 48.9, 54.6+dy), 7.2, body_dark, INK, 1.65)
    a.roundrect((16.2, 10.2+dy, 47.8, 53.6+dy), 6.2, body, INK, 1.65)
    # Fender lips frame the outside edges of the tires.
    for x in (15.2, 48.8):
        a.roundrect((x-1.0, 15.5+dy, x+1.4, 29.4+dy), 1.0, body_hi, INK, 0.9)
        a.roundrect((x-1.0, 36.0+dy, x+1.4, 49.2+dy), 1.0, body_hi, INK, 0.9)
    # Front hood and its raised, bright red center panel.
    hood = (20.0, 12.6+dy, 44.0, 25.2+dy)
    a.roundrect(hood, 3.2, body_hi, INK, 1.45)
    a.roundrect((22.1, 14.3+dy, 41.9, 22.9+dy), 2.1,
                RED if not damaged else (60,48,52,255), body_dark, 1.0)
    if not damaged:
        a.polygon([(23.0, 15.0+dy), (27.0, 14.8+dy), (28.8, 21.7+dy),
                   (23.4, 21.2+dy)], RED_HI, None, 0)
        a.line([(30.2, 15.0+dy), (31.7, 21.6+dy)], (255, 135, 101, 255), 0.75)
        a.line([(34.0, 15.0+dy), (35.1, 21.7+dy)], RED_DARK, 0.7)
    else:
        # Soot and cracked hood panels appear only after the blast.
        a.polygon([(20.5, 15.1+dy), (28.4, 13.8+dy), (31.4, 18.0+dy),
                   (26.5, 23.0+dy), (20.4, 21.0+dy)], (35,35,40,245), None, 0)
        a.line([(31.5, 14.0+dy), (28.8, 18.2+dy), (33.8, 22.7+dy)], (27,27,32,255), 0.8)
        a.ellipse((36.2, 16.3+dy, 40.3, 20.0+dy), (237, 99, 39, 180), None, 0)
    # Front grille, headlights and chunky bumper (front is the top of the frame).
    a.roundrect((26.8, 9.0+dy, 37.2, 13.3+dy), 0.7, CHARCOAL if not damaged else (38,38,43,255), INK, 1.0)
    for x in (28.1,30.0,32.0,34.0,35.9):
        a.line([(x, 9.7+dy), (x, 12.5+dy)], SLATE_LIGHT if not damaged else (73,69,70,255), 0.52)
    for x in (20.4,43.6):
        lamp = GOLD_HI if not damaged else (83,67,55,255)
        a.ellipse((x-2.0, 11.6+dy, x+2.0, 15.2+dy), lamp, INK, 0.8)
        if not damaged:
            a.ellipse((x-0.8, 12.2+dy, x+0.7, 13.6+dy), (255,255,221,255), None, 0)
    draw_bumper(a, 9.6, front=True, damaged=damaged, dy=dy)
    draw_bumper(a, 55.3, front=False, damaged=damaged, dy=dy)

    # Windshield and open-top cabin: there is no roof over the seats or rear gun.
    glass = (46, 65, 75, 255) if damaged else GLASS
    a.roundrect((20.2, 23.7+dy, 43.8, 28.5+dy), 1.2, glass, INK, 1.35)
    if not damaged:
        a.polygon([(22.0,24.2+dy),(29.0,24.2+dy),(27.7,27.4+dy),(22.5,27.4+dy)], GLASS_HI, None, 0)
    else:
        a.line([(22.2,24.1+dy),(30.0,27.0+dy),(27.5,24.0+dy)], INK, 0.8)
        a.line([(38.4,24.5+dy),(34.4,27.7+dy)], INK, 0.7)
    a.roundrect((19.7, 27.3+dy, 44.3, 44.7+dy), 3.0,
                (44,48,55,255) if not damaged else (37,37,42,255), INK, 1.6)
    # Open cabin floor, seats and red side rails.
    a.roundrect((22.0, 29.0+dy, 42.0, 42.8+dy), 2.2,
                (62,70,77,255) if not damaged else (47,43,47,255), None, 0)
    a.roundrect((22.7, 29.1+dy, 29.5, 35.8+dy), 1.8,
                (119,94,72,255) if not damaged else (73,59,53,255), INK, 0.9)
    a.roundrect((34.5, 29.1+dy, 41.3, 35.8+dy), 1.8,
                (119,94,72,255) if not damaged else (73,59,53,255), INK, 0.9)
    a.line([(24.0,30.1+dy),(28.1,30.1+dy)], (190,151,101,255), 0.6)
    a.line([(35.7,30.1+dy),(39.9,30.1+dy)], (190,151,101,255), 0.6)
    # Distinct open roll bars and side rails, painted red with metal joints.
    a.line([(20.4,28.0+dy),(20.4,42.1+dy)], INK, 2.0)
    a.line([(20.4,28.0+dy),(20.4,42.1+dy)], RED_LIGHT if not damaged else (93,61,63,255), 1.05)
    a.line([(43.6,28.0+dy),(43.6,42.1+dy)], INK, 2.0)
    a.line([(43.6,28.0+dy),(43.6,42.1+dy)], RED_LIGHT if not damaged else (93,61,63,255), 1.05)
    # Red side doors / handles add broad color blocks to the dark chassis.
    for x in (17.0,44.2):
        a.roundrect((x, 30.1+dy, x+2.7, 39.4+dy), 1.0,
                    body_hi, INK, 0.9)
        a.line([(x+0.6, 32.0+dy), (x+2.0, 32.0+dy)], GOLD if not damaged else (90,68,59,255), 0.55)
    # Mounted gun is drawn last so it reads clearly above the open cabin.
    draw_machine_gun(a, phase=gun_phase, recoil=recoil, damaged=damaged)
    # Small rear service lights, exhaust tips, bolts and highlight marks.
    for x in (20.5,43.5):
        a.ellipse((x-1.2, 50.4+dy, x+1.2, 52.6+dy), RED_LIGHT if not damaged else FIRE_RED, INK, 0.6)
    for x in (18.0,46.0):
        a.roundrect((x-1.0, 47.0+dy, x+1.0, 50.4+dy), 0.5,
                    (69,72,79,255) if not damaged else (44,42,45,255), INK, 0.55)
    if not damaged:
        a.line([(17.5, 17.0+dy), (18.2, 21.2+dy)], RED_HI, 0.7)
        a.line([(46.5, 17.0+dy), (45.8, 21.2+dy)], RED_HI, 0.7)
        a.ellipse((18.8, 43.1+dy, 20.0, 44.2+dy), GOLD_HI, None, 0)
        a.ellipse((44.0, 43.1+dy, 45.2, 44.2+dy), GOLD_HI, None, 0)


def draw_shell(a: Art, x, y, angle=0.0):
    ca, sa = math.cos(angle), math.sin(angle)
    pts=[]
    for ox,oy in [(-1.8,-0.7),(1.1,-0.7),(2.0,0.0),(1.1,0.7),(-1.8,0.7)]:
        pts.append((x+ox*ca-oy*sa,y+ox*sa+oy*ca))
    a.polygon(pts, GOLD, INK, 0.55)
    a.line([(x-0.6*ca,y-0.6*sa),(x+0.7*ca,y-0.6*sa)], GOLD_HI, 0.4)


def draw_muzzle_flash(a: Art, x, y, strength=1.0):
    # Tall, upward-pointing cartoon flash with a clear silhouette at 64px.
    r=0.9+0.28*strength
    pts=[(x-1.8*r,y+1.0),(x-3.0*r,y-0.2),(x-1.7*r,y-1.6*r),
         (x-2.1*r,y-5.1*r),(x-0.1*r,y-3.2*r),(x+1.2*r,y-7.2*r),
         (x+2.0*r,y-3.4*r),(x+4.0*r,y-4.3*r),(x+2.8*r,y-0.4),
         (x+1.3*r,y+1.2)]
    a.polygon(pts,FIRE_ORANGE,INK,0.75)
    a.polygon([(x-0.8*r,y),(x-0.4*r,y-3.4*r),(x+0.5*r,y-2.0*r),
               (x+1.6*r,y-4.5*r),(x+1.3*r,y-0.5)],FIRE_YELLOW,None,0)
    a.ellipse((x-0.75*r,y-1.7*r,x+0.8*r,y+0.2),GOLD_HI,None,0)


def draw_small_fire(a: Art, cx, base_y, scale=1.0):
    pts=[(cx-4.6*scale,base_y),(cx-3.5*scale,base_y-4.2*scale),
         (cx-1.4*scale,base_y-2.8*scale),(cx,base_y-7.1*scale),
         (cx+1.3*scale,base_y-3.2*scale),(cx+3.5*scale,base_y-5.0*scale),
         (cx+4.3*scale,base_y),(cx+2.5*scale,base_y+1.2)]
    a.polygon(pts,FIRE_RED,INK,0.85)
    a.polygon([(cx-2.0*scale,base_y),(cx-0.6*scale,base_y-3.5*scale),
               (cx+0.2*scale,base_y-2.0*scale),(cx+1.8*scale,base_y-4.0*scale),
               (cx+2.0*scale,base_y)],FIRE_ORANGE,None,0)
    a.ellipse((cx-0.9*scale,base_y-1.8*scale,cx+1.0*scale,base_y+0.4),
              FIRE_YELLOW,None,0)


def draw_explosion_burst(a: Art, cx=32.0, cy=33.0, radius=22.0, power=1.0):
    # A bold comic-book starburst, clean-edged and transparent around the blast.
    pts=[]
    for i in range(24):
        ang=-math.pi/2 + i*math.tau/24
        r=radius if i%2==0 else radius*(0.58 if i%4==0 else 0.69)
        pts.append((cx+math.cos(ang)*r,cy+math.sin(ang)*r))
    a.polygon(pts,(255,112,38,232),INK,1.25)
    inner=[]
    for i in range(16):
        ang=-math.pi/2+i*math.tau/16
        r=radius*0.56 if i%2==0 else radius*0.41
        inner.append((cx+math.cos(ang)*r,cy+math.sin(ang)*r))
    a.polygon(inner,(255,198,63,228),INK,0.85)
    a.ellipse((cx-radius*0.30,cy-radius*0.31,cx+radius*0.30,cy+radius*0.31),
              (255,238,156,240),None,0)
    for ang in (-2.3,-0.4,0.7,2.8):
        x=cx+math.cos(ang)*radius*0.77
        y=cy+math.sin(ang)*radius*0.77
        a.ellipse((x-1.4*power,y-1.4*power,x+1.4*power,y+1.4*power),
                  FIRE_YELLOW,INK,0.55)


def draw_smoke_column(a: Art, frame, low=False):
    # Layered outlined puffs rise from the hood, darkest close to the wreck.
    if low:
        centers=[(31.5,25.0,7.0,5.7),(28.0,20.2,6.4,6.3),(35.0,16.0,6.4,6.0),(31.2,11.5,5.1,4.5)]
        lift=0.5*math.sin(frame*0.7)
    else:
        centers=[(31.5,25.0,7.3,6.1),(27.2,19.2,6.6,6.1),(35.6,14.7,6.6,6.6),(30.2,9.6,5.6,4.9)]
        lift=0.7*math.sin(frame*0.8)
    for i,(cx,cy,rx,ry) in enumerate(centers):
        y=cy-lift-i*0.25
        fill=SMOKE_DARK if i<2 else SMOKE_MID
        a.ellipse((cx-rx,y-ry,cx+rx,y+ry),fill,INK,0.85)
        a.ellipse((cx-rx*0.54,y-ry*0.70,cx+rx*0.12,y-ry*0.14),
                  (125,119,123,175) if i>0 else (91,86,91,180),None,0)
    if not low:
        # Detached charcoal flecks rising with the smoke.
        for x,y,r in ((22,13,1.0),(41,8,1.1),(39,24,0.8)):
            a.ellipse((x-r,y-r,x+r,y+r),(42,39,46,215),None,0)


def render_turn(frame):
    # Four key poses ease from forward/up to a right-facing heading.
    angles=(0.0,30.0,60.0,90.0)
    angle=angles[frame]
    phase=math.tau*frame/4.0
    bob=0.55*math.sin(phase)
    a=Art()
    draw_jeep(a,phase=phase,dy=bob,speed=1.0,gun_phase=phase)
    # Rotating the same high-resolution drawing keeps the jeep identical through the turn.
    a.image=a.image.rotate(-angle, resample=Image.Resampling.BICUBIC,
                           center=(FRAME*SCALE/2,FRAME*SCALE/2), expand=False)
    return a.finish()


def render_frame(animation, index, count):
    a=Art()
    if animation == "forward":
        phase=math.tau*index/count
        dy=0.65*(1.0-math.cos(phase))
        draw_jeep(a,phase=phase,dy=dy,speed=1.0,gun_phase=0.0,dust_frame=index)
    elif animation == "fast":
        phase=math.tau*index/count
        wheel_phase=phase*2.0
        dy=1.1*(1.0-math.cos(phase*2.0))
        draw_jeep(a,phase=wheel_phase,dy=dy,speed=2.0,gun_phase=0.0,dust_frame=index)
        # A small body wobble is indicated by crisp suspension lines beside the chassis.
        if index%2==0:
            for x in (11.3,52.7):
                a.line([(x,31+dy),(x+(1.2 if x<32 else -1.2),35+dy)],
                       (226,211,172,190),0.75)
    elif animation == "shoot":
        recoil={0:0.0,1:0.0,2:1.25,3:1.8,4:0.75,5:0.0}[index]
        dy={0:0.0,1:0.0,2:0.35,3:0.75,4:0.25,5:0.0}[index]
        draw_jeep(a,phase=0.0,dy=dy,speed=0.0,recoil=recoil,gun_phase=0.0)
        muzzle_y=17.8+recoil
        if index in (2,3):
            draw_muzzle_flash(a,32.0,muzzle_y,1.0 if index==2 else 0.68)
        shells={2:(37.8,29.0,-0.8),3:(40.4,26.5,-0.4),4:(43.0,27.5,0.12)}
        if index in shells:
            draw_shell(a,*shells[index])
        if index in (3,4,5):
            s={3:0.72,4:0.86,5:0.42}[index]
            draw_smoke_puff(a,32.0+(index-3)*0.8,16.5-(index-3)*1.2,2.2,1.8,s,dark=False)
            draw_smoke_puff(a,33.0+(index-3)*0.6,14.5-(index-3)*0.7,1.5,1.2,s*0.72,dark=False)
    elif animation == "turn":
        return render_turn(index)
    elif animation == "explosion":
        if index == 0:
            draw_jeep(a,phase=0.0,speed=0.0,gun_phase=0.0)
        elif index == 1:
            draw_jeep(a,phase=0.0,speed=0.0,gun_phase=0.0)
            draw_small_fire(a,30.0,18.0,1.05)
        elif index == 2:
            draw_jeep(a,phase=0.2,dy=0.6,speed=0.0,damaged=False,gun_phase=0.2)
            draw_explosion_burst(a,32.0,32.0,26.0,1.0)
            draw_small_fire(a,30.0,17.4,1.05)
        elif index == 3:
            draw_jeep(a,phase=0.0,dy=0.5,speed=0.0,damaged=True,gun_phase=0.0)
            draw_smoke_column(a,index,low=False)
            draw_small_fire(a,30.0,19.0,0.55)
        elif index == 4:
            draw_jeep(a,phase=0.0,dy=0.2,speed=0.0,damaged=True,gun_phase=0.0)
            draw_smoke_column(a,index,low=False)
            draw_small_fire(a,25.5,22.0,0.8)
            draw_small_fire(a,39.0,24.0,1.0)
        else:
            draw_jeep(a,phase=0.0,dy=0.3,speed=0.0,damaged=True,gun_phase=0.0)
            draw_smoke_column(a,index,low=True)
            # A couple of tiny embers, no continuing large fire.
            for x,y in ((24.5,21.0),(40.0,27.0)):
                a.ellipse((x-0.8,y-0.8,x+0.8,y+0.8),FIRE_ORANGE,None,0)
    else:
        raise ValueError(f"Unknown animation: {animation}")
    return a.finish()


def build_sheet(animation, count, fps, loop, note):
    frames=[render_frame(animation,i,count) for i in range(count)]
    sheet=Image.new("RGBA",(FRAME*count,FRAME),(0,0,0,0))
    for i,frame in enumerate(frames):
        sheet.alpha_composite(frame,(FRAME*i,0))
    filename=f"jeep_{animation}.png"
    sheet.save(OUT/filename,optimize=True)
    return {
        "file":filename,
        "animation":animation,
        "frames":count,
        "frameWidth":FRAME,
        "frameHeight":FRAME,
        "sheetWidth":FRAME*count,
        "sheetHeight":FRAME,
        "layout":"horizontal",
        "alpha":True,
        "fps":fps,
        "loop":loop,
        "notes":note,
    }


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    defs=[
        ("forward",8,10,True,"Top-down forward travel; rotating treads, gentle suspension bounce and small rear dust puffs."),
        ("fast",8,14,True,"Faster tread cycle, stronger bounce and larger dust trail; speed marks are crisp, not blurred."),
        ("shoot",6,12,False,"Stationary mounted-gun burst; muzzle flash, ejecting cartridge, small recoil and smoke."),
        ("turn",4,10,False,"One-shot clockwise 90-degree turn, 0/30/60/90-degree key poses with wheels turning."),
        ("explosion",6,8,False,"Intact jeep, hood fire, comic blast, rising smoke, burning wreck, charcoal remains; hold frame 6."),
    ]
    sheets=[build_sheet(name,count,fps,loop,note) for name,count,fps,loop,note in defs]
    manifest={
        "pack":"cartoon_red_enemy_jeep",
        "version":1,
        "description":"Original cute top-down enemy jeep with an open cabin and rear-mounted machine gun.",
        "vehicle":{
            "teamColors":["bright red","dark slate gray"],
            "features":["rounded chibi body","four oversized treaded tires with visible hubs","open-top cabin","rear-mounted machine gun"],
            "orientation":"front points to the top edge in forward, fast, shoot, and explosion sheets",
            "styleNote":"Original colorful tower-defense cartoon; not a reproduction of any named game vehicle.",
        },
        "frame":{"width":FRAME,"height":FRAME,"format":"PNG RGBA","background":"transparent"},
        "sheets":sheets,
        "construct3":{
            "import":"Import each PNG as a sprite and set animation frame size to 64x64.",
            "frameOrder":"Frames are arranged left-to-right, starting at zero in the image editor.",
            "oneShot":"Turn and explosion are one-shot; hold the last explosion frame.",
            "wheelBlur":"Fast wheels use crisp rotating tread marks and speed dashes, not motion-blurred pixels.",
        },
    }
    (OUT/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(f"Generated {len(sheets)} sprite sheets in {OUT}")


if __name__ == "__main__":
    main()
