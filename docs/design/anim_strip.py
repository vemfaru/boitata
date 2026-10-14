"""
Turns an animation sheet exported from the design file (section F: four poses of one animal in a 2x2 grid, in reading order) into a playground sprite sheet: the frames
side by side at one size, each solid Ink with its carved lines cut out (see ink_cutout.py), lined up so the animal stays in place while its legs or wings move.

    python3 docs/design/anim_strip.py exported.png anim_onca_walk.png [--grid 2x2] [--align body|beak] [--gif preview.gif]

The poses are found as shapes, not cells: everything that touches (after closing the carved gaps a little) is one piece, and each piece joins the pose whose cell
it sits nearest, so a tail that strays over a cell line stays with its animal. Walk cycles line up by their feet and the middle of the body (the top 60% of the pose,
where the legs don't swing); flight cycles line up by the beak.
"""
import argparse
from collections import deque

import numpy as np
from PIL import Image

from ink_cutout import GONE_ABOVE, INK, SOLID_BELOW

BLOCK = 6   # pixels per cell of the coarse mask the pieces are found on


def ink(im: Image.Image) -> np.ndarray:
    """Alpha of the Ink: dark pixels solid, light ones gone (the carving), a short ramp between."""
    a = np.asarray(im.convert("RGBA"), dtype=np.float32)
    lum = 0.299 * a[..., 0] + 0.587 * a[..., 1] + 0.114 * a[..., 2]
    k = np.clip((GONE_ABOVE - lum) / (GONE_ABOVE - SOLID_BELOW), 0, 1)
    return (a[..., 3] * k).astype(np.uint8)


def pieces(alpha: np.ndarray) -> np.ndarray:
    """Labels of the touching pieces on a coarse grid, after a one-cell dilation so a carved gap doesn't split a pose."""
    h, w = alpha.shape[0] // BLOCK, alpha.shape[1] // BLOCK
    coarse = (alpha[: h * BLOCK, : w * BLOCK] > 40).reshape(h, BLOCK, w, BLOCK).any(axis=(1, 3))
    grown = coarse.copy()
    grown[1:] |= coarse[:-1]; grown[:-1] |= coarse[1:]; grown[:, 1:] |= coarse[:, :-1]; grown[:, :-1] |= coarse[:, 1:]
    labels = np.zeros((h, w), dtype=np.int32)
    n = 0
    for y, x in zip(*np.nonzero(grown)):
        if labels[y, x]:
            continue
        n += 1
        labels[y, x] = n
        q = deque([(y, x)])
        while q:
            cy, cx = q.popleft()
            for ny, nx in ((cy - 1, cx), (cy + 1, cx), (cy, cx - 1), (cy, cx + 1)):
                if 0 <= ny < h and 0 <= nx < w and grown[ny, nx] and not labels[ny, nx]:
                    labels[ny, nx] = n
                    q.append((ny, nx))
    return labels


def poses(alpha: np.ndarray, cols: int, rows: int) -> list:
    """One alpha image per pose, in reading order."""
    labels = pieces(alpha)
    h, w = labels.shape
    ids = np.arange(1, labels.max() + 1)
    sizes = np.bincount(labels.ravel())[1:]
    ys, xs = np.indices(labels.shape)
    cy = np.bincount(labels.ravel(), ys.ravel())[1:] / np.maximum(sizes, 1)
    cx = np.bincount(labels.ravel(), xs.ravel())[1:] / np.maximum(sizes, 1)
    # Start each pose at its cell's centre, then let the centres settle on the big pieces (a few rounds of weighted k-means).
    centres = np.array([((c + 0.5) * w / cols, (r + 0.5) * h / rows) for r in range(rows) for c in range(cols)])
    for _ in range(8):
        owner = np.argmin((cx[:, None] - centres[None, :, 0]) ** 2 + (cy[:, None] - centres[None, :, 1]) ** 2, axis=1)
        for i in range(len(centres)):
            m = owner == i
            if sizes[m].sum():
                centres[i] = (np.average(cx[m], weights=sizes[m]), np.average(cy[m], weights=sizes[m]))
    out = []
    for i in range(len(centres)):
        keep = np.isin(labels, ids[owner == i])
        full = np.kron(keep, np.ones((BLOCK, BLOCK), dtype=bool))
        mask = np.zeros_like(alpha, dtype=bool)
        mask[: full.shape[0], : full.shape[1]] = full
        out.append(np.where(mask, alpha, 0))
    return out


