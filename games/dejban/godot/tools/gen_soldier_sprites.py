#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""مولد شیت اسپرایت «سرباز نظامی» برای بازی‌های دفاع از قلعه.

پنج انیمیشن (ایستاده، راه رفتن، دویدن، شلیک، مرگ) در چهار جهت (روبه‌رو،
پشت، چپ، راست) و هر فریم ۶۴×۶۴ پیکسل با پس‌زمینهٔ شفاف ساخته می‌شود.

    python3 tools/gen_soldier_sprites.py             # ساخت کامل بسته
    python3 tools/gen_soldier_sprites.py --debug walk front 3 --zoom 8
    python3 tools/gen_soldier_sprites.py --check     # بررسی بستهٔ ساخته‌شده

اسپرایت‌ها پیکسلی و بدون نرم‌سازی لبه‌اند: هر پیکسل با مختصات صحیح رسم
می‌شود تا نتیجه در موتور بازی «پیکسل‌پرفکت» بماند. بدن سرباز یک‌بار در
مختصات محلی (جلو، پهلو، ارتفاع) مدل می‌شود و «ریگ» چهارجهته آن را با یک
تصویر اریب بالا-به-پایین به صفحه می‌برد؛ بنابراین طراحی شخصیت در همهٔ
فریم‌ها و جهت‌ها یکسان است و رویه‌ها (کلاه، جلیقه، کوله، پوتین) در هر
جهت درست دیده می‌شوند.

خروجی‌ها در spritesheets/military_soldier/ ساخته می‌شوند:
    soldier_<anim>_<dir>.png        شیت افقی (۶۴ پیکسل در هر فریم)
    soldier_<anim>_<dir>.json       قالب TexturePacker JSON Hash
    soldier_all_atlas.png / .json   اطلس یکپارچهٔ همهٔ فریم‌ها
    soldier_pack.json               فهرست کل بسته (فریم، نرخ، لوپ)
    index.html                      پیش‌نمایش متحرک در مرورگر
    preview/                        برگهٔ نمونه و گیف
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
    print("Pillow لازم است:  pip install pillow", file=sys.stderr)
    raise

TAU = math.tau
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "spritesheets", "military_soldier")

# ------------------------------------------------------------------ اندازه‌ها

FRAME = 64            # اندازهٔ هر فریم
CX = 32.0             # مرکز افقی قاب
GROUND_Y = 50.0       # خط تماس کف پا با زمین
DEPTH = 0.62          # فشردگی محور عمق (زاویهٔ دید بالا-به-پایین)
GUN_DEPTH = 1.30      # اغراق عمدی عمق تفنگ تا در نماهای روبه‌رو/پشت خوانا بماند

# ارتفاع‌های محلی بدن (نسبت به کف پا، پیکسل)
Z_BOOT = 3.3
Z_KNEE = 8.6
Z_HIP = 15.6
Z_BELT = 16.9
Z_CHEST = 21.0
Z_SHOULDER = 24.2
Z_NECK = 25.4
Z_CHIN = 26.0
Z_FACE = 28.4      # مرکز سر
Z_BRIM = 31.6      # لبهٔ کلاه
Z_DOME = 34.2      # گنبد کلاه
Z_HELMET = Z_DOME
R_HELMET = 5.6
HALF_SHOULDER = 7.2
HALF_CHEST = 6.4
HALF_HIP = 6.0
FOOT_SIDE = 4.1

# ------------------------------------------------------------------ پالت رنگ

INK = [(9, 11, 8), (17, 19, 14), (26, 28, 20), (36, 38, 28)]
OLIVE = [(48, 58, 30), (68, 82, 42), (92, 108, 56), (120, 136, 76)]        # شلوارک نظامی
OLIVE_L = [(60, 72, 36), (86, 100, 50), (114, 130, 66), (144, 160, 90)]    # کلاه و کوله
VEST = [(22, 24, 20), (34, 37, 30), (48, 52, 42), (64, 68, 56)]            # جلیقه و تسمه
KHAKI = [(70, 54, 30), (100, 78, 44), (132, 106, 62), (164, 134, 82)]      # چرم پوتین و خشاب‌دان
HELMET = OLIVE_L
SKIN = [(104, 72, 48), (146, 104, 71), (184, 139, 99), (214, 172, 132)]
GUN = [(22, 24, 28), (40, 44, 51), (64, 70, 80), (96, 104, 116)]         # تفنگ
WOOD = [(52, 32, 18), (80, 50, 28), (108, 72, 42), (136, 96, 60)]
BOOT = KHAKI
BRASS = [(118, 90, 33), (166, 130, 47), (206, 170, 73), (236, 206, 116)]
FLASH = [(178, 116, 38), (240, 188, 88), (255, 231, 158), (255, 252, 228)]
SMOKE = [(52, 52, 50), (86, 86, 82), (122, 122, 116), (162, 162, 156)]
DUST = [(94, 86, 64), (124, 114, 86), (154, 144, 112), (184, 174, 140)]


def rgba(tone, alpha=255):
    return (int(tone[0]), int(tone[1]), int(tone[2]), int(alpha))


def ramp(mat, t, x=0, y=0, dither=False):
    """رنگ را از نوار تُن ماتریال می‌گیرد (t از -۱ تا ۱)."""
    n = len(mat) - 1
    v = (t + 1.0) * 0.5 * n
    lo = int(math.floor(v))
    frac = v - lo
    if lo < 0:
        return rgba(mat[0])
    if lo >= n:
        return rgba(mat[n])
    if dither and 0.34 <= frac <= 0.66 and ((x + y) & 1) == 0:
        return rgba(mat[lo + 1])
    return rgba(mat[lo])


def lit(mat, gain=1.0, bias=0.0, rim=0.34, light=(-0.62, -0.78), dither=False):
    """سایه‌روشن گنبدی با نور ثابت از بالا-چپ صفحه (در همهٔ جهت‌ها یکی)."""
    lx, ly = light

    def fn(x, y, nx, ny, d):
        t = (nx * lx + ny * ly) * gain + bias - rim * max(0.0, d - 0.55)
        return ramp(mat, t, x, y, dither)

    return fn


def flat(mat, t=0.0):
    def fn(x, y, nx, ny, d):
        return ramp(mat, t)

    return fn


def camo(base_fn, seed=7, amount=18, scale=3):
    """لکه‌های درشت استتار روی لباس (نه نویز ریز)."""
    def fn(x, y, nx, ny, d):
        col = base_fn(x, y, nx, ny, d)
        cx = x // scale
        cy = y // scale
        h = (cx * 73856093) ^ (cy * 19349663) ^ (seed * 83492791)
        h = (h ^ (h >> 13)) & 0xFFFFFF
        if (h % 100) < amount:
            k = 0.74 if (h % 3) else 0.62
            return (int(col[0] * k), int(col[1] * k), int(col[2] * k), col[3])
        return col

    return fn


def solid(color, alpha=255):
    c = rgba(color, alpha)

    def fn(x, y, nx, ny, d):
        return c

    return fn


def ao_edge(fn, strength=0.5, width=0.30):
    """تیره‌کردن لبهٔ شکل (سایهٔ تماس) برای جدا شدن اندام‌ها از هم."""
    def g(x, y, nx, ny, d):
        col = fn(x, y, nx, ny, d)
        if d > 1.0 - width:
            k = 1.0 - strength * (d - (1.0 - width)) / max(0.001, width)
            return (int(col[0] * k), int(col[1] * k), int(col[2] * k), col[3])
        return col

    return g


# ------------------------------------------------------------------ بوم پیکسلی


