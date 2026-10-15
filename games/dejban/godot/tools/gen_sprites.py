#!/usr/bin/env python3
"""مولد تصویرهای دژبان.

همهٔ اسپرایت‌ها (زمین، دکور، برجک‌ها، دشمنان، گلوله‌ها، جلوه‌ها، آیکون‌ها و
آیکون خود اپ) با Pillow ساخته می‌شوند تا هنر بازی داده‌محور و قابل‌جایگزینی باشد:

    python3 tools/gen_sprites.py

خروجی‌ها در assets/sprites/ و assets/icons/ و مسیر ریشه (icon_192.png و ...)
ساخته می‌شوند و سپس tools/gen_assets_manifest.py فهرست کلیدها را می‌نویسد.
"""

from __future__ import annotations

import math
import os
import random
import sys

try:
    from PIL import Image, ImageDraw, ImageFilter
except ImportError:  # pragma: no cover
    print("Pillow لازم است:  pip install pillow", file=sys.stderr)
    raise

SS = 4  # ضریب بزرگ‌نمایی برای نرم‌کردن لبه‌ها
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPRITES = os.path.join(ROOT, "assets", "sprites")
ICONS = os.path.join(ROOT, "assets", "icons")

# ------------------------------------------------------------------ رنگ‌ها

INK = (26, 24, 30, 255)
WOOD = "#8a6234"
WOOD_DARK = "#5d4021"
STEEL = "#98a2b0"
STEEL_DARK = "#5d6673"
IRON = "#3a4048"
GOLD = "#dcae4b"
RED = "#b4402f"
SKIN = "#c99b6f"
GREEN = "#4f8a52"
PURPLE = "#6a4f8a"
CLOTH = "#4a4438"


def lerp(a, b, k):
    return tuple(int(round(a[i] + (b[i] - a[i]) * k)) for i in range(len(a)))


def hexc(value, alpha: int = 255):
    """رشتهٔ «#rrggbb» یا چندگانهٔ رنگی را به چندگانهٔ RGBA تبدیل می‌کند."""
    if isinstance(value, tuple):
        if len(value) == 4:
            return value
        return (value[0], value[1], value[2], alpha)
    value = value.lstrip("#")
    return (int(value[0:2], 16), int(value[2:4], 16), int(value[4:6], 16), alpha)


def shade(color, k: float):
    """k>0 روشن‌تر، k<0 تیره‌تر."""
    if k >= 0:
        return lerp(color, (255, 255, 255, color[3]), k)
    return lerp(color, (10, 10, 14, color[3]), -k)


# ------------------------------------------------------------------ قلم


class P:
    """قلم برداری ساده روی بوم با ابرنمونه‌برداری برای لبه‌های نرم."""

    def __init__(self, w: int, h: int, supersample: int = SS):
        self.w, self.h, self.s = w, h, supersample
        self.img = Image.new("RGBA", (w * self.s, h * self.s), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)

    # --- تبدیل مختصات
    def _pts(self, pts):
        return [(x * self.s, y * self.s) for x, y in pts]

    def _box(self, box):
        return [box[0] * self.s, box[1] * self.s, box[2] * self.s, box[3] * self.s]

    def _w(self, width):
        return max(1, int(round(width * self.s)))

    # --- شکل‌ها
    def ellipse(self, box, fill=None, outline=None, width=1):
        self.d.ellipse(self._box(box), fill=fill, outline=outline, width=self._w(width))

    def circle(self, cx, cy, r, fill=None, outline=None, width=1, ry=None):
        ry = r if ry is None else ry
        self.ellipse([cx - r, cy - ry, cx + r, cy + ry], fill=fill, outline=outline, width=width)

    def rect(self, box, fill=None, outline=None, width=1, radius=0):
        b = self._box(box)
        if radius > 0:
            self.d.rounded_rectangle(b, radius=radius * self.s, fill=fill, outline=outline, width=self._w(width))
        else:
            self.d.rectangle(b, fill=fill, outline=outline, width=self._w(width))

    def poly(self, pts, fill=None, outline=None, width=1):
        self.d.polygon(self._pts(pts), fill=fill, outline=outline, width=self._w(width))

    def line(self, pts, fill=None, width=1):
        self.d.line(self._pts(pts), fill=fill, width=self._w(width), joint="curve")

    def arc(self, box, start, end, fill=None, width=1):
        self.d.arc(self._box(box), start=start, end=end, fill=fill, width=self._w(width))

    def blob(self, cx, cy, rx, ry, fill=None, outline=None, width=1, wobble=0.14, seed=1, points=26, phase=0.0):
        """شکل نامنظم و اندام‌وار برای ساقه، صخره و آبگیر."""
        rng = random.Random(seed)
        offsets = [1.0 + rng.uniform(-wobble, wobble) for _ in range(points)]
        pts = []
        for i in range(points):
            a = 2 * math.pi * i / points + phase
            k = offsets[i]
            pts.append((cx + math.cos(a) * rx * k, cy + math.sin(a) * ry * k))
        self.poly(pts, fill=fill, outline=outline, width=width)
        return pts

    # --- نورپردازی
    def vgrad(self, box, top, bottom):
        """سایه‌روشن عمودی روی شکل‌های کشیده‌شده."""
        x0, y0, x1, y1 = [int(v * self.s) for v in box]
        x0, y0 = max(0, x0), max(0, y0)
        x1, y1 = min(self.img.width, x1), min(self.img.height, y1)
        w, h = x1 - x0, y1 - y0
        if w <= 0 or h <= 0:
            return
        grad = Image.new("RGBA", (w, h))
        gd = ImageDraw.Draw(grad)
        for y in range(h):
            gd.line([(0, y), (w, y)], fill=lerp(top, bottom, y / max(1, h - 1)))
        region = self.img.crop((x0, y0, x1, y1))
        mask = region.getchannel("A")
        self.img.paste(Image.composite(grad, region, mask), (x0, y0), mask)

    def glow(self, cx, cy, r, color, alpha=140, blur=4):
        layer = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        ld.ellipse([(cx - r) * self.s, (cy - r) * self.s, (cx + r) * self.s, (cy + r) * self.s],
                   fill=(color[0], color[1], color[2], alpha))
        layer = layer.filter(ImageFilter.GaussianBlur(blur * self.s / 2))
        self.img.alpha_composite(layer)

    # --- خروجی
    def out(self):
        return self.img.resize((self.w, self.h), Image.LANCZOS)


def shadow_under(img, cx, cy, rx, ry, alpha=95, blur=5):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse(
        [(cx - rx) * SS, (cy - ry) * SS, (cx + rx) * SS, (cy + ry) * SS], fill=(8, 10, 12, alpha))
    layer = layer.filter(ImageFilter.GaussianBlur(blur * SS / 2))
    return Image.alpha_composite(layer, img)


def save(img, folder, name):
    target = os.path.join(SPRITES, folder)
    os.makedirs(target, exist_ok=True)
    path = os.path.join(target, name + ".png")
    img.save(path)
    return "sprites/%s/%s" % (folder, name)


def tile(folder, name, painter):
    save(painter.out(), folder, name)
    return "%s/%s" % (folder, name)


# ------------------------------------------------------------------ نویز

def value_noise(size, cells, seed, octaves=3):
    """نویز ارزشی پیوسته و دورپیچ تا کاشی‌ها بدون درز تکرار شوند."""
    rng = random.Random(seed)
    total = [[0.0] * size for _ in range(size)]
    amplitude = 1.0
    norm = 0.0
    for octave in range(octaves):
        n = max(2, cells * (2 ** octave))
        grid = [[rng.random() for _ in range(n)] for _ in range(n)]
        step = size / n
        for y in range(size):
            fy = y / step
            y0 = int(fy) % n
            y1 = (y0 + 1) % n
            ky = fy - int(fy)
            ky = ky * ky * (3 - 2 * ky)
            for x in range(size):
                fx = x / step
                x0 = int(fx) % n
                x1 = (x0 + 1) % n
                kx = fx - int(fx)
                kx = kx * kx * (3 - 2 * kx)
                top = grid[y0][x0] * (1 - kx) + grid[y0][x1] * kx
                bottom = grid[y1][x0] * (1 - kx) + grid[y1][x1] * kx
                total[y][x] += (top * (1 - ky) + bottom * ky) * amplitude
        norm += amplitude
        amplitude *= 0.5
    return [[v / norm for v in row] for row in total]


def ground_tile(name, dark, base, light, seed, blade_color=None, extra=None, size=64, blade_count=26):
    noise = value_noise(size, 4, seed, 3)
    noise2 = value_noise(size, 9, seed + 7, 2)
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            k = noise[y][x] * 0.72 + noise2[y][x] * 0.28
            if k < 0.42:
                col = lerp(dark, base, k / 0.42)
            else:
                col = lerp(base, light, (k - 0.42) / 0.58 * 0.75)
            px[x, y] = (col[0], col[1], col[2], 255)
    painter = P(size, size, supersample=1)
    painter.img = img.convert("RGBA")
    painter.d = ImageDraw.Draw(painter.img)
    if blade_color:
        rng = random.Random(seed + 33)
        for _ in range(blade_count):
            x = rng.uniform(1, size - 2)
            y = rng.uniform(2, size - 1)
            h = rng.uniform(2.0, 4.0)
            painter.line([(x, y), (x + rng.uniform(-1, 1), y - h)], fill=hexc(blade_color, 190), width=1)
    if extra:
        extra(painter, seed)
    save(painter.out(), "ground", name)
    return "ground/" + name


