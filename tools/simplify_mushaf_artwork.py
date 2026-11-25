"""Simplify the printed title banners and verse-marker ornaments.

Read *original* numbered PNGs from a separate source directory, and write
numbered PNGs to an output directory. The Arabic text and numerals are
extracted from the original pixels; no Arabic is typed or generated here.

    python3 tools/simplify_mushaf_artwork.py SOURCE_DIR OUTPUT_DIR

Requires Pillow, NumPy, and OpenCV. Run on an unmodified source copy; the
original art is preserved in Git history (base commit aca71eb).
"""

import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw

from style_mushaf_frames import opening_page, save_exact_palette, title_bands


RED = (196, 107, 107, 255)  # #C46B6B; the app maps this to #B87575 at night.
OPENING_MARKERS = {
    1: [(154, 216), (124, 250), (97, 284), (240, 287),
        (146, 324), (218, 361), (170, 432)],
    2: [(293, 249), (295, 287), (224, 324), (97, 359), (169, 431)],
}


def marker_centers(original, page):
    if page in OPENING_MARKERS:
        return [(x, y, 10) for x, y in OPENING_MARKERS[page]]

    rgb = original[:, :, :3].astype(np.int16)
    red, green, blue = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
    # The marker's light interior is a compact 23x15 island. Some printed
    # markers use warm cream, others neutral gray; detect each independently.
    masks = [
        (red >= 230) & (green >= 205) & (blue >= 180)
        & (red - green >= 6) & (red - blue >= 15),
        (red >= 215) & (green >= 215) & (blue >= 215),
    ]
    centers = []
    decorations = title_bands(original)
    for mask in masks:
        count, _, stats, centroids = cv2.connectedComponentsWithStats(
            (mask & (original[:, :, 3] > 0)).astype(np.uint8), 8
        )
        for index in range(1, count):
            x, y, width, height, area = stats[index]
            if not (85 <= area <= 370 and 20 <= width <= 27 and 11 <= height <= 22):
                continue
            cx, cy = centroids[index]
            if any(start <= cy <= end for start, end in decorations):
                continue
            if all(abs(cx - xx) > 9 or abs(cy - yy) > 9 for xx, yy, _ in centers):
                centers.append((round(cx), round(cy), 12))
    return centers


def cubic(p0, p1, p2, p3, steps=28):
    return [((1-t)**3 * p0[0] + 3*(1-t)**2*t*p1[0] + 3*(1-t)*t*t*p2[0] + t**3*p3[0],
             (1-t)**3 * p0[1] + 3*(1-t)**2*t*p1[1] + 3*(1-t)*t*t*p2[1] + t**3*p3[1])
            for t in np.linspace(0, 1, steps)]


def draw_banner(width, height, y1, y2):
    """Thin rectangle and restrained mirrored floral lines around source title."""
    banner = Image.new("RGBA", (width, height))
    draw = ImageDraw.Draw(banner)
    top, bottom = y1 + 5, y2 - 5
    center = (top + bottom) / 2
    draw.rectangle((4, top, width - 5, bottom), outline=RED, width=1)

    def side(mirror):
        def point(x, y):
            return (width - 1 - x if mirror else x, y)

        curves = [
            ((7, center + 4), (30, center + 18), (45, center - 12), (105, center)),
            ((22, center + 6), (31, center - 4), (41, top + 3), (56, top + 2)),
            ((47, center - 2), (60, center + 8), (68, bottom - 2), (91, bottom - 2)),
        ]
        for curve in curves:
            draw.line([point(*p) for p in cubic(*curve)], fill=RED, width=1)
        for flower_x, flower_y, radius in ((28, center, 7), (81, center - 6, 5)):
            for dx, dy in ((0, -radius), (radius, 0), (0, radius), (-radius, 0)):
                x, y = point(flower_x + dx, flower_y + dy)
                draw.ellipse((x - 3, y - 3, x + 3, y + 3), outline=RED, width=1)
            x, y = point(flower_x, flower_y)
            draw.ellipse((x - 2, y - 2, x + 2, y + 2), fill=RED)
        for leaf_x, leaf_y in ((50, center - 11), (65, center + 9), (99, center - 3)):
            x, y = point(leaf_x, leaf_y)
            draw.ellipse((x - 4, y - 2, x + 4, y + 2), outline=RED, width=1)

    side(False)
    side(True)
    return np.asarray(banner)


