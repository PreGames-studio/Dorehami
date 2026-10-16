"""ساخت صفحهٔ فروشگاه ارتقا — ۱۹۲۰×۱۰۸۰، سبک کارتونی، آمادهٔ Construct 3.

چیدمان: نوار بالا (عنوان «ارتقاها»، شمارندهٔ سکه/ستاره، دکمهٔ بازگشت/تنظیمات)،
شبکهٔ ۲×۳ کارت‌های ارتقا (۲۵۰×۳۰۰) با آیکن‌های برش‌خورده از raw/، و ناحیهٔ پایین
(پاداش روزانه / پیشنهاد ویژه با نشان اعلان). پس‌زمینهٔ فلزی آبی تیره با الگوی
نظامی ظریف و قاب طلایی تزئینی. متن فارسی با وزیرمتن FD + arabic_reshaper/bidi.

Usage:  python3 shopproc.py
"""
import math
import os

import arabic_reshaper
import numpy as np
from bidi.algorithm import get_display
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = f"{HERE}/raw"
OUT = HERE
W, H = 1920, 1080
SS = 2
FONTS = os.path.normpath(f"{HERE}/../games/dejban/godot/assets/fonts")

BLUE_BG = (30, 44, 74)
BLUE_PANEL = (45, 66, 102)
BLUE_DEEP = (22, 32, 56)
GOLD = (242, 190, 60)
GOLD_TEXT = (255, 206, 64)
GREEN = (104, 196, 86)
RED = (214, 64, 56)
BLACK = (20, 16, 12)
WHITE = (255, 255, 255)

CARD_W, CARD_H = 250, 300
GRID_X0, GRID_Y0, GAP_X, GAP_Y = 495, 180, 90, 60


def S(v):
    return v * SS


def fa(t):
    return get_display(arabic_reshaper.reshape(t))


def font(weight, size):
    return ImageFont.truetype(f"{FONTS}/Vazirmatn-FD-{weight}.ttf", S(size))


def star_pts(cx, cy, ro, ri, n=5, rot=-math.pi / 2):
    pts = []
    for i in range(n * 2):
        r = ro if i % 2 == 0 else ri
        a = rot + i * math.pi / n
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def keyed_icon(path):
    im = Image.open(path).convert("RGBA")
    a = np.asarray(im).astype(np.float32)
    rgb = a[..., :3]
    border = np.concatenate([rgb[:2].reshape(-1, 3), rgb[-2:].reshape(-1, 3),
                             rgb[:, :2].reshape(-1, 3), rgb[:, -2:].reshape(-1, 3)])
    bg = np.median(border, axis=0)
    d = np.sqrt(((rgb - bg) ** 2).sum(axis=2))
    hard, soft = 90.0, 210.0
    alpha = np.full(d.shape, 255.0, np.float32)
    alpha[d < hard] = 0
    band = (d >= hard) & (d < soft)
    alpha[band] = np.clip((d[band] - hard) / (soft - hard), 0, 1) * 255
    af = np.clip(alpha / 255.0, 0, 1)
    safe = np.maximum(af, 1e-3)[..., None]
    rgb = np.clip((rgb - (1.0 - safe) * bg) / safe, 0, 255)
    alpha[af < 0.05] = 0
    out = Image.fromarray(np.dstack([rgb.astype(np.uint8), alpha.astype(np.uint8)]))
    ys, xs = np.nonzero(alpha > 8)
    return out.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def fit_icon(icon, bw, bh):
    iw, ih = icon.size
    s = min(S(bw) / iw, S(bh) / ih)
    return icon.resize((round(iw * s), round(ih * s)), Image.LANCZOS)


def shadow_rect(img, box, r, dy=6):
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle(tuple(S(v) for v in (box[0], box[1] + dy, box[2], box[3] + dy)),
                                         S(r), fill=(0, 0, 0, 90))
    img.alpha_composite(sh)


def panel(img, box, r, fill=BLUE_PANEL, gold=6, black=10):
    dr = ImageDraw.Draw(img)
    dr.rounded_rectangle(tuple(S(v) for v in box), S(r), fill=fill)
    dr.rounded_rectangle(tuple(S(v) for v in box), S(r), outline=BLACK, width=S(black))
    dr.rounded_rectangle(tuple(S(v) for v in box), S(r), outline=GOLD, width=S(gold))


