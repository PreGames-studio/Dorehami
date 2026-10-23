#!/usr/bin/env python3
"""ساخت اسپرایت‌شیت دشمن تاور دیفنس (تانک/ماشین جنگی) با نمای ایزومتریک.

همان دوربین و قرارداد برج‌های `assets/turrets_iso/`:

* دوربین از بالا و کمی مایل (۲:۱)؛ سطح رویی، جلو و کنارهٔ بدنه دیده می‌شود.
* هر فریم ۱۲۸×۱۲۸ و کاملاً شفاف است.
* شیت شبکه‌ای: **ردیف = جهت (۰..۷)، ستون = فریم**.

دشمن یک تانک چاق و سنگین با صفحات زرهی کرم/سفید، اتصالات مکانیکی خاکستری تیره،
دو چشم گنبدی درشت با ابروی فلزی (بامزه ولی ترسناک)، چراغ‌های قرمز/نارنجی درخشان
در جلو و دو پهلو و یک تیرک لیزر قرمز زیر چشم‌ها است. کل وسیله با جهت هدف می‌چرخد
و زنجیرها/چرخ‌ها در حرکت می‌غلتند.

    python3 tools/generate_iso_enemy_tank_sprites.py
    python3 tools/generate_iso_enemy_tank_sprites.py --no-gif

خروجی در `assets/enemy_tank_iso/`: سه شیت، اطلس + متا، مانیفست و پیش‌نمایش‌ها.
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

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

# قلم، دوربین و افکت‌های مشترک با مولد برج‌ها (یک منبع حقیقت برای سبک)
from generate_iso_tower_sprites import (  # noqa: E402
    CX, DEPTH, FRAME, GROUND, INK, Pen, alpha, checker, lerp, proj, ribbon, rot2,
    shade, smoke_puff, soft_glow,
)

OUT_DIR = os.path.join(os.path.dirname(HERE), "assets", "enemy_tank_iso")
PREVIEW_DIR = os.path.join(OUT_DIR, "previews")

DIRECTIONS = 8
ACTIONS = (("idle", 4), ("move", 6), ("attack", 6))
ACTION_FPS = {"idle": 8, "move": 12, "attack": 14}

# پالت دشمن: کرم/سفید + نارنجی + خاکستری تیره
CREAM_L = (242, 233, 210, 255)
CREAM = (214, 202, 174, 255)
CREAM_D = (166, 152, 123, 255)
DARK = (66, 71, 78, 255)
DARK_L = (96, 102, 110, 255)
DARK_D = (38, 41, 46, 255)
ORANGE = (255, 138, 43, 255)
AMBER = (255, 196, 84, 255)
RED = (226, 58, 46, 255)
RED_HOT = (255, 120, 96, 255)
TREAD = (92, 97, 105, 255)
TREAD_L = (124, 130, 138, 255)
BELT = (200, 193, 172, 255)
BELT_TEETH = (122, 118, 104, 255)

SCALE = 1.25         # بزرگ‌نمایی بدنه نسبت به واحد جهان تا کادر ۱۲۸ پر شود
ANCHOR = (CX, 99.0)  # مقیاس حول خط زمین
EMITTER = {}
TANK_ID = "tank"
TANK_NAME_FA = "تانک سنگین"

# ابعاد بدنه در واحد جهان (x پهلو، y ارتفاع، z جلو)
HULL_HW = 21.0
HULL_HL = 19.0
TREAD_OUT = 31.5
TREAD_IN = 16.5
TREAD_TOP = 14.0
HULL_Y0 = 10.0
HULL_Y1 = 31.0


# ------------------------------------------------------------------ کمکی‌ها


def vz(a, d):
    """جابه‌جایی صفحه‌ای برای یک واحد محلی در راستای جلو."""
    return (math.cos(a) * d, math.sin(a) * d * DEPTH)


def facing_screen(a):
    """بردار یک‌واحدی هدف روی صفحه (برای مردمک چشم و لیزر)."""
    sx, sy = math.cos(a), math.sin(a) * DEPTH
    ln = math.hypot(sx, sy) or 1.0
    return (sx / ln, sy / ln)


def foreshorten(a):
    """کوتاه‌شدگی نمای جلو؛ برای لیزر و دهانه در جهت‌هایی که رو به دوربین‌اند."""
    return max(0.55, min(1.0, math.hypot(math.cos(a), math.sin(a) * DEPTH)))


def rounded_plan(hw, hl, r, n_corner=3):
    """چندضلعی محلیِ مستطیل گوشه‌گرد (برای بدنه و زنجیرها)."""
    pts = []
    corners = ((hw - r, hl - r, 0), (-(hw - r), hl - r, 90),
               (-(hw - r), -(hl - r), 180), (hw - r, -(hl - r), 270))
    for cxx, czz, a0 in corners:
        for i in range(n_corner + 1):
            ang = math.radians(a0 + 90.0 * i / n_corner)
            pts.append((cxx + r * math.cos(ang), czz + r * math.sin(ang)))
    return pts


def world_pts(a, plan, y):
    out = []
    for lx, lz in plan:
        wx, wz = rot2(lx, lz, a)
        out.append(proj(wx, y, wz))
    return out


def face_normal_z(a, p0, p1, center=(0.0, 0.0)):
    """نرمال بیرونیِ وجه پلان (به سمت دوربین = z مثبت) و مؤلفهٔ x آن."""
    ex, ez = p1[0] - p0[0], p1[1] - p0[1]
    nx, nz = ez, -ex
    ln = math.hypot(nx, nz) or 1.0
    nx, nz = nx / ln, nz / ln
    # جهت بیرونی: دورشدن از مرکز پلان
    mx, mz = (p0[0] + p1[0]) / 2 - center[0], (p0[1] + p1[1]) / 2 - center[1]
    if nx * mx + nz * mz < 0:
        nx, nz = -nx, -nz
    rx, rz = rot2(nx, nz, a)
    return rz, rx


def prism(pen, a, plan, y0, y1, base, w=2.8, top_boost=0.22, bottom_boost=-0.34,
          top_color=None, side_boost=0.10):
    """چندضلعیِ اکسترودشده با نور جهت‌دار و خط دور؛ مرتب‌سازی از دور به نزدیک."""
    top = world_pts(a, plan, y1)
    bot = world_pts(a, plan, y0)
    faces = []
    for i in range(len(plan)):
        j = (i + 1) % len(plan)
        nz, nx = face_normal_z(a, plan[i], plan[j])
        if nz <= -0.2:
            continue
        lit = max(0.0, min(1.0, 0.5 + 0.5 * (nx * -0.62 + nz * 0.78)))
        col = lerp(shade(base, bottom_boost), shade(base, top_boost), lit)
        quad = [top[i], top[j], bot[j], bot[i]]
        faces.append((nz, quad, col))
    for _, quad, col in sorted(faces, key=lambda f: f[0]):
        pen.poly(quad, [(0.0, shade(col, side_boost)), (1.0, shade(col, -side_boost - 0.08))], INK, w)
    pen.poly(top, [(0.0, shade(top_color or base, top_boost + 0.10)),
                   (1.0, shade(top_color or base, top_boost - 0.22))], INK, w)
    return top


def face_poly(pen, a, pts_local, fills, outline=INK, w=2.4):
    """چندضلعی روی صفحهٔ عمودیِ جلو (نقاط محلی (x,y) در z ثابت)."""
    pts = []
    for lx, ly, lz in pts_local:
        wx, wz = rot2(lx, lz, a)
        pts.append(proj(wx, ly, wz))
    pen.poly(pts, fills, outline, w)
    return pts


def plane_circle(center, ax, bx, r, n=18, angle=0.0):
    """دایره‌ای که در صفحه‌ای با دو بردار پایهٔ صفحه رسم می‌شود (چرخ‌ها)."""
    pts = []
    for i in range(n):
        t = 2 * math.pi * i / n
        pts.append((center[0] + (ax[0] * math.cos(t) + bx[0] * math.sin(t)) * r,
                    center[1] + (ax[1] * math.cos(t) + bx[1] * math.sin(t)) * r))
    return pts


# ------------------------------------------------------------------ زنجیر و چرخ


def draw_tread(pen, a, side, phase, wheel_turn):
    """شنی‌زنجیر: بدنهٔ تیره، تسمهٔ کرم‌روشن با دندانه‌های غلتان و چرخ‌های درشت."""
    cx_local = side * (TREAD_IN + TREAD_OUT) / 2.0
    half_w = (TREAD_OUT - TREAD_IN) / 2.0
    hw, hl = half_w, 21.5
    plan = [(cx_local + px, pz) for px, pz in rounded_plan(hw, hl, 6.0, 3)]

    # بدنهٔ شنی
    prism(pen, a, plan, 0.5, TREAD_TOP, TREAD, w=2.6, top_boost=0.16, bottom_boost=-0.30,
          top_color=TREAD_L, side_boost=0.08)

    # تسمهٔ روشن روی سطح رویی؛ دندانه‌ها با فاز حرکت جابه‌جا می‌شوند
    belt = [(cx_local + px * 0.64, pz * 0.985) for px, pz in rounded_plan(hw, hl, 4.0, 2)]
    prism(pen, a, belt, TREAD_TOP, TREAD_TOP + 1.8, BELT, w=1.6,
          top_boost=0.14, bottom_boost=-0.10, side_boost=0.04)
    spacing = 6.4
    n_teeth = 7
    span = spacing * n_teeth
    for i in range(n_teeth):
        lz = -hl + 3.2 + ((i * spacing + phase * spacing * 2.0) % span)
        if lz > hl - 3.0:
            continue
        tooth = [(cx_local - hw * 0.52, lz), (cx_local + hw * 0.52, lz),
                 (cx_local + hw * 0.52, lz + 2.6), (cx_local - hw * 0.52, lz + 2.6)]
        prism(pen, a, tooth, TREAD_TOP + 1.6, TREAD_TOP + 3.0, BELT_TEETH, w=1.0,
              top_boost=0.10, bottom_boost=-0.20, side_boost=0.03)

    # فندر کرم روی پهلوی بیرونی: در نمای کناری خط روشن می‌سازد
    fx_side = cx_local + side * (hw - 0.5)
    fender = []
    for zz, yy in ((hl - 0.4, TREAD_TOP + 2.2), (-hl + 0.4, TREAD_TOP + 2.2),
                   (-hl + 0.4, TREAD_TOP - 3.4), (hl - 0.4, TREAD_TOP - 3.4)):
        wx, wz = rot2(fx_side, zz, a)
        fender.append(proj(wx, yy, wz))
    if side * math.cos(a) > -0.05:
        pen.poly(fender, [(0, CREAM_L), (1, CREAM_D)], INK, 1.6)
        # پیچ‌های فندر
        for k in (-1, 1):
            wx, wz = rot2(fx_side + side * 0.3, k * (hl * 0.45), a)
            c = proj(wx, TREAD_TOP - 0.4, wz)
            pen.dot(c[0], c[1], 1.0, shade(CREAM_D, -0.15), alpha(INK, 200), 0.4, ry=0.9)
    # نوار نارنجی کوچک روی فندر (جلوی شنی)
    mark = []
    for zz, yy in ((hl - 0.8, TREAD_TOP + 2.4), (hl - 6.0, TREAD_TOP + 2.4),
                   (hl - 6.0, TREAD_TOP - 2.6), (hl - 0.8, TREAD_TOP - 2.6)):
        wx, wz = rot2(fx_side + side * 0.25, zz, a)
        mark.append(proj(wx, yy, wz))
    if side * math.cos(a) > 0.05:
        pen.poly(mark, [(0, AMBER), (1, shade(ORANGE, -0.12))], alpha(INK, 210), 1.1)

    # چرخ‌ها فقط وقتی پهلوی شنی تقریباً رو به دوربین است (وگرنه لبه‌ای دیده می‌شوند)
    outer_nz = side * math.cos(a)
    if outer_nz > 0.45:
        base_x = side * (TREAD_OUT + 0.2)
        ax = vz(a, 1.0)
        bx = (0.0, -1.0)
        for k, lz in enumerate((-13.0, -4.4, 4.4, 13.0)):
            wx, wz = rot2(base_x, lz, a)
            center = proj(wx, 6.6, wz)
            r = 6.0 if k in (0, 3) else 5.1
            pen.poly(plane_circle(center, ax, bx, r + 1.4, 18), [(0, INK), (1, INK)], None, 0)
            pen.poly(plane_circle(center, ax, bx, r, 18),
                     [(0, shade(DARK_L, 0.18)), (1, DARK_D)], alpha(INK, 230), 1.2)
            pen.poly(plane_circle(center, ax, bx, r * 0.66, 18),
                     [(0, CREAM_L), (1, CREAM_D)], INK, 1.1)
            pen.poly(plane_circle(center, ax, bx, r * 0.30, 18),
                     [(0, AMBER), (1, shade(ORANGE, -0.18))], INK, 0.8)
            for s2 in range(3):
                t = wheel_turn + s2 * math.pi / 3
                p0 = (center[0] + (ax[0] * math.cos(t) + bx[0] * math.sin(t)) * r * 0.52,
                      center[1] + (ax[1] * math.cos(t) + bx[1] * math.sin(t)) * r * 0.52)
                pen.line([center, p0], alpha(shade(CREAM_D, -0.25), 190), 1.0)

    # چراغ‌های پهلو: نارنجی جلو، قرمز عقب
    for lz, col, power in ((hl - 5.0, AMBER, 0.85), (-hl + 5.0, RED, 1.0)):
        wx, wz = rot2(cx_local + side * hw * 0.55, lz, a)
        c = proj(wx, TREAD_TOP + 4.0, wz)
        soft_glow(pen, lambda lp, c=c, col=col, power=power:
                  lp.dot(c[0], c[1], 4.6 * power, alpha(col, 115)), blur=3.0)
        pen.dot(c[0], c[1], 1.7, alpha(col, 245), INK, 0.7, ry=1.5)
        pen.dot(c[0] - 0.4, c[1] - 0.5, 0.6, (255, 255, 255, 215), None, ry=0.5)


# ------------------------------------------------------------------ بدنه


def face_quad_pts(a, p0, p1, y0, y1, inset=0.0, lift=0.0):
    """چهار گوشهٔ یک وجه عمودی بین دو نقطهٔ پلان (با تورفتگی اختیاری)."""
    mx, mz = (p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2
    out = []
    for (px, pz) in (p0, p1):
        dx, dz = px - mx, pz - mz
        ln = math.hypot(dx, dz) or 1.0
        k = max(0.0, 1.0 - inset / ln) if ln > inset else 0.0
        out.append((mx + dx * k, mz + dz * k))
    pts = []
    for (px, pz) in (out[1], out[0]):
        wx, wz = rot2(px, pz, a)
        pts.append(proj(wx, y1 - lift, wz))
    for (px, pz) in (out[0], out[1]):
        wx, wz = rot2(px, pz, a)
        pts.append(proj(wx, y0 + lift, wz))
    return [pts[0], pts[1], pts[2], pts[3]]


def hull_panels(pen, a, plan, y0, y1):
    """درزهای زره، پیچ‌ها و نوار نارنجی روی وجه‌های رو به دوربین."""
    for i in range(len(plan)):
        j = (i + 1) % len(plan)
        nz, nx = face_normal_z(a, plan[i], plan[j])
        if nz <= 0.28:
            continue
        inset = face_quad_pts(a, plan[i], plan[j], y0, y1, inset=3.4, lift=3.0)
        pen.ink(inset, 1.2, alpha(INK, 130))
        # پیچ‌های گوشهٔ وجه
        for lx in (0.22, 0.78):
            px = plan[i][0] + (plan[j][0] - plan[i][0]) * lx
            pz = plan[i][1] + (plan[j][1] - plan[i][1]) * lx
            wx, wz = rot2(px, pz, a)
            for yy in (y1 - 4.0, y0 + 4.0):
                c = proj(wx, yy, wz)
                pen.dot(c[0], c[1], 1.15, shade(CREAM_L, 0.10), alpha(INK, 220), 0.5, ry=1.0)


def draw_hull(pen, a):
    """بدنهٔ چاق و سنگین: شاسی تیره با اتصالات مکانیکی، زره کرم و عرشهٔ روشن."""
    # شاسی تیرهٔ پایین (اتصالات مکانیکی دیده می‌شوند)
    chassis = rounded_plan(HULL_HW + 2.0, HULL_HL + 2.0, 7.0, 3)
    prism(pen, a, chassis, HULL_Y0, 18.5, DARK, w=2.4,
          top_boost=0.30, bottom_boost=-0.40, top_color=DARK_L, side_boost=0.10)

    # اتصالات مکانیکی به زنجیرها (بازوهای تیره با پیچ نارنجی)
    for side in (-1, 1):
        for lz in (9.0, -9.0):
            wx, wz = rot2(side * (TREAD_OUT - 3.0), lz, a)
            b = proj(wx, 20.0, wz)
            t = proj(wx, 12.5, wz)
            pen.line([b, t], INK, 4.4)
            pen.line([b, t], shade(TREAD_L, -0.12), 2.2)
            pen.dot(b[0], b[1], 1.6, alpha(ORANGE, 235), INK, 0.7, ry=1.4)

    # زره کرم: بدنهٔ اصلی
    body = rounded_plan(HULL_HW, HULL_HL, 9.0, 3)
    prism(pen, a, body, 17.5, HULL_Y1 - 1.2, CREAM_D, w=2.6,
          top_boost=0.32, bottom_boost=-0.40, top_color=CREAM, side_boost=0.14)
    hull_panels(pen, a, body, 20.0, HULL_Y1 - 2.0)

    # عرشهٔ رویی روشن با نوار نارنجی و درزهای پنل
    deck = rounded_plan(HULL_HW - 2.4, HULL_HL - 2.4, 8.0, 3)
    prism(pen, a, deck, HULL_Y1 - 1.4, HULL_Y1 + 1.0, CREAM_L, w=1.9,
          top_boost=0.10, bottom_boost=-0.12, side_boost=0.06)
    stripe = [(-7.0, -HULL_HL + 6.5), (7.0, -HULL_HL + 6.5),
              (7.0, -HULL_HL + 9.5), (-7.0, -HULL_HL + 9.5)]
    p = world_pts(a, stripe, HULL_Y1 + 1.4)
    pen.poly(p, [(0, AMBER), (1, shade(ORANGE, -0.10))], alpha(INK, 230), 1.4)
    for lx in (-12.5, 12.5):
        q = world_pts(a, [(lx, -HULL_HL + 4.5), (lx, HULL_HL - 4.5)], HULL_Y1 + 1.5)
        pen.line(q, alpha(INK, 110), 1.0)
    # دستگیرهٔ عقب
    for lx in (-9.0, 9.0):
        q = world_pts(a, [(lx, -HULL_HL + 13.5), (lx, -HULL_HL + 16.0)], HULL_Y1 + 2.2)
        pen.line(q, shade(DARK_L, 0.10), 2.2)
    # گنبد پرتابگر لیزر روی لبهٔ جلویی عرشه (از همهٔ جهت‌ها دیده می‌شود)
    wx, wz = rot2(0.0, HULL_HL - 5.0, a)
    c = proj(wx, HULL_Y1 + 1.4, wz)
    pen.ellipse([c[0] - 6.0, c[1] - 2.8, c[0] + 6.0, c[1] + 3.4],
                [(0, shade(CREAM_L, 0.10)), (1, CREAM_D)], INK, 1.8)
    pen.ellipse([c[0] - 3.6, c[1] - 1.9, c[0] + 3.6, c[1] + 2.1],
                [(0, shade(DARK_L, 0.14)), (1, DARK)], INK, 1.2)
    pen.ellipse([c[0] - 2.0, c[1] - 1.2, c[0] + 2.0, c[1] + 1.4],
                [(0, (92, 30, 26, 255)), (1, (40, 14, 14, 255))], alpha(INK, 220), 0.8)
    EMITTER["p"] = (c[0], c[1] - 0.2)

    # نازل تهویهٔ تیره روی عرشه
    vent = [(x, z - 4.5) for x, z in rounded_plan(5.2, 3.0, 2.0, 2)]
    prism(pen, a, vent, HULL_Y1 + 0.8, HULL_Y1 + 3.2, DARK, w=1.6,
          top_boost=0.30, bottom_boost=-0.20, top_color=DARK_L, side_boost=0.04)


def draw_eyes(pen, a, blink=0.0, charge=0.0):
    """دو چشم کارتونی درشت روی صفحهٔ صورت؛ مردمک همیشه رو به هدف است."""
    front_nz = math.sin(a)
    if front_nz <= -0.12:
        return
    fx, fy = facing_screen(a)
    squash = 0.34 + 0.66 * min(1.0, front_nz)
    fz = HULL_HL + 1.0
    # در نمای کناری هر دو چشم روی هم می‌افتند؛ فقط چشمِ رو به دوربین رسم می‌شود
    sides = (-1, 1) if front_nz >= 0.30 else ((1,) if math.cos(a) >= 0 else (-1,))
    for side in sides:
        wx, wz = rot2(side * 7.4, fz, a)
        c = proj(wx, 25.2, wz)
        rx, ry = 5.9 * squash, 5.9
        # کاسهٔ چشم: حلقهٔ تیره، سفیدی، مردمک، برق
        pen.ellipse([c[0] - rx - 1.3, c[1] - ry - 1.3, c[0] + rx + 1.3, c[1] + ry + 1.3],
                    INK, None)
        pen.ellipse([c[0] - rx, c[1] - ry, c[0] + rx, c[1] + ry],
                    [(0, (255, 253, 244, 255)), (1, shade(CREAM_L, -0.08))], INK, 1.8)
        lid = max(0.12, 1.0 - blink)
        prx, pry = 3.2 * squash, 3.2 * lid
        ex, ey = c[0] + fx * 1.7 * squash, c[1] + fy * 1.7
        pen.ellipse([ex - prx, ey - pry, ex + prx, ey + pry],
                    [(0, (52, 60, 72, 255)), (1, (14, 16, 20, 255))], None)
        if charge > 0:
            pen.ellipse([ex - prx - 0.8, ey - pry - 0.8, ex + prx + 0.8, ey + pry + 0.8],
                        None, alpha(RED_HOT, int(70 + 80 * charge)), 1.1)
        pen.dot(ex - 0.9 * squash, ey - 1.0 * lid, 1.0 * max(0.35, lid),
                (255, 255, 255, 245), None, ry=0.95)
        if blink > 0.5:
            pen.ellipse([c[0] - rx, c[1] - ry, c[0] + rx, c[1] + ry * 0.15],
                        [(0, shade(CREAM_L, 0.05)), (1, CREAM_D)], INK, 1.8)
        # ابروی فلزی باریک و کج
        by = c[1] - ry - 1.4
        inner = c[0] - side * rx * 0.95
        outer = c[0] + side * rx * 1.10
        brow = [(outer, by - 0.5), (inner, by + 2.4),
                (inner, by + 3.6), (outer, by + 0.9)]
        pen.poly(brow, [(0, shade(DARK_L, 0.24)), (1, shade(DARK, -0.22))], INK, 1.6)



# ------------------------------------------------------------------ جلو و عقب


def draw_front(pen, a, charge=0.0, recoil=0.0):
    """صورت جلویی: صفحهٔ کرم، دهان دندانه‌دار، چراغ‌های نارنجی و لنز لیزر وسط."""
    front_z = HULL_HL + 0.8
    front_nz = math.sin(a)
    squash = 0.44 + 0.56 * min(1.0, max(0.0, front_nz))
    fz = front_z

    if front_nz > -0.12:
        # صفحهٔ صورت روشن: امضای بصری دشمن
        plate = [(-15.0, 13.4, fz - 0.2), (15.0, 13.4, fz - 0.2),
                 (15.0, 31.2, fz - 0.2), (-15.0, 31.2, fz - 0.2)]
        face_poly(pen, a, plate, [(0, shade(CREAM, 0.02)), (1, shade(CREAM_L, 0.14))], INK, 2.6)
        # لبهٔ روشن بالای صفحه
        face_poly(pen, a, [(-14.2, 30.1, fz), (14.2, 30.1, fz),
                           (14.2, 30.9, fz), (-14.2, 30.9, fz)],
                  [(0, alpha(shade(CREAM_L, 0.40), 190)), (1, alpha(shade(CREAM_L, 0.2), 190))],
                  None, 0)
        # پوزخند کارتونی زیر لنز
        grin = [(-7.2, 15.0, fz), (7.2, 15.0, fz), (6.0, 17.2, fz), (-6.0, 17.2, fz)]
        face_poly(pen, a, grin, [(0, (44, 46, 52, 255)), (1, (20, 22, 26, 255))], INK, 1.8)
        for k in range(3):
            x0 = -5.2 + k * 3.9
            tooth = [(x0, 15.4, fz + 0.3), (x0 + 2.4, 15.4, fz + 0.3),
                     (x0 + 1.2, 16.9, fz + 0.3)]
            face_poly(pen, a, tooth, [(0, CREAM_L), (1, CREAM_D)], alpha(INK, 200), 0.8)
        # چراغ‌های نارنجی در دو گوشهٔ صورت
        for side in ((-1, 1) if front_nz >= 0.30 else ((1,) if math.cos(a) >= 0 else (-1,))):
            wx, wz = rot2(side * 11.2, fz + 0.4, a)
            c = proj(wx, 21.0, wz)
            r = 2.2
            soft_glow(pen, lambda lp, c=c: lp.dot(c[0], c[1], 6.0, alpha(AMBER, 120)), blur=3.4)
            pen.ellipse([c[0] - r * 1.5 * squash, c[1] - r * 1.5,
                         c[0] + r * 1.5 * squash, c[1] + r * 1.5],
                        [(0, shade(DARK_L, 0.22)), (1, DARK)], alpha(INK, 235), 1.3)
            pen.ellipse([c[0] - r * squash, c[1] - r, c[0] + r * squash, c[1] + r],
                        [(0, AMBER), (1, ORANGE)], alpha(INK, 215), 0.9)
            pen.dot(c[0] - 0.4, c[1] - 0.8, 0.8, (255, 253, 235, 235), None, ry=0.7)

    # سپر جلو: نوار تیره با دو نوک نارنجی
    bumper = [(-14.0, HULL_HL + 2.6), (14.0, HULL_HL + 2.6),
              (14.0, HULL_HL + 0.6), (-14.0, HULL_HL + 0.6)]
    prism(pen, a, bumper, 12.4, 16.2, DARK, w=2.0, top_boost=0.34,
          bottom_boost=-0.30, top_color=DARK_L, side_boost=0.05)
    for side in (-1, 1):
        wx, wz = rot2(side * 13.0, HULL_HL + 2.8, a)
        c = proj(wx, 14.2, wz)
        pen.dot(c[0], c[1], 1.6, alpha(ORANGE, 245), INK, 0.7, ry=1.4)

    # لنز لیزر قرمز، وسط صورت بین چشم‌ها و دهان
    wx, wz = rot2(0.0, fz + 1.4, a)
    c = proj(wx, 21.0, wz)
    if front_nz > -0.12:
        for ring_r, col, wdt in ((4.6, INK, 0), (3.9, DARK_L, 0), (2.6, (72, 22, 20, 255), 0)):
            pen.ellipse([c[0] - ring_r * squash * 1.2, c[1] - ring_r,
                         c[0] + ring_r * squash * 1.2, c[1] + ring_r],
                        [(0, col), (1, shade(col, -0.24))], INK if wdt == 0 else None, 1.2)
    if charge > 0:
        soft_glow(pen, lambda lp: lp.dot(c[0], c[1], 5.0 + 2.5 * charge,
                                         alpha(RED, int(120 * charge))), blur=3.4)
        pen.ellipse([c[0] - (1.5 + 0.9 * charge) * squash, c[1] - (1.5 + 0.9 * charge),
                     c[0] + (1.5 + 0.9 * charge) * squash, c[1] + (1.5 + 0.9 * charge)],
                    [(0, (255, 236, 226, 255)), (1, RED)], None)
    axis = facing_screen(a)
    perp = (-axis[1], axis[0])
    if front_nz > 0.28:
        muzzle = (c[0] + axis[0] * 3.4, c[1] + axis[1] * 3.4)
    else:
        # صورت رو به دوربین نیست؛ پرتو از گنبد روی عرشه بیرون می‌آید
        em = EMITTER.get("p", (c[0], c[1]))
        muzzle = (em[0] + axis[0] * 4.5, em[1] + axis[1] * 4.5 - 1.0)
    return muzzle, axis, perp


def draw_rear(pen, a, wave=0.0):
    """عقب: دو اگزوز با نور نارنجی، آنتن با پرچم و نوار چراغ قرمز."""
    rear_z = -HULL_HL - 0.6
    for side in (-1, 1):
        wx, wz = rot2(side * 9.0, rear_z, a)
        b = proj(wx, HULL_Y1 - 2.0, wz)
        t = proj(wx, HULL_Y1 + 11.0, wz)
        pen.line([b, t], INK, 5.4)
        pen.line([b, t], shade(DARK_L, 0.02), 3.2)
        # رینگ‌های اگزوز
        for k in (0.45, 0.78):
            q0 = (b[0] + (t[0] - b[0]) * k, b[1] + (t[1] - b[1]) * k)
            pen.line([(q0[0] - 2.6, q0[1]), (q0[0] + 2.6, q0[1])], INK, 2.6)
            pen.line([(q0[0] - 2.6, q0[1]), (q0[0] + 2.6, q0[1])], shade(DARK, -0.1), 1.4)
        pen.dot(t[0], t[1], 2.5, (28, 26, 26, 255), INK, 1.0, ry=1.5)
        pen.dot(t[0], t[1] + 0.4, 1.4, alpha(ORANGE, 225), None, ry=0.9)

    # آنتن با پرچم نارنجی
    wx, wz = rot2(-15.5, rear_z + 1.5, a)
    b = proj(wx, HULL_Y1, wz)
    t = proj(wx, HULL_Y1 + 17.0, wz)
    pen.line([b, t], INK, 2.6)
    pen.line([b, t], shade(DARK_L, 0.12), 1.2)
    pen.dot(t[0], t[1], 1.6, alpha(RED, 240), None)
    sway = 1.6 * math.sin(wave)
    flag = [(t[0], t[1] + 1.0), (t[0] + 9.0, t[1] + 3.0 + sway), (t[0], t[1] + 6.0)]
    pen.poly(flag, [(0, AMBER), (1, shade(ORANGE, -0.18))], INK, 1.6)

    # نوار چراغ عقب وقتی پشت به دوربین است
    if math.sin(a) < -0.12:
        wx0, wz0 = rot2(-11.0, -HULL_HL - 1.0, a)
        wx1, wz1 = rot2(11.0, -HULL_HL - 1.0, a)
        p0 = proj(wx0, 22.0, wz0)
        p1 = proj(wx1, 22.0, wz1)
        soft_glow(pen, lambda lp: lp.line([p0, p1], alpha(RED, 130), 6.0), blur=3.2)
        pen.line([p0, p1], alpha(RED, 235), 3.0)
        pen.line([p0, p1], alpha(RED_HOT, 200), 1.2)


def draw_cupola(pen, a, charge=0.0):
    """گنبد فرمان کوچک با چراغ گردان نارنجی."""
    wx, wz = rot2(0.0, -8.0, a)
    c = proj(wx, HULL_Y1 + 1.0, wz)
    pen.ellipse([c[0] - 7.4, c[1] - 3.4, c[0] + 7.4, c[1] + 4.2],
                [(0, shade(CREAM_L, 0.08)), (1, CREAM_D)], INK, 2.0)
    pen.ellipse([c[0] - 4.6, c[1] - 2.4, c[0] + 4.6, c[1] + 2.4],
                [(0, shade(DARK_L, 0.12)), (1, DARK)], INK, 1.4)
    pen.dot(c[0], c[1] - 0.6, 2.2, alpha(ORANGE, 240), INK, 0.7, ry=1.8)
    soft_glow(pen, lambda lp: lp.dot(c[0], c[1] - 0.6, 6.0 + 2.0 * charge,
                                     alpha(ORANGE, 110)), blur=3.4)


# ------------------------------------------------------------------ افکت‌ها


def red_beam(pen, origin, axis, perp, length, width, charge=1.0):
    """پرتوی لیزر قرمز درخشان با هاله، مغز سفید-داغ و جرقه."""
    ox, oy = origin
    ux, uy = axis
    px, py = perp
    ex, ey = ox + ux * length, oy + uy * length
    soft_glow(pen, lambda lp: lp.line([(ox, oy), (ex, ey)], alpha(RED, 200), width * 3.0),
              blur=5.5, passes=2)
    pen.line([(ox, oy), (ex, ey)], alpha(RED, 215), width * 2.1)
    pen.line([(ox, oy), (ex, ey)], RED_HOT, width * 1.1)
    pen.line([(ox, oy), (ex, ey)], (255, 246, 238, 255), width * 0.42)
    ring = 3.0 + 3.2 * charge
    pen.ellipse([ox - ring, oy - ring * 0.9, ox + ring, oy + ring * 0.9], None,
                alpha(RED_HOT, 230), 1.8)
    pen.dot(ex, ey, width * 1.05, alpha(RED_HOT, 240), None, ry=width * 0.95)
    pen.dot(ex, ey, width * 0.5, (255, 250, 245, 255), None, ry=width * 0.45)
    for k, off in ((0.35, 7.0), (0.62, -6.0), (0.86, 4.5)):
        bx, by = ox + ux * length * k + px * off, oy + uy * length * k + py * off
        pen.line([(bx, by), (bx + ux * 4.2 + px * 1.4, by + uy * 4.2 + py * 1.4)],
                 alpha(RED_HOT, 210), 1.2)


def muzzle_flare(pen, origin, axis, perp, r, seed=0.0):
    """شکوفهٔ نور دهانه."""
    ox, oy = origin
    ux, uy = axis
    px, py = perp
    soft_glow(pen, lambda lp: lp.dot(ox + ux * 2, oy + uy * 2, r * 1.9, alpha(RED, 160)), blur=6.0)
    pts = []
    n = 8
    for i in range(n):
        ang = 2 * math.pi * i / n
        rr = r * (0.55 if i % 2 else 1.0) * (0.92 + 0.14 * math.sin(seed + i))
        pts.append((ox + ux * 2 + (math.cos(ang) * ux + math.sin(ang) * px) * rr,
                    oy + uy * 2 + (math.cos(ang) * uy + math.sin(ang) * py) * rr))
    pen.poly(pts, [(0, RED), (0.6, RED_HOT), (1, (255, 240, 225, 255))], (86, 22, 16, 255), 2.0)


def dust(pen, x, y, r, alpha_power=110):
    """گرد و خاک گرم‌رنگ پشت زنجیرها."""
    for i, (ox, oy, rr) in enumerate(((-r * 0.55, r * 0.10, r * 0.5),
                                      (0, -r * 0.30, r * 0.72),
                                      (r * 0.55, r * 0.12, r * 0.48))):
        pen.dot(x + ox, y + oy, rr, alpha((196, 182, 150, 255), alpha_power * (0.85 + 0.05 * i)),
                alpha((120, 108, 86, 255), alpha_power * 0.6), 0.9, ry=rr * 0.72)


# ------------------------------------------------------------------ چیدن فریم


def draw_body(pen, a, phase, wheel_turn, charge, blink, wave=0.0):
    """کل تانک را با ترتیب عمق روی قلم می‌کشد و دهانهٔ لیزر را برمی‌گرداند."""
    pieces = []
    for side in (-1, 1):
        zc = side * 24.0 * math.cos(a)
        pieces.append((zc, lambda side=side: draw_tread(pen, a, side, phase, wheel_turn)))
    pieces.append((0.0, lambda: draw_hull(pen, a)))
    pieces.append((-8.0 * math.sin(a) + 0.4, lambda: draw_cupola(pen, a, charge)))
    muzzle_box = {}

    def front():
        muzzle_box["m"] = draw_front(pen, a, charge)

    pieces.append((HULL_HL * math.sin(a) + 1.0, front))
    pieces.append((-HULL_HL * math.sin(a) - 1.0, lambda: draw_rear(pen, a, wave)))
    # چشم‌ها همیشه آخر: روی عرشه‌اند و از بالا دیده می‌شوند
    pieces.append((70.0, lambda: draw_eyes(pen, a, blink, charge)))
    for _, fn in sorted(pieces, key=lambda p: p[0]):
        fn()
    return muzzle_box["m"]


def edge_distance(origin, axis, margin=12.0, scale=None):
    """فاصلهٔ آزاد (در واحد جهان) تا لبهٔ کادر در راستای پرتو.

    مختصات جهان با ضریب `scale` حول لنگر بزرگ می‌شوند، پس فاصلهٔ نهایی
    با تقسیم بر همان ضریب به واحد جهان برمی‌گردد.
    """
    sc = SCALE if scale is None else scale
    ox = origin[0] * sc - CX * (sc - 1.0)
    oy = origin[1] * sc - ANCHOR[1] * (sc - 1.0)
    ux, uy = axis
    best = 1e9
    for u, o in ((ux, ox), (uy, oy)):
        if abs(u) < 1e-6:
            continue
        limit = ((FRAME - margin) - o) / u if u > 0 else (margin - o) / u
        best = min(best, max(0.0, limit))
    return max(0.0, best) / sc


def compose(d: int, action: str, f: int) -> Image.Image:
    """یک فریم ۱۲۸×۱۲۸ شفاف از دشمن، جهت و حالت خواسته‌شده."""
    a = math.radians(d * 45)
    total = dict(ACTIONS)[action]
    fx, fy = facing_screen(a)
    fs = foreshorten(a)

    bob = recoil = blink = charge = 0.0
    beam_len = 0.0
    phase = wheel_turn = 0.0
    wave = 2 * math.pi * f / total
    if action == "idle":
        bob = 0.95 * math.sin(wave)
        blink = (0.0, 0.0, 0.85, 0.0)[f % 4]
    elif action == "move":
        bob = 1.15 * abs(math.sin(math.pi * f / (total - 1)))
        phase = f * 1.0
        wheel_turn = f * (2 * math.pi / total) * 1.6
    else:  # attack
        recoil = (0.0, 0.0, 2.9, 2.0, 0.8, 0.0)[f % 6]
        charge = (0.45, 1.0, 0.95, 0.5, 0.15, 0.0)[f % 6]
        beam_len = (0.0, 0.0, 31.0, 23.0, 0.0, 0.0)[f % 6]
        blink = 0.0

    EMITTER.clear()
    body = Pen(scale=SCALE, anchor=ANCHOR)
    muzzle, axis, _perp = draw_body(body, a, phase, wheel_turn, charge, blink, wave)

    # لرزش/پس‌نشینی کل وسیله
    off = (-fx * recoil, -fy * recoil + bob)
    main = Pen(scale=SCALE, anchor=ANCHOR)
    # سایهٔ تماس با زمین، کمی جابه‌جا در جهت نور
    sh = Image.new("RGBA", main.img.size, (0, 0, 0, 0))
    scx, scy = proj(0.0, 0.0, 0.0)
    ImageDraw.Draw(sh).ellipse(main._box([scx - 33, scy - 12, scx + 33, scy + 13]),
                               fill=(12, 13, 12, 125))
    main.img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(4.0 * main.s / 2)))
    main.img.alpha_composite(body.img, (int(round(off[0] * main.k)), int(round(off[1] * main.k))))

    mx, my = muzzle[0] + off[0], muzzle[1] + off[1]
    origin = (mx + axis[0] * 3.0, my + axis[1] * 3.0)
    perp = (-axis[1], axis[0])

    if action == "idle":
        # دود سبک اگزوز + پالس چراغ‌ها
        for side in (-1, 1):
            wx, wz = rot2(side * 9.0, -HULL_HL - 0.6, a)
            tips = proj(wx, HULL_Y1 + 12.0, wz)
            k = (f / total)
            dust(main, tips[0] + off[0] + k * 2.0, tips[1] + off[1] - 2.0 - 4.0 * k,
                 2.8 + 1.8 * k, 78)
    elif action == "move":
        # گرد و خاک پشت هر زنجیر + تکان بدنه
        for side in (-1, 1):
            wx, wz = rot2(side * 22.0, -20.0, a)
            p = proj(wx, 2.0, wz)
            for i in range(2):
                k = (f / (total - 1)) * 0.7 + i * 0.30
                dx = p[0] + off[0] - fx * (2 + 5 * k)
                dy = p[1] - 2 - 3.5 * k
                dx = min(max(dx, 34.0), FRAME - 34.0)
                dust(main, dx, dy, 3.4 + 1.5 * k + i * 1.0, 92 - 20 * i)
    else:
        if f in (0, 1):
            # شارژ: حلقهٔ انرژی روی دهانه
            r = 4.0 + 2.2 * charge
            main.ellipse([origin[0] - r, origin[1] - r * 0.9, origin[0] + r, origin[1] + r * 0.9],
                         None, alpha(RED_HOT, int(120 + 110 * charge)), 1.6)
            soft_glow(main, lambda lp: lp.dot(origin[0], origin[1], 7.0 + 4.0 * charge,
                                              alpha(RED, int(90 * charge))), blur=4.0)
        if f in (2, 3):
            # پرتو داخل کادر می‌ماند: طول تا ۲ پیکسل مانده به لبه محدود می‌شود
            avail = edge_distance(origin, axis, margin=12.0)
            red_beam(main, origin, axis, perp, min(beam_len * fs, avail), (4.6, 3.4)[f - 2], charge)
            flare = min((9.0, 6.5)[f - 2] * fs, max(3.4, avail * 0.34))
            muzzle_flare(main, (origin[0] + axis[0] * 1.5, origin[1] + axis[1] * 1.5 + 2.0),
                         axis, perp, flare, seed=f)
            if f == 3:
                smoke_puff(main, origin[0] + axis[0] * 8, origin[1] + axis[1] * 8 - 2, 4.2, 85)
        if f == 4:
            smoke_puff(main, origin[0] + axis[0] * 5, origin[1] + axis[1] * 5, 5.0, 95)
            main.ellipse([origin[0] - 5.5, origin[1] - 4.6, origin[0] + 5.5, origin[1] + 4.6],
                         None, alpha(RED, 110), 1.4)

    return main.out()


# ------------------------------------------------------------------ خروجی


def sheet_for(action: str) -> Image.Image:
    """شیت شبکه‌ای: ردیف = جهت (۰..۷)، ستون = فریم."""
    n = dict(ACTIONS)[action]
    sheet = Image.new("RGBA", (FRAME * n, FRAME * DIRECTIONS), (0, 0, 0, 0))
    for d in range(DIRECTIONS):
        for f in range(n):
            sheet.alpha_composite(compose(d, action, f), (f * FRAME, d * FRAME))
    return sheet


def build_atlas(sheets):
    cols = len(ACTIONS)
    atlas = Image.new("RGBA", (FRAME * cols, FRAME * DIRECTIONS), (0, 0, 0, 0))
    names = {}
    for ci, (action, n) in enumerate(ACTIONS):
        sheet = sheets[action]
        for d in range(DIRECTIONS):
            frame = sheet.crop((0, d * FRAME, FRAME, (d + 1) * FRAME))
            atlas.alpha_composite(frame, (ci * FRAME, d * FRAME))
            names[f"{TANK_ID}_{action}_{d}"] = {"column": ci, "row": d,
                                                "x": ci * FRAME, "y": d * FRAME}
    atlas.save(os.path.join(OUT_DIR, "atlas_tank_iso.png"), optimize=True)
    meta = {
        "image": "atlas_tank_iso.png",
        "frameWidth": FRAME, "frameHeight": FRAME,
        "grid": {"columns": cols, "rows": DIRECTIONS, "directionAxis": "y", "buildAxis": "x",
                 "note": "اطلس فقط فریم ۰ هر جهت را دارد؛ انیمیشن کامل در شیت‌های اصلی است "
                         "(ردیف = جهت، ستون = فریم)."},
        "columns": [a for a, _ in ACTIONS],
        "directions": DIRECTIONS,
        "names": names,
    }
    with open(os.path.join(OUT_DIR, "atlas_tank_iso.json"), "w", encoding="utf8") as fh:
        json.dump(meta, fh, indent=1)


def write_manifest():
    manifest = {
        "pack": "iso-tower-defense-enemy",
        "enemy": TANK_ID,
        "nameFa": TANK_NAME_FA,
        "frameWidth": FRAME, "frameHeight": FRAME,
        "layout": "grid", "alpha": True,
        "layoutNote": "هر شیت ۸ ردیف (جهت) × N ستون (فریم) از فریم‌های ۱۲۸×۱۲۸ است.",
        "directions": DIRECTIONS,
        "directionOrder": ["east", "south-east", "south", "south-west",
                           "west", "north-west", "north", "north-east"],
        "directionNote": "ردیف n = جهت n (ساعت‌گرد روی صفحه، گام ۴۵ درجه از شرق)؛ ستون = فریم.",
        "camera": {
            "projection": "isometric-2:1",
            "note": "نمای از بالا و کمی مایل؛ سطح رویی، جلو و کنارهٔ بدنه هم‌زمان دیده می‌شوند.",
            "depthSquash": DEPTH, "groundY": GROUND, "scale": SCALE,
            "shadowY": 99.0,
            "pivotNote": "مرکز چرخش بدنه در پیکسل (64, 99) روی زمین است؛ چشم‌ها و لیزر با "
                         "همین محور می‌چرخند.",
        },
        "palette": {
            "ink": "#1b191f", "cream": "#e6dcbc", "creamLight": "#f4edd6",
            "creamDark": "#a6987b", "dark": "#42474e", "darkLight": "#60666e",
            "orange": "#ff8a2b", "amber": "#ffc454", "red": "#e23a2e",
            "laserRed": "#ff7860",
        },
        "actions": {},
    }
    for action, n in ACTIONS:
        manifest["actions"][action] = {
            "file": f"{TANK_ID}_{action}.png", "frames": n, "fps": ACTION_FPS[action],
            "loop": action != "attack",
            "grid": {"columns": n, "rows": DIRECTIONS},
            "layout": "grid-dir-major: row = direction (0..7), column = frame",
        }
    manifest["atlas"] = {"image": "atlas_tank_iso.png", "meta": "atlas_tank_iso.json"}
    with open(os.path.join(OUT_DIR, "manifest.json"), "w", encoding="utf8") as fh:
        json.dump(manifest, fh, indent=2, ensure_ascii=False)
    return manifest


def build_previews(sheets, make_gif=True):
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    pad, gap, lab = 10, 8, 18

    # ۱) نمای کلی: سه حالت رو به دوربین (جهت ۲)، بزرگ‌نمایی ۲×
    cw = FRAME * 2
    out = checker((pad * 2 + cw * 3 + gap * 2, pad * 2 + 26 + cw + lab), cell=16)
    for j, (action, n) in enumerate(ACTIONS):
        im = compose(2, action, min(1, n - 1) if action == "attack" else 0)
        cell = checker((cw, cw), cell=16)
        cell.alpha_composite(im.resize((cw, cw), Image.LANCZOS))
        ribbon(cell, f"{TANK_ID} - {action} ({n} frames)", 16)
        out.alpha_composite(cell, (pad + j * (cw + gap), pad + 26))
    out = ribbon(out, "ENEMY TANK PACK - 128x128 - idle | move | attack (facing camera)", 26)
    out.save(os.path.join(PREVIEW_DIR, "overview.png"), optimize=True)

    # ۲) هشت جهت، هر کدام یک فریم
    dirs = checker((FRAME * 4 + gap * 3 + pad * 2, FRAME * 2 + gap + lab + pad * 2), cell=16)
    d0 = ImageDraw.Draw(dirs)
    for d in range(DIRECTIONS):
        im = compose(d, "move", 2)
        x = pad + (d % 4) * (FRAME + gap)
        y = pad + (d // 4) * (FRAME + gap)
        dirs.alpha_composite(im, (x, y))
        tag = Image.new("RGBA", (FRAME, 14), (18, 20, 24, 210))
        dirs.alpha_composite(tag, (x, y + FRAME - 14))
        d0.text((x + 4, y + FRAME - 13), f"dir {d}", fill=(240, 226, 170, 255))
    dirs = ribbon(dirs, "8 directions, clockwise from east - whole vehicle turns", lab)
    dirs.save(os.path.join(PREVIEW_DIR, "directions.png"), optimize=True)

    # ۳) شیت کامل هر حالت کنار هم
    patches = []
    for action, n in ACTIONS:
        sheet = sheets[action]
        bg = checker(sheet.size, cell=8)
        bg.alpha_composite(sheet)
        patches.append(ribbon(bg, f"{TANK_ID} / {action} / {n} frames x 8 dirs", 16))
    width = sum(p.width for p in patches) + gap * (len(patches) - 1) + pad * 2
    height = max(p.height for p in patches) + pad * 2
    out = Image.new("RGBA", (width, height), (24, 26, 30, 255))
    x = pad
    for p in patches:
        out.alpha_composite(p, (x, pad))
        x += p.width + gap
    out.save(os.path.join(PREVIEW_DIR, "sheet_tank.png"), optimize=True)

    # ۴) نمای داخل بازی: زمین ایزومتریک + برج‌ها + دشمن‌ها
    tile = 88
    W, H = 660, 470
    scene = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sd = ImageDraw.Draw(scene)
    ox, oy = W / 2, 50

    def tc(gx, gy):
        return (ox + (gx - gy) * tile / 2, oy + (gx + gy) * tile / 4)

    for gy in range(-1, 9):
        for gx in range(-1, 9):
            cx, cy = tc(gx, gy)
            diamond = [(cx, cy - tile / 4), (cx + tile / 2, cy),
                       (cx, cy + tile / 4), (cx - tile / 2, cy)]
            col = (92, 110, 74, 255) if (gx + gy) % 2 else (84, 102, 67, 255)
            sd.polygon(diamond, fill=col)
            sd.line(diamond + [diamond[0]], fill=(60, 74, 50, 130), width=1)
    # برج‌ها (اختیاری؛ اگر پک برج ساخته شده باشد)
    try:
        import generate_iso_tower_sprites as T
        for t, gx, gy, d in (("machinegun", 2, 2, 2), ("laser", 6, 2, 3)):
            cx, cy = tc(gx, gy)
            scene.alpha_composite(T.compose(t, d, "idle", 0),
                                  (int(cx - T.FRAME / 2), int(cy - T.GROUND - 15)))
    except Exception:
        pass
    for gx, gy, d, action, f in ((3, 6, 1, "move", 2), (5, 5, 0, "attack", 2)):
        cx, cy = tc(gx, gy)
        scene.alpha_composite(compose(d, action, f),
                              (int(cx - FRAME / 2), int(cy - 99 - 18)))
    scene = ribbon(scene, "in-game mock - chunky cream enemy tank vs. towers", 18)
    scene.save(os.path.join(PREVIEW_DIR, "scene.png"), optimize=True)

    # ۵) GIF: idle → move → attack
    if make_gif:
        frames = []
        for action, n in ACTIONS:
            dir_seq = {"idle": (2, 1, 2, 3), "move": (2, 1, 0, 0), "attack": (2, 2)}[action]
            for i, d in enumerate(dir_seq):
                for f in range(n):
                    bg = checker((FRAME, FRAME), cell=8)
                    bg.alpha_composite(compose(d, action, f))
                    frames.append(bg)
        rgb = [f.convert("RGB") for f in frames]
        palette = rgb[0].quantize(colors=200, method=Image.MEDIANCUT)
        frames = [f.quantize(palette=palette, dither=Image.NONE) for f in rgb]
        frames[0].save(os.path.join(PREVIEW_DIR, "anim_tank.gif"), save_all=True,
                       append_images=frames[1:], duration=100, loop=0, disposal=1)


def verify(sheets) -> list[str]:
    """بازرسی خروجی: اندازه، شفافیت، نبود کلیپ در لبه‌ها و تفاوت واقعی فریم‌ها."""
    problems = []
    for action, n in ACTIONS:
        sheet = sheets[action]
        if sheet.size != (FRAME * n, FRAME * DIRECTIONS):
            problems.append(f"{action}: اندازه {sheet.size} با قرارداد شبکه نمی‌خواند")
            continue
        for d in range(DIRECTIONS):
            frames = []
            for f in range(n):
                frame = sheet.crop((f * FRAME, d * FRAME, (f + 1) * FRAME, (d + 1) * FRAME))
                if frame.mode != "RGBA":
                    problems.append(f"{action} d{d} f{f}: حالت {frame.mode} نیست RGBA")
                if frame.getchannel("A").getextrema()[1] < 200:
                    problems.append(f"{action} d{d} f{f}: کاملاً شفاف است")
                a = frame.getchannel("A")
                # آستانهٔ ۶۰: هالهٔ نرم مجاز است، اما پیکسل جامد نباید بریده شود
                edges = (a.crop((0, 0, FRAME, 1)), a.crop((0, FRAME - 1, FRAME, FRAME)),
                         a.crop((0, 0, 1, FRAME)), a.crop((FRAME - 1, 0, FRAME, FRAME)))
                if any(e.getextrema()[1] > 60 for e in edges):
                    problems.append(f"{action} d{d} f{f}: تصویر به لبهٔ کادر چسبیده")
                frames.append(frame.tobytes())
            if len(set(frames)) == 1 and action != "aim":
                problems.append(f"{action} d{d}: همهٔ فریم‌ها یکسان‌اند (انیمیشن ندارد)")
    return problems


def main(argv=None):
    ap = argparse.ArgumentParser(description="ساخت اسپرایت‌شیت دشمن تانک ایزومتریک")
    ap.add_argument("--no-gif", action="store_true", help="بدون ساخت GIF")
    args = ap.parse_args(argv)

    os.makedirs(OUT_DIR, exist_ok=True)
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    sheets = {}
    for action, n in ACTIONS:
        sheet = sheet_for(action)
        sheet.save(os.path.join(OUT_DIR, f"{TANK_ID}_{action}.png"), optimize=True)
        sheets[action] = sheet
        print(f"  {TANK_ID}_{action}.png  {sheet.width}x{sheet.height}")
    write_manifest()
    build_atlas(sheets)
    build_previews(sheets, make_gif=not args.no_gif)
    problems = verify(sheets)
    if problems:
        print("بررسی خروجی شکست خورد:", file=sys.stderr)
        for msg in problems:
            print("  -", msg, file=sys.stderr)
        raise SystemExit(1)
    print(f"۳ شیت + اطلس + مانیفست در {OUT_DIR} (بررسی خروجی سبز)")


if __name__ == "__main__":
    main()