# ------------------------------------------------------------------ آسمان/پس‌زمینه (برای منو)


def backdrop_build():
    """آسمان شب‌گونهٔ منو با مه و ستاره."""
    painter = P(1280, 720, supersample=1)
    for y in range(720):
        k = y / 719
        if k < 0.55:
            col = lerp(hexc("#08161c"), hexc("#0e2b31"), k / 0.55)
        else:
            col = lerp(hexc("#0e2b31"), hexc("#16342d"), (k - 0.55) / 0.45)
        painter.d.line([(0, y), (1280, y)], fill=col)
    rng = random.Random(9)
    for _ in range(140):
        x, y = rng.uniform(0, 1280), rng.uniform(0, 360)
        r = rng.choice([0.6, 0.8, 1.0])
        painter.circle(x, y, r, fill=(220, 235, 240, rng.randint(60, 140)))
    moon = painter.out()
    layer = Image.new("RGBA", moon.size, (0, 0, 0, 0))
    md = ImageDraw.Draw(layer)
    md.ellipse([1030, 70, 1150, 190], fill=(230, 238, 226, 235))
    md.ellipse([1042, 66, 1178, 186], fill=(0, 0, 0, 0))
    layer = layer.filter(ImageFilter.GaussianBlur(1.2))
    out = Image.alpha_composite(moon, layer)
    os.makedirs(os.path.join(SPRITES, "ui"), exist_ok=True)
    out.save(os.path.join(SPRITES, "ui", "sky.png"))


# ------------------------------------------------------------------ دکور


def prop_tree_pine(seed=1):
    painter = P(96, 124)
    trunk = WOOD_DARK
    painter.poly([(44, 116), (52, 116), (54, 86), (42, 86)], fill=hexc(trunk))
    layers = [(74, 34), (58, 30), (40, 26), (22, 22)]
    for i, (y, w) in enumerate(layers):
        color = hexc(lerp(hexc("#2f6b46"), hexc("#1d4a31"), i / 3.0))
        painter.poly([(48 - w, y), (48 + w, y), (48, y - 34 + i * 2)], fill=INK)
        painter.poly([(48 - w + 3, y - 2), (48 + w - 3, y - 2), (48, y - 31 + i * 2)],
                     fill=hexc(lerp(hexc("#377a52"), hexc("#235637"), i / 3.0)))
        painter.poly([(48 - w + 6, y - 3), (48 + 2, y - 3), (48 - 4, y - 24 + i * 2)], fill=hexc("#48916a", 120))
    return shadow_under(painter.out(), 50, 116, 30, 8)


def prop_tree_round(seed=2):
    painter = P(104, 122)
    painter.poly([(46, 118), (58, 118), (56, 84), (48, 84)], fill=hexc(WOOD_DARK))
    painter.poly([(48, 112), (54, 100), (50, 90)], fill=hexc(WOOD_DARK, 150))
    rng = random.Random(seed)
    blobs = [(52, 56, 36, 30), (30, 66, 24, 20), (74, 68, 24, 19), (52, 40, 26, 20)]
    for i, (cx, cy, rx, ry) in enumerate(blobs):
        painter.blob(cx, cy, rx + 3, ry + 3, fill=INK, seed=seed + i, wobble=0.16)
    for i, (cx, cy, rx, ry) in enumerate(blobs):
        color = hexc("#3d7d4f" if i % 2 == 0 else "#356e45")
        painter.blob(cx, cy, rx, ry, fill=color, seed=seed + i, wobble=0.16)
    for i, (cx, cy, rx, ry) in enumerate(blobs):
        pts = painter.blob(cx - rx * 0.2, cy - ry * 0.35, rx * 0.55, ry * 0.4, fill=hexc("#59a06b", 150),
                           seed=seed + 40 + i, wobble=0.2)
    return shadow_under(painter.out(), 52, 116, 34, 8)


def prop_tree_palm(seed=3):
    painter = P(104, 130)
    painter.line([(50, 124), (56, 66)], fill=INK, width=9)
    painter.line([(50, 124), (56, 66)], fill=hexc("#7d5a30"), width=6)
    for i in range(6):
        a = -math.pi * 0.92 + i * (math.pi * 0.95 / 5)
        x1 = 56 + math.cos(a) * 40
        y1 = 60 + math.sin(a) * 26
        painter.line([(56, 62), (x1, y1)], fill=INK, width=6)
        painter.line([(56, 62), (x1, y1)], fill=hexc("#3d7d4f"), width=3.5)
    painter.circle(56, 60, 6, fill=hexc("#2f6b46"))
    return shadow_under(painter.out(), 52, 122, 26, 7)


def prop_rock_big(seed=4):
    painter = P(96, 76)
    painter.blob(48, 44, 34, 26, fill=INK, seed=seed, wobble=0.18)
    painter.blob(48, 42, 31, 23, fill=hexc("#6d747e"), seed=seed, wobble=0.18)
    painter.blob(42, 34, 16, 11, fill=hexc("#8b939e", 200), seed=seed + 1, wobble=0.22)
    painter.line([(30, 56), (44, 60), (66, 54)], fill=hexc("#4d545d"), width=2)
    return shadow_under(painter.out(), 48, 66, 32, 8)


def prop_rock_small(seed=5):
    painter = P(56, 46)
    painter.blob(28, 26, 19, 14, fill=INK, seed=seed, wobble=0.2)
    painter.blob(28, 25, 16, 11, fill=hexc("#7b838d"), seed=seed, wobble=0.2)
    painter.blob(24, 20, 7, 5, fill=hexc("#9aa2ad", 190), seed=seed + 3, wobble=0.25)
    return shadow_under(painter.out(), 28, 38, 18, 5)


def prop_bush(seed=6):
    painter = P(64, 46)
    painter.blob(28, 26, 20, 14, fill=INK, seed=seed, wobble=0.2)
    painter.blob(28, 25, 17, 11, fill=hexc("#3c7a4c"), seed=seed, wobble=0.2)
    painter.blob(22, 20, 9, 6, fill=hexc("#569b64", 170), seed=seed + 5, wobble=0.25)
    painter.circle(44, 24, 3, fill=hexc("#c9563f"))
    painter.circle(50, 28, 2.4, fill=hexc("#d86a4a"))
    return shadow_under(painter.out(), 28, 38, 18, 5)


def prop_flower(seed=7, color="#e3b23c"):
    painter = P(28, 30)
    painter.line([(14, 28), (14, 16)], fill=hexc("#3f7a4a"), width=2)
    painter.circle(14, 12, 5.5, fill=INK)
    painter.circle(14, 12, 4.2, fill=hexc(color))
    painter.circle(14, 12, 1.8, fill=hexc("#f6e6b0"))
    painter.circle(8.5, 15, 3, fill=hexc(color, 235))
    painter.circle(19.5, 15, 3, fill=hexc(color, 235))
    return painter.out()


def prop_barrel(seed=8):
    painter = P(40, 50)
    painter.rect([8, 8, 32, 44], fill=INK, radius=6)
    painter.rect([10, 10, 30, 42], fill=hexc("#8a6234"), radius=5)
    painter.vgrad([10, 10, 30, 42], hexc("#a97a42"), hexc("#5d4021"))
    painter.rect([10, 14, 30, 18], fill=hexc("#4a5563"), radius=2)
    painter.rect([10, 34, 30, 38], fill=hexc("#4a5563"), radius=2)
    painter.ellipse([12, 6, 28, 14], fill=hexc("#a97a42"), outline=INK, width=1.4)
    return shadow_under(painter.out(), 20, 46, 14, 4)


def prop_crate(seed=9):
    painter = P(44, 46)
    painter.rect([4, 8, 40, 42], fill=INK, radius=3)
    painter.rect([6, 10, 38, 40], fill=hexc("#8a6234"), radius=2)
    painter.vgrad([6, 10, 38, 40], hexc("#a37a44"), hexc("#66492a"))
    painter.line([(6, 10), (38, 40)], fill=hexc("#5d4021"), width=2)
    painter.line([(38, 10), (6, 40)], fill=hexc("#5d4021"), width=2)
    painter.rect([6, 10, 38, 14], fill=hexc("#b98a4c", 150))
    return shadow_under(painter.out(), 22, 42, 16, 4)


