#!/usr/bin/env python3
"""استخراج مسیر دشمن و جایگاه‌های ساخت از جادهٔ آسفالت تصاویر نقشه.

هر تصویر نقشه دقیقاً ۱۲۸۰×۷۲۰ پیکسل است و همان اندازهٔ میدان بازی
(Balance.WORLD_SIZE)، پس هر پیکسل تصویر یک پیکسل دنیاست و تبدیل به واحد
کاشی ساده است: x/64 و y/72.

خروجی: tools/art_source/maps_v2/routes_extracted.json

    python3 tools/extract_map_routes.py [--write-maps-gd]
"""
from __future__ import annotations

import json
import os
import sys
from collections import deque

import numpy as np
from PIL import Image
from scipy import ndimage
from skimage.morphology import skeletonize

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "assets", "sprites", "maps")
OUT_JSON = os.path.join(ROOT, "tools", "art_source", "maps_v2", "routes_extracted.json")

TILE_X, TILE_Y = 64.0, 72.0
W, H = 1280, 720
N_MAPS = 12

# قیدهای بازی (src/data/Balance.gd و tests/HeadlessTest.gd)
ROUTE_CLEARANCE = 46.0
KEEP_CLEARANCE = 76.0
TOWER_MIN_GAP = 54.0
PAD_MIN_ROAD = 90.0   # تست فقط ۸۰ می‌خواهد؛ حاشیهٔ امن‌تر
PAD_MIN_GAP = 58.0
SPUR_LEN = 46         # شاخه‌های ریز اسکلت که حذف می‌شوند


# ------------------------------------------------------------------ ماسک و اسکلت

def sharp_rect(img: Image.Image) -> tuple[int, int, int, int]:
    """کادر قسمت واضح تصویر؛ حاشیهٔ محو (پس‌زمینهٔ کشسان) بیرون می‌ماند."""
    gray = np.asarray(img.convert("L")).astype(np.float32)
    lap = np.abs(ndimage.laplace(gray))
    col = lap.mean(axis=0)
    row = lap.mean(axis=1)
    c_med = float(np.median(col)) or 1.0
    r_med = float(np.median(row)) or 1.0
    cols = np.nonzero(col > 0.25 * c_med)[0]
    rows = np.nonzero(row > 0.25 * r_med)[0]
    if len(cols) < 100 or len(rows) < 100:
        return 0, 0, W, H
    x0, x1 = int(cols[0]), int(cols[-1]) + 1
    y0, y1 = int(rows[0]), int(rows[-1]) + 1
    return max(0, x0 - 4), max(0, y0 - 4), min(W, x1 + 4), min(H, y1 + 4)


def road_mask(img: Image.Image, rect, dark_max=110, sat_max=24):
    a = np.asarray(img.convert("RGB")).astype(np.float32)
    mx = a.max(axis=2)
    mn = a.min(axis=2)
    sat = np.where(mx > 1, (mx - mn) / np.maximum(mx, 1), 0.0)
    mask = (mx < dark_max) & (sat < sat_max / 100.0)
    keep = np.zeros_like(mask)
    x0, y0, x1, y1 = rect
    keep[y0:y1, x0:x1] = True
    mask &= keep
    mask = ndimage.binary_closing(mask, np.ones((5, 5)))
    mask = ndimage.binary_opening(mask, np.ones((3, 3)))
    lab, n = ndimage.label(mask)
    if not n:
        return mask
    sizes = ndimage.sum(mask, lab, range(1, n + 1))
    return lab == int(np.argmax(sizes)) + 1


NEIGH = ((-1, -1), (-1, 0), (-1, 1), (0, -1), (0, 1), (1, -1), (1, 0), (1, 1))


