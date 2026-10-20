#!/usr/bin/env python3
"""ساخت هشت اسپرایت‌شیت افکت کارتونی برای دژبان.

هر شیت یک ردیف افقی از فریم‌های ۶۴×۶۴ و پس‌زمینهٔ کاملاً شفاف (PNG/RGBA) است؛
خطوط دور ضخیم و تیره، رنگ‌های شاد و شکل‌های پُف‌دار به سبک Kingdom Rush.

    python3 tools/gen_fx_sheets.py                     # نوشتن هشت شیت
    python3 tools/gen_fx_sheets.py --preview <مسیر>    # تصویر نمای کلی روی شطرنجی

خروجی‌ها در assets/sprites/fx/:
    explosion_small.png  ۶ فریم    explosion_medium.png  ۸ فریم
    explosion_large.png  ۱۰ فریم   muzzle_flash.png      ۴ فریم
    laser_beam.png       ۶ فریم    tesla_bolt.png        ۶ فریم
    smoke_cloud.png      ۶ فریم    fire_flame.png        ۶ فریم
"""

from __future__ import annotations

import math
import os
import random
import sys

try:
    from PIL import Image, ImageChops, ImageDraw, ImageFilter
except ImportError:  # pragma: no cover
    print("Pillow لازم است:  pip install pillow", file=sys.stderr)
    raise

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_sprites import P, hexc, save  # noqa: E402  (قلم و ذخیره‌سازیِ مولد اصلی)

SS = 4          # همان ضریب ابرنمونه‌برداریِ مولد اصلی برای لبه‌های تمیز
FRAME = 64      # اندازهٔ هر فریم
CENTER = FRAME / 2.0

# ------------------------------------------------------------------ پالت
INK_FIRE = "#4a1a0e"     # خط دور آتش و انفجار
INK_SMOKE = "#3e434b"    # خط دور دود
INK_LASER = "#10375f"    # خط دور پرتو
INK_TESLA = "#2d1450"    # خط دور صاعقه

FIRE = ["#d8451c", "#f5821f", "#fdc22d", "#fff3b0"]          # بیرون → درون
FIRE_COOL = ["#b8371a", "#e2601f", "#f79a3c", "#ffe6a8"]     # انفجار رو به سرد شدن
SMOKE = ["#cdd3da", "#a9b0ba", "#828a94"]                    # دود روشن
SMOKE_DARK = ["#6f6a75", "#514d5a", "#38353f"]               # دود سیاه غلیظ
LASER = ["#2f8fe0", "#6fd8ff", "#eaffff"]                    # پرتو: هاله، بدنه، مغزه
TESLA = ["#8a4dff", "#c9a4ff", "#f6ecff"]                    # صاعقه
DEBRIS_FILL = "#7d5533"
DEBRIS_INK = "#33200f"


# ------------------------------------------------------------------ ابزارهای ماسک
def _scaled(pts):
    return [(x * SS, y * SS) for x, y in pts]


def poly_mask(size, pts):
    """ماسک سیاه‌وسفیدِ یک چندضلعی در مقیاس ابرنمونه."""
    mask = Image.new("L", (size * SS, size * SS), 0)
    ImageDraw.Draw(mask).polygon(_scaled(pts), fill=255)
    return mask


def grow(mask, px):
    """انبساط ماسک به اندازهٔ px پیکسل ابرنمونه (هر بار یک پیکسل)."""
    out = mask
    for _ in range(int(round(px))):
        out = out.filter(ImageFilter.MaxFilter(3))
    return out


def union_outline(size, draw_fn, thickness):
    """اجتماع چند لُخمه را یک‌تکه می‌کند و خط دورِ بیرونی‌اش را می‌سازد.

    draw_fn روی یک ImageDraw در مقیاس ابرنمونه رسم می‌کند (مقدار ۲۵۵ = داخل).
    خروجی: (ماسکِ شکل، ماسکِ خط دور) — خط دور کاملاً بیرونِ شکل است، پس با هر
    ضخامتی لبهٔ شکل تمیز و یک‌دست می‌ماند.
    """
    mask = Image.new("L", (size * SS, size * SS), 0)
    draw_fn(ImageDraw.Draw(mask))
    ring = ImageChops.subtract(grow(mask, thickness * SS), mask)
    return mask, ring


def paste(painter, color, mask, alpha=1.0):
    """رنگِ یک‌دست را با ماسکِ داده‌شده روی بوم می‌چسباند."""
    layer = Image.new("RGBA", painter.img.size, hexc(color))
    if alpha < 1.0:
        mask = mask.point(lambda v: int(v * alpha))
    layer.putalpha(mask)
    painter.img.alpha_composite(layer)