def prop_ruins(seed=10):
    painter = P(88, 76)
    painter.rect([14, 30, 34, 70], fill=INK, radius=3)
    painter.rect([16, 32, 32, 68], fill=hexc("#98a2b0"), radius=2)
    painter.vgrad([16, 32, 32, 68], hexc("#aab2bd"), hexc("#6d747e"))
    painter.rect([52, 44, 72, 70], fill=INK, radius=3)
    painter.rect([54, 46, 70, 68], fill=hexc("#8f99a6"), radius=2)
    painter.rect([24, 20, 62, 30], fill=INK, radius=2)
    painter.rect([26, 22, 60, 28], fill=hexc("#a7afba"), radius=1.5)
    painter.line([(34, 32), (32, 46)], fill=hexc("#767e88"), width=1.4)
    painter.line([(62, 46), (60, 60)], fill=hexc("#767e88"), width=1.4)
    return shadow_under(painter.out(), 44, 70, 32, 7)


def prop_statue(seed=11):
    painter = P(72, 100)
    painter.rect([10, 80, 62, 96], fill=INK, radius=4)
    painter.rect([12, 82, 60, 94], fill=hexc("#8f99a6"), radius=3)
    painter.rect([26, 72, 46, 84], fill=hexc("#767e88"), radius=3)
    painter.poly([(36, 24), (50, 74), (22, 74)], fill=INK)
    painter.poly([(36, 28), (47, 72), (25, 72)], fill=hexc("#a7afba"))
    painter.circle(36, 20, 10, fill=INK)
    painter.circle(36, 20, 8, fill=hexc("#b3bac4"))
    painter.line([(30, 20), (42, 20)], fill=hexc("#7c848f"), width=1.6)
    painter.line([(36, 30), (36, 70)], fill=hexc("#8f99a6"), width=1.6)
    return shadow_under(painter.out(), 36, 94, 28, 6)


def prop_tent(seed=12):
    painter = P(96, 82)
    painter.poly([(48, 8), (92, 74), (4, 74)], fill=INK)
    painter.poly([(48, 14), (86, 71), (10, 71)], fill=hexc("#8d6b46"))
    painter.vgrad([4, 8, 92, 74], hexc("#a8834f"), hexc("#5f482c"))
    painter.poly([(48, 14), (48, 71), (10, 71)], fill=hexc("#6f5335", 200))
    painter.poly([(48, 30), (62, 71), (34, 71)], fill=hexc("#2c2620"))
    painter.line([(48, 8), (48, 2)], fill=hexc("#4a4438"), width=2)
    return shadow_under(painter.out(), 48, 76, 40, 7)


def prop_pond(seed=13):
    painter = P(128, 96)
    painter.blob(64, 50, 56, 36, fill=(30, 60, 70, 150), seed=seed, wobble=0.12)
    painter.blob(64, 48, 52, 33, fill=hexc("#2f6f80"), seed=seed, wobble=0.12)
    painter.blob(64, 46, 46, 28, fill=hexc("#3a86a0"), seed=seed + 1, wobble=0.14)
    rng = random.Random(seed)
    for _ in range(7):
        x = rng.uniform(30, 98)
        y = rng.uniform(28, 66)
        painter.line([(x, y), (x + rng.uniform(6, 16), y + rng.uniform(-2, 2))], fill=hexc("#bfe3ea", 130), width=1.4)
    painter.blob(50, 40, 18, 9, fill=hexc("#9fd6e2", 70), seed=seed + 6, wobble=0.2)
    return painter.out()


def prop_cactus(seed=14):
    painter = P(56, 92)
    painter.rect([24, 18, 36, 88], fill=INK, radius=6)
    painter.rect([26, 20, 34, 86], fill=hexc("#4f8a52"), radius=5)
    painter.vgrad([26, 18, 36, 88], hexc("#67a76a"), hexc("#356b3c"))
    painter.rect([10, 44, 26, 58], fill=INK, radius=5)
    painter.rect([12, 46, 24, 56], fill=hexc("#4f8a52"), radius=4)
    painter.rect([34, 34, 50, 48], fill=INK, radius=5)
    painter.rect([36, 36, 48, 46], fill=hexc("#4f8a52"), radius=4)
    for i in range(5):
        painter.line([(31, 26 + i * 12), (31, 30 + i * 12)], fill=hexc("#2f6b46", 180), width=1.2)
    return shadow_under(painter.out(), 28, 86, 18, 5)


def prop_grass_tuft(seed=15):
    painter = P(56, 42)
    rng = random.Random(seed)
    for _ in range(9):
        x = rng.uniform(10, 46)
        h = rng.uniform(8, 20)
        painter.line([(x, 38), (x + rng.uniform(-4, 4), 38 - h)], fill=hexc("#3f7a4a", 220), width=2)
    return painter.out()


def prop_torch(seed=16):
    painter = P(30, 62)
    painter.rect([12, 26, 18, 58], fill=INK, radius=2)
    painter.rect([13, 28, 17, 56], fill=hexc("#6b4f2e"), radius=2)
    painter.poly([(8, 22), (22, 22), (19, 28), (11, 28)], fill=INK)
    painter.poly([(9, 23), (21, 23), (18, 27), (12, 27)], fill=hexc("#4a5563"))
    painter.glow(15, 16, 12, hexc("#ffb347")[:3], alpha=120, blur=5)
    return painter.out()


def flame_frames():
    frames = []
    for i in range(4):
        painter = P(24, 34)
        h = [26, 30, 24, 28][i]
        w = [8, 7, 9, 8][i]
        painter.glow(12, 20, 10, (255, 170, 70), alpha=110, blur=4)
        painter.blob(12, 34 - h * 0.5, w * 0.7, h * 0.55, fill=hexc("#e2601f", 235), seed=i + 1, wobble=0.22,
                     points=14, phase=i * 0.5)
        painter.blob(12, 34 - h * 0.5 + 2, w * 0.42, h * 0.34, fill=hexc("#ffb347"), seed=i + 9, wobble=0.25,
                     points=12, phase=i * 0.7)
        painter.blob(12, 30 - h * 0.28, w * 0.22, h * 0.18, fill=hexc("#fff0c2"), seed=i + 20, wobble=0.2, points=9)
        frames.append(painter.out())
    return frames


# ------------------------------------------------------------------ ساختارها


def structure_keep():
    painter = P(176, 156)
    base = hexc("#7c848f")
    painter.rect([10, 96, 166, 150], fill=INK, radius=6)
    painter.rect([14, 100, 162, 146], fill=base, radius=5)
    painter.vgrad([14, 96, 162, 150], hexc("#9aa2ad"), hexc("#5d6673"))
    # برج‌های کناری
    for x0 in (6, 132):
        painter.rect([x0, 66, x0 + 38, 150], fill=INK, radius=6)
        painter.rect([x0 + 3, 69, x0 + 35, 147], fill=hexc("#8a939e"), radius=5)
        painter.vgrad([x0 + 3, 66, x0 + 38, 150], hexc("#a7afba"), hexc("#616a76"))
        for k in range(3):
            painter.rect([x0 + 2 + k * 13, 58, x0 + 10 + k * 13, 70], fill=INK, radius=2)
            painter.rect([x0 + 3 + k * 13, 59, x0 + 9 + k * 13, 69], fill=hexc("#98a2b0"), radius=2)
        painter.rect([x0 + 12, 96, x0 + 26, 112], fill=hexc("#2a3038"), radius=3)
    # دروازه
    painter.rect([68, 96, 108, 146], fill=hexc("#3a2f22"), radius=4)
    painter.rect([72, 100, 104, 146], fill=hexc("#5d4021"), radius=3)
    painter.arc([70, 100, 106, 150], 180, 360, fill=INK, width=3)
    painter.line([(74, 122), (102, 122)], fill=hexc("#3a2f22"), width=2)
    for x in (78, 84, 92, 98):
        painter.line([(x, 100), (x, 146)], fill=hexc("#4a341c"), width=1.4)
    # پرچم
    painter.line([(88, 96), (88, 40)], fill=INK, width=3)
    painter.poly([(88, 40), (128, 50), (88, 62)], fill=INK)
    painter.poly([(89, 42), (124, 50), (89, 60)], fill=hexc("#b4402f"))
    painter.circle(88, 38, 4, fill=hexc(GOLD))
    # جان‌پناه
    for k in range(9):
        painter.rect([14 + k * 17, 88, 26 + k * 17, 100], fill=INK, radius=1.5)
        painter.rect([15 + k * 17, 89, 25 + k * 17, 99], fill=hexc("#98a2b0"), radius=1.5)
    # پنجره‌ها
    for x, y in ((26, 118), (146, 118), (86, 76)):
        painter.rect([x - 5, y - 7, x + 5, y + 7], fill=hexc("#2a3038"), radius=3)
        painter.circle(x, y, 3, fill=hexc("#ffcf70", 220))
    return shadow_under(painter.out(), 88, 150, 74, 9)


