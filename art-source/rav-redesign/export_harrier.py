"""Export the Harrier (Rav flier) look into mod/graphics/battle/rav-harrier/.

python art-source/rav-redesign/export_harrier.py

Three sources. The round-1 concept sheet (glide, flap, dive on grey) gives the glide drawing, which is wingbeat frame
0; exploration/flier/cut_flier.py cuts it. Two ImageGen reskins of make_guide.py's guides (exploration/flier/wingbeat/)
give the rest: raw/wingbeat.png the other beat frames, raw/dive.png the two frames sweeping the wings back, the dive
itself (wings still showing, owner 2026-10-23), four tumbling fall frames and two corpses, which are graded duller.

Every drawing is scaled by one factor, the one that makes the glide drawing `span` cells wide at PX_PER_CELL (the
renderer's `HarrierLook.PX_PER_CELL`), so the body keeps one size from frame to frame.

Every frame but the glide is body-locked: each is cut from its guide cell by the guide's own numbers (never by
looking for gaps), registered on its guide, and only its wings are kept (the generated pixels inside the guide's
wings, grown a little); the glide drawing's own body is laid over them. So the body is the same pixels in every
frame and only the wings move. All of them share one canvas, centred on the glide's spine centroid, so the
renderer, which turns a sprite about its centre, turns every frame about the same point of the body.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
FLIER = HERE / "exploration" / "flier"
sys.path.insert(0, str(FLIER))
sys.path.insert(0, str(FLIER / "wingbeat"))
import cut_flier  # noqa: E402
import make_guide  # noqa: E402

ROOT = HERE.parents[1]
SHEETS = FLIER / "round-1" / "raw"
WINGBEAT = FLIER / "wingbeat" / "raw" / "wingbeat.png"
DIVE = FLIER / "wingbeat" / "raw" / "dive.png"
OUT = ROOT / "mod" / "graphics" / "battle" / "rav-harrier"
PX_PER_CELL = 64
# folder, sheet, span in cells: HarrierLook's values.
# The owner picked A, the lilac glider (2026-10-23); sheets b and c stay in raw/ as the round's record.
LOOKS = [("lilac", "a.png", 2.4)]
# How far round a guide's wings a generated frame's pixels are kept, and how far it may be shifted to register.
WING_GROW = 12
REGISTER = 10
# The corpse grade.
DEAD_DESATURATE = 0.35
DEAD_DARKEN = 0.78


def poses(sheet):
    img = cut_flier.key(Image.open(sheet))
    alpha = np.asarray(img.getchannel("A"))
    out = {}
    for pose, (x0, x1) in zip(cut_flier.POSES, cut_flier.split(alpha)):
        sub = img.crop((x0, 0, x1, img.height))
        box = sub.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox()
        out[pose] = (sub.crop(box), x0 + box[0], box[1])
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
    return canvas, cx, cy


def register(gen_alpha, guide_alpha):
    """The shift of a generated cell that best covers its guide frame, and the overlap (IoU) there."""
    g = gen_alpha > 0.3
    t = guide_alpha > 0.3
    best = (0, 0, -1.0)
    for dy in range(-REGISTER, REGISTER + 1, 2):
        for dx in range(-REGISTER, REGISTER + 1, 2):
            s = np.roll(np.roll(g, dy, axis=0), dx, axis=1)
            iou = (s & t).sum() / max(1, (s | t).sum())
            if iou > best[2]:
                best = (dx, dy, iou)
    return best


def sheet_frames(sheet, raw_path, keep_names):
    """The named frames of one guide sheet at guide-cell scale: the generated wings under the glide body."""
    guide = Image.open(FLIER / "wingbeat" / f"guide-{sheet}.png")
    raw = cut_flier.key(Image.open(raw_path)).resize(guide.size, Image.LANCZOS)
    body = make_guide.to_cell(make_guide.body_layer())
    cw, ch = make_guide.CELL
    names = dict(make_guide.SHEETS)[sheet]
    frames = {}
    for i, name in enumerate(names):
        if name not in keep_names:
            continue
        x0, y0 = (i % make_guide.COLUMNS) * cw, (i // make_guide.COLUMNS) * ch
        cell = raw.crop((x0, y0, x0 + cw, y0 + ch))
        wings = make_guide.wings_cell(name)
        guide_frame = make_guide.frame(name, body)
        dx, dy, iou = register(np.asarray(cell.getchannel("A")) / 255.0,
                               np.asarray(guide_frame.getchannel("A")) / 255.0)
        shifted = Image.new("RGBA", cell.size, (0, 0, 0, 0))
        shifted.paste(cell, (dx, dy))
        keep = wings.getchannel("A").point(lambda v: 255 if v > 20 else 0) \
            .filter(ImageFilter.MaxFilter(2 * WING_GROW + 1)).filter(ImageFilter.GaussianBlur(1.5))
        a = np.asarray(shifted).astype(float)
        a[..., 3] *= np.asarray(keep).astype(float) / 255.0
        out = Image.fromarray(a.astype(np.uint8), "RGBA")
        out.alpha_composite(body)
        if name.startswith("corpse"):
            out = dead(out)
        frames[name] = out
        print(f"{name}: registered by ({dx}, {dy}), IoU {iou:.3f} against its guide")
    return frames


def dead(img):
    """A corpse's grade: the colour drained a third and the whole darkened, so it lies dull under the living."""
    a = np.asarray(img).astype(float)
    rgb = a[..., :3]
    grey = rgb @ np.array([0.299, 0.587, 0.114])
    rgb = (rgb * (1 - DEAD_DESATURATE) + grey[..., None] * DEAD_DESATURATE) * DEAD_DARKEN
    a[..., :3] = np.clip(rgb, 0, 255)
    return Image.fromarray(a.astype(np.uint8), "RGBA")


