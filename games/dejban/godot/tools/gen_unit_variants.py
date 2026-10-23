#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""تولید شیت‌های باکیفیت و هم‌تراز برای نیروهای دشمن جدید از روی شیت‌های اصلی.

ورودی‌ها (دست‌نخورده باقی می‌مانند):
  - assets/sprites/units/soldier.png
  - assets/sprites/units/soldier_walk.png
  - assets/sprites/units/run/soldier_{front,back,left,right}.png
  - assets/sprites/units/death/soldier_{front,back,left,right}.png
  - assets/sprites/units/iso_tank/{atlas_tank_iso,tank_idle,tank_move,tank_attack}.png

خروجی‌ها (با ابعاد، محور زمین و فریم‌بندی ۱۰۰٪ یکسان با نیروهای اصلی):
  ۱) تکاور (commando) — کت آبی تاکتیکی و کلاه‌خود فولادی تیره (حفظ کامل پوست، چشم، چرم و نشان طلایی)
  ۲) تانک زرهی (tank_armored) — زره آبی کبالتی + پرچم فیروزه‌ای + لیزر فیروزه‌ای درخشان
  ۳) تانک سنگین (tank_heavy) — زره سرخ-زرشکی تیره + لیزر پلاسمای بنفش-ارغوانی
"""

from __future__ import annotations

import os
from PIL import Image
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UNITS_DIR = os.path.join(ROOT, "assets", "sprites", "units")


def smoothstep(edge0: float, edge1: float, x: np.ndarray) -> np.ndarray:
    t = np.clip((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def recolor_soldier(img: Image.Image) -> Image.Image:
    """تغییر رنگ لباس و کلاه‌خود سرباز به آبی تاکتیکی و فولادی بدون دست‌زدن به پوست، ابرو، چرم و نشان طلایی."""
    arr = np.array(img, dtype=np.float32)
    r, g, b, a = arr[..., 0], arr[..., 1], arr[..., 2], arr[..., 3]
    vis = a > 4.0

    maxc = np.maximum(np.maximum(r, g), b)
    minc = np.minimum(np.minimum(r, g), b)
    sat = (maxc - minc) / np.maximum(maxc, 1.0)

    yellow_ratio = (g - b) / np.maximum(r - b, 1.0)
    red_dom = r - np.maximum(g, b)

    not_bright_skin = 1.0 - smoothstep(125.0, 150.0, g) * smoothstep(195.0, 220.0, r)
    not_dark_ink = smoothstep(56.0, 78.0, r)
    not_warm_brown = 1.0 - smoothstep(4.0, 10.0, g - b) * (1.0 - smoothstep(115.0, 145.0, r))

    mask = (
        smoothstep(0.24, 0.12, yellow_ratio)
        * smoothstep(18.0, 34.0, red_dom)
        * not_bright_skin
        * not_dark_ink
        * not_warm_brown
    )
    mask = np.where(vis, mask, 0.0)

    coat_w = smoothstep(0.52, 0.66, sat)
    lum = (0.34 * r + 0.46 * g + 0.20 * b) / 255.0

    coat_r = 255.0 * np.clip(0.05 + 0.28 * (lum ** 1.15), 0.0, 1.0)
    coat_g = 255.0 * np.clip(0.18 + 0.72 * (lum ** 1.00), 0.0, 1.0)
    coat_b = 255.0 * np.clip(0.38 + 1.18 * (lum ** 0.84), 0.0, 1.0)

    helm_r = 255.0 * np.clip(0.09 + 0.56 * (lum ** 1.06), 0.0, 1.0)
    helm_g = 255.0 * np.clip(0.13 + 0.66 * (lum ** 1.02), 0.0, 1.0)
    helm_b = 255.0 * np.clip(0.20 + 0.80 * (lum ** 0.95), 0.0, 1.0)

    target_r = coat_w * coat_r + (1.0 - coat_w) * helm_r
    target_g = coat_w * coat_g + (1.0 - coat_w) * helm_g
    target_b = coat_w * coat_b + (1.0 - coat_w) * helm_b

    out = arr.copy()
    out[..., 0] = r * (1.0 - mask) + target_r * mask
    out[..., 1] = g * (1.0 - mask) + target_g * mask
    out[..., 2] = b * (1.0 - mask) + target_b * mask
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


def recolor_tank(img: Image.Image, theme: str) -> Image.Image:
    """تغییر رنگ بدنه، پرچم و پرتو انرژی تانک ایزومتریک با حفظ کامل چشم‌ها، دود اگزوز، سایه و خطوط جوهر."""
    arr = np.array(img, dtype=np.float32)
    r, g, b, a = arr[..., 0], arr[..., 1], arr[..., 2], arr[..., 3]
    vis = a > 2.0

    maxc = np.maximum(np.maximum(r, g), b)
    minc = np.minimum(np.minimum(r, g), b)
    sat = (maxc - minc) / np.maximum(maxc, 1.0)
    lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0

    red_glow = smoothstep(18.0, 42.0, r - np.maximum(g, b)) * smoothstep(0.24, 0.45, sat)
    amber = (
        smoothstep(18.0, 40.0, r - b)
        * smoothstep(0.22, 0.40, sat)
        * smoothstep(0.22, 0.42, (g - b) / np.maximum(r - b, 1.0))
        * (1.0 - red_glow)
    )
    cream_plate = (
        smoothstep(7.5, 15.0, r - b)
        * smoothstep(0.42, 0.58, lum)
        * smoothstep(0.30, 0.16, sat)
        * smoothstep(170.0, 220.0, a)
        * (1.0 - red_glow)
        * (1.0 - amber)
    )
    steel_mid = (
        smoothstep(0.18, 0.30, lum)
        * smoothstep(0.84, 0.68, lum)
        * smoothstep(0.20, 0.08, sat)
        * smoothstep(200.0, 240.0, a)
        * (1.0 - cream_plate)
        * (1.0 - red_glow)
        * (1.0 - amber)
    )

    if theme == "armored":
        plate_rgb = np.stack([
            255.0 * np.clip(0.16 + 0.46 * (lum ** 1.15), 0.0, 1.0),
            255.0 * np.clip(0.32 + 0.58 * (lum ** 1.00), 0.0, 1.0),
            255.0 * np.clip(0.55 + 0.56 * (lum ** 0.85), 0.0, 1.0),
        ], axis=-1)
        steel_rgb = np.stack([r * 0.78, g * 0.90, np.clip(b * 1.18 + 8.0, 0, 255)], axis=-1)
        amber_rgb = np.stack([g * 0.35, np.clip(r * 0.88, 0, 255), np.clip(r * 1.08 + 18.0, 0, 255)], axis=-1)
        laser_rgb = np.stack([
            b * 0.85 + r * 0.10,
            np.clip(g * 0.35 + r * 0.78, 0, 255),
            np.clip(r * 1.05 + 16.0, 0, 255),
        ], axis=-1)
    elif theme == "heavy":
        plate_rgb = np.stack([
            255.0 * np.clip(0.30 + 0.68 * (lum ** 0.92), 0.0, 1.0),
            255.0 * np.clip(0.08 + 0.24 * (lum ** 1.25), 0.0, 1.0),
            255.0 * np.clip(0.12 + 0.28 * (lum ** 1.20), 0.0, 1.0),
        ], axis=-1)
        steel_rgb = np.stack([np.clip(r * 0.95 + 10.0, 0, 255), g * 0.78, b * 0.86], axis=-1)
        amber_rgb = np.stack([np.clip(r * 1.06 + 16.0, 0, 255), np.clip(r * 0.84 + 10.0, 0, 255), b * 0.45], axis=-1)
        laser_rgb = np.stack([
            np.clip(r * 0.96 + 10.0, 0, 255),
            b * 0.75 + g * 0.20,
            np.clip(r * 1.02 + 22.0, 0, 255),
        ], axis=-1)
    else:
        raise ValueError(f"Unknown tank theme: {theme}")

    rgb = arr[..., :3].copy()
    for w, target in ((steel_mid, steel_rgb), (cream_plate, plate_rgb), (amber, amber_rgb), (red_glow, laser_rgb)):
        w3 = (w * vis)[..., None]
        rgb = rgb * (1.0 - w3) + target * w3

    out = arr.copy()
    out[..., :3] = rgb
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8), "RGBA")


def save_png(img: Image.Image, path: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, format="PNG", optimize=True)


def main() -> None:
    # ۱) تکاور (commando)
    soldier_pairs = [
        ("soldier.png", "commando.png"),
        ("soldier_walk.png", "commando_walk.png"),
    ]
    for d in ("front", "back", "left", "right"):
        soldier_pairs.append((f"run/soldier_{d}.png", f"run/commando_{d}.png"))
        soldier_pairs.append((f"death/soldier_{d}.png", f"death/commando_{d}.png"))

    for src_rel, dst_rel in soldier_pairs:
        src = Image.open(os.path.join(UNITS_DIR, src_rel)).convert("RGBA")
        save_png(recolor_soldier(src), os.path.join(UNITS_DIR, dst_rel))

    # ۲ و ۳) تانک زرهی (tank_armored) و تانک سنگین (tank_heavy)
    for unit_id, theme in (("tank_armored", "armored"), ("tank_heavy", "heavy")):
        src_dir = os.path.join(UNITS_DIR, "iso_tank")
        dst_dir = os.path.join(UNITS_DIR, f"iso_{unit_id}")
        file_map = {
            "atlas_tank_iso.png": f"atlas_{unit_id}_iso.png",
            "tank_idle.png": f"{unit_id}_idle.png",
            "tank_move.png": f"{unit_id}_move.png",
            "tank_attack.png": f"{unit_id}_attack.png",
        }
        for src_name, dst_name in file_map.items():
            src = Image.open(os.path.join(src_dir, src_name)).convert("RGBA")
            save_png(recolor_tank(src, theme), os.path.join(dst_dir, dst_name))

    print("تمام شیت‌های تکاور، تانک زرهی و تانک سنگین با موفقیت ساخته شدند.")


if __name__ == "__main__":
    main()
