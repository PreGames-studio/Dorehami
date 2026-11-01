#!/usr/bin/env python3
"""Offline image sanity checks (pip install 'numpy<2' opencv-contrib-python-headless==4.10.0.84).

These checks do not replace visual review: in particular they cannot detect
seams, cropped scenery, or every possible piece of road.
"""
import unittest

import cv2
import numpy as np

from generate_map_margins import MARGIN_X, MARGIN_Y, OUT, SRC


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


if __name__ == '__main__':
    unittest.main()