def blob_pts(cx, cy, rx, ry, seed=1, wobble=0.2, points=18, phase=0.0):
    """چندضلعیِ پُف‌دار و اندام‌وار؛ تصادفِ همسایه‌ها نرم می‌شود تا لبه نپرد."""
    rng = random.Random(seed)
    offsets = [1.0 + rng.uniform(-wobble, wobble) for _ in range(points)]
    pts = []
    for i in range(points):
        angle = 2.0 * math.pi * i / points + phase
        k = (offsets[i] + offsets[(i + 1) % points] + offsets[i - 1]) / 3.0
        pts.append((cx + math.cos(angle) * rx * k, cy + math.sin(angle) * ry * k))
    return pts


def star_pts(cx, cy, outer, inner, spikes, phase=0.0, seed=1, jitter=0.12):
    """ستارهٔ نوک‌تیز برای جرقه و شعلهٔ شلیک."""
    rng = random.Random(seed)
    pts = []
    for i in range(spikes * 2):
        angle = math.pi * i / spikes + phase
        radius = outer if i % 2 == 0 else inner
        radius *= 1.0 + rng.uniform(-jitter, jitter)
        pts.append((cx + math.cos(angle) * radius, cy + math.sin(angle) * radius))
    return pts


# ------------------------------------------------------------------ اجزای افکت
def hot_glow(painter, cx, cy, radius, color=(255, 170, 70), alpha=90, blur=5):
    painter.glow(cx, cy, radius, color, alpha=alpha, blur=blur)


def fireball(painter, size, cx, cy, r, seed=1, alpha=1.0, colors=FIRE, wobble=0.2,
             outline=INK_FIRE, thickness=2.2, hot=True, squash=0.94):
    """گلولهٔ آتش کارتونی: خط دور ضخیم، بدنهٔ نارنجی، مغزهٔ زرد و سفید."""
    if hot:
        hot_glow(painter, cx, cy, min(r * 1.05, 23.0), alpha=int(85 * alpha), blur=5)
    outer = blob_pts(cx, cy, r, r * squash, seed=seed, wobble=wobble, points=18)
    mask, ring = union_outline(size, lambda d: d.polygon(_scaled(outer), fill=255), thickness)
    paste(painter, outline, ring, alpha)
    paste(painter, colors[0], mask, alpha)
    for i, color in enumerate(colors[1:], start=1):
        k = [0.76, 0.52, 0.30][i - 1] if len(colors) > 3 else 0.8 - 0.2 * i
        pts = blob_pts(cx, cy, r * k, r * k * squash, seed=seed + i * 7,
                       wobble=wobble + 0.03 * i, points=16 - 2 * i, phase=i * 0.8)
        paste(painter, color, poly_mask(size, pts), alpha)


def smoke_puff(painter, size, cx, cy, r, seed=1, alpha=1.0, colors=SMOKE, wobble=0.24,
               thickness=2.0, rise=0.0):
    """ابر دود کارتونی: چند لُخمه که یک‌جا (بدون خط داخلی) ترسیم می‌شوند."""
    cy -= rise
    lobes = [
        (cx, cy, r, r * 0.86, seed),
        (cx - r * 0.58, cy + r * 0.22, r * 0.62, r * 0.54, seed + 3),
        (cx + r * 0.55, cy + r * 0.16, r * 0.56, r * 0.50, seed + 5),
        (cx + r * 0.02, cy - r * 0.58, r * 0.62, r * 0.56, seed + 7),
    ]

    def draw(d):
        for lx, ly, rx, ry, sd in lobes:
            d.polygon(_scaled(blob_pts(lx, ly, rx, ry, seed=sd, wobble=wobble, points=16)), fill=255)

    mask, ring = union_outline(size, draw, thickness)
    paste(painter, INK_SMOKE, ring, alpha)
    paste(painter, colors[1], mask, alpha)
    # هایلایتِ روشن در بالا و سایه در پایین برای حجمِ کارتونی
    top = blob_pts(cx - r * 0.18, cy - r * 0.42, r * 0.52, r * 0.40, seed=seed + 11,
                   wobble=0.2, points=14)
    paste(painter, colors[0], poly_mask(size, top), alpha * 0.95)
    bottom = blob_pts(cx + r * 0.20, cy + r * 0.52, r * 0.55, r * 0.30, seed=seed + 13,
                      wobble=0.2, points=14)
    paste(painter, colors[2], poly_mask(size, bottom), alpha * 0.55)