class Canvas:
    """بوم RGBA خام؛ همهٔ اشکال بدون نرم‌سازی لبه رسم می‌شوند."""

    def __init__(self, w=FRAME, h=FRAME):
        self.w, self.h = w, h
        self.buf = bytearray(w * h * 4)

    # ---- دسترسی
    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def alpha(self, x, y):
        if not self.inside(x, y):
            return 0
        return self.buf[(y * self.w + x) * 4 + 3]

    def get(self, x, y):
        i = (y * self.w + x) * 4
        return tuple(self.buf[i:i + 4])

    def put(self, x, y, color):
        if not self.inside(x, y):
            return
        i = (y * self.w + x) * 4
        self.buf[i] = color[0] & 255
        self.buf[i + 1] = color[1] & 255
        self.buf[i + 2] = color[2] & 255
        self.buf[i + 3] = color[3] & 255

    def blend(self, x, y, color):
        a = color[3]
        if a <= 0 or not self.inside(x, y):
            return
        if a >= 255:
            self.put(x, y, color)
            return
        i = (y * self.w + x) * 4
        b = self.buf
        ia = b[i + 3]
        na = a + ia * (255 - a) // 255
        if na == 0:
            return
        keep = ia * (255 - a) // 255
        for k in range(3):
            b[i + k] = (color[k] * a + b[i + k] * keep) // na
        b[i + 3] = na

    # ---- اشکال پایه
    def ellipse(self, cx, cy, rx, ry, fn, ang=0.0):
        rx = max(0.4, rx)
        ry = max(0.4, ry)
        rmax = max(rx, ry) + 1.0
        ca, sa = math.cos(ang), math.sin(ang)
        for y in range(int(math.floor(cy - rmax)), int(math.ceil(cy + rmax)) + 1):
            for x in range(int(math.floor(cx - rmax)), int(math.ceil(cx + rmax)) + 1):
                dx = x + 0.5 - cx
                dy = y + 0.5 - cy
                if ang:
                    ux = dx * ca + dy * sa
                    uy = -dx * sa + dy * ca
                else:
                    ux, uy = dx, dy
                u = ux / rx
                v = uy / ry
                d = math.hypot(u, v)
                if d <= 1.0:
                    self.put(x, y, fn(x, y, u, v, d))

    def capsule(self, p0, p1, r0, r1=None, fn=None, squash=(1.0, 1.0)):
        """میلهٔ گرد با شعاع متغیر و کشیدگی محور (بازو، ساق، لوله، تنه)."""
        r1 = r0 if r1 is None else r1
        x0, y0 = p0
        x1, y1 = p1
        dx, dy = x1 - x0, y1 - y0
        L2 = dx * dx + dy * dy
        rmax = max(r0, r1) + 1.0
        sx, sy = squash
        for y in range(int(math.floor(min(y0, y1) - rmax * sy - 1)),
                       int(math.ceil(max(y0, y1) + rmax * sy + 1)) + 1):
            for x in range(int(math.floor(min(x0, x1) - rmax * sx - 1)),
                           int(math.ceil(max(x0, x1) + rmax * sx + 1)) + 1):
                px = x + 0.5 - x0
                py = y + 0.5 - y0
                if L2 <= 1e-6:
                    t = 0.0
                else:
                    t = (px * dx + py * dy) / L2
                    t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
                qx = px - dx * t
                qy = py - dy * t
                r = r0 + (r1 - r0) * t
                u = (qx / (r * sx)) ** 2 + (qy / (r * sy)) ** 2
                if u <= 1.0:
                    ln = math.hypot(dx, dy)
                    vperp = 0.0
                    if ln > 1e-6:
                        vperp = (qx * (-dy / ln) + qy * (dx / ln)) / max(1e-6, r)
                    nu = t * 2.0 - 1.0
                    self.put(x, y, fn(x, y, max(-1.0, min(1.0, vperp)), nu, math.sqrt(u)))

    def cyl(self, p0, p1, hw0, fn, hw1=None, round_ends=(True, True), squash=(1.0, 1.0)):
        """لولهٔ صفحه‌ای با مقطع گرد (مثل capsule) ولی با امکان سرِ تخت."""
        hw1 = hw0 if hw1 is None else hw1
        x0, y0 = p0
        x1, y1 = p1
        dx, dy = x1 - x0, y1 - y0
        L = math.hypot(dx, dy)
        rmax = max(hw0, hw1) + 1.0
        sx, sy = squash
        for y in range(int(math.floor(min(y0, y1) - rmax * sy - 1)),
                       int(math.ceil(max(y0, y1) + rmax * sy + 1)) + 1):
            for x in range(int(math.floor(min(x0, x1) - rmax * sx - 1)),
                           int(math.ceil(max(x0, x1) + rmax * sx + 1)) + 1):
                px = x + 0.5 - x0
                py = y + 0.5 - y0
                if L <= 1e-6:
                    t, over = 0.0, 0.0
                else:
                    proj = (px * dx + py * dy)
                    over = 0.0
                    if proj < 0.0:
                        over = -proj
                    elif proj > L * L:
                        over = proj - L * L
                    t = min(1.0, max(0.0, proj / (L * L)))
                hw = hw0 + (hw1 - hw0) * t
                if L > 1e-6:
                    perp = (px * (-dy / L) + py * (dx / L))
                else:
                    perp = 0.0
                over_len = over / max(1e-6, L)
                cap_ok = over_len <= 0.0
                if not cap_ok:
                    if (t <= 0.0 and round_ends[0]) or (t >= 1.0 and round_ends[1]):
                        cap_ok = math.hypot(perp, over_len) <= hw
                if not cap_ok or abs(perp) > hw:
                    continue
                ux = abs(perp / hw)
                uz = over_len / hw if over_len > 0 else 0.0
                d = math.hypot(ux, uz)
                self.put(x, y, fn(x, y, max(-1.0, min(1.0, perp / hw)), t * 2.0 - 1.0, min(1.0, d)))

    def rrect(self, cx, cy, hw, hh, rad, fn, ang=0.0):
        rmax = max(hw, hh) + 1.0
        ca, sa = math.cos(ang), math.sin(ang)
        for y in range(int(math.floor(cy - rmax)), int(math.ceil(cy + rmax)) + 1):
            for x in range(int(math.floor(cx - rmax)), int(math.ceil(cx + rmax)) + 1):
                dx = x + 0.5 - cx
                dy = y + 0.5 - cy
                if ang:
                    ux = dx * ca + dy * sa
                    uy = -dx * sa + dy * ca
                else:
                    ux, uy = dx, dy
                qx = abs(ux) - (hw - rad)
                qy = abs(uy) - (hh - rad)
                dist = math.hypot(max(qx, 0.0), max(qy, 0.0)) + min(max(qx, qy), 0.0) - rad
                if dist <= 0.0:
                    d = min(1.0, max(abs(ux) / max(hw, 0.001), abs(uy) / max(hh, 0.001)))
                    self.put(x, y, fn(x, y, ux / max(hw, 0.001), uy / max(hh, 0.001), d))

    def poly(self, pts, fn):
        ys = [p[1] for p in pts]
        xs = [p[0] for p in pts]
        y0, y1 = int(math.floor(min(ys))), int(math.ceil(max(ys)))
        cxm = (min(xs) + max(xs)) * 0.5
        cym = (min(ys) + max(ys)) * 0.5
        hx = max(0.5, (max(xs) - min(xs)) * 0.5)
        hy = max(0.5, (max(ys) - min(ys)) * 0.5)
        for y in range(y0, y1 + 1):
            sy = y + 0.5
            cross = []
            n = len(pts)
            for i in range(n):
                ax, ay = pts[i]
                bx, by = pts[(i + 1) % n]
                if (ay <= sy < by) or (by <= sy < ay):
                    k = (sy - ay) / (by - ay)
                    cross.append(ax + (bx - ax) * k)
            cross.sort()
            for i in range(0, len(cross) - 1, 2):
                for x in range(int(math.floor(cross[i])), int(math.ceil(cross[i + 1])) + 1):
                    if x + 0.5 < cross[i] or x + 0.5 > cross[i + 1]:
                        continue
                    nx = (x + 0.5 - cxm) / hx
                    ny = (sy - cym) / hy
                    self.put(x, y, fn(x, y, nx, ny, math.hypot(nx, ny)))

    # ---- جلوه‌ها
    def line(self, p0, p1, fn, width=1.0):
        self.capsule(p0, p1, width * 0.5, width * 0.5, fn)

    def glow(self, cx, cy, r, color, alpha=90, power=1.6):
        for y in range(int(cy - r), int(cy + r) + 1):
            for x in range(int(cx - r), int(cx + r) + 1):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / max(0.5, r)
                if d <= 1.0:
                    a = int(alpha * (1.0 - d) ** power)
                    if a > 0:
                        self.blend(x, y, rgba(color, a))

    def dithered_disc(self, cx, cy, rx, ry, color, alpha=70, core=0.62, mid=0.86):
        for y in range(int(cy - ry - 1), int(cy + ry + 2)):
            for x in range(int(cx - rx - 1), int(cx + rx + 2)):
                u = (x + 0.5 - cx) / rx
                v = (y + 0.5 - cy) / ry
                d = math.hypot(u, v)
                if d > 1.0:
                    continue
                if d <= core:
                    self.blend(x, y, rgba(color, alpha))
                elif d <= mid and ((x + y) & 1) == 0:
                    self.blend(x, y, rgba(color, int(alpha * 0.70)))

    # ---- خط دور
    def outline(self, strength=0.34, tint=None):
        src = bytes(self.buf)
        w, h = self.w, self.h
        for y in range(h):
            for x in range(w):
                i = (y * w + x) * 4
                if src[i + 3] > 40:
                    continue
                acc = [0, 0, 0]
                cnt = 0
                for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + ox, y + oy
                    if 0 <= nx < w and 0 <= ny < h:
                        j = (ny * w + nx) * 4
                        if src[j + 3] > 128:
                            acc[0] += src[j]
                            acc[1] += src[j + 1]
                            acc[2] += src[j + 2]
                            cnt += 1
                if cnt == 0:
                    continue
                base = tint if tint else (acc[0] // cnt, acc[1] // cnt, acc[2] // cnt)
                k = 1.0 - strength
                j = i
                self.buf[j] = min(255, int(base[0] * k) + 5)
                self.buf[j + 1] = min(255, int(base[1] * k) + 5)
                self.buf[j + 2] = min(255, int(base[2] * k) + 4)
                self.buf[j + 3] = 255

    # ---- ترکیب
    def draw_over(self, other):
        w = self.w
        buf = self.buf
        ob = other.buf
        for i in range(0, len(ob), 4):
            if ob[i + 3] > 0:
                self.blend((i // 4) % w, (i // 4) // w, tuple(ob[i:i + 4]))

    def to_image(self):
        return Image.frombytes("RGBA", (self.w, self.h), bytes(self.buf))


# ------------------------------------------------------------------ ریگ جهت‌ها

DIRS = ("front", "back", "left", "right")


class Rig:
    """نقشهٔ مختصات محلی بدن (جلو، پهلو، ارتفاع) به صفحه.

    دوربین از بالا با شیب ملایم نگاه می‌کند: محور عمق (y جهان) با ضریب
    DEPTH فشرده می‌شود و محور افقی جهان کامل می‌ماند؛ ارتفاع بدن هم به
    همان اندازهٔ واقعی روی محور عمودی صفحه می‌نشیند.
    """

    def __init__(self, direction, pitch=0.0, pivot=0.0, sway=0.0, shift=(0.0, 0.0), yaw=0.0):
        self.dir = direction
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
            a *= GUN_DEPTH
        wx, wy = self.world(a, side)
        return (CX + wx + self.sway + self.shift[0],
                GROUND_Y - z + wy * DEPTH + self.shift[1])

    def sq(self, side_r, along_r, base):
        """کشیدگی صفحه برای مقطع‌های گرد بدن (پهلو و جلو جداگانه فشرده می‌شوند)."""
        if self.vertical:
            return (side_r / base, along_r * DEPTH / base)
        return (along_r / base, side_r * DEPTH / base)

    def box3(self, canvas, along0, along1, side0, side1, z0, z1, fn_top, fn_side):
        """جعبهٔ محلی؛ رویه (سقف) و وجه نزدیک به دوربین رسم می‌شود."""
        top = [self.pt(along0, side0, z1), self.pt(along1, side0, z1),
               self.pt(along1, side1, z1), self.pt(along0, side1, z1)]
        canvas.poly(top, fn_top)
        faces = (
            ((along0, side0), (along0, side1)),
            ((along1, side0), (along1, side1)),
            ((along0, side0), (along1, side0)),
            ((along0, side1), (along1, side1)),
        )
        best = None
        best_y = -1e9
        for (a0, s0), (a1, s1) in faces:
            am, sm = (a0 + a1) * 0.5, (s0 + s1) * 0.5
            _, wy = self.world(am, sm)
            y_scr = wy * DEPTH
            if y_scr > best_y:
                best_y = y_scr
                best = ((a0, s0), (a1, s1))
        (a0, s0), (a1, s1) = best
        quad = [self.pt(a0, s0, z1), self.pt(a1, s1, z1),
                self.pt(a1, s1, z0), self.pt(a0, s0, z0)]
        canvas.poly(quad, fn_side)


# ------------------------------------------------------------------ بدن سرباز

DEFAULTS = {
    "dir": "front",
    "pitch": 0.0,
    "pivot": 0.0,
    "sway": 0.0,
    "bob": 0.0,            # بالا رفتن بدن
    "lean": 0.0,           # جابه‌جایی بدن در راستای نگاه
    "breathe": 0.0,        # باز و بسته شدن سینه
    "head_fwd": 0.0,
    "head_side": 0.0,
    "head_loll": 0.0,
    "feet": ((0.0, -FOOT_SIDE, 0.0, 1.0), (0.0, FOOT_SIDE, 0.0, 1.0)),
    "arm_swing": 0.0,
    "arm_flail": 0.0,
    "sprawl": 0.0,
    "yaw": 0.0,
    "center_h": None,
    "center_dx": 0.0,
    "aim": 0.0,
    "recoil": 0.0,
    "flash": 0.0,
    "smoke": 0.0,
    "casing": 0.0,
    "dust": 0.0,
    "gun_drop": 0.0,
    "shadow": (10.6, 3.7, 42),
    "shift": (0.0, 0.0),
}


def params(**kw):
    p = dict(DEFAULTS)
    p.update(kw)
    return p


# ---- موادی که در چند جا استفاده می‌شوند
CLOTH_TOP = camo(lit(OLIVE, gain=0.45, bias=0.38, rim=0.10), seed=3, amount=15)
CLOTH_SIDE = camo(lit(OLIVE, gain=0.42, bias=-0.05, rim=0.14), seed=3, amount=16)
SLEEVE = camo(lit(OLIVE, gain=0.45, bias=0.24, rim=0.08), seed=3, amount=18)
PANTS_SIDE = camo(lit(OLIVE, gain=0.40, bias=-0.20, rim=0.14), seed=13, amount=14)
VEST_TOP = ao_edge(camo(lit(VEST, gain=0.45, bias=0.30, rim=0.12), seed=11, amount=10), 0.28, 0.20)
VEST_SIDE = lit(VEST, gain=0.32, bias=-0.10, rim=0.16)
PACK_TOP = ao_edge(camo(lit(OLIVE_L, gain=0.42, bias=0.10, rim=0.14), seed=5, amount=15), 0.28, 0.20)
PACK_SIDE = lit(OLIVE, gain=0.32, bias=-0.24, rim=0.16)
BOOT_TOP = ao_edge(lit(KHAKI, gain=0.40, bias=0.58, rim=0.12), 0.28, 0.20)
BOOT_SIDE = lit(KHAKI, gain=0.34, bias=-0.12, rim=0.16)
GUN_TOP = lit(GUN, gain=0.50, bias=0.10, rim=0.16)
GUN_SIDE = lit(GUN, gain=0.34, bias=-0.30, rim=0.18)
HELM_TOP = camo(lit(HELMET, gain=0.52, bias=0.26, rim=0.14), seed=9, amount=12)
HELM_BRIM = ao_edge(lit(OLIVE, gain=0.40, bias=-0.72, rim=0.18), 0.28, 0.26)
SKIN_F = ao_edge(lit(SKIN, gain=0.55, bias=0.10, rim=0.20), 0.32, 0.28)
WEBBING = lit(VEST, gain=0.30, bias=-0.28, rim=0.16)


def _foot_order(rig, feet):
    """پای دورتر اول رسم می‌شود تا پای نزدیک رویش بیفتد."""
    if rig.vertical:
        return [0, 1] if rig.dir == "front" else [1, 0]
    return sorted((0, 1), key=lambda i: -feet[i][1] if rig.dir == "left" else feet[i][1])


def _draw_leg(canvas, rig, along, side, lift, sc):
    sp = p_sprawl = 0.0
    del sp, p_sprawl
    hip = rig.pt(along * 0.16, side * 0.60, Z_HIP - 0.6 + lift * 0.35)
    knee = rig.pt(along * 0.60, side * 0.92, Z_KNEE + lift * 0.65)
    ankle = rig.pt(along * 0.84, side * 0.96, Z_BOOT + lift + 0.4)
    canvas.cyl(hip, knee, 2.9 * sc, PANTS_SIDE, hw1=2.5 * sc, round_ends=(True, False))
    canvas.cyl(knee, ankle, 2.5 * sc, PANTS_SIDE, hw1=2.2 * sc, round_ends=(False, False))
    kp = rig.pt(along * 0.64, side, Z_KNEE + lift * 0.62)
    canvas.ellipse(kp[0], kp[1], 3.0 * sc, 2.3 * sc, lit(VEST, gain=0.42, bias=-0.18))


def _draw_boot(canvas, rig, along, side, lift, sc):
    heel = along - 2.4 * sc
    toe = along + 4.6 * sc
    half = 2.45 * sc
    z0 = lift
    z1 = lift + Z_BOOT
    rig.box3(canvas, heel, toe, side - half, side + half, z0, z1, BOOT_TOP, BOOT_SIDE)
    sole0 = rig.pt(heel + 0.6, side - half + 0.4, z0 + 0.7)
    sole1 = rig.pt(toe - 0.6, side + half - 0.4, z0 + 0.7)
    canvas.capsule(sole0, sole1, 0.9, 0.9, flat(INK, -0.30), squash=rig.sq(1.0, 6.0, 1.0))
    # سرپوش ساق (گتر) تا پوتین با شلوار یکی نشود
    rig.box3(canvas, along - 2.6 * sc, along + 1.0 * sc, side - half + 0.15, side + half - 0.15,
             z1 - 1.0, z1 + 1.2,
             ao_edge(lit(OLIVE, gain=0.42, bias=-0.16, rim=0.18), 0.26, 0.24),
             lit(OLIVE, gain=0.32, bias=-0.52))
    cap = rig.pt(toe - 1.2 * sc, side, z1 + 0.4)
    canvas.ellipse(cap[0], cap[1], 2.0 * sc, 1.6 * sc, lit(KHAKI, gain=0.45, bias=0.24))


def _draw_torso(canvas, rig, p):
    breathe = p["breathe"]
    lean = p["lean"]
    hip = rig.pt(lean, 0.0, Z_HIP - 1.2)
    sh = rig.pt(lean - 0.5, 0.0, Z_SHOULDER - 0.3)
    if rig.vertical:
        w0, w1 = 5.6, 6.9 + breathe * 0.45
    else:
        w0, w1 = 4.1, 4.5 + breathe * 0.25
    canvas.cyl(hip, sh, w0, CLOTH_SIDE, hw1=w1, round_ends=(True, False))
    # یوک شانه: مهم‌ترین جزء سیلوئت از نمای بالا
    rig.box3(canvas, lean - 3.4, lean + 2.4, -7.0 - breathe * 0.4, 7.0 + breathe * 0.4,
             Z_SHOULDER - 2.2, Z_SHOULDER + 1.2, ao_edge(CLOTH_TOP, 0.30, 0.22), CLOTH_SIDE)
    top = rig.pt(lean - 0.6, 0.0, Z_SHOULDER + 0.9)
    tr = rig.sq(6.2 + breathe * 0.40, 4.2 + breathe * 0.30, 1.0)
    canvas.ellipse(top[0], top[1], tr[0], tr[1], CLOTH_TOP)
    # کمربند و سگک
    rig.box3(canvas, lean - 3.6, lean + 3.8, -6.3, 6.3, Z_HIP - 1.8, Z_BELT - 0.4,
             flat(VEST, -0.55), flat(VEST, -0.85))
    buckle = rig.pt(lean + 4.2, 0.0, Z_BELT - 1.6)
    canvas.rrect(buckle[0], buckle[1], 1.5, 1.1, 0.4, lit(BRASS, gain=0.4, bias=-0.45))


def _draw_chest_gear(canvas, rig, p):
    """جلیقهٔ سینه با خشاب‌دان و نوار (نمای روبه‌رو)."""
    lean = p["lean"]
    rig.box3(canvas, lean + 1.0, lean + 5.0, -5.0, 5.0, Z_HIP + 2.2, Z_CHEST + 1.2,
             VEST_TOP, VEST_SIDE)
    for sm in (-2.4, 2.4):
        rig.box3(canvas, lean + 4.4, lean + 6.4, sm - 1.9, sm + 1.9, Z_HIP + 2.6, Z_CHEST - 0.2,
                 ao_edge(lit(KHAKI, gain=0.42, bias=0.30, rim=0.14), 0.26, 0.22),
                 lit(KHAKI, gain=0.30, bias=-0.42))
    # حلقهٔ کمربند جلیقه
    for sm in (-4.9, 4.9):
        s0 = rig.pt(lean + 5.2, sm, Z_HIP + 3.4)
        s1 = rig.pt(lean + 5.2, sm, Z_HIP + 1.6)
        canvas.capsule(s0, s1, 1.0, 1.0, WEBBING, squash=(1.0, 0.85))


def _draw_pack(canvas, rig, p):
    """کوله‌پشتی (نمای پشت)."""
    lean = p["lean"]
    rig.box3(canvas, lean - 8.2, lean - 1.8, -5.2, 5.2, Z_HIP + 0.8, Z_SHOULDER - 0.6,
             PACK_TOP, PACK_SIDE)
    lid = rig.pt(lean - 5.2, 0.0, Z_SHOULDER + 1.0)
    lr = rig.sq(5.4, 3.0, 1.0)
    canvas.ellipse(lid[0], lid[1], lr[0], lr[1], lit(VEST, gain=0.40, bias=-0.20))
    rig.box3(canvas, lean - 8.0, lean - 3.4, -4.6, -2.2, Z_HIP - 1.2, Z_CHEST,
             lit(KHAKI, gain=0.45, bias=0.22), lit(KHAKI, gain=0.28, bias=-0.5))
    for sm in (-5.4, 5.4):
        s0 = rig.pt(lean - 2.6, sm, Z_SHOULDER + 0.4)
        s1 = rig.pt(lean - 2.0, sm * 0.9, Z_HIP - 0.6)
        canvas.capsule(s0, s1, 1.2, 1.2, flat(VEST, -0.9), squash=(1.0, 0.85))


def _draw_head(canvas, rig, p):
    """سر، چهره و کلاه؛ یک واحد به‌هم‌پیوسته تا در همهٔ نماها خوانا بماند."""
    lean = p["lean"] + p["head_fwd"] * 0.5
    side = p["head_side"]
    fwd = 1.0 + lean
    # گردن
    n = rig.pt(0.4 + lean, side, Z_NECK)
    canvas.ellipse(n[0], n[1], 3.2, 2.6, lit(SKIN, gain=0.40, bias=-0.80))
    # سر (پوست)
    hc = rig.pt(fwd, side, Z_FACE)
    if rig.vertical:
        canvas.ellipse(hc[0], hc[1], 4.0 if rig.dir == "front" else 3.5, 3.6, SKIN_F)
    else:
        canvas.ellipse(hc[0], hc[1], 4.0, 3.7, SKIN_F)
    # چهره فقط از روبرو دیده می‌شود: نوار عینک تاکتیکی و خط دهان
    if rig.dir == "front":
        v = rig.pt(fwd + 1.6, side, Z_FACE + 0.8)
        canvas.rrect(v[0], v[1], 2.9, 0.85, 0.35, flat(INK, -0.40))
        m = rig.pt(fwd + 2.0, side, Z_CHIN + 0.9)
        canvas.rrect(m[0], m[1], 1.4, 0.6, 0.25, flat(SKIN, -0.5))
    # گنبد کلاه
    dc = rig.pt(0.0 + lean, side, Z_DOME)
    dry = R_HELMET * 0.74 * (1.0 + 0.40 * math.sin(rig.pitch))
    canvas.ellipse(dc[0], dc[1], R_HELMET, dry, HELM_TOP, ang=p["head_loll"])
    # گیرهٔ تجهیزات روی پیشانی کلاه
    nv = rig.pt(fwd + 3.4, side, Z_DOME - 1.6)
    canvas.rrect(nv[0], nv[1], 1.7, 1.1, 0.45, HELM_BRIM)
    # لبهٔ کلاه
    if rig.vertical:
        b = rig.pt(fwd + 1.8, side, Z_BRIM)
        canvas.ellipse(b[0], b[1], R_HELMET + 0.7, 1.6, HELM_BRIM)
    else:
        for sm in (-1.0, 1.0):
            b = rig.pt(fwd - 0.4, side + sm * 4.9, Z_BRIM)
            canvas.ellipse(b[0], b[1], 2.7, 1.8, HELM_BRIM)
    # تاجِ روشن‌تر روی کلاه (بازتاب نور از بالا)
    cr = rig.pt(-1.6 + lean, side - 1.7, Z_DOME + 1.2)
    canvas.ellipse(cr[0], cr[1], R_HELMET - 2.9, max(0.9, dry - 1.6),
                   lit(OLIVE_L, gain=0.55, bias=0.26, rim=0.0))
    # نوار دور کلاه
    if rig.vertical:
        b0 = rig.pt(-1.2 + lean, side - 5.4, Z_DOME - 0.2)
        b1 = rig.pt(-1.2 + lean, side + 5.4, Z_DOME - 0.2)
        canvas.capsule(b0, b1, 0.8, 0.8, solid(OLIVE[0], 115), squash=(1.0, 0.5))
    # بند چانه: در نمای روبه‌رو خوانا نیست، فقط دو تکهٔ کنارِ گوش
    if not rig.vertical:
        for sm in (-1.0, 1.0):
            k0 = rig.pt(fwd - 0.6, side + sm * 3.4, Z_CHIN + 1.2)
            k1 = rig.pt(fwd - 0.6, side + sm * 4.6, Z_NECK + 0.4)
            canvas.capsule(k0, k1, 0.8, 0.8, WEBBING, squash=rig.sq(1.0, 2.6, 1.0))


def _gun_pose(p, rig):
    """نقاط کلیدی تفنگ در صفحه (تفنگ در سمت راست بدن، دست چپ جلو را می‌گیرد)."""
    aim = p["aim"]
    recoil = p["recoil"]
    lean = p["lean"]

    def mix(a, b):
        t = aim
        return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)

    if rig.vertical:
        # در نمای روبه‌رو/پشت، شانه‌ها هم‌تراز دهانه‌اند و شعله روی سینه می‌افتد؛
        # تفنگ کمی مورب و پایین‌تر گرفته می‌شود تا دهانه بیرون از سیلوئت بدن بماند
        aim_butt = rig.pt(lean - 1.6, -3.4, Z_SHOULDER - 1.2, gun=True)
        aim_grip = rig.pt(lean + 2.8, -3.6, Z_SHOULDER - 2.2, gun=True)
        aim_hand = rig.pt(lean + 9.4, -6.2, Z_CHEST + 0.6, gun=True)
        aim_muzzle = rig.pt(lean + 14.0, -7.8, Z_CHEST - 1.2, gun=True)
    else:
        aim_butt = rig.pt(lean - 1.6, -3.0, Z_SHOULDER - 1.0, gun=True)
        aim_grip = rig.pt(lean + 2.4, -2.6, Z_SHOULDER - 1.6, gun=True)
        aim_hand = rig.pt(lean + 10.6, -2.2, Z_SHOULDER - 2.0, gun=True)
        aim_muzzle = rig.pt(lean + 16.4, -1.8, Z_SHOULDER - 2.4, gun=True)
    if rig.vertical:
        # حالت آماده در نمای روبه‌رو/پشت: تفنگ مورب روی بدن حمل می‌شود
        ready_butt = rig.pt(lean - 3.2, -4.4, Z_HIP + 2.6, gun=True)
        ready_grip = rig.pt(lean + 0.8, -4.2, Z_HIP + 0.8, gun=True)
        ready_hand = rig.pt(lean + 7.0, 3.2, Z_BELT - 0.6, gun=True)
        ready_muzzle = rig.pt(lean + 10.6, 6.2, Z_CHEST + 0.4, gun=True)
    else:
        ready_butt = rig.pt(lean - 3.4, -4.2, Z_HIP + 2.4, gun=True)
        ready_grip = rig.pt(lean + 0.6, -4.0, Z_HIP + 1.0, gun=True)
        ready_hand = rig.pt(lean + 8.6, -4.4, Z_BELT - 1.6, gun=True)
        ready_muzzle = rig.pt(lean + 14.6, -4.6, Z_KNEE + 2.4, gun=True)
    butt = mix(ready_butt, aim_butt)
    grip = mix(ready_grip, aim_grip)
    hand = mix(ready_hand, aim_hand)
    muzzle = mix(ready_muzzle, aim_muzzle)
    dx, dy = muzzle[0] - butt[0], muzzle[1] - butt[1]
    ln = max(1e-6, math.hypot(dx, dy))
    ux, uy = dx / ln, dy / ln
    out = {"axis": (ux, uy), "len": ln}
    for name, ptv in (("butt", butt), ("grip", grip), ("hand", hand), ("muzzle", muzzle)):
        out[name] = (ptv[0] - ux * recoil, ptv[1] - uy * recoil)
    out["rear"] = (out["butt"][0] - ux * 2.6, out["butt"][1] - uy * 2.6)
    return out


def _draw_rifle(canvas, rig, p, g):
    ux, uy = g["axis"]
    px, py = -uy, ux
    butt, grip, hand, muzzle, rear = g["butt"], g["grip"], g["hand"], g["muzzle"], g["rear"]
    canvas.capsule(rear, butt, 1.9, 2.3, lit(VEST, gain=0.35, bias=-0.18), squash=(1.0, 0.95))
    canvas.capsule(butt, grip, 2.4, 2.4, GUN_SIDE, squash=(1.0, 0.95))
    mag_a = ((butt[0] + grip[0]) * 0.5 + px * 0.6, (butt[1] + grip[1]) * 0.5 + py * 0.6)
    mag_b = (mag_a[0] + py * 4.4 - ux * 1.2, mag_a[1] - px * 4.4 - uy * 1.2)
    canvas.capsule(mag_a, mag_b, 2.0, 1.5, flat(GUN, -0.5), squash=(1.0, 0.9))
    canvas.capsule(grip, hand, 2.2, 2.0, GUN_TOP, squash=(1.0, 0.95))
    canvas.capsule(hand, muzzle, 1.15, 1.05, GUN_SIDE, squash=(1.0, 0.9))
    tip = (muzzle[0] + ux * 1.8, muzzle[1] + uy * 1.8)
    canvas.capsule(muzzle, tip, 1.45, 1.15, flat(GUN, -0.8), squash=(1.0, 0.9))
    fs = (grip[0] + ux * 1.4, grip[1] + uy * 1.4)
    canvas.line((fs[0], fs[1]), (hand[0] - ux * 1.0, hand[1] - uy * 1.0), flat(GUN, 0.35), width=0.9)
    st = (butt[0] + ux * 0.6 + px * 1.4, butt[1] + uy * 0.6 + py * 1.4)
    canvas.capsule(st, (st[0] + ux * 2.4, st[1] + uy * 2.4), 0.8, 0.8, flat(GUN, 0.7))


def _draw_ground_gun(canvas, rig, p):
    """تفنگ رهاشده کنار پیکر (نمای پهلو و انیمیشن مرگ)."""
    t = p["gun_drop"]
    side = -3.4 - t * 6.0
    along = 1.0 + t * 3.6
    h = max(0.0, 2.6 * (1.0 - t)) + 0.5
    a = rig.pt(along - 5.4, side, h, gun=True)
    b = rig.pt(along + 12.4, side - 0.8, h, gun=True)
    canvas.capsule(a, b, 2.2, 1.3, GUN_SIDE, squash=(1.0, 0.95))
    m = rig.pt(along + 14.8, side - 1.0, h, gun=True)
    canvas.capsule(b, m, 1.2, 1.0, flat(GUN, -0.75), squash=(1.0, 0.9))
    st = rig.pt(along - 7.6, side + 0.4, h, gun=True)
    canvas.capsule(st, a, 1.8, 2.1, lit(OLIVE, gain=0.45, bias=-0.35), squash=(1.0, 0.95))


def _draw_arm(canvas, rig, p, sgn, hand_pt, full=True):
    """بازو از شانه تا دستِ روی تفنگ؛ sgn=-۱ بازوی راست (سمت تفنگ)."""
    lean = p["lean"]
    swing = p["arm_swing"] + p["arm_flail"]
    aim = p["aim"]
    sh = rig.pt(lean - 0.9, sgn * (6.0 + p["sprawl"] * 0.35), Z_SHOULDER - 1.6)
    el_side = sgn * (7.0 - aim * 1.5 + p["sprawl"])
    el = rig.pt(1.2 + lean + swing * (1.0 if sgn > 0 else 0.5), el_side, Z_CHEST - 1.4 + swing * 0.5)
    canvas.cyl(sh, el, 2.7, SLEEVE, hw1=2.2, round_ends=(True, True))
    if full:
        canvas.cyl(el, hand_pt, 2.2, SLEEVE, hw1=1.8, round_ends=(True, True))


def _draw_hand(canvas, pt):
    canvas.ellipse(pt[0], pt[1], 2.3, 2.0, SKIN_F)


def draw_soldier(p):
    """یک فریم کامل از سرباز در حالت خواسته‌شده."""
    rig = Rig(p["dir"], p["pitch"], p["pivot"], p["sway"],
              (p["shift"][0], -p["bob"] + p["shift"][1]), yaw=p["yaw"])
    if p["center_h"] is not None:
        # تعادل بدن افتاده: مرکز پیکر روی نقطهٔ دلخواه قاب قفل می‌شود
        px, py = rig.pt(0.0, 0.0, 16.0)
        rig.shift = (rig.shift[0] + (CX + p["center_dx"] - px),
                     rig.shift[1] + ((GROUND_Y - p["center_h"]) - py))
    body = Canvas()
    shadow = Canvas()

    sx, sy, sa = p["shadow"]
    shadow.dithered_disc(CX + p["sway"] * 0.30 + rig.shift[0],
                         GROUND_Y + 1.6 + rig.shift[1], sx, sy, (14, 16, 13),
                         alpha=int(sa), core=0.30, mid=0.80)

    feet = p["feet"]
    g = _gun_pose(p, rig)
    grip, hand = g["grip"], g["hand"]

    for k in _foot_order(rig, feet):
        a, s, lift, sc = feet[k]
        _draw_leg(body, rig, a, s, lift, sc)
    for k in _foot_order(rig, feet):
        a, s, lift, sc = feet[k]
        _draw_boot(body, rig, a, s, lift, sc)
    # شکاف تیرهٔ میان دو پا
    if rig.vertical:
        d0 = rig.pt(-1.4, 0.0, Z_BOOT + 0.6)
        d1 = rig.pt(-0.6, 0.0, Z_HIP - 2.4)
        body.capsule(d0, d1, 1.0, 1.0, flat(VEST, -0.95))

    if p["gun_drop"] > 0.02 or p["dir"] == "back":
        _draw_rifle(body, rig, p, g)
        _draw_hand(body, grip)
        _draw_hand(body, hand)

    _draw_torso(body, rig, p)
    if p["dir"] == "front":
        _draw_chest_gear(body, rig, p)
    if p["dir"] == "back":
        _draw_pack(body, rig, p)
    _draw_ground_gun(body, rig, p)

    order = (-1, 1) if p["dir"] in ("front", "back") else (1, -1)
    if p["dir"] == "back":
        for sgn in order:
            _draw_arm(body, rig, p, sgn, grip, full=False)
        _draw_head(body, rig, p)
    else:
        for sgn in order:
            _draw_arm(body, rig, p, sgn, hand if sgn < 0 else grip)
        if p["gun_drop"] < 0.5:
            _draw_rifle(body, rig, p, g)
        _draw_hand(body, grip)
        _draw_hand(body, hand)
        _draw_head(body, rig, p)

    if p["flash"] > 0.02:
        _muzzle_flash(body, p, g)
    if p["smoke"] > 0.02:
        _smoke_puff(body, p, g)
    if p["casing"] > 0.02:
        _casing(body, rig, p, g)
    if p["dust"] > 0.02:
        _dust(body, rig, p, feet, p["dust"])

    body.outline()
    out = Canvas()
    out.draw_over(shadow)
    out.draw_over(body)
    return out


# ------------------------------------------------------------------ جلوه‌های شلیک


def _muzzle_flash(canvas, p, g):
    f = p["flash"]
    ux, uy = g["axis"]
    mx, my = g["muzzle"]
    tip = (mx + ux * 2.2, my + uy * 2.2)
    canvas.glow(tip[0], tip[1], 4.4 + 4.4 * f, FLASH[1], alpha=int(104 * f), power=1.5)
    for i, ln in enumerate((2.0, 3.1, 4.3)):
        if f * 3.0 <= i:
            continue
        canvas.capsule(tip, (tip[0] + ux * ln * f, tip[1] + uy * ln * f), 1.9 * f, 0.9 * f, solid(FLASH[1]))
    px, py = -uy, ux
    for s in (-1.0, 1.0):
        if f > 0.5:
            canvas.capsule(tip, (tip[0] + px * s * 1.9 * f, tip[1] + py * s * 1.9 * f), 1.2 * f, 0.7 * f, solid(FLASH[1]))
    canvas.ellipse(tip[0], tip[1], 2.2 * f + 0.7, 2.0 * f + 0.7, flat(FLASH, 0.6))
    if f > 0.6:
        canvas.ellipse(tip[0], tip[1], 1.4, 1.3, solid(FLASH[3]))


def _smoke_puff(canvas, p, g):
    s = p["smoke"]
    mx, my = g["muzzle"]
    n = max(1, int(round(s * 5)))
    for i in range(n):
        k = (i + 1) / (n + 1)
        cx = mx + math.sin(i * 2.3) * (1.6 + 3.0 * k)
        cy = my - 2.4 - 5.6 * k * s
        r = 1.6 + 3.0 * k
        tone = SMOKE[1 + int(k * 2)]
        for y in range(int(cy - r), int(cy + r) + 1):
            for x in range(int(cx - r), int(cx + r) + 1):
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                    if ((x + y) & 1) == 0 or k < 0.45:
                        canvas.blend(x, y, rgba(tone, int(76 + 66 * s - 46 * k)))


def _casing(canvas, rig, p, g):
    t = p["casing"]
    ux, uy = g["axis"]
    px, py = -uy, ux
    side_dir = 1.0 if rig.dir in ("front", "right") else -1.0
    dist = 2.0 + 8.0 * t
    ex = g["grip"][0] + px * side_dir * dist * 0.9 + ux * dist * 0.5
    ey = g["grip"][1] + py * side_dir * dist * 0.9 + uy * dist * 0.5
    ey += (t - 0.38) ** 2 * 42.0 - 2.5
    canvas.capsule((ex - 1.3, ey), (ex + 1.3, ey), 0.8, 0.8, flat(BRASS, 0.3))


def _dust(canvas, rig, p, feet, d):
    for a, s, lift, sc in feet:
        if lift <= 0.06:
            continue
        ptv = rig.pt(a - 4.0, s, 0.8)
        r = 1.8 + 2.0 * d
        for y in range(int(ptv[1] - r), int(ptv[1] + r) + 1):
            for x in range(int(ptv[0] - r - 1), int(ptv[0] + r + 2) + 1):
                if math.hypot((x + 0.5 - ptv[0]) / (r + 1.2), (y + 0.7 - ptv[1]) / r) <= 1.0:
                    if ((x + y) & 1) == 0:
                        canvas.blend(x, y, rgba(DUST[1], int(64 * d + 26)))


# ------------------------------------------------------------------ انیمیشن‌ها


def _feet_walk(phase, stride, lift_h, side_sway=2.0, base=0.0):
    """چرخهٔ گام: پاها متناوب جلو و عقب می‌روند و کمی باز و بسته می‌شوند."""
    out = []
    for k in (0, 1):
        ph = phase + (0.0 if k == 0 else 0.5)
        sw = math.sin(ph * TAU)
        fwd = sw * stride + base
        lift = max(0.0, math.sin((ph + 0.25) * TAU)) * lift_h
        side = FOOT_SIDE * (-1.0 if k == 0 else 1.0) + math.cos(ph * TAU) * side_sway * 0.35
        sc = 1.0 - 0.08 * (lift / max(0.001, lift_h))
        out.append((fwd, side, lift, sc))
    return tuple(out)


def anim_idle(direction, i, n=7):
    ph = i / n
    br = math.sin(ph * TAU)
    return params(
        dir=direction,
        breathe=br * 0.5,
        bob=0.55 + br * 0.5,
        sway=math.sin(ph * TAU + 0.5) * 0.5,
        head_fwd=-br * 0.3,
        feet=((0.0, -FOOT_SIDE, 0.0, 1.0), (0.0, FOOT_SIDE, 0.0, 1.0)),
        arm_swing=br * 0.35,
        shadow=(10.6, 3.7, 52),
    )


def anim_walk(direction, i, n=7):
    ph = i / n
    return params(
        dir=direction,
        feet=_feet_walk(ph, 8.4, 4.0),
        bob=1.1 + math.sin(ph * TAU * 2.0) * 1.35,
        sway=math.sin(ph * TAU) * 1.3,
        breathe=math.sin(ph * TAU * 2.0) * 0.3,
        lean=0.8 + math.sin(ph * TAU * 2.0) * 0.4,
        arm_swing=math.sin(ph * TAU) * 0.9,
        head_fwd=0.25,
        shadow=(10.4, 3.6, 50),
    )


def anim_run(direction, i, n=7):
    ph = i / n
    feet = _feet_walk(ph, 11.5, 6.4, side_sway=2.8)
    return params(
        dir=direction,
        feet=feet,
        pitch=12.0 if i in (0, 3) else 9.5,
        pivot=12.0,
        bob=1.7 + math.sin(ph * TAU * 2.0 + 0.7) * 1.9,
        sway=math.sin(ph * TAU) * 1.5,
        breathe=0.4,
        lean=1.4,
        arm_swing=math.sin(ph * TAU) * 1.8,
        head_fwd=-0.8,
        dust=0.35 + 0.65 * max(0.0, math.sin(ph * TAU * 2.0)),
        shadow=(11.0, 3.8, 46),
    )


def anim_attack(direction, i, n=8):
    """۰ آماده، ۱ بالا آوردن، ۲ نشانه‌گیری، ۳ شلیک، ۴ پس‌زنی، ۵-۶ بازگشت."""
    if i >= n - 1:
        return anim_attack(direction, 0, n)
    table = [
        dict(aim=0.00, recoil=0.0, flash=0.0, smoke=0.0, casing=0.0, bob=0.6, lean=0.0, arm_swing=0.25, head_fwd=0.2),
        dict(aim=0.55, recoil=0.0, flash=0.0, smoke=0.0, casing=0.0, bob=0.85, lean=0.6, arm_swing=0.05, head_fwd=0.4),
        dict(aim=1.00, recoil=0.0, flash=0.0, smoke=0.0, casing=0.0, bob=1.0, lean=1.0, arm_swing=-0.3, head_fwd=0.5),
        dict(aim=1.00, recoil=2.4, flash=1.00, smoke=0.25, casing=0.10, bob=0.6, lean=0.2, arm_swing=-0.5, head_fwd=0.4),
        dict(aim=0.95, recoil=1.6, flash=0.5, smoke=0.75, casing=0.45, bob=0.8, lean=0.6, arm_swing=-0.32, head_fwd=0.4),
        dict(aim=0.85, recoil=0.6, flash=0.0, smoke=0.55, casing=0.80, bob=0.8, lean=0.8, arm_swing=-0.05, head_fwd=0.35),
        dict(aim=0.40, recoil=0.15, flash=0.0, smoke=0.22, casing=1.00, bob=0.7, lean=0.3, arm_swing=0.12, head_fwd=0.25),
    ]
    t = table[i]
    return params(
        dir=direction,
        breathe=0.25,
        feet=((0.8, -FOOT_SIDE, 0.0, 1.0), (0.4, FOOT_SIDE + 0.8, 0.0, 1.0)),
        shadow=(10.6, 3.7, 52),
        **t
    )


DEATH_AXIS = {   # (افقی، عمودی) جابه‌جایی صفحه وقتی سرباز رو به جلو می‌افتد
    "front": (0.0, 1.0),
    "back": (0.0, -1.0),
    "left": (-1.0, 0.35),
    "right": (1.0, 0.35),
}

DEATH_STEPS = [
    # pitch, fall, yaw, sprawl, flail, head_fwd, loll, bob, dust, gun, breathe, aim, sway, shadow
    dict(pitch=4.0, fall=0.5, yaw=0.0, sprawl=0.0, arm_flail=1.1, head_fwd=-1.8, head_loll=-0.12,
         bob=0.4, dust=0.0, gun_drop=0.0, breathe=0.30, aim=0.15, sway=-0.6, shadow=(10.9, 3.7, 50),
         feet=((0.6, -FOOT_SIDE, 0.0, 1.0), (0.0, FOOT_SIDE, 0.0, 1.0))),
    dict(pitch=16.0, fall=1.6, yaw=6.0, sprawl=0.8, arm_flail=1.8, head_fwd=-2.4, head_loll=-0.22,
         bob=-1.6, dust=0.15, gun_drop=0.06, breathe=0.15, aim=0.30, sway=1.5, shadow=(11.4, 3.9, 48),
         feet=((2.4, -FOOT_SIDE - 0.6, 1.2, 0.98), (-2.6, FOOT_SIDE + 0.6, 0.0, 1.0))),
    dict(pitch=38.0, fall=2.8, yaw=14.0, sprawl=1.8, arm_flail=2.4, head_fwd=-2.4, head_loll=-0.30,
         bob=-3.4, dust=0.35, gun_drop=0.24, breathe=0.05, aim=0.10, sway=1.9, shadow=(12.6, 4.3, 46),
         feet=((4.0, -FOOT_SIDE - 1.2, 2.2, 0.96), (-4.2, FOOT_SIDE + 1.0, 0.0, 1.0))),
    dict(pitch=60.0, fall=4.0, yaw=24.0, sprawl=3.0, arm_flail=2.9, head_fwd=1.0, head_loll=-0.30,
         bob=-5.2, dust=0.70, gun_drop=0.55, breathe=0.0, aim=0.0, sway=1.1, shadow=(15.4, 5.2, 44),
         feet=((-3.6, -FOOT_SIDE - 1.6, 3.2, 0.94), (-7.4, FOOT_SIDE + 1.2, 0.6, 0.98))),
    dict(pitch=74.0, fall=5.0, yaw=31.0, sprawl=3.8, arm_flail=3.1, head_fwd=2.4, head_loll=-0.18,
         bob=-6.0, dust=1.0, gun_drop=0.86, breathe=0.0, aim=0.0, sway=0.4, shadow=(18.2, 6.0, 40),
         feet=((-5.0, -FOOT_SIDE - 2.0, 3.4, 0.92), (-9.4, FOOT_SIDE + 1.6, 0.8, 0.96))),
    dict(pitch=78.0, fall=5.4, yaw=34.0, sprawl=4.2, arm_flail=3.3, head_fwd=3.0, head_loll=-0.06,
         bob=-6.4, dust=0.5, gun_drop=1.0, breathe=0.0, aim=0.0, sway=0.0, shadow=(19.6, 6.6, 36),
         feet=((-5.8, -FOOT_SIDE - 2.2, 3.2, 0.92), (-10.4, FOOT_SIDE + 1.8, 0.7, 0.96))),
]


def anim_death(direction, i, n=6):
    """۰ اصابت، ۱ پس‌رفتن، ۲ زانو، ۳ افتادن، ۴ برخورد، ۵ آرام گرفتن روی زمین.

    بدن با «pitch» روی زمین می‌افتد و با «yaw» کمی می‌چرخد تا سیلوئت افتاده
    مورب و خوانا شود. جابه‌جایی قاب از محورِ افتادنِ همان جهت حساب می‌شود و
    «center_h/center_dx» مرکز پیکر را در قاب قفل می‌کند تا در هر چهار جهت
    داخل کادر ۶۴×۶۴ بماند.
    """
    t = dict(DEATH_STEPS[min(i, len(DEATH_STEPS) - 1)])
    ax = DEATH_AXIS[direction]
    fall = t.pop("fall")
    if direction in ("left", "right"):
        t["yaw"] = t["yaw"] * 0.55
    t["center_dx"] = ax[0] * fall
    t["center_h"] = 15.0 - ax[1] * fall
    return params(dir=direction, **t)


ANIMS = {
    "idle": dict(frames=8, unique=7, loop=True, fps=6, fn=anim_idle, label="ایستاده (تنفس)"),
    "walk": dict(frames=8, unique=7, loop=True, fps=10, fn=anim_walk, label="راه رفتن"),
    "run": dict(frames=8, unique=7, loop=True, fps=14, fn=anim_run, label="دویدن"),
    "attack": dict(frames=8, unique=7, loop=True, fps=12, fn=anim_attack, label="نشانه‌گیری و شلیک"),
    "death": dict(frames=6, unique=6, loop=False, fps=8, fn=anim_death, label="افتادن"),
}


def frames_for(anim, direction):
    """فریم‌های یک انیمیشن؛ فریم آخر برای لوپ دقیقاً برابر فریم اول است."""
    spec = ANIMS[anim]
    n = spec["frames"]
    out = [spec["fn"](direction, i, n) for i in range(n)]
    if spec["loop"]:
        out[-1] = out[0]
    return out


def render_frames(anim, direction):
    return [draw_soldier(p).to_image() for p in frames_for(anim, direction)]


# ------------------------------------------------------------------ خروجی


def sheet_name(anim, direction):
    return "soldier_%s_%s" % (anim, direction)


def build_sheet(images):
    sheet = Image.new("RGBA", (FRAME * len(images), FRAME), (0, 0, 0, 0))
    for i, im in enumerate(images):
        sheet.paste(im, (i * FRAME, 0))
    return sheet


def tp_json(name, count, label, fps, loop):
    """قالب TexturePacker JSON Hash برای یک شیت."""
    frames = {}
    keys = []
    for i in range(count):
        key = "%s_%02d" % (name, i)
        keys.append(key)
        frames[key] = {
            "frame": {"x": i * FRAME, "y": 0, "w": FRAME, "h": FRAME},
            "rotated": False,
            "trimmed": False,
            "spriteSourceSize": {"x": 0, "y": 0, "w": FRAME, "h": FRAME},
            "sourceSize": {"w": FRAME, "h": FRAME},
            "pivot": {"x": 0.5, "y": round(GROUND_Y / FRAME, 4)},
        }
    return {
        "frames": frames,
        "animations": {name: keys},
        "meta": {
            "app": "Dezhban military-soldier sprite generator",
            "version": "1.0",
            "image": name + ".png",
            "format": "RGBA8888",
            "size": {"w": FRAME * count, "h": FRAME},
            "scale": "1",
            "frameTags": [{"name": name, "from": 0, "to": count - 1,
                           "direction": "loop" if loop else "forward",
                           "fps": fps, "label": label}],
        },
    }


def write_text(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        handle.write(text)


def dump_json(path, data):
    write_text(path, json.dumps(data, ensure_ascii=False, indent=2) + "\n")


# ------------------------------------------------------------------ بررسی


def check_pack(pack_dir=OUT_DIR):
    """بررسی سلامت بسته: اندازه‌ها، حاشیهٔ شفاف، لوپ و پوشش همهٔ ترکیب‌ها."""
    problems = []
    for anim, spec in ANIMS.items():
        for direction in DIRS:
            name = sheet_name(anim, direction)
            path = os.path.join(pack_dir, name + ".png")
            if not os.path.exists(path):
                problems.append("نبودِ شیت %s" % path)
                continue
            im = Image.open(path).convert("RGBA")
            want = (FRAME * spec["frames"], FRAME)
            if im.size != want:
                problems.append("%s اندازهٔ نادرست %s (باید %s)" % (name, im.size, want))
                continue
            for i in range(spec["frames"]):
                block = im.crop((i * FRAME, 0, (i + 1) * FRAME, FRAME))
                bbox = block.getchannel("A").getbbox()
                if bbox is None:
                    problems.append("%s فریم %d خالی است" % (name, i))
                    continue
                x0, y0, x1, y1 = bbox
                if min(x0, y0, FRAME - x1, FRAME - y1) < 1:
                    problems.append("%s فریم %d به لبهٔ قاب چسبیده %s" % (name, i, bbox))
            if spec["loop"]:
                a = im.crop((0, 0, FRAME, FRAME)).tobytes()
                b = im.crop(((spec["frames"] - 1) * FRAME, 0, spec["frames"] * FRAME, FRAME)).tobytes()
                if a != b:
                    problems.append("%s فریم آخر با فریم اول یکسان نیست" % name)
    if not os.path.exists(os.path.join(pack_dir, "soldier_pack.json")):
        problems.append("نبودِ soldier_pack.json")
    return problems


# ------------------------------------------------------------------ پیش‌نمایش


def font_small(size=11):
    for path in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                 "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf"):
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                pass
    return ImageFont.load_default()


def contact_sheet(zoom=2, pad=6, bg=(26, 29, 24, 255)):
    """برگهٔ نمونه: هر سطر یک شیت (انیمیشن × جهت) با فریم‌های کنار هم."""
    rows = [(a, d) for a in ANIMS for d in DIRS]
    cell = FRAME * zoom
    label_w = 132
    header = 44
    maxw = FRAME * max(ANIMS[a]["frames"] for a in ANIMS)
    w = label_w + pad * 2 + maxw * zoom
    h = header + len(rows) * (cell + pad) + pad
    img = Image.new("RGBA", (w, h), bg)
    d = ImageDraw.Draw(img)
    f = font_small(14)
    fs = font_small(11)
    d.text((pad, 10), "Military soldier - 64x64 frames - 4 directions - TexturePacker JSON Hash",
           fill=(232, 236, 226, 255), font=f)
    d.text((pad, 27), "TexturePacker JSON Hash beside every sheet  |  last frame = frame 1 (loop)  |  pivot x=32 y=50",
           fill=(150, 165, 142, 255), font=fs)
    y = header
    for anim, direction in rows:
        spec = ANIMS[anim]
        fidx = ANIMS["idle"] and None
        del fidx
        strip = Image.open(os.path.join(OUT_DIR, sheet_name(anim, direction) + ".png")).convert("RGBA")
        d.text((pad, y + 6), "%s / %s" % (anim, direction), fill=(230, 236, 220, 255), font=f)
        d.text((pad, y + 24), "%d frames  %d fps  %s" % (spec["frames"], spec["fps"],
                                                         "loop" if spec["loop"] else "one shot"),
               fill=(146, 160, 138, 255), font=fs)
        for i in range(spec["frames"]):
            tile = strip.crop((i * FRAME, 0, (i + 1) * FRAME, FRAME)).resize((cell, cell), Image.NEAREST)
            x = label_w + pad + i * cell
            d.rectangle([x, y, x + cell - 1, y + cell - 1], outline=(52, 57, 46, 255))
            img.alpha_composite(tile, (x, y))
        y += cell + pad
    return img


def preview_gif(path, zoom=1, fps=20, bg=(44, 48, 40)):
    """گیف پیش‌نمایش: هر سطر یک انیمیشن، هر ستون یک جهت."""
    rows = list(ANIMS.keys())
    cols = list(DIRS)
    cw, ch = FRAME * zoom, FRAME * zoom
    total = max(ANIMS[a]["frames"] for a in rows)
    sheets = {}
    for a in rows:
        for dd in cols:
            sheets[(a, dd)] = Image.open(os.path.join(OUT_DIR, sheet_name(a, dd) + ".png")).convert("RGBA")
    frames = []
    for step in range(total):
        img = Image.new("RGB", (len(cols) * cw, len(rows) * ch), bg)
        for r, a in enumerate(rows):
            spec = ANIMS[a]
            for c, dd in enumerate(cols):
                i = step % spec["frames"] if spec["loop"] else min(step, spec["frames"] - 1)
                tile = sheets[(a, dd)].crop((i * FRAME, 0, (i + 1) * FRAME, FRAME)).resize((cw, ch), Image.NEAREST)
                img.paste(tile, (c * cw, r * ch), tile)
        frames.append(img.convert("P", palette=Image.ADAPTIVE, colors=220))
    frames[0].save(path, save_all=True, append_images=frames[1:], duration=int(1000 / fps),
                   loop=0, optimize=True, disposal=2)


HTML = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>Military soldier sprite sheets - Dezhban</title>
<style>
  :root { color-scheme: dark; }
  body { margin:0; background:#1e221c; color:#dfe6d6; font-family:system-ui,sans-serif; }
  header { padding:12px 18px; background:#272c23; border-bottom:1px solid #3a4133;
           display:flex; gap:16px; align-items:center; flex-wrap:wrap; position:sticky; top:0; z-index:2; }
  h1 { font-size:15px; margin:0; font-weight:600; }
  label { font-size:13px; display:flex; gap:6px; align-items:center; }
  table { border-collapse:separate; border-spacing:6px; }
  th { color:#e6eedd; font-weight:600; font-size:12px; }
  th.row { text-align:left; padding-right:10px; white-space:nowrap; }
  th.row span { display:block; color:#8e9c82; font-weight:400; }
  td.cell { background:#2b3026; border-radius:6px; padding:0; }
  canvas { display:block; image-rendering:pixelated; }
  main { padding:8px 18px 40px; overflow:auto; }
  .hint { font-size:12px; color:#8e9c82; }
  .bg { display:flex; gap:8px; align-items:center; }
</style>
</head>
<body>
<header>
  <h1>Military soldier &mdash; 64&times;64 &mdash; 8-frame horizontal sheets</h1>
  <label>speed <input id="speed" type="range" min="2" max="24" value="10"><span id="fps">10</span> fps</label>
  <label>zoom <input id="zoom" type="range" min="1" max="6" value="3"><span id="zl">3</span>&times;</label>
  <label><input id="play" type="checkbox" checked> play</label>
  <label class="bg">bg
    <select id="bgsel">
      <option value="#2b3026">dark</option>
      <option value="#6f7a5e">grass</option>
      <option value="#c9b98a">sand</option>
      <option value="#ffffff">white</option>
      <option value="#000000">black</option>
    </select>
  </label>
  <span class="hint">Last frame of every looping sheet is an exact copy of frame 1.</span>
</header>
<main><table id="grid"></table></main>
<script>
const DIRS = ["front","back","left","right"];
const ANIMS = [["idle",8,6],["walk",8,10],["run",8,14],["attack",8,12],["death",6,8]];
const LOOPING = { idle:true, walk:true, run:true, attack:true, death:false };
const imgs = {}, canv = [];
let t0 = performance.now(), frame = 0, playing = true, speed = 10, zoom = 3;

function loadSheets() {
  const jobs = [];
  for (const [a] of ANIMS) for (const d of DIRS) {
    jobs.push(new Promise(res => {
      const im = new Image();
      im.onload = () => { imgs[a + "_" + d] = im; res(); };
      im.onerror = () => res();
      im.src = "soldier_" + a + "_" + d + ".png";
    }));
  }
  return Promise.all(jobs);
}

function buildGrid() {
  const table = document.getElementById("grid");
  let html = "<tr><th></th>" + DIRS.map(d => "<th>" + d + "</th>").join("") + "</tr>";
  for (const [a, n, f] of ANIMS) {
    html += "<tr><th class='row'>" + a + "<span>" + n + " frames &middot; " + f + " fps</span></th>";
    for (const d of DIRS) {
      html += "<td class='cell'><canvas data-a='" + a + "' data-d='" + d + "'></canvas></td>";
    }
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
    c.width = 64; c.height = 64;
    c.style.width = (64 * zoom) + "px";
    c.style.height = (64 * zoom) + "px";
  }
}

function tick(now) {
  if (playing && now - t0 > 1000 / speed) { t0 = now; frame++; }
  for (const [c, ctx] of canv) {
    const a = c.dataset.a, d = c.dataset.d;
    const n = ANIMS.find(x => x[0] === a)[1];
    const i = LOOPING[a] ? frame % n : Math.min(frame, n - 1);
    const im = imgs[a + "_" + d];
    if (!im) continue;
    ctx.clearRect(0, 0, 64, 64);
    ctx.drawImage(im, i * 64, 0, 64, 64, 0, 0, 64, 64);
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


def build(verbose=True):
    os.makedirs(OUT_DIR, exist_ok=True)
    pack = {
        "name": "military_soldier",
        "unit": "Military soldier (top-down, high-angle bird's eye)",
        "style": "semi-realistic military pixel art",
        "frameSize": {"w": FRAME, "h": FRAME},
        "background": "transparent",
        "pivot": {"x": 0.5, "y": round(GROUND_Y / FRAME, 4),
                  "note": "ground contact point; frame centre works too for TD placement"},
        "directions": list(DIRS),
        "animations": {},
        "sheets": [],
    }
    all_entries = []
    for anim, spec in ANIMS.items():
        pack["animations"][anim] = {
            "label": spec["label"],
            "frames": spec["frames"],
            "fps": spec["fps"],
            "loop": spec["loop"],
            "uniqueFrames": spec["unique"],
            "loopNote": ("frame %d is an exact copy of frame 1 for seamless looping"
                         % spec["frames"]) if spec["loop"] else "non looping",
        }
        for direction in DIRS:
            name = sheet_name(anim, direction)
            images = render_frames(anim, direction)
            build_sheet(images).save(os.path.join(OUT_DIR, name + ".png"))
            dump_json(os.path.join(OUT_DIR, name + ".json"),
                      tp_json(name, spec["frames"], spec["label"], spec["fps"], spec["loop"]))
            pack["sheets"].append({
                "file": name + ".png", "atlas": name + ".json",
                "animation": anim, "direction": direction,
                "frames": spec["frames"], "fps": spec["fps"], "loop": spec["loop"],
                "size": {"w": FRAME * spec["frames"], "h": FRAME},
            })
            for i, im in enumerate(images):
                all_entries.append(("%s_%02d" % (name, i), im))
            if verbose:
                print("  %s.png  %dx%d" % (name, FRAME * spec["frames"], FRAME))

    cols = 8
    rows = math.ceil(len(all_entries) / cols)
    atlas = Image.new("RGBA", (cols * FRAME, rows * FRAME), (0, 0, 0, 0))
    frames = {}
    for idx, (key, im) in enumerate(all_entries):
        cx, cy = idx % cols, idx // cols
        atlas.paste(im, (cx * FRAME, cy * FRAME))
        frames[key] = {
            "frame": {"x": cx * FRAME, "y": cy * FRAME, "w": FRAME, "h": FRAME},
            "rotated": False, "trimmed": False,
            "spriteSourceSize": {"x": 0, "y": 0, "w": FRAME, "h": FRAME},
            "sourceSize": {"w": FRAME, "h": FRAME},
            "pivot": {"x": 0.5, "y": round(GROUND_Y / FRAME, 4)},
        }
    atlas.save(os.path.join(OUT_DIR, "soldier_all_atlas.png"))
    dump_json(os.path.join(OUT_DIR, "soldier_all_atlas.json"), {
        "frames": frames,
        "meta": {"app": "Dezhban military-soldier sprite generator", "version": "1.0",
                 "image": "soldier_all_atlas.png", "format": "RGBA8888",
                 "size": {"w": cols * FRAME, "h": rows * FRAME}, "scale": "1"},
    })
    pack["atlas"] = {"file": "soldier_all_atlas.png", "atlas": "soldier_all_atlas.json",
                     "frames": len(all_entries), "size": {"w": cols * FRAME, "h": rows * FRAME}}
    dump_json(os.path.join(OUT_DIR, "soldier_pack.json"), pack)

    prev = os.path.join(OUT_DIR, "preview")
    os.makedirs(prev, exist_ok=True)
    contact_sheet().save(os.path.join(prev, "contact_sheet.png"))
    preview_gif(os.path.join(prev, "animations.gif"))
    write_text(os.path.join(OUT_DIR, "index.html"), HTML)
    write_text(os.path.join(prev, "README.txt"),
               "contact_sheet.png  همهٔ شیت‌ها یک‌جا\n"
               "animations.gif     پیش‌نمایش متحرک\n"
               "player:  ../index.html\n")
    write_text(os.path.join(OUT_DIR, ".gdignore"), "")
    if verbose:
        print("فهرست و پیش‌نمایش‌ها نوشته شد: %s" % OUT_DIR)
    return pack


# ------------------------------------------------------------------ خط فرمان


def main(argv=None):
    ap = argparse.ArgumentParser(description="مولد شیت اسپرایت سرباز نظامی")
    ap.add_argument("--check", action="store_true", help="فقط بررسی بستهٔ موجود")
    ap.add_argument("--debug", nargs=3, metavar=("ANIM", "DIR", "FRAME"),
                    help="رسم یک فریم با بزرگ‌نمایی برای بازبینی")
    ap.add_argument("--zoom", type=int, default=8)
    ap.add_argument("--out", default=None, help="مسیر تصویر فریم آزمایشی")
    args = ap.parse_args(argv)

    if args.check:
        problems = check_pack()
        for p in problems:
            print("مشکل:", p)
        print("بررسی تمام شد؛ بدون مشکل." if not problems else "%d مشکل پیدا شد." % len(problems))
        return 1 if problems else 0

    if args.debug:
        anim, direction, index = args.debug[0], args.debug[1], int(args.debug[2])
        p = frames_for(anim, direction)[index]
        img = draw_soldier(p).to_image().resize((FRAME * args.zoom, FRAME * args.zoom), Image.NEAREST)
        path = args.out or "/tmp/soldier_debug.png"
        img.save(path)
        print(path, img.size)
        return 0

    print("ساخت اسپرایت سرباز نظامی...")
    build()
    problems = check_pack()
    for p in problems:
        print("مشکل:", p)
    print("پایان. %s" % ("بدون مشکل." if not problems else "%d مشکل." % len(problems)))
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
