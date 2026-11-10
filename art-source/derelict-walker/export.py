"""Cuts a derelict walker master (raw/walker-<design>[-<version>].png) into the parts DerelictWalkerComposer draws.

  python art-source/derelict-walker/export.py <design> <out-root>
  python art-source/derelict-walker/export.py --master <png> --layout <parts.json> --part-dir <name> <out-root>

The second form cuts another four-legged machine laid out the same way (Tri-Tachyon's crawler:
art-source/mech-modular-topdown/sources/hightech/crawler), from its own master and parts layout into
<out-root>/graphics/battle/<name>/.

The master is drawn over guides/<design>-guide.png (guide.py): the parts sit apart on its right panel, upright, on a
flat grey ground. Each part is keyed off the ground, taken from its guide box (scaled to the raw's size if ImageGen
changed it), trimmed to its opaque extent along its length only (so its joint stays on the box's centre line) and
written at SCALE into <out-root>/graphics/battle/derelict-walker/: torso.png, thigh.png (hip at the bottom, knee at
the top), shin.png (knee at the bottom, foot at the top) and joint.png (the knee, cut from the shin's root). <out-root> is mod/ to ship, or a -Dsnapshot.artOverlay
folder to show a candidate in game without shipping it.
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
# Shipped px per cell: the shipped mech sprites draw about 120 px a cell, so the parts keep detail at close zoom.
SCALE = 0.5


def keyed(path):
    """The master on a transparent ground, the grey unmixed from its soft edge."""
    a = np.asarray(Image.open(path).convert("RGB")).astype(float)
    corners = np.concatenate([a[:20, :20].reshape(-1, 3), a[:20, -20:].reshape(-1, 3),
                              a[-20:, :20].reshape(-1, 3), a[-20:, -20:].reshape(-1, 3)])
    bg = np.median(corners, axis=0)
    dist = np.sqrt(((a - bg) ** 2).sum(2))
    alpha = np.clip((dist - 10) / 24, 0, 1)
    k = np.maximum(alpha, 1e-3)[..., None]
    rgb = np.clip((a - bg * (1 - alpha[..., None])) / k, 0, 255)
    return np.dstack([rgb, alpha * 255]).astype(np.uint8)


def cut(rgba, box, sx, sy, trim_rows):
    x0, y0, x1, y1 = (int(round(box[0] * sx)), int(round(box[1] * sy)), int(round(box[2] * sx)),
                      int(round(box[3] * sy)))
    part = rgba[y0:y1, x0:x1].copy()
    if trim_rows:
        rows = np.where(part[..., 3].max(axis=1) > 24)[0]
        part = part[rows[0]:rows[-1] + 1]
    img = Image.fromarray(part, "RGBA")
    small = img.resize((max(1, round(img.width * SCALE)), max(1, round(img.height * SCALE))), Image.LANCZOS)
    # Downscaling softens the plating's hard edges; a light unsharp mask gives them back, as the vanilla sprites read.
    return small.filter(ImageFilter.UnsharpMask(radius=1.2, percent=70, threshold=2))


def joint(shin):
    """The shin's root joint disc: a square as wide as the opaque disc, standing on the shin's bottom row."""
    a = np.asarray(shin)[..., 3] > 24
    h = a.shape[0]
    rows = a[int(h * 0.8):]
    cols = np.where(rows.any(axis=0))[0]
    side = int(cols[-1] - cols[0] + 1)
    cx = (cols[0] + cols[-1] + 1) / 2
    bottom = int(np.where(a.any(axis=1))[0][-1]) + 1
    x0 = int(round(cx - side / 2))
    disc = np.asarray(shin.crop((x0, bottom - side, x0 + side, bottom))).copy()
    yy, xx = np.mgrid[0:side, 0:side]
    r = (side - 1) / 2
    inside = np.clip(r + 0.5 - np.sqrt((xx - r) ** 2 + (yy - r) ** 2), 0, 1)
    disc[..., 3] = (disc[..., 3] * inside).astype(np.uint8)
    return Image.fromarray(disc, "RGBA")


def main():
    args = sys.argv[1:]
    opts = {}
    while args and args[0].startswith("--"):
        opts[args[0][2:]] = args[1]
        args = args[2:]
    if "master" in opts:
        raw, root = opts["master"], args[0]
        layout = json.load(open(opts["layout"]))
        folder = opts["part-dir"]
    else:
        design, root = args[0], args[1]
        layout = json.load(open(os.path.join(HERE, "guides", "parts.json")))
        # "dome-v2" is drawn over the dome guide; every guide shares one parts layout.
        raw = os.path.join(HERE, "raw", "walker-" + design + ".png")
        folder = "derelict-walker"
    rgba = keyed(raw)
    sx = rgba.shape[1] / layout["canvas"][0]
    sy = rgba.shape[0] / layout["canvas"][1]
    out = os.path.join(root, "graphics", "battle", folder)
    os.makedirs(out, exist_ok=True)
    parts = layout["parts"]
    cut(rgba, parts["torso"], sx, sy, False).save(os.path.join(out, "torso.png"))
    cut(rgba, parts["thigh"], sx, sy, True).save(os.path.join(out, "thigh.png"))
    shin = cut(rgba, parts["shin"], sx, sy, True)
    shin.save(os.path.join(out, "shin.png"))
    # The knee: the shin's own root joint, its bottom square, drawn over both segments where they meet.
    joint(shin).save(os.path.join(out, "joint.png"))
    print("wrote", out)


if __name__ == "__main__":
    main()
