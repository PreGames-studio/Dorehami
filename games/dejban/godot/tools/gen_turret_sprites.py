#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""مولد شیت‌های اسپرایت ۵ برجک دفاعی در ۵ سطح (از ضعیف تا افسانه‌ای).

برای هر یک از ۵ نوع اسلحه (machine_gun, cannon, missile, laser, tesla)،
پنج سطح ارتقا (سطح ۱: ضعیف، سطح ۲: معمولی، سطح ۳: پیشرفته، سطح ۴: حماسی،
سطح ۵: افسانه‌ای) و برای هر سطح ۴ انیمیشن استاندارد (idle 8, aim 8, fire 6,
reload/cooldown 4) ساخته می‌شود:
  جمعاً ۵ × ۵ × ۴ = ۱۰۰ شیت افقی ۶۴×۶۴ با پس‌زمینهٔ شفاف + ۱۰۰ فایل JSON.

ویژگی‌های کلیدی:
  - پایهٔ سنگی/بتنی دایره‌ای با کیسه‌شن‌ها کاملاً ثابت و یکسان در تمام ۱۰۰ شیت و همهٔ فریم‌ها
  - بخش بالایی چرخان با پیشرفت ظاهری واضح از سطح ۱ (ضعیف) تا سطح ۵ (افسانه‌ای)
  - پرتابه و افکت شلیک اختصاصی هر سلاح که با سطح اسلحه قوی‌تر و پرجزئیات‌تر می‌شود
  - لوپ نرم (فریم اول و آخر هر شیت بایت‌به‌بایت یکسان)
  - سازگار با TexturePacker JSON Hash، Construct 3 و Godot 4

اجرا:
    python3 tools/gen_turret_sprites.py            # ساخت کامل ۱۰۰ شیت + جدول تصویری ۵×۵
    python3 tools/gen_turret_sprites.py --check    # تست خودکار همهٔ ۱۰۰ شیت و JSONها
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # pragma: no cover
    print("Pillow لازم است: pip install pillow", file=sys.stderr)
    raise

TAU = math.tau
TOOLS = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(TOOLS)
OUT_ROOT = os.path.join(ROOT, "spritesheets", "turrets")

FRAME = 64
SS = 3
CX = 32.0
CY = 36.0
PIVOT_X = 32.0
PIVOT_Y = 33.5
ISO_Y = 0.78

INK = (18, 20, 24, 255)

TIERS = [
    {
        "level": 1,
        "id": "lv1_weak",
        "en": "Lv.1 Weak",
        "fa": "سطح ۱ — ضعیف",
        "armor_dark": (46, 52, 42, 255),
        "armor_mid": (72, 82, 64, 255),
        "armor_light": (102, 114, 92, 255),
        "metal_dark": (38, 42, 46, 255),
        "metal_mid": (72, 78, 86, 255),
        "metal_light": (116, 124, 134, 255),
        "trim": (138, 126, 98, 255),
        "accent": (160, 175, 190, 255),
    },
    {
        "level": 2,
        "id": "lv2_common",
        "en": "Lv.2 Common",
        "fa": "سطح ۲ — معمولی",
        "armor_dark": (42, 58, 36, 255),
        "armor_mid": (70, 96, 58, 255),
        "armor_light": (106, 138, 86, 255),
        "metal_dark": (36, 40, 48, 255),
        "metal_mid": (78, 86, 98, 255),
        "metal_light": (132, 142, 156, 255),
        "trim": (184, 152, 82, 255),
        "accent": (110, 200, 245, 255),
    },
    {
        "level": 3,
        "id": "lv3_advanced",
        "en": "Lv.3 Advanced",
        "fa": "سطح ۳ — پیشرفته",
        "armor_dark": (36, 50, 64, 255),
        "armor_mid": (62, 84, 106, 255),
        "armor_light": (98, 126, 154, 255),
        "metal_dark": (30, 36, 44, 255),
        "metal_mid": (74, 84, 98, 255),
        "metal_light": (140, 152, 168, 255),
        "trim": (222, 176, 68, 255),
        "accent": (80, 220, 255, 255),
    },
    {
        "level": 4,
        "id": "lv4_epic",
        "en": "Lv.4 Epic",
        "fa": "سطح ۴ — حماسی",
        "armor_dark": (38, 32, 62, 255),
        "armor_mid": (66, 56, 104, 255),
        "armor_light": (106, 92, 156, 255),
        "metal_dark": (28, 30, 42, 255),
        "metal_mid": (68, 74, 96, 255),
        "metal_light": (138, 148, 178, 255),
        "trim": (240, 192, 78, 255),
        "accent": (165, 120, 255, 255),
    },
    {
        "level": 5,
        "id": "lv5_legendary",
        "en": "Lv.5 Legendary",
        "fa": "سطح ۵ — افسانه‌ای",
        "armor_dark": (78, 28, 32, 255),
        "armor_mid": (128, 44, 48, 255),
        "armor_light": (178, 68, 64, 255),
        "metal_dark": (44, 36, 24, 255),
        "metal_mid": (188, 146, 52, 255),
        "metal_light": (250, 218, 110, 255),
        "trim": (255, 232, 130, 255),
        "accent": (110, 245, 255, 255),
    },
]

TURRETS = [
    {
        "id": "machine_gun",
        "title": "Machine Gun",
        "fa": "مسلسل",
        "projectile": "small yellow bullets",
        "anims": [
            ("idle", 8, 8, "Turret slowly scanning left to right"),
            ("aim", 8, 12, "Turret rotating to face target direction"),
            ("fire", 6, 14, "Rapid bullet spray, muzzle flash, yellow sparks, shell casings ejecting"),
            ("reload", 4, 8, "Barrel recoil and return"),
        ],
    },
    {
        "id": "cannon",
        "title": "Cannon",
        "fa": "توپ سنگین",
        "projectile": "large black shell with fire trail",
        "anims": [
            ("idle", 8, 8, "Turret stationary, slight vibration"),
            ("aim", 8, 10, "Turret rotating slowly to face target"),
            ("fire", 6, 12, "Single big shell launch, huge muzzle flash, smoke cloud, barrel recoil"),
            ("reload", 4, 8, "Barrel returning to position, smoke clearing"),
        ],
    },
    {
        "id": "missile",
        "title": "Missile Launcher",
        "fa": "موشک‌انداز",
        "projectile": "white missile with smoke trail",
        "anims": [
            ("idle", 8, 8, "Missile box slowly rotating"),
            ("aim", 8, 10, "Box rotating to face target"),
            ("fire", 6, 12, "Missiles launching one by one with white smoke trails"),
            ("reload", 4, 8, "Empty tubes, smoke clearing"),
        ],
    },
    {
        "id": "laser",
        "title": "Laser",
        "fa": "لیزر",
        "projectile": "bright blue beam",
        "anims": [
            ("idle", 8, 8, "Lens glowing blue, rotating slowly"),
            ("aim", 8, 12, "Lens focusing, rotating to target"),
            ("fire", 6, 14, "Bright blue laser beam shooting forward, energy particles"),
            ("cooldown", 4, 8, "Lens fading back to idle glow"),
        ],
    },
    {
        "id": "tesla",
        "title": "Tesla",
        "fa": "تسلا",
        "projectile": "purple lightning arcs",
        "anims": [
            ("idle", 8, 8, "Coil with small electric sparks, rotating"),
            ("aim", 8, 12, "Coil charging up, rotating to target"),
            ("fire", 6, 14, "Purple lightning bolts shooting from top, electric arcs"),
            ("cooldown", 4, 8, "Sparks fading, coil returning to idle"),
        ],
    },
]


# ------------------------------------------------------------------ ابزار ترسیم ایزومتریک


