"""Space the existing page 4 ink without replacing or resampling its pixels.

Usage: python3 tools/space_existing_page4_pilot.py ORIGINAL_4_PNG OUTPUT_4_PNG
The original is the bundled 4.png at development commit 9d41b79.
"""

import hashlib
import sys
from pathlib import Path

import cv2
import numpy as np
from PIL import Image


SOURCE_SHA = "aba03e85f07fafa04f5880bbe83375c8c2a06c68"
LINE_CUTS = (51, 109, 158, 212, 265, 317, 373, 425, 480, 536, 585, 644, 693, 746)
LINE_GAP = 8


def build(source: Path, target: Path) -> None:
    data = source.read_bytes()
    blob_sha = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
    if blob_sha != SOURCE_SHA:
        raise ValueError(f"Unexpected page 4 source: {blob_sha}")
    original = np.asarray(Image.open(source).convert("RGBA"))
    if original.shape != (802, 491, 4):
        raise ValueError(f"Unexpected page 4 dimensions: {original.shape}")

    # A few letter components reach across an interline cut. Move each intact
    # connected component as one unit so no strokes or diacritics are split.
    count, labels, stats, centroids = cv2.connectedComponentsWithStats(
        (original[:, :, 3] > 0).astype(np.uint8), 8
    )
    if max(stats[1:, cv2.CC_STAT_HEIGHT]) >= 70:
        raise ValueError("A component may span two printed lines")
    spaced = np.zeros((802 + len(LINE_CUTS) * LINE_GAP, 491, 4), dtype=np.uint8)
    for index in range(1, count):
        x, y, width, height, _ = map(int, stats[index])
        line = int(np.searchsorted(LINE_CUTS, centroids[index, 1], side="right"))
        component = labels[y:y + height, x:x + width] == index
        ys, xs = np.where(component)
        target_y = y + ys + line * LINE_GAP
        target_x = x + xs
        if np.any(spaced[target_y, target_x, 3]):
            raise ValueError(f"Components overlap after moving line {line + 1}")
        spaced[target_y, target_x] = original[y + ys, x + xs]
    if np.count_nonzero(spaced[:, :, 3]) != np.count_nonzero(original[:, :, 3]):
        raise ValueError("Printed pixels were lost")
    target.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(spaced, "RGBA").save(target, optimize=True)


if __name__ == "__main__":
    build(Path(sys.argv[1]), Path(sys.argv[2]))
    print("Built page 4 at 491x914 from unchanged source pixels")
