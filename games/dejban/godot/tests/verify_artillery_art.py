#!/usr/bin/env python3
"""Validate actual RGBA assets, aligned layers and 48 safe directional cells.

Run from the repository root; needs Pillow. Gameplay is tested separately by
ArtilleryTest.gd in the Godot headless suite.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / "assets/sprites/towers_artillery"


def digest(image: Image.Image) -> str:
    return hashlib.sha256(image.tobytes()).hexdigest()


def main() -> None:
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["ground_anchor"] == [128, 206]
    assert manifest["head_pivot"] == manifest["barrel_pivot"] == [128, 100]
    assert manifest["muzzle_from_pivot"] == [100, 0]
    assert manifest["directions"] == 48 and manifest["direction_cell"] == [128, 128]
    expected = {"shell.png"}
    for level in range(1, 6):
        expected.update({f"artillery_{level}.png", f"base_{level}.png", f"head_{level}.png",
                         f"barrel_{level}.png", f"head_{level}_directions.png",
                         f"barrel_{level}_directions.png"})
    assert {p.name for p in ROOT.glob("*.png")} == expected
    base_bounds = []
    complete_hashes = []
    for name in sorted(expected):
        with Image.open(ROOT / name) as image:
            assert image.mode == "RGBA", name
            alpha = image.getchannel("A")
            assert alpha.getextrema() == (0, 255), name
            assert alpha.getpixel((0, 0)) == 0 and alpha.getpixel((image.width-1, image.height-1)) == 0, name
            if name.endswith("_directions.png"):
                assert image.size == (1024, 768), name
                frames = []
                for y in range(0, image.height, 128):
                    for x in range(0, image.width, 128):
                        cell = alpha.crop((x, y, x + 128, y + 128))
                        bounds = cell.getbbox()
                        assert bounds is not None, (name, x, y)
                        # Transparent gutters prevent interpolation from sampling
                        # the neighbouring view when sprites are filtered.
                        assert bounds[0] > 1 and bounds[1] > 1 and bounds[2] < 127 and bounds[3] < 127, (name, x, y, bounds)
                        frames.append(digest(cell))
                assert len(frames) == len(set(frames)) == 48, name
            elif name == "shell.png":
                assert image.size == (40, 20), name
            else:
                assert image.size == (256, 256), name
                if name.startswith("base_"):
                    base_bounds.append(alpha.getbbox())
                if name.startswith("artillery_"):
                    complete_hashes.append(digest(image))
    # A consistently anchored pedestal, five distinct complete designs and
    # exact agreement between the standalone image and runtime component order.
    assert len(set(base_bounds)) == 1
    assert len(set(complete_hashes)) == 5
    for level in range(1, 6):
        with Image.open(ROOT / f"base_{level}.png") as b, \
             Image.open(ROOT / f"barrel_{level}.png") as barrel, \
             Image.open(ROOT / f"head_{level}.png") as head, \
             Image.open(ROOT / f"artillery_{level}.png") as full:
            composite = b.copy()
            composite.alpha_composite(barrel)
            composite.alpha_composite(head)
            assert composite.tobytes() == full.tobytes(), level
    print(f"Artillery art: {len(expected)} transparent PNGs, 5 aligned towers, and 48 safe distinct directions per layer validated.")


if __name__ == "__main__":
    main()