def structure_gate():
    painter = P(96, 104)
    painter.rect([8, 26, 88, 100], fill=INK, radius=6)
    painter.rect([11, 29, 85, 98], fill=hexc("#6d747e"), radius=5)
    painter.vgrad([11, 26, 85, 100], hexc("#8f99a6"), hexc("#4d545d"))
    for k in range(5):
        painter.rect([9 + k * 16, 16, 22 + k * 16, 30], fill=INK, radius=2)
        painter.rect([10 + k * 16, 17, 21 + k * 16, 29], fill=hexc("#98a2b0"), radius=2)
    painter.rect([34, 44, 62, 98], fill=hexc("#241d17"), radius=8)
    painter.arc([34, 44, 62, 120], 180, 360, fill=INK, width=3)
    painter.circle(48, 70, 4, fill=hexc("#6d747e"))
    painter.line([(48, 74), (48, 98)], fill=hexc("#6d747e"), width=3)
    return shadow_under(painter.out(), 48, 98, 40, 7)


# ------------------------------------------------------------------ برجک‌ها


def tower_base():
    painter = P(60, 60)
    painter.circle(30, 31, 26, fill=INK)
    painter.circle(30, 30, 23, fill=hexc("#8a939e"))
    painter.vgrad([7, 7, 53, 53], hexc("#aab2bd"), hexc("#616a76"))
    painter.circle(30, 30, 17, fill=hexc("#767e88"))
    painter.circle(30, 28, 14, fill=hexc("#9aa2ad"))
    for i in range(8):
        a = math.pi * 2 * i / 8
        painter.circle(30 + math.cos(a) * 21, 30 + math.sin(a) * 21, 3.4, fill=hexc("#6d747e"))
    painter.circle(30, 26, 9, fill=hexc("#b3bac4"))
    return shadow_under(painter.out(), 30, 42, 22, 8)


def head_chassis(painter, cx=24, cy=26, color=STEEL):
    """بدنهٔ مشترک همهٔ سرهای برجک (رو به راست)."""
    painter.circle(cx, cy, 12.5, fill=INK)
    painter.circle(cx, cy, 11, fill=hexc(color))
    painter.vgrad([cx - 13, cy - 13, cx + 13, cy + 13], hexc("#b3bac4"), hexc("#616a76"))
    painter.circle(cx, cy, 5.4, fill=hexc("#5d6673"))
    painter.circle(cx - 1, cy - 1, 3.4, fill=hexc("#8f99a6"))


def head_bow():
    painter = P(52, 52)
    head_chassis(painter, 22)
    painter.arc([16, 6, 52, 46], -70, 70, fill=INK, width=5)
    painter.arc([16, 6, 52, 46], -70, 70, fill=hexc(WOOD), width=3)
    painter.line([(30, 8), (30, 44)], fill=hexc("#ddd2b8"), width=1.2)
    painter.poly([(18, 24), (44, 26), (18, 30)], fill=hexc("#cfd6dd"))
    return painter.out()


def head_spear():
    painter = P(50, 48)
    head_chassis(painter, 20, 24, STEEL_DARK)
    painter.poly([(14, 20), (44, 24), (14, 30)], fill=INK)
    painter.poly([(15, 22), (41, 24), (15, 28)], fill=hexc(WOOD))
    painter.poly([(38, 20), (50, 24), (38, 29)], fill=INK)
    painter.poly([(39, 21.5), (48, 24), (39, 27.5)], fill=hexc("#dfe4e9"))
    return painter.out()


def head_catapult():
    painter = P(60, 54)
    head_chassis(painter, 24, 30, WOOD_DARK)
    painter.line([(12, 40), (46, 16)], fill=INK, width=6)
    painter.line([(12, 40), (46, 16)], fill=hexc(WOOD), width=3.6)
    painter.poly([(38, 8), (54, 16), (42, 24)], fill=INK)
    painter.poly([(40, 11), (51, 16), (42, 21)], fill=hexc("#7b838d"))
    painter.circle(46, 16, 6.5, fill=INK)
    painter.circle(46, 16, 5, fill=hexc("#8f99a6"))
    return painter.out()


def head_longbow():
    painter = P(58, 52)
    head_chassis(painter, 20, 26, WOOD_DARK)
    painter.arc([8, 2, 62, 50], -72, 72, fill=INK, width=5)
    painter.arc([8, 2, 62, 50], -72, 72, fill=hexc("#a97a42"), width=3)
    painter.line([(34, 5), (34, 47)], fill=hexc("#e8dfc8"), width=1.1)
    painter.poly([(16, 24), (52, 26), (16, 32)], fill=hexc("#e3e8ee"))
    return painter.out()


def head_zubin():
    painter = P(60, 50)
    head_chassis(painter, 20, 25, STEEL_DARK)
    painter.rect([10, 20, 52, 30], fill=INK, radius=3)
    painter.rect([12, 22, 50, 28], fill=hexc("#6d747e"), radius=2)
    painter.poly([(44, 17), (60, 25), (44, 33)], fill=INK)
    painter.poly([(45, 19), (57, 25), (45, 31)], fill=hexc("#dfe4e9"))
    painter.rect([28, 14, 38, 36], fill=hexc(GOLD, 200), radius=2)
    return painter.out()


def weapon_firepot():
    painter = P(54, 54)
    head_chassis(painter, 22, 28, IRON)
    painter.rect([14, 14, 40, 36], fill=INK, radius=6)
    painter.rect([16, 16, 38, 34], fill=hexc("#4a5563"), radius=5)
    painter.glow(34, 24, 12, (255, 160, 60), alpha=150, blur=6)
    painter.blob(34, 24, 9, 8, fill=hexc("#e2601f"), seed=3, wobble=0.3, points=14)
    painter.blob(34, 22, 5, 4.5, fill=hexc("#ffd27a"), seed=8, wobble=0.3, points=12)
    return painter.out()


def head_frost():
    painter = P(50, 56)
    head_chassis(painter, 22, 30, "#7c93a8")
    painter.poly([(22, 8), (36, 26), (22, 40), (10, 26)], fill=INK)
    painter.poly([(22, 11), (33, 26), (22, 37), (13, 26)], fill=hexc("#9fd6e2"))
    painter.poly([(22, 15), (28, 26), (22, 33), (17, 26)], fill=hexc("#d8f4f8"))
    painter.glow(24, 24, 12, (150, 220, 235), alpha=90, blur=5)
    return painter.out()


def head_thunder():
    painter = P(50, 54)
    head_chassis(painter, 22, 28, "#6a6f8a")
    for r in (13, 9):
        painter.arc([22 - r, 26 - r, 22 + r, 26 + r], 0, 360, fill=hexc("#c9c2e8", 200), width=2)
    painter.circle(24, 24, 7, fill=INK)
    painter.circle(24, 24, 5.4, fill=hexc("#f4e79a"))
    painter.line([(40, 14), (34, 22), (38, 26), (30, 34)], fill=hexc("#fff3b0"), width=2.2)
    painter.glow(24, 24, 12, (255, 240, 170), alpha=120, blur=5)
    return painter.out()


def head_venom():
    painter = P(56, 50)
    head_chassis(painter, 20, 26, "#4f6b45")
    painter.rect([10, 16, 30, 38], fill=INK, radius=5)
    painter.rect([12, 18, 28, 36], fill=hexc("#3f6b3a"), radius=4)
    painter.circle(20, 27, 5.5, fill=hexc("#8fd06a"))
    painter.poly([(28, 22), (50, 26), (28, 32)], fill=INK)
    painter.poly([(29, 24), (47, 26), (29, 30)], fill=hexc("#6f9aa0"))
    painter.circle(48, 26, 3, fill=hexc("#9be07a"))
    return painter.out()


def head_falcon():
    painter = P(54, 52)
    head_chassis(painter, 20, 28, WOOD_DARK)
    painter.line([(10, 34), (46, 34)], fill=INK, width=5)
    painter.line([(11, 33), (45, 33)], fill=hexc("#8a6234"), width=3)
    painter.blob(38, 22, 9, 7, fill=hexc("#6b5b3e"), seed=2, wobble=0.2, points=16)
    painter.circle(45, 18, 4, fill=hexc("#8a7850"))
    painter.poly([(48, 18), (54, 20), (48, 21)], fill=hexc(GOLD))
    painter.circle(46, 16.5, 1.2, fill=INK)
    return painter.out()


def head_breaker():
    painter = P(60, 52)
    head_chassis(painter, 20, 26, "#5d6673")
    painter.rect([8, 18, 50, 34], fill=INK, radius=4)
    painter.rect([10, 20, 48, 32], fill=hexc("#8a939e"), radius=3)
    painter.poly([(44, 18), (60, 26), (44, 34)], fill=INK)
    painter.poly([(45, 20), (57, 26), (45, 32)], fill=hexc("#c9d1d8"))
    painter.rect([26, 14, 34, 38], fill=hexc("#3a4048"), radius=2)
    return painter.out()


