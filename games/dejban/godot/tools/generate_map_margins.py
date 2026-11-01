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
