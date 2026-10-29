#!/usr/bin/env python3
"""Validate the transparency, safe atlas margins and Gatling animation frames.

Run from the repository root after generating the tower art. Requires Pillow.
This is a complementary asset test; the Godot headless suite tests gameplay.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / "assets/sprites/towers_machinegun"


def digest(im: Image.Image) -> str:
    return hashlib.sha256(im.tobytes()).hexdigest()


def main() -> None:
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["ground_anchor"] == [128, 206]
    assert manifest["base_pivot"] == manifest["head_layer_pivot"] == manifest["barrel_layer_pivot"] == [128, 100]
    assert manifest["muzzle_from_pivot"] == [100, 0]
    assert manifest["directions"] == 48 and manifest["gatling_frames"] == 8
    expected = {"base.png", "barrels_5_spin.png"}
    for level in range(1, 6):
        expected.update({f"machinegun_{level}.png", f"head_{level}.png",
                         f"head_{level}_directions.png", f"barrels_{level}_directions.png"})
        if level < 5:
            expected.add(f"barrels_{level}.png")
    assert {p.name for p in ROOT.glob("*.png")} == expected
    for name in sorted(expected):
        with Image.open(ROOT / name) as image:
            assert image.mode == "RGBA", name
            alpha = image.getchannel("A")
            assert alpha.getextrema() == (0, 255), name
            if name.startswith("machinegun_"):
                assert image.size == (256, 256), name
                assert alpha.getpixel((0, 0)) == alpha.getpixel((255, 255)) == 0, name
            if name.endswith("_directions.png"):
                is_gatling = name == "barrels_5_directions.png"
                assert image.size == ((2048, 3072) if is_gatling else (1024, 768)), name
                frames = []
                for y in range(0, image.height, 128):
                    for x in range(0, image.width, 128):
                        cell = alpha.crop((x, y, x + 128, y + 128))
                        bounds = cell.getbbox()
                        assert bounds is not None, (name, x, y)
                        # A transparent gutter keeps bilinear filtering from bleeding
                        # adjacent frames into the active view.
                        assert bounds[0] > 1 and bounds[1] > 1 and bounds[2] < 127 and bounds[3] < 127, (name, x, y, bounds)
                        frames.append(digest(cell))
                assert len(frames) == (384 if is_gatling else 48), name
                assert len(set(frames)) == len(frames), name
            if name == "barrels_5_spin.png":
                assert image.size == (2048, 256)
                frames = [digest(image.crop((x, 0, x + 256, 256))) for x in range(0, 2048, 256)]
                assert len(set(frames)) == 8
    print(f"Machine gun art: {len(expected)} transparent PNGs validated; 48 directions and 8 Gatling frames.")


if __name__ == "__main__":
    main()
