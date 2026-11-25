"""Rebuild framed-page variants from the bundled original artwork.

Requires Pillow and NumPy. Never redraws or types Qur'an text: verse pixels
come directly from the original page, at their original coordinates.

    python3 tools/style_mushaf_frames.py app/src/main/assets/mushaf
"""

import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw


LIGHT = {
    "base": (255, 254, 250), "base_shade": (242, 239, 233),
    "motif_light": (231, 228, 220), "motif": (216, 212, 204),
    "motif_shadow": (189, 185, 177), "outline": (157, 151, 140),
    "outline_dark": (132, 125, 115), "red": (196, 107, 107),
    "red_light": (214, 157, 157), "title": (34, 36, 35),
}
DARK = {
    "base": (23, 23, 23), "base_shade": (34, 33, 32),
    "motif_light": (45, 44, 42), "motif": (57, 57, 54),
    "motif_shadow": (81, 77, 73), "outline": (119, 115, 108),
    "outline_dark": (146, 140, 131), "red": (184, 117, 117),
    "red_light": (128, 88, 88), "title": (248, 248, 248),
}

# Exact source-image pixel bands, calibrated against the printed opening pages.
PAGE_ONE_TEXT = [
    (140, 200, 302, 230), (114, 232, 341, 268),
    (84, 268, 363, 305), (82, 303, 359, 340),
    (82, 337, 359, 379), (112, 377, 321, 414),
    (152, 414, 285, 448),
]
PAGE_TWO_TEXT = [
    (145, 198, 300, 233), (105, 231, 335, 271),
    (82, 268, 360, 307), (78, 305, 365, 343),
    (82, 341, 360, 381), (105, 378, 338, 417),
    (145, 413, 295, 451),
]


def title_bands(image):
    rgb = image[:, :, :3].astype(np.int32)
    luma = (77 * rgb[:, :, 0] + 150 * rgb[:, :, 1] + 29 * rgb[:, :, 2]) >> 8
    dense = np.flatnonzero(((image[:, :, 3] > 0) & (luma < 200)).mean(axis=1) > 0.75)
    bands = []
    for y in dense:
        if bands and y - bands[-1][1] <= 62:
            bands[-1][1] = int(y)
        else:
            bands.append([int(y), int(y)])
    for band in bands:
        if band[1] - band[0] not in (57, 58, 59):
            raise ValueError(f"Unexpected title band: {band}")
        if band[0] and (image[band[0] - 1, :, 3] > 0).mean() > .75:
            band[0] -= 1
        if band[1] + 1 < len(image) and (image[band[1] + 1, :, 3] > 0).mean() > .75:
            band[1] += 1
    return bands


def styled_ornament(original, palette, title_region):
    """Map only ornament palette entries, keeping the printed Arabic title."""
    result = original.copy()
    rgb = original[:, :, :3]
    # Artwork uses a 15-color indexed palette. Exact source RGB is used to
    # classify each tone; the position disambiguates dark title from outline.
    for color in np.unique(rgb.reshape(-1, 3), axis=0):
        r, g, b = map(int, color)
        if r == g == b == 255:
            continue
        if r > 185 and r - g > 25 and g - b > 18:
            role = "red" if g < 185 else "red_light"
        elif r >= 230 and g >= 218 and b >= 205:
            role = "base" if g >= 230 and b >= 215 else "base_shade"
        else:
            luminance = (77 * r + 150 * g + 29 * b) >> 8
            if luminance < 75:
                role = "outline_dark"
            elif luminance < 125:
                role = "outline"
            elif luminance < 170:
                role = "motif_shadow"
            elif luminance < 205:
                role = "motif"
            else:
                role = "motif_light"
        selector = np.all(rgb == color, axis=2) & (original[:, :, 3] > 0)
        result[selector, :3] = palette[role]
    # The title is printed in the darkest ink, identical to some of the
    # outlines. Keep its original pixel silhouette and verse markers intact.
    dark_ink = (rgb[:, :, :3].astype(np.int16).mean(axis=2) < 75)
    result[title_region & dark_ink & (original[:, :, 3] > 0), :3] = palette["title"]
    return result


def invert_original(original):
    result = original.copy()
    result[:, :, :3] = np.clip(240 - .85 * original[:, :, :3].astype(np.float32), 0, 255).astype(np.uint8)
    return result


