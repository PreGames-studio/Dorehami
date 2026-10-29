#!/usr/bin/env python3
"""Check the five Tesla pictures, transparency, anchors and exact layering.

Run with Pillow from the repository root. Combat and rotation are covered
separately by TeslaTest.gd in the Godot headless suite.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1] / 'assets/sprites/towers_tesla'
SOURCE = Path(__file__).resolve().parents[1] / 'tools/tesla_source/reference_cutout.png'


def digest(image: Image.Image) -> str:
    return hashlib.sha256(image.tobytes()).hexdigest()


def main() -> None:
    manifest = json.loads((ROOT / 'manifest.json').read_text(encoding='utf-8'))
    assert manifest['canvas'] == [384, 384]
    assert manifest['ground_anchor'] == [192, 345]
    assert manifest['image_scale'] == 0.46
    assert manifest['levels'] == 5
    assert manifest['directions'] == 48 and manifest['direction_cell'] == [192, 192]
    assert manifest['direction_grid'] == [8, 6] and len(manifest['head_seams']) == 5
    assert len(manifest['core_centers']) == len(manifest['core_radii']) == 5
    assert all(a[1] > b[1] for a, b in zip(manifest['core_centers'], manifest['core_centers'][1:]))
    assert all(a < b for a, b in zip(manifest['core_radii'], manifest['core_radii'][1:]))
    expected = {'base.png', 'icon.png'}
    for level in range(1, 6):
        expected.update({f'body_{level}.png', f'head_{level}.png',
                         f'core_{level}.png', f'tesla_{level}.png', f'head_{level}_directions.png'})
    assert {p.name for p in ROOT.glob('*.png')} == expected
    hashes = []
    for name in sorted(expected):
        with Image.open(ROOT / name) as image:
            assert image.mode == 'RGBA', name
            expected_size = ((256, 256) if name == 'icon.png' else
                             (1536, 1152) if name.endswith('_directions.png') else (384, 384))
            assert image.size == expected_size, name
            alpha = image.getchannel('A')
            assert alpha.getextrema() == (0, 255), name
            assert all(alpha.getpixel(pt) == 0 for pt in [(0, 0), (image.width-1, 0),
                        (0, image.height-1), (image.width-1, image.height-1)]), name
            if name.startswith('tesla_'):
                hashes.append(digest(image))
            if name.endswith('_directions.png'):
                frames = set()
                for direction in range(48):
                    x, y = direction % 8 * 192, direction // 8 * 192
                    frame = image.crop((x, y, x + 192, y + 192))
                    bounds = frame.getchannel('A').getbbox()
                    assert bounds and min(bounds[0], bounds[1], 192-bounds[2], 192-bounds[3]) > 1, (name, direction, bounds)
                    frames.add(digest(frame))
                assert len(frames) == 48, name
    assert len(hashes) == len(set(hashes)) == 5
    with Image.open(ROOT / 'base.png') as base:
        bounds = base.getchannel('A').getbbox()
        assert bounds and bounds[0] < 192 < bounds[2] and bounds[1] < 345 < bounds[3]
        for level in range(1, 6):
            with Image.open(ROOT / f'body_{level}.png') as body, \
                 Image.open(ROOT / f'head_{level}.png') as head, \
                 Image.open(ROOT / f'core_{level}.png') as core, \
                 Image.open(ROOT / f'tesla_{level}.png') as whole:
                composite = base.copy()
                composite.alpha_composite(body)
                composite.alpha_composite(head)
                assert composite.tobytes() == whole.tobytes(), level
                x, y = map(round, manifest['core_centers'][level-1])
                assert core.getchannel('A').getpixel((x, y)) > 0, level
                assert core.getchannel('A').getbbox() is not None, level
    with Image.open(SOURCE) as source:
        assert source.mode == 'RGBA' and source.getchannel('A').getpixel((0, 0)) == 0
    print(f'Tesla art: {len(expected)} RGBA PNGs, one shared base and five aligned levels and 48 safe, distinct head directions per level validated.')


if __name__ == '__main__':
    main()
