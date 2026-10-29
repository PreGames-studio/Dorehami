#!/usr/bin/env python3
"""Rebuild the scaled map art from the original hand-drawn JPEGs.

Each road is resampled as a separate layer, so its arrows, style and centre
markings remain from the original while the surrounding scenery is not warped.
The related, measured route is printed in pixel coordinates; Maps.gd stores
those points as x/64 and y/72. Map 1 was already rebuilt to this scale.

    python3 games/dejban/godot/tools/scale_map_art.py
    python3 games/dejban/godot/tools/scale_map_art.py --routes

Requires numpy and opencv-python-headless (offline art tools only). The ignored
source directory contains reference art, not another set of game resources.
"""
from __future__ import annotations

import argparse
from pathlib import Path

import cv2
import numpy as np

from map_scale_layouts import LAYOUTS

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "tools" / "map_scale_source"
DESTINATION = ROOT / "assets" / "sprites" / "maps"
ROAD_WIDTH = 71.0


def axis_map(bands, extent):
    """Monotonic, piecewise-affine source/output mapping for the road bands.

    Overlapping bands belong to short elbows in different parts of the image;
    they share one affine transform rather than stretching one another. Outer
    map boundaries are fixed unless the artwork itself is clipped there.
    """
    groups = []
    for center, width, target in sorted(bands):
        start, end = center - width / 2, center + width / 2
        scale = ROAD_WIDTH / width
        offset = target - scale * center
        if groups and start < groups[-1]["end"]:
            group = groups[-1]
            if abs(group["scale"] - scale) > 0.001 or abs(group["offset"] - offset) > 0.02:
                raise ValueError("Overlapping road bands need the same affine transform")
            group["end"] = max(group["end"], end)
        else:
            groups.append({"start": start, "end": end, "scale": scale, "offset": offset})

    source, target = [], []
    for group in groups:
        for coordinate in (group["start"], group["end"]):
            source.append(coordinate)
            target.append(coordinate * group["scale"] + group["offset"])
    if source[0] > 0:
        source.insert(0, 0)
        target.insert(0, 0)
    if source[-1] < extent:
        source.append(extent)
        target.append(extent)
    if not all(a < b for a, b in zip(source, source[1:])):
        raise ValueError("Source bands must be ordered")
    if not all(a <= b + 1e-6 for a, b in zip(target, target[1:])):
        raise ValueError("Output bands must not cross")
    return np.asarray(source), np.asarray(target)


def transformed_path(layout):
    old_x, new_x = axis_map(layout["x"], 1279)
    old_y, new_y = axis_map(layout["y"], 719)
    return [(float(np.interp(x, old_x, new_x)), float(np.interp(y, old_y, new_y)))
            for x, y in layout["path"]]


def filled_road(image):
    """Mask the connected asphalt, filling white dashes and coloured arrows."""
    rgb = image[:, :, ::-1].astype(np.int16)
    high, low = rgb.max(axis=2), rgb.min(axis=2)
    road = ((high < 155) & ((high - low) < 45)).astype(np.uint8)
    road = cv2.morphologyEx(road, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    count, labels, stats, _ = cv2.connectedComponentsWithStats(road, 8)
    if count < 2:
        raise ValueError("Could not find the road in the original artwork")
    component = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
    road = (labels == component).astype(np.uint8) * 255

    # Fill enclosed holes, with an extra border so a road touching an image
    # edge does not accidentally seal off an otherwise open landscape.
    padded = cv2.copyMakeBorder(road, 1, 1, 1, 1, cv2.BORDER_CONSTANT, value=0)
    flood = padded.copy()
    cv2.floodFill(flood, None, (0, 0), 255)
    road |= cv2.bitwise_not(flood)[1:-1, 1:-1]
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7))
    return cv2.morphologyEx(road, cv2.MORPH_CLOSE, kernel)