def spark(painter, cx, cy, r, color="#fff3b0", outline=INK_FIRE, width=1.1, alpha=1.0,
          spikes=4, phase=0.0, seed=1):
    pts = star_pts(cx, cy, r, r * 0.42, spikes, phase=phase, seed=seed)
    painter.poly(pts, fill=hexc(color, int(255 * alpha)),
                 outline=hexc(outline, int(255 * alpha)), width=width)


def sparks_around(painter, cx, cy, count, dist, r, seed=1, alpha=1.0, phase=0.0,
                  color="#ffd166", outline=INK_FIRE):
    """جرقه‌هایی که به‌شکل منظم و پرشی به بیرون می‌پرند."""
    for i in range(count):
        angle = 2.0 * math.pi * i / count + phase + 0.35 * (i % 2)
        d = dist * (0.85 + 0.3 * ((i * 7 % 5) / 5.0))
        spark(painter, cx + math.cos(angle) * d, cy + math.sin(angle) * d,
              r * (0.75 + 0.35 * ((i * 3 % 4) / 4.0)), color=color, outline=outline,
              alpha=alpha, seed=seed + i, phase=angle)


def debris(painter, cx, cy, r, seed, alpha=1.0, fill=DEBRIS_FILL, ink=DEBRIS_INK):
    pts = blob_pts(cx, cy, r, r * 0.82, seed=seed, wobble=0.42, points=8, phase=seed * 0.7)
    painter.poly(pts, fill=hexc(fill, int(255 * alpha)),
                 outline=hexc(ink, int(255 * alpha)), width=1.5)


def debris_burst(painter, cx, cy, count, dist, r, seed=1, alpha=1.0, phase=0.0):
    for i in range(count):
        angle = 2.0 * math.pi * i / count + phase + 0.4 * (i % 3)
        d = dist * (0.8 + 0.35 * ((i * 5 % 4) / 4.0))
        debris(painter, cx + math.cos(angle) * d, cy + math.sin(angle) * d,
               r * (0.7 + 0.5 * ((i * 3 % 5) / 5.0)), seed=seed + i * 3, alpha=alpha)


def shock_ring(painter, cx, cy, r, color="#ffe08a", width=2.4, alpha=1.0):
    painter.ellipse([cx - r, cy - r, cx + r, cy + r],
                    outline=hexc(color, int(255 * alpha)), width=width)


def beam_pts(cx, cy, length, half, taper=0.5, steps=26):
    """عدسیِ پرتو: از مرکز به دو سر باریک می‌شود."""
    top, bottom = [], []
    for i in range(steps + 1):
        x = -length + 2.0 * length * i / steps
        t = abs(x) / max(length, 0.001)
        hw = half * (1.0 - min(1.0, t) ** 2.6) ** taper
        top.append((cx + x, cy - hw))
        bottom.append((cx + x, cy + hw))
    return top + bottom[::-1]


def laser_beam(painter, size, cx, cy, length, half, alpha=1.0, core=True):
    hot_glow(painter, cx, cy, max(length * 0.5, half * 1.8), color=(90, 200, 255),
             alpha=int(55 * alpha), blur=4)
    outer = beam_pts(cx, cy, length, half, taper=0.35)
    mask, ring = union_outline(size, lambda d: d.polygon(_scaled(outer), fill=255), 2.0)
    paste(painter, INK_LASER, ring, alpha)
    paste(painter, LASER[0], mask, alpha)
    paste(painter, LASER[1], poly_mask(size, beam_pts(cx, cy, length, half * 0.60, 0.45)), alpha)
    if core:
        paste(painter, LASER[2], poly_mask(size, beam_pts(cx, cy, length, half * 0.32, 0.5)), alpha)


def bolt_pts(p0, p1, seed, displace, detail=5):
    """قوس برقی با جابه‌جایی میانیِ بازگشتی (ظاهرِ دندانه‌دارِ صاعقه)."""
    rng = random.Random(seed)
    pts = [p0, p1]
    for _ in range(detail):
        nxt = []
        for i in range(len(pts) - 1):
            a, b = pts[i], pts[i + 1]
            dx, dy = b[0] - a[0], b[1] - a[1]
            length = math.hypot(dx, dy) or 1.0
            nx, ny = -dy / length, dx / length
            off = rng.uniform(-displace, displace)
            mid = ((a[0] + b[0]) / 2.0 + nx * off, (a[1] + b[1]) / 2.0 + ny * off)
            nxt += [a, mid]
        nxt.append(pts[-1])
        pts = nxt
        displace *= 0.55
    return pts


def draw_bolt(painter, pts, width=4.0, colors=TESLA, ink=INK_TESLA, alpha=1.0):
    painter.line(pts, fill=hexc(ink, int(255 * alpha)), width=width + 3.0)
    painter.line(pts, fill=hexc(colors[0], int(255 * alpha)), width=width)
    painter.line(pts, fill=hexc(colors[1], int(255 * alpha)), width=width * 0.5)
    painter.line(pts, fill=hexc(colors[2], int(255 * alpha)), width=max(1.0, width * 0.22))