def draw_coin(dr, cx, cy, r):
    dr.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(255, 202, 62), outline=BLACK, width=max(2, round(r * 0.22)))
    dr.ellipse((cx - r * 0.6, cy - r * 0.6, cx + r * 0.6, cy + r * 0.6),
               outline=(196, 138, 22), width=max(2, round(r * 0.16)))


def draw_star(dr, cx, cy, r):
    dr.polygon(star_pts(cx, cy, r, r * 0.46), fill=GOLD, outline=BLACK, width=max(2, round(r * 0.2)))


def draw_gear(dr, cx, cy, r):
    for i in range(8):
        a = i * math.pi / 4
        p = [(cx + math.cos(a + t) * r, cy + math.sin(a + t) * r) for t in (-0.22, 0.22)]
        p += [(cx + math.cos(a + 0.22) * r * 0.72, cy + math.sin(a + 0.22) * r * 0.72),
              (cx + math.cos(a - 0.22) * r * 0.72, cy + math.sin(a - 0.22) * r * 0.72)]
        dr.polygon(p, fill=GOLD, outline=BLACK, width=S(3))
    dr.ellipse((cx - r * 0.74, cy - r * 0.74, cx + r * 0.74, cy + r * 0.74), fill=GOLD, outline=BLACK, width=S(4))
    dr.ellipse((cx - r * 0.3, cy - r * 0.3, cx + r * 0.3, cy + r * 0.3), fill=BLUE_PANEL, outline=BLACK, width=S(3))


def draw_back_arrow(dr, cx, cy, s):
    # فلش بازگشت RTL: به سمت راست
    dr.polygon([(cx - s * 0.7, cy - s * 0.16), (cx + s * 0.15, cy - s * 0.16),
                (cx + s * 0.15, cy + s * 0.16), (cx - s * 0.7, cy + s * 0.16)],
               fill=WHITE, outline=BLACK, width=S(3))
    dr.polygon([(cx + s * 0.1, cy - s * 0.55), (cx + s * 0.75, cy), (cx + s * 0.1, cy + s * 0.55)],
               fill=WHITE, outline=BLACK, width=S(3))


def draw_plus(dr, cx, cy, s, color=WHITE):
    w = s * 0.3
    dr.rectangle((cx - s, cy - w, cx + s, cy + w), fill=color, outline=BLACK, width=S(3))
    dr.rectangle((cx - w, cy - s, cx + w, cy + s), fill=color, outline=BLACK, width=S(3))


def draw_gift(dr, cx, cy, s):
    dr.rectangle((cx - s, cy - s * 0.45, cx + s, cy + s), fill=RED, outline=BLACK, width=S(4))
    dr.rectangle((cx - s * 1.12, cy - s * 0.8, cx + s * 1.12, cy - s * 0.4), fill=(236, 90, 80), outline=BLACK, width=S(4))
    dr.rectangle((cx - s * 0.18, cy - s * 0.8, cx + s * 0.18, cy + s), fill=WHITE, outline=BLACK, width=S(3))
    dr.ellipse((cx - s * 0.62, cy - s * 1.18, cx - s * 0.06, cy - s * 0.72), outline=WHITE, width=S(4))
    dr.ellipse((cx + s * 0.06, cy - s * 1.18, cx + s * 0.62, cy - s * 0.72), outline=WHITE, width=S(4))


def draw_tag(dr, cx, cy, s):
    dr.polygon([(cx - s, cy - s * 0.6), (cx + s * 0.35, cy - s * 0.6), (cx + s, cy),
                (cx + s * 0.35, cy + s * 0.6), (cx - s, cy + s * 0.6)],
               fill=WHITE, outline=BLACK, width=S(4))
    dr.ellipse((cx - s * 0.72, cy - s * 0.24, cx - s * 0.28, cy + s * 0.24), fill=RED, outline=BLACK, width=S(3))


def draw_badge(dr, cx, cy, r, txt="۱"):
    dr.ellipse((cx - r, cy - r, cx + r, cy + r), fill=RED, outline=BLACK, width=S(4))
    dr.text((cx, cy + S(1)), fa(txt), font=font("Bold", 18), fill=WHITE, anchor="mm")


