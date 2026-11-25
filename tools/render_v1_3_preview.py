"""Combine three user-supplied app screenshots into the v1.3 preview.

The screenshots are resized uniformly and clipped only at the rounded phone
corners. Their Qur'an text and controls are never reconstructed or retouched.
"""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parent.parent
DESTINATION = ROOT / "preview/glossa-quran-v1.3-preview.png"
SOURCES = ROOT / "preview/source-v1.3"
SCALE = 2
WIDTH, HEIGHT = 1600, 1200
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def at(*values):
    return tuple(round(value * SCALE) for value in values)


def font(size, bold=False):
    return ImageFont.truetype(BOLD if bold else FONT, size * SCALE)


def text(draw, x, y, content, size, color, bold=False, anchor=None):
    draw.text(at(x, y), content, fill=color, font=font(size, bold), anchor=anchor)


def phone(canvas, x, image_path):
    y = 200
    handset_width, handset_height = 430, 940
    screen_width, screen_height = 410, 920

    shadow = Image.new("RGBA", canvas.size)
    ImageDraw.Draw(shadow).rounded_rectangle(
        at(x + 5, y + 13, x + handset_width + 5, y + handset_height + 13),
        radius=52 * SCALE, fill=(0, 0, 0, 74)
    )
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(22 * SCALE)))

    draw = ImageDraw.Draw(canvas)
    draw.rounded_rectangle(
        at(x, y, x + handset_width, y + handset_height),
        radius=52 * SCALE, fill=(4, 4, 4), outline=(89, 91, 91), width=3 * SCALE
    )
    draw.rounded_rectangle(
        at(x + 5, y + 5, x + handset_width - 5, y + handset_height - 5),
        radius=48 * SCALE, outline=(29, 30, 30), width=2 * SCALE
    )

    source = Image.open(image_path).convert("RGBA")
    assert source.size == (1080, 2424), image_path
    screenshot = source.resize(at(screen_width, screen_height), Image.Resampling.LANCZOS)
    mask = Image.new("L", screenshot.size)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, screenshot.width - 1, screenshot.height - 1),
        radius=43 * SCALE, fill=255
    )
    screenshot.putalpha(mask)
    canvas.alpha_composite(screenshot, at(x + 10, y + 10))


def main():
    canvas = Image.new("RGBA", at(WIDTH, HEIGHT), (237, 239, 236, 255))
    draw = ImageDraw.Draw(canvas)
    icon = Image.open(ROOT / "design/glossa-icon-128.png").convert("RGBA")
    canvas.alpha_composite(icon.resize(at(60, 60), Image.Resampling.LANCZOS), at(332, 27))
    text(draw, 413, 60, "Glossa AI - Qur'an Version (v1.3)", 37,
         (34, 36, 38), bold=True, anchor="lm")
    text(draw, WIDTH / 2, 126,
         "Red Verse Markers  ·  Tap to Toggle Controls  ·  Animated Page Swipe",
         21, (77, 83, 83), bold=True, anchor="mm")

    for x, name, image_path in (
        (85, "LIGHT MODE · READING", SOURCES / "light-reading.png"),
        (585, "DARK MODE · CONTROLS", SOURCES / "dark-controls.png"),
        (1085, "DARK MODE · READING", SOURCES / "dark-reading.png"),
    ):
        text(draw, x + 215, 174, name, 14, (91, 96, 96), bold=True, anchor="mm")
        phone(canvas, x, image_path)

    result = canvas.convert("RGB").resize((WIDTH, HEIGHT), Image.Resampling.LANCZOS)
    result.save(DESTINATION, optimize=True)
    print(DESTINATION)


if __name__ == "__main__":
    main()
