"""
Turns a prop exported from the design file into a playground sprite: solid Ink with the carved lines cut out as transparency, cropped to the drawing.

The generated woodcut props draw their carved lines as white shapes over the Ink. The game tints every visible pixel, so white has to become a hole: dark pixels
stay Ink, light ones go, with a short ramp between so the edges stay smooth.

    python3 docs/design/ink_cutout.py exported.png samples/playground/src/commonMain/composeResources/drawable/prop_onca.png
"""
import sys

from PIL import Image

INK = (0x14, 0x11, 0x0F)
SOLID_BELOW = 120   # luminance at or under this stays fully Ink
GONE_ABOVE = 160    # luminance at or over this is a hole


def cutout(src: str, dst: str) -> tuple:
    im = Image.open(src).convert("RGBA")
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    px, op = im.load(), out.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = 0.299 * r + 0.587 * g + 0.114 * b
            k = 1.0 if lum <= SOLID_BELOW else 0.0 if lum >= GONE_ABOVE else (GONE_ABOVE - lum) / (GONE_ABOVE - SOLID_BELOW)
            alpha = int(a * k)
            if alpha:
                op[x, y] = (*INK, alpha)
    box = out.getbbox()
    out = out.crop(box)
    out.save(dst, optimize=True)
    return out.size


if __name__ == "__main__":
    w, h = cutout(sys.argv[1], sys.argv[2])
    print(f"{sys.argv[2]}: {w}x{h}")
