"""Generate launcher icons from the source artwork.

Usage: python tools/make_icons.py <source.png> app/src/main/res
"""
import os
import sys

from PIL import Image, ImageDraw

LEGACY = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def main(src, res):
    im = Image.open(src).convert("RGBA")
    yellow = im.getpixel((im.width // 2, 40))
    # The artwork has white corners outside its rounded square; make them transparent.
    for corner in [(0, 0), (im.width - 1, 0), (0, im.height - 1), (im.width - 1, im.height - 1)]:
        ImageDraw.floodfill(im, corner, (255, 255, 255, 0), thresh=40)
    for name, size in LEGACY.items():
        d = os.path.join(res, f"mipmap-{name}")
        os.makedirs(d, exist_ok=True)
        im.resize((size, size), Image.LANCZOS).save(os.path.join(d, "ic_launcher.png"))
    for name, size in ADAPTIVE.items():
        d = os.path.join(res, f"mipmap-{name}")
        canvas = Image.new("RGBA", (size, size), yellow)
        inner = int(size * 0.78)
        art = im.resize((inner, inner), Image.LANCZOS)
        off = (size - inner) // 2
        canvas.paste(art, (off, off), art)
        canvas.save(os.path.join(d, "ic_launcher_foreground.png"))
    print("icon background colour: #%02X%02X%02X" % yellow[:3])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