def pattern_overlay():
    ov = Image.new("RGBA", (S(W), S(H)), (0, 0, 0, 0))
    dr = ImageDraw.Draw(ov)
    col = (150, 180, 235, 17)
    step = S(240)
    k = 0
    for gy in range(S(120), S(H), step):
        for gx in range(S(120), S(W), step):
            gx2 = gx + (step // 2 if (k % 2) else 0)
            s = S(46)
            kind = k % 3
            if kind == 0:   # شمشیرهای متقاطع
                for ang in (math.pi / 4, -math.pi / 4):
                    dx, dy = math.cos(ang) * s, math.sin(ang) * s
                    dr.line((gx2 - dx, gy - dy, gx2 + dx, gy + dy), fill=col, width=S(9))
                    dr.ellipse((gx2 + dx * 0.8 - S(7), gy + dy * 0.8 - S(7),
                                gx2 + dx * 0.8 + S(7), gy + dy * 0.8 + S(7)), outline=col, width=S(5))
            elif kind == 1:  # سپر
                dr.polygon([(gx2 - s * 0.7, gy - s * 0.7), (gx2 + s * 0.7, gy - s * 0.7),
                            (gx2 + s * 0.7, gy + s * 0.1), (gx2, gy + s * 0.8),
                            (gx2 - s * 0.7, gy + s * 0.1)], outline=col, width=S(7))
            else:            # ستاره
                dr.polygon(star_pts(gx2, gy, s * 0.8, s * 0.37), outline=col, width=S(6))
            k += 1
    return ov


def radial_glow(w, h, color, strength=0.5):
    yy, xx = np.mgrid[0:h, 0:w]
    rx, ry = (w - 1) / 2, (h - 1) / 2
    r = np.sqrt(((xx - rx) / rx) ** 2 + ((yy - ry) / ry) ** 2)
    a = np.clip(1 - r, 0, 1) ** 1.6 * 255 * strength
    arr = np.dstack([np.full((h, w, 3), color, np.uint8), a.astype(np.uint8)])
    return Image.fromarray(arr)


CARDS = [
    dict(icon="medal", name="افزایش اعتبار", level="۱۵ / ۵۰", prog=15 / 50,
         reward="+۱۵۰ سکه شروع", cost=("star", "۱۰"), highlight=True),
    dict(icon="coins", name="سکه شروع", level="۸ / ۲۰",
         reward="+۸۰ سکه", cost=("coin", "۲۰۰")),
    dict(icon="soldier", name="قدرت سرباز", level="۵ / ۱۰",
         reward="+۲۵٪ آسیب", cost=("coin", "۳۰۰")),
    dict(icon="jeep", name="سرعت جیپ", level="۳ / ۱۰",
         reward="+۱۵٪ سرعت", cost=("coin", "۲۵۰")),
    dict(icon="tank", name="زره تانک", level="۴ / ۱۰",
         reward="+۲ جان برای تانک", cost=("coin", "۴۰۰")),
    dict(icon="tower", name="آسیب برج", level="۲ / ۱۰",
         reward="+۱۰٪ آسیب برج", cost=("coin", "۵۰۰")),
]
ICON_FILES = {
    "medal": "../item-cards/raw/medal_icon_raw.png",
    "coins": "raw/shop_icon_coins_raw.png",
    "soldier": "raw/shop_icon_soldier_raw.png",
    "jeep": "raw/shop_icon_jeep_raw.png",
    "tank": "raw/shop_icon_tank_raw.png",
    "tower": "raw/shop_icon_tower_raw.png",
}


def build():
    img = Image.new("RGBA", (S(W), S(H)), BLUE_BG + (255,))
    dr = ImageDraw.Draw(img)
    # الگوی نظامی ظریف + قاب طلایی
    img.alpha_composite(pattern_overlay())
    dr = ImageDraw.Draw(img)
    dr.rounded_rectangle((S(12), S(12), S(W - 12), S(H - 12)), S(30), outline=BLACK, width=S(14))
    dr.rounded_rectangle((S(12), S(12), S(W - 12), S(H - 12)), S(30), outline=GOLD, width=S(8))
    for cx, cy in [(44, 44), (W - 44, 44), (44, H - 44), (W - 44, H - 44)]:
        dr.ellipse((S(cx - 13), S(cy - 13), S(cx + 13), S(cy + 13)), fill=GOLD, outline=BLACK, width=S(4))

    # نوار بالا
    shadow_rect(img, (36, 36, 120, 120), 22)
    panel(img, (36, 36, 120, 120), 22)
    draw_back_arrow(ImageDraw.Draw(img), S(78), S(78), S(30))
    shadow_rect(img, (W - 120, 36, W - 36, 120), 22)
    panel(img, (W - 120, 36, W - 36, 120), 22)
    draw_gear(ImageDraw.Draw(img), S(W - 78), S(78), S(34))

    dr = ImageDraw.Draw(img)
    dr.text((S(W / 2), S(82)), fa("ارتقاها"), font=font("Bold", 60), fill=GOLD_TEXT,
            anchor="mm", stroke_width=S(9), stroke_fill=BLACK)

    # شمارندهٔ ستاره (چپ) و سکه (راست)
    for box, kind, value in [((150, 46, 330, 110), "star", "۴۵"),
                             ((W - 330, 46, W - 150, 46 + 64), "coin", "۱۲۵۰")]:
        shadow_rect(img, box, 32, 5)
        panel(img, box, 32, fill=BLUE_DEEP, gold=5, black=8)
        dr = ImageDraw.Draw(img)
        icx = S(box[2] - 34)
        if kind == "star":
            draw_star(dr, icx, S(78), S(21))
        else:
            draw_coin(dr, icx, S(78), S(20))
        dr.text((icx - S(30), S(79)), fa(value), font=font("Bold", 34), fill=GOLD_TEXT,
                anchor="rm", stroke_width=S(5), stroke_fill=BLACK)

    # شبکهٔ کارت‌ها
    icons = {k: keyed_icon(os.path.normpath(f"{HERE}/{p}")) for k, p in ICON_FILES.items()}
    f_name, f_lvl, f_rw, f_cost, f_btn = (font("Bold", 26), font("Bold", 22),
                                          font("Bold", 19), font("Bold", 20), font("Bold", 22))
    for i, c in enumerate(CARDS):
        col, row = i % 3, i // 3
        x0 = GRID_X0 + col * (CARD_W + GAP_X)
        y0 = GRID_Y0 + row * (CARD_H + GAP_Y)
        box = (x0, y0, x0 + CARD_W, y0 + CARD_H)
        if c.get("highlight"):
            glow = radial_glow(S(CARD_W + 150), S(CARD_H + 150), (255, 214, 90), 0.55)
            img.paste(glow, (S(x0) - S(75), S(y0) - S(75)), glow)
        shadow_rect(img, box, 26)
        panel(img, box, 26, gold=8 if c.get("highlight") else 6)
        dr = ImageDraw.Draw(img)
        cx = x0 + CARD_W / 2
        # آیکن
        ib = (92, 80) if c.get("prog") is not None else (104, 92)
        iy = y0 + (68 if c.get("prog") is not None else 78)
        ic = fit_icon(icons[c["icon"]], *ib)
        img.paste(ic, (round(S(cx)) - ic.size[0] // 2, round(S(iy)) - ic.size[1] // 2), ic)
        dr = ImageDraw.Draw(img)
        # نام / سطح
        ny = y0 + (128 if c.get("prog") is not None else 140)
        dr.text((S(cx), S(ny)), fa(c["name"]), font=f_name, fill=WHITE, anchor="mm",
                stroke_width=S(5), stroke_fill=BLACK)
        ly = ny + 28
        dr.text((S(cx), S(ly)), fa(c["level"]), font=f_lvl, fill=GOLD_TEXT, anchor="mm",
                stroke_width=S(4), stroke_fill=BLACK)
        yy = ly + 26
        # نوار پیشرفت فقط کارت اول
        if c.get("prog") is not None:
            bx0, by0, bx1, by1 = S(x0 + 30), S(yy - 10), S(x0 + CARD_W - 30), S(yy + 10)
            dr.rounded_rectangle((bx0, by0, bx1, by1), S(10), fill=BLUE_DEEP, outline=BLACK, width=S(4))
            fw = round((bx1 - bx0 - S(8)) * c["prog"])
            if fw > S(8):
                g = np.linspace(0, 1, fw)[None, :, None]
                colr = (np.asarray((70, 150, 255), float) * (1 - g) + np.asarray((255, 200, 60), float) * g)
                colr = np.repeat(colr, round(by1 - by0 - S(8)), axis=0).astype(np.uint8)
                arr = np.concatenate([colr, np.full((*colr.shape[:2], 1), 255, np.uint8)], axis=2)
                fill = Image.fromarray(arr)
                m = Image.new("L", fill.size, 0)
                ImageDraw.Draw(m).rounded_rectangle((0, 0, fill.size[0] - 1, fill.size[1] - 1), S(6), fill=255)
                img.paste(fill, (round(bx0 + S(4)), round(by0 + S(4))), m)
                dr = ImageDraw.Draw(img)
            yy += 24
        # پاداش
        dr.text((S(cx), S(yy + (6 if c.get("prog") is not None else 8))), fa(c["reward"]), font=f_rw, fill=(126, 226, 132), anchor="mm",
                stroke_width=S(4), stroke_fill=BLACK)
        # هزینه: آیکن چپ، عدد راست
        cost_kind, cost_val = c["cost"]
        tw = dr.textlength(fa(cost_val), font=f_cost)
        total = tw + S(8) + S(22)
        gx0 = S(cx) - total / 2
        if cost_kind == "star":
            draw_star(dr, gx0 + S(11), S(yy + (32 if c.get("prog") is not None else 36)), S(11))
        else:
            draw_coin(dr, gx0 + S(11), S(yy + (32 if c.get("prog") is not None else 36)), S(10))
        dr.text((gx0 + S(22) + S(8) + tw, S(yy + (32 if c.get("prog") is not None else 36))), fa(cost_val), font=f_cost, fill=GOLD_TEXT,
                anchor="rm", stroke_width=S(4), stroke_fill=BLACK)
        # دکمهٔ ارتقا
        bb = (x0 + 40, y0 + CARD_H - 46, x0 + CARD_W - 40, y0 + CARD_H - 8)
        shadow_rect(img, bb, 14, 4)
        dr = ImageDraw.Draw(img)
        dr.rounded_rectangle(tuple(S(v) for v in bb), S(14), fill=GREEN)
        dr.rounded_rectangle(tuple(S(v) for v in bb), S(14), outline=BLACK, width=S(6))
        bcx = (bb[0] + bb[2]) / 2
        if c.get("highlight"):
            draw_plus(dr, S(bcx + 52), S((bb[1] + bb[3]) / 2), S(9))
            dr.text((S(bcx - 12), S((bb[1] + bb[3]) / 2 + 1)), fa("ارتقا"), font=f_btn, fill=WHITE,
                    anchor="mm", stroke_width=S(4), stroke_fill=BLACK)
        else:
            dr.text((S(bcx), S((bb[1] + bb[3]) / 2 + 1)), fa("ارتقا"), font=f_btn, fill=WHITE,
                    anchor="mm", stroke_width=S(4), stroke_fill=BLACK)

    # ناحیهٔ پایین: پاداش روزانه (چپ) / پیشنهاد ویژه (راست)
    dr = ImageDraw.Draw(img)
    f_bb = font("Bold", 34)
    for box, kind, label in [((150, 880, 520, 980), "gift", "پاداش روزانه"),
                             ((W - 520, 880, W - 150, 980), "tag", "پیشنهاد ویژه")]:
        shadow_rect(img, box, 26)
        dr = ImageDraw.Draw(img)
        dr.rounded_rectangle(tuple(S(v) for v in box), S(26),
                             fill=GOLD if kind == "gift" else RED)
        dr.rounded_rectangle(tuple(S(v) for v in box), S(26), outline=BLACK, width=S(8))
        icx = box[2] - 52
        if kind == "gift":
            draw_gift(dr, S(icx), S(932), S(26))
            tcol = (96, 54, 8)
        else:
            draw_tag(dr, S(icx), S(930), S(26))
            tcol = WHITE
        if kind == "gift":
            dr.text((S(icx - 44), S(931)), fa(label), font=f_bb, fill=tcol, anchor="rm")
        else:
            dr.text((S(icx - 44), S(931)), fa(label), font=f_bb, fill=tcol, anchor="rm",
                    stroke_width=S(5), stroke_fill=BLACK)
        draw_badge(dr, S(box[2] - 6), S(box[1] + 6), S(17))

    return img.resize((W, H), Image.LANCZOS)


if __name__ == "__main__":
    out = build()
    out.save(f"{OUT}/upgrade_shop.png", optimize=True)
    print("upgrade_shop.png", out.size, out.mode)