def main():
    for folder, sheet, span in LOOKS:
        cut = poses(SHEETS / sheet)
        glide, gx0, gy0 = cut["glide"]
        factor = span * PX_PER_CELL / glide.width
        dest = OUT / folder
        dest.mkdir(parents=True, exist_ok=True)

        # Frame 0 is the glide drawing itself, placed in a guide cell like the others.
        keyed = cut_flier.key(Image.open(SHEETS / sheet)).crop((0, 0, 1100, 809))
        frames = {"glide": make_guide.to_cell(keyed)}
        frames.update(sheet_frames("all", WINGBEAT, [f"beat-{i}" for i in range(1, 8)]))
        frames.update(sheet_frames("dive", DIVE, ["fold-1", "fold-2", "dive", "fall-1", "fall-2", "fall-3",
                                                  "fall-4", "corpse-1", "corpse-2"]))

        # The shared pivot: the glide's spine centroid, in cell coordinates.
        _, _, cy = centred(glide)
        ax = make_guide.CELL[0] / 2
        ay = round((gy0 + cy - make_guide.BODY_TOP) * make_guide.SCALE)
        half_w = half_h = 0.0
        for img in frames.values():
            l, t, r, b = img.getchannel("A").point(lambda v: 255 if v > 8 else 0).getbbox()
            half_w = max(half_w, ax - l, r - ax)
            half_h = max(half_h, ay - t, b - ay)
        half_w, half_h = np.ceil(half_w) + 3, np.ceil(half_h) + 3
        k = factor / make_guide.SCALE
        size = (int(round(2 * half_w * k)) | 1, int(round(2 * half_h * k)) | 1)
        for name, img in frames.items():
            window = img.crop((int(ax - half_w), int(ay - half_h), int(ax + half_w), int(ay + half_h)))
            window.resize(size, Image.LANCZOS).save(dest / f"{name}.png", optimize=True)
            print(f"{folder}/{name}.png {size}")


if __name__ == "__main__":
    main()