"""Render the v1.3 light/dark reading preview from bundled app artwork.

This is a visual mockup of the controls-shown state, not a device screenshot.
The Qur'an page is composited from the same numbered PNG the app displays;
the dark palette mirrors MushafNightPalette.convert.
"""

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parent.parent
DESTINATION = ROOT / "preview/glossa-quran-v1.3-preview.png"
SCALE = 2
WIDTH, HEIGHT = 1300, 1220
RED = (239, 77, 77)
SURFACE = (16, 16, 16)
CARD = (23, 23, 23)
WHITE = (248, 248, 248)
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, round(size * SCALE))


def rect(box):
    return tuple(round(value * SCALE) for value in box)


def point(x, y):
    return round(x * SCALE), round(y * SCALE)


def label(draw, xy, value, size, color, bold=False, anchor=None):
    draw.text(point(*xy), value, font=font(size, bold), fill=color, anchor=anchor)


def dark_artwork(source):
    data = np.asarray(source.convert("RGBA")).copy()
    rgb = data[:, :, :3].astype(np.float32)
    red = (
        (rgb[:, :, 0] >= 223) & (rgb[:, :, 0] <= 255)
        & (rgb[:, :, 1] >= 61) & (rgb[:, :, 1] <= 93)
        & (rgb[:, :, 2] >= 61) & (rgb[:, :, 2] <= 93)
    )
    data[:, :, :3] = np.clip(240 - .85 * rgb, 0, 255).astype(np.uint8)
    data[red, :3] = RED
    return Image.fromarray(data, "RGBA")


def phone(canvas, left, night, artwork):
    top = 155
    screen_width, screen_height = 496, 1010
    screen = Image.new("RGBA", point(screen_width, screen_height), CARD + (255,) if night else (255, 254, 250, 255))
    draw = ImageDraw.Draw(screen)

    # ContentScale.Fit within the reading page's horizontal and vertical padding.
    page = dark_artwork(artwork) if night else artwork.convert("RGBA")
    width = screen_width - 28
    height = round(page.height * width / page.width)
    page = page.resize(point(width, height), Image.Resampling.LANCZOS)
    screen.alpha_composite(page, point((screen_width - width) / 2, (screen_height - height) / 2))

    # Reading chrome appears over the fixed-size page after a single tap.
    draw.rectangle(rect((0, 0, screen_width, 111)), fill=SURFACE)
    draw.rectangle(rect((0, 111, screen_width, 211)), fill=CARD)
    draw.rectangle(rect((0, 856, screen_width, 1010)), fill=CARD)
    draw.rectangle(rect((0, 946, screen_width, 1010)), fill=SURFACE)
    label(draw, (screen_width / 2, 55), "Al-Baqara", 20, WHITE, True, "mm")
    label(draw, (30, 57), "‹", 34, WHITE, anchor="mm")
    if night:
        cx, cy = point(screen_width - 43, 56)
        radius = 7 * SCALE
        draw.ellipse((cx - radius, cy - radius, cx + radius, cy + radius), fill=WHITE)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (.71, .71), (.71, -.71), (-.71, .71), (-.71, -.71)):
            draw.line((cx + round(dx * 11 * SCALE), cy + round(dy * 11 * SCALE),
                       cx + round(dx * 15 * SCALE), cy + round(dy * 15 * SCALE)),
                      fill=WHITE, width=2 * SCALE)
    else:
        label(draw, (screen_width - 43, 56), "☾", 24, WHITE, anchor="mm")

    label(draw, (103, 145), "READ ARABIC", 14, WHITE, True, "mm")
    label(draw, (346, 145), "TRANSLATION", 13, (150, 150, 150), anchor="mm")
    draw.rectangle(rect((23, 172, 231, 175)), fill=RED)
    label(draw, (25, 195), "Al-Baqara", 12, WHITE, anchor="lm")
    label(draw, (screen_width - 25, 195), "Juz 1", 12, WHITE, anchor="rm")

    label(draw, (screen_width / 2, 874), "2 / 604", 13, WHITE, anchor="mm")
    label(draw, (26, 916), "Reciter", 12, (156, 156, 156), anchor="lm")
    draw.rounded_rectangle(rect((103, 895, 474, 936)), radius=7 * SCALE,
                           outline=(80, 80, 80), width=SCALE)
    label(draw, (118, 916), "Mishary Rashid Alafasy", 12, WHITE, anchor="lm")
    label(draw, (453, 916), "⌄", 18, WHITE, anchor="mm")
    label(draw, (45, 978), "‹", 24, (120, 120, 120), anchor="mm")
    label(draw, (screen_width - 45, 978), "›", 24, (120, 120, 120), anchor="mm")
    draw.rounded_rectangle(rect((173, 963, 323, 995)), radius=17 * SCALE, fill=RED)
    label(draw, (screen_width / 2, 979), "Play", 14, WHITE, True, "mm")

    # Rounded handset shell and inset display.
    shadow = Image.new("RGBA", canvas.size)
    sd = ImageDraw.Draw(shadow)
    sd.rounded_rectangle(rect((left + 3, top + 12, left + 515, top + 1040)),
                         radius=55 * SCALE, fill=(0, 0, 0, 70))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(23 * SCALE)))
    shell = ImageDraw.Draw(canvas)
    shell.rounded_rectangle(rect((left, top, left + 514, top + 1030)),
                            radius=54 * SCALE, fill=(4, 4, 4), outline=(94, 94, 94), width=3 * SCALE)
    shell.rounded_rectangle(rect((left + 6, top + 6, left + 508, top + 1024)),
                            radius=49 * SCALE, outline=(30, 30, 30), width=3 * SCALE)
    mask = Image.new("L", screen.size)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, screen.width - 1, screen.height - 1),
                                           radius=43 * SCALE, fill=255)
    screen.putalpha(mask)
    canvas.alpha_composite(screen, point(left + 9, top + 10))


def main():
    canvas = Image.new("RGBA", point(WIDTH, HEIGHT), (237, 239, 236, 255))
    draw = ImageDraw.Draw(canvas)
    icon = Image.open(ROOT / "design/glossa-icon-128.png").convert("RGBA")
    canvas.alpha_composite(icon.resize(point(61, 61), Image.Resampling.LANCZOS), point(285, 28))
    label(draw, (367, 60), "Glossa AI - Qur'an Version (v1.3)", 30, (34, 36, 38), True, "lm")
    label(draw, (650, 119), "Read Arabic · Madinah Mushaf · Hafs", 21,
          (81, 87, 87), True, "mm")
    artwork = Image.open(ROOT / "app/src/main/assets/mushaf/2.png").convert("RGBA")
    phone(canvas, 110, False, artwork)
    phone(canvas, 676, True, artwork)
    result = canvas.convert("RGB").resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS)
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    result.save(DESTINATION, optimize=True)
    print(DESTINATION)


if __name__ == "__main__":
    main()
