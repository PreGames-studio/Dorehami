#!/usr/bin/env python3
"""ساخت اسپرایت‌شیت برج‌های دفاعی با نمای ایزومتریک (۱۲۸×۱۲۸، پس‌زمینهٔ شفاف).

هر برج دو بخش دارد:

* **پایهٔ ثابت** — سکوی هشت‌ضلعی زرهی با سکو، نوار خطر، پیچ و دکل ارتباطی.
  پیکسل‌های پایه در همهٔ فریم‌ها یکسان است، پس هرگز تکان نمی‌خورد.
* **سرِ گردان** — درام زرهی، گیربکس، لوله/سلاح و جعبهٔ مهمات که حول محور پایه
  می‌چرخد (هشت جهت) و در شلیک پس‌نشینی و شعلهٔ دهانه دارد.

قرارداد جهت‌ها: ستون‌های شیت = هشت جهت، ساعت‌گرد روی صفحه:
`0=شرق، 1=جنوب‌شرق، 2=جنوب (رو به دوربین)، 3=جنوب‌غرب، 4=غرب، 5=شمال‌غرب،
6=شمال، 7=شمال‌شرق`.

    python3 tools/generate_iso_tower_sprites.py
    python3 tools/generate_iso_tower_sprites.py --only machinegun --no-gif

خروجی در `assets/turrets_iso/`: شیت هر برج/حرکت، `atlas_towers_iso.png` و
`.json`، `manifest.json` و پیش‌نمایش‌های PNG/GIF.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys

try:
    from PIL import Image, ImageDraw, ImageFilter
except ImportError:  # pragma: no cover - ابزار توسعه
    print("Pillow لازم است:  pip install pillow", file=sys.stderr)
    raise

# ------------------------------------------------------------------ ثابت‌ها

SS = 4                      # ابرنمونه‌برداری برای لبه‌های تمیز
FRAME = 128                 # اندازهٔ هر فریم
CX = 64.0                   # مرکز افقی کادر
GROUND = 97.0               # خط زمین (صفحه) برای ارتفاع و عمق صفر
DEPTH = 0.5                 # فشردگی عمق: دایرهٔ زمین ← بیضی ۲:۱ (نمای ایزومتریک)
LIGHT_X, LIGHT_Z = -0.62, 0.78   # نور از بالا-چپ-جلو

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(os.path.dirname(HERE), "assets", "turrets_iso")
PREVIEW_DIR = os.path.join(OUT_DIR, "previews")

DIRECTIONS = 8
ACTIONS = (("idle", 4), ("aim", 2), ("fire", 4))
ACTION_FPS = {"idle": 10, "aim": 12, "fire": 14}

INK = (27, 25, 31, 255)

# پالت نظامی-علمی تخیلی: سبز ارتشی، آبی نفتی، خاکستری تیره + نارنجی/قرمز درخشان
OLIVE_L = (131, 148, 96, 255)
OLIVE = (91, 107, 65, 255)
OLIVE_D = (56, 67, 42, 255)
NAVY_L = (70, 112, 138, 255)
NAVY = (40, 73, 95, 255)
NAVY_D = (22, 43, 58, 255)
STEEL_L = (180, 189, 196, 255)
STEEL = (133, 143, 152, 255)
STEEL_D = (88, 97, 106, 255)
IRON = (70, 76, 84, 255)
CONCRETE_L = (150, 150, 137, 255)
CONCRETE = (116, 117, 107, 255)
CONCRETE_D = (78, 79, 73, 255)
ORANGE = (255, 138, 43, 255)
AMBER = (255, 194, 74, 255)
RED = (216, 69, 47, 255)
GREEN = (108, 255, 138, 255)
GREEN_PALE = (206, 255, 216, 255)
CYAN = (110, 226, 255, 255)
WHITE = (255, 252, 236, 255)
SMOKE = (200, 203, 206, 255)


# ------------------------------------------------------------------ رنگ


def lerp(a, b, k):
    k = 0.0 if k < 0 else (1.0 if k > 1 else k)
    return tuple(int(round(a[i] + (b[i] - a[i]) * k)) for i in range(len(a)))


def shade(c, k):
    """k>0 روشن‌تر، k<0 تیره‌تر (آلفا حفظ می‌شود)."""
    if k >= 0:
        return lerp(c, (255, 255, 255, c[3]), k)
    return lerp(c, (12, 12, 16, c[3]), -k)


def alpha(c, a):
    return (c[0], c[1], c[2], int(max(0, min(255, a))))


# ------------------------------------------------------------------ هندسه


def proj(x, y, z):
    """(x راست، y بالا، z به سمت دوربین) ← مختصات صفحه."""
    return (CX + x, GROUND - y + z * DEPTH)


def ang_dir(a):
    return (math.cos(a), math.sin(a))


def ngon(cx, cy, rx, ry, n=8, phase=math.pi / 8):
    return [(cx + rx * math.cos(phase + 2 * math.pi * i / n),
             cy + ry * math.sin(phase + 2 * math.pi * i / n)) for i in range(n)]


# ------------------------------------------------------------------ قلم


class Pen:
    """قلم برداری با پرکردن گرادیانی و خط دور ضخیم روی بوم ابرنمونه."""

    def __init__(self, size=FRAME, ss=SS):
        self.size, self.s = size, ss
        self.img = Image.new("RGBA", (size * ss, size * ss), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    # تبدیل
    def _p(self, pts):
        return [(x * self.s, y * self.s) for x, y in pts]

    def _box(self, b):
        return [b[0] * self.s, b[1] * self.s, b[2] * self.s, b[3] * self.s]

    def _w(self, w):
        return max(1, int(round(w * self.s)))

    # پرکردن گرادیانی از یک ماسک
    def _grad(self, mask, stops, box, horizontal=False):
        x0, y0, x1, y1 = [int(round(v * self.s)) for v in box]
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(self.img.width, x1), min(self.img.height, y1)
        if x1 <= x0 or y1 <= y0:
            return
        w, h = x1 - x0, y1 - y0
        n = max(2, (w if horizontal else h))
        strip = Image.new("RGBA", (w, h))
        sd = ImageDraw.Draw(strip)
        for i in range(n):
            k = i / (n - 1)
            col = stops[-1][1]
            for j in range(len(stops) - 1):
                if stops[j][0] <= k <= stops[j + 1][0]:
                    span = max(1e-6, stops[j + 1][0] - stops[j][0])
                    col = lerp(stops[j][1], stops[j + 1][1], (k - stops[j][0]) / span)
                    break
            if horizontal:
                sd.line([(i, 0), (i, h)], fill=col)
            else:
                sd.line([(0, i), (w, i)], fill=col)
        self.img.paste(strip, (x0, y0), mask.crop((x0, y0, x1, y1)))

    # خط دور
    def ink(self, pts, w=3.0, col=INK):
        if len(pts) < 2:
            return
        self.d.line(self._p(list(pts) + [pts[0]]), fill=col, width=self._w(w), joint="curve")

    def ink_open(self, pts, w=3.0, col=INK):
        if len(pts) < 2:
            return
        self.d.line(self._p(pts), fill=col, width=self._w(w), joint="curve")

    # شکل‌ها
    def poly(self, pts, stops, outline=INK, w=3.0):
        if not pts:
            return
        if isinstance(stops, tuple):
            stops = [(0.0, stops), (1.0, stops)]
        xs = [p[0] for p in pts]
        ys = [p[1] for p in pts]
        mask = Image.new("L", self.img.size, 0)
        ImageDraw.Draw(mask).polygon(self._p(pts), fill=255)
        self._grad(mask, stops, (min(xs), min(ys), max(xs), max(ys)))
        if outline is not None:
            self.ink(pts, w, outline)

    def ellipse(self, box, stops, outline=INK, w=3.0, ry=None):
        if stops is not None:
            if isinstance(stops, tuple):
                stops = [(0.0, stops), (1.0, stops)]
            mask = Image.new("L", self.img.size, 0)
            ImageDraw.Draw(mask).ellipse(self._box(box), fill=255)
            self._grad(mask, stops, box)
        if outline is not None:
            self.d.ellipse(self._box(box), outline=outline, width=self._w(w))

    def circle(self, cx, cy, r, stops, outline=INK, w=3.0, ry=None):
        ry = r if ry is None else ry
        self.ellipse([cx - r, cy - ry, cx + r, cy + ry], stops, outline, w)

    def rect(self, box, stops, radius=0, outline=INK, w=3.0):
        if isinstance(stops, tuple):
            stops = [(0.0, stops), (1.0, stops)]
        if radius <= 0:
            self.poly([(box[0], box[1]), (box[2], box[1]), (box[2], box[3]), (box[0], box[3])],
                      stops, outline, w)
            return
        mask = Image.new("L", self.img.size, 0)
        ImageDraw.Draw(mask).rounded_rectangle(self._box(box), radius=radius * self.s, fill=255)
        self._grad(mask, stops, box)
        if outline is not None:
            x0, y0, x1, y1 = box
            r = min(radius, (x1 - x0) / 2, (y1 - y0) / 2)
            pts = []
            for (ccx, ccy), a0, a1 in (((x1 - r, y0 + r), -90, 0), ((x1 - r, y1 - r), 0, 90),
                                       ((x0 + r, y1 - r), 90, 180), ((x0 + r, y0 + r), 180, 270)):
                for i in range(7):
                    ang = math.radians(a0 + (a1 - a0) * i / 6)
                    pts.append((ccx + r * math.cos(ang), ccy + r * math.sin(ang)))
            self.ink(pts, w, outline)

    def line(self, pts, col, w):
        self.d.line(self._p(pts), fill=col, width=self._w(w), joint="curve")

    def dot(self, cx, cy, r, col, outline=None, w=1.0, ry=None):
        ry = r if ry is None else ry
        box = self._box([cx - r, cy - ry, cx + r, cy + ry])
        if isinstance(col, list):
            mask = Image.new("L", self.img.size, 0)
            ImageDraw.Draw(mask).ellipse(box, fill=255)
            self._grad(mask, col, [cx - r, cy - ry, cx + r, cy + ry])
        elif col is not None:
            self.d.ellipse(box, fill=col)
        if outline is not None:
            self.d.ellipse(box, outline=outline, width=self._w(w))

    def out(self):
        return self.img.resize((self.size, self.size), Image.LANCZOS)


# ------------------------------------------------------------------ پایه


def plinth(pen, cy, rx, ry, height, top_light, top_dark, side_light, side_dark,
           phase=math.pi / 8, n=8, w=3.0):
    """سکوی هشت‌ضلعی: وجه‌های رو به دوربین با نور جهت‌دار، بعد سطح رویی."""
    top = ngon(CX, cy, rx, ry, n, phase)
    faces = []
    for i in range(n):
        a, b = top[i], top[(i + 1) % n]
        mx = ((a[0] + b[0]) / 2 - CX) / max(rx, 1e-6)
        mz = ((a[1] + b[1]) / 2 - cy) / max(ry, 1e-6)
        if mz <= 0.02:
            continue
        lit = max(0.0, min(1.0, 0.5 + 0.5 * (mx * LIGHT_X + mz * LIGHT_Z)))
        col = lerp(side_dark, side_light, lit)
        quad = [(a[0], a[1]), (b[0], b[1]), (b[0], b[1] + height), (a[0], a[1] + height)]
        pen.poly(quad, [(0.0, shade(col, 0.14)), (1.0, shade(col, -0.16))], INK, 2.4)
        # درز عمودی بین وجه‌ها
        pen.line([(a[0], a[1] + 1.5), (a[0], a[1] + height)], alpha(INK, 150), 1.0)
        faces.append((mz, a, b))
    pen.poly(top, [(0.0, top_light), (1.0, top_dark)], INK, w)
    return top


def draw_base(pen, accent):
    """پایهٔ ثابت: سایه، سکوی بتنی، صفحهٔ زرهی، حلقهٔ گردان، دکل و چراغ‌ها."""
    # سایهٔ نرم زیر پایه
    sh = Image.new("RGBA", pen.img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).ellipse(pen._box([CX - 44, 106, CX + 44, 122]), fill=(10, 12, 11, 125))
    pen.img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(4.5 * SS / 2)))

    # سکوی پایین (بتن)
    plinth(pen, 86.0, 40.0, 20.0, 11.0, CONCRETE_L, shade(CONCRETE, -0.24),
           shade(CONCRETE, 0.20), shade(CONCRETE_D, -0.10))
    # صفحهٔ زرهی (فولاد)
    plinth(pen, 80.0, 34.5, 17.2, 7.0, shade(STEEL_D, 0.22), shade(STEEL_D, -0.28),
           STEEL, shade(STEEL_D, -0.42))

    # لبهٔ روشن صفحهٔ زرهی
    rim = [(CX - 27.0, 79.2), (CX - 14.0, 84.4), (CX + 13.0, 84.6), (CX + 26.0, 79.6)]
    pen.ink_open(rim, 1.1, alpha(shade(STEEL_L, 0.15), 190))

    # نوار خطر نارنجی روی وجه جلویی
    y0, y1 = 103.0, 107.2
    for i in range(8):
        xa = CX - 23 + i * 6.0
        pen.poly([(xa, y1), (xa + 3.2, y1), (xa + 5.0, y0), (xa + 1.8, y0)],
                 [(0, AMBER), (1, ORANGE)], None, 0)
    pen.line([(CX - 23, y1 + 0.8), (CX + 23, y1 + 0.8)], alpha(INK, 170), 1.0)
    pen.line([(CX - 23, y0 - 0.8), (CX + 23, y0 - 0.8)], alpha(INK, 170), 1.0)

    # حلقهٔ گردان
    ring_y = 75.0
    pen.circle(CX, ring_y, 26.0, [(0, shade(STEEL_D, -0.12)), (1, shade(STEEL_D, -0.34))], INK, 3.0, ry=12.8)
    pen.circle(CX, ring_y - 1.6, 23.4, [(0, shade(STEEL, 0.18)), (1, shade(STEEL, -0.26))], INK, 2.4, ry=11.6)
    pen.circle(CX, ring_y - 2.6, 19.8, [(0, shade(IRON, 0.02)), (1, shade(IRON, -0.28))], INK, 2.2, ry=9.8)
    for i in range(10):
        a = math.pi / 10 + 2 * math.pi * i / 10
        if math.sin(a) < -0.15:
            continue
        bx = CX + 22.2 * math.cos(a)
        by = ring_y - 1.6 + 11.0 * math.sin(a)
        pen.dot(bx, by, 1.6, shade(STEEL_L, 0.05), alpha(INK, 230), 0.6, ry=0.95)
        pen.dot(bx - 0.45, by - 0.45, 0.5, (255, 255, 255, 170), None)

    # سایهٔ نرم سر روی حلقه (ژرفانمایی)
    sh2 = Image.new("RGBA", pen.img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh2).ellipse(pen._box([CX - 23, ring_y - 11.5, CX + 23, ring_y + 11.0]),
                                fill=(10, 12, 14, 120))
    pen.img.alpha_composite(sh2.filter(ImageFilter.GaussianBlur(2.4 * SS / 2)))

    # قفل‌های زرهی کنار حلقه
    for sgn in (-1, 1):
        bx = CX + sgn * 28.0
        pen.rect([bx - 4.0, 72.5, bx + 4.0, 80.5], [(0, shade(OLIVE, 0.20)), (1, OLIVE_D)],
                 radius=1.8, w=2.0)
        pen.dot(bx, 75.5, 1.4, shade(STEEL_L, 0.08), INK, 0.5, ry=0.9)

    # دکل کوتاه ثابت روی لبهٔ پشتی-چپ (نشان می‌دهد پایه نمی‌چرخد)
    ax, ay = CX - 26.5, 70.0
    tx, ty = ax - 2.6, ay - 15.0
    pen.line([(ax, ay), (tx, ty)], INK, 3.4)
    pen.line([(ax, ay), (tx, ty)], shade(STEEL_D, 0.12), 1.7)
    pen.line([(ax + 0.6, ay - 6), (ax + 4.2, ay - 9)], shade(STEEL_D, 0.02), 1.4)
    pen.dot(tx, ty, 2.9, shade(IRON, 0.05), INK, 1.2, ry=2.5)
    pen.dot(tx, ty, 1.9, alpha(accent, 240), None, ry=1.6)
    pen.dot(tx - 0.6, ty - 0.7, 0.6, (255, 255, 255, 220), None, ry=0.5)
    # بشقاب کوچک روی دکل
    pen.ellipse([tx - 7.6, ty - 4.2, tx - 1.6, ty + 0.6],
                [(0, shade(STEEL, 0.22)), (1, shade(STEEL_D, -0.12))], INK, 1.4)

    # چراغ‌های وضعیت
    for dx, col in ((-31.0, RED), (31.0, accent)):
        pen.dot(CX + dx, 92.0, 2.4, alpha(col, 235), INK, 0.8, ry=1.0)
        pen.dot(CX + dx - 0.5, 91.5, 0.7, (255, 255, 255, 200), None, ry=0.5)

    # ریزخط‌های سایش
    pen.line([(CX - 34, 98.0), (CX - 27, 99.6)], (58, 60, 56, 150), 0.9)
    pen.line([(CX + 17, 99.2), (CX + 24, 97.6)], (58, 60, 56, 150), 0.9)


# ------------------------------------------------------------------ سرِ گردان


def rot2(lx, lz, a):
    """چرخش مختصات محلی سر به زاویهٔ هدف (ساعت‌گرد روی صفحه)."""
    ca, sa = math.cos(a), math.sin(a)
    return (-lx * sa + lz * ca, lx * ca + lz * sa)


def blade_bands(pen, a, y, d0, d1, half_w, col=OLIVE_D):
    """نوارهای زیتونی دور لوله (عمود بر محور، هم‌جهت با هدف)."""
    dx, dz = ang_dir(a)
    px, pz = -dz, dx
    for k in (0.0, 1.0):
        d = d0 + (d1 - d0) * k
        cxr, czr = dx * d, dz * d
        p0 = proj(cxr + px * half_w, y, czr + pz * half_w)
        p1 = proj(cxr - px * half_w, y, czr - pz * half_w)
        pen.line([p0, p1], INK, 6.2)
        pen.line([p0, p1], col, 3.8)


def box3d(pen, a, lx, y, lz, base, w=2.6, top_boost=0.20, bottom_boost=-0.34):
    """جعبهٔ جهت‌دار: فقط وجه‌های رو به دوربین + سطح رویی، با نور جهت‌دار."""
    x0, x1 = lx
    y0, y1 = y
    z0, z1 = lz
    pts = {}

    def corner(i, j, k):
        wx, wz = rot2(lx[i], lz[j], a)
        return proj(wx, y[k], wz)

    for i in range(2):
        for j in range(2):
            for k in range(2):
                pts[(i, j, k)] = corner(i, j, k)

    faces = [
        ("top", [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)], None),
        ("side", [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)], (1, 0)),
        ("side", [(0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1)], (-1, 0)),
        ("side", [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], (0, 1)),
        ("side", [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)], (0, -1)),
    ]
    vertical = []
    for kind, idx, normal in faces:
        if kind == "top":
            continue
        nx, nz = rot2(normal[0], normal[1], a)
        if nz < 0.07:
            continue
        lit = max(0.0, min(1.0, 0.5 + 0.5 * (nx * LIGHT_X + nz * LIGHT_Z)))
        col = lerp(shade(base, bottom_boost), shade(base, top_boost), lit)
        quad = [pts[i] for i in idx]
        avgz = sum(rot2(lx[i[0]], lz[i[1]], a)[1] for i in idx) / 4
        vertical.append((avgz, quad, col))
    for _, quad, col in sorted(vertical, key=lambda f: f[0]):
        pen.poly(quad, [(0.0, shade(col, 0.10)), (1.0, shade(col, -0.12))], INK, w)
    top_pts = [pts[i] for i in faces[0][1]]
    pen.poly(top_pts, [(0.0, shade(base, top_boost + 0.12)),
                       (1.0, shade(base, top_boost - 0.16))], INK, w)


def drum(pen, accent, top_y=49.0, bot_y=74.0, r=20.0, ry=10.0, sides=10,
         body=OLIVE, light=OLIVE_L, dark=OLIVE_D, plate=STEEL_D, glow=True):
    """بدنهٔ چندضلعی سر گردان با پخ بالایی، دریچه و دریچه‌های خنک‌کننده."""
    rz = r * 0.71
    bevel_y = top_y + 3.2
    top = ngon(CX, bevel_y, rz, rz * DEPTH, sides, math.pi / sides)
    bevel = ngon(CX, top_y, r * 0.93, ry * 0.93, sides, math.pi / sides)
    bot = ngon(CX, bot_y, r, ry, sides, math.pi / sides, )
    # وجه‌های پخ
    for i in range(sides):
        a0, b0, b1, a1 = top[i], top[(i + 1) % sides], bevel[(i + 1) % sides], bevel[i]
        mz = ((b1[1] + a1[1]) / 2 - top_y) / max(ry, 1e-6)
        if mz < 0.02:
            continue
        col = shade(plate, 0.16 if mz > 0.55 else -0.16)
        pen.poly([a0, b0, b1, a1], [(0, shade(col, 0.18)), (1, shade(col, -0.10))], INK, 2.2)
    # وجه‌های بدنه
    faces = []
    for i in range(sides):
        a0, b0 = bevel[i], bevel[(i + 1) % sides]
        a1, b1 = bot[i], bot[(i + 1) % sides]
        mx = ((a0[0] + b0[0]) / 2 - CX) / max(r, 1e-6)
        mz = ((a0[1] + b0[1]) / 2 - top_y) / max(ry, 1e-6)
        if mz <= 0.02:
            continue
        lit = max(0.0, min(1.0, 0.5 + 0.5 * (mx * LIGHT_X + mz * LIGHT_Z)))
        col = lerp(shade(dark, 0.02), shade(light, 0.12), lit)
        faces.append((mz, [a0, b0, b1, a1], col, mz))
    for _, quad, col, mz in sorted(faces, key=lambda f: f[0]):
        pen.poly(quad, [(0.0, shade(col, 0.16)), (0.55, col), (1.0, shade(col, -0.18))], INK, 2.6)
    # سطح رویی
    pen.poly(top, [(0.0, shade(plate, 0.26)), (1.0, shade(plate, -0.12))], INK, 2.8)
    # لبهٔ روشن سمت نور
    idx = [i for i in range(sides) if math.cos(math.pi / sides + 2 * math.pi * i / sides) < -0.45]
    if len(idx) >= 2:
        pen.ink_open([top[i] for i in idx], 1.1, shade(plate, 0.52))
    # صفحهٔ داخلی رویی
    inner = ngon(CX, bevel_y + 0.6, rz * 0.72, rz * 0.72 * DEPTH, sides, math.pi / sides)
    pen.poly(inner, [(0.0, shade(plate, -0.12)), (1.0, shade(plate, -0.30))], alpha(INK, 200), 1.6)
    # دریچهٔ مرکزی + دستگیره
    pen.ellipse([CX - rz * 0.34, bevel_y - rz * 0.34 * DEPTH, CX + rz * 0.34,
                 bevel_y + rz * 0.34 * DEPTH],
                [(0, shade(plate, 0.16)), (1, shade(plate, -0.06))], INK, 1.8)
    pen.line([(CX - 2.2, bevel_y - 0.4), (CX + 2.6, bevel_y - 0.4)], alpha(INK, 180), 1.3)
    # دریچه‌های خنک‌کننده روی سطح رویی
    for sgn in (-1, 1):
        for k in range(3):
            vx = CX + sgn * (rz * 0.56)
            vy = bevel_y + (k - 1) * rz * 0.17 * DEPTH
            pen.line([(vx - rz * 0.13, vy), (vx + rz * 0.13, vy)], alpha(INK, 165), 1.4)
    # نوار نوری دور بدنه
    if glow:
        ring = ngon(CX, top_y + 8.0, r * 0.995, ry * 0.995, sides, math.pi / sides)
        for i in range(sides):
            a0, b0 = ring[i], ring[(i + 1) % sides]
            mz = ((a0[1] + b0[1]) / 2 - (top_y + 8.0)) / max(ry, 1e-6)
            if mz < 0.18:
                continue
            pen.line([a0, b0], alpha(accent, 205 if mz > 0.5 else 150), 1.5)
    # پیچ‌های بدنه
    for i in range(sides):
        mx = (bot[i][0] - CX) / max(r, 1e-6)
        mz = (bot[i][1] - bot_y) / max(ry, 1e-6)
        if mz < 0.25:
            continue
        pen.dot(bot[i][0] * 0.975 + CX * 0.025, bot[i][1] * 0.975 + (bot_y - 4) * 0.025,
                1.5, shade(light, 0.16), alpha(INK, 220), 0.6, ry=0.9)


def side_pods(pen, cy=68.5, accent=ORANGE):
    """زره‌های کناری سر گردان (متقارن، پس با چرخش ظاهرشان عوض نمی‌شود)."""
    for sgn in (-1, 1):
        x = CX + sgn * 23.0
        pen.rect([x - 4.4, cy - 8.0, x + 4.4, cy + 6.0],
                 [(0, shade(OLIVE, 0.18)), (1, OLIVE_D)], radius=2.6, w=2.4)
        pen.rect([x - 2.8, cy - 5.2, x + 2.8, cy + 1.0],
                 [(0, shade(STEEL, 0.1)), (1, shade(STEEL_D, -0.1))], radius=1.2, w=1.2)
        pen.dot(x, cy + 3.4, 1.3, alpha(accent, 230), alpha(INK, 210), 0.6, ry=0.9)


def barrel(pen, a, y0, muzzle, half_w, y1=None, recoil=0.0, lateral=0.0,
           body=STEEL, hi=STEEL_L, lo=STEEL_D, start=11.0, bands=3,
           muzzle_brake=True, heat=0.0):
    """لولهٔ سلاح در راستای هدف، با کمی فراز؛ بدنه، بندهای خنک‌کننده و دهانه."""
    y1 = y0 + (y1 - y0 if y1 is not None else 0.0)
    dx, dz = ang_dir(a)
    px, pz = -dz, dx

    def at(dist, y, lat=0.0):
        return proj(dx * dist + px * (lat + lateral), y, dz * dist + pz * (lat + lateral))

    s = at(start - recoil, y0)
    m = at(muzzle - recoil, y1)
    ex, ey = m[0] - s[0], m[1] - s[1]
    ln = math.hypot(ex, ey) or 1.0
    ex, ey = ex / ln, ey / ln
    nx, ny = -ey, ex
    lit = -nx * 0.55 - ny * 0.82

    def off(p, k):
        return (p[0] + nx * k, p[1] + ny * k)

    # خط دور و بدنهٔ استوانه‌ای
    pen.line([s, m], INK, half_w * 2 + 3.6)
    pen.dot(s[0], s[1], half_w + 1.8, INK, None)
    pen.dot(m[0], m[1], half_w + 1.8, INK, None)
    pen.line([s, m], body, half_w * 2)
    pen.dot(s[0], s[1], half_w, body, None)
    pen.dot(m[0], m[1], half_w, body, None)
    pen.line([off(s, lit * half_w * 0.42), off(m, lit * half_w * 0.42)],
             shade(hi, 0.10), half_w * 0.9)
    pen.line([off(s, -lit * half_w * 0.55), off(m, -lit * half_w * 0.55)],
             alpha(shade(lo, -0.20), 215), half_w * 0.72)
    for i in range(1, bands + 1):
        k = i / (bands + 1.0)
        bx, by = s[0] + ex * ln * k, s[1] + ey * ln * k
        pen.line([(bx + nx * half_w, by + ny * half_w), (bx - nx * half_w, by - ny * half_w)],
                 alpha(shade(lo, -0.30), 200), 1.3)
    if muzzle_brake:
        bx, by = m[0] - ex * 3.4, m[1] - ey * 3.4
        pen.line([(bx + nx * (half_w + 2.0), by + ny * (half_w + 2.0)),
                  (bx - nx * (half_w + 2.0), by - ny * (half_w + 2.0))], INK, 4.6)
        pen.line([(bx + nx * (half_w + 2.0), by + ny * (half_w + 2.0)),
                  (bx - nx * (half_w + 2.0), by - ny * (half_w + 2.0))], shade(lo, 0.18), 2.0)
    pen.dot(m[0], m[1], half_w * 0.98, shade(lo, -0.34), INK, 1.3, ry=half_w * 0.95)
    pen.dot(m[0], m[1], half_w * 0.55, (24, 24, 26, 255), None, ry=half_w * 0.55)
    if heat > 0:
        pen.dot(m[0] - ex * 7, m[1] - ey * 7, half_w * 1.2, alpha(ORANGE, int(95 * heat)), None,
                ry=half_w * 0.95)
    return (m[0] + ex * 1.6, m[1] + ey * 1.6), (ex, ey), (nx, ny)


# ------------------------------------------------------------------ برج‌ها

TOWERS = {
    "machinegun": dict(accent=ORANGE, name_fa="مسلسل سنگین", body=OLIVE),
    "laser": dict(accent=GREEN, name_fa="توپ لیزر", body=NAVY),
    "rocket": dict(accent=RED, name_fa="سکوی موشک", body=OLIVE),
    "cannon": dict(accent=AMBER, name_fa="توپ سنگین", body=STEEL_D),
}


def draw_sorted(parts):
    """قطعه‌ها را از دور به نزدیک می‌کشد (مرتب‌سازی نقاش)."""
    for _, fn in sorted(parts, key=lambda p: p[0]):
        fn()


def head_machinegun(pen, a, rec=0.0):
    s = math.sin(a)
    parts = [(-18 * s, lambda: box3d(pen, a, (-9, 9), (42, 53), (-22, -11), OLIVE_D)),
             (0.0, lambda: drum(pen, ORANGE, top_y=49.0, bot_y=74.5, r=20.0, ry=10.0,
                                body=OLIVE, light=OLIVE_L, dark=OLIVE_D, plate=STEEL_D)),
             (0.0, lambda: side_pods(pen, 64.0, ORANGE)),
             (13 * s, lambda: box3d(pen, a, (-9.5, 9.5), (46, 58), (3, 15), OLIVE)),
             (16 * s, lambda: (
                 box3d(pen, a, (-10.5, 10.5), (45, 56), (8, 17), STEEL_D, w=2.8),
                 box3d(pen, a, (-10.7, 10.7), (52.6, 53.4), (16.6, 17.4), AMBER, w=1.4)))]
    info = {}

    def belt():
        for k in range(4):
            t = k / 3.0
            p = proj(-13 + 9 * t, 40.0, -4 + 7 * t)
            pen.dot(p[0], p[1], 1.5, alpha(AMBER, 240), INK, 0.5, ry=1.2)

    parts.append((5 * s, belt))

    def guns():
        guns_out = []
        for lat in (-3.8, 3.8):
            guns_out.append(barrel(pen, a, 55.0, 41.0, 2.35, y1=64.0, lateral=lat, recoil=rec,
                                   start=12.0, body=STEEL_D, hi=STEEL_L, lo=IRON, bands=3))
        m0, ax, pp = guns_out[0]
        m1 = guns_out[1][0]
        info["m"] = (((m0[0] + m1[0]) / 2, (m0[1] + m1[1]) / 2), ax, pp)
        # چراغ وضعیت روی سپر
        rc = rot2(0, 17.4, a)
        p = proj(rc[0], 56.2, rc[1])
        pen.dot(p[0], p[1], 1.5, alpha(ORANGE, 245), alpha(INK, 220), 0.5, ry=1.1)

    parts.append((19 * s, guns))
    draw_sorted(parts)
    return info


def head_laser(pen, a, rec=0.0):
    s = math.sin(a)
    parts = [(-17 * s, lambda: box3d(pen, a, (-8.5, 8.5), (43, 54), (-22, -11), NAVY_D)),
             (0.0, lambda: drum(pen, GREEN, top_y=49.0, bot_y=74.5, r=20.0, ry=10.0,
                                body=NAVY, light=NAVY_L, dark=NAVY_D, plate=IRON)),
             (0.0, lambda: side_pods(pen, 64.0, GREEN)),
             (12 * s, lambda: box3d(pen, a, (-8.5, 8.5), (46, 58), (3, 15), NAVY)),
             (16 * s, lambda: (
                 box3d(pen, a, (-6.2, 6.2), (45, 57), (9, 18), IRON, w=2.8),
                 box3d(pen, a, (-6.4, 6.4), (56.4, 57.2), (17.6, 18.4), GREEN, w=1.4)))]
    info = {}

    def caps():
        # پره‌های خنک‌کنندهٔ سقف
        for k in (-1, 0, 1):
            r0 = rot2(k * 3.4, 6, a)
            r1 = rot2(k * 3.4, 3, a)
            c0 = proj(r0[0], 58.2, r0[1])
            c1 = proj(r1[0], 58.2, r1[1])
            pen.line([c0, c1], shade(STEEL_D, -0.05), 2.0)

    parts.append((5 * s, caps))

    def gun():
        info["m"] = barrel(pen, a, 53.5, 38.0, 4.4, y1=59.0, recoil=rec, start=13.0,
                           body=IRON, hi=STEEL_L, lo=(34, 38, 44, 255), bands=2,
                           muzzle_brake=False)
        mx, my = info["m"][0]
        pen.circle(mx, my, 6.6, [(0, shade(STEEL_D, 0.16)), (1, shade(IRON, -0.24))], INK, 2.0, ry=5.8)
        pen.circle(mx, my, 4.6, [(0, GREEN_PALE), (1, GREEN)], alpha(INK, 220), 1.5, ry=4.0)
        pen.dot(mx - 1.0, my - 1.2, 1.5, (255, 255, 255, 210), None, ry=1.3)
        # حلقهٔ انرژی روی لوله
        rc = rot2(0, 22.0, a)
        ex, ey = proj(rc[0], 55.4, rc[1])
        pen.ellipse([ex - 3.4, ey - 2.6, ex + 3.4, ey + 2.6], None, alpha(GREEN, 220), 1.5)

    def coils():
        for t in (0.34, 0.52, 0.70):
            d = 13 + 25 * t
            blade_bands(pen, a, 53.5 + 6.0 * t, d, d + 0.6, 5.0, col=alpha(GREEN, 235))

    parts.append((19 * s, coils))
    parts.append((21 * s, gun))
    draw_sorted(parts)
    return info


def head_rocket(pen, a, rec=0.0):
    s = math.sin(a)
    parts = [(-18 * s, lambda: box3d(pen, a, (-9, 9), (42, 53), (-23, -12), OLIVE_D)),
             (0.0, lambda: drum(pen, RED, top_y=49.0, bot_y=74.5, r=20.0, ry=10.0,
                                body=OLIVE, light=OLIVE_L, dark=OLIVE_D, plate=STEEL_D)),
             (0.0, lambda: side_pods(pen, 64.0, RED))]

    def pod():
        box3d(pen, a, (-11.5, 11.5), (43, 60), (-1, 16), OLIVE, w=2.8)
        # نوار خطر روی وجه جلویی (در راستای همان وجه)
        r0 = rot2(-9.5, 16.6, a)
        r1 = rot2(9.5, 16.6, a)
        ox0, oy0 = proj(r0[0], 56.0, r0[1])
        ox1, oy1 = proj(r1[0], 56.0, r1[1])
        for k in range(4):
            t = k / 3.0
            x = ox0 + (ox1 - ox0) * t
            y = oy0 + (oy1 - oy0) * t
            pen.line([(x, y), (x + (ox1 - ox0) * 0.16, y + (oy1 - oy0) * 0.16 - 3.4)],
                     alpha(AMBER, 225), 2.4)
        # لوله‌های موشک روی وجه جلویی
        for lx in (-5.6, 5.6):
            for y in (47.5, 54.0):
                rc = rot2(lx, 16.4, a)
                c = proj(rc[0], y, rc[1])
                w = max(1.7, 3.4 * abs(math.cos(a)))
                pen.dot(c[0], c[1], w + 0.7, alpha(INK, 225), None)
                pen.dot(c[0], c[1], w, (30, 30, 32, 255), None, ry=3.1)
                pen.dot(c[0], c[1] - 0.9, w * 0.55, alpha(ORANGE, 230), None, ry=1.5)
        # کلاهک موشک‌های بیرون‌آمده
        for lx in (-5.6, 5.6):
            for y in (47.5, 54.0):
                rb = rot2(lx, 16.0, a)
                base = proj(rb[0], y, rb[1])
                rt = rot2(lx, 25.5 - rec * 0.6, a)
                tip = proj(rt[0], y, rt[1])
                pen.line([base, tip], INK, 7.2)
                pen.line([base, tip], shade(STEEL_L, -0.02), 4.8)
                pen.dot(tip[0], tip[1], 4.1, [(0, shade(RED, 0.22)), (1, shade(RED, -0.40))],
                        INK, 1.7, ry=3.7)
                pen.dot(tip[0] - 0.8, tip[1] - 1.0, 1.15, (255, 226, 214, 200), None, ry=0.9)

    parts.append((19 * s, pod))

    def radar():
        rb = rot2(0, -7, a)
        b = proj(rb[0], 50.0, rb[1])
        t = proj(rb[0], 63.0, rb[1] - 1.0)
        pen.line([b, t], INK, 3.6)
        pen.line([b, t], shade(STEEL_D, 0.12), 1.7)
        pen.ellipse([t[0] - 7.0, t[1] - 4.6, t[0] + 7.0, t[1] + 2.4],
                    [(0, shade(STEEL_L, 0.10)), (1, STEEL_D)], INK, 1.8)
        pen.line([(t[0] - 5.6, t[1] - 0.6), (t[0] + 5.6, t[1] - 0.6)], alpha(INK, 140), 0.9)
        pen.dot(t[0], t[1] - 5.2, 1.4, alpha(RED, 240), None)

    parts.append((6 * s, radar))
    draw_sorted(parts)
    tube = []
    for lx in (-5.6, 5.6):
        rc = rot2(lx, 17.0, a)
        tube.append(proj(rc[0], 54.0, rc[1]))
    dx, dz = ang_dir(a)
    sx = math.hypot(dx, dz * DEPTH)
    mid = ((tube[0][0] + tube[1][0]) / 2, (tube[0][1] + tube[1][1]) / 2)
    return {"m": (mid, (dx / sx, dz * DEPTH / sx), (-dz / sx, dx * DEPTH / sx)),
            "muzzles": tube, "dual": True}


def head_cannon(pen, a, rec=0.0):
    """خمپاره‌انداز سنگین: لولهٔ کوتاه و کلفت با دهانهٔ رو به بالا-جلو."""
    s = math.sin(a)
    parts = [(-20 * s, lambda: box3d(pen, a, (-10, 10), (41, 52), (-24, -13), OLIVE_D)),
             (0.0, lambda: drum(pen, AMBER, top_y=48.0, bot_y=74.5, r=20.5, ry=10.2,
                                body=OLIVE_D, light=OLIVE, dark=(44, 52, 33, 255),
                                plate=STEEL_D)),
             (0.0, lambda: side_pods(pen, 64.0, AMBER))]
    info = {}

    def cradle():
        # گهوارهٔ لوله با جک‌های هیدرولیکی
        box3d(pen, a, (-10.5, 10.5), (47, 55), (1, 13), OLIVE, w=2.8)
        for sgn in (-1, 1):
            r0 = rot2(sgn * 11.5, 1, a)
            r1 = rot2(sgn * 11.5, 14, a)
            p0 = proj(r0[0], 48.0, r0[1])
            p1 = proj(r1[0], 53.5, r1[1])
            pen.line([p0, p1], INK, 4.6)
            pen.line([p0, p1], shade(STEEL, 0.1), 2.5)
        # پیچ گهواره
        r = rot2(8.6, 13.2, a)
        c = proj(r[0], 54.6, r[1])
        pen.dot(c[0], c[1], 1.5, shade(STEEL_L, 0.1), alpha(INK, 220), 0.6, ry=1.1)

    parts.append((14 * s, cradle))

    def tube():
        dx, dz = ang_dir(a)
        px, pz = -dz, dx
        # مختصات سه‌بعدی: نزدیک بدنهٔ گردان، دورتر و بالاتر
        b0 = proj(dx * 4, 49.0, dz * 4)
        b1 = proj(dx * 19, 71.0 - rec * 0.9, dz * 19)
        ex, ey = b1[0] - b0[0], b1[1] - b0[1]
        ln = math.hypot(ex, ey) or 1.0
        ux, uy = ex / ln, ey / ln
        nx, ny = -uy, ux
        half = 7.4
        lit = -nx * 0.55 - ny * 0.82
        # خط دور
        pen.line([b0, b1], INK, half * 2 + 4.0)
        pen.dot(b0[0], b0[1], half + 2.0, INK, None)
        pen.dot(b1[0], b1[1], half + 2.0, INK, None)
        # بدنه
        pen.line([b0, b1], STEEL_D, half * 2)
        pen.dot(b0[0], b0[1], half, STEEL_D, None)
        pen.dot(b1[0], b1[1], half, STEEL_D, None)
        # سایه‌روشن استوانه
        pen.line([(b0[0] + nx * lit * half * 0.45, b0[1] + ny * lit * half * 0.45),
                  (b1[0] + nx * lit * half * 0.45, b1[1] + ny * lit * half * 0.45)],
                 shade(STEEL_L, 0.05), half * 0.95)
        pen.line([(b0[0] - nx * lit * half * 0.62, b0[1] - ny * lit * half * 0.62),
                  (b1[0] - nx * lit * half * 0.62, b1[1] - ny * lit * half * 0.62)],
                 alpha(shade(IRON, -0.22), 225), half * 0.8)
        # حلقه‌های تقویت + بند زیتونی
        for k, col, rw in ((0.35, IRON, half + 1.9), (0.52, OLIVE_D, half + 1.4), (0.86, IRON, half + 2.1)):
            cxr = b0[0] + ex * k
            cyr = b0[1] + ey * k
            pen.line([(cxr + nx * rw, cyr + ny * rw), (cxr - nx * rw, cyr - ny * rw)], INK, 6.4)
            pen.line([(cxr + nx * rw, cyr + ny * rw), (cxr - nx * rw, cyr - ny * rw)],
                     shade(col, 0.14), 3.6)
        # دهانهٔ کلفت رو به بالا
        pen.ellipse([b1[0] - half - 1.6, b1[1] - half * 0.92 - 1.6,
                     b1[0] + half + 1.6, b1[1] + half * 0.92 + 1.6],
                    [(0, shade(STEEL_D, 0.22)), (1, shade(IRON, -0.28))], INK, 2.6)
        pen.ellipse([b1[0] - half * 0.72, b1[1] - half * 0.66,
                     b1[0] + half * 0.72, b1[1] + half * 0.66],
                    [(0, (18, 18, 21, 255)), (1, (46, 48, 52, 255))], INK, 1.6)
        pen.dot(b1[0] - nx * 1.4, b1[1] - ny * 1.4, half * 0.30, (58, 50, 42, 255), None,
                ry=half * 0.26)
        # حلقهٔ نارنجی نشانه‌گیری کنار دهانه
        pen.dot(b1[0] + nx * (half + 3.2), b1[1] + ny * (half + 3.2), 1.7,
                alpha(AMBER, 240), alpha(INK, 220), 0.5, ry=1.3)
        # جعبهٔ گلولهٔ ذخیره روی سطح گردان
        rc = rot2(-11.0, 6.0, a)
        cc = proj(rc[0], 49.6, rc[1])
        pen.rect([cc[0] - 6.0, cc[1] - 3.4, cc[0] + 6.0, cc[1] + 3.6],
                 [(0, shade(OLIVE, 0.22)), (1, OLIVE_D)], radius=1.6, w=2.0)
        pen.dot(cc[0], cc[1] - 0.4, 2.2, [(0, shade(ORANGE, 0.2)), (1, shade(ORANGE, -0.35))],
                alpha(INK, 220), 0.9, ry=1.9)
        info["m"] = ((b1[0] + ux * 2.0, b1[1] + uy * 2.0), (ux, uy), (nx, ny))

    parts.append((19 * s, tube))
    draw_sorted(parts)
    return info



HEAD_BUILDERS = {
    "machinegun": head_machinegun,
    "laser": head_laser,
    "rocket": head_rocket,
    "cannon": head_cannon,
}


# ------------------------------------------------------------------ افکت‌ها


class LayerPen:
    """قلم سبک برای لایهٔ درخشش."""

    def __init__(self, img, s):
        self.img, self.s = img, s
        self.d = ImageDraw.Draw(img)

    def dot(self, cx, cy, r, col, ry=None):
        ry = r if ry is None else ry
        self.d.ellipse([(cx - r) * self.s, (cy - ry) * self.s,
                        (cx + r) * self.s, (cy + ry) * self.s], fill=col)

    def line(self, pts, col, w):
        self.d.line([(x * self.s, y * self.s) for x, y in pts], fill=col,
                    width=max(1, int(w * self.s)), joint="curve")


def soft_glow(pen, paint, blur=6.0, passes=1):
    layer = Image.new("RGBA", pen.img.size, (0, 0, 0, 0))
    paint(LayerPen(layer, pen.s))
    layer = layer.filter(ImageFilter.GaussianBlur(blur * pen.s / 2))
    for _ in range(passes):
        pen.img.alpha_composite(layer)


def flame_star(pen, origin, axis, perp, length, width, seed=0.0, core=True):
    """شعلهٔ کارتونی ستاره‌ای در راستای لوله."""
    ox, oy = origin
    ux, uy = axis
    px, py = perp
    cxx = ox + ux * length * 0.34
    cyy = oy + uy * length * 0.34

    def star(scale_l, scale_w, n=7, jitter=0.0):
        pts = []
        for i in range(n * 2):
            k = i / (n * 2)
            ang = 2 * math.pi * k
            r = 1.0 if i % 2 == 0 else 0.48
            r *= 1.0 + jitter * math.sin(seed + i * 2.1)
            pts.append((cxx + ux * math.cos(ang) * r * length * scale_l
                        + px * math.sin(ang) * r * width * scale_w,
                        cyy + uy * math.cos(ang) * r * length * scale_l
                        + py * math.sin(ang) * r * width * scale_w))
        return pts

    pen.poly(star(0.62, 0.58, 7, 0.10),
             [(0.0, (206, 52, 16, 255)), (0.45, ORANGE), (1.0, AMBER)], (86, 27, 8, 255), 2.2)
    if core:
        pen.poly(star(0.42, 0.40, 6, 0.06),
                 [(0.0, AMBER), (0.55, (255, 236, 150, 255)), (1.0, WHITE)], None, 0)
    pen.dot(ox + ux * 1.2, oy + uy * 1.2, width * 0.34, WHITE, None, ry=width * 0.32)


def beam(pen, origin, axis, perp, length, width, charge=1.0):
    """پرتوی لیزر سبز با هاله، مغز سفید و حلقهٔ انرژی روی دهانه."""
    ox, oy = origin
    ux, uy = axis
    px, py = perp
    ex, ey = ox + ux * length, oy + uy * length
    soft_glow(pen, lambda lp: lp.line([(ox, oy), (ex, ey)], alpha(GREEN, 190), width * 2.6),
              blur=5.0, passes=2)
    pen.line([(ox, oy), (ex, ey)], alpha(GREEN, 210), width * 1.9)
    pen.line([(ox, oy), (ex, ey)], alpha(GREEN_PALE, 240), width * 1.1)
    pen.line([(ox, oy), (ex, ey)], WHITE, width * 0.45)
    # حلقهٔ دهانه
    ring_r = 3.2 + 5.0 * charge
    pen.ellipse([ox - ring_r, oy - ring_r * 0.9, ox + ring_r, oy + ring_r * 0.9],
                None, alpha(GREEN_PALE, 230), 1.8)
    pen.dot(ex, ey, width * 0.9, alpha(GREEN_PALE, 240), None, ry=width * 0.85)
    pen.dot(ex, ey, width * 0.45, WHITE, None, ry=width * 0.42)
    # جرقه‌های اطراف دهانه
    for i, (k, off) in enumerate(((0.35, 6.0), (0.6, -5.0), (0.85, 4.0))):
        bx, by = ox + ux * length * k + px * off, oy + uy * length * k + py * off
        pen.line([(bx, by), (bx + ux * 4.0 + px * 1.6, by + uy * 4.0 + py * 1.6)],
                 alpha(GREEN_PALE, 200), 1.2)


def fireball(pen, origin, axis, perp, radius, seed=0.0, smoke=0):
    ox, oy = origin
    ux, uy = axis
    px, py = perp
    if smoke:
        for i in range(smoke):
            k = (i + 1) / (smoke + 1.0)
            smoke_puff(pen, ox + ux * radius * (1.1 + k * 1.5) + px * (i - 1) * 2.0,
                       oy + uy * radius * (1.1 + k * 1.5) + py * (i - 1) * 2.0,
                       radius * (0.42 - 0.05 * k), 105)
    soft_glow(pen, lambda lp: lp.dot(ox + ux * radius * 0.4, oy + uy * radius * 0.4,
                                     radius * 1.5, alpha(ORANGE, 150)), blur=7.0)
    # گلولهٔ آتش با لوب‌های نامنظم
    pts = []
    n = 12
    for i in range(n):
        ang = 2 * math.pi * i / n
        r = radius * (1.0 + 0.18 * math.sin(seed + i * 2.3))
        pts.append((ox + ux * radius * 0.45 + math.cos(ang) * r * 0.85,
                    oy + uy * radius * 0.45 + math.sin(ang) * r * 0.85))
    pen.poly(pts, [(0.0, (196, 46, 14, 255)), (0.5, ORANGE), (1.0, AMBER)], (86, 27, 8, 255), 2.2)
    pen.dot(ox + ux * radius * 0.45, oy + uy * radius * 0.45, radius * 0.55,
            [(0, (255, 226, 120, 255)), (1, WHITE)], None, ry=radius * 0.52)


def ejected_shell(pen, origin, axis, perp, k):
    """پوکهٔ فشنگ جهیده از کنار گیربکس."""
    ox, oy = origin
    ux, uy = axis
    px, py = perp
    x = ox - ux * 6 + px * (10 + 8 * k)
    y = oy - uy * 6 + py * (10 + 8 * k) - 14 * k + 20 * k * k
    ang = -0.9 + 4.0 * k
    L, W = 3.4, 1.5
    ca, sa = math.cos(ang), math.sin(ang)
    pts = [(x + ca * L + -sa * W, y + sa * L + ca * W), (x + ca * L - -sa * W, y + sa * L - ca * W),
           (x - ca * L - -sa * W, y - sa * L - ca * W), (x - ca * L + -sa * W, y - sa * L + ca * W)]
    pen.poly(pts, [(0, (255, 214, 110, 255)), (1, (196, 138, 40, 255))], (60, 42, 16, 255), 1.2)


def aim_reticle(pen, origin, axis, perp, accent, strong=False, scale=1.0):
    ox, oy = origin
    ux, uy = axis
    px, py = perp
    col = alpha(accent, 235 if strong else 170)
    # خط نقطه‌چین هدف‌گیری
    for i in range(3):
        d = 7 + i * 7.0 * scale
        a = (ox + ux * d, oy + uy * d)
        b = (ox + ux * (d + 3.6), oy + uy * (d + 3.6))
        pen.line([a, b], col, 1.3)
    tx, ty = ox + ux * 25 * scale, oy + uy * 25 * scale
    r = 4.2 if strong else 3.4
    # براکت هدف
    for sx in (-1, 1):
        for sy in (-1, 1):
            pen.line([(tx + sx * r, ty + sy * r * 0.55),
                      (tx + sx * (r + 2.2), ty + sy * r * 0.55)], col, 1.2)
            pen.line([(tx + sx * r, ty + sy * r * 0.55),
                      (tx + sx * r, ty + sy * (r * 0.55 + 2.0))], col, 1.2)
    pen.dot(tx, ty, 1.4, alpha(accent, 240), None, ry=1.2)


def smoke_puff(pen, x, y, r, a=110):
    """دود کارتونی سه‌لوب."""
    for i, (ox, oy, rr) in enumerate(((-r * 0.62, r * 0.1, r * 0.52),
                                      (0, -r * 0.34, r * 0.70),
                                      (r * 0.58, r * 0.12, r * 0.48))):
        pen.dot(x + ox, y + oy, rr, alpha(SMOKE, int(a * (0.82 + 0.08 * i))),
                alpha(INK, int(a * 0.5)), 0.9, ry=rr * 0.86)


def vent_steam(pen, x, y, k):
    for i in range(2):
        pen.dot(x + (6 + 6 * i) * k, y - (5 + 7 * k) * (i + 1) * 0.6 - i * 2,
                2.0 + 1.6 * k + i * 0.7, alpha(SMOKE, int(60 - 20 * k)),
                alpha(INK, int(35)), 0.7, ry=(2.0 + 1.6 * k) * 0.8)


# ------------------------------------------------------------------ چیدن فریم


def compose(tower: str, d: int, action: str, f: int) -> Image.Image:
    """یک فریم ۱۲۸×۱۲۸ شفاف از برج، جهت و حالت خواسته‌شده."""
    accent = TOWERS[tower]["accent"]
    ang = math.radians(d * 45)
    total = dict(ACTIONS)[action]
    t = f / max(1, total - 1)

    bob = 0.0
    recoil = 0.0
    heat = 0.0
    if action == "idle":
        bob = 0.6 * math.sin(2 * math.pi * f / total)
    elif action == "aim":
        bob = 0.0
    else:  # fire
        recoil = (2.6, 3.6, 2.2, 0.8)[f % 4]
        bob = (0.9, 0.4, 0.2, 0.0)[f % 4]
        heat = (0.6, 1.0, 0.7, 0.3)[f % 4]

    if action == "idle":
        # چرخش آرام و رفت‌وبرگشتی سر در حالت آماده (حدود ۳ درجه)
        ang += math.radians(3.0) * math.sin(2 * math.pi * f / total)

    pen = Pen()
    draw_base(pen, accent)
    head_pen = Pen()
    info = HEAD_BUILDERS[tower](head_pen, ang, rec=recoil)
    # تکان/پس‌نشینی سر روی پایهٔ ثابت
    pen.img.alpha_composite(head_pen.img, (0, int(round((bob - recoil * 0.55) * SS))))

    head_off = bob - recoil * 0.55
    if info.get("m"):
        muzzle, axis, perp = info["m"]
        origin = (muzzle[0] + axis[0] * 3.0, muzzle[1] + head_off + axis[1] * 3.0)
    else:
        dx, dz = ang_dir(ang)
        origin = proj(dx * 44, 35.5, dz * 44)
        origin = (origin[0], origin[1] + head_off)
        sx = math.hypot(dx, dz * DEPTH)
        axis = (dx / sx, dz * DEPTH / sx)
        perp = (-axis[1], axis[0])

    # کوتاه‌شدگی جلوه در جهت‌های رو به دوربین (مثل خود لوله)
    fs = max(0.55, min(1.0, math.hypot(math.cos(ang), math.sin(ang) * DEPTH)))

    if action == "aim":
        aim_reticle(pen, origin, axis, perp, accent, strong=f == 1, scale=fs)

    elif action == "fire":
        if tower == "laser":
            beam(pen, origin, axis, perp, length=(18, 34, 30, 16)[f % 4] * fs,
                 width=(2.6, 3.4, 3.0, 2.2)[f % 4], charge=(0.4, 1.0, 0.8, 0.3)[f % 4])
        elif tower == "machinegun":
            lengths = (10, 26, 16, 6)
            widths = (7, 13, 9, 4.5)
            if f == 1:
                ejected_shell(pen, origin, axis, perp, 0.55)
            if f >= 2:
                smoke_puff(pen, origin[0] + axis[0] * 7, origin[1] + axis[1] * 7 - 3,
                           3.2 - f * 0.3, 80)
            soft_glow(pen, lambda lp: lp.dot(origin[0] + axis[0] * 6, origin[1] + axis[1] * 6,
                                             11, alpha(ORANGE, 120)), blur=6.0)
            flame_star(pen, origin, axis, perp, lengths[f % 4] * fs, widths[f % 4] * (0.7 + 0.3 * fs), seed=f * 1.7)
            if f == 1:
                for i, (k, o) in enumerate(((0.7, 7), (0.85, -5), (0.55, 8))):
                    bx, by = origin[0] + axis[0] * 16 + perp[0] * o, origin[1] + axis[1] * 16 + perp[1] * o
                    pen.line([(bx, by), (bx + axis[0] * 5 + perp[0] * 2,
                                         by + axis[1] * 5 + perp[1] * 2)], alpha(AMBER, 220), 1.3)
        elif tower == "rocket":
            r = (5.6, 9.5, 7.0, 3.6)[f % 4] * (0.75 + 0.25 * fs)
            for k, tp in enumerate(info.get("muzzles", [origin])):
                o2 = (tp[0] + axis[0] * 3.0, tp[1] + head_off + axis[1] * 3.0)
                fireball(pen, o2, axis, perp, r * (1.0 if k == 0 else 0.9),
                         seed=f * 1.3 + k, smoke=(0, 1, 2, 1)[f % 4])
            if f in (1, 2):
                smoke_puff(pen, origin[0] - axis[0] * 10, origin[1] - axis[1] * 10 + 4,
                           6.0 + f, 85 - 15 * f)
        else:  # cannon
            fireball(pen, origin, axis, perp, (8.0, 14.0, 10.5, 5.5)[f % 4] * (0.82 + 0.18 * fs),
                     seed=f * 2.1, smoke=(0, 1, 2, 3)[f % 4])
            if f in (1, 2):
                pen.ellipse([origin[0] - 12, origin[1] - 9, origin[0] + 12, origin[1] + 9],
                            None, alpha(AMBER, 180), 1.4)

    # پالس چراغ‌های پایه در حالت آماده
    if action == "idle" and f in (1, 2):
        soft_glow(pen, lambda lp: lp.dot(CX + 31, 92.0 + 0.4 * f, 5.5, alpha(accent, 90)), blur=4.0)
    if action == "idle" and f == 3:
        vent_steam(pen, CX + 21, 74.0, 0.8)

    return pen.out()


# ------------------------------------------------------------------ خروجی


def sheet_for(tower: str, action: str) -> Image.Image:
    """شیت شبکه‌ای: هر ردیف یک جهت (۰..۷) و هر ستون یک فریم."""
    n = dict(ACTIONS)[action]
    sheet = Image.new("RGBA", (FRAME * n, FRAME * DIRECTIONS), (0, 0, 0, 0))
    for d in range(DIRECTIONS):
        for f in range(n):
            sheet.alpha_composite(compose(tower, d, action, f), (f * FRAME, d * FRAME))
    return sheet


def checker(size, cell=8, a=(70, 74, 82, 255), b=(52, 56, 63, 255)):
    img = Image.new("RGBA", size, a)
    d = ImageDraw.Draw(img)
    for y in range(0, size[1], cell):
        for x in range(0, size[0], cell):
            if (x // cell + y // cell) % 2:
                d.rectangle([x, y, x + cell - 1, y + cell - 1], fill=b)
    return img


def ribbon(img, text, height=22):
    """روبان پایین تصویر با متن لاتین (پشتیبانی RTL برای فونت پیدا نشد)."""
    from PIL import ImageFont
    w = img.width
    band = Image.new("RGBA", (w, height), (18, 20, 24, 235))
    img.alpha_composite(band, (0, img.height - height))
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 12)
    except Exception:
        font = ImageFont.load_default()
    d = ImageDraw.Draw(img)
    d.text((8, img.height - height + 5), text, font=font, fill=(236, 240, 236, 255))
    return img


def build_previews(sheet_map, towers):
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    pad, gap, lab = 10, 8, 18

    # ۱) نمای کلی: هر ردیف یک برج، سه ستون idle/aim/fire با بزرگ‌نمایی ۲×
    cw = FRAME * 2
    out = Image.new("RGBA", (pad * 2 + cw * 3 + gap * 2, pad * 2 + 26 + len(towers) * (cw + lab + gap)),
                    (24, 26, 30, 255))
    sheet = checker(out.size, cell=16)
    sheet.alpha_composite(out)
    out = sheet
    for i, t in enumerate(towers):
        for j, action in enumerate(("idle", "aim", "fire")):
            im = compose(t, 1, action, 1 if action == "aim" else 0)
            cell = checker((cw, cw), cell=16)
            cell.alpha_composite(im.resize((cw, cw), Image.LANCZOS))
            ribbon(cell, f"{t} - {action}", 16)
            x = pad + j * (cw + gap)
            y = pad + 26 + i * (cw + lab + gap)
            out.alpha_composite(cell, (x, y))
    out = ribbon(out, "ISO TOWER PACK - 128x128 - idle | aim | fire (facing south-east)", 26)
    out.save(os.path.join(PREVIEW_DIR, "overview.png"), optimize=True)

    # ۲) هشت جهت چرخش (یک برج، ۴×۲)
    spin = checker((FRAME * 4 + gap * 3 + pad * 2, FRAME * 2 + gap + lab + pad * 2), cell=16)
    for d in range(8):
        im = compose("machinegun", d, "idle", 0)
        x = pad + (d % 4) * (FRAME + gap)
        y = pad + (d // 4) * (FRAME + gap)
        spin.alpha_composite(im, (x, y))
        tag = Image.new("RGBA", (FRAME, 14), (18, 20, 24, 210))
        spin.alpha_composite(tag, (x, y + FRAME - 14))
        d0 = ImageDraw.Draw(spin)
        d0.text((x + 4, y + FRAME - 13), f"dir {d}", fill=(240, 226, 170, 255))
    spin = ribbon(spin, "8 directions, clockwise from east - base stays fixed", lab)
    spin.save(os.path.join(PREVIEW_DIR, "directions.png"), optimize=True)

    # ۳) شیت‌های کامل هر برج: سه حرکت کنار هم، ردیف=جهت و ستون=فریم
    for t in towers:
        patches = []
        for action in ("idle", "aim", "fire"):
            sheet = sheet_map[(t, action)]
            bg = checker(sheet.size, cell=8)
            bg.alpha_composite(sheet)
            patches.append(ribbon(bg, f"{t} / {action} / {dict(ACTIONS)[action]} frames x 8 dirs", 16))
        width = sum(p.width for p in patches) + gap * (len(patches) - 1) + pad * 2
        height = max(p.height for p in patches) + pad * 2
        out = Image.new("RGBA", (width, height), (24, 26, 30, 255))
        x = pad
        for p in patches:
            out.alpha_composite(p, (x, pad))
            x += p.width + gap
        out.save(os.path.join(PREVIEW_DIR, f"sheet_{t}.png"), optimize=True)

    # ۴) صحنهٔ بازی: زمین ایزومتریک + چهار برج روی خانه‌های خودشان
    tile = 88
    W, H = 620, 430
    scene = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(scene)
    origin_x, origin_y = W / 2, 46
    grid = {}

    def tile_center(gx, gy):
        return (origin_x + (gx - gy) * tile / 2, origin_y + (gx + gy) * tile / 4)

    for gy in range(-1, 9):
        for gx in range(-1, 9):
            tcx, tcy = tile_center(gx, gy)
            diamond = [(tcx, tcy - tile / 4), (tcx + tile / 2, tcy),
                       (tcx, tcy + tile / 4), (tcx - tile / 2, tcy)]
            col = (92, 110, 74, 255) if (gx + gy) % 2 else (84, 102, 67, 255)
            sd.polygon(diamond, fill=col)
            sd.line(diamond + [diamond[0]], fill=(60, 74, 50, 130), width=1)
            grid[(gx, gy)] = tcx, tcy
    # خانه‌های اشغال‌شده: کف سنگی تیره‌تر
    spots = (("machinegun", 4, 3, 1, 1), ("laser", 2, 5, 1, 2),
             ("cannon", 6, 5, 3, 1), ("rocket", 5, 1, 5, 2))
    for _, gx, gy, d, _a in spots:
        tcx, tcy = tile_center(gx, gy)
        diamond = [(tcx, tcy - tile / 4), (tcx + tile / 2, tcy),
                   (tcx, tcy + tile / 4), (tcx - tile / 2, tcy)]
        sd.polygon(diamond, fill=(78, 78, 70, 255))
        sd.line(diamond + [diamond[0]], fill=(46, 48, 44, 160), width=2)
    # برج‌ها با ترتیب عمق (پایین‌تر = جلوتر)
    spots = sorted(spots, key=lambda sp: sp[1] + sp[2])
    for t, gx, gy, d, _a in spots:
        tcx, tcy = tile_center(gx, gy)
        scene.alpha_composite(compose(t, d, "idle", 0),
                              (int(tcx - FRAME / 2), int(tcy - GROUND - 15)))
    scene = ribbon(scene, "in-game mock - fixed isometric bases, heads rotate to targets", 18)
    scene.save(os.path.join(PREVIEW_DIR, "scene.png"), optimize=True)

    # ۵) GIF: چرخش آرام + شلیک
    for t in towers:
        frames = []
        for d in (1, 2, 3, 4):
            for f in range(4):
                bg = checker((FRAME, FRAME), cell=8)
                bg.alpha_composite(compose(t, d, "idle", f))
                frames.append(bg)
        for f in range(4):
            bg = checker((FRAME, FRAME), cell=8)
            bg.alpha_composite(compose(t, 3, "fire", f))
            frames.append(bg)
        # همهٔ فریم‌ها روی یک پالت مشترک می‌روند تا رنگ‌ها در GIF جابه‌جا نشوند
        rgb = [f.convert("RGB") for f in frames]
        palette = rgb[0].quantize(colors=200, method=Image.MEDIANCUT)
        frames = [f.quantize(palette=palette, dither=Image.NONE) for f in rgb]
        frames[0].save(os.path.join(PREVIEW_DIR, f"anim_{t}.gif"), save_all=True,
                       append_images=frames[1:], duration=110, loop=0, disposal=1)


def build_atlas(sheet_map, towers):
    cols = len(towers) * 3
    rows = DIRECTIONS
    atlas = Image.new("RGBA", (FRAME * cols, FRAME * rows), (0, 0, 0, 0))
    index = {}
    for ci, (t, action) in enumerate([(t, a) for t in towers for a in ("idle", "aim", "fire")]):
        n = dict(ACTIONS)[action]
        sheet = sheet_map[(t, action)]
        for d in range(DIRECTIONS):
            for f in range(n):
                frame = sheet.crop((f * FRAME, d * FRAME, (f + 1) * FRAME, (d + 1) * FRAME))
                atlas.alpha_composite(frame, (ci * FRAME, d * FRAME))
                index[f"{t}_{action}_{d}_{f}"] = {
                    "index": [ci, d], "frame": [f], "x": ci * FRAME, "y": d * FRAME,
                }
    atlas.save(os.path.join(OUT_DIR, "atlas_towers_iso.png"), optimize=True)
    meta = {
        "image": "atlas_towers_iso.png",
        "frameWidth": FRAME, "frameHeight": FRAME,
        "grid": {"columns": cols, "rows": rows, "directionAxis": "y", "buildAxis": "x",
                 "note": "اطلس برای پیش‌نمایش سریع است: ستون = برج×حرکت، ردیف = جهت (فریم ۰). "
                         "برای انیمیشن کامل از شیت اصلی <tower>_<action>.png استفاده کنید "
                         "(ردیف = جهت، ستون = فریم)."},
        "columns": [f"{t}_{a}" for t in towers for a in ("idle", "aim", "fire")],
        "directions": DIRECTIONS,
        "names": index,
    }
    with open(os.path.join(OUT_DIR, "atlas_towers_iso.json"), "w", encoding="utf8") as fh:
        json.dump(meta, fh, indent=1)


def write_manifest(sheet_map, towers):
    manifest = {
        "pack": "iso-tower-defense",
        "frameWidth": FRAME, "frameHeight": FRAME,
        "layout": "grid", "alpha": True,
        "layoutNote": "هر شیت ۸ ردیف (جهت) × N ستون (فریم) از فریم‌های ۱۲۸×۱۲۸ است.",
        "directions": DIRECTIONS,
        "directionOrder": ["east", "south-east", "south", "south-west",
                           "west", "north-west", "north", "north-east"],
        "directionNote": "ردیف n = جهت n (ساعت‌گرد روی صفحه، گام ۴۵ درجه از شرق)؛ ستون = فریم.",
        "camera": {
            "projection": "isometric-2:1",
            "note": "نمای از بالا و کمی مایل؛ ارتفاع روی محور y صفحه و عمق با ضریب ۰٫۵ فشرده می‌شود.",
            "depthSquash": DEPTH, "groundY": GROUND, "pivotX": CX,
            "pivotNote": "محور چرخش سر در پیکسل (64, 75) است و با هر فریم ثابت می‌ماند.",
        },
        "palette": {
            "ink": "#1b191f", "olive": "#5b6b41", "oliveLight": "#839460",
            "navy": "#28495f", "navyLight": "#46708a", "steel": "#859098",
            "steelDark": "#58616a", "concrete": "#74756b",
            "orange": "#ff8a2b", "amber": "#ffc24a", "red": "#d8452f",
            "laserGreen": "#6cff8a", "laserPale": "#ceffd8",
        },
        "towers": [],
    }
    for t in towers:
        meta = TOWERS[t]
        entry = {
            "id": t, "nameFa": meta["name_fa"],
            "accent": "#%02x%02x%02x" % meta["accent"][:3],
            "pivot": [CX, 75], "muzzleHint": "دهانه با چرخش سر می‌چرخد؛ افکت شلیک روی فریم‌هاست.",
            "actions": {},
        }
        for action, n in ACTIONS:
            fn = f"{t}_{action}.png"
            entry["actions"][action] = {
                "file": fn, "frames": n, "fps": ACTION_FPS[action],
                "loop": action != "fire",
                "grid": {"columns": n, "rows": DIRECTIONS},
                "layout": "grid-dir-major: row = direction (0..7), column = frame",
            }
        manifest["towers"].append(entry)
    manifest["atlas"] = {"image": "atlas_towers_iso.png", "meta": "atlas_towers_iso.json"}
    with open(os.path.join(OUT_DIR, "manifest.json"), "w", encoding="utf8") as fh:
        json.dump(manifest, fh, indent=2, ensure_ascii=False)
    return manifest


def verify(sheet_map, towers) -> list[str]:
    """بازرسی خروجی: اندازهٔ فریم، شفافیت، پایداری پایه و نبود کلیپ.

    خطاها را برمی‌گرداند تا CI یا اجرای دستی با کد ۱ تمام شود.
    """
    problems = []
    for t in towers:
        base_pixels = None
        for action, n in ACTIONS:
            sheet = sheet_map[(t, action)]
            if sheet.size != (FRAME * n, FRAME * DIRECTIONS):
                problems.append(f"{t}_{action}: اندازه {sheet.size} با قرارداد شبکه نمی‌خواند")
                continue
            frames = [[sheet.crop((f * FRAME, d * FRAME, (f + 1) * FRAME, (d + 1) * FRAME))
                       for f in range(n)] for d in range(DIRECTIONS)]
            for d, row in enumerate(frames):
                for f, frame in enumerate(row):
                    if frame.mode != "RGBA":
                        problems.append(f"{t}_{action} d{d} f{f}: حالت {frame.mode} نیست RGBA")
                    if frame.getchannel("A").getextrema()[1] < 200:
                        problems.append(f"{t}_{action} d{d} f{f}: کاملاً شفاف است")
            if action == "idle" and d == 1:
                base_pixels = row[0]
            # پایهٔ ثابت: ردیف پایین کادر (y ≥ 104) در همهٔ فریم‌ها یکسان است
            for d, row in enumerate(frames):
                for f, frame in enumerate(row):
                    strip = frame.crop((0, 104, FRAME, FRAME)).tobytes()
                    ref = frames[1][0].crop((0, 104, FRAME, FRAME)).tobytes()
                    if strip != ref:
                        problems.append(f"{t}_{action} d{d} f{f}: پایه بین فریم‌ها جابه‌جا شده")
                        break
        # برج باید در همهٔ جهت‌ها شکل داشته باشد
        for action, n in ACTIONS:
            if sheet_map[(t, action)].getbbox() is None:
                problems.append(f"{t}_{action}: خالی است")
    return problems


def main(argv=None):
    ap = argparse.ArgumentParser(description="ساخت اسپرایت‌شیت ایزومتریک برج‌های دفاعی")
    ap.add_argument("--only", nargs="*", default=None, help="فقط این برج‌ها")
    ap.add_argument("--no-gif", action="store_true", help="بدون ساخت پیش‌نمایش متحرک")
    args = ap.parse_args(argv)

    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    towers = [t for t in TOWERS if not args.only or t in args.only]
    sheet_map = {}
    for t in towers:
        for action, n in ACTIONS:
            sheet = sheet_for(t, action)
            sheet.save(os.path.join(OUT_DIR, f"{t}_{action}.png"), optimize=True)
            sheet_map[(t, action)] = sheet
            print(f"  {t}_{action}.png  {sheet.width}x{sheet.height}")
    write_manifest(sheet_map, towers)
    build_atlas(sheet_map, towers)
    if not args.no_gif:
        build_previews(sheet_map, towers)
    problems = verify(sheet_map, towers)
    if problems:
        print("بررسی خروجی شکست خورد:", file=sys.stderr)
        for msg in problems:
            print("  -", msg, file=sys.stderr)
        raise SystemExit(1)
    print(f"{len(towers) * len(ACTIONS)} شیت + اطلس + مانیفست در {OUT_DIR} (بررسی خروجی سبز)")


if __name__ == "__main__":
    main()
