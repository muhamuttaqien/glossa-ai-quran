"""Replace the earlier drawn title motifs with the supplied floral silhouette.

Runs on the already simplified numbered PNG Mushaf pages. Only red banner pixels
inside the detected title bands are cleared; printed Arabic and verse markers
are left untouched. Run against a copy of app/src/main/assets/mushaf first.
"""

import sys
from pathlib import Path

import numpy as np
from PIL import Image

from simplify_mushaf_artwork import RED, draw_banner
from style_mushaf_frames import save_exact_palette

SOURCE_REDS = ((196, 107, 107, 255), RED)


def retouch(source, target):
    count = pages = 0
    target.mkdir(parents=True, exist_ok=True)
    for page in range(1, 605):
        image = np.asarray(Image.open(source / f"{page}.png").convert("RGBA"))
        red = np.logical_or.reduce([np.all(image == color, axis=2) for color in SOURCE_REDS])
        lines = np.flatnonzero(red.sum(axis=1) > image.shape[1] * .65)
        if len(lines) % 2:
            raise ValueError(f"Page {page}: unmatched border line: {lines}")
        if not len(lines):
            continue
        pages += 1
        output = image.copy()
        for top, bottom in lines.reshape(-1, 2):
            if not 45 <= bottom - top <= 55:
                raise ValueError(f"Page {page}: unexpected banner size {top}:{bottom}")
            y1, y2 = top - 5, bottom + 5
            region = output[y1:y2 + 1]
            ornament = np.logical_or.reduce([
                np.all(region[:, :, :3] == color[:3], axis=2) for color in SOURCE_REDS
            ]) & (region[:, :, 3] > 0)
            region[ornament] = (0, 0, 0, 0)
            art = draw_banner(image.shape[1], image.shape[0], y1, y2)
            painted = art[:, :, 3] > 0
            output[painted] = art[painted]
            count += 1
        save_exact_palette(output, target / f"{page}.png")
    if (pages, count) != (98, 114):
        raise ValueError(f"Expected 98 pages / 114 banners, got {pages} / {count}")
    print(f"Retouched {count} banners on {pages} pages; other pages unchanged.")


if __name__ == "__main__":
    retouch(Path(sys.argv[1]), Path(sys.argv[2]))