def title_ink(original, page, y1, y2):
    h, w = original.shape[:2]
    luma = (77 * original[:, :, 0].astype(np.int32)
            + 150 * original[:, :, 1].astype(np.int32)
            + 29 * original[:, :, 2].astype(np.int32)) >> 8
    mask = np.zeros((h, w), dtype=bool)
    if page <= 2:
        mask[47:74, 174:273] = True
        shift = y1 + 15 - 47
    else:
        mask[y1 + 10:y2 - 8, w // 2 - 85:w // 2 + 85] = True
        shift = 0
    mask &= (luma < 205) & (original[:, :, 3] > 0)
    return mask, shift


def simplify_marker(original, output, center):
    cx, cy, radius = center
    h, w = original.shape[:2]
    y0, y1 = max(0, cy - radius - 9), min(h, cy + radius + 10)
    x0, x1 = max(0, cx - radius - 4), min(w, cx + radius + 5)
    yy, xx = np.ogrid[y0:y1, x0:x1]
    erasure = ((xx - cx) / (radius + 3.0)) ** 2 + ((yy - cy) / (radius + 8.0)) ** 2 <= 1
    output[y0:y1, x0:x1][erasure] = (0, 0, 0, 0)

    # Reuse only the printed numeral ink from the source marker's interior.
    inner = ((xx - cx) / (radius - 2.4)) ** 2 + ((yy - cy) / (radius - 4.0)) ** 2 < 1
    rgb = original[y0:y1, x0:x1, :3].astype(np.int32)
    luma = (77 * rgb[:, :, 0] + 150 * rgb[:, :, 1] + 29 * rgb[:, :, 2]) >> 8
    ink = inner & (luma < 155) & (original[y0:y1, x0:x1, 3] > 0)
    if not np.any(ink):
        raise ValueError(f"No printed numeral ink at ({cx}, {cy})")
    numerator = np.zeros((y1 - y0, x1 - x0, 4), dtype=np.uint8)
    numerator[ink] = original[y0:y1, x0:x1][ink]
    circle = Image.new("RGBA", (x1 - x0, y1 - y0))
    draw = ImageDraw.Draw(circle)
    draw.ellipse((cx - radius - x0, cy - radius - y0,
                  cx + radius - x0, cy + radius - y0), outline=RED, width=1)
    ring = np.asarray(circle)
    region = output[y0:y1, x0:x1]
    region[ring[:, :, 3] > 0] = RED
    region[ink] = numerator[ink]


def build_page(original, page):
    h, w = original.shape[:2]
    markers = marker_centers(original, page)
    if page <= 2:
        text_mask, _, _ = opening_page(original, page)
        output = np.zeros_like(original)
        output[text_mask] = original[text_mask]
        bands = [(112, 170)]
    else:
        output = original.copy()
        bands = title_bands(original)
    for y1, y2 in bands:
        if page > 2:
            output[y1:y2 + 1] = (0, 0, 0, 0)
        banner = draw_banner(w, h, y1, y2)
        output[banner[:, :, 3] > 0] = banner[banner[:, :, 3] > 0]
        title, shift = title_ink(original, page, y1, y2)
        for y, x in zip(*np.where(title)):
            target_y = y + shift
            if 0 <= target_y < h:
                output[target_y, x] = original[y, x]
    for marker in markers:
        simplify_marker(original, output, marker)
    return output, len(markers), len(bands)


def main(source, target):
    target.mkdir(parents=True, exist_ok=True)
    markers = banners = 0
    for page in range(1, 605):
        original = np.asarray(Image.open(source / f"{page}.png").convert("RGBA"))
        output, m, b = build_page(original, page)
        save_exact_palette(output, target / f"{page}.png")
        markers += m
        banners += b
    if banners != 114:  # 112 on regular pages and one each on pages 1–2.
        raise ValueError(f"Expected 114 title banners, got {banners}")
    print(f"Simplified {banners} title banners and {markers} verse markers on 604 pages.")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
