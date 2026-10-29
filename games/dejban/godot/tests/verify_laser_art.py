#!/usr/bin/env python3
"""Validate laser transparency, footprint, source alignment and 48 views."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from PIL import Image, ImageChops

ROOT = Path(__file__).resolve().parents[1] / "assets/sprites/towers_laser"


def main() -> None:
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["canvas"] == [384, 384]
    assert manifest["ground_anchor"] == [192, 345]
    assert manifest["direction_grid"] == [8, 6] and manifest["directions"] == 48
    names = ([f"laser_{i}.png" for i in range(1, 6)]
             + [f"base_{i}.png" for i in range(1, 6)]
             + [f"head_{i}.png" for i in range(1, 6)]
             + [f"head_{i}_directions.png" for i in range(1, 6)]
             + ["icon.png", "beam_strip.png", "muzzle_glow.png", "muzzle_flash.png"])
    assert set(p.name for p in ROOT.glob("*.png")) == set(names)
    for name in names:
        im = Image.open(ROOT / name)
        assert im.mode == "RGBA", name
        assert im.getpixel((0, 0))[3] == 0 and im.getpixel((im.width-1, im.height-1))[3] == 0, name
        low, high = im.getchannel("A").getextrema()
        assert low == 0 and high >= (100 if name in ["beam_strip.png", "muzzle_glow.png", "muzzle_flash.png"] else 255), name

    heights = []
    foot_boxes = []
    for level in range(1, 6):
        base = Image.open(ROOT / f"base_{level}.png").convert("RGBA")
        head = Image.open(ROOT / f"head_{level}.png").convert("RGBA")
        whole = Image.open(ROOT / f"laser_{level}.png").convert("RGBA")
        assert base.size == head.size == whole.size == (384, 384)
        assembled = base.copy()
        assembled.alpha_composite(head)
        assert ImageChops.difference(assembled, whole).getbbox() is None, f"layers {level}"
        foot = base.getchannel("A").getbbox()
        assert foot is not None and foot[3] == 345 and 133 <= foot[2] - foot[0] <= 136, foot
        foot_boxes.append(foot)
        box = whole.getchannel("A").getbbox()
        assert box is not None and box[0] >= 110 and box[2] <= 275 and box[1] >= 20
        heights.append(box[3] - box[1])
        # The white lightning on the blue banner is art, not transparent holes.
        banner_white = sum(1 for y in range(230, 345) for x in range(180, 210)
                           if (lambda c: c[3] > 230 and min(c[:3]) > 225)(whole.getpixel((x, y))))
        assert banner_white > 3, f"missing lightning insignia {level}"
        atlas = Image.open(ROOT / f"head_{level}_directions.png").convert("RGBA")
        assert atlas.size == (1536, 1152)
        digests = set()
        for direction in range(48):
            x = direction % 8 * 192
            y = direction // 8 * 192
            frame = atlas.crop((x, y, x + 192, y + 192))
            bbox = frame.getchannel("A").getbbox()
            assert bbox is not None and bbox[0] >= 2 and bbox[1] >= 2 and bbox[2] <= 190 and bbox[3] <= 190, (level, direction, bbox)
            digests.add(hashlib.sha256(frame.tobytes()).hexdigest())
        assert len(digests) == 48, f"duplicate direction level {level}"
    assert all(a < b for a, b in zip(heights, heights[1:])), heights
    assert max(box[0] for box in foot_boxes) - min(box[0] for box in foot_boxes) <= 2
    assert max(box[2] for box in foot_boxes) - min(box[2] for box in foot_boxes) <= 2
    print("Laser art: 24 transparent RGBA PNGs; five aligned feet and 48 unique safe head directions per level validated.")


if __name__ == "__main__":
    main()