def save_exact_palette(image, path):
    """Use indexed PNG losslessly; the originals have only ~15 colors."""
    colors, indices = np.unique(image.reshape(-1, 4), axis=0, return_inverse=True)
    if len(colors) > 256:
        Image.fromarray(image, "RGBA").save(path, optimize=True)
        return
    indexed = Image.fromarray(indices.reshape(image.shape[:2]).astype(np.uint8), "P")
    palette = np.zeros((256, 3), dtype=np.uint8)
    palette[:len(colors)] = colors[:, :3]
    indexed.putpalette(palette.flatten().tolist())
    indexed.info["transparency"] = bytes(colors[:, 3])
    indexed.save(path, optimize=True)


def opening_page(original, page):
    h, w = original.shape[:2]
    allowed = np.zeros((h, w), dtype=bool)
    for x1, y1, x2, y2 in (PAGE_ONE_TEXT if page == 1 else PAGE_TWO_TEXT):
        allowed[y1:y2, x1:x2] = True
    rgb = original[:, :, :3].astype(np.int32)
    luma = (77 * rgb[:, :, 0] + 150 * rgb[:, :, 1] + 29 * rgb[:, :, 2]) >> 8
    ink = allowed & (original[:, :, 3] > 0) & (luma < 205)
    nearby = np.zeros_like(ink)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            nearby[max(0, dy):h + min(0, dy), max(0, dx):w + min(0, dx)] |= ink[max(0, -dy):h - max(0, dy), max(0, -dx):w - max(0, dx)]
    text_mask = allowed & nearby & (original[:, :, 3] > 0)

    # Retain the original title cartouche and its Arabic lettering, but none
    # of the floral full-page frame. The fixed mask is the same on pages 1–2.
    crest = Image.new("1", (w, h))
    ImageDraw.Draw(crest).polygon([
        (213, 0), (230, 0), (250, 6), (267, 16), (281, 29),
        (298, 43), (311, 57), (318, 71), (312, 82), (292, 86),
        (269, 86), (246, 93), (225, 111), (212, 97), (193, 88),
        (169, 85), (145, 86), (125, 81), (123, 71), (131, 55),
        (144, 40), (160, 27), (179, 14), (196, 5),
    ], fill=1)
    crest_mask = np.asarray(crest).astype(bool) & (original[:, :, 3] > 0)
    title = np.zeros((h, w), dtype=bool)
    title[36:75, 165:282] = True
    return text_mask, crest_mask, title


def main(directory):
    pages = 0
    bands_count = 0
    for page in range(1, 605):
        original = np.asarray(Image.open(directory / f"{page}.png").convert("RGBA"))
        if page <= 2:
            text_mask, crest_mask, title = opening_page(original, page)
            masks = [crest_mask]
        else:
            bands = title_bands(original)
            if not bands:
                continue
            bands_count += len(bands)
            masks = []
            title = np.zeros(original.shape[:2], dtype=bool)
            for y1, y2 in bands:
                mask = np.zeros(original.shape[:2], dtype=bool)
                mask[y1:y2 + 1, :] = True
                masks.append(mask)
                title[y1 + 12:y2 - 8, original.shape[1] // 2 - 95:original.shape[1] // 2 + 95] = True
        ornament_mask = np.logical_or.reduce(masks)
        light = original.copy()
        dark = invert_original(original)
        for palette, result in ((LIGHT, light), (DARK, dark)):
            colored = styled_ornament(original, palette, title)
            result[ornament_mask] = colored[ornament_mask]
        if page <= 2:
            keep = text_mask | crest_mask
            light[~keep] = (0, 0, 0, 0)
            dark[~keep] = (0, 0, 0, 0)
        save_exact_palette(light, directory / f"{page}-frame.png")
        save_exact_palette(dark, directory / f"{page}-frame-dark.png")
        pages += 1
    if pages != 98 or bands_count != 112:
        raise ValueError(f"Expected 98 pages and 112 title bands; got {pages} and {bands_count}")
    print(f"Generated {pages} light/dark framed-page pairs ({bands_count} title bands).")


if __name__ == "__main__":
    main(Path(sys.argv[1]))