def head_sniper():
    painter = P(68, 46)
    head_chassis(painter, 20, 24, "#4a5563")
    painter.rect([8, 19, 56, 29], fill=INK, radius=3)
    painter.rect([10, 20.5, 54, 27.5], fill=hexc("#b08a4a"), radius=2)
    painter.rect([26, 24, 40, 34], fill=INK, radius=3)
    painter.rect([28, 25.5, 38, 32.5], fill=hexc("#6d747e"), radius=2)
    painter.circle(34, 29, 3, fill=hexc("#9be0ea"))
    painter.poly([(54, 16), (68, 24), (54, 32)], fill=INK)
    painter.poly([(55, 18), (65, 24), (55, 30)], fill=hexc("#e8eef2"))
    return painter.out()


def head_firecat():
    painter = P(62, 56)
    head_chassis(painter, 24, 30, "#5d4021")
    painter.line([(10, 42), (48, 14)], fill=INK, width=7)
    painter.line([(10, 42), (48, 14)], fill=hexc("#7a5a2e"), width=4)
    painter.glow(48, 18, 14, (255, 150, 60), alpha=150, blur=6)
    painter.blob(48, 18, 10, 9, fill=hexc("#e2601f"), seed=5, wobble=0.28, points=15)
    painter.blob(48, 16, 5.5, 5, fill=hexc("#ffd27a"), seed=9, wobble=0.3, points=12)
    painter.circle(20, 34, 7, fill=INK)
    painter.circle(20, 34, 5.5, fill=hexc("#8a939e"))
    return painter.out()


def head_scythe():
    painter = P(60, 58)
    head_chassis(painter, 20, 29, "#4a4438")
    painter.arc([16, 8, 56, 50], -80, 80, fill=hexc("#98a2b0", 220), width=2)
    for i, a in enumerate((-52, 0, 52)):
        ra = math.radians(a)
        cx = 30 + math.cos(ra) * 17
        cy = 29 + math.sin(ra) * 17
        painter.line([(cx, cy), (cx + math.cos(ra) * 14, cy + math.sin(ra) * 14)], fill=INK, width=5)
        painter.line([(cx, cy), (cx + math.cos(ra) * 12, cy + math.sin(ra) * 12)], fill=hexc("#cfd6dd"), width=2.6)
    return painter.out()


def head_volley():
    painter = P(60, 52)
    head_chassis(painter, 22, 26, IRON)
    for k in range(4):
        y = 12 + k * 8
        painter.rect([18, y, 54, y + 7], fill=INK, radius=3)
        painter.rect([20, y + 1, 52, y + 6], fill=hexc(k % 2 and "#6d747e" or "#5d6673"), radius=2.5)
        painter.circle(52, y + 3.5, 2.6, fill=hexc("#2a3038"))
    return painter.out()


def head_triplet():
    painter = P(58, 54)
    head_chassis(painter, 20, 27, WOOD_DARK)
    for k, dy in enumerate((-10, 0, 10)):
        painter.arc([14, 12 + dy, 54, 40 + dy], -52, 52, fill=INK, width=4)
        painter.arc([14, 12 + dy, 54, 40 + dy], -52, 52, fill=hexc("#a97a42"), width=2.4)
        painter.line([(30 + dy * 0.2, 10 + dy), (30 + dy * 0.2, 44 + dy)], fill=hexc("#e8dfc8", 200), width=0.9)
    return painter.out()


def head_pyre():
    painter = P(56, 60)
    head_chassis(painter, 22, 34, "#5d4021")
    painter.poly([(6, 24), (40, 24), (36, 40), (10, 40)], fill=INK)
    painter.poly([(9, 26), (37, 26), (34, 38), (12, 38)], fill=hexc("#4a5563"))
    painter.glow(24, 16, 16, (255, 150, 60), alpha=160, blur=7)
    painter.blob(24, 14, 12, 12, fill=hexc("#e2601f"), seed=4, wobble=0.3, points=16)
    painter.blob(24, 12, 7, 7, fill=hexc("#ffb347"), seed=7, wobble=0.3, points=14)
    painter.blob(24, 10, 3.5, 3.5, fill=hexc("#fff0c2"), seed=11, wobble=0.25, points=10)
    return painter.out()


def head_eye():
    painter = P(58, 58)
    head_chassis(painter, 22, 30, "#3f5b6b")
    painter.glow(34, 26, 16, (120, 220, 240), alpha=140, blur=7)
    painter.circle(34, 26, 12, fill=INK)
    painter.circle(34, 26, 10.5, fill=hexc("#bfeef5"))
    painter.circle(34, 26, 5.4, fill=hexc("#3f6b7a"))
    painter.circle(34, 26, 2.4, fill=INK)
    painter.circle(31.5, 23.5, 1.8, fill=(255, 255, 255, 220))
    painter.arc([24, 14, 46, 40], 200, 340, fill=hexc("#6d747e"), width=3)
    return painter.out()


def muzzle_flash():
    frames = []
    for i in range(2):
        painter = P(34, 34)
        r = 14 - i * 4
        painter.glow(17, 17, r, (255, 220, 140), alpha=150, blur=5)
        painter.blob(17, 17, r * 0.8, r * 0.55, fill=hexc("#ffe6a8", 230), seed=i + 1, wobble=0.35, points=12)
        painter.circle(17, 17, r * 0.3, fill=(255, 255, 255, 240))
        frames.append(painter.out())
    return frames


# ------------------------------------------------------------------ دشمنان


def unit_body(painter, cx, cy, w, h, color, helmet=None, cape=None, outline=True):
    if outline:
        painter.ellipse([cx - w - 1.5, cy - h - 1.5, cx + w + 1.5, cy + h + 1.5], fill=INK)
    painter.ellipse([cx - w, cy - h, cx + w, cy + h], fill=hexc(color))
    painter.vgrad([cx - w, cy - h, cx + w, cy + h], shade(hexc(color), 0.18), shade(hexc(color), -0.22))
    if cape:
        painter.poly([(cx - w * 0.7, cy - h * 0.5), (cx - w * 1.5, cy + h * 0.9), (cx + w * 0.4, cy + h * 0.7)],
                     fill=hexc(cape, 235))
    if helmet:
        painter.ellipse([cx - w * 0.75, cy - h * 1.25, cx + w * 0.85, cy - h * 0.1], fill=INK)
        painter.ellipse([cx - w * 0.7, cy - h * 1.2, cx + w * 0.8, cy - h * 0.15], fill=hexc(helmet))
        painter.line([(cx + w * 0.15, cy - h * 1.1), (cx + w * 0.15, cy - h * 0.2)], fill=hexc("#2a3038"), width=1.6)


def unit_grunt():
    painter = P(44, 46)
    unit_body(painter, 21, 26, 10, 11, "#6b5b3e", helmet="#8a939e")
    painter.line([(21, 22), (40, 16)], fill=INK, width=5)
    painter.line([(21, 22), (39, 16)], fill=hexc(WOOD), width=3)
    painter.poly([(38, 12), (46, 16), (38, 20)], fill=hexc("#dfe4e9"))
    painter.circle(21, 22, 3.4, fill=hexc(SKIN))
    return shadow_under(painter.out(), 21, 38, 12, 4)


def unit_runner():
    painter = P(44, 46)
    painter.poly([(10, 34), (30, 18), (34, 26), (14, 40)], fill=INK)
    unit_body(painter, 22, 27, 8.5, 9.5, "#3f4a52", cape="#2f3a42")
    painter.arc([12, 12, 30, 26], 200, 340, fill=hexc("#2a3038"), width=4)
    painter.poly([(28, 22), (44, 18), (30, 27)], fill=INK)
    painter.poly([(29, 23), (42, 19), (31, 26)], fill=hexc("#c9d1d8"))
    return shadow_under(painter.out(), 22, 39, 12, 4)


def unit_armored():
    painter = P(48, 48)
    unit_body(painter, 23, 27, 12, 12, "#8f99a6", helmet="#aab2bd")
    painter.poly([(9, 20), (20, 16), (22, 34), (10, 32)], fill=INK)
    painter.poly([(11, 21), (19, 18), (20, 32), (11, 31)], fill=hexc("#b3bac4"))
    painter.line([(30, 20), (44, 16)], fill=INK, width=6)
    painter.line([(30, 20), (43, 16)], fill=hexc("#6d747e"), width=3.4)
    painter.circle(44, 16, 4, fill=INK)
    painter.circle(44, 16, 3, fill=hexc("#c9d1d8"))
    return shadow_under(painter.out(), 23, 40, 14, 4)


def unit_shield():
    painter = P(50, 48)
    unit_body(painter, 21, 27, 10, 11, "#5d6b4a", helmet="#7c848f")
    painter.rect([24, 12, 46, 34], fill=INK, radius=4)
    painter.rect([26, 14, 44, 32], fill=hexc("#8a6234"), radius=3)
    painter.vgrad([26, 12, 46, 34], hexc("#a8834f"), hexc("#5d4021"))
    painter.line([(35, 15), (35, 31)], fill=hexc("#6d747e"), width=1.6)
    painter.circle(35, 23, 3.6, fill=hexc(GOLD))
    return shadow_under(painter.out(), 22, 41, 14, 4)


