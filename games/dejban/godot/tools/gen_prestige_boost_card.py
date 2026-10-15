#!/usr/bin/env python3
"""مولد کارت آیتم منوی ارتقا «PRESTIGE BOOST (افزایش اعتبار)» در ابعاد دقیق 512x512 با پس‌زمینهٔ شفاف،
آمادهٔ درون‌ریزی در Construct 3 و Godot."""

from __future__ import annotations

import json
import math
import os
import random
from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

try:
    import arabic_reshaper
    from bidi.algorithm import get_display
except ImportError:
    arabic_reshaper = None
    get_display = None

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "spritesheets", "ui")
OUT_PNG = os.path.join(OUT_DIR, "prestige_boost_card.png")
OUT_JSON = os.path.join(OUT_DIR, "prestige_boost_card.json")

FINAL_SIZE = 512
SS = 4
W = FINAL_SIZE * SS  # 2048


def fa_text(text: str) -> str:
    if arabic_reshaper is not None and get_display is not None:
        return get_display(arabic_reshaper.reshape(text))
    return text


def chamfer_rect_pts(x0: float, y0: float, x1: float, y1: float, ch: float) -> list[tuple[float, float]]:
    """چندضلعی مستطیل با گوشه‌های پخ‌خوردهٔ تیز نظامی (Sharp-edged military chamfer)."""
    return [
        (x0 + ch, y0),
        (x1 - ch, y0),
        (x1, y0 + ch),
        (x1, y1 - ch),
        (x1 - ch, y1),
        (x0 + ch, y1),
        (x0, y1 - ch),
        (x0, y0 + ch),
    ]


def star_pts(cx: float, cy: float, r_outer: float, r_inner: float, points: int = 5, angle_offset: float = -math.pi / 2) -> list[tuple[float, float]]:
    pts = []
    for i in range(points * 2):
        r = r_outer if i % 2 == 0 else r_inner
        a = angle_offset + i * math.pi / points
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def make_vertical_gradient(w: int, h: int, stops: list[tuple[float, tuple[int, int, int, int]]]) -> Image.Image:
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        t = y / max(1, h - 1)
        col = stops[-1][1]
        for i in range(len(stops) - 1):
            t0, c0 = stops[i]
            t1, c1 = stops[i + 1]
            if t0 <= t <= t1:
                u = (t - t0) / max(1e-6, t1 - t0)
                col = tuple(int(round(c0[k] + (c1[k] - c0[k]) * u)) for k in range(4))
                break
        for x in range(w):
            px[x, y] = col
    return img


