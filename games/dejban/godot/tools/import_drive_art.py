#!/usr/bin/env python3
"""استخراج هنر بازی از شیت‌های گوگل‌درایو.

تصویرهای منبع در tools/art_source/ نگه داشته می‌شوند و این اسکریپت آن‌ها را
به اسپرایت‌های بازی تبدیل می‌کند:

    python3 tools/import_drive_art.py

  sheet_towers.png : شیت ۶×۵ برجک‌های شماره‌دار (۱ تا ۳۰)
  sheet_units.png  : شیت ۵×۴ یگان‌ها (۱ تا ۲۰)

خروجی‌ها:
  assets/sprites/towers/head_<id>.png   سرِ برجک هر سلاح
  assets/sprites/units/<id>.png         اسپرایت هر دشمن
  assets/icons/w_<id>.png               آیکون دکمهٔ سلاح در نوار پایین

نشان شمارهٔ گوشهٔ خانه‌های شیت برجک‌ها خودکار پاک می‌شود: مؤلفه‌های کوچک
چسبیده در گوشهٔ بالا-چپ هر خانه حذف می‌شوند.
"""

from __future__ import annotations

import os
import sys

try:
    import numpy as np
    from PIL import Image
    from scipy import ndimage
except ImportError:  # pragma: no cover
    print("Pillow، numpy و scipy لازم است:  pip install pillow numpy scipy", file=sys.stderr)
    raise

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "tools", "art_source")
SPRITES = os.path.join(ROOT, "assets", "sprites")
ICONS = os.path.join(ROOT, "assets", "icons")

TOWER_SHEET = os.path.join(SOURCE, "sheet_towers.png")
UNIT_SHEET = os.path.join(SOURCE, "sheet_units.png")

# --------------------------------------------------------------- نگاشت سلاح‌ها

# شناسهٔ سلاح → شمارهٔ خانه در شیت برجک‌ها (۱ تا ۳۰)
TOWER_CELLS = {
    "bow": 1,          # توپ طلایی؛ برجک پایه
    "spear": 7,        # مسلسل سبز؛ شلیک پی‌درپی
    "catapult": 19,    # هویتزر سنگین؛ پرتابهٔ ناحیه‌ای
    "longbow": 18,     # تفنگ نشانه‌رو؛ برد دراز
    "zubin": 30,       # نیزهٔ بنفش؛ نفوذ در زره
    "firepot": 3,      # شعله‌افکن طلایی؛ سوزاندن
    "frost": 5,        # توپ یخی؛ کندکردن
    "thunder": 22,     # قوس آذرخش؛ زنجیره‌ای
    "venom": 9,        # اسید سبز؛ زهر
    "falcon": 23,      # سه برجک کوچک؛ شلیک تند و ضعیف
    "breaker": 26,     # کویل نارنجی سنگین؛ ضربهٔ کوبنده
    "sniper": 17,      # پرتو بنفش دقیق؛ تک‌تیرانداز
    "firecat": 16,     # شعله‌افکن دوم؛ جهش آتشین
    "scythe": 11,      # حلقه‌های موج؛ آسیب aura
    "volley": 4,       # چهار موشک؛ چهار تیر
    "triplet": 10,     # لولهٔ چندتیر؛ سه تیر
    "pyre": 2,         # توپ انرژی سرخ؛ کورهٔ بزرگ
    "eye": 6,          # برج طوفان؛ aura و کندی
}

# --------------------------------------------------------------- نگاشت یگان‌ها

# شناسهٔ دشمن → (شمارهٔ خانه، ارتفاع هدف به پیکسل، آینه‌کردن افقی)
# خانه‌ها از چپ به راست و بالا به پایین شمرده می‌شوند (۵ ستون × ۴ سطر).
UNIT_CELLS = {
    "grunt": (1, 46, False),      # سرباز تفنگ‌دار
    "runner": (3, 46, False),     # سرباز دونده با آرپی‌جی
    "armored": (7, 52, False),    # تانک سنگین تیره
    "shield": (11, 50, True),     # سپردار (در شیت رو به چپ است)
    "archer": (4, 48, False),     # تک‌تیرانداز استتارشده
    "healer": (13, 52, True),     # جادوگر بنفش
    "flyer": (16, 46, False),     # بالگرد
    "commander": (19, 62, False),  # ربات بزرگ
    "brute": (14, 60, False),     # غول سبز
    "warlord": (20, 72, True),    # قطار زره‌پوش (در شیت رو به چپ است)
}

TOWER_HEIGHT = 62      # ارتفاع نهایی سرِ برجک
ICON_SIZE = 72         # اندازهٔ آیکون دکمهٔ سلاح


def load_rgba(path: str) -> Image.Image:
    return Image.open(path).convert("RGBA")


def trim(image: Image.Image, pad: int = 2) -> Image.Image:
    """برش دور محتوای غیرشفاف."""
    box = image.getbbox()
    if box is None:
        return image
    left = max(0, box[0] - pad)
    top = max(0, box[1] - pad)
    right = min(image.width, box[2] + pad)
    bottom = min(image.height, box[3] + pad)
    return image.crop((left, top, right, bottom))


def fit_height(image: Image.Image, height: int) -> Image.Image:
    """تغییر اندازه با حفظ نسبت تا ارتفاع خواسته‌شده."""
    width = max(1, int(round(image.width * height / image.height)))
    return image.resize((width, height), Image.LANCZOS)


