#!/usr/bin/env python3
"""Generate natural, road-free borders from the restored map artwork.

Offline tool: pip install numpy opencv-python-headless. The gameplay image is
not changed: a separate image is drawn behind it. Road bands are inpainted
before extending terrain. Side borders continue edge objects horizontally
instead of abruptly joining an unrelated portion of the opposite edge.
Eight extra pixels cover camera shake.
"""
from pathlib import Path

import cv2
import numpy as np

from map_scale_layouts import LAYOUTS

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / 'assets/sprites/maps'
OUT = ROOT / 'assets/sprites/map_margins'
MARGIN_X = 150  # four houses (142 px) + camera shake
MARGIN_Y = 280  # extra render-only padding for moving beneath HUD panels
MAP1 = [(3, 148), (931, 148), (931, 361), (349, 361), (349, 574), (994, 574), (994, 716)]


def cropped_edge_mask(image: np.ndarray, top: bool) -> np.ndarray:
    """Find strong foreground shapes interrupted by a horizontal edge.

    Used only for the two flat-colour meadow maps after visual inspection;
    woodland and snow do not have a uniform enough background for this method.
    """
    lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB).astype(np.float32)
    band = lab[:100] if top else lab[-100:]
    background = np.median(band.reshape(-1, 3), axis=0)
    foreground = (np.linalg.norm(lab - background, axis=2) > 22).astype(np.uint8) * 255
    foreground = cv2.morphologyEx(foreground, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    contours, _ = cv2.findContours(foreground, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    mask = np.zeros(image.shape[:2], np.uint8)
    for contour in contours:
        x, y, w, h = cv2.boundingRect(contour)
        if ((top and y <= 1) or (not top and y + h >= image.shape[0] - 1)) and h > 3 and w > 3:
            cv2.drawContours(mask, [contour], -1, 255, cv2.FILLED)
    return cv2.dilate(mask, np.ones((5, 5), np.uint8))


def meadow_extension(clean: np.ndarray) -> np.ndarray:
    """Continue just cropped edge shapes, not the whole upside-down scene."""
    top = cropped_edge_mask(clean, True)
    bottom = cropped_edge_mask(clean, False)
    # The wrap comes from the opposite edge. Remove the truncated objects there
    # first so a detached half-bush does not appear at this edge's seam.
    source = cv2.inpaint(clean, top | bottom, 5, cv2.INPAINT_TELEA)
    result = cv2.copyMakeBorder(source, MARGIN_Y, MARGIN_Y, MARGIN_X, MARGIN_X, cv2.BORDER_WRAP)
    reflected = cv2.copyMakeBorder(clean, MARGIN_Y, MARGIN_Y, MARGIN_X, MARGIN_X, cv2.BORDER_REFLECT_101)
    for area, mask in ((slice(0, MARGIN_Y), top), (slice(MARGIN_Y + 720, None), bottom)):
        mask = cv2.copyMakeBorder(mask, MARGIN_Y, MARGIN_Y, MARGIN_X, MARGIN_X, cv2.BORDER_REFLECT_101)
        alpha = cv2.GaussianBlur(mask, (5, 5), 1).astype(np.float32)[area, :, None] / 255.0
        result[area] = (reflected[area] * alpha + result[area] * (1.0 - alpha)).astype(np.uint8)
    return result


def clean_meadow_fragments(image: np.ndarray, index: int) -> None:
    """Remove known detached wrap fragments in the padding, never in the map."""
    boxes = [(75, 1092, 220, 1215), (1335, 1092, 1500, 1215)]
    if index == 3:
        boxes += [(0, 172, 295, 280), (454, 235, 585, 280), (1290, 172, 1579, 280)]
    mask = np.zeros(image.shape[:2], np.float32)
    for x0, y0, x1, y1 in boxes:
        cv2.rectangle(mask, (x0, y0), (x1, y1), 1.0, -1)
    mask = cv2.GaussianBlur(mask, (0, 0), 5)
    mask[MARGIN_Y:MARGIN_Y + 720, MARGIN_X:MARGIN_X + 1280] = 0
    # The sampled strip is uninterrupted meadow for these two maps.
    backdrop = np.repeat(image[:, 650:651, :], image.shape[1], axis=1)
    alpha = mask[:, :, None]
    image[:] = (image * (1.0 - alpha) + backdrop * alpha).astype(np.uint8)


def make_margin(index: int) -> None:
    src = SRC / f'map_{index:02}.jpg'
    image = cv2.imread(str(src), cv2.IMREAD_COLOR)
    if image is None or image.shape[:2] != (720, 1280):
        raise ValueError(f'{src}: expected 1280x720 image')
    layout = LAYOUTS.get(index)
    path = MAP1 if index == 1 else layout['path']
    width = 86 if index == 1 else int(max(b[1] for b in layout['x'] + layout['y']) + 18)
    road = np.zeros(image.shape[:2], dtype=np.uint8)
    # Some measured paths stop short of the painted arrow/road at the image
    # edge. Extend both ends along the tangent before removing the road from
    # the border source; otherwise arrows and asphalt leak into the margin.
    points = np.asarray(path, dtype=np.float64)
    first = points[1] - points[0]
    last = points[-1] - points[-2]
    points[0] -= first / np.linalg.norm(first) * 160.0
    points[-1] += last / np.linalg.norm(last) * 160.0
    pts = np.rint(points).astype(np.int32).reshape((-1, 1, 2))
    cv2.polylines(road, [pts], False, 255, width, lineType=cv2.LINE_8)
    clean = cv2.inpaint(image, road, 5, cv2.INPAINT_TELEA)
    if index in (2, 3):
        extended = meadow_extension(clean)
        clean_meadow_fragments(extended, index)
    else:
        extended = cv2.copyMakeBorder(clean, MARGIN_Y, MARGIN_Y, MARGIN_X, MARGIN_X, cv2.BORDER_WRAP)
    # Match the side edge to itself rather than cutting off an unrelated
    # object copied from the far side of the map. Only horizontal reflection
    # is used here, so trees are not flipped upside-down.
    side = cv2.copyMakeBorder(clean, 0, 0, MARGIN_X, MARGIN_X, cv2.BORDER_REFLECT_101)
    extended[MARGIN_Y:MARGIN_Y + 720, :MARGIN_X] = side[:, :MARGIN_X]
    extended[MARGIN_Y:MARGIN_Y + 720, MARGIN_X + 1280:] = side[:, MARGIN_X + 1280:]
    # The true artwork is drawn on top at runtime, but retaining it here makes
    # the generated image independently inspectable.
    extended[MARGIN_Y:MARGIN_Y + 720, MARGIN_X:MARGIN_X + 1280] = image
    OUT.mkdir(parents=True, exist_ok=True)
    if not cv2.imwrite(str(OUT / src.name), extended, [cv2.IMWRITE_JPEG_QUALITY, 94]):
        raise RuntimeError(f'could not write margin for {src}')


if __name__ == '__main__':
    # map_01 is an individually retouched border; do not overwrite it with
    # the automatic approximation. The original gameplay image stays separate.
    for i in range(2, 13):
        make_margin(i)
        print(f'generated map_{i:02} margin')