class Painter:
    def __init__(self, w: int = FRAME, h: int = FRAME, ss: int = SS):
        self.w = w
        self.h = h
        self.ss = ss
        self.img = Image.new("RGBA", (w * ss, h * ss), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    def _pts(self, pts) -> list[tuple[float, float]]:
        return [(x * self.ss, y * self.ss) for x, y in pts]

    def _w(self, width: float) -> int:
        return max(1, int(round(width * self.ss)))

    def ellipse(self, cx: float, cy: float, rx: float, ry: float, fill=None, outline=None, width: float = 1.0):
        box = [(cx - rx) * self.ss, (cy - ry) * self.ss, (cx + rx) * self.ss, (cy + ry) * self.ss]
        self.d.ellipse(box, fill=fill, outline=outline, width=self._w(width))

    def poly(self, pts, fill=None, outline=None, width: float = 1.0):
        self.d.polygon(self._pts(pts), fill=fill, outline=outline, width=self._w(width))

    def line(self, pts, fill=None, width: float = 1.0):
        self.d.line(self._pts(pts), fill=fill, width=self._w(width), joint="curve")

    def finish(self) -> Image.Image:
        out = self.img.resize((self.w, self.h), Image.LANCZOS)
        px = out.load()
        for y in range(self.h):
            for x in range(self.w):
                if x < 2 or x >= self.w - 2 or y < 2 or y >= self.h - 2:
                    px[x, y] = (0, 0, 0, 0)
                elif px[x, y][3] < 18:
                    px[x, y] = (0, 0, 0, 0)
        return out


def rot_pt(cx: float, cy: float, angle: float, along: float, side: float, z: float = 0.0) -> tuple[float, float]:
    ca = math.cos(angle)
    sa = math.sin(angle)
    wx = along * ca - side * sa
    wy = (along * sa + side * ca) * ISO_Y
    return (cx + wx, cy + wy - z * 0.72)


def rot_box(cx: float, cy: float, angle: float, a0: float, a1: float, s0: float, s1: float, z: float = 0.0):
    return [
        rot_pt(cx, cy, angle, a0, s0, z),
        rot_pt(cx, cy, angle, a1, s0, z),
        rot_pt(cx, cy, angle, a1, s1, z),
        rot_pt(cx, cy, angle, a0, s1, z),
    ]


def draw_prism(p: Painter, cx: float, cy: float, angle: float,
               a0: float, a1: float, s0: float, s1: float, z0: float, z1: float,
               top_col, side_col, dark_col, outline=INK):
    b = rot_box(cx, cy, angle, a0, a1, s0, s1, z0)
    t = rot_box(cx, cy, angle, a0, a1, s0, s1, z1)
    faces = []
    for i in range(4):
        j = (i + 1) % 4
        mid_y = (b[i][1] + b[j][1] + t[i][1] + t[j][1]) * 0.25
        faces.append((mid_y, i, j))
    faces.sort(key=lambda item: item[0])
    for idx, (_, i, j) in enumerate(faces[1:]):
        col = side_col if idx == 1 else dark_col
        p.poly([b[i], b[j], t[j], t[i]], fill=col, outline=outline, width=0.8)
    p.poly(t, fill=top_col, outline=outline, width=0.9)


def draw_cylinder_along(p: Painter, cx: float, cy: float, angle: float,
                        a0: float, a1: float, side_offset: float, z: float, radius: float,
                        body_col, hi_col, muzzle_col=INK):
    p0 = rot_pt(cx, cy, angle, a0, side_offset, z)
    p1 = rot_pt(cx, cy, angle, a1, side_offset, z)
    p.line([p0, p1], fill=INK, width=radius * 2.2 + 1.3)
    p.line([p0, p1], fill=body_col, width=radius * 2.0)
    h0 = rot_pt(cx, cy, angle, a0 + 0.4, side_offset - radius * 0.3, z + radius * 0.45)
    h1 = rot_pt(cx, cy, angle, a1 - 0.4, side_offset - radius * 0.3, z + radius * 0.45)
    p.line([h0, h1], fill=hi_col, width=max(0.7, radius * 0.68))
    p.ellipse(p1[0], p1[1], radius * 0.95, radius * 0.85, fill=muzzle_col, outline=INK, width=0.65)


# ------------------------------------------------------------------ ۱. پایهٔ سنگی ثابت و مشترک

_STONE_BASE: Image.Image | None = None


def get_stone_base() -> Image.Image:
    """پایهٔ سنگی/بتنی دایره‌ای با کیسه‌شن‌ها؛ ۱۰۰٪ ثابت و یکسان در تمام فریم‌ها و سطوح."""
    global _STONE_BASE
    if _STONE_BASE is not None:
        return _STONE_BASE

    stone_dark = (62, 66, 74, 255)
    stone_mid = (102, 108, 118, 255)
    stone_light = (144, 152, 162, 255)
    sand_dark = (114, 92, 58, 255)
    sand_mid = (162, 134, 88, 255)
    sand_light = (202, 174, 122, 255)
    ring_dark = (36, 40, 48, 255)
    ring_mid = (72, 80, 92, 255)

    p = Painter()
    p.ellipse(CX, CY + 3.2, 24.5, 19.2, fill=(0, 0, 0, 92))
    p.ellipse(CX, CY + 1.8, 22.5, 17.5, fill=stone_dark, outline=INK, width=1.1)
    p.ellipse(CX, CY - 0.8, 21.8, 16.8, fill=stone_mid, outline=INK, width=1.0)
    p.ellipse(CX - 0.8, CY - 1.5, 19.6, 14.8, fill=stone_light)
    p.ellipse(CX + 0.4, CY - 0.5, 18.8, 14.2, fill=stone_mid)

    for k in range(8):
        ang = k * (TAU / 8.0) + 0.2
        r0, r1 = 12.0, 21.2
        x0, y0 = CX + math.cos(ang) * r0, CY - 0.8 + math.sin(ang) * r0 * ISO_Y
        x1, y1 = CX + math.cos(ang) * r1, CY - 0.8 + math.sin(ang) * r1 * ISO_Y
        p.line([(x0, y0), (x1, y1)], fill=stone_dark, width=0.8)

    bags = []
    for k in range(10):
        ang = k * (TAU / 10.0) + 0.15
        bx = CX + math.cos(ang) * 18.6
        by = (CY - 1.2) + math.sin(ang) * 18.6 * ISO_Y
        bags.append((by, bx, ang))
    bags.sort(key=lambda item: item[0])
    for by, bx, ang in bags:
        tang = ang + math.pi * 0.5
        dx = math.cos(tang) * 3.6
        dy = math.sin(tang) * 3.6 * ISO_Y
        nx = -math.sin(tang) * 2.1
        ny = math.cos(tang) * 2.1 * ISO_Y
        pts_shadow = [
            (bx - dx + nx, by - dy + ny + 1.1),
            (bx + dx + nx, by + dy + ny + 1.1),
            (bx + dx - nx, by + dy - ny + 1.1),
            (bx - dx - nx, by - dy - ny + 1.1),
        ]
        p.poly(pts_shadow, fill=(15, 16, 20, 120))
        pts = [
            (bx - dx * 0.9 + nx, by - dy * 0.9 + ny - 0.6),
            (bx + dx * 0.9 + nx, by + dy * 0.9 + ny - 0.6),
            (bx + dx - nx, by + dy - ny - 0.3),
            (bx - dx - nx, by - dy - ny - 0.3),
        ]
        col = sand_light if math.sin(ang) < 0.0 else sand_mid
        p.poly(pts, fill=col, outline=sand_dark, width=0.85)
        p.line([(bx - nx * 0.85, by - ny * 0.85 - 0.5), (bx + nx * 0.85, by + ny * 0.85 - 0.5)],
               fill=sand_dark, width=0.7)

    p.ellipse(PIVOT_X, PIVOT_Y + 0.8, 12.6, 9.8, fill=ring_dark, outline=INK, width=1.0)
    p.ellipse(PIVOT_X, PIVOT_Y + 0.2, 11.2, 8.6, fill=ring_mid, outline=INK, width=0.8)
    for k in range(4):
        ang = k * (TAU / 4.0) + math.pi * 0.25
        rx = PIVOT_X + math.cos(ang) * 11.8
        ry = PIVOT_Y + 0.4 + math.sin(ang) * 11.8 * ISO_Y
        p.ellipse(rx, ry, 1.1, 0.9, fill=(95, 195, 250, 215))

    _STONE_BASE = p.finish()
    return _STONE_BASE


# ------------------------------------------------------------------ ۲. وضعیت انیمیشن


def frame_state(turret_id: str, anim: str, frame_idx: int, total_frames: int) -> dict:
    if frame_idx == total_frames - 1:
        frame_idx = 0

    base_angle = -0.32
    state = {
        "angle": base_angle,
        "recoil": 0.0,
        "vib": (0.0, 0.0),
        "flash": 0.0,
        "fire_step": 0,
        "reload_step": 0,
        "charge": 0.25,
        "phase": frame_idx / float(max(1, total_frames - 1)),
    }

    if anim == "idle":
        scan_lookup = [0.0, -0.22, -0.38, -0.20, 0.0, 0.22, 0.36, 0.0]
        if turret_id == "cannon":
            vib_lookup = [(0.0, 0.0), (0.4, -0.3), (-0.4, 0.2), (0.3, 0.3),
                          (0.0, -0.4), (-0.3, 0.2), (0.4, -0.2), (0.0, 0.0)]
            state["angle"] = base_angle + scan_lookup[frame_idx] * 0.15
            state["vib"] = vib_lookup[frame_idx]
        else:
            state["angle"] = base_angle + scan_lookup[frame_idx]
        state["charge"] = 0.25 + 0.15 * math.sin(frame_idx * TAU / 7.0)

    elif anim == "aim":
        aim_angles = [-0.32, -0.68, -0.95, -0.55, -0.12, 0.18, -0.08, -0.32]
        charges = [0.25, 0.35, 0.50, 0.68, 0.85, 0.96, 0.60, 0.25]
        state["angle"] = aim_angles[frame_idx]
        state["charge"] = charges[frame_idx]

    elif anim == "fire":
        recoil_table = [0.0, 1.8, 3.6, 2.4, 1.0, 0.0]
        flash_table = [0.0, 0.85, 1.0, 0.75, 0.30, 0.0]
        charge_table = [0.75, 0.95, 1.0, 0.90, 0.80, 0.75]
        state["recoil"] = recoil_table[frame_idx]
        state["flash"] = flash_table[frame_idx]
        state["fire_step"] = frame_idx
        state["charge"] = charge_table[frame_idx]

    elif anim in ("reload", "cooldown"):
        recoil_table = [0.0, 3.2, 1.4, 0.0]
        charge_table = [0.25, 0.06, 0.15, 0.25]
        state["recoil"] = recoil_table[frame_idx]
        state["reload_step"] = frame_idx
        state["charge"] = charge_table[frame_idx]

    return state


def draw_tier_emblem(p: Painter, cx: float, cy: float, ang: float, level: int, z_top: float, st: dict):
    """نشان سطح (از ۱ تا ۵) و تزئینات حماسی/افسانه‌ای روی سقف برجک."""
    if level >= 4:
        # هالهٔ رون یا تاج طلایی روی سقف برجک برای سطح ۴ و ۵
        crown = rot_pt(cx, cy, ang, -2.5, 0.0, z_top + 0.4)
        r_c = 2.8 if level == 4 else 3.8
        p.ellipse(crown[0], crown[1], r_c, r_c * ISO_Y, fill=st["trim"], outline=INK, width=0.65)
        p.ellipse(crown[0], crown[1], r_c * 0.55, r_c * 0.55 * ISO_Y, fill=st["accent"])


# ------------------------------------------------------------------ ۳. رسم ۵ خانوادهٔ سلاح در ۵ سطح (۱ تا ۵)


def draw_machine_gun(p: Painter, st: dict, s: dict, level: int):
    ang = s["angle"]
    rec = s["recoil"]
    cx, cy = PIVOT_X + s["vib"][0], PIVOT_Y + s["vib"][1]

    # ابعاد اتاقک با سطح بزرگ‌تر و زره‌پوش‌تر می‌شود
    half_w = 5.0 + level * 0.45
    z_top = 6.2 + level * 0.45
    draw_prism(p, cx, cy, ang, -6.5 - level * 0.3, 4.8 + level * 0.2, -half_w, half_w, 1.0, z_top,
               st["armor_light"], st["armor_mid"], st["armor_dark"])

    # جعبهٔ مهمات جانبی (از سطح ۲ به بالا در یک یا هر دو طرف)
    if level >= 2:
        draw_prism(p, cx, cy, ang, -4.5, 2.5, half_w, half_w + 2.4, 1.8, z_top - 1.0,
                   st["trim"], st["metal_mid"], st["metal_dark"])
    if level >= 4:
        draw_prism(p, cx, cy, ang, -4.5, 2.5, -half_w - 2.4, -half_w, 1.8, z_top - 1.0,
                   st["trim"], st["metal_mid"], st["metal_dark"])

    # لوله‌های مسلسل بر اساس سطح:
    # سطح ۱: تک‌لول سبک | سطح ۲ و ۳: دولول | سطح ۴: گتلینگ ۳ لول | سطح ۵: گتلینگ افسانه‌ای ۵ لول با طوقهٔ طلایی
    barrel_len = 15.5 + level * 1.0
    if level == 1:
        offsets = [0.0]
    elif level in (2, 3):
        offsets = [-2.3, 2.3]
    elif level == 4:
        offsets = [-3.0, 0.0, 3.0]
    else:
        offsets = [-3.8, -1.9, 0.0, 1.9, 3.8]

    for idx, b_side in enumerate(offsets):
        r_mod = rec * (0.95 if (idx + s["fire_step"]) % 2 == 0 else 0.45)
        b_rad = 1.25 if level <= 3 else (1.15 if level == 4 else 1.1)
        draw_cylinder_along(p, cx, cy, ang, 3.8 - r_mod, barrel_len - r_mod, b_side, 4.4 + level * 0.2, b_rad,
                            st["metal_mid"], st["metal_light"])

    if level >= 3:
        # سپر زرهی جلوی لوله و طوقهٔ گتلینگ در سطوح بالا
        draw_prism(p, cx, cy, ang, 3.5, 5.6, -half_w + 0.6, half_w - 0.6, 2.0, z_top - 0.3,
                   st["trim"], st["metal_mid"], st["metal_dark"])
    if level >= 4:
        clamp_pt = rot_pt(cx, cy, ang, barrel_len - 3.2 - rec * 0.6, 0.0, 4.6 + level * 0.2)
        p.ellipse(clamp_pt[0], clamp_pt[1], 3.4 + (level - 4) * 0.9, 2.6, fill=st["trim"], outline=INK, width=0.7)

    draw_tier_emblem(p, cx, cy, ang, level, z_top, st)

    step = s["fire_step"]
    if step in (1, 2, 3, 4):
        active_side = offsets[(step - 1) % len(offsets)]
        tip = rot_pt(cx, cy, ang, barrel_len + 0.8 - rec * 0.5, active_side, 4.4 + level * 0.2)
        fl_r = (2.8 + level * 0.45) * s["flash"]
        p.ellipse(tip[0], tip[1], fl_r * 1.3, fl_r, fill=(255, 165, 35, 240))
        p.ellipse(tip[0], tip[1], fl_r * 0.75, fl_r * 0.6, fill=(255, 248, 170, 255))
        for sp_ang, sp_d in [(-0.45, 4.0 + level * 0.4), (0.42, 4.4 + level * 0.4), (0.0, 5.4 + level * 0.4)]:
            sp = rot_pt(cx, cy, ang + sp_ang * 0.35, barrel_len + sp_d, active_side, 4.4 + level * 0.2)
            p.line([tip, sp], fill=(255, 232, 85, 245), width=1.0 + level * 0.1)

        # تعداد گلوله‌های زرد با سطح افزایش می‌یابد
        bullet_count = min(4, 1 + level // 2)
        for b_idx in range(bullet_count):
            b_dist = barrel_len + 2.5 + step * 1.8 + b_idx * 3.0
            if b_dist <= 30.2:
                b_side = offsets[(b_idx + step) % len(offsets)]
                b0 = rot_pt(cx, cy, ang, b_dist - 1.8, b_side, 4.4 + level * 0.2)
                b1 = rot_pt(cx, cy, ang, b_dist + 1.0, b_side, 4.4 + level * 0.2)
                p.line([b0, b1], fill=(255, 212, 42, 255), width=1.6 + level * 0.12)
                p.ellipse(b1[0], b1[1], 1.2 + level * 0.08, 1.0 + level * 0.06,
                          fill=(255, 252, 180, 255), outline=INK, width=0.45)

        # پوکه‌های برنجی در حال پرتاب به بیرون
        for c_idx in range(1 + level // 2):
            cas_along = -1.0 - step * 1.1 - c_idx * 1.6
            cas_side = -half_w - 1.5 - step * 1.4 - c_idx * 1.3
            cas_z = 5.8 + (2.0 - abs(step - 2.5)) * 1.4
            cp = rot_pt(cx, cy, ang, cas_along, cas_side, cas_z)
            p.ellipse(cp[0], cp[1], 1.4, 0.85, fill=(242, 198, 72, 255), outline=INK, width=0.45)

    if s["reload_step"] in (1, 2):
        sm = rot_pt(cx, cy, ang, barrel_len - 1.5 - rec, 0.0, 5.8 + s["reload_step"] * 1.4)
        alpha = 160 if s["reload_step"] == 1 else 95
        p.ellipse(sm[0], sm[1], 2.6 + level * 0.2, 2.0 + level * 0.15, fill=(195, 200, 208, alpha))


def draw_cannon(p: Painter, st: dict, s: dict, level: int):
    ang = s["angle"]
    rec = s["recoil"]
    cx, cy = PIVOT_X + s["vib"][0], PIVOT_Y + s["vib"][1]

    half_w = 6.2 + level * 0.5
    z_top = 7.2 + level * 0.45
    draw_prism(p, cx, cy, ang, -7.5 - level * 0.35, 5.5 + level * 0.25, -half_w, half_w, 1.0, z_top,
               st["armor_light"], st["armor_mid"], st["armor_dark"])

    if level >= 3:
        # زره تقویتی طرفین توپخانه
        draw_prism(p, cx, cy, ang, -5.5, 3.5, half_w, half_w + 2.2, 1.6, z_top - 1.2,
                   st["trim"], st["armor_mid"], st["armor_dark"])
        draw_prism(p, cx, cy, ang, -5.5, 3.5, -half_w - 2.2, -half_w, 1.6, z_top - 1.2,
                   st["trim"], st["armor_mid"], st["armor_dark"])

    hatch = rot_pt(cx, cy, ang, -2.2, -1.2, z_top + 0.2)
    p.ellipse(hatch[0], hatch[1], 2.8 + level * 0.2, 2.2 + level * 0.15, fill=st["trim"], outline=INK, width=0.75)

    barrel_len = 16.5 + level * 0.85
    barrel_rad = 2.3 + level * 0.25
    # در سطح ۴ توپ دوقلو و در سطح ۵ ابرتوپ افسانه‌ای با دو لولهٔ کمکی و دهانهٔ طلایی
    barrels = [0.0] if level <= 3 else ([-2.6, 2.6] if level == 4 else [0.0])
    if level == 5:
        for side_aux in (-4.4, 4.4):
            draw_cylinder_along(p, cx, cy, ang, 3.5 - rec * 0.6, 14.5 - rec * 0.6, side_aux, 4.6, 1.4,
                                st["trim"], st["metal_light"])
    for b_side in barrels:
        draw_cylinder_along(p, cx, cy, ang, 3.8 - rec, barrel_len - rec, b_side, 5.0 + level * 0.15, barrel_rad,
                            st["metal_dark"], st["metal_light"])
        if level >= 2:
            draw_cylinder_along(p, cx, cy, ang, barrel_len - 3.0 - rec, barrel_len + 0.4 - rec, b_side,
                                5.0 + level * 0.15, barrel_rad + 0.55, st["trim"], st["metal_light"])

    draw_tier_emblem(p, cx, cy, ang, level, z_top, st)

    step = s["fire_step"]
    if step in (1, 2, 3, 4):
        tip = rot_pt(cx, cy, ang, barrel_len + 0.8 - rec, 0.0, 5.2)
        if step in (1, 2):
            fr = (4.6 + level * 0.5) if step == 2 else (3.6 + level * 0.4)
            p.ellipse(tip[0], tip[1], fr * 1.25, fr, fill=(255, 110, 20, 242))
            p.ellipse(tip[0], tip[1], fr * 0.78, fr * 0.65, fill=(255, 232, 105, 255))
        for sm_i, (sa, ss_off, sr) in enumerate([(1.8, -3.0, 3.0 + level * 0.2), (3.2, 2.8, 2.8 + level * 0.2), (4.6, 0.0, 3.4 + level * 0.25)]):
            sp = rot_pt(cx, cy, ang, barrel_len - 1.5 + sa + step * 0.8, ss_off, 5.8 + sm_i * 0.8)
            al = max(70, 215 - step * 32)
            p.ellipse(sp[0], sp[1], sr, sr * 0.8, fill=(148, 152, 160, al))

        shell_dist = barrel_len + 1.5 + step * 1.9
        if shell_dist <= 30.0:
            for b_side in barrels:
                tail = rot_pt(cx, cy, ang, shell_dist - (3.0 + level * 0.35), b_side, 5.2)
                sh_pos = rot_pt(cx, cy, ang, shell_dist, b_side, 5.2)
                p.line([tail, sh_pos], fill=(255, 128, 24, 242), width=2.6 + level * 0.25)
                p.line([rot_pt(cx, cy, ang, shell_dist - 1.8, b_side, 5.2), sh_pos],
                       fill=(255, 240, 130, 255), width=1.5 + level * 0.15)
                sh_r = 2.1 + level * 0.18
                p.ellipse(sh_pos[0], sh_pos[1], sh_r, sh_r * 0.86, fill=(22, 24, 28, 255),
                          outline=(255, 195, 70, 235), width=0.7)

    if s["reload_step"] in (1, 2):
        for ss_off in (-2.4, 2.2):
            sp = rot_pt(cx, cy, ang, barrel_len - 2.0 - rec, ss_off, 6.2 + s["reload_step"] * 1.4)
            al = 175 if s["reload_step"] == 1 else 95
            p.ellipse(sp[0], sp[1], 3.0 + level * 0.2, 2.4 + level * 0.15, fill=(168, 172, 178, al))


def draw_missile(p: Painter, st: dict, s: dict, level: int):
    ang = s["angle"]
    rec = s["recoil"] * 0.45
    cx, cy = PIVOT_X + s["vib"][0], PIVOT_Y + s["vib"][1]

    draw_prism(p, cx, cy, ang, -5.2, 4.2, -5.2, 5.2, 1.0, 4.6,
               st["metal_mid"], st["metal_dark"], INK)

    box_w = 6.2 + level * 0.55
    box_z1 = 10.6 + level * 0.55
    draw_prism(p, cx, cy, ang, -6.8 - level * 0.25 - rec, 7.8 + level * 0.2 - rec,
               -box_w, box_w, 4.0, box_z1,
               st["armor_light"], st["armor_mid"], st["armor_dark"])
    draw_prism(p, cx, cy, ang, 5.0 - rec, 7.6 + level * 0.2 - rec,
               -box_w + 0.2, box_w - 0.2, box_z1 - 1.0, box_z1 + 0.15,
               st["trim"], st["metal_mid"], st["metal_dark"])

    if level >= 3:
        # دیش رادار یا گنبد هدایت روی سقف موشک‌انداز
        rad = rot_pt(cx, cy, ang, -2.2 - rec, 0.0, box_z1 + 1.2)
        p.ellipse(rad[0], rad[1], 2.6 + (level - 3) * 0.5, 2.0 + (level - 3) * 0.35,
                  fill=st["accent"], outline=INK, width=0.7)

    # تعداد تیوب‌های موشک بر اساس سطح (۲، ۴، ۴، ۶، ۶ تیوب)
    if level == 1:
        tube_offsets = [(-2.6, 7.4), (2.6, 7.4)]
    elif level in (2, 3):
        tube_offsets = [(-3.4, 9.2), (3.4, 9.2), (-3.4, 6.2), (3.4, 6.2)]
    else:
        tube_offsets = [(-4.8, 9.8), (0.0, 9.8), (4.8, 9.8), (-4.8, 6.4), (0.0, 6.4), (4.8, 6.4)]

    step = s["fire_step"]
    r_step = s["reload_step"]
    front_x = 7.9 + level * 0.2 - rec
    for idx, (tside, tz) in enumerate(tube_offsets):
        tp = rot_pt(cx, cy, ang, front_x, tside, tz)
        p.ellipse(tp[0], tp[1], 1.75, 1.5, fill=(20, 24, 28, 255), outline=INK, width=0.65)
        has_warhead = True
        if r_step == 1:
            has_warhead = False
        elif r_step == 2 and idx >= len(tube_offsets) // 2:
            has_warhead = False
        elif step in (2, 3, 4) and idx < step - 1:
            has_warhead = False
        if has_warhead:
            p.ellipse(tp[0], tp[1], 1.1, 0.95, fill=(244, 248, 252, 255))
            tip_col = (225, 55, 45, 255) if level < 5 else (255, 195, 45, 255)
            p.ellipse(tp[0], tp[1], 0.55, 0.48, fill=tip_col)

    if step in (1, 2, 3, 4):
        max_active = 1 if level == 1 else (2 if level in (2, 3) else 3)
        active_indices = [(step - 1 + k) % len(tube_offsets) for k in range(min(max_active, step))]
        for m_i, t_idx in enumerate(active_indices):
            tside, tz = tube_offsets[t_idx]
            tube_tip = rot_pt(cx, cy, ang, front_x + 0.5, tside, tz)
            p.ellipse(tube_tip[0], tube_tip[1], 2.2, 1.8, fill=(255, 185, 55, 225))
            m_dist = 12.8 + (step + m_i * 1.3) * 2.8
            if m_dist <= 30.0:
                for sm_k in range(3):
                    sm_d = m_dist - 2.3 - sm_k * 2.2
                    smp = rot_pt(cx, cy, ang, sm_d, tside, tz)
                    p.ellipse(smp[0], smp[1], 2.0 - sm_k * 0.28, 1.6 - sm_k * 0.22,
                              fill=(242, 246, 252, 218 - sm_k * 45))
                m_tail = rot_pt(cx, cy, ang, m_dist - 2.4, tside, tz)
                m_head = rot_pt(cx, cy, ang, m_dist + 1.2, tside, tz)
                p.line([m_tail, m_head], fill=(246, 250, 255, 255), width=2.0 + level * 0.1)
                p.ellipse(m_head[0], m_head[1], 1.2, 1.0, fill=(228, 52, 42, 255))

    if r_step in (1, 2):
        for tside in (-3.2, 3.2):
            sp = rot_pt(cx, cy, ang, 11.2, tside, 8.2 + r_step * 1.2)
            al = 175 if r_step == 1 else 90
            p.ellipse(sp[0], sp[1], 2.7, 2.0, fill=(230, 236, 242, al))


def draw_laser(p: Painter, st: dict, s: dict, level: int):
    ang = s["angle"]
    cx, cy = PIVOT_X + s["vib"][0], PIVOT_Y + s["vib"][1]
    charge = s["charge"]

    half_w = 5.4 + level * 0.45
    z_top = 6.8 + level * 0.4
    draw_prism(p, cx, cy, ang, -6.8 - level * 0.25, 5.0 + level * 0.2, -half_w, half_w, 1.0, z_top,
               st["armor_light"], st["armor_mid"], st["armor_dark"])

    if level >= 3:
        # باله‌های خنک‌کننده/منشور جانبی در سطوح ۳ تا ۵
        draw_prism(p, cx, cy, ang, -4.0, 3.5, half_w, half_w + 2.0, 2.2, z_top - 0.8,
                   st["trim"], st["metal_mid"], st["metal_dark"])
        draw_prism(p, cx, cy, ang, -4.0, 3.5, -half_w - 2.0, -half_w, 2.2, z_top - 0.8,
                   st["trim"], st["metal_mid"], st["metal_dark"])

    core = rot_pt(cx, cy, ang, -1.5, 0.0, z_top + 0.4)
    glow_a = int(130 + 120 * charge)
    core_r = 2.8 + level * 0.35
    p.ellipse(core[0], core[1], core_r, core_r * 0.78, fill=(35, 140, 255, glow_a), outline=INK, width=0.7)
    p.ellipse(core[0], core[1], core_r * 0.55, core_r * 0.45, fill=(195, 245, 255, glow_a))

    emitter_len = 12.0 + level * 0.65
    draw_cylinder_along(p, cx, cy, ang, 4.0, emitter_len, 0.0, 5.0 + level * 0.15, 2.4 + level * 0.22,
                        st["metal_mid"], st["metal_light"], muzzle_col=(20, 68, 145, 255))
    if level >= 4:
        ring_pt = rot_pt(cx, cy, ang, emitter_len - 2.2, 0.0, 5.0 + level * 0.15)
        p.ellipse(ring_pt[0], ring_pt[1], 3.4, 2.8, fill=st["trim"], outline=INK, width=0.65)

    lens = rot_pt(cx, cy, ang, emitter_len + 0.3, 0.0, 5.0 + level * 0.15)
    lens_r = 2.0 + level * 0.22 + 0.75 * charge
    p.ellipse(lens[0], lens[1], lens_r * 1.2, lens_r, fill=(50, 168, 255, int(160 + 90 * charge)))
    p.ellipse(lens[0], lens[1], lens_r * 0.7, lens_r * 0.6, fill=(215, 252, 255, 255))

    step = s["fire_step"]
    if step in (1, 2, 3, 4):
        beam_end = rot_pt(cx, cy, ang, 30.2, 0.0, 5.0 + level * 0.15)
        w_outer = (2.4 + level * 0.55) if step in (2, 3) else (1.8 + level * 0.4)
        p.line([lens, beam_end], fill=(35, 155, 255, 220), width=w_outer)
        p.line([lens, beam_end], fill=(150, 232, 255, 252), width=w_outer * 0.58)
        p.line([lens, beam_end], fill=(245, 255, 255, 255), width=max(1.0, w_outer * 0.3))
        p.ellipse(lens[0], lens[1], 3.8 + level * 0.3, 3.2 + level * 0.25, fill=(95, 205, 255, 225))
        p.ellipse(lens[0], lens[1], 2.2 + level * 0.15, 1.9 + level * 0.12, fill=(245, 255, 255, 255))
        for p_i, (pd, ps) in enumerate([(16.5, -2.8), (20.8, 2.8), (24.8, -2.4), (27.6, 2.2)]):
            if p_i <= level:
                ep = rot_pt(cx, cy, ang, pd + (step % 2) * 1.1, ps, 5.2 + (p_i % 2 - 0.5) * 1.4)
                p.ellipse(ep[0], ep[1], 1.2 + level * 0.08, 1.0 + level * 0.06, fill=(180, 242, 255, 245))


def draw_tesla(p: Painter, st: dict, s: dict, level: int):
    ang = s["angle"]
    cx, cy = PIVOT_X + s["vib"][0], PIVOT_Y + s["vib"][1]
    charge = s["charge"]

    half_w = 5.4 + level * 0.4
    draw_prism(p, cx, cy, ang, -5.8 - level * 0.2, 6.8 + level * 0.25, -half_w, half_w, 1.0, 5.2 + level * 0.2,
               st["armor_light"], st["armor_mid"], st["armor_dark"])

    emitter_dist = 9.5 + level * 0.45
    emitter = rot_pt(cx, cy, ang, emitter_dist, 0.0, 4.8 + level * 0.15)
    draw_cylinder_along(p, cx, cy, ang, 4.8, emitter_dist, 0.0, 4.6 + level * 0.15, 1.4 + level * 0.12,
                        st["trim"], st["metal_light"], muzzle_col=(188, 112, 255, 255))

    if level >= 3:
        # خازن‌های الکتریکی جانبی در سطوح ۳ تا ۵
        for c_side in (-half_w + 0.5, half_w - 0.5):
            cap = rot_pt(cx, cy, ang, -1.0, c_side, 7.2)
            p.ellipse(cap[0], cap[1], 1.8 + (level - 3) * 0.3, 1.5 + (level - 3) * 0.25,
                      fill=st["trim"], outline=INK, width=0.65)

    # تعداد حلقه‌های سیم‌پیچ تسلا با سطح بیشتر می‌شود
    ring_count = min(4, 1 + (level + 1) // 2)
    for r_idx in range(ring_count):
        ring_z = 6.2 + r_idx * 2.5
        ring_r = 5.2 - r_idx * 0.65 + level * 0.15
        rc = rot_pt(cx, cy, ang, 0.0, 0.0, ring_z)
        r_col = st["trim"] if (level >= 4 and r_idx % 2 == 0) else st["metal_light"]
        p.ellipse(rc[0], rc[1], ring_r, ring_r * ISO_Y, fill=r_col, outline=INK, width=0.75)

    orb_z = 6.8 + ring_count * 2.5
    top_orb = rot_pt(cx, cy, ang, 0.0, 0.0, orb_z)
    orb_glow = int(125 + 125 * charge)
    orb_r = 3.6 + level * 0.35
    p.ellipse(top_orb[0], top_orb[1], orb_r * 1.25, orb_r * 1.12, fill=(158, 72, 255, orb_glow))
    p.ellipse(top_orb[0], top_orb[1], orb_r * 0.85, orb_r * 0.78, fill=st["metal_light"], outline=INK, width=0.8)
    p.ellipse(top_orb[0] - 0.5, top_orb[1] - 0.5, orb_r * 0.45, orb_r * 0.4, fill=(238, 200, 255, 255))

    spark_phase = int(round(s["phase"] * 8))
    for k in range(2 + level // 2):
        sp_a = ang + (k - 1) * 0.82 + spark_phase * 0.55
        s0 = (top_orb[0] + math.cos(sp_a) * 2.2, top_orb[1] + math.sin(sp_a) * 1.8)
        s1 = (top_orb[0] + math.cos(sp_a + 0.3) * (4.6 + level * 0.4 + 2.2 * charge),
              top_orb[1] + math.sin(sp_a + 0.3) * (3.8 + level * 0.3 + 1.8 * charge))
        p.line([s0, s1], fill=(208, 138, 255, int(150 + 100 * charge)), width=0.95 + level * 0.08)

    step = s["fire_step"]
    if step in (1, 2, 3, 4):
        p.ellipse(top_orb[0], top_orb[1], orb_r * 1.55, orb_r * 1.35, fill=(178, 88, 255, 225))
        p.ellipse(emitter[0], emitter[1], 3.2 + level * 0.25, 2.7 + level * 0.2, fill=(215, 145, 255, 242))
        zig_offsets = [-2.5, 2.7, -2.0, 1.8] if step % 2 == 0 else [2.4, -2.7, 2.1, -1.6]
        bolt_pts = [top_orb, emitter]
        for z_i, z_side in enumerate(zig_offsets):
            d_along = emitter_dist + 3.2 + (z_i + 1) * 3.6
            if d_along <= 30.0:
                bolt_pts.append(rot_pt(cx, cy, ang, d_along, z_side, 6.4 - z_i * 0.5))
        p.line(bolt_pts, fill=(152, 60, 255, 238), width=2.2 + level * 0.25)
        p.line(bolt_pts, fill=(242, 210, 255, 255), width=1.0 + level * 0.12)
        if level >= 2 and len(bolt_pts) >= 4:
            sub_pts = [
                top_orb,
                rot_pt(cx, cy, ang, 8.8, -4.2 if step % 2 == 0 else 4.2, 10.5),
                bolt_pts[3],
            ]
            p.line(sub_pts, fill=(198, 125, 255, 225), width=1.2 + level * 0.12)
        if level >= 4 and len(bolt_pts) >= 5:
            sub2 = [
                emitter,
                rot_pt(cx, cy, ang, 17.5, 4.4 if step % 2 == 0 else -4.4, 5.5),
                bolt_pts[-1],
            ]
            p.line(sub2, fill=(255, 220, 125, 230) if level == 5 else (130, 220, 255, 225), width=1.3)


OMEGA_STYLE = {
    "level": 6,
    "id": "lv6_mythic",
    "en": "Mythic (10x IAP)",
    "fa": "ایزدی (۱۰ برابر قدرت)",
    "metal_dark": (42, 18, 28, 255),
    "metal_mid": (196, 148, 42, 255),
    "metal_light": (255, 226, 108, 255),
    "armor_dark": (92, 18, 32, 255),
    "armor_mid": (175, 28, 48, 255),
    "armor_light": (242, 62, 82, 255),
    "trim": (255, 218, 75, 255),
    "accent": (90, 245, 255, 255),
}


def draw_omega(p: Painter, st: dict, s: dict, _level: int = 6):
    """ابرسلاح ایزدی سیمرغ (۱۰ برابر قوی‌ترین سلاح بازی):
    هستهٔ منشوری خورشیدی + دو توپ پلاسما-شهاب‌سنگ جانبی + باله‌های طلایی سیمرغ + تاج رعد کیهانی."""
    ang = s["angle"]
    cx, cy = PIVOT_X + s["vib"][0], PIVOT_Y + s["vib"][1]
    recoil = s["recoil"]
    charge = s["charge"]

    # هالهٔ طلایی-فیروزه‌ای دور برجک ایزدی
    p.ellipse(cx, cy, 13.8, 13.8 * ISO_Y, fill=(255, 210, 65, int(75 + 75 * charge)))
    p.ellipse(cx, cy, 11.4, 11.4 * ISO_Y, fill=st["metal_dark"], outline=INK, width=0.9)

    # بدنهٔ اصلی زرهی سرخ-طلایی با باله‌های سیمرغ در دو طرف
    draw_prism(p, cx, cy, ang, -8.5, 6.2, -7.8, 7.8, 1.2, 8.8,
               st["armor_light"], st["armor_mid"], st["armor_dark"])
    for wing_side in (-1, 1):
        s0 = wing_side * 7.8
        s1 = wing_side * 11.2
        draw_prism(p, cx, cy, ang, -6.5, 4.2, min(s0, s1), max(s0, s1), 2.5, 7.6,
                   st["metal_light"], st["trim"], st["metal_mid"])

    # دو لولهٔ توپ پلاسما-شهاب‌سنگ در طرفین
    barrel_len = 19.5 - recoil
    for b_side in (-4.4, 4.4):
        draw_cylinder_along(p, cx, cy, ang, 4.2, barrel_len, b_side, 5.2, 1.95,
                            st["metal_mid"], st["metal_light"], muzzle_col=(35, 18, 22, 255))
        ring_p = rot_pt(cx, cy, ang, barrel_len - 1.5, b_side, 5.2)
        p.ellipse(ring_p[0], ring_p[1], 2.5, 2.1, fill=st["trim"], outline=INK, width=0.65)

    # لولهٔ منشوری مرکزی خورشیدی
    center_len = 16.5 - recoil * 0.5
    draw_cylinder_along(p, cx, cy, ang, 4.8, center_len, 0.0, 6.2, 2.7,
                        st["trim"], st["metal_light"], muzzle_col=(40, 220, 255, 255))
    lens = rot_pt(cx, cy, ang, center_len + 0.5, 0.0, 6.2)
    p.ellipse(lens[0], lens[1], 3.2 + 0.8 * charge, 2.7 + 0.6 * charge, fill=(85, 240, 255, 235))
    p.ellipse(lens[0], lens[1], 1.9, 1.6, fill=(255, 255, 240, 255))

    # تاج حلقه‌ای طلایی و گوی کیهانی بالای برجک
    crown_c = rot_pt(cx, cy, ang, -1.2, 0.0, 11.2)
    p.ellipse(crown_c[0], crown_c[1], 5.6, 5.6 * ISO_Y, fill=st["trim"], outline=INK, width=0.8)
    orb = rot_pt(cx, cy, ang, -1.2, 0.0, 13.8)
    p.ellipse(orb[0], orb[1], 4.4, 3.9, fill=(255, 215, 75, int(155 + 95 * charge)))
    p.ellipse(orb[0], orb[1], 3.0, 2.7, fill=(120, 245, 255, 255), outline=INK, width=0.75)
    p.ellipse(orb[0] - 0.6, orb[1] - 0.5, 1.5, 1.3, fill=(255, 255, 255, 255))

    step = s["fire_step"]
    if step in (1, 2, 3, 4):
        # ۱) ابرپرتو کیهانی طلایی-فیروزه‌ای از منشور مرکزی
        beam_end = rot_pt(cx, cy, ang, 30.2, 0.0, 6.2)
        p.line([lens, beam_end], fill=(255, 195, 45, 230), width=5.4)
        p.line([lens, beam_end], fill=(95, 245, 255, 250), width=3.2)
        p.line([lens, beam_end], fill=(255, 255, 245, 255), width=1.5)
        # ۲) گلوله‌های شهاب‌سنگی آتشین از دو لولهٔ جانبی
        sh_dist = min(29.2, 21.0 + step * 2.2)
        for b_side in (-4.4, 4.4):
            sh_pos = rot_pt(cx, cy, ang, sh_dist, b_side, 5.2)
            tr_pos = rot_pt(cx, cy, ang, sh_dist - 3.2, b_side, 5.2)
            p.line([tr_pos, sh_pos], fill=(255, 125, 28, 235), width=3.2)
            p.ellipse(sh_pos[0], sh_pos[1], 2.5, 2.2, fill=(32, 24, 28, 255), outline=(255, 220, 90, 255), width=0.8)
        # ۳) صاعقه‌های بنفش-طلایی از تاج بالایی
        bolt_pts = [
            orb,
            rot_pt(cx, cy, ang, 10.0, -3.5 if step % 2 == 0 else 3.5, 10.5),
            rot_pt(cx, cy, ang, 20.0, 3.8 if step % 2 == 0 else -3.8, 8.0),
            beam_end,
        ]
        p.line(bolt_pts, fill=(195, 95, 255, 235), width=2.4)
        p.line(bolt_pts, fill=(255, 240, 175, 255), width=1.1)


DRAW_FUNCS = {
    "machine_gun": draw_machine_gun,
    "cannon": draw_cannon,
    "missile": draw_missile,
    "laser": draw_laser,
    "tesla": draw_tesla,
    "omega": draw_omega,
}


def render_upper_only(turret_id: str, level: int, anim: str, frame_idx: int, total_frames: int, force_zero_angle: bool = False) -> Image.Image:
    p = Painter()
    tier_st = OMEGA_STYLE if turret_id == "omega" else TIERS[level - 1]
    state = frame_state("laser" if turret_id == "omega" else turret_id, anim, frame_idx, total_frames)
    if force_zero_angle:
        state["angle"] = 0.0
    DRAW_FUNCS[turret_id](p, tier_st, state, level)
    return p.finish()


def render_frame(turret_id: str, level: int, anim: str, frame_idx: int, total_frames: int) -> Image.Image:
    base = get_stone_base().copy()
    upper = render_upper_only(turret_id, level, anim, frame_idx, total_frames, False)
    base.alpha_composite(upper)
    return base


# ------------------------------------------------------------------ ۴. خروجی شیت‌ها و JSON


def make_tp_json(sheet_name: str, frame_count: int, fps: int, level: int, tier_id: str) -> dict:
    frames = {}
    anim_list = []
    for i in range(frame_count):
        fname = f"{sheet_name}_{i:02d}"
        anim_list.append(fname)
        frames[fname] = {
            "frame": {"x": i * FRAME, "y": 0, "w": FRAME, "h": FRAME},
            "rotated": False,
            "trimmed": False,
            "spriteSourceSize": {"x": 0, "y": 0, "w": FRAME, "h": FRAME},
            "sourceSize": {"w": FRAME, "h": FRAME},
            "pivot": {"x": 0.5, "y": round(PIVOT_Y / FRAME, 4)},
        }
    return {
        "frames": frames,
        "animations": {sheet_name: anim_list},
        "meta": {
            "app": "https://www.codeandweb.com/texturepacker",
            "version": "1.0",
            "image": f"{sheet_name}.png",
            "format": "RGBA8888",
            "size": {"w": FRAME * frame_count, "h": FRAME},
            "scale": "1",
            "level": level,
            "tier": tier_id,
            "fps": fps,
            "loop": True,
            "construct3": {
                "horizontalCells": frame_count,
                "verticalCells": 1,
                "cellWidth": FRAME,
                "cellHeight": FRAME,
                "speed": fps,
                "loop": True,
            },
        },
    }


def build_all_tiers(out_dir: str) -> dict:
    os.makedirs(out_dir, exist_ok=True)
    os.makedirs(os.path.join(out_dir, "preview"), exist_ok=True)
    tiers_root = os.path.join(out_dir, "tiers")
    os.makedirs(tiers_root, exist_ok=True)

    pack = {
        "frameSize": {"w": FRAME, "h": FRAME},
        "pivot": {"x": PIVOT_X, "y": PIVOT_Y},
        "tiers": [
            {"level": tr["level"], "id": tr["id"], "en": tr["en"], "fa": tr["fa"]}
            for tr in TIERS
        ],
        "turrets": {},
        "sheets": [],
    }
    matrix_frames: dict[str, dict[int, dict[str, list[Image.Image]]]] = {}

    for t in TURRETS:
        tid = t["id"]
        matrix_frames[tid] = {}
        pack["turrets"][tid] = {
            "title": t["title"],
            "fa": t["fa"],
            "projectile": t["projectile"],
            "levels": {},
        }
        for tr in TIERS:
            lvl = tr["level"]
            tier_dir = os.path.join(tiers_root, f"lv{lvl}")
            os.makedirs(tier_dir, exist_ok=True)
            matrix_frames[tid][lvl] = {}
            pack["turrets"][tid]["levels"][str(lvl)] = {
                "tier_id": tr["id"],
                "fa": tr["fa"],
                "en": tr["en"],
                "animations": {},
            }
            for anim, fcount, fps, desc in t["anims"]:
                frames = [render_frame(tid, lvl, anim, i, fcount) for i in range(fcount)]
                matrix_frames[tid][lvl][anim] = frames
                sheet_name = f"{tid}_lv{lvl}_{anim}"
                sheet = Image.new("RGBA", (FRAME * fcount, FRAME), (0, 0, 0, 0))
                for i, fr in enumerate(frames):
                    sheet.paste(fr, (i * FRAME, 0))

                sheet_path = os.path.join(tier_dir, f"{sheet_name}.png")
                json_path = os.path.join(tier_dir, f"{sheet_name}.json")
                sheet.save(sheet_path)
                with open(json_path, "w", encoding="utf-8") as fh:
                    json.dump(make_tp_json(sheet_name, fcount, fps, lvl, tr["id"]), fh, ensure_ascii=False, indent=2)

                # کپی سطح ۳ به عنوان شیت پایهٔ بدون پسوند در ریشهٔ spritesheets/turrets/ برای سازگاری
                if lvl == 3:
                    base_name = f"{tid}_{anim}"
                    sheet.save(os.path.join(out_dir, f"{base_name}.png"))
                    with open(os.path.join(out_dir, f"{base_name}.json"), "w", encoding="utf-8") as fh:
                        json.dump(make_tp_json(base_name, fcount, fps, lvl, tr["id"]), fh, ensure_ascii=False, indent=2)

                rel_png = f"tiers/lv{lvl}/{sheet_name}.png"
                rel_json = f"tiers/lv{lvl}/{sheet_name}.json"
                pack["turrets"][tid]["levels"][str(lvl)]["animations"][anim] = {
                    "frames": fcount,
                    "fps": fps,
                    "loop": True,
                    "description": desc,
                    "sheet": rel_png,
                    "atlas": rel_json,
                }
                pack["sheets"].append({
                    "name": sheet_name,
                    "file": rel_png,
                    "atlas": rel_json,
                    "turret": tid,
                    "level": lvl,
                    "tier": tr["id"],
                    "animation": anim,
                    "frames": fcount,
                    "fps": fps,
                    "loop": True,
                    "size": {"w": FRAME * fcount, "h": FRAME},
                })

    # ۶) ابرسلاح ایزدی سیمرغ (omega — ۱۰ برابر قوی‌ترین سلاح بازی، خرید درون‌برنامه‌ای)
    omega_dir = os.path.join(tiers_root, "omega")
    os.makedirs(omega_dir, exist_ok=True)
    omega_anims = [
        ("idle", 8, 8, "حالت آماده با درخشش منشور خورشیدی و گوی کیهانی"),
        ("aim", 8, 12, "هدف‌گیری هم‌زمان پرتو، شهاب‌سنگ و تاج رعد"),
        ("fire", 6, 14, "شلیک هم‌زمان ابرپرتو کیهانی، شهاب‌سنگ‌های آتشین و صاعقهٔ زنجیره‌ای"),
        ("cooldown", 4, 8, "خنک‌شدن منشور خورشیدی و تاج سیمرغ"),
    ]
    matrix_frames["omega"] = {6: {}}
    pack["turrets"]["omega"] = {
        "title": "Simorgh Omega (10x IAP)",
        "fa": "ابرسلاح ایزدی سیمرغ",
        "projectile": "cosmic beam + meteor shells + lightning crown",
        "levels": {
            "6": {
                "tier_id": OMEGA_STYLE["id"],
                "fa": OMEGA_STYLE["fa"],
                "en": OMEGA_STYLE["en"],
                "animations": {},
            }
        },
    }
    for anim, fcount, fps, desc in omega_anims:
        frames = [render_frame("omega", 6, anim, i, fcount) for i in range(fcount)]
        matrix_frames["omega"][6][anim] = frames
        sheet_name = f"omega_{anim}"
        sheet = Image.new("RGBA", (FRAME * fcount, FRAME), (0, 0, 0, 0))
        for i, fr in enumerate(frames):
            sheet.paste(fr, (i * FRAME, 0))
        sheet.save(os.path.join(omega_dir, f"{sheet_name}.png"))
        with open(os.path.join(omega_dir, f"{sheet_name}.json"), "w", encoding="utf-8") as fh:
            json.dump(make_tp_json(sheet_name, fcount, fps, 6, OMEGA_STYLE["id"]), fh, ensure_ascii=False, indent=2)
        rel_png = f"tiers/omega/{sheet_name}.png"
        rel_json = f"tiers/omega/{sheet_name}.json"
        pack["turrets"]["omega"]["levels"]["6"]["animations"][anim] = {
            "frames": fcount,
            "fps": fps,
            "loop": True,
            "description": desc,
            "sheet": rel_png,
            "atlas": rel_json,
        }
        pack["sheets"].append({
            "name": sheet_name,
            "file": rel_png,
            "atlas": rel_json,
            "turret": "omega",
            "level": 6,
            "tier": OMEGA_STYLE["id"],
            "animation": anim,
            "frames": fcount,
            "fps": fps,
            "loop": True,
            "size": {"w": FRAME * fcount, "h": FRAME},
        })

    with open(os.path.join(out_dir, "turret_pack.json"), "w", encoding="utf-8") as fh:
        json.dump(pack, fh, ensure_ascii=False, indent=2)

    with open(os.path.join(out_dir, ".gdignore"), "w", encoding="utf-8") as fh:
        fh.write("")

    export_game_assets()

    matrix_img = make_tier_matrix_image(matrix_frames)
    matrix_path = os.path.join(out_dir, "preview", "tiers_1_to_5_matrix.png")
    matrix_img.save(matrix_path)
    # همچنین در contact_sheet.png ذخیره می‌شود
    matrix_img.save(os.path.join(out_dir, "preview", "contact_sheet.png"))

    return {"pack": pack, "frames": matrix_frames, "matrix_path": matrix_path}


def export_game_assets() -> None:
    """دارایی‌های داخل بازی (towers/base_<id>, towers/top_<id>, towers/head_<id>, icons/w_<id>)
    را برای تمام ۲۶ سلاح جدید می‌سازد و فایل‌های ۱۸ سلاح قدیمی را پاک می‌کند."""
    towers_dir = os.path.join(ROOT, "assets", "sprites", "towers")
    icons_dir = os.path.join(ROOT, "assets", "icons")
    os.makedirs(towers_dir, exist_ok=True)
    os.makedirs(icons_dir, exist_ok=True)

    old_ids = [
        "bow", "spear", "catapult", "longbow", "zubin", "firepot", "frost",
        "falcon", "venom", "volley", "thunder", "firecat", "sniper", "breaker",
        "triplet", "scythe", "pyre", "eye",
    ]
    for oid in old_ids:
        for p in [
            os.path.join(towers_dir, f"base_{oid}.png"),
            os.path.join(towers_dir, f"top_{oid}.png"),
            os.path.join(towers_dir, f"head_{oid}.png"),
            os.path.join(icons_dir, f"w_{oid}.png"),
        ]:
            if os.path.exists(p):
                os.remove(p)

    # پایهٔ سنگی ۶۴×۶۴ را روی بوم ۹۶×۸۴ متمرکز می‌کنیم تا با PIVOT_OFFSET بازی منطبق باشد
    stone_64 = get_stone_base()
    base_canvas = Image.new("RGBA", (96, 84), (0, 0, 0, 0))
    base_canvas.alpha_composite(stone_64, (16, 10))
    # همچنین base.png پیش‌فرض را به‌روز می‌کنیم
    stone_64.resize((60, 60), Image.NEAREST).save(os.path.join(towers_dir, "base.png"))

    tier_border_cols = {
        1: (128, 138, 150, 255),
        2: (92, 184, 102, 255),
        3: (76, 174, 242, 255),
        4: (182, 114, 252, 255),
        5: (250, 202, 72, 255),
        6: (255, 82, 102, 255),
    }

    weapon_entries: list[tuple[str, str, int, str]] = []
    for t in TURRETS:
        tid = t["id"]
        rel_anim = t["anims"][3][0]
        for tr in TIERS:
            lvl = tr["level"]
            weapon_entries.append((f"{tid}_lv{lvl}", tid, lvl, rel_anim))
    weapon_entries.append(("omega", "omega", 6, "cooldown"))

    for wid, tid, lvl, rel_anim in weapon_entries:
        # ۱) base_<wid>.png (پایهٔ ثابت سنگی)
        base_canvas.save(os.path.join(towers_dir, f"base_{wid}.png"))

        # ۲) top_<wid>.png (۴ فریم افقی ۳۸۴×۸۴ فقط از بخش بالایی چرخان با زاویهٔ ۰)
        u0 = render_upper_only(tid, lvl, "idle", 0, 8, True)
        u1 = render_upper_only(tid, lvl, "idle", 2, 8, True)
        u2 = render_upper_only(tid, lvl, "fire", 2, 6, True)
        u3 = render_upper_only(tid, lvl, rel_anim, 1, 4, True)
        top_sheet = Image.new("RGBA", (96 * 4, 84), (0, 0, 0, 0))
        for idx, u_im in enumerate([u0, u1, u2, u3]):
            top_sheet.alpha_composite(u_im, (idx * 96 + 16, 10))
        top_sheet.save(os.path.join(towers_dir, f"top_{wid}.png"))

        # ۳) head_<wid>.png (پیش‌نمای کامل پایه + بخش بالایی در ابعاد ۷۷×۶۲)
        full_fire = render_frame(tid, lvl, "fire", 2, 6)
        head_im = Image.new("RGBA", (77, 62), (0, 0, 0, 0))
        scaled_58 = full_fire.resize((58, 58), Image.NEAREST)
        head_im.alpha_composite(scaled_58, (9, 2))
        head_im.save(os.path.join(towers_dir, f"head_{wid}.png"))

        # ۴) icons/w_<wid>.png (مدال طلایی بال‌دار با هالهٔ آبی که عکس اسلحه جای ستاره داخل آن قرار گرفته است)
        card_w_path = os.path.join(ROOT, "assets", "sprites", "ui", "card_weapon_base.png")
        if os.path.exists(card_w_path):
            card_w_im = Image.open(card_w_path).convert("RGBA")
            medal_crop = card_w_im.crop((168, 20, 344, 196))  # 176x176، مرکز دیسک در (88, 85)
            turret_84 = full_fire.resize((84, 84), Image.NEAREST)
            medal_crop.alpha_composite(turret_84, (88 - 42, 85 - 42))
            icon_im = medal_crop.resize((72, 72), Image.LANCZOS)
        else:
            icon_im = Image.new("RGBA", (72, 72), (0, 0, 0, 0))
            idraw = ImageDraw.Draw(icon_im)
            bcol = tier_border_cols.get(lvl, (180, 180, 180, 255))
            bg_col = (36, 18, 28, 242) if lvl == 6 else (22, 28, 36, 240)
            idraw.rounded_rectangle([2, 2, 69, 69], radius=12, fill=bg_col, outline=bcol, width=2)
            turret_64 = full_fire.resize((64, 64), Image.NEAREST)
            icon_im.alpha_composite(turret_64, (4, 4))
        icon_im.save(os.path.join(icons_dir, f"w_{wid}.png"))


def make_tier_matrix_image(matrix_frames: dict) -> Image.Image:
    """جدول تصویری ۵×۵ از تمام ۵ اسلحه در ۵ سطح (ضعیف تا افسانه‌ای) — هر خانه شامل حالت آماده و حالت شلیک."""
    scale = 2
    cell = FRAME * scale  # 128px
    pair_gap = 6
    col_w = cell * 2 + pair_gap
    pad = 14
    header_h = 52
    label_w = 175
    cols = len(TIERS)
    rows = len(TURRETS) + 1
    w = label_w + cols * (col_w + pad) + pad
    h = header_h + rows * (cell + pad) + pad
    img = Image.new("RGBA", (w, h), (16, 20, 26, 255))
    d = ImageDraw.Draw(img)

    font_path = os.path.join(ROOT, "assets", "fonts", "Vazirmatn-FD-Bold.ttf")
    try:
        font_head = ImageFont.truetype(font_path, 15)
        font_sub = ImageFont.truetype(font_path, 12)
    except Exception:
        font_head = ImageFont.load_default()
        font_sub = ImageFont.load_default()

    tier_border_cols = [
        (118, 128, 138, 255),  # Lv.1 Weak
        (96, 178, 104, 255),   # Lv.2 Common
        (82, 176, 238, 255),   # Lv.3 Advanced
        (178, 118, 250, 255),  # Lv.4 Epic
        (248, 202, 78, 255),   # Lv.5 Legendary
    ]

    for c, tr in enumerate(TIERS):
        x0 = label_w + pad + c * (col_w + pad)
        bcol = tier_border_cols[c]
        d.text((x0 + 10, 10), tr["en"], fill=bcol, font=font_head)
        d.text((x0 + 10, 30), "Idle          Fire", fill=(165, 176, 190, 255), font=font_sub)

    for r, t in enumerate(TURRETS):
        tid = t["id"]
        y0 = header_h + pad + r * (cell + pad)
        d.text((12, y0 + cell // 2 - 20), f"{r + 1}. {t['title']}", fill=(235, 240, 246, 255), font=font_head)
        d.text((12, y0 + cell // 2 + 4), t["projectile"], fill=(148, 162, 178, 255), font=font_sub)
        for c, tr in enumerate(TIERS):
            lvl = tr["level"]
            x0 = label_w + pad + c * (col_w + pad)
            bcol = tier_border_cols[c]
            d.rounded_rectangle([x0 - 3, y0 - 3, x0 + col_w + 3, y0 + cell + 3],
                                radius=8, fill=(26, 32, 40, 255), outline=bcol, width=2)
            fr_idle = matrix_frames[tid][lvl]["idle"][1].resize((cell, cell), Image.NEAREST)
            fr_fire = matrix_frames[tid][lvl]["fire"][2].resize((cell, cell), Image.NEAREST)
            img.alpha_composite(fr_idle, (x0, y0))
            img.alpha_composite(fr_fire, (x0 + cell + pair_gap, y0))

    # ردیف ۶: ابرسلاح ایزدی سیمرغ (۱۰ برابر قوی‌ترین سلاح بازی — خرید درون‌برنامه‌ای ۱۰۰٬۰۰۰ تومان)
    if "omega" in matrix_frames and 6 in matrix_frames["omega"]:
        y_om = header_h + pad + len(TURRETS) * (cell + pad)
        d.text((12, y_om + cell // 2 - 22), "6. Simorgh Omega", fill=(255, 218, 75, 255), font=font_head)
        d.text((12, y_om + cell // 2 + 2), "10x Power · IAP 100,000T", fill=(255, 110, 128, 255), font=font_sub)
        x_om = label_w + pad
        total_w = cols * col_w + (cols - 1) * pad
        d.rounded_rectangle([x_om - 3, y_om - 3, x_om + total_w + 3, y_om + cell + 3],
                            radius=10, fill=(38, 20, 30, 255), outline=(255, 210, 75, 255), width=2)
        om_idle = matrix_frames["omega"][6]["idle"][1].resize((cell, cell), Image.NEAREST)
        om_aim = matrix_frames["omega"][6]["aim"][2].resize((cell, cell), Image.NEAREST)
        om_fire1 = matrix_frames["omega"][6]["fire"][1].resize((cell, cell), Image.NEAREST)
        om_fire2 = matrix_frames["omega"][6]["fire"][2].resize((cell, cell), Image.NEAREST)
        om_cool = matrix_frames["omega"][6]["cooldown"][1].resize((cell, cell), Image.NEAREST)
        for idx, fr_im in enumerate([om_idle, om_aim, om_fire1, om_fire2, om_cool]):
            cx_pos = label_w + pad + idx * (col_w + pad) + (col_w - cell) // 2
            img.alpha_composite(fr_im, (cx_pos, y_om))

    return img


# ------------------------------------------------------------------ ۵. تست خودکار (--check)


def check_all_tiers(out_dir: str) -> list[str]:
    problems: list[str] = []
    pack_path = os.path.join(out_dir, "turret_pack.json")
    if not os.path.exists(pack_path):
        return [f"فایل {pack_path} پیدا نشد"]

    base_sample_pts = [(12, 38), (18, 48), (32, 51)]
    ref_base_pixels = None
    checked_sheets = 0

    for t in TURRETS:
        tid = t["id"]
        for tr in TIERS:
            lvl = tr["level"]
            tier_dir = os.path.join(out_dir, "tiers", f"lv{lvl}")
            for anim, fcount, _fps, _desc in t["anims"]:
                sname = f"{tid}_lv{lvl}_{anim}"
                png_path = os.path.join(tier_dir, f"{sname}.png")
                json_path = os.path.join(tier_dir, f"{sname}.json")
                if not os.path.exists(png_path):
                    problems.append(f"شیت {png_path} وجود ندارد")
                    continue
                if not os.path.exists(json_path):
                    problems.append(f"اطلس {json_path} وجود ندارد")
                    continue

                img = Image.open(png_path)
                if img.mode != "RGBA":
                    problems.append(f"{sname}.png باید RGBA باشد")
                if img.size != (FRAME * fcount, FRAME):
                    problems.append(f"اندازهٔ {sname}.png اشتباه است: {img.size}")

                frames = [img.crop((i * FRAME, 0, (i + 1) * FRAME, FRAME)) for i in range(fcount)]
                if frames[0].tobytes() != frames[-1].tobytes():
                    problems.append(f"فریم اول و آخر در {sname}.png یکسان نیستند (لوپ نرم)")

                for i, fr in enumerate(frames):
                    for cx_p, cy_p in [(0, 0), (FRAME - 1, 0), (0, FRAME - 1), (FRAME - 1, FRAME - 1)]:
                        if fr.getpixel((cx_p, cy_p))[3] != 0:
                            problems.append(f"گوشهٔ فریم {i} در {sname}.png شفاف نیست")
                            break
                    sample = [fr.getpixel(pt) for pt in base_sample_pts]
                    if ref_base_pixels is None:
                        ref_base_pixels = sample
                    elif sample != ref_base_pixels:
                        problems.append(f"پایهٔ سنگی در {sname} فریم {i} تغییر کرده است")
                checked_sheets += 1

    omega_dir = os.path.join(out_dir, "tiers", "omega")
    for anim, fcount in [("idle", 8), ("aim", 8), ("fire", 6), ("cooldown", 4)]:
        sname = f"omega_{anim}"
        png_path = os.path.join(omega_dir, f"{sname}.png")
        json_path = os.path.join(omega_dir, f"{sname}.json")
        if not os.path.exists(png_path):
            problems.append(f"شیت {png_path} وجود ندارد")
            continue
        if not os.path.exists(json_path):
            problems.append(f"اطلس {json_path} وجود ندارد")
            continue
        img = Image.open(png_path)
        if img.size != (FRAME * fcount, FRAME):
            problems.append(f"اندازهٔ {sname}.png اشتباه است: {img.size}")
        frames = [img.crop((i * FRAME, 0, (i + 1) * FRAME, FRAME)) for i in range(fcount)]
        if frames[0].tobytes() != frames[-1].tobytes():
            problems.append(f"فریم اول و آخر در {sname}.png یکسان نیستند")
        checked_sheets += 1

    if checked_sheets != 104:
        problems.append(f"تعداد شیت‌های بررسی‌شده باید ۱۰۴ باشد، هست: {checked_sheets}")
    return problems


def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="مولد شیت‌های اسپرایت ۵ برجک در ۵ سطح (ضعیف تا افسانه‌ای)")
    ap.add_argument("--check", action="store_true", help="تست کامل ۱۰۰ شیت و JSONها")
    args = ap.parse_args(argv)

    if args.check:
        probs = check_all_tiers(OUT_ROOT)
        for p in probs:
            print("✗", p)
        print("بررسی ۱۰۰ شیت (۵ سلاح × ۵ سطح × ۴ انیمیشن) تمام شد؛ بدون مشکل."
              if not probs else f"{len(probs)} مشکل پیدا شد.")
        return 1 if probs else 0

    res = build_all_tiers(OUT_ROOT)
    probs = check_all_tiers(OUT_ROOT)
    for p in probs:
        print("✗", p)
    print(f"ساخته شد: ۱۰۰ شیت افقی (۵ سلاح × ۵ سطح × ۴ انیمیشن) و جدول مقایسه در {res['matrix_path']}")
    print("نتیجهٔ بررسی خودکار:", "بدون مشکل." if not probs else f"{len(probs)} مشکل.")
    return 1 if probs else 0


if __name__ == "__main__":
    sys.exit(main())