def drop_corner_badges(cell: Image.Image) -> Image.Image:
    """پاک‌کردن نشان شمارهٔ گوشهٔ بالا-چپ خانه (مربع تیره با رقم سفید)."""
    array = np.array(cell)
    mask = array[..., 3] > 100
    labels, count = ndimage.label(mask, structure=np.ones((3, 3)))
    if count == 0:
        return cell
    for index in range(1, count + 1):
        ys, xs = np.where(labels == index)
        size = ys.size
        if size > 6000 or size < 200:
            continue
        # نشان‌ها مربع‌های کوچکی در گوشهٔ بالا-چپ‌اند
        if xs.min() < 80 and ys.min() < 90:
            array[labels == index] = [0, 0, 0, 0]
    return Image.fromarray(array)


# --------------------------------------------------------------- شیت برجک‌ها

def slice_grid(sheet: Image.Image, cols: int, rows: int) -> list[Image.Image]:
    cell_w = sheet.width / cols
    cell_h = sheet.height / rows
    cells = []
    for row in range(rows):
        for col in range(cols):
            box = (
                round(col * cell_w), round(row * cell_h),
                round((col + 1) * cell_w), round((row + 1) * cell_h),
            )
            cells.append(sheet.crop(box))
    return cells


def unit_cells(sheet: Image.Image, cols: int, rows: int) -> list[Image.Image]:
    """خانه‌های شیت یگان‌ها؛ چیدمان نامنظم است، پس با مؤلفه‌های همبند پیدا می‌شود."""
    array = np.array(sheet)
    mask = array[..., 3] > 100
    mask = ndimage.binary_closing(mask, structure=np.ones((7, 7)))
    labels, count = ndimage.label(mask)
    total = array.shape[0] * array.shape[1]
    boxes = []
    for index in range(1, count + 1):
        size = int((labels == index).sum())
        if size < total * 0.004:
            continue
        ys, xs = np.where(labels == index)
        boxes.append([
            int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1,
            float(xs.mean()), float(ys.mean()),
        ])
    if len(boxes) != cols * rows:
        raise SystemExit(f"شیت یگان‌ها {len(boxes)} اسپرایت داشت، انتظار {cols * rows} بود.")

    # خوشه‌بندی سطری: مرکزهای y را به سطرها بخش می‌کنیم (چیدمان شبکه‌ای کاملاً یکدست نیست).
    boxes.sort(key=lambda b: b[5])
    clusters: list[list[list[float]]] = [[]]
    for box in boxes:
        if clusters[-1] and box[5] - clusters[-1][-1][5] > 60.0:
            clusters.append([])
        clusters[-1].append(box)
    if len(clusters) != rows:
        raise SystemExit(f"شیت یگان‌ها {len(clusters)} سطر داشت، انتظار {rows} بود.")

    out = []
    for cluster in clusters:
        cluster.sort(key=lambda b: b[4])
        for left, top, right, bottom, _, _ in cluster:
            pad = 6
            out.append(sheet.crop((
                max(0, left - pad), max(0, top - pad),
                min(sheet.width, right + pad), min(sheet.height, bottom + pad),
            )))
    return out


def main() -> int:
    for path in (TOWER_SHEET, UNIT_SHEET):
        if not os.path.exists(path):
            print(f"تصویر منبع پیدا نشد: {path}", file=sys.stderr)
            return 1

    towers_dir = os.path.join(SPRITES, "towers")
    units_dir = os.path.join(SPRITES, "units")
    os.makedirs(towers_dir, exist_ok=True)
    os.makedirs(units_dir, exist_ok=True)
    os.makedirs(ICONS, exist_ok=True)

    # ---------------------------------------------------------- برجک‌ها
    tower_sheet = load_rgba(TOWER_SHEET)
    cells = slice_grid(tower_sheet, 6, 5)
    print(f"شیت برجک‌ها: {tower_sheet.width}×{tower_sheet.height} → {len(cells)} خانه")
    for weapon_id, number in sorted(TOWER_CELLS.items()):
        if number < 1 or number > len(cells):
            print(f"شمارهٔ خانهٔ نامعتبر برای {weapon_id}: {number}", file=sys.stderr)
            return 1
        art = trim(drop_corner_badges(cells[number - 1]))
        head = fit_height(art, TOWER_HEIGHT)
        head.save(os.path.join(towers_dir, f"head_{weapon_id}.png"))
        icon = fit_height(art, ICON_SIZE - 8)
        canvas = Image.new("RGBA", (ICON_SIZE, ICON_SIZE), (0, 0, 0, 0))
        canvas.paste(icon, ((ICON_SIZE - icon.width) // 2, (ICON_SIZE - icon.height) // 2), icon)
        canvas.save(os.path.join(ICONS, f"w_{weapon_id}.png"))

    # ---------------------------------------------------------- یگان‌ها
    unit_sheet = load_rgba(UNIT_SHEET)
    cells = unit_cells(unit_sheet, 5, 4)
    print(f"شیت یگان‌ها: {unit_sheet.width}×{unit_sheet.height} → {len(cells)} خانه")
    for enemy_id, (number, height, mirror) in sorted(UNIT_CELLS.items()):
        if number < 1 or number > len(cells):
            print(f"شمارهٔ خانهٔ نامعتبر برای {enemy_id}: {number}", file=sys.stderr)
            return 1
        art = trim(cells[number - 1], pad=1)
        if mirror:
            art = art.transpose(Image.FLIP_LEFT_RIGHT)
        art = fit_height(art, height)
        art.save(os.path.join(units_dir, f"{enemy_id}.png"))

    print(f"ساخته شد: {len(TOWER_CELLS)} سرِ برجک، {len(UNIT_CELLS)} یگان و {len(TOWER_CELLS)} آیکون.")
    print("یادآوری: سپس tools/gen_assets_manifest.py را اجرا کن.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
