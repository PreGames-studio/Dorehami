#!/usr/bin/env python3
"""Generate natural, road-free borders from the restored map artwork.

Offline tool: pip install 'numpy<2' opencv-contrib-python-headless==4.10.0.84.
The gameplay image is not changed: a separate image is drawn behind it.
Road bands are inpainted
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


def retouch_known_streaks(image: np.ndarray, index: int) -> None:
    """Replace selected inpaint streaks with nearby terrain, outside the map.

    The coordinates and source patches were chosen per image, not inferred
    from the road. Keeping the gameplay rectangle masked out is important:
    the separately drawn original map must still align with the border.
    """
    patches = {
        4: [
            ((1090, 200, 140, 80), (650, 150)),
            ((0, 280, 150, 80), (650, 150)),
            ((440, 1000, 80, 75), (650, 150)),
            ((1430, 1000, 150, 90), (650, 150)),
        ],
        7: [
            ((194, 148, 110, 120), (190, 275)),
            ((1470, 148, 110, 120), (190, 275)),
            ((0, 865, 110, 120), (190, 275)),
            ((0, 390, 150, 90), (500, 290)),
            ((160, 1104, 140, 95), (500, 290)),
            ((1430, 1104, 150, 95), (500, 290)),
        ],
        9: [
            # Dark inpaint slivers left by the road's corners, on snow only.
            ((395, 150, 80, 80), (650, 600)),
            ((1100, 150, 80, 80), (750, 600)),
            ((1100, 1040, 80, 80), (750, 600)),
        ],
    }
    for (x, y, width, height), (source_x, source_y) in patches.get(index, []):
        source = image[source_y:source_y + height, source_x:source_x + width].copy()
        alpha = np.ones((height, width), np.float32)
        for edge in range(20):
            amount = (edge + 1) / 21.0
            alpha[edge, :] *= amount
            alpha[-1 - edge, :] *= amount
            alpha[:, edge] *= amount
            alpha[:, -1 - edge] *= amount
        yy, xx = np.mgrid[y:y + height, x:x + width]
        alpha[(xx >= MARGIN_X) & (xx < MARGIN_X + 1280) &
              (yy >= MARGIN_Y) & (yy < MARGIN_Y + 720)] = 0
        alpha = alpha[:, :, None]
        destination = image[y:y + height, x:x + width]
        destination[:] = (source * alpha + destination * (1 - alpha)).astype(np.uint8)


def clean_snow_six_streaks(image: np.ndarray) -> None:
    """Replace conspicuous road-inpainting streaks in map 6's padding.

    The sampled snow is from a road-free part of the same map.  These boxes
    are confined to padding; the original map is never changed.
    """
    sample = image[480:620, 950:1040].copy()
    # Remove the occasional tiny rock from the otherwise empty snow sample so
    # tiling it cannot create regularly spaced dark specks.
    dark = (cv2.cvtColor(sample, cv2.COLOR_BGR2GRAY) < 215).astype(np.uint8) * 255
    dark = cv2.dilate(dark, np.ones((5, 5), np.uint8))
    sample = cv2.inpaint(sample, dark, 4, cv2.INPAINT_TELEA)
    background = np.tile(sample, (10, 20, 1))[:image.shape[0], :image.shape[1]]
    mask = np.zeros(image.shape[:2], np.uint8)
    for (x0, y0), (x1, y1) in (
            ((0, 285), (39, 826)), ((248, 0), (316, 100)),
            ((260, 48), (830, 110)), ((790, 0), (840, 105)),
            ((1131, 0), (1195, 275)), ((252, 1003), (317, 1279)),
            ((774, 1085), (1196, 1143)), ((774, 1100), (835, 1279)),
            ((1130, 1085), (1190, 1279)), ((1540, 1000), (1579, 1279))):
        cv2.rectangle(mask, (x0, y0), (x1, y1), 255, -1)
    mask = cv2.GaussianBlur(mask, (0, 0), 12)
    mask[MARGIN_Y:MARGIN_Y + 720, MARGIN_X:MARGIN_X + 1280] = 0
    alpha = mask.astype(np.float32)[:, :, None] / 255.0
    image[:] = (image * (1 - alpha) + background * alpha).astype(np.uint8)


def desert_ten_borders(extended: np.ndarray, image: np.ndarray) -> None:
    """Rebuild the flat top/bottom sand on map 10 without inpaint streaks.

    This map's top and bottom edge pixels are sand, not cut-off objects. A
    softly continued edge color, sparse grains, and whole cacti extracted from
    the original keep the padding road-free. Never edit the gameplay image.
    """
    cactus = image[660:720, 560:620].copy()
    hsv = cv2.cvtColor(cactus, cv2.COLOR_BGR2HSV)
    green = ((hsv[:, :, 0] >= 28) & (hsv[:, :, 0] < 90) &
             (hsv[:, :, 1] > 50) & (hsv[:, :, 2] < 220)).astype(np.uint8) * 255
    count, labels, stats, _ = cv2.connectedComponentsWithStats(green)
    if count <= 1:
        raise ValueError('could not extract the map 10 cactus')
    largest = max(range(1, count), key=lambda label: stats[label, 4])
    mask = (labels == largest).astype(np.uint8) * 255
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((3, 3), np.uint8))
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    mask[:] = 0
    cv2.drawContours(mask, contours, -1, 255, -1)
    mask = cv2.dilate(mask, np.ones((5, 5), np.uint8))
    mask = cv2.GaussianBlur(mask, (3, 3), 0.5)
    sprite = np.dstack((cactus, mask))
    rng = np.random.default_rng(10)
    for top, start in ((True, 0), (False, MARGIN_Y + 720)):
        edge = image[:1] if top else image[-1:]
        edge = cv2.GaussianBlur(edge, (31, 1), 0).astype(float)
        edge = cv2.copyMakeBorder(edge, 0, 0, MARGIN_X, MARGIN_X, cv2.BORDER_REFLECT_101)
        background = np.repeat(edge, MARGIN_Y, axis=0)
        mean = np.median(edge.reshape(-1, 3), axis=0)
        distance = np.arange(MARGIN_Y - 1, -1, -1) if top else np.arange(MARGIN_Y)
        strength = np.clip(distance / 65.0, 0, 1)[:, None, None]
        background = background * (1 - strength) + mean * strength
        if not top:
            # Continue dune shapes rather than stretching the final row.
            strip = image[-65:].copy()
            hsv_strip = cv2.cvtColor(strip, cv2.COLOR_BGR2HSV)
            objects = ((hsv_strip[:, :, 0] > 32) | (hsv_strip[:, :, 2] < 165)).astype(np.uint8) * 255
            objects = cv2.dilate(objects, np.ones((7, 7), np.uint8))
            strip = cv2.inpaint(strip, objects, 5, cv2.INPAINT_TELEA)
            strip = cv2.copyMakeBorder(strip, 0, 0, MARGIN_X, MARGIN_X, cv2.BORDER_REFLECT_101)
            strip = cv2.flip(strip, 0).astype(float)
            blend = (1 - np.arange(65) / 65.0)[:, None, None]
            background[:65] = strip * blend + background[:65] * (1 - blend)
        for _ in range(210):
            x, y = int(rng.integers(0, 1580)), int(rng.integers(0, MARGIN_Y))
            distance_from_edge = MARGIN_Y - y if top else y
            strength = min(1.0, distance_from_edge / 55.0)
            if strength < 0.1:
                continue
            color = tuple(float(v) for v in background[y, x] - np.array([13, 20, 27]) * strength)
            cv2.ellipse(background, (x, y), (int(rng.integers(1, 4)), 1),
                        int(rng.integers(-15, 15)), 0, 360, color, -1)
        background = np.clip(background, 0, 255).astype(np.uint8)
        positions = ([(210, 45, 1), (480, 145, .75), (850, 40, .85), (1190, 145, 1), (1450, 55, .75)]
                     if top else [(200, 110, .85), (520, 50, .75), (820, 150, 1), (1130, 60, .75), (1440, 150, .85)])
        for x, y, scale in positions:
            size = int(60 * scale)
            prop = cv2.resize(sprite, (size, size), interpolation=cv2.INTER_AREA)
            alpha = prop[:, :, 3:4].astype(float) / 255.0
            target = background[y:y + size, x:x + size]
            target[:] = (prop[:, :, :3] * alpha + target * (1 - alpha)).astype(np.uint8)
        extended[start:start + MARGIN_Y] = background


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
    if index in (8, 12):
        # Patch-based reconstruction avoids the long coloured streaks that
        # Telea creates when a broad road mask overlaps snow or desert props.
        # xphoto uses the opposite mask convention (255 = known pixels).
        if not hasattr(cv2, 'xphoto'):
            raise RuntimeError('Install opencv-contrib-python-headless for xphoto')
        clean = np.empty_like(image)
        cv2.xphoto.inpaint(image, 255 - road, clean, cv2.xphoto.INPAINT_SHIFTMAP)
    else:
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
    if index == 6:
        clean_snow_six_streaks(extended)
    if index == 10:
        desert_ten_borders(extended, image)
    retouch_known_streaks(extended, index)
    OUT.mkdir(parents=True, exist_ok=True)
    if not cv2.imwrite(str(OUT / src.name), extended, [cv2.IMWRITE_JPEG_QUALITY, 94]):
        raise RuntimeError(f'could not write margin for {src}')


if __name__ == '__main__':
    # map_01 is an individually retouched border; do not overwrite it with
    # the automatic approximation. The original gameplay image stays separate.
    for i in range(2, 13):
        make_margin(i)
        print(f'generated map_{i:02} margin')
