"""Repair enlarged opening-page circles and audit all bundled verse rings.

The 6,224 circles on regular pages are already exact 51-pixel circles. On the
opening pages, old circles were enlarged with the Arabic artwork and picked up
interpolation fringes. Replace only those red pixels after layout, retaining
every printed numeral and every other source pixel.

    python3 tools/repair_verse_circles.py app/src/main/assets/mushaf
"""

import json
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw


RED = (239, 77, 77, 255)
OPENING_CIRCLES = {1: 7, 2: 5}


def tinted_red(pixels):
    rgb = pixels[:, :, :3].astype(np.int16)
    return ((rgb[:, :, 0] >= 60)
            & (rgb[:, :, 0] > rgb[:, :, 1] + 25)
            & (rgb[:, :, 0] > rgb[:, :, 2] + 25)
            & (pixels[:, :, 3] > 0))


def components(mask):
    _, _, stats, _ = cv2.connectedComponentsWithStats(mask.astype(np.uint8), 8)
    return [tuple(map(int, row)) for row in stats[1:]]


def circle_template(radius):
    size = radius * 2 + 1
    image = Image.new("L", (size, size))
    ImageDraw.Draw(image).ellipse((0, 0, size - 1, size - 1), fill=None,
                                  outline=255, width=2)
    return np.asarray(image) > 0


def opening_centers(pixels, page):
    red = tinted_red(pixels) & (pixels[:, :, 3] >= 10)
    red[:600] = False  # The floral title is not a verse marker.
    rings = [(x, y, w, h) for x, y, w, h, area in components(red)
             if 50 <= w <= 65 and 50 <= h <= 65 and 250 <= area <= 1100]
    if len(rings) != OPENING_CIRCLES[page]:
        raise ValueError(f"Page {page}: found {len(rings)} opening circles")
    return sorted((round(x + (w - 1) / 2), round(y + (h - 1) / 2))
                  for x, y, w, h in rings)


def repair_opening(path, page):
    original = np.asarray(Image.open(path).convert("RGBA"))
    centers = opening_centers(original, page)
    old_red = tinted_red(original)
    old_red[:600] = False
    ring = circle_template(28)
    new_red = np.zeros(old_red.shape, dtype=bool)
    for cx, cy in centers:
        y, x = cy - 28, cx - 28
        new_red[y:y+57, x:x+57] |= ring
    # Numeral strokes and neighboring Arabic must never be overwritten.
    if np.any(new_red & (original[:, :, 3] > 0) & ~old_red):
        raise ValueError(f"Page {page}: new circle would touch printed ink")
    result = original.copy()
    result[old_red] = 0
    result[new_red] = RED
    if not np.array_equal(result[~old_red & ~new_red],
                          original[~old_red & ~new_red]):
        raise ValueError(f"Page {page}: non-circle pixels changed")
    Image.fromarray(result, "RGBA").save(path, optimize=True)
    return len(centers), int(np.count_nonzero(old_red))


def audit_page(path, page, expected):
    pixels = np.asarray(Image.open(path).convert("RGBA"))
    solid = np.all(pixels == RED, axis=2)
    radius = 28 if page in OPENING_CIRCLES else 25
    template = circle_template(radius)
    diameter = radius * 2 + 1
    rings = []
    for x, y, w, h, area in components(solid):
        if w == diameter and h == diameter and area == int(template.sum()):
            if not np.array_equal(solid[y:y+h, x:x+w], template):
                raise ValueError(f"Page {page}: imperfect circle at {(x, y)}")
            rings.append((x, y, w, h))
    if len(rings) != expected:
        raise ValueError(f"Page {page}: {len(rings)}/{expected} exact circles")

    red = tinted_red(pixels)
    for x, y, w, h in rings:
        red[y:y+h, x:x+w] = False
    if page in OPENING_CIRCLES:
        red[:600] = False
    else:
        # The full-width red edges identify the existing surah title banners.
        rows = np.where(solid.sum(axis=1) > 900)[0]
        groups = []
        for row in rows:
            if not groups or row > groups[-1][-1] + 3:
                groups.append([int(row)])
            else:
                groups[-1].append(int(row))
        if len(groups) % 2:
            raise ValueError(f"Page {page}: unmatched title border")
        for top, bottom in zip(groups[::2], groups[1::2]):
            red[max(0, top[0]-3):min(red.shape[0], bottom[-1]+4)] = False
    if np.any(red):
        raise ValueError(f"Page {page}: {int(red.sum())} red fragments outside circles or titles")
    return len(rings)


def main(directory):
    mapping = json.loads((directory / "highres_ayah_bounds.json").read_text())
    for page in OPENING_CIRCLES:
        count, cleared = repair_opening(directory / f"{page}.png", page)
        print(f"Repaired page {page}: {count} circles, cleared {cleared} old red pixels")
    checked = 0
    for page in range(1, 605):
        expected = len({tuple(area[:2]) for area in mapping[str(page)]["areas"]})
        checked += audit_page(directory / f"{page}.png", page, expected)
    if checked != 6236:
        raise ValueError(f"Unexpected total: {checked} circles")
    print("PASS: 6,236 exact circles across 604 pages; no detached red fragments")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