def make_brushed_metal(w: int, h: int) -> Image.Image:
    """بافت فلز برس‌خوردهٔ سرمه‌ای تیرهٔ نظامی با خطوط مورب و سایه‌روشن لبه‌ها."""
    # گرادیان سرمه‌ای-فولادی تیره در مقیاس کوچک و بزرگ‌نمایی نرم
    gw, gh = 256, 256
    base = make_vertical_gradient(gw, gh, [
        (0.0, (24, 38, 66, 255)),
        (0.25, (16, 27, 49, 255)),
        (0.55, (11, 20, 38, 255)),
        (0.85, (15, 26, 48, 255)),
        (1.0, (9, 15, 30, 255)),
    ]).resize((w, h), Image.BILINEAR)

    # خطوط برس فلزی و خراش‌های ظریف نظامی
    grain = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(grain)
    rng = random.Random(42)
    for _ in range(900):
        y = rng.randint(0, h - 1)
        x_start = rng.randint(0, w // 3)
        x_end = rng.randint(w * 2 // 3, w)
        bright = rng.choice([True, False])
        alpha = rng.randint(6, 18)
        col = (140, 190, 255, alpha) if bright else (4, 8, 16, alpha + 8)
        gdraw.line([(x_start, y), (x_end, y)], fill=col, width=rng.randint(1, 3))

    # خطوط مورب زرهی بسیار ظریف
    for k in range(-h, w + h, 64):
        gdraw.line([(k, 0), (k - h // 2, h)], fill=(80, 140, 220, 8), width=2)

    base.alpha_composite(grain)
    return base


def make_gold_gradient(w: int, h: int, bright_boost: int = 0) -> Image.Image:
    b = bright_boost
    gw, gh = 128, 256
    grad = make_vertical_gradient(gw, gh, [
        (0.0, (min(255, 255 + b), min(255, 240 + b), min(255, 165 + b), 255)),
        (0.18, (min(255, 246 + b), min(255, 198 + b), min(255, 72 + b), 255)),
        (0.45, (min(255, 202 + b), min(255, 142 + b), min(255, 28 + b), 255)),
        (0.55, (min(255, 252 + b), min(255, 218 + b), min(255, 102 + b), 255)),
        (0.82, (min(255, 182 + b), min(255, 120 + b), min(255, 22 + b), 255)),
        (1.0, (min(255, 126 + b), min(255, 78 + b), min(255, 14 + b), 255)),
    ]).resize((w, h), Image.BILINEAR)
    return grad


def draw_masked(canvas: Image.Image, fill_img: Image.Image, poly: list[tuple[float, float]]) -> None:
    mask = Image.new("L", canvas.size, 0)
    ImageDraw.Draw(mask).polygon(poly, fill=255)
    canvas.paste(fill_img, (0, 0), mask)


def draw_rivet(draw: ImageDraw.ImageDraw, cx: float, cy: float, r: float) -> None:
    draw.ellipse([cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3], fill=(8, 14, 26, 220))
    hex_pts = []
    for i in range(6):
        a = i * math.pi / 3 + math.pi / 6
        hex_pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    draw.polygon(hex_pts, fill=(218, 166, 54, 255), outline=(72, 46, 12, 255))
    draw.ellipse([cx - r * 0.45, cy - r * 0.45, cx + r * 0.45, cy + r * 0.45],
                 fill=(255, 232, 142, 255), outline=(120, 82, 20, 255))


def draw_faceted_gold_star(canvas: Image.Image, cx: float, cy: float, r_outer: float, r_inner: float) -> None:
    """ستارهٔ ۵ پر سه‌بعدی طلایی با وجوه نور و سایه (Faceted 3D Golden Star)."""
    d = ImageDraw.Draw(canvas)
    pts = star_pts(cx, cy, r_outer, r_inner, 5, -math.pi / 2)
    # سایهٔ زیر ستاره
    shadow_pts = [(x + 4, y + 8) for (x, y) in pts]
    d.polygon(shadow_pts, fill=(18, 12, 6, 160))

    #۱۰ مثلث وجهی از مرکز به لبه‌ها برای ایجاد برجستگی سه‌بعدی واقعی
    facet_cols = [
        (255, 244, 172, 255), (196, 134, 24, 255),
        (252, 216, 92, 255),  (168, 108, 18, 255),
        (240, 192, 64, 255),  (154, 96, 14, 255),
        (232, 182, 54, 255),  (184, 124, 22, 255),
        (255, 232, 132, 255), (206, 146, 32, 255),
    ]
    n = len(pts)
    for i in range(n):
        p0 = (cx, cy)
        p1 = pts[i]
        p2 = pts[(i + 1) % n]
        d.polygon([p0, p1, p2], fill=facet_cols[i % len(facet_cols)])

    d.polygon(pts, fill=None, outline=(255, 248, 196, 255), width=4)
    # درخشش مرکزی ستاره
    d.ellipse([cx - r_inner * 0.35, cy - r_inner * 0.35, cx + r_inner * 0.35, cy + r_inner * 0.35],
              fill=(255, 252, 225, 210))


def draw_medal_with_aura(canvas: Image.Image, cx: float, cy: float) -> None:
    """مدال نظامی طلایی با ستارهٔ مرکزی و هالهٔ آبی درخشان در بالای کارت."""
    # ۱. هالهٔ آبی درخشان (Glowing Blue Aura)
    aura = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ad = ImageDraw.Draw(aura)
    for r, col in [
        (250, (18, 118, 255, 42)),
        (205, (28, 155, 255, 78)),
        (165, (58, 198, 255, 118)),
        (132, (125, 232, 255, 155)),
    ]:
        ad.ellipse([cx - r, cy - r, cx + r, cy + r], fill=col)

    # پرتوهای انرژی آبی در پس‌زمینهٔ مدال
    for i in range(16):
        a = i * (math.pi * 2 / 16)
        r_len = 225 if i % 2 == 0 else 175
        a_w = 0.09 if i % 2 == 0 else 0.055
        ray = [
            (cx, cy),
            (cx + math.cos(a - a_w) * r_len, cy + math.sin(a - a_w) * r_len),
            (cx + math.cos(a + a_w) * r_len, cy + math.sin(a + a_w) * r_len),
        ]
        ad.polygon(ray, fill=(75, 210, 255, 72 if i % 2 == 0 else 44))

    aura = aura.filter(ImageFilter.GaussianBlur(radius=22))
    canvas.alpha_composite(aura)

    d = ImageDraw.Draw(canvas)

    # حلقهٔ نئونی آبی دور هاله
    d.ellipse([cx - 148, cy - 148, cx + 148, cy + 148], outline=(95, 225, 255, 165), width=5)

    # ۲. روبان نظامی بالای مدال (Military Ribbon)
    rib_top = cy - 182
    rib_bot = cy - 58
    rib_w = 88
    rib_poly = [
        (cx - rib_w, rib_top),
        (cx + rib_w, rib_top),
        (cx + rib_w * 0.72, rib_bot),
        (cx, rib_bot + 26),
        (cx - rib_w * 0.72, rib_bot),
    ]
    d.polygon([(x + 4, y + 8) for (x, y) in rib_poly], fill=(6, 10, 20, 175))
    d.polygon(rib_poly, fill=(22, 62, 138, 255), outline=(248, 204, 82, 255), width=6)
    # نوارهای طلایی و سرخ وسط روبان
    stripe_poly = [
        (cx - 26, rib_top + 4),
        (cx + 26, rib_top + 4),
        (cx + 20, rib_bot + 10),
        (cx, rib_bot + 22),
        (cx - 20, rib_bot + 10),
    ]
    d.polygon(stripe_poly, fill=(185, 32, 48, 255), outline=(255, 218, 95, 255), width=4)
    # گیرهٔ طلایی بالای روبان
    d.rounded_rectangle([cx - rib_w - 8, rib_top - 10, cx + rib_w + 8, rib_top + 14],
                        radius=8, fill=(242, 192, 68, 255), outline=(92, 58, 12, 255), width=4)

    # ۳. بدنهٔ مدال طلایی دالبری/۱۲ ضلعی (Golden Military Medallion)
    outer_r = 128
    inner_r = 114
    badge_pts = star_pts(cx, cy, outer_r, inner_r, 14, 0.0)
    d.polygon([(x + 6, y + 10) for (x, y) in badge_pts], fill=(5, 10, 22, 190))
    d.polygon(badge_pts, fill=(238, 184, 52, 255), outline=(255, 242, 168, 255), width=5)

    # حلقهٔ طلایی برجستهٔ مدال
    d.ellipse([cx - 104, cy - 104, cx + 104, cy + 104],
              fill=(182, 124, 24, 255), outline=(255, 232, 128, 255), width=6)
    # صفحهٔ سرمه‌ای-طلایی داخل مدال با درخشش آبی
    d.ellipse([cx - 88, cy - 88, cx + 88, cy + 88],
              fill=(16, 44, 96, 255), outline=(88, 218, 255, 235), width=5)
    d.ellipse([cx - 72, cy - 72, cx + 72, cy + 72],
              fill=(24, 72, 148, 255))

    # ۴. ستارهٔ طلایی سه‌بعدی در مرکز مدال
    draw_faceted_gold_star(canvas, cx, cy + 2, 74, 31)


def draw_text_with_shadow(
    draw: ImageDraw.ImageDraw,
    pos: tuple[float, float],
    text: str,
    font: ImageFont.FreeTypeFont,
    fill: tuple[int, int, int, int],
    shadow_col: tuple[int, int, int, int] = (6, 10, 18, 230),
    shadow_offset: tuple[int, int] = (0, 6),
    stroke_width: int = 3,
    stroke_fill: tuple[int, int, int, int] = (14, 20, 32, 245),
) -> None:
    bbox = draw.textbbox((0, 0), text, font=font, stroke_width=stroke_width)
    tw = bbox[2] - bbox[0]
    th = bbox[3] - bbox[1]
    x = pos[0] - tw / 2 - bbox[0]
    y = pos[1] - th / 2 - bbox[1]
    draw.text((x + shadow_offset[0], y + shadow_offset[1]), text, font=font, fill=shadow_col,
              stroke_width=stroke_width, stroke_fill=shadow_col)
    draw.text((x, y), text, font=font, fill=fill, stroke_width=stroke_width, stroke_fill=stroke_fill)


def build_prestige_boost_card() -> Image.Image:
    canvas = Image.new("RGBA", (W, W), (0, 0, 0, 0))

    # حاشیهٔ شفاف اطراف کارت مربعی (1:1 ratio, isolated item card)
    margin = 76
    c_x0, c_y0 = margin, margin
    c_x1, c_y1 = W - margin, W - margin
    chamfer = 84

    # ۱. سایهٔ بیرونی ملایم و هالهٔ آبی-طلایی دور کارت (با حفظ شفافیت کامل لبه‌های تصویر)
    outer_glow = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    og_draw = ImageDraw.Draw(outer_glow)
    og_draw.polygon(chamfer_rect_pts(c_x0 - 8, c_y0 - 4, c_x1 + 8, c_y1 + 14, chamfer + 6),
                    fill=(8, 18, 38, 175), outline=(38, 152, 255, 95), width=8)
    outer_glow = outer_glow.filter(ImageFilter.GaussianBlur(radius=18))
    canvas.alpha_composite(outer_glow)

    # ۲. قاب طلایی چندلایهٔ زرهی (Golden Border)
    gold_tex = make_gold_gradient(W, W)
    outer_poly = chamfer_rect_pts(c_x0, c_y0, c_x1, c_y1, chamfer)
    draw_masked(canvas, gold_tex, outer_poly)

    d = ImageDraw.Draw(canvas)
    # خط بیرونی تیز و براق طلایی
    d.polygon(outer_poly, fill=None, outline=(255, 246, 182, 255), width=6)

    # پخ میانی تیرهٔ قاب
    mid_pad = 22
    mid_poly = chamfer_rect_pts(c_x0 + mid_pad, c_y0 + mid_pad, c_x1 - mid_pad, c_y1 - mid_pad, chamfer - 14)
    d.polygon(mid_poly, fill=(38, 26, 12, 255), outline=(128, 86, 18, 255), width=4)

    # لایهٔ دوم قاب طلایی درونی
    in_gold_pad = 32
    in_gold_poly = chamfer_rect_pts(c_x0 + in_gold_pad, c_y0 + in_gold_pad,
                                    c_x1 - in_gold_pad, c_y1 - in_gold_pad, chamfer - 20)
    draw_masked(canvas, make_gold_gradient(W, W, bright_boost=12), in_gold_poly)

    # ۳. صفحهٔ فلزی سرمه‌ای تیرهٔ نظامی داخل کارت (Dark Military Metal Texture)
    metal_pad = 48
    metal_poly = chamfer_rect_pts(c_x0 + metal_pad, c_y0 + metal_pad,
                                  c_x1 - metal_pad, c_y1 - metal_pad, chamfer - 30)
    metal_tex = make_brushed_metal(W, W)
    draw_masked(canvas, metal_tex, metal_poly)

    d = ImageDraw.Draw(canvas)
    # خط نئون آبی-فیروزه‌ای ظریف در مرز داخلی فلز و طلا
    d.polygon(metal_poly, fill=None, outline=(52, 182, 255, 190), width=5)

    # صفحات تقویتی گوشه‌ها و پرچ‌های نظامی طلایی
    for rx, ry in [
        (c_x0 + 82, c_y0 + 82),
        (c_x1 - 82, c_y0 + 82),
        (c_x0 + 82, c_y1 - 82),
        (c_x1 - 82, c_y1 - 82),
    ]:
        draw_rivet(d, rx, ry, 18)

    # ۴. آیکن مدال نظامی طلایی با ستارهٔ مرکزی و هالهٔ آبی درخشان در بالا-مرکز
    medal_cx = W / 2
    medal_cy = 395
    draw_medal_with_aura(canvas, medal_cx, medal_cy)
    d = ImageDraw.Draw(canvas)

    # بارگذاری فونت‌ها
    font_en_bold = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    font_fa_bold = os.path.join(ROOT, "assets", "fonts", "Vazirmatn-FD-Bold.ttf")

    f_title = ImageFont.truetype(font_en_bold, 92)
    f_fa_sub = ImageFont.truetype(font_fa_bold, 58)
    f_level = ImageFont.truetype(font_en_bold, 74)
    f_bonus = ImageFont.truetype(font_en_bold, 66)
    f_cost = ImageFont.truetype(font_en_bold, 82)
    f_upgrade = ImageFont.truetype(font_en_bold, 84)

    # ۵. نام آیتم زیر آیکن: "PRESTIGE BOOST" و زیرنویس فارسی «افزایش اعتبار»
    draw_text_with_shadow(
        d, (W / 2, 650), "PRESTIGE BOOST", f_title,
        fill=(255, 224, 98, 255),
        shadow_col=(6, 12, 24, 240),
        shadow_offset=(0, 8),
        stroke_width=5,
        stroke_fill=(52, 32, 8, 255),
    )
    draw_text_with_shadow(
        d, (W / 2, 742), fa_text("افزایش اعتبار"), f_fa_sub,
        fill=(145, 228, 255, 255),
        shadow_col=(6, 12, 24, 230),
        shadow_offset=(0, 5),
        stroke_width=3,
        stroke_fill=(12, 28, 54, 255),
    )

    # خط جداکنندهٔ طلایی-آبی زیر عنوان
    d.line([(310, 808), (W - 310, 808)], fill=(232, 182, 58, 210), width=4)
    d.polygon([(W / 2 - 18, 808), (W / 2, 796), (W / 2 + 18, 808), (W / 2, 820)],
              fill=(82, 218, 255, 255), outline=(255, 228, 118, 255))

    # ۶. نمایش سطح فعلی در میانهٔ کارت: "Level 15 / 50" با فونت طلایی بولد
    draw_text_with_shadow(
        d, (W / 2, 888), "Level 15 / 50", f_level,
        fill=(255, 216, 78, 255),
        shadow_col=(6, 10, 20, 240),
        shadow_offset=(0, 6),
        stroke_width=4,
        stroke_fill=(56, 36, 8, 255),
    )

    # ۷. نوار پیشرفت (Progress Bar — 15 / 50 = 30%)
    bar_x0, bar_y0 = 260, 962
    bar_x1, bar_y1 = W - 260, 1058
    bar_ch = 22
    bar_outer = chamfer_rect_pts(bar_x0 - 8, bar_y0 - 8, bar_x1 + 8, bar_y1 + 8, bar_ch + 4)
    d.polygon(bar_outer, fill=(224, 172, 48, 255), outline=(255, 238, 148, 255), width=4)

    bar_trough = chamfer_rect_pts(bar_x0, bar_y0, bar_x1, bar_y1, bar_ch)
    d.polygon(bar_trough, fill=(8, 15, 28, 255), outline=(28, 52, 92, 255), width=3)

    # پرشدگی ۳۰٪ نوار پیشرفت (15/50) با گرادیان آبی-طلایی درخشان
    fill_ratio = 15.0 / 50.0
    fill_x1 = bar_x0 + (bar_x1 - bar_x0) * fill_ratio
    fill_poly = chamfer_rect_pts(bar_x0 + 6, bar_y0 + 6, fill_x1, bar_y1 - 6, bar_ch - 4)

    bar_grad = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    bg_d = ImageDraw.Draw(bar_grad)
    bg_d.polygon(fill_poly, fill=(32, 182, 255, 255))
    # هایلایت براق نیمهٔ بالایی نوار پیشرفت
    bg_d.polygon(chamfer_rect_pts(bar_x0 + 10, bar_y0 + 10, fill_x1 - 4, (bar_y0 + bar_y1) / 2, 10),
                 fill=(155, 240, 255, 195))
    canvas.alpha_composite(bar_grad)
    d = ImageDraw.Draw(canvas)

    # خطوط درجه‌بندی روی نوار پیشرفت
    for seg in range(1, 10):
        sx = bar_x0 + (bar_x1 - bar_x0) * (seg / 10.0)
        d.line([(sx, bar_y0 + 6), (sx, bar_y1 - 6)], fill=(18, 32, 58, 150), width=3)

    # ۸. نمایش بونوس فعلی: "+150 Starting Coins" با رنگ سبز درخشان
    bonus_x0, bonus_y0 = 260, 1112
    bonus_x1, bonus_y1 = W - 260, 1236
    bonus_poly = chamfer_rect_pts(bonus_x0, bonus_y0, bonus_x1, bonus_y1, 24)
    d.polygon(bonus_poly, fill=(10, 24, 42, 235), outline=(46, 168, 108, 215), width=4)

    draw_text_with_shadow(
        d, (W / 2, (bonus_y0 + bonus_y1) / 2), "+150 Starting Coins", f_bonus,
        fill=(62, 248, 128, 255),
        shadow_col=(4, 16, 10, 240),
        shadow_offset=(0, 5),
        stroke_width=4,
        stroke_fill=(8, 42, 22, 255),
    )

    # ۹. بخش پایینی: نمایش هزینه "10 ⭐" و دکمهٔ مستطیلی طلایی "UPGRADE"
    # قاب هزینه (10 ⭐)
    cost_y = 1345
    cost_box_x0, cost_box_y0 = 700, cost_y - 58
    cost_box_x1, cost_box_y1 = W - 700, cost_y + 58
    d.polygon(chamfer_rect_pts(cost_box_x0, cost_box_y0, cost_box_x1, cost_box_y1, 20),
              fill=(12, 22, 42, 245), outline=(232, 182, 58, 240), width=4)

    draw_text_with_shadow(
        d, (W / 2 - 54, cost_y), "10", f_cost,
        fill=(255, 238, 148, 255),
        shadow_col=(6, 10, 20, 240),
        shadow_offset=(0, 5),
        stroke_width=4,
        stroke_fill=(52, 34, 8, 255),
    )
    draw_faceted_gold_star(canvas, W / 2 + 78, cost_y + 2, 44, 19)
    d = ImageDraw.Draw(canvas)

    # ۱۰. دکمهٔ ارتقا (Rectangular with golden gradient, text "UPGRADE")
    btn_x0, btn_y0 = 280, 1445
    btn_x1, btn_y1 = W - 280, 1695
    btn_ch = 32

    # سایهٔ دکمه
    d.polygon(chamfer_rect_pts(btn_x0 + 6, btn_y0 + 14, btn_x1 + 6, btn_y1 + 14, btn_ch),
              fill=(4, 8, 18, 210))

    # بدنهٔ دکمه با گرادیان طلایی درخشان
    btn_grad = make_vertical_gradient(W, btn_y1 - btn_y0, [
        (0.0, (255, 246, 176, 255)),
        (0.18, (255, 216, 84, 255)),
        (0.52, (232, 166, 32, 255)),
        (0.85, (196, 126, 18, 255)),
        (1.0, (148, 88, 12, 255)),
    ])
    btn_layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    btn_layer.paste(btn_grad, (0, btn_y0))
    btn_poly = chamfer_rect_pts(btn_x0, btn_y0, btn_x1, btn_y1, btn_ch)
    draw_masked(canvas, btn_layer, btn_poly)

    d = ImageDraw.Draw(canvas)
    # هایلایت براق بالای دکمهٔ طلایی
    btn_hi_poly = chamfer_rect_pts(btn_x0 + 14, btn_y0 + 12, btn_x1 - 14, (btn_y0 + btn_y1) / 2 - 8, btn_ch - 10)
    d.polygon(btn_hi_poly, fill=(255, 250, 205, 85))

    # کادر دور دکمه
    d.polygon(btn_poly, fill=None, outline=(255, 248, 192, 255), width=6)
    d.polygon(chamfer_rect_pts(btn_x0 + 10, btn_y0 + 10, btn_x1 - 10, btn_y1 - 10, btn_ch - 6),
              fill=None, outline=(128, 78, 12, 220), width=4)

    # متن "UPGRADE" روی دکمهٔ طلایی
    draw_text_with_shadow(
        d, (W / 2, (btn_y0 + btn_y1) / 2), "UPGRADE", f_upgrade,
        fill=(28, 18, 6, 255),
        shadow_col=(255, 236, 148, 190),
        shadow_offset=(0, 3),
        stroke_width=2,
        stroke_fill=(56, 36, 8, 255),
    )

    # ۱۱. کوچک‌سازی با کیفیت بالا به ابعاد نهایی 512x512 RGBA با پس‌زمینهٔ شفاف
    final_img = canvas.resize((FINAL_SIZE, FINAL_SIZE), Image.LANCZOS)
    return final_img


def write_construct3_metadata() -> None:
    meta = {
        "frames": {
            "prestige_boost_card": {
                "frame": {"x": 0, "y": 0, "w": FINAL_SIZE, "h": FINAL_SIZE},
                "rotated": False,
                "trimmed": False,
                "spriteSourceSize": {"x": 0, "y": 0, "w": FINAL_SIZE, "h": FINAL_SIZE},
                "sourceSize": {"w": FINAL_SIZE, "h": FINAL_SIZE},
                "pivot": {"x": 0.5, "y": 0.5},
            }
        },
        "meta": {
            "app": "https://www.codeandweb.com/texturepacker",
            "version": "1.0",
            "image": "prestige_boost_card.png",
            "format": "RGBA8888",
            "size": {"w": FINAL_SIZE, "h": FINAL_SIZE},
            "scale": "1",
            "item": {
                "id": "prestige_boost",
                "name_en": "PRESTIGE BOOST",
                "name_fa": "افزایش اعتبار",
                "level": 15,
                "max_level": 50,
                "cost_stars": 10,
                "bonus": "+150 Starting Coins",
            },
            "construct3": {
                "horizontalCells": 1,
                "verticalCells": 1,
                "cellWidth": FINAL_SIZE,
                "cellHeight": FINAL_SIZE,
            },
        },
    }
    with open(OUT_JSON, "w", encoding="utf-8") as fh:
        json.dump(meta, fh, ensure_ascii=False, indent=2)


def main() -> int:
    os.makedirs(OUT_DIR, exist_ok=True)
    with open(os.path.join(OUT_DIR, ".gdignore"), "w", encoding="utf-8") as fh:
        fh.write("")
    img = build_prestige_boost_card()
    img.save(OUT_PNG)
    write_construct3_metadata()
    print(f"ساخته شد: {OUT_PNG} ({img.size[0]}x{img.size[1]} {img.mode})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