def prune_spurs(skel: np.ndarray, min_len: int = SPUR_LEN) -> np.ndarray:
    """شاخه‌های کوتاه اسکلت (پهنای جاده، حاشیه‌ها) را می‌برد."""
    skel = skel.copy()
    h, w = skel.shape

    def nbrs(y, x):
        return [(ny, nx) for dy, dx in NEIGH
                for ny, nx in ((y + dy, x + dx),)
                if 0 <= ny < h and 0 <= nx < w and skel[ny, nx]]

    while True:
        removed = False
        ends = [(y, x) for y, x in zip(*np.nonzero(skel)) if len(nbrs(y, x)) == 1]
        for end in ends:
            path = [end]
            prev, cur = None, end
            while True:
                options = [p for p in nbrs(*cur) if p != prev]
                if not options:
                    break
                nxt = options[0]
                if len(nbrs(*nxt)) >= 3:
                    path.append(nxt)
                    break
                path.append(nxt)
                prev, cur = cur, nxt
                if len(path) > min_len:
                    break
            if len(path) <= min_len:
                for py, px in path[:-1] if len(path) > 1 else path:
                    if len(nbrs(py, px)) <= 2:
                        skel[py, px] = False
                        removed = True
        if not removed:
            return skel


def bfs(start, skel, want_path=False):
    h, w = skel.shape
    dist = {start: 0.0}
    parent = {start: None}
    q = deque([start])
    far, far_d = start, 0.0
    while q:
        y, x = q.popleft()
        for ny, nx in nbrs_of(y, x, skel, h, w):
            if (ny, nx) not in dist:
                dist[(ny, nx)] = dist[(y, x)] + float(np.hypot(ny - y, nx - x))
                parent[(ny, nx)] = (y, x)
                if dist[(ny, nx)] > far_d:
                    far, far_d = (ny, nx), dist[(ny, nx)]
                q.append((ny, nx))
    if not want_path:
        return far, far_d
    path, node = [], far
    while node is not None:
        path.append(node)
        node = parent[node]
    return path[::-1]


def nbrs_of(y, x, skel, h, w):
    return [(ny, nx) for dy, dx in NEIGH
            for ny, nx in ((y + dy, x + dx),)
            if 0 <= ny < h and 0 <= nx < w and skel[ny, nx]]


def centerline_path(mask):
    skel = prune_spurs(skeletonize(mask))
    lab, n = ndimage.label(skel, structure=np.ones((3, 3), int))
    if not n:
        raise RuntimeError("اسکلت جاده پیدا نشد")
    sizes = ndimage.sum(skel, lab, range(1, n + 1))
    skel = lab == int(np.argmax(sizes)) + 1
    ys, xs = np.nonzero(skel)
    a, _ = bfs((int(ys[0]), int(xs[0])), skel)
    path = bfs(a, skel, want_path=True)
    return [(float(x), float(y)) for y, x in path]


# ------------------------------------------------------------------ ساده‌سازی به قطعه‌های ۹۰ درجه

def resample(path, step=4.0):
    pts = np.array(path, dtype=float)
    d = np.r_[0.0, np.cumsum(np.hypot(np.diff(pts[:, 0]), np.diff(pts[:, 1])))]
    total = d[-1]
    ts = np.arange(0.0, total, step)
    return np.c_[np.interp(ts, d, pts[:, 0]), np.interp(ts, d, pts[:, 1])]


