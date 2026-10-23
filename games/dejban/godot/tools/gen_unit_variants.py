#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""موتور رندر کارتونی استودیویی (Cel-Shaded Toon Raytracer) برای سه نیروی دشمن جدید دژبان.

سه نیروی کاملاً مستقل با شکل ظاهری، سیلوئت و جزئیات کاملاً متمایز از نیروهای اولیه می‌سازد:
  ۱) تکاور (commando): تکاور کلاه‌خوددار سبز-تیره با عینک دید در شب دوقلو، جلیقهٔ ضدگلوله و آرپی‌جی روی شانه
  ۲) تانک زرهی (tank_armored): خودروی زرهی ۶چرخ شکارچی با بدنهٔ گوه‌ای، پرچم و توپ بلند ریل‌گان فیروزه‌ای
  ۳) تانک سنگین (tank_heavy): تانک غول‌پیکر دولول با تیغهٔ بولدوزر راه‌راه جلو، لانچر سقفی و دو لولهٔ توپ سنگین
"""

from __future__ import annotations

import math
import os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UNITS_DIR = os.path.join(ROOT, "assets", "sprites", "units")

INF = 1e9


def rot_z(deg: float) -> np.ndarray:
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0.0], [s, c, 0.0], [0.0, 0.0, 1.0]], dtype=np.float32)


def rot_y(deg: float) -> np.ndarray:
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0.0, s], [0.0, 1.0, 0.0], [-s, 0.0, c]], dtype=np.float32)


def rot_x(deg: float) -> np.ndarray:
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[1.0, 0.0, 0.0], [0.0, c, -s], [0.0, s, c]], dtype=np.float32)


class Scene3D:
    """بوم پرتونگاری ارتوگرافیک با خطوط جوهر کمیک و سایه‌زنی کارتونی هم‌سبک دژبان."""

    def __init__(self, out_size: int, ssaa: int, pivot_xy: tuple[float, float], elevation_deg: float = 30.0):
        self.out_size = out_size
        self.ssaa = ssaa
        self.res = out_size * ssaa
        self.pivot_xy = pivot_xy
        el = math.radians(elevation_deg)
        self.ray_dir = np.array([0.0, -math.cos(el), -math.sin(el)], dtype=np.float32)
        self.ray_dir /= np.linalg.norm(self.ray_dir)
        self.cam_right = np.array([1.0, 0.0, 0.0], dtype=np.float32)
        self.cam_up = np.cross(self.cam_right, self.ray_dir)
        self.cam_up /= np.linalg.norm(self.cam_up)
        if self.cam_up[2] < 0:
            self.cam_up = -self.cam_up

        px = (np.arange(self.res, dtype=np.float32) + 0.5) / ssaa - pivot_xy[0]
        py = pivot_xy[1] - (np.arange(self.res, dtype=np.float32) + 0.5) / ssaa
        gx, gy = np.meshgrid(px, py)
        self.ro = (
            gx[..., None] * self.cam_right[None, None, :]
            + gy[..., None] * self.cam_up[None, None, :]
            - 260.0 * self.ray_dir[None, None, :]
        )
        self.rd = np.broadcast_to(self.ray_dir[None, None, :], self.ro.shape).copy()
        self.reset()

    def project(self, world_pt: np.ndarray) -> tuple[float, float]:
        x = self.pivot_xy[0] + float(np.dot(world_pt, self.cam_right))
        y = self.pivot_xy[1] - float(np.dot(world_pt, self.cam_up))
        return (x, y)

    def reset(self) -> None:
        h, w = self.res, self.res
        self.t = np.full((h, w), INF, dtype=np.float32)
        self.nrm = np.zeros((h, w, 3), dtype=np.float32)
        self.col = np.zeros((h, w, 3), dtype=np.float32)
        self.emit = np.zeros((h, w), dtype=np.float32)
        self.part = np.zeros((h, w), dtype=np.int32)
        self.z_hit = np.zeros((h, w), dtype=np.float32)

    def _bbox_slice(self, center: np.ndarray, radius: float) -> tuple[slice, slice] | None:
        sx, sy = self.project(center)
        margin = radius + 2.5
        x0 = max(0, int(math.floor((sx - margin) * self.ssaa)))
        x1 = min(self.res, int(math.ceil((sx + margin) * self.ssaa)))
        y0 = max(0, int(math.floor((sy - margin) * self.ssaa)))
        y1 = min(self.res, int(math.ceil((sy + margin) * self.ssaa)))
        if x1 <= x0 or y1 <= y0:
            return None
        return (slice(y0, y1), slice(x0, x1))

    def _commit_roi(self, ys: slice, xs: slice, mask: np.ndarray, t_val: np.ndarray,
                    n_world: np.ndarray, rgb, part_id: int, emissive: float = 0.0) -> None:
        cur_t = self.t[ys, xs]
        upd = mask & (t_val < cur_t) & (t_val > 0.0)
        if not np.any(upd):
            return
        cur_t[upd] = t_val[upd]
        self.nrm[ys, xs][upd] = n_world[upd]
        if isinstance(rgb, np.ndarray) and rgb.ndim == 3:
            self.col[ys, xs][upd] = rgb[upd]
        else:
            self.col[ys, xs][upd] = np.asarray(rgb, dtype=np.float32)
        self.emit[ys, xs][upd] = emissive
        self.part[ys, xs][upd] = part_id
        self.z_hit[ys, xs][upd] = self.ro[ys, xs][upd, 2] + t_val[upd] * self.rd[ys, xs][upd, 2]

    def add_obb(self, center, half_size, basis: np.ndarray, color, part_id: int,
                emissive: float = 0.0, stripe_fn=None) -> None:
        c = np.asarray(center, dtype=np.float32)
        hs = np.asarray(half_size, dtype=np.float32)
        roi = self._bbox_slice(c, float(np.linalg.norm(hs)))
        if roi is None:
            return
        ys, xs = roi
        ro_l = (self.ro[ys, xs] - c) @ basis
        rd_l = self.rd[ys, xs] @ basis
        inv_d = np.where(np.abs(rd_l) > 1e-6, 1.0 / np.where(np.abs(rd_l) > 1e-6, rd_l, 1.0), 1e6 * np.sign(rd_l + 1e-12))
        t0 = (-hs - ro_l) * inv_d
        t1 = (hs - ro_l) * inv_d
        tmin = np.maximum(np.maximum(np.minimum(t0[..., 0], t1[..., 0]), np.minimum(t0[..., 1], t1[..., 1])),
                          np.minimum(t0[..., 2], t1[..., 2]))
        tmax = np.minimum(np.minimum(np.maximum(t0[..., 0], t1[..., 0]), np.maximum(t0[..., 1], t1[..., 1])),
                          np.maximum(t0[..., 2], t1[..., 2]))
        hit = tmax >= np.maximum(tmin, 0.0)
        if not np.any(hit):
            return
        p_l = ro_l + tmin[..., None] * rd_l
        rel = np.abs(p_l) / np.maximum(hs, 1e-4)
        axis = np.argmax(rel, axis=-1)
        n_l = np.zeros_like(p_l)
        for ax in range(3):
            sel = axis == ax
            n_l[sel, ax] = np.sign(p_l[sel, ax])
        bevel = np.clip((rel - 0.72) / 0.28, 0.0, 1.0) * np.sign(p_l)
        n_l = n_l + 0.42 * bevel
        n_l /= np.maximum(np.linalg.norm(n_l, axis=-1, keepdims=True), 1e-5)
        n_w = n_l @ basis.T
        rgb = stripe_fn(p_l) if stripe_fn is not None else color
        self._commit_roi(ys, xs, hit, tmin, n_w, rgb, part_id, emissive)

    def add_cylinder(self, p0, p1, radius: float, color, part_id: int,
                     emissive: float = 0.0, cap_color=None) -> None:
        a = np.asarray(p0, dtype=np.float32)
        b = np.asarray(p1, dtype=np.float32)
        ba = b - a
        baba = float(np.dot(ba, ba))
        if baba < 1e-6:
            return
        mid = 0.5 * (a + b)
        roi = self._bbox_slice(mid, 0.5 * math.sqrt(baba) + radius)
        if roi is None:
            return
        ys, xs = roi
        ro_s = self.ro[ys, xs]
        rd_s = self.rd[ys, xs]
        oa = ro_s - a
        bard = np.sum(rd_s * ba, axis=-1)
        baoa = np.sum(oa * ba, axis=-1)
        k2 = baba - bard * bard
        k1 = baba * np.sum(oa * rd_s, axis=-1) - baoa * bard
        k0 = baba * np.sum(oa * oa, axis=-1) - baoa * baoa - radius * radius * baba

        h = k1 * k1 - k2 * k0
        safe_k2 = np.where(np.abs(k2) > 1e-6, k2, 1.0)
        sqrt_h = np.sqrt(np.maximum(h, 0.0))

        t_body = (-k1 - sqrt_h) / safe_k2
        y_body = baoa + t_body * bard
        hit_body = (h >= 0.0) & (np.abs(k2) > 1e-6) & (y_body > 0.0) & (y_body < baba) & (t_body > 0.0)

        safe_bard = np.where(np.abs(bard) > 1e-6, bard, 1.0)
        t_cap = ((np.where(bard < 0.0, baba, 0.0)) - baoa) / safe_bard
        hit_cap = (np.abs(bard) > 1e-6) & (np.abs(k1 + safe_k2 * t_cap) < sqrt_h) & (h >= 0.0) & (t_cap > 0.0)

        use_body = hit_body & (~hit_cap | (t_body <= t_cap))
        use_cap = hit_cap & ~use_body

        if np.any(use_body):
            pa = oa + t_body[..., None] * rd_s
            proj = (y_body / baba)[..., None] * ba
            n_w = (pa - proj) / max(radius, 1e-4)
            n_w /= np.maximum(np.linalg.norm(n_w, axis=-1, keepdims=True), 1e-5)
            self._commit_roi(ys, xs, use_body, t_body, n_w, color, part_id, emissive)

        if np.any(use_cap):
            sign_cap = np.where(bard < 0.0, 1.0, -1.0)[..., None]
            n_w = np.broadcast_to(ba / math.sqrt(baba), ro_s.shape) * sign_cap
            self._commit_roi(ys, xs, use_cap, t_cap, n_w, cap_color if cap_color is not None else color, part_id, emissive)

    def add_capsule(self, p0, p1, radius: float, color, part_id: int, emissive: float = 0.0) -> None:
        """کپسول نرم سه‌بعدی با دو سر کروی (ایده‌آل برای اندام‌های کارتونی نرم و لولهٔ توپ)."""
        a = np.asarray(p0, dtype=np.float32)
        b = np.asarray(p1, dtype=np.float32)
        ba = b - a
        baba = float(np.dot(ba, ba))
        mid = 0.5 * (a + b)
        roi = self._bbox_slice(mid, 0.5 * math.sqrt(baba) + radius)
        if roi is None:
            return
        ys, xs = roi
        ro_s = self.ro[ys, xs]
        rd_s = self.rd[ys, xs]
        oa = ro_s - a
        bard = np.sum(rd_s * ba, axis=-1)
        baoa = np.sum(oa * ba, axis=-1)
        rdoa = np.sum(rd_s * oa, axis=-1)
        oaoa = np.sum(oa * oa, axis=-1)

        k_a = baba - bard * bard
        k_b = baba * rdoa - baoa * bard
        k_c = baba * oaoa - baoa * baoa - radius * radius * baba
        h = k_b * k_b - k_a * k_c
        safe_a = np.where(np.abs(k_a) > 1e-6, k_a, 1.0)
        t_cyl = (-k_b - np.sqrt(np.maximum(h, 0.0))) / safe_a
        y = baoa + t_cyl * bard
        hit_cyl = (h >= 0.0) & (np.abs(k_a) > 1e-6) & (y > 0.0) & (y < baba)

        oc = np.where((y <= 0.0)[..., None], oa, ro_s - b)
        b_sph = np.sum(rd_s * oc, axis=-1)
        c_sph = np.sum(oc * oc, axis=-1) - radius * radius
        h_sph = b_sph * b_sph - c_sph
        t_sph = -b_sph - np.sqrt(np.maximum(h_sph, 0.0))

        hit = hit_cyl | (h_sph >= 0.0)
        if not np.any(hit):
            return
        t_val = np.where(hit_cyl, t_cyl, t_sph)
        p_hit = ro_s + t_val[..., None] * rd_s
        pa = p_hit - a
        h_proj = np.clip(np.sum(pa * ba, axis=-1) / max(baba, 1e-6), 0.0, 1.0)
        n_w = (pa - h_proj[..., None] * ba) / max(radius, 1e-4)
        n_w /= np.maximum(np.linalg.norm(n_w, axis=-1, keepdims=True), 1e-5)
        self._commit_roi(ys, xs, hit, t_val, n_w, color, part_id, emissive)

    def add_ellipsoid(self, center, radii, basis: np.ndarray, color, part_id: int,
                      emissive: float = 0.0) -> None:
        c = np.asarray(center, dtype=np.float32)
        rad = np.asarray(radii, dtype=np.float32)
        roi = self._bbox_slice(c, float(np.max(rad)))
        if roi is None:
            return
        ys, xs = roi
        ro_l = ((self.ro[ys, xs] - c) @ basis) / rad
        rd_l = (self.rd[ys, xs] @ basis) / rad
        a = np.sum(rd_l * rd_l, axis=-1)
        b = np.sum(ro_l * rd_l, axis=-1)
        cc = np.sum(ro_l * ro_l, axis=-1) - 1.0
        disc = b * b - a * cc
        hit = disc >= 0.0
        if not np.any(hit):
            return
        t_val = (-b - np.sqrt(np.maximum(disc, 0.0))) / np.maximum(a, 1e-6)
        p_l = ro_l + t_val[..., None] * rd_l
        n_l = p_l / rad
        n_l /= np.maximum(np.linalg.norm(n_l, axis=-1, keepdims=True), 1e-5)
        n_w = n_l @ basis.T
        self._commit_roi(ys, xs, hit, t_val, n_w, color, part_id, emissive)

    def render_image(
        self,
        shadow_radius: tuple[float, float] | None = (42.0, 22.0),
        shadow_alpha: int = 122,
        outline_px: float = 2.1,
        ink_rgb: tuple[float, float, float] = (18.0, 19.0, 24.0),
        crease_strength: float = 0.92,
    ) -> Image.Image:
        hit = self.t < INF * 0.5
        rgb = np.zeros((self.res, self.res, 4), dtype=np.float32)

        if np.any(hit):
            light = np.array([-0.42, -0.32, 0.85], dtype=np.float32)
            light /= np.linalg.norm(light)
            ndotl = np.sum(self.nrm * light[None, None, :], axis=-1)

            band = np.where(ndotl > 0.48, 1.14, np.where(ndotl > 0.02, 0.92, 0.62))
            smooth = 0.64 + 0.50 * np.clip(ndotl * 0.5 + 0.5, 0.0, 1.0)
            shade = 0.62 * band + 0.38 * smooth
            ao = 0.85 + 0.15 * np.clip(self.z_hit / 18.0, 0.0, 1.0)
            spec = np.where((ndotl > 0.82) & (self.emit < 0.2), 22.0, 0.0)

            lit_rgb = self.col * (shade * ao)[..., None] + spec[..., None]
            em = self.emit[..., None]
            final_rgb = lit_rgb * (1.0 - em) + (self.col * 1.10 + 22.0) * em

            r_sil = max(1, int(round(outline_px * self.ssaa)))
            outer_ink = ndimage.binary_dilation(hit, iterations=r_sil) & (~hit)

            # خط جوهر کامل روی مرز تمام قطعات جدا (چرخ، رینگ، برجک، دریچه، لوله، عینک، کلاه، آرپی‌جی)
            k_seam = max(2, int(round(self.ssaa * 1.15)))
            part_max = ndimage.maximum_filter(self.part, size=k_seam)
            part_min = ndimage.minimum_filter(self.part, size=k_seam)
            depth_jump = ndimage.maximum_filter(self.t, size=k_seam) - ndimage.minimum_filter(self.t, size=k_seam)
            crease = hit & ((part_max != part_min) | (depth_jump > 6.5))

            ink_color = np.asarray(ink_rgb, dtype=np.float32)
            final_rgb[crease] = final_rgb[crease] * (1.0 - crease_strength) + ink_color * crease_strength

            rgb[hit, :3] = np.clip(final_rgb[hit], 0.0, 255.0)
            rgb[hit, 3] = 255.0
            rgb[outer_ink, :3] = ink_color
            rgb[outer_ink, 3] = 255.0

        fg = Image.fromarray(rgb.astype(np.uint8), "RGBA")
        if self.ssaa > 1:
            fg = fg.resize((self.out_size, self.out_size), Image.LANCZOS)

        if shadow_radius is None:
            return fg

        base = Image.new("RGBA", (self.out_size, self.out_size), (0, 0, 0, 0))
        sh = Image.new("RGBA", (self.out_size, self.out_size), (0, 0, 0, 0))
        sd = ImageDraw.Draw(sh)
        cx, cy = self.pivot_xy
        rx, ry = shadow_radius
        sd.ellipse((cx - rx, cy - ry + 2.0, cx + rx, cy + ry + 2.0), fill=(12, 13, 16, shadow_alpha))
        sh = sh.filter(ImageFilter.GaussianBlur(radius=4.0))
        base.alpha_composite(sh)
        base.alpha_composite(fg)
        return base


def draw_glow_burst(img: Image.Image, xy: tuple[float, float], target_xy: tuple[float, float],
                    core_rgb: tuple[int, int, int], halo_rgb: tuple[int, int, int],
                    strength: float) -> Image.Image:
    if strength <= 0.01:
        return img
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    x0, y0 = xy
    x1, y1 = target_xy
    if strength > 0.45:
        gd.line([(x0, y0), (x1, y1)], fill=(*halo_rgb, int(225 * strength)), width=int(round(15 * strength)))
        gd.ellipse((x1 - 7.5 * strength, y1 - 7.5 * strength, x1 + 7.5 * strength, y1 + 7.5 * strength),
                   fill=(*halo_rgb, int(230 * strength)))
    r_ring = 10.5 * strength + 3.0
    gd.ellipse((x0 - r_ring, y0 - r_ring, x0 + r_ring, y0 + r_ring),
               outline=(*halo_rgb, int(240 * min(1.0, strength * 1.2))), width=3)
    glow_blur = glow.filter(ImageFilter.GaussianBlur(radius=2.8))

    sharp = Image.new("RGBA", img.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(sharp)
    if strength > 0.45:
        sd.line([(x0, y0), (x1, y1)], fill=(*core_rgb, int(250 * strength)), width=max(2, int(round(6 * strength))))
        sd.line([(x0, y0), (x1, y1)], fill=(255, 255, 255, int(240 * strength)), width=2)
        s = 9.5 * strength
        poly = [(x0, y0 - s), (x0 + s * 0.35, y0 - s * 0.35), (x0 + s, y0),
                (x0 + s * 0.35, y0 + s * 0.35), (x0, y0 + s), (x0 - s * 0.35, y0 + s * 0.35),
                (x0 - s, y0), (x0 - s * 0.35, y0 - s * 0.35)]
        sd.polygon(poly, fill=(255, 255, 255, int(248 * strength)), outline=(*halo_rgb, 255))
    else:
        rr = 6.0 + 3.5 * strength
        sd.ellipse((x0 - rr, y0 - rr, x0 + rr, y0 + rr), outline=(*core_rgb, int(220 * strength + 30)), width=2)

    out = img.copy()
    out.alpha_composite(glow_blur)
    out.alpha_composite(sharp)
    return out


def draw_smoke_puffs(img: Image.Image, puffs: list[tuple[float, float, float, int]]) -> Image.Image:
    out = img.copy()
    d = ImageDraw.Draw(out)
    for x, y, r, alpha in puffs:
        if r <= 0.8 or alpha <= 5:
            continue
        d.ellipse((x - r - 1.3, y - r * 0.8 - 1.3, x + r + 1.3, y + r * 0.8 + 1.3),
                  fill=(24, 26, 32, int(alpha * 0.78)))
        d.ellipse((x - r, y - r * 0.78, x + r, y + r * 0.78),
                  fill=(244, 246, 250, alpha))
    return out


# ====================================================================== ۱) تکاور کماندوی آرپی‌جی‌زن (commando)

def build_rpg_commando(scene: Scene3D, dir_name: str, anim: str, frame: int) -> list:
    """تکاور چیبی کارتونی با کلاه تکاوری سبز تیره، عینک دید در شب دوقلوی درخشان، شال صورت و آرپی‌جی بزرگ روی شانه!"""
    scene.reset()
    yaw_map = {"right": 0.0, "front": 26.0, "left": 180.0, "back": 206.0}
    yaw = yaw_map[dir_name]

    stride = 0.0
    bob = 0.0
    fall_angle = 0.0
    fall_z = 0.0
    helm_fly = 0.0
    puffs = []

    if anim == "run":
        ph = (frame / 8.0) * math.tau
        stride = math.sin(ph)
        bob = 2.0 * abs(math.cos(ph))
    elif anim == "walk":
        ph = (frame / 8.0) * math.tau
        stride = 0.72 * math.sin(ph)
        bob = 1.1 * abs(math.cos(ph))
    elif anim == "death":
        # افتادن کارتونی کامل داخل قاب ۶۴×۶۴ بدون بریده شدن پایین قاب!
        angles = [-10.0, -25.0, -50.0, -76.0, -84.0, -86.0]
        z_shifts = [2.0, 4.5, 4.5, 4.0, 3.5, 3.5]
        helm_fly = [0.0, 4.5, 8.5, 5.0, 1.5, 0.0][frame]
        fall_angle = angles[frame]
        fall_z = z_shifts[frame]
        stride = 0.65 if frame < 2 else 0.1

    B_yaw = rot_z(yaw)
    B_body = B_yaw @ rot_y(-fall_angle)
    fall_rad = math.radians(abs(fall_angle))
    pivot_world = B_yaw @ np.array([-13.5 * math.sin(fall_rad), 0.0, 0.0], dtype=np.float32) + np.array([0.0, 0.0, 10.0 + fall_z], dtype=np.float32)

    def P(lx: float, ly: float, lz: float) -> np.ndarray:
        return pivot_world + B_body @ np.array([lx, ly, (lz - 10.0) + bob], dtype=np.float32)

    CAMO_SUIT = (104, 132, 166)      # لباس آبی-خاکستری روشن تکاوری (تضاد عالی روی چمن، شن و خاکستر!)
    VEST_DARK = (46, 54, 68)         # جلیقهٔ زرهی دودی
    POUCH_TAN = (196, 146, 76)       # خشاب‌دان و کمربند چرمی روشن
    BOOT_DARK = (78, 52, 38)         # پوتین نظامی چرمی
    SKIN_PEACH = (248, 186, 138)     # پوست گرم کارتونی مشابه سرباز
    BANDANA_DARK = (42, 48, 60)      # ماسک/شال تاکتیکی پایین صورت
    HELM_SLATE = (64, 84, 108)       # کلاه‌خود تکاوری فولادی-سرمه‌ای
    NVG_GREEN = (98, 255, 132)       # عینک دید در شب دوقلوی فسفری درخشان!
    RPG_TUBE = (86, 98, 76)          # لولهٔ آرپی‌جی روی شانه
    WARHEAD_ORANGE = (244, 96, 42)   # کلاهک موشک نارنجی-قرمز برجسته!
    GOLD_BADGE = (244, 196, 68)

    # ۱) پاهای کارتونی با گام بلند رو به جلو و عقب (دقیقاً هم‌ریتم سرباز اصلی!)
    for sgn, pm in ((-1.0, 1.0), (1.0, -1.0)):
        rad = math.radians(stride * pm * 52.0)
        hip = P(0.0, sgn * 4.8, 15.0)
        foot = P(math.sin(rad) * 10.5, sgn * 4.8, 15.0 - math.cos(rad) * 10.2)
        toe = P(math.sin(rad) * 10.5 + 3.2 * math.cos(rad), sgn * 4.8, 15.0 - math.cos(rad) * 10.2 + 2.2 * max(0.0, math.sin(rad)))
        scene.add_capsule(hip, foot, 3.6, CAMO_SUIT, part_id=10 + (1 if sgn > 0 else 0))
        scene.add_capsule(foot, toe, 3.9, BOOT_DARK, part_id=12 + (1 if sgn > 0 else 0))

    # ۲) تنهٔ چیبی با جلیقهٔ تاکتیکی، کمربند و کوله‌پشتی مهمات
    scene.add_ellipsoid(P(0.0, 0.0, 22.0), (7.6, 8.6, 8.2), B_body, CAMO_SUIT, part_id=2)
    scene.add_obb(P(0.4, 0.0, 21.8), (6.8, 7.6, 5.2), B_body, VEST_DARK, part_id=3)
    # کمربند و کیسه‌های خشاب
    scene.add_obb(P(0.4, 0.0, 16.8), (7.1, 7.9, 1.7), B_body, POUCH_TAN, part_id=4)
    scene.add_obb(P(7.2, 0.0, 16.8), (1.0, 2.2, 2.0), B_body, GOLD_BADGE, part_id=5)
    # کولهٔ رادیویی + آنتن کوتاه پشت
    scene.add_obb(P(-7.2, 0.0, 23.0), (3.4, 6.2, 6.2), B_body, POUCH_TAN, part_id=6)
    scene.add_cylinder(P(-8.0, -4.2, 28.0), P(-9.0, -4.5, 38.0), 1.0, VEST_DARK, part_id=7)

    # ۳) سر بزرگ و بانمک چیبی (تناسب دقیق سرباز بازی!) + ماسک تاکتیکی + کلاه تکاوری + عینک دید در شب دوقلو!
    scene.add_ellipsoid(P(0.6, 0.0, 35.5), (10.2, 11.2, 10.2), B_body, SKIN_PEACH, part_id=20)
    # ماسک تاکتیکی روی بینی و دهان (تفاوت ۱۰۰٪ چهره با سرباز معمولی!)
    scene.add_ellipsoid(P(2.6, 0.0, 31.8), (9.0, 9.6, 5.2), B_body, BANDANA_DARK, part_id=21)
    # چشم‌ها و ابروهای مصمم کارتونی
    for sgn in (-1.0, 1.0):
        scene.add_ellipsoid(P(10.0, sgn * 3.8, 35.8), (1.4, 1.5, 1.9), B_body, (24, 18, 18), part_id=22)
        brow_b = B_body @ rot_x(sgn * 16.0)
        scene.add_obb(P(10.2, sgn * 3.9, 38.0), (1.2, 2.3, 0.8), brow_b, (32, 22, 18), part_id=23)

    # کلاه‌خود تکاوری (در انیمیشن مرگ به هوا می‌پرد دقیقاً مثل سرباز اصلی!)
    h_pos = P(0.0, 0.0, 39.2 + helm_fly)
    scene.add_ellipsoid(h_pos, (11.6, 12.6, 8.8), B_body, HELM_SLATE, part_id=30)
    scene.add_obb(h_pos + B_body @ np.array([0.0, 0.0, -1.8], dtype=np.float32), (11.9, 12.8, 1.5), B_body, VEST_DARK, part_id=31)
    # دو لنز گرد بزرگ عینک دید در شب فسفری روی پیشانی کلاه‌خود!
    for sgn in (-1.0, 1.0):
        g_in = h_pos + B_body @ np.array([8.5, sgn * 4.2, -0.4], dtype=np.float32)
        g_out = h_pos + B_body @ np.array([12.6, sgn * 4.2, -0.4], dtype=np.float32)
        scene.add_cylinder(g_in, g_out, 3.1, VEST_DARK, part_id=32 + (1 if sgn > 0 else 0), cap_color=NVG_GREEN)
        scene.add_ellipsoid(g_out, (1.1, 2.3, 2.3), B_body, NVG_GREEN, part_id=34 + (1 if sgn > 0 else 0), emissive=0.95)

    # ۴) دست چپ در حال دویدن + دست راست و لانچر بزرگ آرپی‌جی روی شانه!
    arm_sw = -stride * 4.5
    scene.add_capsule(P(0.0, 9.5, 24.5), P(3.5 + arm_sw, 10.5, 18.0), 3.1, CAMO_SUIT, part_id=40)
    scene.add_ellipsoid(P(4.5 + arm_sw, 10.8, 17.5), (2.9, 2.9, 2.9), B_body, BOOT_DARK, part_id=41)

    scene.add_capsule(P(0.5, -9.5, 24.5), P(6.5, -9.8, 25.5), 3.1, CAMO_SUIT, part_id=42)
    # لولهٔ بلند آرپی‌جی از پشت شانه تا جلو + کلاهک موشکی نارنجی-سرخ بزرگ بیرون‌زده!
    rpg_b = B_body @ rot_y(-8.0)
    p_back = P(-13.5, -10.2, 28.5)
    p_front = P(13.5, -10.2, 26.5)
    scene.add_cylinder(p_back, p_front, 3.3, RPG_TUBE, part_id=50, cap_color=VEST_DARK)
    # قیف خروجی عقب آرپی‌جی
    scene.add_cylinder(P(-16.2, -10.2, 28.8), p_back, 4.3, VEST_DARK, part_id=51)
    # کلاهک مخروطی/پیازی نارنجی-سرخ موشک در جلو!
    warhead_c = P(17.2, -10.2, 26.1)
    scene.add_ellipsoid(warhead_c, (5.8, 4.2, 4.2), rpg_b, WARHEAD_ORANGE, part_id=52)
    scene.add_obb(P(22.0, -10.2, 25.7), (1.6, 1.3, 1.3), rpg_b, GOLD_BADGE, part_id=53)

    if anim == "death" and frame in (3, 4):
        px, py = scene.project(np.array([0.0, 0.0, 3.0], dtype=np.float32))
        puffs.append((px - 10.0, py + 1.0, 5.2 if frame == 3 else 3.2, 185 if frame == 3 else 110))
        puffs.append((px + 10.0, py + 0.5, 4.6 if frame == 3 else 2.8, 175 if frame == 3 else 100))
    return puffs


# ====================================================================== ۲) تانک زرهی ۶چرخ شکارچی بزرگ (tank_armored)

def build_striker_tank(scene: Scene3D, dir_idx: int, anim: str, frame: int) -> tuple:
    """خودروی زرهی ۶چرخ شکارچی هم‌قواره با تانک اصلی (عرض ~۹۰ پیکسل) + توپ بلند ریل‌گان فیروزه‌ای و پرچم."""
    scene.reset()
    yaw = dir_idx * 45.0
    B = rot_z(yaw)
    S = 1.24  # مقیاس دقیق هم‌اندازهٔ تانک اصلی در کادر ۱۲۸×۱۲۸

    bob = 0.0
    wheel_spin = 0.0
    recoil = 0.0
    if anim == "idle":
        bob = 0.65 * math.sin(frame * math.pi * 0.5)
    elif anim == "move":
        bob = 1.25 * math.sin(frame * math.tau / 6.0)
        wheel_spin = frame * 30.0
    elif anim == "attack":
        recoil = [0.0, -1.2, 6.2, 4.4, 2.0, 0.0][frame]
        bob = -0.9 if frame in (2, 3) else 0.0

    def T(lx: float, ly: float, lz: float) -> np.ndarray:
        return B @ np.array([lx * S, ly * S, lz * S + bob], dtype=np.float32)

    STEEL_DARK = (46, 54, 68)
    TIRE_DARK = (32, 35, 42)
    HUB_SILVER = (196, 210, 226)
    ARMOR_BLUE = (74, 134, 206)
    ARMOR_TOP = (132, 188, 246)
    FENDER_NAVY = (48, 86, 142)
    CYAN_GLOW = (76, 242, 255)
    GOLD_ACCENT = (242, 186, 62)

    # ۱) شاسی سنگین زیرین
    scene.add_obb(T(0.0, 0.0, 11.5), (27.0 * S, 15.0 * S, 5.8 * S), B, STEEL_DARK, part_id=1)

    # ۲) ۶ چرخ بزرگ آفرود زرهی با رینگ نقره‌ای و توپی طلایی (در تمام نماها کاملاً برجسته!)
    for wi, wx in enumerate((-19.0, 0.0, 19.0)):
        for si, wy in enumerate((-19.2, 19.2)):
            pid = 10 + wi * 2 + si
            sgn = np.sign(wy)
            scene.add_cylinder(T(wx, wy - 3.6 * sgn, 10.0), T(wx, wy + 3.6 * sgn, 10.0), 9.8 * S, TIRE_DARK, part_id=pid)
            scene.add_cylinder(T(wx, wy - 3.9 * sgn, 10.0), T(wx, wy + 3.9 * sgn, 10.0), 5.2 * S, HUB_SILVER, part_id=pid + 20)
            scene.add_cylinder(T(wx, wy - 4.2 * sgn, 10.0), T(wx, wy + 4.2 * sgn, 10.0), 2.1 * S, GOLD_ACCENT, part_id=pid + 40)
            sp_b = B @ rot_y(wheel_spin + wi * 35.0)
            scene.add_obb(T(wx, wy + 3.8 * sgn, 10.0), (1.3 * S, 0.7 * S, 4.9 * S), sp_b, STEEL_DARK, part_id=pid + 60)

    # ۳) بدنهٔ گوه‌ای چندتکه با گلگیرهای زرهی و دماغهٔ تیز شکارچی
    scene.add_obb(T(-1.5, 0.0, 19.8), (27.5 * S, 17.8 * S, 6.2 * S), B, ARMOR_BLUE, part_id=2)
    scene.add_obb(T(-2.0, 0.0, 26.2), (21.5 * S, 14.8 * S, 2.2 * S), B, ARMOR_TOP, part_id=3)
    glacis_b = B @ rot_y(24.0)
    scene.add_obb(T(22.5, 0.0, 17.0), (11.5 * S, 15.2 * S, 4.4 * S), glacis_b, ARMOR_TOP, part_id=4)
    for sgn in (-1.0, 1.0):
        scene.add_obb(T(-1.0, sgn * 19.6, 17.5), (25.0 * S, 2.6 * S, 3.4 * S), B, FENDER_NAVY, part_id=5 + (1 if sgn > 0 else 0))
        scene.add_obb(T(3.0, sgn * 21.5, 17.8), (11.0 * S, 0.9 * S, 1.4 * S), B, CYAN_GLOW, part_id=7, emissive=0.9)
        # چراغ‌های دوقلوی درخشان جلو
        scene.add_ellipsoid(T(30.0, sgn * 9.8, 15.6), (2.4 * S, 3.0 * S, 2.6 * S), B, GOLD_ACCENT, part_id=8, emissive=0.8)

    # ۴) برجک شش‌ضلعی مدرن + دریچهٔ فرمانده + پرچم آبی-فیروزه‌ای روی آنتن بلند (امضای هنری مشابه تانک اصلی!)
    scene.add_obb(T(-2.5, 0.0, 31.5), (15.5 * S, 13.5 * S, 5.6 * S), B, ARMOR_TOP, part_id=70)
    scene.add_obb(T(8.5, 0.0, 30.8), (7.5 * S, 10.5 * S, 4.6 * S), B @ rot_y(18.0), ARMOR_BLUE, part_id=71)
    scene.add_cylinder(T(-5.5, -4.5, 36.8), T(-5.5, -4.5, 39.8), 5.2 * S, STEEL_DARK, part_id=72, cap_color=GOLD_ACCENT)
    # میلهٔ آنتن بلند + پرچم مثلثی فیروزه‌ای در عقب
    scene.add_cylinder(T(-15.0, 10.5, 34.0), T(-15.0, 10.5, 52.0), 1.15 * S, STEEL_DARK, part_id=73)
    scene.add_obb(T(-19.5, 10.5, 48.5), (4.5 * S, 0.8 * S, 3.0 * S), B, CYAN_GLOW, part_id=74, emissive=0.65)
    scene.add_ellipsoid(T(-15.0, 10.5, 53.2), (1.8 * S, 1.8 * S, 1.8 * S), B, GOLD_ACCENT, part_id=75, emissive=0.85)

    # ۵) توپ بلند ریل‌گان با حلقه‌های پلاسمای فیروزه‌ای در جلو
    bx0 = 10.0 - recoil
    bx1 = 42.5 - recoil
    scene.add_obb(T(14.5 - recoil * 0.4, 0.0, 31.2), (7.5 * S, 5.5 * S, 4.0 * S), B, STEEL_DARK, part_id=80)
    scene.add_cylinder(T(bx0, 0.0, 31.2), T(bx1, 0.0, 31.2), 3.3 * S, STEEL_DARK, part_id=81, cap_color=CYAN_GLOW)
    for cx_off in (21.5, 29.5, 37.5):
        cp = cx_off - recoil
        scene.add_cylinder(T(cp - 1.3, 0.0, 31.2), T(cp + 1.3, 0.0, 31.2), 4.6 * S, CYAN_GLOW, part_id=82, emissive=0.92)

    muzzle_2d = scene.project(T(bx1 + 2.0, 0.0, 31.2))
    aim_2d = scene.project(T(bx1 + 15.0, 0.0, 31.2))

    puffs = []
    if anim == "move":
        phase = frame / 6.0
        for sgn in (-1.0, 1.0):
            px, py = scene.project(T(-30.0 - phase * 6.0, sgn * 12.0, 18.0 + phase * 5.0))
            puffs.append((px, py, 5.6 - 2.2 * phase, int(215 * (1.0 - 0.5 * phase))))
    return (muzzle_2d, aim_2d, puffs)


# ====================================================================== ۳) تانک سنگین غول‌پیکر دولول بولدوزردار (tank_heavy)

def _hazard_stripes(p_l: np.ndarray) -> np.ndarray:
    s = np.floor((p_l[..., 1] + p_l[..., 2] * 0.85) / 6.2)
    even = (np.mod(s, 2.0) == 0.0)
    out = np.empty(p_l.shape[:-1] + (3,), dtype=np.float32)
    out[even] = (240.0, 184.0, 44.0)
    out[~even] = (32.0, 33.0, 40.0)
    return out


def build_mammoth_tank(scene: Scene3D, dir_idx: int, anim: str, frame: int) -> tuple:
    """تانک فوق‌سنگین دولول هم‌قواره و پهن (عرض ~۹۶ پیکسل) با تیغهٔ بولدوزر راه‌راه، لانچر موشک و ۲ لولهٔ توپ."""
    scene.reset()
    yaw = dir_idx * 45.0
    B = rot_z(yaw)
    S = 1.24

    bob = 0.0
    cleat_shift = 0.0
    recoil = 0.0
    if anim == "idle":
        bob = 0.5 * math.sin(frame * math.pi * 0.5)
    elif anim == "move":
        bob = 0.95 * math.sin(frame * math.tau / 6.0)
        cleat_shift = (frame / 6.0) * 9.5
    elif anim == "attack":
        recoil = [0.0, -0.9, 6.2, 4.4, 2.0, 0.0][frame]

    def T(lx: float, ly: float, lz: float) -> np.ndarray:
        return B @ np.array([lx * S, ly * S, lz * S + bob], dtype=np.float32)

    IRON_DARK = (40, 40, 48)
    TREAD_DARK = (50, 50, 60)
    CRIMSON_MAIN = (168, 44, 50)
    CRIMSON_LIGHT = (214, 78, 78)
    BRONZE_GOLD = (232, 178, 62)
    FIRE_CORE = (255, 164, 52)

    # ۱) شنی‌های پهن دوقلو + چرخک‌های طلایی + زره پوشانندهٔ جانبی
    for si, sgn in enumerate((-1.0, 1.0)):
        wy = sgn * 19.8
        scene.add_obb(T(-1.5, wy, 10.8), (27.5 * S, 6.2 * S, 8.8 * S), B, TREAD_DARK, part_id=10 + si)
        scene.add_cylinder(T(24.0, wy - 5.6, 10.8), T(24.0, wy + 5.6, 10.8), 8.8 * S, TREAD_DARK, part_id=12 + si)
        scene.add_cylinder(T(-26.5, wy - 5.6, 10.8), T(-26.5, wy + 5.6, 10.8), 8.8 * S, TREAD_DARK, part_id=14 + si)
        scene.add_obb(T(-1.5, wy + sgn * 1.3, 17.5), (28.5 * S, 6.0 * S, 4.0 * S), B, CRIMSON_MAIN, part_id=16 + si)
        for wi, cx in enumerate((-18.0, -6.0, 6.0, 18.0)):
            scene.add_cylinder(T(cx, wy - 6.4, 8.2), T(cx, wy + 6.4, 8.2), 3.6 * S, BRONZE_GOLD, part_id=20 + wi)
        for k in range(5):
            kx = -20.0 + k * 9.5 + (cleat_shift % 9.5) - 4.0
            scene.add_obb(T(kx, wy, 20.5), (1.7 * S, 5.5 * S, 1.1 * S), B, IRON_DARK, part_id=26)

    # ۲) بدنهٔ زرهی پهن و سنگین
    scene.add_obb(T(-2.0, 0.0, 18.2), (25.5 * S, 16.8 * S, 8.0 * S), B, CRIMSON_MAIN, part_id=2)
    scene.add_obb(T(-2.0, 0.0, 26.2), (22.0 * S, 15.2 * S, 2.8 * S), B, CRIMSON_LIGHT, part_id=3)

    # ۳) تیغهٔ بولدوزر پهن راه‌راه زرد-مشکی در جلوی بدنه
    blade_b = B @ rot_y(-18.0)
    scene.add_obb(T(30.2, 0.0, 10.8), (3.4 * S, 25.0 * S, 8.8 * S), blade_b, BRONZE_GOLD, part_id=30, stripe_fn=_hazard_stripes)
    for sgn in (-1.0, 1.0):
        scene.add_obb(T(25.0, sgn * 14.5, 11.5), (5.5 * S, 2.4 * S, 3.2 * S), B, IRON_DARK, part_id=31)

    # ۴) برجک قلعه‌ای پهن + غلاف موشکی ۶تایی روی سقف + دودکش‌های دوقلوی برنزی
    scene.add_obb(T(-2.5, 0.0, 33.2), (16.8 * S, 15.8 * S, 6.8 * S), B, CRIMSON_LIGHT, part_id=40)
    scene.add_obb(T(-5.0, 0.0, 40.8), (11.0 * S, 11.0 * S, 2.6 * S), B, BRONZE_GOLD, part_id=41)
    pod_b = B @ rot_y(-12.0)
    scene.add_obb(T(-6.5, 0.0, 45.2), (6.8 * S, 10.2 * S, 3.8 * S), pod_b, IRON_DARK, part_id=42)
    for my in (-6.0, 0.0, 6.0):
        scene.add_ellipsoid(T(0.2, my, 45.5), (1.8 * S, 2.0 * S, 2.0 * S), pod_b, FIRE_CORE, part_id=43, emissive=0.88)

    for si, sgn in enumerate((-1.0, 1.0)):
        scene.add_cylinder(T(-23.5, sgn * 11.0, 23.0), T(-23.5, sgn * 11.0, 41.0), 3.4 * S, IRON_DARK, part_id=44 + si)
        scene.add_cylinder(T(-23.5, sgn * 11.0, 40.2), T(-23.5, sgn * 11.0, 42.8), 4.4 * S, BRONZE_GOLD, part_id=46 + si)

    # ۵) دو لولهٔ توپ سنگین موازی با ترمز دهانهٔ برنزی
    bx0 = 11.0 - recoil
    bx1 = 39.5 - recoil
    scene.add_obb(T(14.0 - recoil * 0.4, 0.0, 33.0), (6.0 * S, 11.8 * S, 4.8 * S), B, IRON_DARK, part_id=50)
    for si, sgn in enumerate((-1.0, 1.0)):
        wy = sgn * 6.4
        scene.add_cylinder(T(bx0, wy, 33.0), T(bx1, wy, 33.0), 3.6 * S, IRON_DARK, part_id=51 + si, cap_color=FIRE_CORE)
        scene.add_cylinder(T(bx1 - 4.2, wy, 33.0), T(bx1 + 0.8, wy, 33.0), 4.9 * S, BRONZE_GOLD, part_id=53 + si, cap_color=FIRE_CORE)

    muzzle_2d = scene.project(T(bx1 + 2.0, 0.0, 33.0))
    aim_2d = scene.project(T(bx1 + 14.5, 0.0, 33.0))

    puffs = []
    if anim == "move":
        phase = frame / 6.0
        for sgn in (-1.0, 1.0):
            px, py = scene.project(T(-25.0 - phase * 6.0, sgn * 11.0, 44.0 + phase * 6.5))
            puffs.append((px, py, 6.0 - 2.3 * phase, int(220 * (1.0 - 0.5 * phase))))
    return (muzzle_2d, aim_2d, puffs)


# ====================================================================== ساخت تمام شیت‌ها و ذخیرهٔ PNG

def save_png(img: Image.Image, path: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, format="PNG", optimize=True)


def generate_iso_vehicle_pack(unit_id: str, builder_fn, core_rgb: tuple[int, int, int],
                              halo_rgb: tuple[int, int, int]) -> list[tuple[float, float]]:
    scene = Scene3D(out_size=128, ssaa=3, pivot_xy=(64.0, 100.0), elevation_deg=32.0)
    dst_dir = os.path.join(UNITS_DIR, f"iso_{unit_id}")

    sheets = {
        "idle": Image.new("RGBA", (4 * 128, 8 * 128), (0, 0, 0, 0)),
        "move": Image.new("RGBA", (6 * 128, 8 * 128), (0, 0, 0, 0)),
        "attack": Image.new("RGBA", (6 * 128, 8 * 128), (0, 0, 0, 0)),
        "atlas": Image.new("RGBA", (3 * 128, 8 * 128), (0, 0, 0, 0)),
    }
    laser_tips: list[tuple[float, float]] = []

    for d in range(8):
        for anim, n_frames in (("idle", 4), ("move", 6), ("attack", 6)):
            for f in range(n_frames):
                muzzle_2d, aim_2d, puffs = builder_fn(scene, d, anim, f)
                cell = scene.render_image(
                    shadow_radius=(43.0, 23.0),
                    shadow_alpha=122,
                    outline_px=2.15,
                    ink_rgb=(18.0, 19.0, 25.0),
                    crease_strength=0.90,
                )
                if puffs:
                    cell = draw_smoke_puffs(cell, puffs)
                if anim == "attack":
                    burst_strength = [0.24, 0.45, 1.0, 0.80, 0.36, 0.0][f]
                    cell = draw_glow_burst(cell, muzzle_2d, aim_2d, core_rgb, halo_rgb, burst_strength)
                    if f == 2:
                        laser_tips.append((round(aim_2d[0] - 64.0, 1), round(aim_2d[1] - 99.0, 1)))
                sheets[anim].paste(cell, (f * 128, d * 128))
                if f == 0:
                    col = {"idle": 0, "move": 1, "attack": 2}[anim]
                    sheets["atlas"].paste(cell, (col * 128, d * 128))

    save_png(sheets["atlas"], os.path.join(dst_dir, f"atlas_{unit_id}_iso.png"))
    save_png(sheets["idle"], os.path.join(dst_dir, f"{unit_id}_idle.png"))
    save_png(sheets["move"], os.path.join(dst_dir, f"{unit_id}_move.png"))
    save_png(sheets["attack"], os.path.join(dst_dir, f"{unit_id}_attack.png"))
    return laser_tips


def generate_commando_sheets() -> None:
    scene = Scene3D(out_size=64, ssaa=4, pivot_xy=(32.0, 54.5), elevation_deg=22.0)

    def render_one(dir_name: str, anim: str, frame: int) -> Image.Image:
        puffs = build_rpg_commando(scene, dir_name, anim, frame)
        img = scene.render_image(
            shadow_radius=(13.5, 5.2),
            shadow_alpha=90,
            outline_px=1.35,
            ink_rgb=(32.0, 20.0, 18.0),
            crease_strength=0.82,
        )
        if puffs:
            img = draw_smoke_puffs(img, puffs)
        return img

    save_png(render_one("front", "idle", 0), os.path.join(UNITS_DIR, "commando.png"))
    walk_strip = Image.new("RGBA", (8 * 64, 64), (0, 0, 0, 0))
    for f in range(8):
        walk_strip.paste(render_one("right", "walk", f), (f * 64, 0))
    save_png(walk_strip, os.path.join(UNITS_DIR, "commando_walk.png"))

    for d in ("front", "back", "left", "right"):
        run_strip = Image.new("RGBA", (8 * 64, 64), (0, 0, 0, 0))
        for f in range(8):
            run_strip.paste(render_one(d, "run", f), (f * 64, 0))
        save_png(run_strip, os.path.join(UNITS_DIR, "run", f"commando_{d}.png"))

        death_strip = Image.new("RGBA", (6 * 64, 64), (0, 0, 0, 0))
        for f in range(6):
            death_strip.paste(render_one(d, "death", f), (f * 64, 0))
        save_png(death_strip, os.path.join(UNITS_DIR, "death", f"commando_{d}.png"))


def main() -> None:
    generate_commando_sheets()
    armored_tips = generate_iso_vehicle_pack(
        "tank_armored", build_striker_tank,
        core_rgb=(150, 248, 255), halo_rgb=(36, 185, 255),
    )
    heavy_tips = generate_iso_vehicle_pack(
        "tank_heavy", build_mammoth_tank,
        core_rgb=(255, 230, 120), halo_rgb=(245, 92, 42),
    )
    print("armored LASER_TIP:", armored_tips)
    print("heavy   LASER_TIP:", heavy_tips)


if __name__ == "__main__":
    main()