def flame_pts(cx, base_y, height, half, lean, sway, phase, wobble, steps=20):
    """نیم‌رخِ شعله: از قاعده تا نوک، با کج شدن و موجِ لبه."""
    left, right = [], []
    for i in range(steps + 1):
        t = i / steps
        hw = half * (1.0 - t ** 1.8) ** 0.6 * (1.0 + wobble * math.sin(3.4 * math.pi * t + phase))
        center = cx + lean * (t ** 1.6) + sway * math.sin(2.0 * math.pi * t + phase) * (t ** 0.7)
        left.append((center - hw, base_y - height * t))
        right.append((center + hw, base_y - height * t))
    return left + right[::-1]


def flame(painter, size, cx, base_y, height, half, lean, sway, phase, alpha=1.0, seed=1,
          colors=FIRE, thickness=2.2):
    hot_glow(painter, cx, base_y - height * 0.4, min(half * 1.7, 19.0), alpha=int(70 * alpha),
             blur=4)
    outer = flame_pts(cx, base_y, height, half, lean, sway, phase, 0.16)
    mask, ring = union_outline(size, lambda d: d.polygon(_scaled(outer), fill=255), thickness)
    paste(painter, INK_FIRE, ring, alpha)
    paste(painter, colors[0], mask, alpha)
    layers = [(0.82, 0.66, colors[1], 0.5), (0.62, 0.46, colors[2], 1.0), (0.38, 0.26, colors[3], 1.6)]
    for i, (kh, kw, color, dphase) in enumerate(layers, start=1):
        pts = flame_pts(cx + lean * 0.15 * i, base_y, height * kh, half * kw,
                        lean * 0.8, sway * 0.7, phase + dphase, 0.2, steps=16)
        paste(painter, color, poly_mask(size, pts), alpha)


# ------------------------------------------------------------------ فریم و شیت
def new_frame():
    return P(FRAME, FRAME, supersample=SS)


def faded(img, k):
    img.putalpha(img.getchannel("A").point(lambda v: int(v * k)))
    return img


def blank(size=FRAME):
    return Image.new("RGBA", (size, size), (0, 0, 0, 0))


