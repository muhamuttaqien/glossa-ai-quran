"""Build the experimental page 3 from Miftah Fentaw's transparent WebP.

Usage: python3 tools/build_highres_page3_pilot.py SOURCE_003_WEBP OUTPUT_3_PNG

The Arabic ink and verse numerals are copied from source pixels. Only the
connected ornamental rings are removed and replaced by Glossa-red circles.
The assertions deliberately limit this tool to the pinned 1080x1440 page 3
layout; it must not be run indiscriminately on other Mushaf pages.
"""

import hashlib
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw


SOURCE_SHA = "dc9425c28e93b0a82fb4a1c3dcec9ff1b1c69df9"
RED = (239, 77, 77, 255)


def build(source: Path, target: Path) -> list[tuple[int, int]]:
    data = source.read_bytes()
    blob_sha = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
    if blob_sha != SOURCE_SHA:
        raise ValueError(f"Unexpected WebP source blob: {blob_sha}")
    original = Image.open(source).convert("RGBA")
    if original.size != (1080, 1440):
        raise ValueError(f"Unexpected source dimensions: {original.size}")
    alpha = np.array(original.getchannel("A"))
    count, labels, stats, _ = cv2.connectedComponentsWithStats(
        (alpha > 16).astype(np.uint8), 8
    )
    ornaments = [
        index for index in range(1, count)
        if 90 <= stats[index, cv2.CC_STAT_WIDTH] <= 92
        and 66 <= stats[index, cv2.CC_STAT_HEIGHT] <= 68
        and 1250 <= stats[index, cv2.CC_STAT_AREA] <= 1400
    ]
    if len(ornaments) != 11:
        raise ValueError(f"Expected 11 isolated verse ornaments, got {len(ornaments)}")

    edited = alpha.copy()
    ring_pixels = np.isin(labels, ornaments)
    edited[ring_pixels] = 0
    # Clear only the barely visible antialias fringe of the removed ring.
    fringe = cv2.dilate(ring_pixels.astype(np.uint8), np.ones((3, 3), np.uint8)) > 0
    edited[fringe & (alpha <= 16)] = 0

    result = Image.new("RGBA", original.size, (0, 0, 0, 0))
    result.putalpha(Image.fromarray(edited, "L"))
    draw = ImageDraw.Draw(result)
    centers = []
    for index in sorted(ornaments, key=lambda i: stats[i, cv2.CC_STAT_TOP]):
        x, y, width, height, _ = map(int, stats[index])
        cx, cy = x + width // 2, y + height // 2
        # The original Arabic numeral is a separate ink component and stays
        # untouched at the middle of the newly drawn circle.
        if not (edited[cy - 19:cy + 20, cx - 19:cx + 20] > 40).any():
            raise ValueError(f"Printed numeral missing at ({cx}, {cy})")
        draw.ellipse((cx - 25, cy - 25, cx + 25, cy + 25), outline=RED, width=2)
        centers.append((cx, cy))
    target.parent.mkdir(parents=True, exist_ok=True)
    result.save(target, optimize=True)
    return centers


if __name__ == "__main__":
    centers = build(Path(sys.argv[1]), Path(sys.argv[2]))
    print(f"Built page 3 at 1080x1440 with {len(centers)} red verse circles: {centers}")
