"""Export the Harrier (Rav flier) looks from their round-1 concept sheets into mod/graphics/battle/rav-harrier/.

python art-source/rav-redesign/export_harrier.py

Each look's sheet (three poses on grey: glide, flap, dive) is cut by exploration/flier/cut_flier.py, then all three
poses are scaled by one factor, the one that makes the glide drawing `span` cells wide at PX_PER_CELL (the
renderer's `HarrierLook.PX_PER_CELL`), so the body keeps one size from pose to pose. Each pose is then padded so the
centroid of its spine strip (the middle sixth of its width) sits at the image centre: the renderer turns a sprite
about its centre, and a body that sat in a different place in each pose would jump when the wings beat.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "exploration" / "flier"))
import cut_flier  # noqa: E402

ROOT = HERE.parents[1]
SHEETS = HERE / "exploration" / "flier" / "round-1" / "raw"
OUT = ROOT / "mod" / "graphics" / "battle" / "rav-harrier"
PX_PER_CELL = 64
# folder, sheet, span in cells: HarrierLook's values.
LOOKS = [("lilac", "a.png", 2.4), ("manta", "b.png", 2.2), ("scythe", "c.png", 2.3)]


def poses(sheet):
    img = cut_flier.key(Image.open(sheet))
    alpha = np.asarray(img.getchannel("A"))
    out = {}
    for pose, (x0, x1) in zip(cut_flier.POSES, cut_flier.split(alpha)):
        sub = img.crop((x0, 0, x1, img.height))
        box = sub.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox()
        out[pose] = sub.crop(box)
    return out


def centred(img):
    a = np.asarray(img.getchannel("A")).astype(float)
    h, w = a.shape
    strip = a[:, int(w * 5 / 12):int(w * 7 / 12) + 1]
    ys = np.arange(h)[:, None]
    cy = float((strip * ys).sum() / max(1.0, strip.sum()))
    cx = (w - 1) / 2
    half_w = int(np.ceil(max(cx, w - 1 - cx))) + 2
    half_h = int(np.ceil(max(cy, h - 1 - cy))) + 2
    canvas = Image.new("RGBA", (2 * half_w + 1, 2 * half_h + 1), (0, 0, 0, 0))
    canvas.paste(img, (int(round(half_w - cx)), int(round(half_h - cy))))
    return canvas


def main():
    for folder, sheet, span in LOOKS:
        cut = poses(SHEETS / sheet)
        factor = span * PX_PER_CELL / cut["glide"].width
        dest = OUT / folder
        dest.mkdir(parents=True, exist_ok=True)
        for pose, img in cut.items():
            size = (max(1, round(img.width * factor)), max(1, round(img.height * factor)))
            small = img.resize(size, Image.LANCZOS)
            centred(small).save(dest / f"{pose}.png", optimize=True)
            print(f"{folder}/{pose}.png {small.size}")


if __name__ == "__main__":
    main()