def restore_ground(image, road, number):
    """Repair the old road footprint without smearing nearby flowers/cacti."""
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (25, 25))
    erase = cv2.dilate(road, kernel)
    if number not in (2, 4, 10, 11):
        return cv2.inpaint(image, erase, 5, cv2.INPAINT_TELEA)

    # These four maps have flat green/sand backgrounds. Ignore foreground art
    # while estimating the local terrain colour: regular inpainting propagates
    # sharp plant outlines across an entire former road as coloured streaks.
    hue, saturation, value = cv2.split(cv2.cvtColor(image, cv2.COLOR_BGR2HSV))
    if number in (2, 4):
        terrain = (hue > 32) & (hue < 86) & (saturation > 55) & (value > 145)
    else:
        terrain = (hue > 10) & (hue < 35) & (saturation > 45) & (value > 180)
    safe = (terrain & (erase == 0)).astype(np.float32)
    weight = cv2.GaussianBlur(safe, (0, 0), 45)
    smooth = np.empty_like(image, dtype=np.float32)
    for channel in range(3):
        weighted = cv2.GaussianBlur(image[:, :, channel].astype(np.float32) * safe, (0, 0), 45)
        smooth[:, :, channel] = weighted / np.maximum(weight, 1e-5)
    blend = cv2.GaussianBlur(erase, (5, 5), 1).astype(np.float32) / 255.0
    ground = np.round(image * (1 - blend[:, :, None]) + smooth * blend[:, :, None])
    ground = ground.clip(0, 255).astype(np.uint8)

    if number == 2:
        # Preserve the colourful flowers/bush that meet the original road's
        # outline. They are not part of the road, even inside the repair rim.
        colour = ((saturation > 65) & (value > 100) & ((hue < 30) | (hue > 86))).astype(np.uint8)
        colour = cv2.dilate(colour, np.ones((7, 7), np.uint8)) != 0
        keep = colour & (road == 0)
        ground[keep] = image[keep]
    return ground


def rebuild(number):
    layout = LAYOUTS[number]
    source = SOURCE / ("map_%02d.jpg" % number)
    image = cv2.imread(str(source), cv2.IMREAD_COLOR)
    if image is None or image.shape[:2] != (720, 1280):
        raise ValueError("Missing or invalid 1280×720 map source: %s" % source)
    old_x, new_x = axis_map(layout["x"], 1279)
    old_y, new_y = axis_map(layout["y"], 719)
    road = filled_road(image)
    ground = restore_ground(image, road, number)

    road_mask = cv2.dilate(road, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5)))
    sample_x = np.interp(np.arange(1280), new_x, old_x).astype(np.float32)
    sample_y = np.interp(np.arange(720), new_y, old_y).astype(np.float32)
    grid_x = np.broadcast_to(sample_x, (720, 1280)).copy()
    grid_y = np.broadcast_to(sample_y[:, None], (720, 1280)).copy()
    warped = cv2.remap(image, grid_x, grid_y, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
    alpha = cv2.remap(road_mask, grid_x, grid_y, cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)
    if number == 4:
        # The original top road was clipped by the image. Its scaled centre is
        # 45.5, so restore the 10-pixel grass strip and the missing upper edge.
        alpha[:10, :] = 0
    alpha = cv2.GaussianBlur(alpha, (3, 3), 0.65).astype(np.float32) / 255.0
    output = np.round(warped * alpha[:, :, None] + ground * (1 - alpha[:, :, None]))
    output = output.clip(0, 255).astype(np.uint8)
    if number == 4:
        cv2.line(output, (0, 10), (1100, 10), (4, 3, 50), 2, cv2.LINE_AA)
    destination = DESTINATION / ("map_%02d.jpg" % number)
    if not cv2.imwrite(str(destination), output, [cv2.IMWRITE_JPEG_QUALITY, 96]):
        raise OSError("Could not write %s" % destination)
    print("map_%02d:" % number, transformed_path(layout))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--routes", action="store_true", help="print route pixels without changing artwork")
    parser.add_argument("--map", type=int, choices=sorted(LAYOUTS), help="regenerate just one image")
    args = parser.parse_args()
    numbers = [args.map] if args.map is not None else sorted(LAYOUTS)
    for number in numbers:
        if args.routes:
            print("map_%02d:" % number, transformed_path(LAYOUTS[number]))
        else:
            rebuild(number)


if __name__ == "__main__":
    main()