def orthogonal_runs(pts, window=7, min_run=34.0):
    n = len(pts)
    cls = []
    for i in range(n):
        j0, j1 = max(0, i - window), min(n - 1, i + window)
        dx = abs(pts[j1, 0] - pts[j0, 0])
        dy = abs(pts[j1, 1] - pts[j0, 1])
        cls.append(0 if dx >= dy else 1)

    runs, i = [], 0
    while i < n:
        j = i
        while j + 1 < n and cls[j + 1] == cls[i]:
            j += 1
        seg = pts[i:j + 1]
        runs.append({"axis": cls[i],
                     "fixed": float(np.median(seg[:, 1] if cls[i] == 0 else seg[:, 0])),
                     "length": float(np.hypot(seg[-1, 0] - seg[0, 0], seg[-1, 1] - seg[0, 1]))})
        i = j + 1

    # ادغام کوتاه‌ها: جفت‌های هم‌محور پس از حذف ران کوتاه به هم می‌رسند (گردی گوشه‌ها)
    changed = True
    while changed and len(runs) > 2:
        changed = False
        for k in range(1, len(runs) - 1):
            if runs[k]["length"] >= min_run:
                continue
            if runs[k - 1]["axis"] == runs[k + 1]["axis"]:
                merged = {
                    "axis": runs[k - 1]["axis"],
                    "fixed": float((runs[k - 1]["fixed"] + runs[k + 1]["fixed"]) / 2.0),
                    "length": runs[k - 1]["length"] + runs[k]["length"] + runs[k + 1]["length"],
                }
                runs[k - 1:k + 2] = [merged]
                changed = True
                break

    # رأس‌ها از تقاطع ران‌های متوالی
    verts = [tuple(pts[0])]
    for k in range(1, len(runs)):
        r0, r1 = runs[k - 1], runs[k]
        if r0["axis"] == r1["axis"]:
            continue
        verts.append((r1["fixed"], r0["fixed"]) if r0["axis"] == 0 else (r0["fixed"], r1["fixed"]))
    verts.append(tuple(pts[-1]))

    # تثبیت: هر قطعه کاملاً افقی یا کاملاً عمودی + حذف نقاط میانی هم‌راستا
    snapped = []
    for x, y in verts:
        if not snapped:
            snapped.append([x, y])
            continue
        px, py = snapped[-1]
        if abs(x - px) < 1.0 and abs(y - py) < 1.0:
            continue
        if abs(x - px) >= abs(y - py):
            y = py
        else:
            x = px
        snapped.append([x, y])
    out = []
    for v in snapped:
        if len(out) >= 2:
            a, b = out[-2], out[-1]
            if (abs(a[0] - b[0]) < 1e-6 and abs(b[0] - v[0]) < 1e-6) or \
               (abs(a[1] - b[1]) < 1e-6 and abs(b[1] - v[1]) < 1e-6):
                out[-1] = v
                continue
        out.append(v)
    return out


def relaxed_road(img: Image.Image, mask: np.ndarray) -> np.ndarray:
    """آسفالت با آستانهٔ بازتر (سه‌راههٔ کم‌رنگ و لبهٔ محو را هم می‌گیرد)."""
    a = np.asarray(img.convert("RGB")).astype(np.float32)
    mx, mn = a.max(axis=2), a.min(axis=2)
    sat = np.where(mx > 1, (mx - mn) / np.maximum(mx, 1), 0.0)
    return mask | ((mx < 152) & (sat < 0.3))


def fix_ends(verts, img, mask, rect, stub=30.0, margin=10.0):
    """دنبالهٔ ریز انتهایی را حذف و انتهای مسیر را تا لبهٔ جاده امتداد می‌دهد."""
    x0, y0, x1, y1 = rect
    road = relaxed_road(img, mask)
    h, w = road.shape

    # ۱) دنبالهٔ ریز: تکهٔ اول/آخر کوتاه‌تر از stub را می‌اندازد
    for _ in range(2):
        if len(verts) > 3 and float(np.hypot(verts[1][0] - verts[0][0], verts[1][1] - verts[0][1])) < stub:
            verts = verts[1:]
        if len(verts) > 3 and float(np.hypot(verts[-1][0] - verts[-2][0], verts[-1][1] - verts[-2][1])) < stub:
            verts = verts[:-1]

    def walk(px, py, dx, dy, limit_x, limit_y):
        """تا جایی که آسفالت ادامه دارد جلو می‌رود (با تحمل چند پیکسل گپ)."""
        step, miss, dist = 2.0, 0, 0.0
        while True:
            nx, ny = px + dx * step, py + dy * step
            ix, iy = int(round(nx)), int(round(ny))
            if not (0 <= ix < w and 0 <= iy < h):
                break
            if not (limit_x[0] - margin <= nx <= limit_x[1] + margin and
                    limit_y[0] - margin <= ny <= limit_y[1] + margin):
                break
            if road[iy, ix]:
                px, py, dist, miss = nx, ny, dist + step, 0
            else:
                miss += 1
                px, py = nx, ny
                if miss > 5:
                    break
        return dist

    # ۲) امتداد سر و ته در جهت همان قطعهٔ انتهایی
    head, second = np.array(verts[0], float), np.array(verts[1], float)
    d = head - second
    if abs(d[0]) > abs(d[1]):
        step = (np.sign(d[0]), 0.0)
    else:
        step = (0.0, np.sign(d[1]))
    ext = walk(head[0], head[1], step[0], step[1], (x0, x1), (y0, y1))
    if ext > 20.0:
        verts[0] = (head[0] + step[0] * ext, head[1] + step[1] * ext)

    tail, prev = np.array(verts[-1], float), np.array(verts[-2], float)
    d = tail - prev
    if abs(d[0]) > abs(d[1]):
        step = (np.sign(d[0]), 0.0)
    else:
        step = (0.0, np.sign(d[1]))
    ext = walk(tail[0], tail[1], step[0], step[1], (x0, x1), (y0, y1))
    if ext > 20.0:
        verts[-1] = (tail[0] + step[0] * ext, tail[1] + step[1] * ext)
    return verts


