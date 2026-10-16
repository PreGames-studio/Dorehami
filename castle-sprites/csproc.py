"""پردازش اسپرایت‌های قلعه: از raw/ (پس‌زمینهٔ ماژنتا) خروجی شفاف ۵۱۲×۵۱۲ می‌سازد.

روش: رنگ پس‌زمینه از میانهٔ پیکسل‌های حاشیه خوانده می‌شود، ناحیهٔ متصل به حاشیه
حذف می‌شود، لبه‌ها آلفای نیمه‌شفاف (ضدلبهٔ نرم) می‌گیرند و رنگ لبه از آلودگی
پس‌زمینه پاک می‌شود. سپس محتوا بریده می‌شود و با مقیاس پلکانی هر سطح روی
بوم ۵۱۲×۵۱۲ شفاف، هم‌تراز کف و وسط، می‌نشیند. خروجی آمادهٔ Construct 3 است.

Usage:  python3 csproc.py
"""
import os

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
RAW = f"{HERE}/raw"
OUT = HERE
SIZE = 512
BOTTOM_MARGIN = 8          # فاصلهٔ کف قلعه تا لبهٔ پایین بوم
# پیشرفت اندازهٔ ظاهری قلعه بین سطح‌ها: کوچیک ← عظیم.
# پهنا معیار است چون سیلوئت قلعه در سطح‌های بالا پهن‌تر می‌شود.
WIDTH_PX = {1: 340, 2: 410, 3: 445, 4: 478, 5: 497}
MAX_HEIGHT_PX = SIZE - 2 * BOTTOM_MARGIN
BG_HARD, BG_SOFT = 90.0, 200.0   # آستانهٔ حذف قطعی و باند لبهٔ نرم


def keyed_alpha(src):
    """حذف پس‌زمینهٔ تک‌رنگ: (rgb, alpha) برمی‌گرداند."""
    im = Image.open(src).convert("RGBA")
    a = np.asarray(im).astype(np.float32)
    rgb, alpha_in = a[..., :3], a[..., 3]
    border = np.concatenate([rgb[:2].reshape(-1, 3), rgb[-2:].reshape(-1, 3),
                             rgb[:, :2].reshape(-1, 3), rgb[:, -2:].reshape(-1, 3)])
    bg = np.median(border, axis=0)
    d = np.sqrt(((rgb - bg) ** 2).sum(axis=2))

    close = d < BG_SOFT
    lab, n = ndimage.label(close)
    edge_ids = set(np.unique(np.concatenate(
        [lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    outer = np.isin(lab, list(edge_ids))

    alpha = np.where(alpha_in > 0, 255, 0).astype(np.float32)
    # حذف قطعی پس‌زمینهٔ بیرونی + حفره‌های بستهٔ هم‌رنگ پس‌زمینه
    alpha[outer & (d < BG_HARD)] = 0
    alpha[(~outer) & (d < BG_HARD) & (alpha_in > 0)] = 0
    # باند لبهٔ نرم فقط روی ناحیهٔ بیرونی (ضدلبهٔ AA و هالهٔ درخشش)
    band = outer & (d >= BG_HARD) & (d < BG_SOFT)
    alpha[band] = np.clip((d[band] - BG_HARD) / (BG_SOFT - BG_HARD), 0, 1) * 255

    # پاک‌سازی رنگ لبه از آلودگی پس‌زمینه (un-premultiply نسبت به bg)
    af = np.clip(alpha / 255.0, 0, 1)
    safe = np.maximum(af, 1e-3)[..., None]
    rgb = np.clip((rgb - (1.0 - safe) * bg) / safe, 0, 255)
    alpha[af < 0.04] = 0

    # حذف خال‌های سرگردان
    m = alpha > 0
    lab, n = ndimage.label(m)
    if n > 1:
        sizes = ndimage.sum(m, lab, range(1, n + 1))
        keep = [i for i in range(1, n + 1)
                if sizes[i - 1] >= max(64, 0.0005 * sizes.sum())]
        alpha[~np.isin(lab, keep)] = 0
    return rgb.astype(np.uint8), alpha.astype(np.uint8)


def fit_canvas(rgb, alpha, target_w):
    """برش به محتوا، مقیاس به پهنای هدف، هم‌تراز کف و وسط روی بوم شفاف."""
    ys, xs = np.nonzero(alpha > 8)
    rgb = rgb[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    alpha = alpha[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    h, w = alpha.shape
    scale = min(target_w / w, MAX_HEIGHT_PX / h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    spr = Image.fromarray(np.dstack([rgb, alpha[..., None]]))
    spr = spr.resize((nw, nh), Image.LANCZOS)
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.paste(spr, ((SIZE - nw) // 2, SIZE - BOTTOM_MARGIN - nh), spr)
    return canvas


def preview(castles, path):
    """ورق پیش‌نمایش روی زمینهٔ شطرنجی تا شفافیت دیده شود."""
    cell, pad = 256, 8
    sheet = Image.new("RGBA", (cell * len(castles), cell + 24), (0, 0, 0, 0))
    px = sheet.load()
    for y in range(sheet.size[1]):
        for x in range(sheet.size[0]):
            px[x, y] = (200, 200, 200, 255) if (x // 16 + y // 16) % 2 else (245, 245, 245, 255)
    dr = ImageDraw.Draw(sheet)
    for i, c in enumerate(castles):
        thumb = c.resize((cell - 2 * pad, cell - 2 * pad), Image.LANCZOS)
        sheet.paste(thumb, (i * cell + pad, pad), thumb)
        dr.text((i * cell + cell // 2 - 8, cell + 4), f"L{i + 1}", fill=(40, 40, 40, 255))
    sheet.save(path, optimize=True)


if __name__ == "__main__":
    sheets = []
    for lv in range(1, 6):
        rgb, alpha = keyed_alpha(f"{RAW}/castle_level_{lv}_raw.png")
        out = fit_canvas(rgb, alpha, WIDTH_PX[lv])
        out.save(f"{OUT}/castle_level_{lv}.png", optimize=True)
        sheets.append(out)
        print(f"castle_level_{lv}.png", out.size, out.mode)
    preview(sheets, f"{OUT}/preview_castles.png")
    print("preview_castles.png written")
