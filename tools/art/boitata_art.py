#!/usr/bin/env python3
"""Prepares a game's pictures for Boitatá (docs/art-pipeline.md): cropped to their content, with the frame that puts them back in their canvas,
and a small shadow outline per thing.

As a module (an importer calls it on the PNG it rendered, before encoding):
    frame = crop_to_content("in.png", "out.png")      # {"canvas": [w, h], "box": [l, t, r, b]}, or None when nothing needs cutting
    outline("state.png", "thing_outline.webp")        # an alpha-only silhouette, OUTLINE_HEIGHT px tall

As a command:
    python3 -I boitata_art.py crop <in.png> <out.png>
    python3 -I boitata_art.py outline <in.png|webp> [more layers ...] <out.webp> [--height 160]

Needs Pillow.
"""
import sys
from PIL import Image

# A cell this opaque (0-255) counts as paint when finding a picture's content; fainter pixels at the edge of a soft glow are kept by the margin.
SOLID = 1
# Pixels kept round the content, so a filtered (smoothed) draw of the cropped picture does not lose its edge.
MARGIN = 2
# A crop that keeps more than this share of the canvas is not worth a frame: the picture is kept whole.
WORTH = 0.9
# The height of a shadow outline, in pixels: a shadow is soft and drawn from a few points across the light, so this is plenty for any size on screen.
OUTLINE_HEIGHT = 160
# How opaque a pixel of the art must be to be part of a shadow outline (glows and steam are fainter and are left out).
OUTLINE_SOLID = 160


def content_box(img, solid=SOLID, margin=MARGIN):
    """The box (left, top, right, bottom) of the pixels at least [solid] opaque, grown by [margin] and kept inside the picture; None when it is empty."""
    alpha = img.getchannel("A").point(lambda a: 255 if a >= solid else 0)
    box = alpha.getbbox()
    if box is None:
        return None
    l, t, r, b = box
    return max(0, l - margin), max(0, t - margin), min(img.width, r + margin), min(img.height, b + margin)


def crop_to_content(src, dst, worth=WORTH):
    """Writes [src] cut to its content to [dst] and returns its frame ({"canvas": [w, h], "box": [l, t, r, b]}); writes it whole and returns None when the cut saves too little."""
    img = Image.open(src).convert("RGBA")
    box = content_box(img)
    if box is None or (box[2] - box[0]) * (box[3] - box[1]) > worth * img.width * img.height:
        if src != dst:
            img.save(dst)
        return None
    img.crop(box).save(dst)
    return {"canvas": [img.width, img.height], "box": list(box)}


def outline(layers, dst, height=OUTLINE_HEIGHT, solid=OUTLINE_SOLID):
    """
    The shadow outline of a thing drawn as [layers] (paths of pictures of one canvas size, stacked back to front): black, opaque where any layer is at least [solid] opaque,
    scaled to [height] px tall (its width follows), so its box is the same as the art's canvas. Glows, steam and faint smoke are below [solid] and do not cast; gaps (a handle,
    a frame) stay open. Written as a lossless WebP.
    """
    imgs = [Image.open(p).convert("RGBA") for p in layers]
    w, h = imgs[0].size
    mask = Image.new("L", (w, h), 0)
    for im in imgs:
        if im.size != (w, h):
            im = im.resize((w, h), Image.LANCZOS)
        mask = Image.composite(Image.new("L", (w, h), 255), mask, im.getchannel("A").point(lambda a: 255 if a >= solid else 0))
    out_w = max(1, round(w * height / h))
    small = mask.resize((out_w, height), Image.LANCZOS)
    black = Image.new("RGBA", small.size, (0, 0, 0, 0))
    black.putalpha(small)
    black.save(dst, "WEBP", lossless=True)
    return black.size


def main(argv):
    if len(argv) >= 3 and argv[0] == "crop":
        print(crop_to_content(argv[1], argv[2]))
    elif len(argv) >= 3 and argv[0] == "outline":
        args = list(argv[1:]); height = OUTLINE_HEIGHT
        if "--height" in args:
            i = args.index("--height"); height = int(args[i + 1]); del args[i:i + 2]
        print(outline(args[:-1], args[-1], height))
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