def sheet(frames):
    out = Image.new("RGBA", (FRAME * len(frames), FRAME), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        # alpha_composite و نه paste: چسباندنِRGBA با ماسکِ RGBA آلفا را دوبار
        # ضرب می‌کند و فریم‌های نیمه‌شفاف تیره و بیش‌از‌حد محو می‌شوند.
        out.alpha_composite(frame, (i * FRAME, 0))
    return out


# ------------------------------------------------------------------ هشت شیت
def explosion_small():
    """۶ فریم: جرقه → گلولهٔ کوچک → اوج → محو → دود کوچک → خالی."""
    frames = []
    # ۱: جرقهٔ کوچک
    p = new_frame()
    hot_glow(p, CENTER, CENTER, 12, alpha=120, blur=4)
    spark(p, CENTER, CENTER, 9, seed=2, spikes=4)
    sparks_around(p, CENTER, CENTER, 3, 14, 2.6, seed=5, alpha=0.9)
    frames.append(p.out())

    # ۲: گلولهٔ آتش نارنجی در حال رشد
    p = new_frame()
    fireball(p, FRAME, CENTER, CENTER, 13, seed=11, colors=FIRE_COOL)
    sparks_around(p, CENTER, CENTER, 2, 19, 3.0, seed=8, alpha=0.95)
    frames.append(p.out())

    # ۳: اوج انفجار نارنجی و زرد
    p = new_frame()
    fireball(p, FRAME, CENTER, CENTER, 20, seed=21, wobble=0.22)
    shock_ring(p, CENTER, CENTER, 23, alpha=0.85)
    sparks_around(p, CENTER, CENTER, 5, 25, 3.4, seed=13, alpha=0.95)
    frames.append(p.out())

    # ۴: محو شدن گلولهٔ آتش
    p = new_frame()
    fireball(p, FRAME, CENTER, CENTER, 16, seed=31, colors=FIRE_COOL, alpha=0.8, hot=False)
    sparks_around(p, CENTER, CENTER, 3, 22, 2.4, seed=19, alpha=0.5, phase=0.9)
    frames.append(p.out())

    # ۵: دود کوچک
    p = new_frame()
    smoke_puff(p, FRAME, CENTER, CENTER + 2, 11, seed=41, alpha=0.85)
    frames.append(p.out())

    # ۶: خالی / کاملاً شفاف
    frames.append(blank())
    return frames


def explosion_medium():
    """۸ فریم: جرقه → رشد → اوج → آوار → کوچک شدن → دود → محو."""
    frames = []
    # ۱: جرقهٔ اولیه
    p = new_frame()
    hot_glow(p, CENTER, CENTER, 14, alpha=130, blur=4)
    spark(p, CENTER, CENTER, 10, seed=3, spikes=4)
    sparks_around(p, CENTER, CENTER, 4, 16, 3.0, seed=7, alpha=0.9)
    frames.append(p.out())

    # ۲ و ۳: گلولهٔ آتش نارنجی در حال رشد
    for i, r in enumerate([15, 21]):
        p = new_frame()
        fireball(p, FRAME, CENTER, CENTER, r, seed=51 + i * 9, colors=FIRE if i else FIRE_COOL)
        sparks_around(p, CENTER, CENTER, 2 + i, min(r + 6, 25), 3.0, seed=17 + i, alpha=0.9)
        frames.append(p.out())

    # ۴: اوج انفجار با زرد و نارنجی
    p = new_frame()
    fireball(p, FRAME, CENTER, CENTER, 21, seed=61, wobble=0.22)
    shock_ring(p, CENTER, CENTER, 25, alpha=0.8)
    sparks_around(p, CENTER, CENTER, 6, 26, 3.6, seed=23, alpha=0.95)
    frames.append(p.out())

    # ۵: آوارهٔ کوچکِ پرتابی
    p = new_frame()
    fireball(p, FRAME, CENTER, CENTER, 20, seed=71, colors=FIRE, alpha=0.95)
    debris_burst(p, CENTER, CENTER, 5, 25, 3.4, seed=29, alpha=0.95)
    sparks_around(p, CENTER, CENTER, 4, 26, 2.8, seed=31, alpha=0.8, phase=0.6)
    frames.append(p.out())

    # ۶: کوچک شدن گلولهٔ آتش
    p = new_frame()
    fireball(p, FRAME, CENTER, CENTER, 14, seed=81, colors=FIRE_COOL, alpha=0.75, hot=False)
    smoke_puff(p, FRAME, CENTER, CENTER + 6, 8, seed=37, alpha=0.45)
    frames.append(p.out())

    # ۷: ابر دود خاکستری
    p = new_frame()
    smoke_puff(p, FRAME, CENTER, CENTER, 18, seed=91, alpha=0.85)
    frames.append(p.out())

    # ۸: محو شدن دود
    p = new_frame()
    smoke_puff(p, FRAME, CENTER, CENTER - 1, 22, seed=97, alpha=0.42)
    frames.append(p.out())
    return frames


def explosion_large():
    """۱۰ فریم: جرقه → رشد → انفجار عظیم → آوار → دود سیاه → محو."""
    frames = []
    # ۱: جرقه
    p = new_frame()
    hot_glow(p, CENTER, CENTER, 16, alpha=140, blur=4)
    spark(p, CENTER, CENTER, 11, seed=5, spikes=4)
    sparks_around(p, CENTER, CENTER, 5, 18, 3.2, seed=11, alpha=0.9)
    frames.append(p.out())

    # ۲ تا ۴: گلولهٔ آتش در حال رشد
    for i, r in enumerate([12, 17, 20]):
        p = new_frame()
        fireball(p, FRAME, CENTER, CENTER, r, seed=101 + i * 11,
                 colors=FIRE if i >= 1 else FIRE_COOL)
        sparks_around(p, CENTER, CENTER, 2 + i * 2, min(r + 6, 25), 3.2, seed=41 + i, alpha=0.9)
        frames.append(p.out())

    # ۵ و ۶: انفجار عظیم نارنجی و زرد
    for i, (r, ring) in enumerate([(23, 26), (22, 28)]):
        p = new_frame()
        fireball(p, FRAME, CENTER, CENTER, r, seed=131 + i * 13, wobble=0.26)
        shock_ring(p, CENTER, CENTER, ring, alpha=0.85 - 0.25 * i, width=2.8)
        sparks_around(p, CENTER, CENTER, 8, min(r + 3, 26), 3.4, seed=53 + i,
                      alpha=0.95 - 0.15 * i, phase=0.3 * i)
        frames.append(p.out())

    # ۷: آواره و جرقهٔ پرتابی
    p = new_frame()
    fireball(p, FRAME, CENTER, CENTER, 18, seed=151, colors=FIRE, alpha=0.9)
    debris_burst(p, CENTER, CENTER, 7, 25, 3.6, seed=59, alpha=0.95)
    sparks_around(p, CENTER, CENTER, 6, 26, 3.0, seed=61, alpha=0.85, phase=0.5)
    frames.append(p.out())

    # ۸: دود سیاه غلیظِ بلند شده
    p = new_frame()
    for i, (cy, r) in enumerate([(44, 14), (30, 12), (18, 9)]):
        smoke_puff(p, FRAME, CENTER + (i - 1) * 3, cy, r, seed=161 + i * 5,
                   colors=SMOKE_DARK, alpha=0.9 - 0.08 * i)
    for i in range(3):
        angle = -math.pi / 2 + (i - 1) * 0.7
        spark(p, CENTER + math.cos(angle) * 20, 44 + math.sin(angle) * 8, 2.6,
              color="#ffb347", outline=INK_FIRE, alpha=0.85, seed=67 + i)
    frames.append(p.out())

    # ۹: ابر دود
    p = new_frame()
    smoke_puff(p, FRAME, CENTER, CENTER - 2, 21, seed=171, colors=SMOKE_DARK, alpha=0.78)
    frames.append(p.out())

    # ۱۰: محو شدن دود
    p = new_frame()
    smoke_puff(p, FRAME, CENTER, CENTER - 1, 22, seed=179, colors=SMOKE_DARK, alpha=0.36)
    frames.append(p.out())
    return frames


def muzzle_flash():
    """۴ فریم: ستارهٔ زرد → انفجار شعلهٔ نارنجی → گسترش → محو."""
    frames = []
    # ۱: ستارهٔ زرد کوچک
    p = new_frame()
    hot_glow(p, CENTER, CENTER, 15, color=(255, 220, 140), alpha=140, blur=4)
    spark(p, CENTER, CENTER, 12, color="#ffe97a", outline=INK_FIRE, width=1.6, spikes=4, seed=3)
    p.circle(CENTER, CENTER, 4.0, fill=hexc("#fffbe6", 235))
    frames.append(p.out())

    # ۲: انفجار شعلهٔ نارنجی
    p = new_frame()
    hot_glow(p, CENTER, CENTER, 20, alpha=150, blur=5)
    p.poly(star_pts(CENTER, CENTER, 20, 11, 6, phase=0.2, seed=7, jitter=0.18),
           fill=hexc("#f5821f", 245), outline=hexc(INK_FIRE), width=2.0)
    p.poly(star_pts(CENTER, CENTER, 12, 6.5, 6, phase=0.2, seed=7, jitter=0.18),
           fill=hexc("#fdc22d", 250))
    p.circle(CENTER, CENTER, 5.5, fill=hexc("#fff6d6", 245))
    frames.append(p.out())

    # ۳: گسترش شعله
    p = new_frame()
    hot_glow(p, CENTER, CENTER, 22, alpha=150, blur=5)
    p.poly(star_pts(CENTER, CENTER, 23, 12, 6, phase=0.45, seed=13, jitter=0.22),
           fill=hexc("#e2601f", 245), outline=hexc(INK_FIRE), width=2.2)
    p.poly(star_pts(CENTER, CENTER, 15, 8, 6, phase=0.45, seed=13, jitter=0.22),
           fill=hexc("#f5821f", 250))
    p.poly(star_pts(CENTER, CENTER, 10, 5, 6, phase=0.45, seed=13, jitter=0.22),
           fill=hexc("#fdc22d", 250))
    p.circle(CENTER, CENTER, 5.0, fill=hexc("#fffbe6", 245))
    sparks_around(p, CENTER, CENTER, 4, 25, 3.0, seed=17, alpha=0.9, color="#fff3b0")
    frames.append(p.out())

    # ۴: محو شدن شعله
    p = new_frame()
    hot_glow(p, CENTER, CENTER, 21, alpha=80, blur=5)
    p.poly(star_pts(CENTER, CENTER, 21, 11, 6, phase=0.6, seed=13, jitter=0.22),
           fill=hexc("#e2601f", 110), outline=hexc(INK_FIRE, 110), width=2.0)
    p.poly(star_pts(CENTER, CENTER, 14, 7, 6, phase=0.6, seed=13, jitter=0.22),
           fill=hexc("#f79a3c", 110))
    p.circle(CENTER, CENTER, 4.0, fill=hexc("#fff3b0", 90))
    frames.append(p.out())
    return frames


def laser_beam_sheet():
    """۶ فریم: نقطهٔ آبی → رشد پرتو → پرتو کامل با جرقه → محو → ناپدید."""
    frames = []
    # ۱: نقطهٔ آبی درخشان
    p = new_frame()
    hot_glow(p, CENTER, CENTER, 14, color=(90, 200, 255), alpha=140, blur=4)
    spark(p, CENTER, CENTER, 9, color="#eaffff", outline=INK_LASER, width=1.4, spikes=4, seed=5)
    p.circle(CENTER, CENTER, 4.5, fill=hexc("#ffffff", 240))
    frames.append(p.out())

    # ۲ و ۳: پرتو آبی در حال رشد
    for i, length in enumerate([13, 23]):
        p = new_frame()
        laser_beam(p, FRAME, CENTER, CENTER, length, 4.2 + i * 0.8)
        frames.append(p.out())

    # ۴: پرتو کامل با جرقه
    p = new_frame()
    laser_beam(p, FRAME, CENTER, CENTER, 28, 5.5)
    p.circle(CENTER, CENTER, 7.0, fill=hexc("#ffffff", 235))
    for i in range(4):
        side = -1 if i % 2 == 0 else 1
        spark(p, CENTER + side * (20 + 5 * (i // 2)), CENTER + (5 - 10 * (i % 2)), 3.4,
              color="#eaffff", outline=INK_LASER, alpha=0.95, seed=23 + i, phase=i * 0.8)
    frames.append(p.out())

    # ۵: محو شدن پرتو
    p = new_frame()
    laser_beam(p, FRAME, CENTER, CENTER, 27, 3.6, alpha=0.5)
    frames.append(p.out())

    # ۶: ناپدید شدن جرقه‌ها
    p = new_frame()
    for i in range(5):
        spark(p, CENTER + (i - 2) * 9, CENTER + (4 - 8 * (i % 2)), 2.6 - 0.2 * abs(i - 2),
              color="#cdf3ff", outline=INK_LASER, alpha=0.6, seed=31 + i, phase=i)
    frames.append(p.out())
    return frames


def tesla_bolt():
    """۶ فریم: جرقه → قوس‌های بنفش → صاعقهٔ کامل → محو → ناپدید."""
    frames = []
    # ۱: جرقهٔ کوچک
    p = new_frame()
    hot_glow(p, CENTER, CENTER, 13, color=(170, 110, 255), alpha=130, blur=4)
    spark(p, CENTER, CENTER, 8, color="#f6ecff", outline=INK_TESLA, width=1.4, spikes=4, seed=9)
    frames.append(p.out())

    # ۲ و ۳: قوس‌های برقی در حال رشد
    for i, (displace, width) in enumerate([(7.0, 3.0), (9.0, 4.0)]):
        p = new_frame()
        hot_glow(p, CENTER, CENTER, 14 + i * 2, color=(150, 90, 255), alpha=70, blur=5)
        main = bolt_pts((CENTER, 9 - i * 1), (CENTER, 55 + i * 1), seed=101 + i,
                        displace=displace, detail=4)
        draw_bolt(p, main, width=width)
        for b in range(1 + i):
            start = main[len(main) // (3 + b)]
            end = (start[0] + (14 + b * 6) * (1 if b % 2 == 0 else -1), max(8.0, start[1] - 9 - b * 4))
            draw_bolt(p, bolt_pts(start, end, seed=211 + b, displace=5.0, detail=2),
                      width=width * 0.55)
        frames.append(p.out())

    # ۴: صاعقهٔ کامل با قوس‌ها
    p = new_frame()
    hot_glow(p, CENTER, CENTER, 16, color=(150, 90, 255), alpha=85, blur=5)
    main = bolt_pts((CENTER, 8), (CENTER, 56), seed=301, displace=10.0, detail=4)
    draw_bolt(p, main, width=3.6)
    for b in range(2):
        start = main[6 + b * 10]
        end = (start[0] + (16 + b * 5) * (1 if b % 2 == 0 else -1), max(7.0, start[1] - 11 - b * 4))
        draw_bolt(p, bolt_pts(start, end, seed=311 + b, displace=6.0, detail=2), width=2.2)
    sparks_around(p, CENTER, CENTER, 5, 24, 2.8, seed=321, alpha=0.95,
                  color="#f6ecff", outline=INK_TESLA)
    frames.append(p.out())

    # ۵: محو شدن صاعقه
    p = new_frame()
    hot_glow(p, CENTER, CENTER, 19, color=(150, 90, 255), alpha=60, blur=5)
    main = bolt_pts((CENTER, 9), (CENTER, 55), seed=301, displace=9.0, detail=4)
    draw_bolt(p, main, width=3.0, alpha=0.5)
    for b in range(2):
        start = main[6 + b * 10]
        end = (start[0] + (15 + b * 6) * (1 if b % 2 == 0 else -1), max(8.0, start[1] - 10 - b * 4))
        draw_bolt(p, bolt_pts(start, end, seed=311 + b, displace=5.0, detail=2),
                  width=2.0, alpha=0.45)
    frames.append(p.out())

    # ۶: ناپدید شدن جرقه‌ها
    p = new_frame()
    for i in range(5):
        angle = 2.0 * math.pi * i / 5 + 0.4
        spark(p, CENTER + math.cos(angle) * (13 + 3 * (i % 2)),
              CENTER + math.sin(angle) * (13 + 3 * (i % 2)), 2.8,
              color="#e6d4ff", outline=INK_TESLA, alpha=0.55, seed=331 + i, phase=angle)
    frames.append(p.out())
    return frames


def smoke_cloud():
    """۶ فریم: ابر دود کارتونی که از کوچک به بزرگ می‌رسد و آرام محو می‌شود."""
    radii = [9, 12, 15, 17, 20, 22]
    alphas = [1.0, 0.96, 0.9, 0.8, 0.6, 0.36]
    frames = []
    for i, (r, alpha) in enumerate(zip(radii, alphas)):
        p = new_frame()
        smoke_puff(p, FRAME, CENTER, CENTER + 4, r, seed=401 + i * 7,
                   alpha=alpha, rise=i * 1.2)
        # ذره‌های کوچکِ جدا شده برای سبک‌تر شدنِ لبهٔ ابر
        for j in range(2):
            angle = 1.2 + j * 2.6 + i * 0.5
            smoke_puff(p, FRAME, CENTER + math.cos(angle) * (r * 0.95),
                       CENTER + 4 + math.sin(angle) * (r * 0.7), r * 0.20,
                       seed=421 + i * 3 + j, alpha=alpha * 0.8)
        frames.append(p.out())
    return frames


def fire_flame():
    """۶ فریم: شعلهٔ کارتونی سوسوزن با لوپ نرم (آخر به اول می‌چسبد)."""
    frames = []
    for i in range(6):
        phase = 2.0 * math.pi * i / 6.0
        height = 44.0 * (1.0 + 0.05 * math.cos(phase + 0.6))
        lean = 4.5 * math.sin(phase)
        sway = 3.0 * math.sin(phase + 1.1)
        p = new_frame()
        flame(p, FRAME, CENTER, 58.0, height, 13.0, lean, sway, phase, seed=501 + i)
        # اخگرهایی که بالا می‌روند و در چرخه محو می‌شوند
        for k in range(2):
            t = ((i / 6.0) + k / 2.0) % 1.0
            spark(p, CENTER + lean * 1.4 + math.sin(phase + k * 2.0) * 3.5,
                  58.0 - height - 2.0 - t * 7.0, 2.4 * (1.0 - 0.4 * t), color="#ffd166",
                  outline=INK_FIRE, alpha=0.75 * (1.0 - t), seed=511 + i * 2 + k,
                  phase=phase + k)
        frames.append(p.out())
    return frames


SHEETS = [
    ("explosion_small", explosion_small),
    ("explosion_medium", explosion_medium),
    ("explosion_large", explosion_large),
    ("muzzle_flash", muzzle_flash),
    ("laser_beam", laser_beam_sheet),
    ("tesla_bolt", tesla_bolt),
    ("smoke_cloud", smoke_cloud),
    ("fire_flame", fire_flame),
]


def preview(path, sheets):
    """همهٔ شیت‌ها را روی زمینهٔ شطرنجی می‌چیند تا شفافی و ترتیب دیده شود."""
    scale = 2
    pad = 8
    cell = 8
    width = max(len(frames) for _, frames in sheets) * FRAME + 2 * pad
    height = len(sheets) * (FRAME + pad) + pad
    board = Image.new("RGBA", (width, height), (28, 30, 36, 255))
    grid = ImageDraw.Draw(board)
    for y in range(0, height, cell):
        for x in range(0, width, cell):
            if (x // cell + y // cell) % 2 == 0:
                grid.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(44, 47, 55, 255))
    for row, (_, frames) in enumerate(sheets):
        strip = sheet(frames)
        board.alpha_composite(strip, (pad, pad + row * (FRAME + pad)))
    board.resize((width * scale, height * scale), Image.NEAREST).save(path)
    print("پیش‌نمایش: %s" % path)


def main():
    built = []
    for name, builder in SHEETS:
        frames = builder()
        img = sheet(frames)
        path = save(img, "fx", name)
        built.append((name, frames))
        print("%-18s %2d فریم  %4dx%-4d  %s" % (name, len(frames), img.width, img.height, path))

    if "--preview" in sys.argv:
        index = sys.argv.index("--preview")
        target = sys.argv[index + 1] if len(sys.argv) > index + 1 else "/tmp/fx_sheets_preview.png"
        preview(target, built)


if __name__ == "__main__":
    main()