def unit_healer():
    painter = P(46, 50)
    painter.poly([(12, 42), (24, 12), (36, 42)], fill=INK)
    painter.poly([(14, 40), (24, 15), (34, 40)], fill=hexc("#3f5b48"))
    painter.vgrad([10, 12, 38, 44], hexc("#5b8a68"), hexc("#2f4a3a"))
    painter.circle(24, 20, 6, fill=hexc(SKIN))
    painter.arc([16, 12, 32, 26], 200, 340, fill=hexc("#2a3038"), width=3)
    painter.line([(34, 16), (34, 44)], fill=INK, width=4)
    painter.line([(34, 16), (34, 43)], fill=hexc(WOOD), width=2.4)
    painter.glow(34, 12, 8, (140, 230, 150), alpha=150, blur=4)
    painter.circle(34, 12, 4.4, fill=hexc("#9be07a"))
    return shadow_under(painter.out(), 24, 43, 14, 4)


def unit_commander():
    painter = P(50, 52)
    unit_body(painter, 22, 28, 11, 12, "#5a4a6a", helmet="#aab2bd", cape="#6a4f8a")
    painter.poly([(18, 14), (22, 6), (26, 14)], fill=hexc(GOLD))
    painter.line([(36, 14), (44, 10)], fill=INK, width=3)
    painter.line([(44, 10), (44, 34)], fill=INK, width=3)
    painter.poly([(44, 10), (54, 13), (44, 20)], fill=hexc("#b4402f"))
    painter.line([(30, 22), (46, 26)], fill=INK, width=4)
    painter.circle(46, 26, 3.6, fill=hexc(GOLD))
    return shadow_under(painter.out(), 24, 44, 15, 4)


def unit_flyer():
    painter = P(52, 40)
    painter.poly([(26, 18), (4, 4), (12, 20), (2, 26), (22, 24)], fill=INK)
    painter.poly([(26, 18), (24, 6), (32, 16)], fill=INK)
    painter.poly([(26, 18), (6, 6), (13, 19), (5, 24), (22, 22)], fill=hexc("#4a3a52", 235))
    painter.ellipse([20, 14, 38, 30], fill=INK)
    painter.ellipse([21, 15, 37, 29], fill=hexc("#5d4a66"))
    painter.circle(35, 20, 5, fill=INK)
    painter.circle(35, 20, 4, fill=hexc("#3f3546"))
    painter.poly([(38, 18), (46, 20), (38, 22)], fill=hexc("#c4553f"))
    painter.circle(36.5, 19, 1.2, fill=hexc("#e8d06a"))
    painter.circle(31, 20, 1.4, fill=hexc("#2a2430"))
    painter.line([(26, 30), (30, 37)], fill=hexc("#3f3546"), width=2)
    painter.line([(31, 30), (33, 37)], fill=hexc("#3f3546"), width=2)
    return painter.out()


def unit_archer():
    painter = P(48, 48)
    unit_body(painter, 22, 27, 10, 11, "#4a5b48", helmet="#5d4021")
    painter.arc([24, 10, 50, 44], -60, 60, fill=INK, width=4)
    painter.arc([24, 10, 50, 44], -60, 60, fill=hexc(WOOD), width=2.4)
    painter.line([(36, 12), (36, 42)], fill=hexc("#ddd2b8"), width=1)
    painter.poly([(28, 24), (48, 26), (28, 30)], fill=hexc("#e3e8ee"))
    painter.rect([10, 12, 20, 34], fill=hexc("#3a2f22"), radius=3)
    for k in range(3):
        painter.line([(11, 14 + k * 6), (19, 13 + k * 6)], fill=hexc("#c9a86a"), width=1.4)
    return shadow_under(painter.out(), 22, 41, 13, 4)


def unit_brute():
    painter = P(62, 60)
    painter.ellipse([12, 20, 48, 54], fill=INK)
    painter.ellipse([14, 22, 46, 52], fill=hexc("#6b5a48"))
    painter.vgrad([12, 20, 50, 54], hexc("#8a7860"), hexc("#4a3f33"))
    painter.ellipse([16, 6, 44, 30], fill=INK)
    painter.ellipse([18, 8, 42, 28], fill=hexc("#7d6a54"))
    painter.poly([(20, 22), (26, 32), (22, 32)], fill=hexc("#e8dfc8"))
    painter.poly([(40, 22), (34, 32), (38, 32)], fill=hexc("#e8dfc8"))
    painter.circle(24, 16, 2.2, fill=hexc("#c4553f"))
    painter.circle(36, 16, 2.2, fill=hexc("#c4553f"))
    painter.line([(46, 24), (58, 12)], fill=INK, width=7)
    painter.line([(46, 24), (57, 13)], fill=hexc(WOOD), width=4)
    painter.circle(58, 12, 7, fill=INK)
    painter.circle(58, 12, 5.6, fill=hexc("#8a939e"))
    for a in (0, 60, 120, 180, 240, 300):
        ra = math.radians(a)
        painter.circle(58 + math.cos(ra) * 6, 12 + math.sin(ra) * 6, 2.2, fill=hexc("#c9d1d8"))
    return shadow_under(painter.out(), 30, 52, 20, 6)


def unit_warlord():
    painter = P(74, 72)
    painter.poly([(14, 46), (30, 16), (52, 22), (58, 48), (36, 60)], fill=hexc("#7a2f24", 220))
    painter.ellipse([14, 24, 56, 62], fill=INK)
    painter.ellipse([16, 26, 54, 60], fill=hexc("#4a4048"))
    painter.vgrad([14, 24, 58, 62], hexc("#6b5f6a"), hexc("#2f2a32"))
    painter.poly([(20, 14), (26, 2), (32, 14), (44, 2), (50, 14)], fill=INK)
    painter.poly([(22, 13), (27, 4), (32, 13), (43, 4), (48, 13)], fill=hexc("#8a939e"))
    painter.ellipse([20, 12, 50, 32], fill=hexc("#2f2a32"))
    painter.circle(27, 21, 2.6, fill=hexc("#ff8a5c"))
    painter.circle(43, 21, 2.6, fill=hexc("#ff8a5c"))
    painter.glow(35, 22, 8, (255, 120, 60), alpha=90, blur=4)
    painter.line([(54, 30), (70, 14)], fill=INK, width=8)
    painter.line([(54, 30), (69, 15)], fill=hexc("#5d6673"), width=5)
    painter.poly([(64, 4), (74, 18), (60, 20)], fill=INK)
    painter.poly([(65, 7), (72, 17), (61, 19)], fill=hexc("#dfe4e9"))
    return shadow_under(painter.out(), 34, 64, 24, 6)


# ------------------------------------------------------------------ گلوله‌ها و جلوه‌ها


def bullet(name, w, h, color, kind="ball", glow_color=None):
    painter = P(w, h)
    if kind == "ball":
        r = min(w, h) / 2 - 1
        painter.circle(w / 2, h / 2, r, fill=INK)
        painter.circle(w / 2, h / 2, r - 1, fill=hexc(color))
        painter.circle(w / 2 - r * 0.3, h / 2 - r * 0.3, r * 0.35, fill=shade(hexc(color), 0.45))
    elif kind == "shard":
        painter.poly([(2, h / 2), (w / 2, 2), (w - 2, h / 2), (w / 2, h - 2)], fill=INK)
        painter.poly([(4, h / 2), (w / 2, 4), (w - 4, h / 2), (w / 2, h - 4)], fill=hexc(color))
        painter.poly([(w / 2, 4), (w - 4, h / 2), (w / 2, h / 2)], fill=shade(hexc(color), 0.4))
    elif kind == "bolt":
        painter.poly([(1, h / 2), (w - 1, h / 2 - 2), (w - 1, h / 2 + 2)], fill=INK)
        painter.poly([(2, h / 2), (w - 2, h / 2 - 1.4), (w - 2, h / 2 + 1.4)], fill=hexc("#e8dfc8"))
        painter.poly([(w - 2, h / 2 - 1.4), (w - 1, h / 2), (w - 2, h / 2 + 1.4)], fill=hexc(STEEL))
    elif kind == "blade":
        painter.poly([(1, h - 2), (w - 3, 1), (w - 1, h / 2)], fill=INK)
        painter.poly([(3, h - 3), (w - 4, 3), (w - 3, h / 2)], fill=hexc("#dfe4e9"))
    if glow_color:
        painter.glow(w / 2, h / 2, max(w, h) / 2, glow_color, alpha=90, blur=3)
    return painter.out()