def anchor(a: np.ndarray, align: str) -> tuple:
    """The point that should stay put from frame to frame."""
    ys, xs = np.nonzero(a > 128)
    if align == "beak":
        # The very tip of the beak: a wider window would catch a wing swept down past the head.
        tip = xs.max()
        near = xs >= tip - 0.01 * a.shape[1]
        return float(tip), float(ys[near].mean())
    top, bottom = ys.min(), ys.max()
    body = ys < top + 0.6 * (bottom - top)
    return float(xs[body].mean()), float(bottom)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("src")
    p.add_argument("dst")
    p.add_argument("--grid", default="2x2", help="columns x rows of poses, read left to right, top to bottom")
    p.add_argument("--align", choices=["body", "beak"], default="body")
    p.add_argument("--height", type=int, default=480, help="frame height in the sheet, so a sheet is about the size of a prop")
    p.add_argument("--pick", help="which poses to keep, counted from 1 in reading order (e.g. 1,3,4), when one came out unlike the others")
    p.add_argument("--gif")
    args = p.parse_args()
    cols, rows = (int(v) for v in args.grid.split("x"))

    frames = poses(ink(Image.open(args.src)), cols, rows)
    if args.pick:
        frames = [frames[int(i) - 1] for i in args.pick.split(",")]
    boxes = []
    for a in frames:
        ys, xs = np.nonzero(a)
        ax, ay = anchor(a, args.align)
        boxes.append((xs.min() - ax, ys.min() - ay, xs.max() + 1 - ax, ys.max() + 1 - ay, ax, ay))
    # One frame size that holds every pose with its anchor in the same place.
    left, top = -min(b[0] for b in boxes), -min(b[1] for b in boxes)
    w, h = int(np.ceil(left + max(b[2] for b in boxes))), int(np.ceil(top + max(b[3] for b in boxes)))
    sheet = np.zeros((h, w * len(frames)), dtype=np.uint8)
    for i, (a, b) in enumerate(zip(frames, boxes)):
        ax, ay = b[4], b[5]
        ys, xs = np.nonzero(a)
        dx, dy = int(round(i * w + left - ax)), int(round(top - ay))
        sheet[ys + dy, xs + dx] = a[ys, xs]
    rgba = np.zeros((h, w * len(frames), 4), dtype=np.uint8)
    rgba[..., :3] = INK
    rgba[..., 3] = sheet
    k = min(1.0, args.height / h)
    if k < 1.0:
        w = int(round(w * k))
        out = Image.fromarray(rgba).resize((w * len(frames), int(round(h * k))), Image.LANCZOS)
        h = out.height
        sheet = np.asarray(out)[..., 3]
    else:
        out = Image.fromarray(rgba)
    out.save(args.dst, optimize=True)
    print(f"{args.dst}: {len(frames)} frames of {w}x{h}")

    if args.gif:
        gif = []
        for i in range(len(frames)):
            f = np.zeros((h, w, 3), dtype=np.float32)
            f[:] = (60, 90, 80)
            k = sheet[:, i * w:(i + 1) * w, None] / 255.0
            f = f * (1 - k) + np.array((230, 190, 120)) * k
            gif.append(Image.fromarray(f.astype(np.uint8)))
        gif[0].save(args.gif, save_all=True, append_images=gif[1:], duration=150, loop=0)


if __name__ == "__main__":
    main()
