#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""مولد شیت «دویدن» نیروها (دشمنان بازی) — ۸ فریم افقی، پس‌زمینهٔ شفاف.

برای هر نیرو یک شیت افقی با ۸ فریم یکتا (بدون تکرار فریم آخر) و چهار جهت
ساخته می‌شود؛ همان قراردادی که بستهٔ سرباز نظامی دارد: پیکسل‌پرفکت، بدون
نرم‌سازی لبه، محور فریم روی نقطهٔ تماس پا با زمین، فاصلهٔ حداقل دو پیکسل تا
لبهٔ فریم.

    python3 tools/gen_enemy_sprites.py                  # ساخت کل بسته
    python3 tools/gen_enemy_sprites.py --only grunt     # فقط یک نیرو
    python3 tools/gen_enemy_sprites.py --check          # بررسی بستهٔ ساخته‌شده
    python3 tools/gen_enemy_sprites.py --debug grunt left 3 --zoom 6

بومِ پیکسلی، ریگ چهارجهته و موادِ رنگِ پیکسلی از مولد سرباز نظامی
(tools/gen_soldier_sprites.py) بازاستفاده می‌شوند تا سبک و ابزار یکسان بماند؛
شخصیت‌ها اما در همین فایل مدل شده‌اند.

اندازه‌های فریم: هر نیرو با بومِ بزرگ اندازه‌گیری و سپس فریمِ نهایی گرد
می‌شود (عرض زوج، خط زمین در ۵ پیکسل بالای لبهٔ پایین، مثل شیت‌های راه رفتنِ
موجود در assets/sprites/units/<id>_walk.png).
"""

from __future__ import annotations

import argparse
import importlib.util
import json
import math
import os
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    print("Pillow لازم است:  pip install pillow", file=sys.stderr)
    raise

TAU = math.tau
TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)

MIN_SIDE = 64      # کمینهٔ اندازهٔ فریم (استاندارد بستهٔ سرباز)
PAD_X = 3          # حاشیهٔ افقی هر فریم
PAD_TOP = 2        # حاشیهٔ بالای محتوا
PAD_BOTTOM = 5     # فاصلهٔ خط زمین تا لبهٔ پایین (هم‌اندازهٔ شیت‌های walk موجود)
BIG_W, BIG_H = 320, 170
BIG_CX, BIG_GROUND = 160.0, 130.0
FPS_RUN = 14
FRAMES = 8


# ------------------------------------------------------------------ بازاستفاده از مولد سرباز


def _load_soldier_module():
    path = os.path.join(TOOLS, "gen_soldier_sprites.py")
    spec = importlib.util.spec_from_file_location("dezhban_soldier_sprites", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


sol = _load_soldier_module()
Canvas = sol.Canvas
flat, lit, camo, solid, ao_edge, rgba, ramp = (
    sol.flat, sol.lit, sol.camo, sol.solid, sol.ao_edge, sol.rgba, sol.ramp)

# پالت‌های مشترک با بستهٔ سرباز نظامی (شخصیت و سبک یکسان)
INK = sol.INK
OLIVE, OLIVE_L, VEST, KHAKI, SKIN, GUN = sol.OLIVE, sol.OLIVE_L, sol.VEST, sol.KHAKI, sol.SKIN, sol.GUN
BRASS, DUST, SMOKE = sol.BRASS, sol.DUST, sol.SMOKE

# پالت‌های تازهٔ همین بسته
IRON = [(24, 26, 30), (46, 50, 56), (76, 82, 90), (116, 124, 134)]
STEEL = [(32, 36, 42), (60, 66, 74), (94, 102, 112), (140, 150, 162)]
NAVY = [(16, 20, 28), (30, 38, 52), (50, 62, 82), (78, 92, 118)]
RUST = [(48, 18, 14), (86, 32, 22), (126, 52, 32), (168, 78, 46)]
BLOOD = [(70, 16, 20), (112, 28, 32), (156, 46, 46), (196, 84, 72)]
PLUM = [(26, 18, 40), (48, 32, 74), (78, 54, 116), (112, 84, 156)]
ORC = [(38, 52, 26), (62, 84, 38), (92, 120, 54), (128, 156, 78)]
BONE = [(112, 104, 84), (148, 140, 116), (186, 178, 152), (220, 214, 192)]
MAROON = [(38, 24, 20), (64, 38, 30), (96, 58, 42), (134, 86, 60)]
COVER = [(30, 36, 22), (48, 58, 32), (68, 84, 44), (96, 116, 64)]
GLOW_V = (188, 140, 255)
GLOW_R = (255, 120, 70)
GLOW_B = (120, 190, 255)


# ------------------------------------------------------------------ ریگ

class Rig:
    """نقشهٔ مختصات بدن (جلو، پهلو، ارتفاع) به صفحه، با چارچوب دلخواه هر نیرو.

    ساختار همان ریگِ مولد سرباز است، ولی مرکز افقی و خط زمین در هر بوم
    پارامتری‌اند تا اندازهٔ فریم هر نیرو آزاد باشد.
    """

    def __init__(self, direction, frame_w, ground, pitch=0.0, pivot=0.0, sway=0.0,
                 shift=(0.0, 0.0), yaw=0.0, depth=0.62, gun_depth=1.0):
        self.dir = direction
        self.cx = float(frame_w) * 0.5
        self.ground = float(ground)
        self.depth = depth
        self.gun_depth = gun_depth
        self.pitch = math.radians(pitch)
        self.pivot = pivot
        self.sway = sway
        self.shift = shift
        self.yaw = math.radians(yaw)
        self.vertical = direction in ("front", "back")

    def world(self, along, side):
        if self.yaw:
            ca, sa = math.cos(self.yaw), math.sin(self.yaw)
            along, side = along * ca - side * sa, along * sa + side * ca
        if self.dir == "front":
            return (-side, along)
        if self.dir == "back":
            return (side, -along)
        if self.dir == "left":
            return (-along, -side)
        return (along, side)

    def pt(self, along, side=0.0, h=0.0, gun=False):
        if self.pitch:
            a = along + (h - self.pivot) * math.sin(self.pitch)
            z = self.pivot + (h - self.pivot) * math.cos(self.pitch)
        else:
            a, z = along, h
        if gun and self.vertical:
            a *= self.gun_depth
        wx, wy = self.world(a, side)
        return (self.cx + wx + self.sway + self.shift[0],
                self.ground - z + wy * self.depth + self.shift[1])

    def sq(self, side_r, along_r, base):
        if self.vertical:
            return (side_r / base, along_r * self.depth / base)
        return (along_r / base, side_r * self.depth / base)

    def box3(self, canvas, along0, along1, side0, side1, z0, z1, fn_top, fn_side):
        """جعبهٔ محلی: رویه و وجهِ نزدیک به دوربین."""
        top = [self.pt(along0, side0, z1), self.pt(along1, side0, z1),
               self.pt(along1, side1, z1), self.pt(along0, side1, z1)]
        canvas.poly(top, fn_top)
        faces = (
            ((along0, side0), (along0, side1)),
            ((along1, side0), (along1, side1)),
            ((along0, side0), (along1, side0)),
            ((along0, side1), (along1, side1)),
        )
        best, best_y = None, -1e9
        for (a0, s0), (a1, s1) in faces:
            _, wy = self.world((a0 + a1) * 0.5, (s0 + s1) * 0.5)
            if wy * self.depth > best_y:
                best_y, best = wy * self.depth, ((a0, s0), (a1, s1))
        (a0, s0), (a1, s1) = best
        canvas.poly([self.pt(a0, s0, z1), self.pt(a1, s1, z1),
                     self.pt(a1, s1, z0), self.pt(a0, s0, z0)], fn_side)


# ------------------------------------------------------------------ ابزار رسم


def disc(canvas, rig, along, side, h, rx, ry, fn, ang=0.0):
    x, y = rig.pt(along, side, h)
    canvas.ellipse(x, y, rx, ry, fn, ang=ang)


def limb(canvas, rig, a0, s0, h0, a1, s1, h1, hw0, fn, hw1=None, round_ends=(True, True)):
    canvas.cyl(rig.pt(a0, s0, h0), rig.pt(a1, s1, h1), hw0, fn,
               hw1=hw1, round_ends=round_ends)


def rod(canvas, rig, a0, s0, h0, a1, s1, h1, r0, fn, r1=None, squash=(1.0, 1.0)):
    canvas.capsule(rig.pt(a0, s0, h0), rig.pt(a1, s1, h1), r0, r1, fn, squash=squash)


def tone(mat, t):
    """یک تُن از نوار رنگ را به‌صورت رنگِ خام برمی‌گرداند (برای پف‌های خاک)."""
    n = len(mat) - 1
    lo = int(math.floor((t + 1.0) * 0.5 * n))
    return mat[0 if lo < 0 else (n if lo > n else lo)]


def prism(canvas, rig, along0, along1, side_top, side_bot, z_top, z_bot, fn_top, fn_side):
    """حجم باریک‌شونده (دامن، شنل، تنهٔ قوس‌دار).

    مقطع بالا و پایین جدا داده می‌شوند؛ وجه‌ها از دور به نزدیک رسم می‌شوند تا
    وجهِ رو به دوربین روی بقیه بیفتد.
    """
    st0, st1 = side_top
    sb0, sb1 = side_bot
    faces = [
        ((along0, st0, z_top), (along1, st0, z_top), (along1, sb0, z_bot), (along0, sb0, z_bot)),
        ((along0, st1, z_top), (along1, st1, z_top), (along1, sb1, z_bot), (along0, sb1, z_bot)),
        ((along0, st0, z_top), (along0, st1, z_top), (along0, sb1, z_bot), (along0, sb0, z_bot)),
        ((along1, st0, z_top), (along1, st1, z_top), (along1, sb1, z_bot), (along1, sb0, z_bot)),
    ]

    def depth_of(face):
        total = 0.0
        for al, sd, _z in face:
            _, wy = rig.world(al, sd)
            total += wy * rig.depth
        return total / 4.0

    for face in sorted(faces, key=depth_of):
        canvas.poly([rig.pt(al, sd, zz) for (al, sd, zz) in face], fn_side)
    canvas.poly([rig.pt(along0, st0, z_top), rig.pt(along1, st0, z_top),
                 rig.pt(along1, st1, z_top), rig.pt(along0, st1, z_top)], fn_top)


def ground_puff(canvas, rig, along, side, size, alpha=150, color=None):
    """گردِ خاکِ زیر پا (پشتِ پیکر رسم می‌شود)."""
    x, y = rig.pt(along, side, 0.4)
    canvas.dithered_disc(x, y, size * 1.25, max(1.0, size * 0.52),
                         color if color is not None else tone(DUST, -0.35), alpha=alpha)


def dust_trail(canvas, rig, p, spec, count=3):
    """ردِ خاکِ دویدن: دو-سه پفِ محو که با فاز پا جابه‌جا می‌شوند."""
    t = p.get("dust", 0.0)
    if t < 0.05:
        return
    behind = spec.get("dust_behind", -6.0)
    for k in range(count):
        ph = p["ph"] + k * 0.34
        k1 = max(0.0, math.sin(ph * TAU)) * t
        if k1 < 0.05:
            continue
        size = 1.6 + 2.6 * k1
        ground_puff(canvas, rig, behind - k * 2.6 - k1 * 2.0, (k % 2) * 2.4 - 1.2,
                    size, alpha=int(120 * (1.0 - k * 0.22)),
                    color=tone(DUST, -0.1 + 0.25 * k1))


def glow_disc(canvas, rig, along, side, h, r, color, alpha=110, ry=None):
    x, y = rig.pt(along, side, h)
    canvas.ellipse(x, y, r, r * (0.62 if ry is None else ry), solid(color, alpha))


# ------------------------------------------------------------------ حالت‌ها


def run_pose(spec, direction, i, n=FRAMES):
    """حالتِ دویدن: گام بزرگ، زانو بلند، بازو پرتاب می‌شود، بدن بالاپایین می‌رود."""
    R = spec.get("run", {})
    ph = (i % n) / float(n)
    stride = R.get("stride", 12.0)
    lift_h = R.get("lift", 6.5)
    foot_side = spec.get("foot_side", 4.0)
    feet = []
    for k in (0, 1):
        p2 = ph + 0.5 * k
        sw = math.sin(p2 * TAU)
        along = sw * stride
        lift = max(0.0, math.sin((p2 + 0.25) * TAU)) ** 0.82 * lift_h
        side = foot_side * (-1.0 if k == 0 else 1.0) + math.cos(p2 * TAU) * R.get("side_sway", 2.2) * 0.45
        sc = 1.0 - 0.07 * (lift / max(0.001, lift_h))
        feet.append((along, side, lift, sc))
    bob_amp = R.get("bob_amp", 1.7)
    pose = dict(
        ph=ph,
        feet=tuple(feet),
        bob=R.get("bob_base", 1.5) + math.sin(ph * TAU * 2.0 + R.get("bob_phase", 0.7)) * bob_amp,
        sway=math.sin(ph * TAU) * R.get("sway", 1.3),
        lean=R.get("lean", 1.4) + math.sin(ph * TAU * 2.0) * 0.3,
        arm_swing=math.sin(ph * TAU) * R.get("arm", 1.7),
        head_fwd=R.get("head_fwd", -0.5),
        head_loll=math.sin(ph * TAU) * R.get("head_loll", 0.0),
        dust=R.get("dust", 0.6) * (0.30 + 0.70 * max(0.0, math.sin(ph * TAU * 2.0))),
        pitch=R.get("pitch", 0.0) if spec.get("lean_pitch") else 0.0,
        pivot=R.get("pivot", 12.0),
        aura=R.get("aura", 0.0),
        hover=R.get("hover", 0.0) + math.sin(ph * TAU * 2.0 + 1.1) * R.get("hover_amp", 0.0),
    )
    return pose


# ------------------------------------------------------------------ خانوادهٔ انسان‌نما

BODY_DEFAULTS = dict(
    z_boot=3.2, z_knee=8.4, z_hip=15.4, z_belt=16.8, z_chest=21.0, z_shoulder=24.0,
    z_neck=25.2, z_face=28.2, z_dome=31.4, r_helmet=5.5,
    half_shoulder=7.0, half_chest=6.2, half_hip=5.8, foot_side=4.0,
    leg_r=2.9, arm_r=2.6,
)


def body(spec):
    b = dict(BODY_DEFAULTS)
    b.update(spec.get("body", {}))
    return b


def draw_leg(canvas, rig, b, along, side, lift, sc, mat_pant, mat_knee):
    hip = rig.pt(along * 0.16, side * 0.60, b["z_hip"] - 0.6 + lift * 0.32)
    knee = rig.pt(along * 0.60, side * 0.95, b["z_knee"] + lift * 0.68)
    ankle = rig.pt(along * 0.86, side * 1.00, b["z_boot"] + lift + 0.4)
    r = b["leg_r"] * sc
    canvas.cyl(hip, knee, r, mat_pant, hw1=r * 0.86, round_ends=(True, False))
    canvas.cyl(knee, ankle, r * 0.86, mat_pant, hw1=r * 0.74, round_ends=(False, False))
    kp = rig.pt(along * 0.64, side, b["z_knee"] + lift * 0.64)
    canvas.ellipse(kp[0], kp[1], r * 1.10, r * 0.84, mat_knee)


def draw_boot(canvas, rig, b, along, side, lift, sc, mat_top, mat_side, sole=None):
    heel = along - 2.5 * sc
    toe = along + 4.8 * sc
    half = 2.45 * sc
    z0, z1 = lift, lift + b["z_boot"]
    rig.box3(canvas, heel, toe, side - half, side + half, z0, z1, mat_top, mat_side)
    s0 = rig.pt(heel + 0.6, side - half + 0.4, z0 + 0.6)
    s1 = rig.pt(toe - 0.6, side + half - 0.4, z0 + 0.6)
    canvas.capsule(s0, s1, 0.9, 0.9, sole if sole is not None else flat(INK, -0.30),
                   squash=rig.sq(1.0, 6.0, 1.0))
    cap = rig.pt(toe - 1.3 * sc, side, z1 + 0.4)
    canvas.ellipse(cap[0], cap[1], 2.0 * sc, 1.5 * sc, mat_top)


def foot_order(rig, feet):
    if rig.vertical:
        return [0, 1] if rig.dir == "front" else [1, 0]
    return sorted((0, 1), key=lambda i: -feet[i][1] if rig.dir == "left" else feet[i][1])


def draw_torso(canvas, rig, p, b, mat_cloth_top, mat_cloth_side, mat_belt, mat_buckle):
    lean = p["lean"]
    hip = rig.pt(lean, 0.0, b["z_hip"] - 1.2)
    sh = rig.pt(lean - 0.5, 0.0, b["z_shoulder"] - 0.3)
    if rig.vertical:
        w0, w1 = b["half_hip"] * 0.97, b["half_shoulder"] * 0.99
    else:
        w0, w1 = b["half_hip"] * 0.70, b["half_shoulder"] * 0.65
    canvas.cyl(hip, sh, w0, mat_cloth_side, hw1=w1, round_ends=(True, False))
    rig.box3(canvas, lean - 3.2, lean + 2.6, -b["half_shoulder"], b["half_shoulder"],
             b["z_shoulder"] - 2.2, b["z_shoulder"] + 1.2, mat_cloth_top, mat_cloth_side)
    rig.box3(canvas, lean - 3.4, lean + 3.6, -b["half_hip"] - 0.3, b["half_hip"] + 0.3,
             b["z_hip"] - 1.8, b["z_belt"] - 0.4, flat(VEST, -0.55), mat_belt)
    disc(canvas, rig, lean + 4.0, 0.0, b["z_belt"] - 1.5, 1.5, 1.1, mat_buckle)


def draw_head(canvas, rig, p, b, mat_skin, mat_dome, mat_brim, face=True, visor=True,
              crown_mat=None):
    lean = p["lean"] + p.get("head_fwd", 0.0) * 0.5
    n = rig.pt(0.4 + lean, 0.0, b["z_neck"])
    canvas.ellipse(n[0], n[1], 3.1, 2.5, mat_skin)
    disc(canvas, rig, 1.0 + lean, 0.0, b["z_face"], 4.0, 3.6, mat_skin)
    if face and rig.dir == "front":
        disc(canvas, rig, 2.6 + lean, 0.0, b["z_face"] + 0.8, 2.9, 0.85, flat(INK, -0.40))
        disc(canvas, rig, 3.0 + lean, 0.0, b["z_face"] - 2.0, 1.4, 0.6, flat(SKIN, -0.5))
    dry = b["r_helmet"] * 0.74 * (1.0 + 0.40 * math.sin(rig.pitch))
    disc(canvas, rig, lean, 0.0, b["z_dome"], b["r_helmet"], dry, mat_dome,
         ang=p.get("head_loll", 0.0))
    disc(canvas, rig, 2.4 + lean, 0.0, b["z_dome"] - 1.6, 1.7, 1.1, mat_brim)
    if rig.vertical:
        disc(canvas, rig, 1.8 + lean, 0.0, b["z_dome"] - 3.0, b["r_helmet"] + 0.7, 1.6, mat_brim)
    else:
        for sm in (-1.0, 1.0):
            disc(canvas, rig, lean - 0.4, sm * 4.9, b["z_dome"] - 3.0, 2.7, 1.8, mat_brim)
    disc(canvas, rig, lean - 1.6, -1.7, b["z_dome"] + 1.2, b["r_helmet"] - 2.9,
         max(0.9, dry - 1.6), crown_mat if crown_mat is not None else mat_dome_top(b))
    if visor and not rig.vertical:
        for sm in (-1.0, 1.0):
            k0 = rig.pt(0.4 + lean, sm * 3.4, b["z_face"] - 1.4)
            k1 = rig.pt(0.4 + lean, sm * 4.6, b["z_neck"] - 0.6)
            canvas.capsule(k0, k1, 0.8, 0.8, flat(VEST, -0.28), squash=rig.sq(1.0, 2.6, 1.0))


def mat_dome_top(b):
    return lit(OLIVE_L, gain=0.55, bias=0.26, rim=0.0)


def draw_arm(canvas, rig, p, b, sgn, hand_pt, mat_sleeve, full=True, spread=0.0, elbow_fwd=1.2):
    lean = p["lean"]
    swing = p.get("arm_swing", 0.0) + p.get("arm_flail", 0.0) * 0.35
    sh = rig.pt(lean - 0.9, sgn * (b["half_shoulder"] * 0.86 + spread), b["z_shoulder"] - 1.6)
    el = rig.pt(elbow_fwd + lean + swing * (1.0 if sgn > 0 else 0.45),
                sgn * (b["half_shoulder"] + spread),
                b["z_chest"] - 1.4 + swing * 0.5 - p.get("sprawl", 0.0) * 0.30)
    canvas.cyl(sh, el, b["arm_r"], mat_sleeve, hw1=b["arm_r"] * 0.82, round_ends=(True, True))
    if full:
        canvas.cyl(el, hand_pt, b["arm_r"] * 0.82, mat_sleeve, hw1=b["arm_r"] * 0.68,
                   round_ends=(True, True))


def draw_hand(canvas, pt, mat=SKIN):
    canvas.ellipse(pt[0], pt[1], 2.3, 2.0, ao_edge(lit(mat, gain=0.55, bias=0.10, rim=0.20), 0.32, 0.28))


def rifle_pose(rig, p, b, carry=0.0):
    """نقاط تفنگ: در نمای پهلوی، لوله در راستای نگاه؛ در روبه‌رو/پشت، مورب روی سینه."""
    lean = p["lean"]

    def lerp3(a, c, t):
        return tuple(a[i] + (c[i] - a[i]) * t for i in range(3))

    if rig.vertical:
        ready = dict(butt=(lean - 2.4, -3.6, b["z_hip"] + 2.4),
                     grip=(lean + 1.0, -3.4, b["z_hip"] + 0.6),
                     hand=(lean + 7.0, 2.6, b["z_belt"] - 0.4),
                     muzzle=(lean + 10.6, 5.4, b["z_chest"] + 0.6))
        carry_p = dict(butt=(lean - 1.8, -3.4, b["z_shoulder"] - 1.2),
                       grip=(lean + 2.6, -3.4, b["z_shoulder"] - 2.2),
                       hand=(lean + 8.4, -5.6, b["z_chest"] + 0.4),
                       muzzle=(lean + 12.8, -7.0, b["z_chest"] - 1.0))
    else:
        ready = dict(butt=(lean - 3.2, -3.6, b["z_belt"] + 2.0),
                     grip=(lean + 0.8, -3.4, b["z_belt"] + 0.4),
                     hand=(lean + 8.0, -3.6, b["z_belt"] - 1.2),
                     muzzle=(lean + 13.6, -3.8, b["z_knee"] + 3.0))
        carry_p = dict(butt=(lean - 3.0, -3.0, b["z_shoulder"] - 1.0),
                       grip=(lean + 0.8, -2.8, b["z_shoulder"] - 1.8),
                       hand=(lean + 8.8, -2.6, b["z_shoulder"] - 2.2),
                       muzzle=(lean + 14.8, -2.4, b["z_shoulder"] - 2.6))
    pts = {k: lerp3(ready[k], carry_p[k], carry) for k in ready}
    out = {}
    for k, v in pts.items():
        out[k] = rig.pt(v[0], v[1], v[2], gun=True)
    dx, dy = out["muzzle"][0] - out["butt"][0], out["muzzle"][1] - out["butt"][1]
    ln = max(1e-6, math.hypot(dx, dy))
    out["axis"] = (dx / ln, dy / ln)
    out["len"] = ln
    return out


def draw_rifle(canvas, rig, g, mat_gun_side, mat_gun_top, mat_stock, scale=1.0, long=False):
    ux, uy = g["axis"]
    px, py = -uy, ux
    butt, grip, hand, muzzle = g["butt"], g["grip"], g["hand"], g["muzzle"]
    rear = (butt[0] - ux * 2.6 * scale, butt[1] - uy * 2.6 * scale)
    if long:
        k = 1.28
        hand = (butt[0] + (hand[0] - butt[0]) * k, butt[1] + (hand[1] - butt[1]) * k)
        muzzle = (butt[0] + (muzzle[0] - butt[0]) * k, butt[1] + (muzzle[1] - butt[1]) * k)
    canvas.capsule(rear, butt, 1.9 * scale, 2.3 * scale, mat_stock, squash=(1.0, 0.95))
    canvas.capsule(butt, grip, 2.4 * scale, 2.4 * scale, mat_gun_side, squash=(1.0, 0.95))
    mag_a = ((butt[0] + grip[0]) * 0.5 + px * 0.6, (butt[1] + grip[1]) * 0.5 + py * 0.6)
    mag_b = (mag_a[0] + py * 4.4 * scale - ux * 1.2, mag_a[1] - px * 4.4 * scale - uy * 1.2)
    canvas.capsule(mag_a, mag_b, 2.0 * scale, 1.5 * scale, flat(GUN, -0.5), squash=(1.0, 0.9))
    canvas.capsule(grip, hand, 2.2 * scale, 2.0 * scale, mat_gun_top, squash=(1.0, 0.95))
    canvas.capsule(hand, muzzle, 1.15 * scale, 1.05 * scale, mat_gun_side, squash=(1.0, 0.9))
    tip = (muzzle[0] + ux * 1.9 * scale, muzzle[1] + uy * 1.9 * scale)
    canvas.capsule(muzzle, tip, 1.45 * scale, 1.15 * scale, flat(GUN, -0.8), squash=(1.0, 0.9))
    st = (butt[0] + ux * 0.6 * scale + px * 1.4, butt[1] + uy * 0.6 * scale + py * 1.4)
    canvas.capsule(st, (st[0] + ux * 2.4 * scale, st[1] + uy * 2.4 * scale),
                   0.8 * scale, 0.8 * scale, flat(GUN, 0.7))
    return {"grip": grip, "hand": hand, "muzzle": muzzle, "butt": butt}


def draw_humanoid(canvas, rig, p, spec, mats, weapon="rifle", extras=None, carry=0.0,
                  legs=True, head=True):
    """اسکلت مشترک انسان‌نماها؛ هر نیرو فقط مواد، سلاح و افزوده‌ها را عوض می‌کند."""
    b = body(spec)
    feet = p["feet"]
    apply_rig(rig, p)
    dropped = not in_hand(p)
    sprawl = p.get("sprawl", 0.0)

    mat_pant, mat_top, mat_side = mats["pant"], mats["top"], mats["side"]
    if legs:
        for k in foot_order(rig, feet):
            a, s, lift, sc = feet[k]
            draw_leg(canvas, rig, b, a, s, lift, sc, mat_pant, mats["knee"])
        for k in foot_order(rig, feet):
            a, s, lift, sc = feet[k]
            draw_boot(canvas, rig, b, a, s, lift, sc, mats["boot_top"], mats["boot_side"],
                      mats.get("sole"))
        if rig.vertical:
            d0 = rig.pt(-1.4, 0.0, b["z_boot"] + 0.6)
            d1 = rig.pt(-0.6, 0.0, b["z_hip"] - 2.4)
            canvas.capsule(d0, d1, 1.0, 1.0, flat(VEST, -0.95))

    weapon_pts = None
    if weapon == "rifle" and not dropped and rig.dir == "back":
        g = rifle_pose(rig, p, b, carry)
        weapon_pts = draw_rifle(canvas, rig, g, mats["gun_side"], mats["gun_top"],
                                mats["stock"], scale=mats.get("gun_scale", 1.0),
                                long=spec.get("long_gun", False))
        draw_hand(canvas, weapon_pts["grip"])
        draw_hand(canvas, weapon_pts["hand"])
    # سلاحِ رهاشده روی لایهٔ زمین، پیش از پیکر تا بدن رویش بیفتد
    draw_dropped_gun(canvas, rig, p, mats, scale=mats.get("gun_scale", 1.0),
                     long=spec.get("long_gun", False))

    if extras:
        extras(canvas, rig, p, b, "back")

    draw_torso(canvas, rig, p, b, mat_top, mat_side, mats["belt"], mats["buckle"])
    for fn in (spec.get("gear") or ()):
        fn(canvas, rig, p, b, mats, "back" if rig.dir == "back" else "front")

    if weapon == "rifle":
        order = (-1, 1) if rig.vertical else (1, -1)
        if dropped:
            # دست‌ها به پهلو باز می‌شوند و از سلاح جدا می‌مانند
            for sgn in order:
                hand = rig.pt(p["lean"] + 1.4 + sprawl * 0.6,
                              sgn * (b["half_shoulder"] + 2.4 + sprawl * 1.7),
                              b["z_chest"] - 3.6 - sprawl * 0.7)
                draw_arm(canvas, rig, p, b, sgn, hand, mats["sleeve"], full=True,
                         spread=mats.get("arm_spread", 0.0) + sprawl * 0.55)
                draw_hand(canvas, hand)
        elif rig.dir != "back":
            g = rifle_pose(rig, p, b, carry)
            for sgn in order:
                draw_arm(canvas, rig, p, b, sgn, g["hand"] if sgn < 0 else g["grip"],
                         mats["sleeve"], full=True, spread=mats.get("arm_spread", 0.0))
            weapon_pts = draw_rifle(canvas, rig, g, mats["gun_side"], mats["gun_top"],
                                    mats["stock"], scale=mats.get("gun_scale", 1.0),
                                    long=spec.get("long_gun", False))
            draw_hand(canvas, weapon_pts["grip"])
            draw_hand(canvas, weapon_pts["hand"])
    if head:
        draw_head(canvas, rig, p, b, mats["skin"], mats["dome"], mats["brim"],
                  face=mats.get("face", True), crown_mat=mats.get("dome_crown"))

    if extras:
        extras(canvas, rig, p, b, "front")
    return weapon_pts


def finish(body_canvas, rig, spec, dust=True, dust_alpha=150):
    """خط دور + سایهٔ زیر پا؛ خروجی یک فریم کامل."""
    body_canvas.outline()
    out = Canvas(body_canvas.w, body_canvas.h)
    if dust:
        sh = spec.get("shadow", (10.4, 3.6, 40))
        ground_puff(out, rig, 0.0, 0.0, sh[0] * 0.5, alpha=sh[2], color=(14, 16, 13))
    out.draw_over(body_canvas)
    return out


# ------------------------------------------------------------------ نیروها


def _mats(spec, R, T=None, G=None, SK=None, pants=None, sleeve=None):
    """موادِ مشترکِ یک نیرو را از پالت‌های داده‌شده می‌سازد."""
    base = pants or R
    return dict(
        pant=sol.camo(lit(base, gain=0.40, bias=-0.20, rim=0.14), seed=13, amount=12),
        top=sol.camo(lit(R, gain=0.45, bias=0.30, rim=0.12), seed=3, amount=13),
        side=sol.camo(lit(T or R, gain=0.42, bias=-0.05, rim=0.14), seed=3, amount=14),
        sleeve=sol.camo(lit(sleeve or R, gain=0.45, bias=0.22, rim=0.08), seed=5, amount=15),
        knee=lit(G or VEST, gain=0.42, bias=-0.18, rim=0.16),
        boot_top=ao_edge(lit(base, gain=0.40, bias=0.50, rim=0.12), 0.28, 0.20),
        boot_side=lit(base, gain=0.34, bias=-0.24, rim=0.16),
        belt=lit(G or VEST, gain=0.32, bias=-0.20, rim=0.16),
        buckle=lit(BRASS, gain=0.4, bias=-0.35),
        dome=sol.camo(lit(OLIVE_L, gain=0.52, bias=0.26, rim=0.14), seed=9, amount=10),
        dome_crown=lit(OLIVE_L, gain=0.55, bias=0.26, rim=0.0),
        brim=ao_edge(lit(OLIVE, gain=0.40, bias=-0.72, rim=0.18), 0.28, 0.26),
        skin=ao_edge(lit(SK or SKIN, gain=0.55, bias=0.10, rim=0.20), 0.32, 0.28),
        gun_top=lit(GUN, gain=0.50, bias=0.10, rim=0.16),
        gun_side=lit(GUN, gain=0.34, bias=-0.30, rim=0.18),
        stock=lit(VEST, gain=0.35, bias=-0.18),
    )


def _gear_rig(canvas, rig, p, b, mats, kind):
    """جلیقه/کوله‌پشتی؛ kind برابر back یعنی بخشِ پشتی رسم شود."""
    lean = p["lean"]
    if kind == "front" and rig.dir != "back":
        rig.box3(canvas, lean + 1.0, lean + 5.0, -b["half_chest"], b["half_chest"],
                 b["z_hip"] + 2.2, b["z_chest"] + 1.2,
                 ao_edge(lit(VEST, gain=0.45, bias=0.30, rim=0.12), 0.28, 0.20), mats["belt"])
        for sm in (-2.4, 2.4):
            rig.box3(canvas, lean + 4.4, lean + 6.4, sm - 1.9, sm + 1.9,
                     b["z_hip"] + 2.6, b["z_chest"] - 0.2,
                     ao_edge(lit(KHAKI, gain=0.42, bias=0.30, rim=0.14), 0.26, 0.22),
                     lit(KHAKI, gain=0.30, bias=-0.42))
    if kind == "back" and rig.dir == "back":
        rig.box3(canvas, lean - 8.0, lean - 1.6, -b["half_chest"] * 0.86, b["half_chest"] * 0.86,
                 b["z_hip"] + 0.8, b["z_shoulder"] - 0.4,
                 ao_edge(sol.camo(lit(OLIVE_L, gain=0.42, bias=0.10, rim=0.14), seed=5, amount=15),
                         0.28, 0.20),
                 lit(OLIVE, gain=0.32, bias=-0.24, rim=0.16))
        for sm in (-b["half_chest"] - 0.4, b["half_chest"] + 0.4):
            a = rig.pt(lean - 2.4, sm, b["z_shoulder"] + 0.2)
            c = rig.pt(lean - 1.8, sm, b["z_hip"] - 0.6)
            canvas.capsule(a, c, 1.1, 1.1, flat(VEST, -0.9), squash=(1.0, 0.85))


# ------------------------------------------------------------------ حالت مرگ

DEATH_FRAMES = 6
DEATH_AXIS = {        # (افقی، عمودی) جابه‌جایی صفحه وقتی پیکر رو به همان جهت می‌افتد
    "front": (0.0, 1.0),
    "back": (0.0, -1.0),
    "left": (-1.0, 0.35),
    "right": (1.0, 0.35),
}

# شش گامِ افتادن: اصابت → پس‌رفتن → زانو → افتادن → برخورد → آرام گرفتن.
# مقادیر با قدِ هر نیرو مقیاس می‌شوند (سرِ ستون death_pose).
DEATH_STEPS = [
    dict(pitch=4.0, fall=0.5, yaw=0.0, sprawl=0.0, arm_flail=1.1, head_fwd=-1.8, head_loll=-0.12,
         bob=0.4, dust=0.0, gun_drop=0.0,
         feet=((0.6, -1.00, 0.0, 1.00), (0.0, 1.00, 0.0, 1.00))),
    dict(pitch=16.0, fall=1.6, yaw=6.0, sprawl=0.8, arm_flail=1.8, head_fwd=-2.4, head_loll=-0.22,
         bob=-1.6, dust=0.15, gun_drop=0.06,
         feet=((2.4, -1.60, 1.2, 0.98), (-2.6, 1.60, 0.0, 1.00))),
    dict(pitch=38.0, fall=2.8, yaw=14.0, sprawl=1.8, arm_flail=2.4, head_fwd=-2.4, head_loll=-0.30,
         bob=-3.4, dust=0.35, gun_drop=0.24,
         feet=((4.0, -2.20, 2.2, 0.96), (-4.2, 2.00, 0.0, 0.96))),
    dict(pitch=60.0, fall=4.0, yaw=24.0, sprawl=3.0, arm_flail=2.9, head_fwd=1.0, head_loll=-0.30,
         bob=-5.2, dust=0.70, gun_drop=0.55,
         feet=((-3.6, -2.60, 3.2, 0.94), (-7.4, 2.20, 0.6, 0.98))),
    dict(pitch=74.0, fall=5.0, yaw=31.0, sprawl=3.8, arm_flail=3.1, head_fwd=2.4, head_loll=-0.18,
         bob=-6.0, dust=1.00, gun_drop=0.86,
         feet=((-5.0, -3.00, 3.4, 0.92), (-9.4, 2.60, 0.8, 0.96))),
    dict(pitch=78.0, fall=5.4, yaw=34.0, sprawl=4.2, arm_flail=3.3, head_fwd=3.0, head_loll=-0.06,
         bob=-6.4, dust=0.50, gun_drop=1.00,
         feet=((-5.8, -3.20, 3.2, 0.92), (-10.4, 2.80, 0.7, 0.96))),
]


def death_pose(spec, direction, i, n=DEATH_FRAMES):
    """حالتِ گامِ i ام از افتادن؛ پیکر با pitch روی زمین می‌افتد و با yaw می‌چرخد."""
    step = dict(DEATH_STEPS[min(i, len(DEATH_STEPS) - 1)])
    cfg = spec.get("death", {})
    k = body(spec)["z_dome"] / 31.4            # مقیاس قد
    fs = spec.get("foot_side", 4.0) / 4.0
    fall = step.pop("fall") * k
    ax = DEATH_AXIS[direction]
    if direction in ("left", "right"):
        step["yaw"] *= 0.55 * cfg.get("yaw", 1.0)
    else:
        step["yaw"] *= cfg.get("yaw", 1.0)
    prog = i / float(max(1, n - 1))
    feet = tuple((a * k, s * fs, lift * k, sc) for (a, s, lift, sc) in step["feet"])
    return dict(
        death=True,
        progress=prog,
        ph=i / float(n),
        direction=direction,
        fall=fall,
        center_dx=ax[0] * fall,
        center_h=15.0 * k - ax[1] * fall,
        center_z=16.0 * k,
        dust=step["dust"] * cfg.get("dust", 1.0),
        sprawl=step["sprawl"] * cfg.get("sprawl", 1.0),
        arm_flail=step["arm_flail"],
        arm_swing=0.0,
        head_fwd=step["head_fwd"] * cfg.get("head", 1.0),
        head_loll=step["head_loll"],
        pitch=step["pitch"] * cfg.get("pitch", 1.0),
        sink=cfg.get("sink", 0.0) * prog,
        pivot=12.0 * k,
        yaw=step["yaw"],
        bob=step["bob"] * k,
        sway=0.0,
        lean=0.0,
        gun_drop=step["gun_drop"],
        feet=feet,
        shadow=(10.9 + 3.2 * prog, 3.7 + 1.3 * prog, int(50 - 14 * prog)),
        hover=0.0,
        aura=0.0,
    )


def apply_rig(rig, p, base=(0.0, 0.0), use_bob=True, bob_scale=1.0, use_sway=0.30):
    """حالتِ پیکر را روی ریگ می‌نشاند: شیب، چرخش، جابه‌جایی و قفلِ مرکزِ پیکر."""
    rig.pitch = math.radians(p.get("pitch", 0.0))
    rig.pivot = p.get("pivot", 0.0)
    rig.yaw = math.radians(p.get("yaw", 0.0))
    shift = p.get("shift", (0.0, 0.0))
    rig.shift = (base[0] + shift[0] + p.get("sway", 0.0) * use_sway,
                 base[1] + shift[1] + p.get("sink", 0.0)
                 - (p.get("bob", 0.0) * bob_scale if use_bob else 0.0))
    if p.get("center_h") is not None:
        # پیکرِ افتاده: مرکز پیکر روی نقطهٔ دلخواه قاب قفل می‌شود تا در کادر بماند
        px, py = rig.pt(0.0, 0.0, p.get("center_z", 16.0))
        rig.shift = (rig.shift[0] + (rig.cx + p.get("center_dx", 0.0) - px),
                     rig.shift[1] + ((rig.ground - p["center_h"]) - py))


# ---- رها کردن سلاح و تجهیزات هنگام افتادن


def drop_t(p):
    """پیشرفتِ رهاشدنِ سلاح: ۰ در دست، ۱ روی زمین."""
    return max(0.0, min(1.0, (p.get("gun_drop", 0.0) - 0.25) / 0.75))


def in_hand(p):
    """آیا سلاح هنوز در دست است؟"""
    return p.get("gun_drop", 0.0) < 0.45


def draw_dropped_gun(canvas, rig, p, mats, scale=1.0, long=False, side=-3.4):
    """تفنگِ رهاشده: از ارتفاعِ دست پایین می‌آید و روی زمین می‌خوابد."""
    t = drop_t(p)
    if t <= 0.0:
        return
    h = 7.0 * (1.0 - t) + 0.6
    off = side - t * 2.8
    along = 1.0 + t * 3.6
    k = 1.28 if long else 1.0
    stock = rig.pt(along - 8.4 * k, off + 0.4, h, gun=True)
    a = rig.pt(along - 6.0 * k, off, h, gun=True)
    m = rig.pt(along + 11.0 * k, off - 0.8, h, gun=True)
    muzzle = rig.pt(along + 14.0 * k, off - 1.0, h, gun=True)
    canvas.capsule(stock, a, 1.8 * scale, 2.1 * scale, mats["stock"], squash=(1.0, 0.95))
    canvas.capsule(a, m, 2.2 * scale, 1.3 * scale, mats["gun_side"], squash=(1.0, 0.95))
    canvas.capsule(m, muzzle, 1.2 * scale, 1.0 * scale, flat(GUN, -0.75), squash=(1.0, 0.9))
    mag = rig.pt(along + 1.0 * k, off - 1.6, h + 0.4, gun=True)
    canvas.ellipse(mag[0], mag[1], 1.4 * scale, 1.1 * scale, flat(GUN, -0.5))


def draw_dropped_club(canvas, rig, p, side=-9.0):
    """گرزِ غول: دسته و سرِ خاردار که کنارِ پیکر روی زمین می‌افتد."""
    t = drop_t(p)
    if t <= 0.0:
        return
    h = 8.0 * (1.0 - t) + 1.0
    off = side - t * 2.0
    along = 2.0 + t * 5.0
    iron_top = lit(IRON, gain=0.46, bias=0.16, rim=0.14)
    iron_side = lit(IRON, gain=0.30, bias=-0.46, rim=0.16)
    handle_a = rig.pt(along - 9.0, off, h, gun=True)
    handle_b = rig.pt(along + 3.0, off - 0.6, h, gun=True)
    canvas.capsule(handle_a, handle_b, 1.2, 1.0, lit(MAROON, gain=0.5, bias=0.35, rim=0.14))
    head = rig.pt(along + 7.0, off - 1.0, h, gun=True)
    canvas.ellipse(head[0], head[1], 3.4, 3.0, iron_top)
    for k in range(6):
        ra = math.radians(k * 60.0)
        canvas.ellipse(head[0] + math.cos(ra) * 3.2, head[1] + math.sin(ra) * 2.6, 1.1, 0.9,
                       iron_side)


def draw_dropped_staff(canvas, rig, p, glow_fade=1.0, side=-5.6):
    """عصای درمانگر: کنارِ پیکر می‌افتد و چرخ می‌خورد، گویش کم‌نور می‌شود."""
    t = drop_t(p)
    if t <= 0.0:
        return
    h = 16.0 * (1.0 - t) + 1.0
    off = side - t * 3.4
    along = 2.0 + t * 6.0
    wood = lit(MAROON, gain=0.5, bias=0.62, rim=0.12)
    a = rig.pt(along - 6.0, off, h, gun=True)
    b = rig.pt(along + 8.0, off - 1.2 * t, h, gun=True)
    canvas.capsule(a, b, 1.1, 1.0, wood)
    gx, gy = rig.pt(along + 10.4, off - 1.6 * t, h + 0.6, gun=True)
    fade = max(0.0, glow_fade) * (1.0 - 0.75 * t)
    if fade > 0.05:
        canvas.glow(gx, gy, 6.0, GLOW_V, alpha=int(90 * fade), power=1.5)
        canvas.ellipse(gx, gy, 2.2, 2.0, solid(GLOW_V, int(190 * fade)))
        canvas.ellipse(gx - 0.3, gy - 0.4, 0.9, 0.8, solid((245, 236, 255), int(220 * fade)))


def draw_dropped_flag(canvas, rig, p, side=-6.0):
    """پرچمِ فرمانده: میله و پارچه روی زمین می‌خوابند."""
    t = drop_t(p)
    if t <= 0.0:
        return
    h = 11.0 * (1.0 - t) + 1.0
    plate_top = lit(STEEL, gain=0.48, bias=0.28, rim=0.12)
    off = side - t * 2.4
    along = -1.0 + t * 3.0
    a = rig.pt(along - 2.0, off, h, gun=True)
    b = rig.pt(along + 9.0, off - 1.0 * t, h, gun=True)
    canvas.capsule(a, b, 0.7, 0.7, plate_top)
    tip = rig.pt(along + 9.6, off - 1.0 * t, h, gun=True)
    cloth = lit(MAROON, gain=0.46, bias=0.12, rim=0.14)
    canvas.poly([tip, (tip[0] - 6.6, tip[1] + 1.2 + 1.4 * t),
                 (tip[0] - 5.4, tip[1] + 3.6 - 1.0 * t)], solid((158, 40, 34), 225))
    canvas.poly([(tip[0] - 1.6, tip[1] + 1.6), (tip[0] - 5.8, tip[1] + 2.2 + t),
                 (tip[0] - 5.0, tip[1] + 3.8)], cloth)


def draw_dropped_shield(canvas, rig, p):
    """سپر: روی زمین می‌افتد و پهن می‌خوابد."""
    t = drop_t(p)
    if t <= 0.0:
        return
    face_mat = lit(NAVY, gain=0.42, bias=-0.02, rim=0.14)
    face_top = lit(IRON, gain=0.46, bias=0.16, rim=0.12)
    edge_mat = lit(IRON, gain=0.30, bias=-0.52, rim=0.18)
    h = 8.0 * (1.0 - t) + 1.0
    off = -8.0 - t * 2.0
    along = 2.0 + t * 4.0
    cx, cy = rig.pt(along, off, h, gun=True)
    rx = 7.4 + 1.4 * t
    ry = 4.6 + 2.6 * t
    canvas.ellipse(cx, cy, rx, ry, edge_mat)
    canvas.ellipse(cx, cy, rx - 1.1, ry - 1.1, face_mat)
    canvas.ellipse(cx, cy + ry * 0.16, rx - 2.4, ry * 0.68, face_top)
    canvas.ellipse(cx, cy + ry * 0.30, 1.8, 1.5, lit(BRASS, gain=0.45, bias=-0.2))


def death_dust(canvas, rig, p, scale=1.0):
    """گردِ برخورد با زمین: چند پفِ دایره‌ای دورِ نقطهٔ افتادن."""
    d = p.get("dust", 0.0)
    if d < 0.05:
        return
    for k in range(4):
        t = k / 3.0
        along = -2.0 + t * 9.0
        side = (-1.0 + t * 2.0) * 4.6
        size = (2.0 + 5.0 * d) * (0.7 + 0.5 * math.sin(t * math.pi)) * scale
        ground_puff(canvas, rig, along, side, size, alpha=int(105 * d),
                    color=tone(DUST, -0.05 + 0.2 * t))


# ---------------------------------------------------------- grunt: سرباز پیاده

GRUNT = dict(
    id="grunt", label="سرباز پیاده", run=dict(stride=12.6, lift=6.2, arm=1.5, dust=0.55),
    foot_side=4.0, shadow=(11.0, 3.8, 46),
)


def draw_grunt(canvas, rig, p):
    spec = GRUNT
    mats = _mats(spec, OLIVE)

    def extras(c, r, pp, bb, layer):
        _gear_rig(c, r, pp, bb, mats, layer)

    draw_humanoid(canvas, rig, p, spec, mats, weapon="rifle", extras=extras)
    return spec


# ---------------------------------------------------------- runner: دوندهٔ سبک

RUNNER = dict(
    id="runner", label="دونده", run=dict(stride=13.8, lift=7.4, arm=2.0, dust=0.8,
                                         lean=1.9, bob_base=1.7, bob_amp=2.0),
    foot_side=3.8, shadow=(10.4, 3.6, 42),
    body=dict(z_hip=15.0, z_shoulder=23.2, z_dome=30.4, half_shoulder=6.4, leg_r=2.7,
              arm_r=2.4),
)


def draw_runner(canvas, rig, p):
    spec = RUNNER
    mats = _mats(spec, MAROON, T=KHAKI, SK=SKIN)
    mats["dome"] = sol.camo(lit(BLOOD, gain=0.5, bias=0.14, rim=0.14), seed=4, amount=10)
    mats["brim"] = ao_edge(lit(BLOOD, gain=0.4, bias=-0.7, rim=0.18), 0.28, 0.26)
    mats["dome_crown"] = lit(BLOOD, gain=0.55, bias=0.30, rim=0.0)
    mats["gun_scale"] = 0.88

    def extras(c, r, pp, bb, layer):
        lean = pp["lean"]
        settle = 1.0 - 0.75 * pp.get("progress", 0.0) if pp.get("death") else 1.0
        if layer == "back":
            # شالِ گردن که در دویدن عقب می‌پرد و هنگام افتادن می‌خوابد
            ph = pp["ph"]
            for k in range(3):
                wave = math.sin((ph + 0.18 * k) * TAU) * (1.0 + k * 0.7) * settle
                a0 = lean - 2.0 - k * 3.2
                rod(c, r, a0, -1.6 + wave * 0.5, bb["z_shoulder"] - 0.8 - k * 0.6,
                    a0 - 3.4, -2.2 + wave, bb["z_shoulder"] - 3.0 - k * 1.0,
                    1.5 - k * 0.26, lit(BLOOD, gain=0.45, bias=0.05 - k * 0.12, rim=0.16))
            # کیفِ پهلو
            rig.box3(c, lean - 3.0, lean + 1.2, -bb["half_hip"] - 2.8, -bb["half_hip"] - 0.2,
                     bb["z_hip"] - 1.6, bb["z_belt"] + 1.0,
                     ao_edge(lit(KHAKI, gain=0.42, bias=0.28, rim=0.14), 0.26, 0.22),
                     lit(KHAKI, gain=0.30, bias=-0.42))
        else:
            rig.box3(c, lean + 1.0, lean + 4.4, -bb["half_chest"] + 0.4, bb["half_chest"] - 0.4,
                     bb["z_hip"] + 2.4, bb["z_chest"] + 0.6,
                     ao_edge(lit(VEST, gain=0.45, bias=0.30, rim=0.12), 0.28, 0.20),
                     mats["belt"])
            for sm in (-1.9, 1.9):
                rig.box3(c, lean + 3.6, lean + 5.4, sm - 1.6, sm + 1.6,
                         bb["z_hip"] + 3.0, bb["z_chest"] - 0.4,
                         ao_edge(lit(KHAKI, gain=0.42, bias=0.30, rim=0.14), 0.26, 0.22),
                         lit(KHAKI, gain=0.30, bias=-0.42))

    draw_humanoid(canvas, rig, p, spec, mats, weapon="rifle", extras=extras, carry=0.35)
    return spec


# ---------------------------------------------------------- armored: خودروی زرهی

ARMORED = dict(
    id="armored", label="زره‌پوش", run=dict(dust=0.9), shadow=(15.4, 4.8, 50),
    mechanical="tank",
    # خودروی زرهی صُلب است: به‌جای افتادنِ پیکره، کمی می‌نشیند و کج می‌شود؛
    # پس شیب و چرخشِ مرگ برایش کوچک نگه داشته می‌شود تا بدنه از هم نپاشد.
    death=dict(pitch=0.13, yaw=0.25, dust=1.2, sprawl=0.0),
)


def draw_armored(canvas, rig, p):
    spec = ARMORED
    wreck = p.get("death", False)
    prog = p.get("progress", 0.0) if wreck else 0.0   # نامِ جدا تا با متغیرهای حلقه قاطی نشود
    ph = 0.0 if wreck else p["ph"]
    flicker = 0.5 + 0.5 * math.sin(i_flicker(p) * 3.1)
    if wreck:
        # از کار افتادن: بدنه کمی پایین می‌نشیند و کج می‌شود؛ زنجیرها می‌ایستند
        apply_rig(rig, p, base=(0.0, -0.7 * prog), use_bob=False, use_sway=0.0)
        rig.yaw = math.radians(1.4 * prog)
    else:
        apply_rig(rig, p, base=(math.sin(ph * TAU) * 0.6,
                                -1.3 + math.sin(ph * TAU * 2.0 + 0.4) * 1.5),
                  use_bob=False, use_sway=0.0)
    hull_top = lit(STEEL, gain=0.46, bias=0.24, rim=0.14)
    hull_side = lit(STEEL, gain=0.30, bias=-0.34, rim=0.16)
    dark = lit(IRON, gain=0.34, bias=-0.44, rim=0.18)
    track_top = lit(IRON, gain=0.38, bias=-0.08, rim=0.18)
    wheel = lit(STEEL, gain=0.42, bias=-0.02, rim=0.16)
    plate_top = lit(STEEL, gain=0.50, bias=0.30, rim=0.12)
    light = solid(GLOW_R, 210)

    # زنجیرها (دو ردیف)، با حلقه‌های متحرک و چرخ‌های دندانه‌دار
    for sm in (-1.0, 1.0):
        side = sm * 9.4
        rig.box3(canvas, -13.2, 13.2, side - 4.4, side + 4.4, 0.6, 7.4, track_top, dark)
        n = 10
        for k in range(n):
            tk = ((k + ph * n) % n) / float(n)
            a = -12.6 + tk * 25.2
            rig.box3(canvas, a, a + 2.0, side - 4.5, side + 4.5, 6.0, 7.8,
                     lit(STEEL, gain=0.44, bias=0.12 - tk * 0.34, rim=0.16),
                     lit(IRON, gain=0.30, bias=-0.52, rim=0.18))
        if not rig.vertical:
            for k in range(4):
                a = -9.6 + k * 6.4
                x, y = rig.pt(a, side, 3.8)
                canvas.ellipse(x, y, 2.7, 2.4, wheel)
                canvas.ellipse(x, y, 1.2, 1.0, dark)
                ang = (ph * TAU * 2.0) + k
                canvas.line((x + math.cos(ang) * 1.8, y + math.sin(ang) * 1.6),
                            (x - math.cos(ang) * 1.8, y - math.sin(ang) * 1.6), light, width=0.9)
    # بدنهٔ اصلی و گلگیرها
    rig.box3(canvas, -12.4, 12.0, -7.2, 7.2, 6.6, 13.6, hull_top, hull_side)
    rig.box3(canvas, -8.0, 7.6, -5.4, 5.4, 13.4, 16.4, plate_top,
             lit(STEEL, gain=0.34, bias=-0.14, rim=0.16))
    rig.box3(canvas, 9.0, 12.4, -6.6, 6.6, 8.0, 13.0, plate_top, hull_side)
    # برجک (در مرگ کمی می‌افتد و می‌چرخد)
    disc(canvas, rig, -0.6 - 1.2 * prog, 0.6 * prog, 16.6 - 1.6 * prog, 7.0, 5.8,
         lit(STEEL, gain=0.5, bias=0.30 - 0.25 * prog, rim=0.12))
    disc(canvas, rig, -0.2 - 1.2 * prog, 0.6 * prog, 18.8 - 1.6 * prog, 4.6, 3.4,
         lit(STEEL, gain=0.52, bias=0.36 - 0.28 * prog, rim=0.10))
    # لولهٔ توپ + ترمز دهانه؛ در مرگ دهانه پایین می‌افتد و کج می‌شود
    bang = math.radians(11.0 * prog)
    ma, ms = 1.6 + 13.4 * math.cos(bang), 13.4 * math.sin(bang)
    rod(canvas, rig, 1.6, 0.0, 16.2 - 1.6 * prog, ma, ms, 15.2 - 3.4 * prog, 2.0,
        lit(IRON, gain=0.42 - 0.25 * prog, bias=0.0, rim=0.18))
    mb = ma - 15.0                      # جابه‌جاییِ دهانهٔ توپ نسبت به حالتِ سالم
    rod(canvas, rig, 14.6 + mb, ms, 15.2 - 3.5 * prog, 16.4 + mb, ms, 15.2 - 3.5 * prog,
        1.6, flat(GUN, -0.7))
    # چراغ‌ها، نوارهای هشدار و جزئیات
    for sm in (-5.0, 5.0):
        disc(canvas, rig, 12.0, sm, 10.2, 1.4, 1.2, light)
        disc(canvas, rig, -2.0, sm * 1.2, 13.6, 1.2, 1.0, solid(GLOW_R, 150))
    disc(canvas, rig, 6.2, 0.0, 13.9, 1.3, 1.1, solid(GLOW_B, 190))
    for tk in (0.28, 0.62):
        rig.box3(canvas, -12.2 + tk * 22.0, -10.6 + tk * 22.0, -7.3, 7.3, 12.8, 13.8,
                 solid(GLOW_R, 120), solid(GLOW_R, 90))
    # آنتن (در مرگ خم می‌شود)
    rod(canvas, rig, -10.6, 5.6, 16.2, -11.6 - math.sin(ph * TAU) * 0.9 - 2.4 * prog, 6.2,
        24.4 - 5.0 * prog, 0.5, lit(IRON, gain=0.5, bias=0.3, rim=0.2))
    # دود اگزوز: در مرگ ستونِ دود غلیظ می‌شود
    # در حالتِ سالم دقیقاً مثل قبل (تا شیت‌های دویدن بی‌دلیل تغییر نکنند) و
    # فقط در مرگ دود غلیظ‌تر و عقب‌تر می‌شود.
    puffs = 2 + int(round(3 * prog))
    for k in range(puffs):
        tp = (ph + k * 0.5) % 1.0
        size = (1.4 + 2.4 * tp) * (1.0 + 0.7 * prog)
        ground_puff(canvas, rig, -12.6 - k * 1.4 - 2.4 * prog, 4.8 + k * 0.6,
                    size, alpha=int((90 + 55 * prog) * (1.0 - tp)),
                    color=tone(SMOKE, 0.1 + 0.2 * k))
    if wreck and prog > 0.45:
        # آتشِ زبانه‌کشنده روی بدنه و دودهٔ سوختگیِ کوچک
        fa = (prog - 0.45) / 0.55
        for k, zz in ((0, 14.2), (1, 12.4)):
            fl = 2.0 + 1.6 * flicker - k * 0.4
            disc(canvas, rig, -9.4 + k * 1.6, -4.6 + k * 1.4, zz,
                 fl, fl * 0.8, solid((255, 150 + int(60 * flicker), 40),
                                     int((80 + 70 * flicker) * fa)))
            disc(canvas, rig, -9.4 + k * 1.6, -4.6 + k * 1.4, zz,
                 fl * 0.5, fl * 0.42, solid((255, 232, 170), int(130 * fa * flicker)))
        disc(canvas, rig, -7.4, -1.4, 13.9, 5.4, 3.4, solid((22, 18, 16), int(70 * fa)))
    return spec


def i_flicker(p):
    """فازِ سوسوزدنِ آتش بر اساسِ شمارهٔ فریم."""
    return p.get("ph", 0.0) * DEATH_FRAMES


# ---------------------------------------------------------- shield: سپردار

SHIELD = dict(
    id="shield", label="سپردار", run=dict(stride=9.6, lift=4.4, arm=0.9, dust=0.75,
                                          lean=0.9, sway=0.9, bob_amp=1.2),
    foot_side=3.6, shadow=(10.6, 3.7, 46), body=dict(half_shoulder=7.2, leg_r=3.0),
)


def draw_shield(canvas, rig, p):
    spec = SHIELD
    mats = _mats(spec, NAVY, T=IRON, G=VEST)
    mats["dome"] = lit(STEEL, gain=0.5, bias=0.15, rim=0.14)
    mats["brim"] = ao_edge(lit(IRON, gain=0.4, bias=-0.6, rim=0.18), 0.28, 0.24)
    mats["dome_crown"] = lit(STEEL, gain=0.55, bias=0.30, rim=0.0)
    mats["arm_spread"] = 0.8
    ph = p["ph"]
    face_mat = lit(NAVY, gain=0.42, bias=-0.02, rim=0.14)
    face_top = lit(IRON, gain=0.46, bias=0.16, rim=0.12)
    edge_mat = lit(IRON, gain=0.30, bias=-0.52, rim=0.18)

    def shield_plate(c, r, pp, bb):
        """سپر: در نمای روبه‌رو تختهٔ گردِ روبه‌دوربین، در نماهای پهلو لبهٔ ضخیم."""
        lean = pp["lean"]
        tilt = math.sin(ph * TAU) * 0.16
        z0 = bb["z_belt"] - 3.4 + tilt
        z1 = bb["z_shoulder"] + 4.0
        zc = (z0 + z1) * 0.5
        if r.vertical:
            cx, cy = r.pt(lean + 4.6, 0.0, zc)
            rx, ry = 8.0, (z1 - z0) * 0.5
            c.ellipse(cx, cy, rx, ry, edge_mat)
            c.ellipse(cx, cy, rx - 1.2, ry - 1.2, face_mat)
            c.ellipse(cx, cy + ry * 0.18, rx - 2.6, ry * 0.72, face_top)
            c.rrect(cx, cy - ry * 0.42, rx - 3.0, 1.1, 0.4, flat(INK, -0.55))
            c.ellipse(cx, cy + ry * 0.34, 2.0, 1.8, lit(BRASS, gain=0.45, bias=-0.2))
            for k in (-1, 1):
                c.ellipse(cx + k * (rx - 1.6), cy - ry * 0.30, 0.9, 2.4, face_top)
        else:
            rig.box3(c, lean + 4.4, lean + 6.6, -6.4 + tilt, 6.0 + tilt, z0, z1,
                     face_top, face_mat)
            rig.box3(c, lean + 5.0, lean + 6.4, -5.4 + tilt, 5.0 + tilt, z0 + 1.0, z1 - 1.0,
                     face_top, face_mat)
            rod(c, r, lean + 6.6, 4.4 + tilt, z0 + 1.4, lean + 6.6, 4.4 + tilt, z1 - 1.4, 1.0,
                lit(BRASS, gain=0.42, bias=-0.3))
            c.rrect(*r.pt(lean + 6.8, 0.0, z1 - 3.2), 1.4, 5.4, 0.5, flat(INK, -0.5))

    def extras(c, r, pp, bb, layer):
        if layer == "back" and in_hand(pp):
            shield_plate(c, r, pp, bb)

    draw_humanoid(canvas, rig, p, spec, mats, weapon="rifle", extras=extras, carry=0.15)
    draw_dropped_shield(canvas, rig, p)
    return spec


# ---------------------------------------------------------- archer: کمانگیر / تک‌تیرانداز

ARCHER = dict(
    id="archer", label="کمانگیر", run=dict(stride=11.4, lift=5.4, arm=1.2, dust=0.5,
                                           lean=1.2, sway=1.0),
    foot_side=3.8, shadow=(11.0, 3.8, 44), long_gun=True,
    body=dict(half_shoulder=6.6, half_chest=6.0, z_shoulder=23.6),
)


def draw_archer(canvas, rig, p):
    spec = ARCHER
    mats = _mats(spec, COVER, T=OLIVE, G=VEST, pants=MAROON)
    mats["dome"] = sol.camo(lit(COVER, gain=0.5, bias=0.3, rim=0.12), seed=7, amount=26, scale=2)
    mats["brim"] = ao_edge(lit(COVER, gain=0.4, bias=-0.6, rim=0.18), 0.28, 0.26)
    mats["dome_crown"] = lit(COVER, gain=0.55, bias=0.32, rim=0.0)
    mats["gun_scale"] = 0.95
    ph = p["ph"]
    cloak = sol.camo(lit(COVER, gain=0.44, bias=-0.02, rim=0.16), seed=21, amount=26, scale=2)
    cloak_top = sol.camo(lit(COVER, gain=0.46, bias=0.24, rim=0.12), seed=21, amount=22, scale=2)
    cloak_dark = lit(COVER, gain=0.34, bias=-0.34, rim=0.16)

    def extras(c, r, pp, bb, layer):
        lean = pp["lean"]
        prog = pp.get("progress", 0.0) if pp.get("death") else 0.0
        settle = 1.0 - 0.85 * prog
        if layer == "back":
            # شنلِ گیلی: تنهٔ باریک‌شونده به سمت پایین با لبه‌های موج‌دار
            prism(c, r, lean - 6.8, lean - 0.6, (bb["half_chest"] + 1.4, -bb["half_chest"] - 1.4),
                  (bb["half_hip"] - 1.6, -bb["half_hip"] + 1.6),
                  bb["z_shoulder"] + 1.4, bb["z_hip"] - 1.6, cloak_top, cloak)
            for k in range(5):
                t = k / 4.0
                wave = math.sin((ph + t * 0.35) * TAU) * 1.4 * settle
                a = lean - 6.0
                # هنگام افتادن، لَخت‌ها روی زمین پهن می‌شوند
                rod(c, r, a, (-5.4 + t * 10.8) * (1.0 + 0.30 * prog), bb["z_hip"] - 0.8 - 3.0 * prog,
                    a - 2.4 - wave, (-4.8 + t * 9.6) * (1.0 + 0.30 * prog), bb["z_hip"] - 5.4 + wave * 0.6 - 3.4 * prog,
                    1.7 - abs(t - 0.5) * 1.2, cloak_dark)
        else:
            prism(c, r, lean - 1.2, lean + 3.4, (bb["half_chest"] + 1.2, -bb["half_chest"] - 1.2),
                  (bb["half_hip"] - 0.4, -bb["half_hip"] + 0.4),
                  bb["z_chest"] + 2.4, bb["z_hip"] + 0.2, cloak_top, cloak)
            for sm in (-1.0, 1.0):
                rod(c, r, lean - 0.4, sm * 5.4, bb["z_shoulder"] + 1.6,
                    lean + 1.8, sm * 7.0, bb["z_chest"] + 0.2, 2.4, cloak_top)
            # شاخه‌های استتار روی شانه‌ها
            for sm in (-1.0, 1.0):
                for k in range(3):
                    rod(c, r, lean + 0.4 - k * 0.6, sm * (5.0 + k * 0.8), bb["z_shoulder"] + 2.4 - k * 0.5,
                        lean + 2.2 - k * 0.6, sm * (7.4 + k * 0.9), bb["z_chest"] + 1.4 - k * 0.6,
                        1.1 - k * 0.2, cloak_dark)
            # ماسک روی صورت
            if r.dir == "front":
                disc(c, r, lean + 3.0, 0.0, bb["z_face"] - 0.4, 3.2, 2.6, cloak_dark)

    draw_humanoid(canvas, rig, p, spec, mats, weapon="rifle", extras=extras, carry=0.55)
    return spec


# ---------------------------------------------------------- healer: درمانگر

HEALER = dict(
    id="healer", label="درمانگر", death=dict(pitch=0.55, yaw=0.7, sink=1.6, dust=1.3, sprawl=1.1),
    run=dict(dust=0.0, aura=0.7, hover=1.4, hover_amp=1.6),
    shadow=(9.0, 3.2, 34), floater=True,
)


def draw_healer(canvas, rig, p):
    """درمانگر: شناور، و در مرگ آرام به زمین می‌آید و ردایش فرو می‌ریزد."""
    spec = HEALER
    ph = p["ph"]
    dying = p.get("death", False)
    prog = p.get("progress", 0.0) if dying else 0.0
    hover = 0.0 if dying else p.get("hover", 1.4)
    if dying:
        apply_rig(rig, p, use_sway=0.0)
    else:
        apply_rig(rig, p, base=(math.sin(ph * TAU) * 0.8, -hover),
                  use_bob=False, use_sway=0.0)
    settle = 1.0 - 0.85 * prog      # موجِ پارچه با افتادن می‌خوابد
    robe = sol.camo(lit(PLUM, gain=0.44, bias=0.02, rim=0.14), seed=17, amount=13, scale=3)
    robe_low = lit(PLUM, gain=0.40, bias=-0.34, rim=0.16)
    robe_side = lit(PLUM, gain=0.36, bias=-0.22, rim=0.16)
    robe_top = lit(PLUM, gain=0.48, bias=0.24, rim=0.12)
    trim = lit(PLUM, gain=0.5, bias=0.58, rim=0.10)
    hood = lit(PLUM, gain=0.44, bias=-0.14, rim=0.16)
    hood_top = lit(PLUM, gain=0.46, bias=0.20, rim=0.12)
    z_hem, z_belt, z_hip, z_chest, z_shoulder, z_hood = 0.9, 15.0, 13.4, 19.2, 22.4, 27.4

    # هالهٔ زیر پا (با افتادن کم‌رنگ و خاموش می‌شود)
    x, y = rig.pt(0.0, 0.0, 0.4)
    aura = (1.0 - prog) * (1.0 if not dying else 1.0)
    if aura > 0.04:
        canvas.glow(x, y, 12.0 * (1.0 + 0.25 * prog), GLOW_V, alpha=int(52 * aura), power=1.3)
    # دامن: مخروطی به سمت پایین با لَخت‌های هم‌پوشان و موج‌دار (لبهٔ زنگوله‌ای)
    prism(canvas, rig, -1.0, 1.4, (6.2, -6.2), (7.4, -7.4), z_chest, z_hip - 2.2, robe_top, robe)
    for k in range(9):
        t = k / 8.0
        side = (-7.2 + t * 14.4) * (1.0 + 0.55 * prog)
        wave = math.sin((ph + t * 0.30) * TAU) * settle
        rod(canvas, rig, 1.0, side, z_hip - 1.2 - 3.4 * prog,
            1.3, side * 1.06 + wave * 1.0, z_hem + max(0.0, wave) * 1.9,
            3.0 - abs(t - 0.5) * 1.6, robe_low if k % 2 else robe_side)
    # بالاتنه
    canvas.cyl(rig.pt(0.0, 0.0, z_hip + 2.0), rig.pt(0.0, 0.0, z_shoulder - 0.6), 6.0, robe,
               hw1=5.2, round_ends=(True, True))
    # جبه: نواری روشن از گلو تا کمربند
    prism(canvas, rig, 1.8, 4.6, (3.4, -3.4), (3.8, -3.8), z_shoulder - 0.4, z_belt - 0.6,
          robe_top, robe_side)
    if rig.dir != "back":
        for sm in (-2.4, 2.4):
            rod(canvas, rig, 4.6, sm, z_shoulder - 1.2, 4.8, sm, z_belt - 0.2, 1.0, trim)
    # کمربند نازک و طلسمِ آویزان
    rod(canvas, rig, 0.0, -5.0, z_belt, 0.6, 5.0, z_belt, 1.5, flat(INK, -0.5))
    rod(canvas, rig, 3.0, -4.4, z_belt + 2.6, 3.4, 4.4, z_belt + 2.2, 1.1,
        lit(BRASS, gain=0.45, bias=-0.25))
    disc(canvas, rig, 4.8, -2.6, z_belt - 1.8, 1.0, 2.0, trim)
    disc(canvas, rig, 4.8, 2.6, z_belt - 1.4, 1.0, 1.8, trim)
    # سر، شقهود و سایهٔ صورت
    disc(canvas, rig, 0.6, 0.0, 25.0, 3.0, 2.7, solid((22, 16, 34)))
    disc(canvas, rig, 0.4, 0.0, z_hood, 5.0, 4.2, hood)
    disc(canvas, rig, -0.4, 0.0, z_hood + 2.8, 2.8, 2.0, hood_top)
    rod(canvas, rig, 3.4, 0.0, z_hood - 3.4, 2.0, 0.0, z_shoulder + 1.0, 1.2, trim)
    for sm in (-1.0, 1.0):
        disc(canvas, rig, 2.0, sm * 4.4, z_hood - 1.0, 1.8, 2.2, hood)
    if rig.dir != "back":
        disc(canvas, rig, 2.8, 0.0, z_hood - 2.0, 2.6, 1.9, solid((22, 16, 34)))
        for sm in (-1.0, 1.0):
            xx, yy = rig.pt(3.6, sm * 1.5, z_hood - 1.8)
            eye = int(235 * (1.0 - 0.85 * prog))
            if eye > 12:
                canvas.ellipse(xx, yy, 0.9, 1.0, solid(GLOW_V, eye))
    # آستین‌ها و دست‌ها روی عصا
    pad = -5.6
    for sgn in ((1, -1) if rig.vertical else (1, -1)):
        sh = rig.pt(-0.4, sgn * 4.8, z_shoulder - 0.8)
        hand = rig.pt(4.8, pad - (0.8 if sgn > 0 else -0.8), z_chest - 1.2 + math.sin(ph * TAU) * 0.7)
        canvas.cyl(sh, hand, 2.4, robe, hw1=1.8, round_ends=(True, True))
        canvas.ellipse(hand[0], hand[1], 1.9, 1.7,
                       ao_edge(lit(SKIN, gain=0.5, bias=-0.45, rim=0.2), 0.3, 0.3))
    # عصا با گویِ درخشان؛ هنگام افتادن از دست رها می‌شود
    if in_hand(p):
        staff_top = 31.4 + math.sin(ph * TAU) * 0.9
        wood = lit(MAROON, gain=0.5, bias=0.62, rim=0.12)
        rod(canvas, rig, 7.6, pad - 0.6, z_hem, 7.6, pad - 0.6, staff_top, 1.1, wood)
        gx, gy = rig.pt(7.6, pad - 0.6, staff_top + 1.6)
        fade = 1.0 - 0.35 * prog
        canvas.glow(gx, gy, 7.0, GLOW_V, alpha=int(120 * fade), power=1.5)
        canvas.ellipse(gx, gy, 2.6, 2.4, solid(GLOW_V, int(210 * fade)))
        canvas.ellipse(gx - 0.4, gy - 0.5, 1.1, 1.0, solid((245, 236, 255), int(240 * fade)))
    else:
        draw_dropped_staff(canvas, rig, p, glow_fade=1.0 - 0.8 * prog)
    return spec


# ---------------------------------------------------------- commander: فرمانده

COMMANDER = dict(
    id="commander", label="فرمانده", death=dict(pitch=0.92, yaw=0.85),
    run=dict(stride=11.0, lift=5.0, dust=0.85, sway=1.1,
             arm=0.9, bob_base=1.5, bob_amp=1.5),
    foot_side=4.4, shadow=(12.4, 4.2, 48),
    body=dict(z_boot=3.0, z_knee=8.2, z_hip=15.0, z_belt=16.4, z_chest=20.4, z_shoulder=23.6,
              z_neck=24.6, z_face=27.4, z_dome=30.6, r_helmet=5.2, half_shoulder=7.8,
              half_chest=6.6, half_hip=6.2, leg_r=3.1, arm_r=2.9),
)


def draw_commander(canvas, rig, p):
    spec = COMMANDER
    b = body(spec)
    ph = p["ph"]
    apply_rig(rig, p, use_sway=0.35, bob_scale=0.9)
    plate_top = lit(STEEL, gain=0.48, bias=0.28, rim=0.12)
    plate_side = lit(STEEL, gain=0.32, bias=-0.30, rim=0.16)
    plate_dark = lit(IRON, gain=0.34, bias=-0.46, rim=0.18)
    joint = lit(IRON, gain=0.44, bias=-0.04, rim=0.16)
    sash = lit(MAROON, gain=0.44, bias=0.10, rim=0.14)
    glow = solid(GLOW_R, 205)
    gold = lit(BRASS, gain=0.5, bias=0.05, rim=0.14)
    feet = p["feet"]
    lean = p["lean"]

    # پاها: ران و ساقِ زرهی + چکمه‌های معمولی (نه غول‌آسا)
    for k in foot_order(rig, feet):
        along, side, lift, sc = feet[k]
        hip = rig.pt(along * 0.14, side * 0.66, b["z_hip"] - 0.6 + lift * 0.30)
        knee = rig.pt(along * 0.56, side * 0.98, b["z_knee"] + lift * 0.66)
        ankle = rig.pt(along * 0.84, side * 1.0, b["z_boot"] + lift + 0.4)
        canvas.cyl(hip, knee, b["leg_r"] * sc, plate_side, hw1=b["leg_r"] * 0.84 * sc,
                   round_ends=(True, False))
        canvas.cyl(knee, ankle, b["leg_r"] * 0.84 * sc, plate_dark, hw1=b["leg_r"] * 0.74 * sc,
                   round_ends=(False, False))
        canvas.ellipse(knee[0], knee[1], 3.2, 2.6, plate_top)
        canvas.ellipse(knee[0], knee[1], 1.2, 1.0, glow)
        # چکمه: کفِ سنگینِ کوچک‌تر از قبل
        heel, toe = along - 2.6, along + 4.2
        half = 2.5 * sc
        rig.box3(canvas, heel, toe, side - half, side + half, lift, lift + b["z_boot"] + 0.6,
                 plate_top, plate_dark)
        s0 = rig.pt(heel + 0.6, side - half + 0.4, lift + 0.6)
        s1 = rig.pt(toe - 0.6, side + half - 0.4, lift + 0.6)
        canvas.capsule(s0, s1, 0.9, 0.9, flat(INK, -0.35), squash=rig.sq(1.0, 6.0, 1.0))
        disc(canvas, rig, heel + 1.2, side, lift + b["z_boot"] + 0.9, 0.8, 0.7, joint)
    if rig.vertical:
        d0 = rig.pt(-1.4, 0.0, b["z_boot"] + 1.0)
        d1 = rig.pt(-0.6, 0.0, b["z_hip"] - 2.2)
        canvas.capsule(d0, d1, 1.0, 1.0, flat(VEST, -0.95))

    # کوله و آنتن (پشت) — باریک‌شونده تا در نمای پهلو تودهٔ تیره نسازد
    prism(canvas, rig, lean - 7.0, lean - 2.8, (4.4, -4.4), (3.4, -3.4),
          b["z_shoulder"] + 0.2, b["z_belt"] + 1.6, plate_side, plate_dark)
    for sm in (-4.6, 4.6):
        rod(canvas, rig, lean - 2.2, sm, b["z_shoulder"] + 0.4, lean - 1.8, sm, b["z_belt"] - 0.4,
            1.1, flat(VEST, -0.9))
    rod(canvas, rig, lean - 5.0, 4.4, b["z_shoulder"] + 0.4,
        lean - 5.6 - math.sin(ph * TAU) * 0.8, 5.0, b["z_shoulder"] + 9.6, 0.5, plate_top)

    # تنه: سینه‌پوشِ زرهی + کمربند + شالِ فرماندهی
    prism(canvas, rig, lean - 3.8, lean + 3.8, (b["half_hip"] + 0.4, -b["half_hip"] - 0.4),
          (b["half_hip"] + 0.2, -b["half_hip"] - 0.2), b["z_belt"] + 0.6, b["z_hip"] - 1.6,
          plate_side, plate_dark)
    prism(canvas, rig, lean - 4.6, lean + 4.4, (b["half_shoulder"], -b["half_shoulder"]),
          (b["half_hip"] + 0.4, -b["half_hip"] - 0.4), b["z_shoulder"] + 1.6, b["z_chest"] - 1.4,
          plate_top, plate_side)
    prism(canvas, rig, lean - 4.2, lean + 4.2, (b["half_hip"] + 0.6, -b["half_hip"] - 0.6),
          (b["half_hip"] + 0.8, -b["half_hip"] - 0.8), b["z_chest"] - 1.6, b["z_belt"] + 0.4,
          plate_dark if False else plate_side, plate_dark)
    # سینه‌پوش برجسته و چراغ
    rig.box3(canvas, lean + 3.6, lean + 6.0, -4.6, 4.6, b["z_chest"] - 2.6, b["z_chest"] + 2.2,
             plate_top, plate_dark)
    disc(canvas, rig, lean + 6.2, 0.0, b["z_chest"], 1.8, 1.6, glow)
    for sm in (-3.0, 3.0):
        disc(canvas, rig, lean + 5.6, sm, b["z_chest"] + 3.0, 1.0, 0.9, joint)
    # شالِ مورب روی سینه
    rod(canvas, rig, lean + 3.4, -5.4, b["z_shoulder"] - 0.4, lean + 4.2, 4.6, b["z_belt"] + 1.2,
        1.6, sash)
    # کمربند و سگک
    rig.box3(canvas, lean - 3.8, lean + 4.0, -b["half_hip"] - 0.5, b["half_hip"] + 0.5,
             b["z_belt"] - 1.8, b["z_belt"] + 0.4, plate_side, plate_dark)
    disc(canvas, rig, lean + 4.2, 0.0, b["z_belt"] - 0.7, 1.6, 1.4, gold)
    # سر: کلاه‌خودِ زرهی با شکافِ جلوی کلاه و چراغِ کنارِ گوش
    draw_head(canvas, rig, p, b, mats_skin(), mats_dome(), mats_brim(), face=True,
              crown_mat=lit(STEEL, gain=0.55, bias=0.30, rim=0.0))
    rig.box3(canvas, lean + 2.4, lean + 4.4, -4.4, 4.4, b["z_dome"] - 3.4, b["z_dome"] - 2.0,
             joint, plate_dark)
    for sm in (-1.0, 1.0):
        disc(canvas, rig, lean - 0.6, sm * 5.0, b["z_dome"] - 2.2, 1.0, 1.6, joint)
    # پرچمِ فرماندهی: سرِ پا می‌ماند یا همراهِ پیکر روی زمین می‌خوابد
    if not in_hand(p):
        draw_dropped_flag(canvas, rig, p)
        return spec
    wave = math.sin(ph * TAU) * 1.4
    px, py = rig.pt(lean - 3.0, -5.8, b["z_shoulder"] - 0.4)
    rear = -1.0 if rig.vertical else -3.4
    canvas.capsule((px, py), (px + rear * 0.26, py - 10.4), 0.7, 0.7, plate_top)
    top = (px + rear * 0.26, py - 10.4)
    fdir = -1.0 if rig.vertical else -1.4
    canvas.poly([top, (top[0] + fdir * 6.6 - wave * 0.5, top[1] + 1.2 + wave),
                 (top[0] + fdir * 5.4, top[1] + 4.0 - wave * 0.4)], solid((158, 40, 34), 235))
    canvas.poly([(top[0] + fdir * 1.6, top[1] + 1.6),
                 (top[0] + fdir * 5.9 - wave * 0.4, top[1] + 2.3 + wave * 0.8),
                 (top[0] + fdir * 5.0, top[1] + 4.3 - wave * 0.3)], solid((198, 76, 54), 200))
    _commander_arms(canvas, rig, p, b, joint, plate_top, plate_side, plate_dark)
    return spec


def _commander_arms(canvas, rig, p, b, joint=None, plate_top=None, plate_side=None,
                    plate_dark=None):
    """بازوها و مشت‌های زرهیِ فرمانده؛ در مرگ بازتر و آویزان‌تر."""
    joint = joint or lit(IRON, gain=0.44, bias=-0.04, rim=0.16)
    plate_top = plate_top or lit(STEEL, gain=0.48, bias=0.28, rim=0.12)
    plate_side = plate_side or lit(STEEL, gain=0.32, bias=-0.30, rim=0.16)
    plate_dark = plate_dark or lit(IRON, gain=0.34, bias=-0.46, rim=0.18)
    lean = p["lean"]
    swing = p["arm_swing"] + p.get("arm_flail", 0.0) * 0.30
    sprawl = p.get("sprawl", 0.0)
    for sgn in ((-1, 1) if rig.vertical else (1, -1)):
        sh = rig.pt(lean - 0.6, sgn * (b["half_shoulder"] - 0.8 + sprawl * 0.5),
                    b["z_shoulder"] - 1.4)
        el = rig.pt(lean + 1.6 + swing * (1.0 if sgn > 0 else 0.4),
                    sgn * (b["half_shoulder"] + 0.6 + sprawl * 1.2),
                    b["z_chest"] - 1.8 + swing * 0.5 - sprawl * 0.5)
        hand = rig.pt(lean + 4.0 + swing * (1.8 if sgn > 0 else 0.7),
                      sgn * (b["half_shoulder"] - 0.2 + sprawl * 1.6),
                      b["z_belt"] + 1.4 + swing * 0.7 - sprawl * 1.2)
        canvas.cyl(sh, el, b["arm_r"], plate_side, hw1=b["arm_r"] * 0.86, round_ends=(True, True))
        canvas.cyl(el, hand, b["arm_r"] * 0.86, plate_dark, hw1=b["arm_r"] * 0.76,
                   round_ends=(True, True))
        canvas.ellipse(hand[0], hand[1], 2.4, 2.1, joint)
        disc(canvas, rig, lean - 0.6, sgn * (b["half_shoulder"] - 0.4), b["z_shoulder"] + 1.2,
             2.8, 2.4, plate_top)
        disc(canvas, rig, lean - 0.6, sgn * (b["half_shoulder"] - 0.4), b["z_shoulder"] + 1.6,
             1.9, 1.5, plate_side)


def mats_skin():
    return ao_edge(lit(SKIN, gain=0.55, bias=0.05, rim=0.20), 0.32, 0.28)


def mats_dome():
    return lit(STEEL, gain=0.52, bias=0.24, rim=0.14)


def mats_brim():
    return ao_edge(lit(IRON, gain=0.40, bias=-0.62, rim=0.18), 0.28, 0.24)


# ---------------------------------------------------------- brute: غول

BRUTE = dict(
    id="brute", label="غول", run=dict(stride=10.2, lift=4.8, arm=1.4, dust=1.0, sway=1.4,
                                      lean=1.8, bob_base=1.2, bob_amp=1.8),
    foot_side=5.0, shadow=(14.2, 4.8, 52),
    body=dict(z_boot=4.4, z_knee=9.8, z_hip=17.8, z_belt=19.4, z_chest=24.2, z_shoulder=27.2,
              z_neck=28.6, z_face=30.9, z_dome=33.9, r_helmet=4.6, half_shoulder=8.4,
              half_chest=7.2, half_hip=6.4, leg_r=3.4, arm_r=3.2),
)


def draw_brute(canvas, rig, p):
    spec = BRUTE
    b = body(spec)
    ph = p["ph"]
    apply_rig(rig, p, use_sway=0.5, bob_scale=0.8)
    hide = sol.camo(lit(ORC, gain=0.44, bias=0.02, rim=0.14), seed=23, amount=12, scale=2)
    hide_top = lit(ORC, gain=0.46, bias=0.26, rim=0.12)
    hide_dark = lit(ORC, gain=0.36, bias=-0.34, rim=0.16)
    hide_leg = lit(ORC, gain=0.40, bias=-0.18, rim=0.16)
    leather = lit(MAROON, gain=0.42, bias=-0.12, rim=0.16)
    leather_top = lit(MAROON, gain=0.44, bias=0.26, rim=0.12)
    iron_top = lit(IRON, gain=0.46, bias=0.16, rim=0.14)
    iron_side = lit(IRON, gain=0.30, bias=-0.46, rim=0.16)
    feet = p["feet"]
    lean = p["lean"]

    # پاها: ستبر با پنجه‌های روشن و شکاف تیرهٔ میان دو پا
    for k in foot_order(rig, feet):
        along, side, lift, sc = feet[k]
        hip = rig.pt(along * 0.12, side * 0.62, b["z_hip"] - 0.8 + lift * 0.28)
        knee = rig.pt(along * 0.52, side * 0.94, b["z_knee"] + lift * 0.64)
        ankle = rig.pt(along * 0.78, side * 1.0, b["z_boot"] + lift + 0.6)
        canvas.cyl(hip, knee, b["leg_r"] * sc, hide_leg, hw1=b["leg_r"] * 0.86 * sc,
                   round_ends=(True, False))
        canvas.cyl(knee, ankle, b["leg_r"] * 0.86 * sc, hide_leg, hw1=b["leg_r"] * 0.76 * sc,
                   round_ends=(False, False))
        canvas.ellipse(knee[0], knee[1], 3.4, 2.8, hide_top)
        heel, toe = along - 2.6, along + 5.4
        half = 3.2 * sc
        rig.box3(canvas, heel, toe, side - half, side + half, lift, lift + b["z_boot"],
                 hide_dark, hide_dark)
        for t2 in range(3):
            disc(canvas, rig, toe - 0.4, side - 2.2 + t2 * 2.2, lift + b["z_boot"] + 0.4,
                 0.9, 0.7, sol.solid(BONE[2]))
    if rig.vertical:
        d0 = rig.pt(-1.6, 0.0, b["z_boot"] + 1.2)
        d1 = rig.pt(-0.8, 0.0, b["z_hip"] - 2.0)
        canvas.capsule(d0, d1, 1.2, 1.2, flat(INK, -0.6))

    # تنهٔ خمیده: کوهانِ پشت بلندتر، سینهٔ جلو کوتاه‌تر (قوس رو به جلو)
    prism(canvas, rig, lean - 4.2, lean + 3.6, (b["half_shoulder"] - 1.2, -b["half_shoulder"] + 1.2),
          (b["half_hip"] - 0.4, -b["half_hip"] + 0.4), b["z_shoulder"] + 1.0, b["z_belt"] + 0.4,
          hide_top, hide)
    # کوهانِ پشت
    disc(canvas, rig, lean - 3.8, 0.0, b["z_shoulder"] + 1.4, 4.4, 2.8, hide_top)
    # فکِ سنگین (بی‌رنگ روشن، فقط سایه)
    disc(canvas, rig, lean + 3.8, 0.0, b["z_face"] - 2.2, 2.8, 1.7, hide_dark)
    rig.box3(canvas, lean - 4.4, lean + 3.4, -b["half_hip"] - 0.2, b["half_hip"] + 0.2,
             b["z_belt"] - 1.4, b["z_belt"] + 0.4, leather_top, leather)
    # تسمه‌های چرمی روی سینه
    for sm in (-1.0, 1.0):
        rod(canvas, rig, lean + 3.2, sm * 5.6, b["z_belt"] + 0.2,
            lean - 2.4, sm * 6.6, b["z_shoulder"] + 0.6, 1.3, leather)
    # بازوها: بازوی گوریل‌وار که جلو و پایین می‌رود و مشت جلوتر از سینه می‌ایستد؛
    # در نمای پهلو بازوی دورتر عقب‌تر می‌ماند تا هر دو بازو خوانا بمانند.
    swing = p["arm_swing"]
    for sgn in ((-1, 1) if rig.vertical else (1, -1)):
        far = (not rig.vertical) and sgn < 0
        sh = rig.pt(lean - 0.8 + (-1.4 if far else 0.0), sgn * (b["half_shoulder"] - 1.0),
                    b["z_shoulder"] - 1.2)
        el = rig.pt(lean + 2.4 + swing * (1.6 if sgn > 0 else 0.6) - (3.0 if far else 0.0),
                    sgn * (b["half_shoulder"] + 0.8), b["z_chest"] - 3.2 + swing * 0.5)
        hand = rig.pt(lean + 5.0 + swing * (2.6 if sgn > 0 else 1.0) - (4.2 if far else 0.0),
                      sgn * (b["half_shoulder"] + 0.5), b["z_belt"] - 3.4 + swing * 1.0)
        canvas.cyl(sh, el, b["arm_r"], hide, hw1=b["arm_r"] * 0.88, round_ends=(True, True))
        canvas.cyl(el, hand, b["arm_r"] * 0.88, hide_leg, hw1=b["arm_r"] * 0.82,
                   round_ends=(True, True))
        canvas.ellipse(hand[0], hand[1], 3.0, 2.6, hide_top)
        # پنجه‌های فلزی روی مشت
        for t2 in (-1, 0, 1):
            kx, ky = rig.pt(lean + 6.6 + swing * 2.2 - (4.2 if far else 0.0),
                            sgn * (b["half_shoulder"] + 0.5 + t2 * 1.1),
                            b["z_belt"] - 2.8 + swing * 1.0)
            canvas.ellipse(kx, ky, 1.1, 1.0, iron_top)
    # شانه‌های خاردار (کوچک‌تر و عقب‌تر تا سر و بازوها را نپوشانند)
    for sm in (-1, 1):
        a = rig.pt(lean - 1.4, sm * (b["half_shoulder"] + 0.6), b["z_shoulder"] + 0.8)
        canvas.ellipse(a[0], a[1], 3.6, 2.4, iron_top)
        canvas.ellipse(a[0], a[1], 2.5, 1.6, iron_side)
        for k in range(3):
            s = rig.pt(lean - 2.6 + k * 1.8, sm * (b["half_shoulder"] + 0.8), b["z_shoulder"] + 1.4)
            canvas.capsule(s, (s[0] - 0.7, s[1] - 2.8), 1.0, 0.3, iron_side)
    # سرِ سبز با ابروی سنگین، چشم‌های سرخ و عاج
    disc(canvas, rig, 1.0 + lean, 0.0, b["z_neck"], 4.4, 3.4, hide_dark)
    disc(canvas, rig, lean + 2.2, 0.0, b["z_face"], 4.8, 4.0, hide)
    disc(canvas, rig, lean - 1.2, 0.0, b["z_dome"] + 0.6, 4.6, 3.4, hide_top)
    # پیشانیِ برجسته و پوزه
    disc(canvas, rig, lean + 4.0, 0.0, b["z_dome"] - 0.8, 3.4, 2.0, hide_top)
    if rig.dir != "back":
        rig.box3(canvas, lean + 3.2, lean + 5.4, -4.0, 4.0, b["z_face"] + 0.4, b["z_dome"] - 1.0,
                 hide_top, hide_dark)
        # خط دهان و دو عاجِ کوچک در دو کنارِ فک
        disc(canvas, rig, lean + 4.8, 0.0, b["z_face"] - 2.0, 2.2, 0.7, flat(INK, -0.35))
        for sm in (-1.0, 1.0):
            disc(canvas, rig, lean + 4.4, sm * 1.6, b["z_face"] - 0.6, 0.9, 0.9,
                 solid((214, 62, 46), 235))
            tusk = rig.pt(lean + 5.0, sm * 1.9, b["z_face"] - 2.4)
            canvas.capsule((tusk[0], tusk[1]), (tusk[0] - 0.6, tusk[1] - 1.5), 0.55, 0.35,
                           sol.solid(BONE[3]))
    return spec


# ---------------------------------------------------------- ثبت نیروها

UNITS = {
    "grunt": draw_grunt,
    "runner": draw_runner,
    "armored": draw_armored,
    "shield": draw_shield,
    "archer": draw_archer,
    "healer": draw_healer,
    "commander": draw_commander,
    "brute": draw_brute,
}

DIRS = ("front", "back", "left", "right")


# ------------------------------------------------------------------ انیمیشن‌ها

ANIMS = {
    "run": dict(
        frames=FRAMES, fps=FPS_RUN, loop=True, pose=run_pose, label="دویدن",
        out="enemy_run", index="run_pack.json",
        title="شیت دویدن نیروها - دژبان",
        h1="نیروها — دویدن — ۸ فریم افقی — پس‌زمینهٔ شفاف",
        doc="چرخهٔ کامل دویدن: گامِ بلند، زانوی بالا، بازوهای پرتابی و خاکِ پشتِ پا",
    ),
    "death": dict(
        frames=DEATH_FRAMES, fps=8, loop=False, pose=death_pose, label="افتادن",
        out="enemy_death", index="death_pack.json",
        title="شیت مرگ نیروها - دژبان",
        h1="نیروها — افتادن — ۶ فریم افقی — پس‌زمینهٔ شفاف",
        doc="اصابت ← پس‌رفتن ← زانو ← افتادن ← برخورد با زمین ← آرام گرفتن؛ سلاح رها می‌شود",
    ),
}

def out_dir(anim):
    return os.path.join(ROOT, "spritesheets", ANIMS[anim]["out"])


# ------------------------------------------------------------------ رندر


_SPEC_CACHE = {}


def unit_spec(unit_id):
    """خواص نیرو؛ یک‌بار حساب و ذخیره می‌شود."""
    if unit_id not in _SPEC_CACHE:
        fn = UNITS[unit_id]
        _SPEC_CACHE[unit_id] = fn(Canvas(BIG_W, BIG_H), Rig("front", BIG_W, BIG_GROUND),
                                  run_pose({}, "front", 0))
    return _SPEC_CACHE[unit_id]


def render_canvas(unit_id, anim, direction, i, w, h, ground):
    """یک فریم روی بومِ داده‌شده؛ خروجی: بومِ آماده + خواص + حالت."""
    spec = unit_spec(unit_id)
    rig = Rig(direction, w, ground, depth=0.62, gun_depth=1.30)
    body_canvas = Canvas(w, h)
    ground_layer = Canvas(w, h)
    p = ANIMS[anim]["pose"](spec, direction, i)
    UNITS[unit_id](body_canvas, rig, p)
    if anim == "run":
        # مثل قبل: گردِ خاک داخلِ بومِ پیکر می‌آید (کنارِ خط دور و رویِ سایه)
        if spec.get("run", {}).get("dust", 0.0) > 0.05 and not spec.get("mechanical"):
            dust_trail(body_canvas, rig, p, spec)
    else:
        # گردِ برخورد با زمین زیرِ پیکر می‌ماند تا بدن رویش بیفتد
        death_dust(ground_layer, rig, p, scale=body(spec)["z_dome"] / 31.4)
    body_canvas.outline()
    out = Canvas(w, h)
    sh = p.get("shadow") or spec.get("shadow")
    if sh:
        ground_puff(out, rig, 0.0, 0.0, sh[0] * 0.5, alpha=sh[2], color=(14, 16, 13))
    out.draw_over(ground_layer)
    out.draw_over(body_canvas)
    return out, spec, p


def big_frame(unit_id, anim, direction, i):
    return render_canvas(unit_id, anim, direction, i, BIG_W, BIG_H, BIG_GROUND)


def measure(unit_id, anim):
    """اندازهٔ فریم لازم از اجتماعِ همهٔ فریم‌های چهار جهتِ همان انیمیشن."""
    left = right = up = down = 0.0
    for direction in DIRS:
        for i in range(ANIMS[anim]["frames"]):
            canvas, spec, p = big_frame(unit_id, anim, direction, i)
            img = canvas.to_image()
            bbox = img.getbbox()
            if bbox is None:
                continue
            x0, y0, x1, y1 = bbox
            left = max(left, BIG_CX - x0)
            right = max(right, x1 - BIG_CX)
            up = max(up, BIG_GROUND - y0)
            down = max(down, y1 - BIG_GROUND)
    return left, right, up, down


GEOM = {}


def fit_size(unit_id, anim):
    """اندازهٔ فریم نهایی: عرض زوج، محتوا با حاشیهٔ PAD_X، خط زمین بالای سایه.

    انیمیشن‌های دیگر (مثلِ مرگ) خط زمین را همان‌قدر از لبهٔ پایین نگه می‌دارند
    که شیتِ دویدنِ همان نیرو دارد؛ این‌طور با عوض‌کردن شیت، پیکر جابه‌جا نمی‌شود.
    """
    if (unit_id, anim) not in GEOM:
        left, right, up, down = measure(unit_id, anim)
        w = max(MIN_SIDE, int(math.ceil(max(left, right) * 2.0)) + PAD_X * 2)
        if w % 2:
            w += 1
        if anim == "run":
            h = max(MIN_SIDE, int(math.ceil(up + down)) + PAD_TOP + PAD_BOTTOM)
            if h % 2:
                h += 1
            ground = h - PAD_BOTTOM - down
        else:
            ref = fit_size(unit_id, "run")
            bottom = max(ref["h"] - ref["ground"], down + PAD_BOTTOM)
            h = max(MIN_SIDE, int(math.ceil(up + PAD_TOP + bottom)))
            if h % 2:
                h += 1
            ground = h - bottom
        GEOM[(unit_id, anim)] = dict(w=w, h=h, ground=ground,
                                     up=up, down=down, left=left, right=right)
    return GEOM[(unit_id, anim)]


def render_frame(unit_id, anim, direction, i, geom):
    w, h, ground = geom["w"], geom["h"], geom["ground"]
    canvas, spec, p = render_canvas(unit_id, anim, direction, i, w, h, ground)
    return canvas.to_image()


def render_unit(unit_id, anim, geom):
    return {d: [render_frame(unit_id, anim, d, i, geom)
                for i in range(ANIMS[anim]["frames"])] for d in DIRS}


# ------------------------------------------------------------------ بررسی


def check_sheet(img, unit_id, direction, anim, pad=2):
    """اندازه، شفافیت، حاشیه و یکتا بودن فریم‌ها را بررسی می‌کند."""
    problems = []
    n = ANIMS[anim]["frames"]
    fw = img.width // n
    if img.width % n or fw * n != img.width:
        problems.append("%s_%s_%s: عرض شیت مضرب %d نیست" % (unit_id, anim, direction, n))
    if img.mode != "RGBA":
        problems.append("%s_%s_%s: کانال آلفا ندارد" % (unit_id, anim, direction))
    seen = []
    for k in range(n):
        fr = img.crop((k * fw, 0, (k + 1) * fw, img.height))
        bbox = fr.getbbox()
        if bbox is None:
            problems.append("%s_%s_%s: فریم %d خالی است" % (unit_id, anim, direction, k))
            continue
        x0, y0, x1, y1 = bbox
        if x0 < pad or y0 < pad or x1 > fw - pad or y1 > img.height - pad:
            problems.append("%s_%s_%s: فریم %d حاشیهٔ %d پیکسلی ندارد"
                            % (unit_id, anim, direction, k, pad))
        data = fr.tobytes()
        if data in seen:
            problems.append("%s_%s_%s: فریم %d با فریم %d یکسان است"
                            % (unit_id, anim, direction, k, seen.index(data)))
        seen.append(data)
    return problems


def check_pack(anim):
    problems = []
    pack_dir = out_dir(anim)
    index = os.path.join(pack_dir, ANIMS[anim]["index"])
    if not os.path.exists(index):
        return ["%s/%s پیدا نشد؛ اول بسته را بسازید." % (ANIMS[anim]["out"], ANIMS[anim]["index"])]
    with open(index, encoding="utf-8") as fh:
        pack = json.load(fh)
    for sheet in pack["sheets"]:
        path = os.path.join(pack_dir, sheet["file"])
        if not os.path.exists(path):
            problems.append("%s گم شده" % sheet["file"])
            continue
        img = Image.open(path).convert("RGBA")
        unit_id = sheet["unit"]
        problems += check_sheet(img, unit_id, sheet["direction"], anim)
        if img.size != (sheet["size"]["w"], sheet["size"]["h"]):
            problems.append("%s: اندازه با فهرست نمی‌خواند" % sheet["file"])
        frame_w = img.width // sheet["frames"]
        if frame_w != pack["units"][unit_id]["frameSize"]["w"]:
            problems.append("%s: عرض فریم با فهرست نمی‌خواند" % sheet["file"])
    return problems


# ------------------------------------------------------------------ پیش‌نمایش


def contact_sheet(frames, anim, zoom=2, bg=(26, 29, 24, 255)):
    n = ANIMS[anim]["frames"]
    size = next(iter(next(iter(frames.values())).values()))[0].size
    fw, fh = size
    pad = 4
    rows = len(frames) * len(DIRS)
    sheet = Image.new("RGBA", (n * (fw * zoom + pad) + pad, rows * (fh * zoom + pad) + pad), bg)
    y = pad
    for unit_id in frames:
        for direction in DIRS:
            for k, fr in enumerate(frames[unit_id][direction]):
                im = fr.resize((fw * zoom, fh * zoom), Image.NEAREST)
                sheet.alpha_composite(im, (pad + k * (fw * zoom + pad), y))
            y += fh * zoom + pad
    return sheet


def preview_gif(frames, path, anim, zoom=1, bg=(44, 48, 40)):
    from PIL import ImageDraw  # noqa: F401  (رسم مستقیم لازم نیست)
    n = ANIMS[anim]["frames"]
    grids = []
    for k in range(n):
        size = next(iter(next(iter(frames.values())).values()))[0].size
        fw, fh = size[0] * zoom, size[1] * zoom
        pad = 3
        sheet = Image.new("RGBA", (len(frames) * (fw + pad) + pad, len(DIRS) * (fh + pad) + pad),
                          bg + (255,))
        for r, direction in enumerate(DIRS):
            for c, unit_id in enumerate(frames):
                im = frames[unit_id][direction][k]
                if zoom != 1:
                    im = im.resize((fw, fh), Image.NEAREST)
                sheet.alpha_composite(im, (pad + c * (fw + pad), pad + r * (fh + pad)))
        grids.append(sheet.convert("P", palette=Image.ADAPTIVE, colors=96))
    grids[0].save(path, save_all=True, append_images=grids[1:] if n > 1 else [],
                  duration=int(1000 / ANIMS[anim]["fps"]), loop=0, optimize=False)


HTML = """<!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8">
<title>__TITLE__</title>
<style>
  :root { color-scheme: dark; }
  body { margin:0; background:#1e221c; color:#dfe6d6; font-family:system-ui,Tahoma,sans-serif; }
  header { padding:12px 18px; background:#272c23; border-bottom:1px solid #3a4133;
           display:flex; gap:16px; align-items:center; flex-wrap:wrap; position:sticky; top:0; z-index:2; }
  h1 { font-size:15px; margin:0; font-weight:600; }
  label { font-size:13px; display:flex; gap:6px; align-items:center; }
  table { border-collapse:separate; border-spacing:6px; }
  th { color:#e6eedd; font-weight:600; font-size:12px; }
  th.row { text-align:right; padding-left:10px; white-space:nowrap; }
  td.cell { background:#2b3026; border-radius:6px; }
  canvas { display:block; image-rendering:pixelated; }
  main { padding:8px 18px 40px; overflow:auto; }
  .hint { font-size:12px; color:#8e9c82; }
</style>
</head>
<body>
<header>
  <h1>__H1__</h1>
  <label>سرعت <input id="speed" type="range" min="2" max="24" value="__FPS__"><span id="fps">__FPS__</span> فریم بر ثانیه</label>
  <label>بزرگ‌نمایی <input id="zoom" type="range" min="1" max="6" value="3"><span id="zl">3</span>×</label>
  <label><input id="play" type="checkbox" checked> پخش</label>
  <label>پس‌زمینه
    <select id="bgsel">
      <option value="#2b3026">تیره</option>
      <option value="#6f7a5e">چمن</option>
      <option value="#c9b98a">کویر</option>
      <option value="#ffffff">سفید</option>
      <option value="#000000">سیاه</option>
    </select>
  </label>
</header>
<main><table id="grid"></table>
<p class="hint">__HINT__</p>
</main>
<script>
const UNITS = __UNITS__;
const SIZES = __SIZES__;
const ANIM = "__ANIM__";
const FRAMES = __FRAMES__;
const LOOP = __LOOP__;
const HOLD = 3;                       // فریمِ آخرِ انیمیشنِ بدونِ لوپ چند تیک می‌ماند
const DIRS = ["front","back","left","right"];
const imgs = {}, canv = [];
let frame = 0, playing = true, speed = __FPS__, zoom = 3, last = 0;

function loadSheets() {
  const jobs = [];
  for (const u of UNITS) for (const d of DIRS) {
    jobs.push(new Promise(res => {
      const im = new Image();
      im.onload = () => { imgs[u + "_" + d] = im; res(); };
      im.onerror = () => res();
      im.src = u + "_" + ANIM + "_" + d + ".png";
    }));
  }
  return Promise.all(jobs);
}

function buildGrid() {
  const table = document.getElementById("grid");
  let html = "<tr><th></th>" + UNITS.map(u => "<th>" + u + "</th>").join("") + "</tr>";
  for (const d of DIRS) {
    html += "<tr><th class='row'>" + d + "</th>";
    for (const u of UNITS) html += "<td class='cell'><canvas data-u='" + u + "' data-d='" + d + "'></canvas></td>";
    html += "</tr>";
  }
  table.innerHTML = html;
  for (const c of table.querySelectorAll("canvas")) {
    const ctx = c.getContext("2d");
    ctx.imageSmoothingEnabled = false;
    canv.push([c, ctx]);
  }
}

function resize() {
  for (const [c] of canv) {
    const s = SIZES[c.dataset.u];
    c.width = s[0]; c.height = s[1];
    c.style.width = (s[0] * zoom) + "px";
    c.style.height = (s[1] * zoom) + "px";
  }
}

function tick(now) {
  if (playing && now - last > 1000 / speed) { last = now; frame++; }
  for (const [c, ctx] of canv) {
    const u = c.dataset.u, d = c.dataset.d, s = SIZES[u];
    const im = imgs[u + "_" + d];
    if (!im) continue;
    const i = LOOP ? frame % FRAMES : Math.min(Math.floor(frame / 2), FRAMES - 1);
    ctx.clearRect(0, 0, s[0], s[1]);
    ctx.drawImage(im, i * s[0], 0, s[0], s[1], 0, 0, s[0], s[1]);
  }
  requestAnimationFrame(tick);
}

document.getElementById("speed").addEventListener("input", e => {
  speed = +e.target.value; document.getElementById("fps").textContent = speed;
});
document.getElementById("zoom").addEventListener("input", e => {
  zoom = +e.target.value; document.getElementById("zl").textContent = zoom; resize();
});
document.getElementById("play").addEventListener("change", e => { playing = e.target.checked; });
document.getElementById("bgsel").addEventListener("change", e => {
  for (const td of document.querySelectorAll("td.cell")) td.style.background = e.target.value;
});
loadSheets().then(() => { buildGrid(); resize(); requestAnimationFrame(tick); });
</script>
</body>
</html>
"""


# ------------------------------------------------------------------ ساخت بسته


def dump_json(path, data):
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, indent=2, ensure_ascii=False)
        fh.write("\n")


def fa_num(n):
    """عدد فارسی برای متن‌های راهنما (۸ / ۶)."""
    return {4: "چهار", 6: "شش", 8: "هشت", 10: "ده", 12: "دوازده", 14: "چهارده"}.get(n, str(n))


def write_text(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(text)


def pack_header(anim):
    a = ANIMS[anim]
    return {
        "name": "enemy_" + anim,
        "style": "semi-realistic military pixel art (same canvas/rig as military_soldier)",
        "animation": anim,
        "label": a["label"],
        "framesPerSheet": a["frames"],
        "fps": a["fps"],
        "loop": a["loop"],
        "loopNote": ("all %d frames are unique; cycle with the frame index modulo the count"
                     % a["frames"]) if a["loop"]
                    else ("one-shot: play frames 0..%d once and hold the last frame"
                          % (a["frames"] - 1)),
        "background": "transparent",
        "directions": list(DIRS),
        "groundOffsetFromBottom": PAD_BOTTOM,
        "note": a["doc"],
    }


def death_readme(pack):
    """راهنمای بستهٔ مرگ (فقط برای انیمیشن مرگ نوشته می‌شود)."""
    rows = []
    for uid, info in pack["units"].items():
        fw, fh = info["frameSize"]["w"], info["frameSize"]["h"]
        note = ""
        if uid == "armored":
            note = "خودرو از کار می‌افتد (از هم نمی‌پاشد)"
        elif uid == "healer":
            note = "فریم 64x72 — ردای بلندتر"
        elif uid == "commander":
            note = "پرچم روی زمین کاشته می‌شود"
        elif uid == "archer":
            note = "شنلِ گیلی روی زمین پهن می‌شود"
        rows.append("| `%s` | %s | %d×%d | %s |"
                    % (uid, info["label"], fw, fh, note or "—"))
    return """# Enemy Death — Top-Down Death Sprite Sheets

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
%s

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
""" % ("\n".join(rows))


def tp_json(name, anim, fw, fh, ground):
    a = ANIMS[anim]
    frames, keys = {}, []
    for i in range(a["frames"]):
        key = "%s_%02d" % (name, i)
        keys.append(key)
        frames[key] = {
            "frame": {"x": i * fw, "y": 0, "w": fw, "h": fh},
            "rotated": False, "trimmed": False,
            "spriteSourceSize": {"x": 0, "y": 0, "w": fw, "h": fh},
            "sourceSize": {"w": fw, "h": fh},
            "pivot": {"x": 0.5, "y": round(ground / float(fh), 4)},
        }
    return {
        "frames": frames,
        "animations": {name: keys},
        "meta": {
            "app": "Dezhban enemy sprite generator",
            "version": "1.1",
            "image": name + ".png",
            "format": "RGBA8888",
            "size": {"w": fw * a["frames"], "h": fh},
            "scale": "1",
            "frameTags": [{"name": name, "from": 0, "to": a["frames"] - 1,
                           "direction": "loop" if a["loop"] else "once",
                           "fps": a["fps"], "label": a["label"]}],
        },
    }


def load_pack(path):
    if os.path.exists(path):
        try:
            with open(path, encoding="utf-8") as fh:
                return json.load(fh)
        except (OSError, ValueError):
            return None
    return None


def build(anim, only=None, verbose=True, previews=True):
    """بستهٔ یک انیمیشن را می‌سازد؛ با only فقط همان نیرو به‌روز می‌شود."""
    a = ANIMS[anim]
    prefix = "%s_" % anim
    ids = [only] if only else list(UNITS)
    out = out_dir(anim)
    os.makedirs(out, exist_ok=True)
    os.makedirs(os.path.join(out, "preview"), exist_ok=True)
    index_path = os.path.join(out, a["index"])
    pack = load_pack(index_path) or {"units": {}, "sheets": []}
    pack.update(pack_header(anim))
    pack["units"] = {k: v for k, v in pack.get("units", {}).items() if k not in ids}
    pack["sheets"] = [sh for sh in pack.get("sheets", []) if sh.get("unit") not in ids]
    all_frames = {}
    for unit_id in ids:
        geom = fit_size(unit_id, anim)
        frames = render_unit(unit_id, anim, geom)
        all_frames[unit_id] = frames
        fw, fh, ground = geom["w"], geom["h"], geom["ground"]
        pack["units"][unit_id] = {"label": unit_spec(unit_id).get("label", unit_id),
                                  "frameSize": {"w": fw, "h": fh},
                                  "groundY": ground, "frames": a["frames"]}
        for direction in DIRS:
            name = "%s_%s_%s" % (unit_id, prefix.rstrip("_"), direction)
            sheet = Image.new("RGBA", (fw * a["frames"], fh), (0, 0, 0, 0))
            for i, fr in enumerate(frames[direction]):
                sheet.paste(fr, (i * fw, 0))
            sheet.save(os.path.join(out, name + ".png"))
            dump_json(os.path.join(out, name + ".json"),
                      tp_json(name, anim, fw, fh, ground))
            pack["sheets"].append({
                "file": name + ".png", "atlas": name + ".json",
                "unit": unit_id, "animation": anim, "direction": direction,
                "frames": a["frames"], "fps": a["fps"], "loop": a["loop"],
                "size": {"w": fw * a["frames"], "h": fh},
            })
            if verbose:
                print("  %-26s %4dx%-4d  (%d فریم %dx%d)"
                      % (name, fw * a["frames"], fh, a["frames"], fw, fh))
    order = [u for u in UNITS if u in pack["units"]] + \
            [u for u in pack["units"] if u not in UNITS]
    pack["units"] = {u: pack["units"][u] for u in order}
    pack["sheets"].sort(key=lambda sh: (order.index(sh["unit"]), DIRS.index(sh["direction"])))
    dump_json(index_path, pack)
    if previews and not only:
        contact_sheet(all_frames, anim).save(
            os.path.join(out, "preview", "contact_sheet.png"))
        preview_gif(all_frames, os.path.join(out, "preview", "%s.gif" % anim), anim)
        write_text(os.path.join(out, "preview", "README.txt"),
                   "contact_sheet.png  همهٔ شیت‌ها یک‌جا (هر نیرو، چهار جهت)\n"
                   "%s.gif            پیش‌نمایش متحرک %s فریمی\n"
                   "player:  ../index.html\n" % (anim, fa_num(a["frames"])))
    sizes = {u: [pack["units"][u]["frameSize"]["w"], pack["units"][u]["frameSize"]["h"]]
             for u in order}
    html = (HTML
            .replace("__TITLE__", a["title"])
            .replace("__H1__", a["h1"])
            .replace("__ANIM__", anim)
            .replace("__FRAMES__", str(a["frames"]))
            .replace("__FPS__", str(a["fps"]))
            .replace("__LOOP__", "true" if a["loop"] else "false")
            .replace("__HINT__", a["doc"])
            .replace("__UNITS__", json.dumps(order))
            .replace("__SIZES__", json.dumps(sizes)))
    write_text(os.path.join(out, "index.html"), html)
    if anim == "death":
        # راهنمای بستهٔ مرگ هم مثل بقیهٔ خروجی‌ها بازتولید می‌شود
        # (README بستهٔ دویدن دست‌نویس است و دست نمی‌خورد)
        write_text(os.path.join(out, "README.md"), death_readme(pack))
    write_text(os.path.join(out, ".gdignore"), "")
    return pack, all_frames


# ------------------------------------------------------------------ خط فرمان


def main(argv=None):
    ap = argparse.ArgumentParser(description="مولد شیت اسپرایت نیروها (دویدن و افتادن)")
    ap.add_argument("--anim", default="both", choices=("run", "death", "both"),
                    help="کدام انیمیشن ساخته/بررسی شود")
    ap.add_argument("--only", default=None, help="فقط یک نیرو: " + ", ".join(UNITS))
    ap.add_argument("--check", action="store_true", help="فقط بررسی بستهٔ ساخته‌شده")
    ap.add_argument("--no-preview", action="store_true", help="بدون گیف و برگهٔ نمونه")
    ap.add_argument("--measure", action="store_true", help="فقط چاپ اندازهٔ فریم هر نیرو")
    ap.add_argument("--debug", nargs=3, metavar=("UNIT", "DIR", "FRAME"),
                    help="رسم یک فریم با بزرگ‌نمایی")
    ap.add_argument("--zoom", type=int, default=6)
    ap.add_argument("--out", default=None, help="مسیر تصویر فریم آزمایشی")
    args = ap.parse_args(argv)
    anims = ("run", "death") if args.anim == "both" else (args.anim,)

    if args.check:
        problems = []
        for anim in anims:
            problems += check_pack(anim)
        for pr in problems:
            print("مشکل:", pr)
        print("بررسی تمام شد؛ بدون مشکل." if not problems else "%d مشکل پیدا شد." % len(problems))
        return 1 if problems else 0

    if args.measure:
        for anim in anims:
            print("[%s]" % anim)
            for unit_id in UNITS:
                g = fit_size(unit_id, anim)
                print("  %-10s frame %dx%d  ground=%d  (up=%.1f down=%.1f left=%.1f right=%.1f)"
                      % (unit_id, g["w"], g["h"], g["ground"], g["up"], g["down"],
                         g["left"], g["right"]))
        return 0

    if args.debug:
        unit_id, direction, index = args.debug[0], args.debug[1], int(args.debug[2])
        anim = anims[0]
        geom = fit_size(unit_id, anim)
        img = render_frame(unit_id, anim, direction, index, geom)
        img = img.resize((img.width * args.zoom, img.height * args.zoom), Image.NEAREST)
        path = args.out or "/tmp/enemy_debug.png"
        img.save(path)
        print(path, img.size)
        return 0

    problems = []
    for anim in anims:
        print("ساخت %s..." % ANIMS[anim]["label"])
        build(anim, only=args.only, previews=not args.no_preview)
        problems += check_pack(anim)
    for pr in problems:
        print("مشکل:", pr)
    print("پایان. %s" % ("بدون مشکل." if not problems else "%d مشکل." % len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
