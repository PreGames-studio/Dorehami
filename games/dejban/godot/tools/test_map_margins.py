#!/usr/bin/env python3
"""Offline image sanity checks (pip install 'numpy<2' opencv-contrib-python-headless==4.10.0.84).

These checks do not replace visual review: in particular they cannot detect
seams, cropped scenery, or every possible piece of road.
"""
import unittest

import cv2
import numpy as np

from generate_map_margins import MARGIN_X, MARGIN_Y, OUT, SRC, ROOT
from map_scale_layouts import LAYOUTS


class MapMarginTest(unittest.TestCase):
    def test_dimensions_and_original_center(self):
        for index in range(1, 13):
            with self.subTest(map=index):
                name = f'map_{index:02}.jpg'
                original = cv2.imread(str(SRC / name))
                margin = cv2.imread(str(OUT / name))
                self.assertIsNotNone(original, name)
                self.assertIsNotNone(margin, name)
                self.assertEqual(original.shape, (720, 1280, 3))
                self.assertEqual(margin.shape, (1280, 1580, 3))
                center = margin[MARGIN_Y:MARGIN_Y + 720, MARGIN_X:MARGIN_X + 1280]
                difference = np.abs(original.astype(np.int16) - center.astype(np.int16))
                # JPEG was encoded a second time. The actual game draws the
                # original over this center, so small compression differences
                # are harmless; a wrong image or offset is not.
                self.assertLess(float(difference.mean()), 3.0, name)

    def test_no_large_dark_gray_component_in_margins(self):
        # A limited regression check for large asphalt leaks, NOT proof that
        # every road pixel was removed. Ignore the center and JPEG bleed.
        for index in range(1, 13):
            with self.subTest(map=index):
                image = cv2.imread(str(OUT / f'map_{index:02}.jpg')).astype(np.int16)
                spread = image.max(axis=2) - image.min(axis=2)
                mask = ((spread < 24) & (image.max(axis=2) < 150)).astype(np.uint8)
                mask[275:1005, 145:1435] = 0
                _, _, stats, _ = cv2.connectedComponentsWithStats(mask, 8)
                large = [tuple(row) for row in stats[1:]
                         if row[4] > 1000 and (row[2] > 110 or row[3] > 110)]
                self.assertFalse(large, f'map {index}: {large}')

    def test_map_six_inpaint_stripes_stay_reduced(self):
        # These rectangles used to contain conspicuous saturated blue bands.
        # This does not verify cliffs or tree seams elsewhere in the image.
        image = cv2.imread(str(OUT / 'map_06.jpg'))
        self.assertIsNotNone(image)
        hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
        for x0, y0, x1, y1 in ((0, 320, 28, 800), (265, 1050, 305, 1260),
                               (780, 1100, 830, 1260)):
            with self.subTest(box=(x0, y0, x1, y1)):
                self.assertLess(float(hsv[y0:y1, x0:x1, 1].mean()), 30.0)

    def test_repairs_stay_clear_of_road(self):
        patches = (
            (5, 'map_05_top_left', 0, 100, 600, 330),
            (5, 'map_05_top_right', 1240, 110, 340, 340),
            (5, 'map_05_bottom_left', 0, 1000, 800, 280),
            (8, 'map_08_tree_cluster', 470, 100, 330, 330),
            (8, 'map_08_tree_cluster', 470, 820, 330, 330),
        )
        for index, name, x, y, width, height in patches:
            with self.subTest(patch=name, y=y):
                road = np.zeros((1280, 1580), np.uint8)
                layout = LAYOUTS[index]
                points = np.asarray(layout['path'], dtype=np.float64)
                first, last = points[1] - points[0], points[-1] - points[-2]
                points[0] -= first / np.linalg.norm(first) * 100
                points[-1] += last / np.linalg.norm(last) * 100
                points += (MARGIN_X, MARGIN_Y)
                road_width = int(max(b[1] for b in layout['x'] + layout['y']) + 60)
                cv2.polylines(road, [np.rint(points).astype(np.int32).reshape(-1, 1, 2)],
                              False, 255, road_width)
                if name == 'map_05_bottom_left':
                    # This patch lies entirely outside the original image;
                    # the artificially extended safety band is not a road.
                    road[MARGIN_Y + 720:] = 0
                image = cv2.imread(str(ROOT / 'assets/sprites/map_margin_repairs' / (name + '.png')),
                                   cv2.IMREAD_UNCHANGED)
                self.assertIsNotNone(image)
                self.assertEqual(image.shape, (height, width, 4))
                self.assertGreater(int(image[:, :, 3].max()), 0)
                self.assertFalse(np.any(image[:, :, 3][road[y:y + height, x:x + width] != 0]),
                                 'repair must not cover the route or its entry/exit')


if __name__ == '__main__':
    unittest.main()