def explosion_frames():
    frames = []
    for i in range(6):
        painter = P(72, 72)
        k = i / 5.0
        r = 12 + k * 26
        alpha = int(235 * (1.0 - k * 0.85))
        painter.glow(36, 36, r * 1.15, (255, 150, 60), alpha=int(150 * (1 - k)), blur=6)
        painter.blob(36, 36, r, r * 0.92, fill=(226, 96, 31, alpha), seed=i + 1, wobble=0.24, points=18)
        painter.blob(36, 36, r * 0.62, r * 0.56, fill=(255, 179, 71, min(255, alpha + 20)), seed=i + 7, wobble=0.28, points=16)
        painter.blob(36, 36, r * 0.3, r * 0.28, fill=(255, 240, 194, alpha), seed=i + 13, wobble=0.3, points=12)
        if i >= 2:
            for j in range(5 + i):
                a = math.pi * 2 * j / (5 + i) + i
                d = r * 1.1 + (i * 2)
                painter.circle(36 + math.cos(a) * d, 36 + math.sin(a) * d, max(1.0, 3.4 - i * 0.5),
                               fill=(90, 80, 74, int(200 * (1 - k))))
        frames.append(painter.out())
    return frames


def smoke_frames():
    frames = []
    for i in range(3):
        painter = P(48, 48)
        painter.blob(24, 26 - i * 3, 12 + i * 3, 10 + i * 2, fill=(70, 66, 62, 170 - i * 40), seed=i + 3, wobble=0.3, points=16)
        painter.blob(17, 20 - i * 2, 7 + i, 6 + i, fill=(92, 88, 84, 130 - i * 30), seed=i + 11, wobble=0.3, points=14)
        frames.append(painter.out())
    return frames


def fx_simple(name, size, drawer):
    painter = P(size, size)
    drawer(painter, size)
    return painter.out()


