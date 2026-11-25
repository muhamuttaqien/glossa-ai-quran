"""Remove detached original-ornament fragments from `-new.png` Mushaf pages.

Supply the unmodified source artwork from Git commit aca71eb and the current
asset directory. Only pages whose marker pixels change are rewritten.

    python3 tools/retouch_verse_markers.py SOURCE_DIR app/src/main/assets/mushaf
"""

import sys
from pathlib import Path

import numpy as np
from PIL import Image

from simplify_mushaf_artwork import marker_centers, save_exact_palette, simplify_marker


def main(source: Path, target: Path):
    updated_pages = updated_pixels = markers = 0
    for page in range(1, 605):
        original = np.asarray(Image.open(source / f"{page}.png").convert("RGBA"))
        existing = np.asarray(Image.open(target / f"{page}-new.png").convert("RGBA"))
        if original.shape != existing.shape:
            raise ValueError(f"Unexpected dimensions on page {page}")
        result = existing.copy()
        centers = marker_centers(original, page)
        markers += len(centers)
        for center in centers:
            simplify_marker(original, result, center)
        changed = np.any(result != existing, axis=2)
        if not np.any(changed):
            continue
        # Every difference must only erase a fragment previously left inside
        # the simplified ring; the verse text, numerals, and rings stay put.
        if np.any(result[changed, 3] != 0) or np.any(existing[changed, 3] == 0):
            raise ValueError(f"Unexpected non-fragment change on page {page}")
        save_exact_palette(result, target / f"{page}-new.png")
        updated_pages += 1
        updated_pixels += int(np.count_nonzero(changed))
    if markers != 6236:
        raise ValueError(f"Expected 6236 verse markers, found {markers}")
    print(f"Checked {markers} markers on 604 pages; removed {updated_pixels} "
          f"stray pixels across {updated_pages} pages.")


if __name__ == "__main__":
    main(Path(sys.argv[1]), Path(sys.argv[2]))