def orient(verts):
    """شروع را نزدیک گوشهٔ پایین‌چپ و پایان را نزدیک گوشهٔ بالا‌راست می‌گذارد."""
    def cost(a, b):
        return float(np.hypot(a[0], H - a[1]) + np.hypot(b[0] - W, b[1]))
    if cost(verts[-1], verts[0]) < cost(verts[0], verts[-1]):
        return verts[::-1]
    return verts


def sample(verts, step=3.0):
    pts = []
    for i in range(len(verts) - 1):
        (x0, y0), (x1, y1) = verts[i], verts[i + 1]
        seg = float(np.hypot(x1 - x0, y1 - y0))
        k = max(1, int(seg / step))
        for j in range(k):
            t = j / k
            pts.append((x0 + (x1 - x0) * t, y0 + (y1 - y0) * t))
    pts.append(tuple(verts[-1]))
    return np.array(pts)


def polyline_length(verts):
    return sum(float(np.hypot(verts[i + 1][0] - verts[i][0], verts[i + 1][1] - verts[i][1]))
               for i in range(len(verts) - 1))


def dist_point_seg(p, a, b):
    ap = np.array(p, float) - np.array(a, float)
    ab = np.array(b, float) - np.array(a, float)
    l2 = float(ab @ ab)
    t = 0.0 if l2 < 1e-9 else max(0.0, min(1.0, float(ap @ ab) / l2))
    return float(np.hypot(*(np.array(a, float) + ab * t - np.array(p, float))))


def dist_to_polyline(p, verts):
    return min(dist_point_seg(p, verts[i], verts[i + 1]) for i in range(len(verts) - 1))


def make_pads(verts, keep, mask, rect, max_pads=12):
    """جایگاه‌های ساخت: گوشهٔ جاده‌ها در اولویت، سپس شبکهٔ پراکنده؛ دور از جاده."""
    x0, y0, x1, y1 = rect
    cands = [(1.0, float(x), float(y))
             for y in np.arange(max(72.0, y0 + 46.0), min(H - 60.0, y1 - 46.0), 60.0)
             for x in np.arange(max(56.0, x0 + 46.0), min(W - 56.0, x1 - 46.0), 60.0)]
    for i in range(1, len(verts) - 1):
        cx, cy = verts[i]
        for sx in (-1, 1):
            for sy in (-1, 1):
                for dist in (100.0, 132.0, 168.0):
                    cands.append((0.0, cx + sx * dist * 0.7, cy + sy * dist * 0.7))
    chosen = []
    for _, x, y in sorted(cands):
        if not (x0 + 30.0 <= x <= x1 - 30.0 and y0 + 30.0 <= y <= y1 - 30.0):
            continue
        if dist_to_polyline((x, y), verts) < PAD_MIN_ROAD:
            continue
        if float(np.hypot(x - keep[0], y - keep[1])) < KEEP_CLEARANCE + 26.0:
            continue
        if any(float(np.hypot(x - q[0], y - q[1])) < PAD_MIN_GAP for q in chosen):
            continue
        if mask[int(round(y)), int(round(x))]:
            continue
        chosen.append((float(x), float(y)))
        if len(chosen) >= max_pads:
            break
    return chosen


# ------------------------------------------------------------------ اعتبارسنجی مثل تست بازی