def main():
    os.makedirs(SPRITES, exist_ok=True)
    os.makedirs(ICONS, exist_ok=True)
    print("زمین و پس‌زمینه...")
    ground_tile("grass", hexc("#3d6b40"), hexc("#4a7c4b"), hexc("#5d9459"), 11, blade_color="#7fb26a")
    ground_tile("grass_dark", hexc("#31552f"), hexc("#3d6b40"), hexc("#4a7c4b"), 12, blade_color="#5d9459")
    ground_tile("dirt", hexc("#6b5136"), hexc("#7d6040"), hexc("#8f7049"), 13)
    ground_tile("sand", hexc("#b99a63"), hexc("#c9aa72"), hexc("#d9bd85"), 14)
    ground_tile("snow", hexc("#c3d2d8"), hexc("#dbe6ea"), hexc("#f2f8fa"), 15)
    ground_tile("rock", hexc("#5d6673"), hexc("#6d747e"), hexc("#8a939e"), 16)
    ground_tile("ash", hexc("#4a4038"), hexc("#5b4d42"), hexc("#6d5c4e"), 17)
    backdrop_build()

    print("دکور...")
    save(prop_tree_pine(), "props", "tree_pine")
    save(prop_tree_round(), "props", "tree_round")
    save(prop_tree_palm(), "props", "tree_palm")
    save(prop_rock_big(), "props", "rock_big")
    save(prop_rock_small(), "props", "rock_small")
    save(prop_bush(), "props", "bush")
    save(prop_flower(7, "#e3b23c"), "props", "flower_1")
    save(prop_flower(8, "#d96a8a"), "props", "flower_2")
    save(prop_barrel(), "props", "barrel")
    save(prop_crate(), "props", "crate")
    save(prop_ruins(), "props", "ruins")
    save(prop_statue(), "props", "statue")
    save(prop_tent(), "props", "tent")
    save(prop_pond(), "props", "pond")
    save(prop_cactus(), "props", "cactus")
    save(prop_grass_tuft(), "props", "grass_tuft")
    save(prop_torch(), "props", "torch")
    for i, frame in enumerate(flame_frames(), start=1):
        save(frame, "fx", "flame_%d" % i)

    print("ساختارها و برجک‌ها...")
    save(structure_keep(), "structures", "keep")
    save(structure_gate(), "structures", "gate")
    save(tower_base(), "towers", "base")
    heads = {
        "bow": head_bow, "spear": head_spear, "catapult": head_catapult, "longbow": head_longbow,
        "zubin": head_zubin, "firepot": weapon_firepot, "frost": head_frost, "thunder": head_thunder,
        "venom": head_venom, "falcon": head_falcon, "breaker": head_breaker, "sniper": head_sniper,
        "firecat": head_firecat, "scythe": head_scythe, "volley": head_volley, "triplet": head_triplet,
        "pyre": head_pyre, "eye": head_eye,
    }
    for name, builder in heads.items():
        save(builder(), "towers", "head_" + name)
    for i, frame in enumerate(muzzle_flash(), start=1):
        save(frame, "towers", "muzzle_%d" % i)

    print("دشمنان...")
    units = {
        "grunt": unit_grunt, "runner": unit_runner, "armored": unit_armored, "shield": unit_shield,
        "healer": unit_healer, "commander": unit_commander, "flyer": unit_flyer, "archer": unit_archer,
        "brute": unit_brute, "warlord": unit_warlord,
    }
    for name, builder in units.items():
        save(builder(), "units", name)

    print("گلوله‌ها و جلوه‌ها...")
    save(bullet("arrow", 30, 10, "#e8dfc8", "bolt"), "bullets", "arrow")
    save(bullet("bolt", 26, 10, "#dfe4e9", "bolt"), "bullets", "bolt")
    save(bullet("stone", 18, 18, "#7b838d", "ball"), "bullets", "stone")
    save(bullet("fire", 20, 20, "#e2601f", "ball", glow_color=(255, 150, 60)), "bullets", "fire")
    save(bullet("frost", 18, 18, "#9fd6e2", "shard", glow_color=(150, 220, 235)), "bullets", "frost")
    save(bullet("spark", 24, 12, "#fff3b0", "shard", glow_color=(255, 240, 170)), "bullets", "spark")
    save(bullet("poison", 18, 18, "#8fd06a", "ball", glow_color=(140, 230, 150)), "bullets", "poison")
    save(bullet("shell", 22, 16, "#5d6673", "ball"), "bullets", "shell")
    save(bullet("scythe", 28, 28, "#dfe4e9", "blade"), "bullets", "scythe")
    save(bullet("dart", 22, 10, "#c9d1d8", "bolt"), "bullets", "dart")

    for i, frame in enumerate(explosion_frames(), start=1):
        save(frame, "fx", "explosion_%d" % i)
    for i, frame in enumerate(smoke_frames(), start=1):
        save(frame, "fx", "smoke_%d" % i)
    save(fx_simple("spark", 20, lambda p, s: (
        p.glow(s / 2, s / 2, 7, (255, 230, 150), alpha=150, blur=3),
        p.poly([(s / 2, 2), (s / 2 + 4, s / 2), (s / 2, s - 2), (s / 2 - 4, s / 2)], fill=hexc("#fff3b0")))),
        "fx", "spark")
    save(fx_simple("star", 34, lambda p, s: (
        p.glow(s / 2, s / 2, 13, (255, 220, 120), alpha=140, blur=4),
        p.poly([(17, 2), (21, 12), (32, 13), (23, 20), (26, 31), (17, 24), (8, 31), (11, 20), (2, 13), (13, 12)],
               fill=hexc("#ffd76a")),
        p.poly([(17, 6), (20, 13), (27, 14), (21, 18), (23, 26), (17, 21), (11, 26), (13, 18), (7, 14), (14, 13)],
               fill=hexc("#fff0c2")))),
        "fx", "star")
    save(fx_simple("heal", 30, lambda p, s: (
        p.glow(s / 2, s / 2, 12, (140, 230, 150), alpha=130, blur=4),
        p.rect([12, 4, 18, 26], fill=hexc("#a8e88a"), radius=2),
        p.rect([4, 12, 26, 18], fill=hexc("#a8e88a"), radius=2))),
        "fx", "heal")
    save(fx_simple("ring", 64, lambda p, s: (
        p.circle(s / 2, s / 2, 28, outline=hexc("#ffffff", 90), width=4),
        p.circle(s / 2, s / 2, 22, outline=hexc("#ffd76a", 130), width=2))),
        "fx", "ring")
    save(fx_simple("coin", 24, lambda p, s: (
        p.circle(12, 12, 10, fill=INK),
        p.circle(12, 12, 8.5, fill=hexc(GOLD)),
        p.circle(10, 10, 3, fill=hexc("#f6e0a0")))),
        "fx", "coin")
    save(fx_simple("select", 72, lambda p, s: (
        p.arc([4, 4, 68, 68], 0, 360, fill=hexc("#ffd76a", 210), width=3),
        p.arc([10, 10, 62, 62], 0, 360, fill=hexc("#ffd76a", 90), width=2))),
        "fx", "select")

    print("آیکون‌های واسط...")
    hud_icons = {
        "coin": lambda p: (p.circle(20, 20, 15, fill=INK), p.circle(20, 20, 13, fill=hexc(GOLD)),
                            p.circle(16, 16, 4.4, fill=hexc("#f6e0a0")),
                            p.line([(20, 12), (20, 28)], fill=hexc("#a97a42"), width=2.4)),
        "heart": lambda p: (p.poly([(20, 33), (4, 17), (8, 8), (20, 14), (32, 8), (36, 17)], fill=INK),
                             p.poly([(20, 30), (7, 17), (10, 10), (20, 15), (30, 10), (33, 17)], fill=hexc("#d4523f"))),
        "star_small": lambda p: (p.poly([(20, 4), (24, 15), (36, 16), (27, 24), (30, 35), (20, 28), (10, 35), (13, 24),
                                          (4, 16), (16, 15)], fill=INK),
                                  p.poly([(20, 8), (23, 15.5), (31, 16.5), (25, 22), (27, 30), (20, 25), (13, 30),
                                          (15, 22), (9, 16.5), (17, 15.5)], fill=hexc("#ffd76a"))),
        "speed": lambda p: (p.poly([(22, 3), (10, 21), (18, 21), (15, 37), (30, 17), (21, 17)], fill=INK),
                             p.poly([(22, 6), (13, 19), (20, 19), (18, 32), (27, 18), (20, 18)], fill=hexc("#8fd0ea"))),
        "pause": lambda p: (p.rect([10, 6, 16, 34], fill=INK, radius=2), p.rect([24, 6, 30, 34], fill=INK, radius=2),
                             p.rect([11, 7, 15, 33], fill=hexc("#e8eef2"), radius=1),
                             p.rect([25, 7, 29, 33], fill=hexc("#e8eef2"), radius=1)),
        "lock": lambda p: (p.arc([12, 6, 28, 26], 180, 360, fill=INK, width=4),
                            p.rect([8, 17, 32, 34], fill=INK, radius=3),
                            p.arc([13, 7, 27, 25], 180, 360, fill=hexc("#c9d1d8"), width=2.6),
                            p.rect([10, 19, 30, 32], fill=hexc("#98a2b0"), radius=2),
                            p.circle(20, 25, 3, fill=hexc("#3a4048"))),
        "skull": lambda p: (p.circle(20, 17, 12, fill=INK), p.rect([14, 24, 26, 34], fill=INK, radius=2),
                             p.circle(20, 17, 10, fill=hexc("#e8eef2")), p.rect([15, 25, 25, 33], fill=hexc("#e8eef2")),
                             p.circle(16, 16, 3.2, fill=INK), p.circle(24, 16, 3.2, fill=INK),
                             p.line([(17, 26), (17, 32)], fill=hexc("#98a2b0"), width=1.6),
                             p.line([(20, 26), (20, 32)], fill=hexc("#98a2b0"), width=1.6),
                             p.line([(23, 26), (23, 32)], fill=hexc("#98a2b0"), width=1.6)),
        "flag": lambda p: (p.line([(10, 4), (10, 36)], fill=INK, width=3),
                            p.poly([(10, 5), (33, 11), (10, 20)], fill=hexc("#b4402f"))),
        "check": lambda p: (p.line([(6, 21), (16, 31), (34, 9)], fill=INK, width=7),
                             p.line([(6, 21), (16, 31), (34, 9)], fill=hexc("#7bd48a"), width=4)),
        "trophy": lambda p: (p.rect([11, 6, 29, 18], fill=INK, radius=3), p.rect([17, 18, 23, 28], fill=INK),
                              p.rect([10, 28, 30, 35], fill=INK, radius=2),
                              p.rect([13, 8, 27, 16], fill=hexc(GOLD)), p.rect([13, 30, 27, 33], fill=hexc("#a97a42"))),
        "wave": lambda p: (p.arc([6, 10, 26, 30], 200, 520, fill=INK, width=4),
                            p.arc([12, 16, 24, 28], 200, 520, fill=hexc("#8fd0ea"), width=2.4),
                            p.arc([18, 22, 30, 34], 200, 520, fill=hexc("#8fd0ea", 150), width=2)),
        "close": lambda p: (p.line([(9, 9), (31, 31)], fill=INK, width=7), p.line([(31, 9), (9, 31)], fill=INK, width=7),
                             p.line([(10, 10), (30, 30)], fill=hexc("#e8eef2"), width=3.4),
                             p.line([(30, 10), (10, 30)], fill=hexc("#e8eef2"), width=3.4)),
    }
    for name, drawer in hud_icons.items():
        save(fx_simple(name, 40, lambda p, s, d=drawer: d(p)), "icons", name)

    print("آیکون سلاح‌ها...")
    head_paths = {name: builder for name, builder in heads.items()}
    for name, builder in head_paths.items():
        head_img = builder()
        icon = Image.new("RGBA", (72, 72), (0, 0, 0, 0))
        ring = ImageDraw.Draw(icon)
        ring.ellipse([2, 2, 70, 70], fill=(14, 26, 30, 255), outline=(90, 110, 116, 255), width=2)
        ring.ellipse([6, 6, 66, 66], fill=(22, 42, 48, 255))
        head_img = head_img.resize((int(head_img.width * 0.92), int(head_img.height * 0.92)), Image.LANCZOS)
        icon.alpha_composite(head_img, ((72 - head_img.width) // 2, (72 - head_img.height) // 2 - 2))
        icon.save(os.path.join(ICONS, "w_%s.png" % name))

    print("آیکون اپ...")
    for size in (192, 512):
        app = Image.new("RGBA", (size, size), (11, 26, 30, 255))
        d = ImageDraw.Draw(app)
        for y in range(size):
            k = y / size
            d.line([(0, y), (size, y)], fill=lerp((8, 22, 27, 255), (20, 48, 44, 255), k))
        scale = size / 512.0
        keep = Image.open(os.path.join(SPRITES, "structures", "keep.png")).convert("RGBA")
        keep = keep.resize((int(320 * scale), int(284 * scale)), Image.LANCZOS)
        glow = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        ImageDraw.Draw(glow).ellipse([size * 0.18, size * 0.35, size * 0.82, size * 0.95], fill=(220, 174, 75, 60))
        glow = glow.filter(ImageFilter.GaussianBlur(size * 0.06))
        app = Image.alpha_composite(app, glow)
        app.alpha_composite(keep, ((size - keep.width) // 2, int(size * 0.42)))
        app.save(os.path.join(ROOT, "icon_%d.png" % size))
        if size == 512:
            app.save(os.path.join(ICONS, "app_512.png"))
    # پیش‌زمینهٔ آیکون تطبیقی (اندروید): نشان سپر و برج
    fg = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
    fd = ImageDraw.Draw(fg)
    fd.polygon([(216, 60), (356, 118), (356, 246), (216, 366), (76, 246), (76, 118)], fill=(20, 44, 48, 255),
               outline=(220, 174, 75, 255))
    keep = Image.open(os.path.join(SPRITES, "structures", "keep.png")).convert("RGBA")
    keep = keep.resize((240, 213), Image.LANCZOS)
    fg.alpha_composite(keep, (96, 150))
    fg.save(os.path.join(ROOT, "icon_foreground_432.png"))
    bg = Image.new("RGBA", (432, 432), (12, 30, 34, 255))
    bd = ImageDraw.Draw(bg)
    for y in range(432):
        bd.line([(0, y), (432, y)], fill=lerp((10, 26, 30, 255), (22, 52, 46, 255), y / 431))
    bg.save(os.path.join(ROOT, "icon_background_432.png"))

    _guard_drive_art()
    print("تصویرها ساخته شدند.")


# آثار دست‌ساز برگرفته از شیت‌های گوگل‌درایو (tools/import_drive_art.py)؛ این
# مولد نباید رویشان بنویسد، وگرنه هنر درایو با طرح‌های قدیمی بازنویسی می‌شود.
DRIVE_ART = [
    os.path.join(SPRITES, "towers", "head_%s.png" % name)
    for name in ("bow", "spear", "catapult", "longbow", "zubin", "firepot", "frost",
                 "thunder", "venom", "falcon", "breaker", "sniper", "firecat",
                 "scythe", "volley", "triplet", "pyre", "eye")
] + [
    os.path.join(SPRITES, "units", "%s.png" % name)
    for name in ("grunt", "runner", "armored", "shield", "archer", "healer",
                 "flyer", "commander", "brute", "warlord")
] + [
    os.path.join(ICONS, "w_%s.png" % name)
    for name in ("bow", "spear", "catapult", "longbow", "zubin", "firepot", "frost",
                 "thunder", "venom", "falcon", "breaker", "sniper", "firecat",
                 "scythe", "volley", "triplet", "pyre", "eye")
]


def _guard_drive_art():
    """اگر هنر درایو وجود دارد، هشدار می‌دهد که این مولد بازنویسی‌اش می‌کند."""
    existing = [path for path in DRIVE_ART if os.path.exists(path)]
    if not existing:
        return
    print("", file=sys.stderr)
    print("هشدار: این مولد طرح‌های قدیمی می‌سازد و هنر گوگل‌درایو را بازنویسی می‌کند.", file=sys.stderr)
    print("برای بازگرداندن هنر درایو: python3 tools/import_drive_art.py", file=sys.stderr)
    print("تعداد فایل در معرض بازنویسی: %d" % len(existing), file=sys.stderr)
    print("", file=sys.stderr)


if __name__ == "__main__":
    main()
