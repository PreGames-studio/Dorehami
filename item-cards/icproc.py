"""ساخت کارت آیتم «افزایش اعتبار (PRESTIGE BOOST)» — ۵۱۲×۵۱۲ شفاف، آمادهٔ Construct 3.

کارت به‌صورت رویه‌ای در مقیاس ۴برابر (سوپرسمپل) کشیده می‌شود و برای لبه‌های نرم
به ۵۱۲ xuống می‌شود: بدنهٔ فلزی آبی تیره با حاشیهٔ طلایی و گوشه‌های گرد، آیکن مدال
(از raw/ با حذف پس‌زمینهٔ ماژنتا)، متن‌های فارسی با فونت وزیرمتن FD (کشیده‌شده با
arabic_reshaper + python-bidi)، نوار پیشرفت با گرادیان آبی-طلایی و دکمهٔ طلایی.

Usage:  python3 icproc.py
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
FINAL = 512
SS = 4                      # سوپرسمپل
W = FINAL * SS
FONTS = os.path.normpath(f"{HERE}/../games/dejban/godot/assets/fonts")

# رنگ‌ها
BLUE_DARK = (33, 48, 78)
BLUE_PANEL = (45, 66, 102)
GOLD = (242, 190, 60)
GOLD_TEXT = (255, 206, 64)
GREEN = (126, 226, 132)
BLACK = (20, 16, 12)

# چیدمان در فضای ۵۱۲ (هنگام رسم در S() ضرب می‌شود)
CARD = (12, 12, 500, 500)
CARD_R = 46
ICON_C = (256, 134)
NAME_Y, NAME_S = 256, 44
LEVEL_Y, LEVEL_S = 300, 29
BAR = (86, 320, 426, 352)
BAR_R = 15
PROGRESS = 15 / 50
ROW_Y, ROW_S = 383, 29
BTN = (112, 408, 400, 478)
BTN_R = 22
BTN_TEXT_Y, BTN_TEXT_S = 443, 35


def S(v):
    return v * SS


def fa(text):
    """متن فارسی آمادهٔ رندر (پیوسته‌سازی + ترتیب bidi)."""
    return get_display(arabic_reshaper.reshape(text))


def font(name, size):
    return ImageFont.truetype(f"{FONTS}/Vazirmatn-FD-{name}.ttf", S(size))


def star_pts(cx, cy, r_out, r_in, n=5, rot=-math.pi / 2):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = rot + i * math.pi / n
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return pts


def keyed_icon(src):
    """حذف پس‌زمینهٔ ماژنتا با لبهٔ نرم (هالهٔ آبی مدال حفظ می‌شود)."""
    im = Image.open(src).convert("RGBA")
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
    ys, xs = np.nonzero(alpha > 8)          # برش به محتوای واقعی مدال
    return out.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def vgradient(w, h, c0, c1):
    g = np.linspace(0, 1, h)[:, None, None]
    col = np.asarray(c0, float) * (1 - g) + np.asarray(c1, float) * g
    col = np.repeat(col, w, axis=1).astype(np.uint8)
    arr = np.concatenate([col, np.full((h, w, 1), 255, np.uint8)], axis=2)
    return Image.fromarray(arr)


def hgradient(w, h, c0, c1):
    g = np.linspace(0, 1, w)[None, :, None]
    col = np.asarray(c0, float) * (1 - g) + np.asarray(c1, float) * g
    col = np.repeat(col, h, axis=0).astype(np.uint8)
    arr = np.concatenate([col, np.full((h, w, 1), 255, np.uint8)], axis=2)
    return Image.fromarray(arr)


def rounded_mask(size, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius, fill=255)
    return m


def draw_coin(dr, cx, cy, r):
    dr.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(255, 202, 62), outline=BLACK, width=S(4))
    dr.ellipse((cx - r * 0.62, cy - r * 0.62, cx + r * 0.62, cy + r * 0.62),
               outline=(196, 138, 22), width=S(3))


def build():
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)

    # بدنهٔ کارت: پرکردن + پنل فلزی + پرچ‌ها، سپس حاشیهٔ طلایی با لبهٔ مشکی
    dr.rounded_rectangle(tuple(S(v) for v in CARD), S(CARD_R), fill=BLUE_DARK)
    dr.rounded_rectangle(tuple(S(v) for v in (26, 26, 486, 486)), S(CARD_R - 10), fill=BLUE_PANEL)
    for cx, cy in [(40, 40), (472, 40), (40, 472), (472, 472)]:
        dr.ellipse((S(cx - 7), S(cy - 7), S(cx + 7), S(cy + 7)), fill=GOLD, outline=BLACK, width=S(3))
    dr.rounded_rectangle(tuple(S(v) for v in CARD), S(CARD_R), outline=BLACK, width=S(14))
    dr.rounded_rectangle(tuple(S(v) for v in CARD), S(CARD_R), outline=GOLD, width=S(8))

    # آیکن مدال
    icon = keyed_icon(f"{RAW}/medal_icon_raw.png")
    iw, ih = icon.size
    scale = min(S(200) / iw, S(185) / ih)
    nw, nh = round(iw * scale), round(ih * scale)
    icon = icon.resize((nw, nh), Image.LANCZOS)
    img.paste(icon, (S(ICON_C[0]) - nw // 2, S(ICON_C[1]) - nh // 2), icon)
    dr = ImageDraw.Draw(img)

    f_bold = font("Bold", NAME_S)
    f_med = font("Bold", LEVEL_S)
    f_row = font("Bold", ROW_S)
    f_btn = font("Bold", BTN_TEXT_S)

    # نام آیتم
    dr.text((S(256), S(NAME_Y)), fa("افزایش اعتبار"), font=f_bold, fill=(255, 255, 255),
            anchor="mm", stroke_width=S(6), stroke_fill=BLACK)
    # سطح
    dr.text((S(256), S(LEVEL_Y)), fa("سطح ۱۵ / ۵۰"), font=f_med, fill=GOLD_TEXT,
            anchor="mm", stroke_width=S(5), stroke_fill=BLACK)

    # نوار پیشرفت: 트랙 + پرشدگی گرادیان آبی→طلایی
    bx0, by0, bx1, by1 = (S(v) for v in BAR)
    dr.rounded_rectangle((bx0, by0, bx1, by1), S(BAR_R), fill=(22, 30, 50), outline=BLACK, width=S(5))
    fw = round((bx1 - bx0 - S(10)) * PROGRESS)
    if fw > S(10):
        fill = hgradient(fw, round(by1 - by0 - S(10)), (70, 150, 255), (255, 200, 60))
        mask = rounded_mask(fill.size, S(BAR_R - 5))
        img.paste(fill, (round(bx0 + S(5)), round(by0 + S(5))), mask)
        dr = ImageDraw.Draw(img)

    # ردیف پاداش (راست) و هزینه (چپ)
    reward_txt = fa("+۱۵۰ سکه شروع")
    cost_txt = fa("۱۰")
    rw = dr.textlength(reward_txt, font=f_row)
    cw = dr.textlength(cost_txt, font=f_row)
    coin_d, star_r, gap = S(30), S(15), S(8)
    reward_w = coin_d + gap + rw
    cost_w = cw + S(6) + star_r * 2
    total = reward_w + S(26) + cost_w
    x0 = S(256) - total / 2
    # هزینه: ستاره چپ، عدد راست
    cx_star = x0 + star_r
    dr.polygon(star_pts(cx_star, S(ROW_Y), star_r, star_r * 0.46), fill=GOLD, outline=BLACK, width=S(3))
    dr.text((x0 + star_r * 2 + S(6) + cw, S(ROW_Y)), cost_txt, font=f_row, fill=(255, 224, 120),
            anchor="rm", stroke_width=S(5), stroke_fill=BLACK)
    # پاداش: سکه راست، متن چپِ آن
    gx1 = x0 + total                      # لبهٔ راست گروه پاداش
    draw_coin(dr, gx1 - coin_d / 2, S(ROW_Y), coin_d / 2)
    dr.text((gx1 - coin_d - gap, S(ROW_Y)), reward_txt, font=f_row, fill=GREEN,
            anchor="rm", stroke_width=S(5), stroke_fill=BLACK)

    # دکمهٔ ارتقا: گرادیان طلایی عمودی + حاشیهٔ مشکی
    bx0, by0, bx1, by1 = (S(v) for v in BTN)
    grad = vgradient(round(bx1 - bx0), round(by1 - by0), (255, 218, 96), (224, 156, 24))
    mask = rounded_mask(grad.size, S(BTN_R))
    img.paste(grad, (round(bx0), round(by0)), mask)
    dr = ImageDraw.Draw(img)
    dr.rounded_rectangle((bx0, by0, bx1, by1), S(BTN_R), outline=BLACK, width=S(7))
    dr.text((S(256), S(BTN_TEXT_Y)), fa("ارتقا"), font=f_btn, fill=(96, 54, 8), anchor="mm")

    return img.resize((FINAL, FINAL), Image.LANCZOS)


def preview(card, path):
    sheet = Image.new("RGBA", (600, 600))
    px = sheet.load()
    for y in range(600):
        for x in range(600):
            px[x, y] = (200, 200, 200, 255) if (x // 20 + y // 20) % 2 else (245, 245, 245, 255)
    sheet.paste(card, (44, 44), card)
    sheet.save(path, optimize=True)


if __name__ == "__main__":
    card = build()
    card.save(f"{OUT}/prestige_boost_card.png", optimize=True)
    preview(card, f"{OUT}/preview_prestige_boost.png")
    print("prestige_boost_card.png", card.size, card.mode)