def emulate_build_checks(verts, pads, keep):
    """همان بررسی‌های tests/HeadlessTest.gd را در پایتون تکرار می‌کند."""
    report = {}
    axis_ok = all(abs(verts[i][0] - verts[i + 1][0]) < 1e-6 or abs(verts[i][1] - verts[i + 1][1]) < 1e-6
                  for i in range(len(verts) - 1))
    right_angles = True
    for i in range(1, len(verts) - 1):
        a = np.array(verts[i], float) - np.array(verts[i - 1], float)
        b = np.array(verts[i + 1], float) - np.array(verts[i], float)
        if abs(float(a @ b)) > 1e-6:
            right_angles = False
    report["axis_aligned"] = axis_ok
    report["right_angles"] = right_angles
    report["length"] = polyline_length(verts)
    report["keep_equals_last"] = float(np.hypot(keep[0] - verts[-1][0], keep[1] - verts[-1][1])) < 1.0
    report["pad_min_dist"] = min(dist_to_polyline(p, verts) for p in pads) if pads else 0.0

    spots = placed = 0
    occupied = []
    gy = 54.0 + 24.0
    while gy < H - 24.0:
        gx = 32.0
        while gx < W - 32.0:
            p = (gx, gy)
            inside = 18.0 <= gx <= W - 18.0 and 18.0 <= gy <= H - 18.0
            free = inside and dist_to_polyline(p, verts) >= ROUTE_CLEARANCE
            if free:
                spots += 1
                if all(float(np.hypot(gx - q[0], gy - q[1])) >= TOWER_MIN_GAP for q in occupied) \
                        and float(np.hypot(gx - keep[0], gy - keep[1])) >= KEEP_CLEARANCE:
                    occupied.append(p)
                    placed += 1
            gx += 48.0
        gy += 48.0
    report["spots"] = spots
    report["placed"] = placed
    report["waypoint_count"] = len(verts)
    return report


def main():
    write_gd = "--write-maps-gd" in sys.argv
    result = {}
    print("%-8s %-11s %-6s %-7s %-7s %-6s %-6s %-6s %-7s" % (
        "map", "sharp", "رأس", "طول", "روی آسفالت", "p95", "pads", "spots", "placed"))
    for i in range(1, N_MAPS + 1):
        img = Image.open(os.path.join(SRC, "map_%02d.jpg" % i))
        rect = sharp_rect(img)
        mask = road_mask(img, rect)
        path = resample(centerline_path(mask), 4.0)
        verts = orthogonal_runs(path)
        verts = fix_ends(verts, img, mask, rect)
        verts = orient(verts)

        d_road = ndimage.distance_transform_edt(~mask)
        pts = sample(verts)
        xi = np.clip(pts[:, 0].round().astype(int), 0, W - 1)
        yi = np.clip(pts[:, 1].round().astype(int), 0, H - 1)
        inside = float(mask[yi, xi].mean() * 100)
        p95 = float(np.percentile(d_road[yi, xi], 95))

        verts = [(round(x, 3), round(y, 3)) for x, y in verts]
        keep = verts[-1]
        pads = make_pads(verts, keep, mask, rect)
        check = emulate_build_checks(verts, pads, keep)
        result[i] = {"verts": verts, "pads": pads, "sharp_rect": list(rect),
                     "inside": inside, "p95": p95, "check": check}
        print("%-8d %-11s %-6d %-7.0f %-9.1f %-6.1f %-6d %-6d %-7d" % (
            i, "%d..%d" % (rect[0], rect[2]), len(verts), check["length"], inside,
            p95, len(pads), check["spots"], check["placed"]))
        if not (check["axis_aligned"] and check["right_angles"] and check["keep_equals_last"]):
            print("   ⚠ ایراد هندسی: %s" % {k: check[k] for k in
                  ("axis_aligned", "right_angles", "keep_equals_last")})
        if check["length"] <= 260.0 or check["spots"] < 40 or check["placed"] < 15:
            print("   ⚠ ایراد تست: طول=%0.f spots=%d placed=%d" % (
                check["length"], check["spots"], check["placed"]))
        if check["pad_min_dist"] < 80.0:
            print("   ⚠ سکوی نزدیک جاده: %.0f" % check["pad_min_dist"])

    os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)
    json.dump(result, open(OUT_JSON, "w"), ensure_ascii=False, indent=1)
    print("\nذخیره شد: %s" % os.path.relpath(OUT_JSON, ROOT))


if __name__ == "__main__":
    main()
