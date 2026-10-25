"""Exports the Spitter's parts (the Ridge design in indigo-black, built as layers the way infantry are) into the sprites
RavSpitterComposer draws, and the acid sheet into its glob and its ground splats.

  python export_spitter_rig.py [--preview <png>]

Each part was generated on its own over a top-down render of a 3D pose model of that part
(exploration/spitter_pose_model.py, guides/ridge-part-*.png), on the same 1024 x 1536 canvas, so every part's
pixels share one frame:
  back       raw/ridge-body.png: the body from where it rises off the tail to a neck stump;
  head-shut  raw/ridge-head.png: the head, jaws closed;
  head-open  raw/ridge-head-spit.png: the head reared back to spit, crest flared, the lower jaw thrust forward;
  tail-0..11 raw/ridge-tail.png: the straight tail, cut into short slices from the root down, each overlapping the
             next, so it can curl round the base and slither in a smooth curve.
A dead Spitter bursts into these same parts (RavBurst), so it has no corpse sprite of its own.
Boxes and pivots are canvas pixels; RavSpitterRig and RavSpitterComposer name the same ones. Sprites are written at
SCALE of the canvas into mod/graphics/battle/rav-spitter/.

The acid sheet (raw/acid.png) has four items in its quarters: the glob in flight (turned to travel up, as projectile
sprites do), two splashes and a pool. The glob ships as the acid weapon's projectile sprite; the splashes and the
pool are appended to the decal strip mod/graphics/decals/decals.png as
DecalKind ACID_SPLASH, ACID_SPLASH_LONG and ACID_POOL (a rerun rebuilds them from the strip's first 2048 columns).
"""
import argparse
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
RAW = HERE / "raw"
ACID = RAW / "acid.png"
OUT = REPO / "mod" / "graphics" / "battle" / "rav-spitter"
DECALS = REPO / "mod" / "graphics" / "decals" / "decals.png"
SCALE = 0.25

# Canvas pixel geometry (1024 x 1536, facing up), mirrored about CENTRE_COL.
CENTRE_COL = 512
BACK_BOX = (360, 360, 664, 790)           # the body from the tail to the neck
HEAD_BOX = (300, 200, 724, 580)           # both head states share it
TAIL_TOP, TAIL_TIP = 560, 1404            # the tail's root (hidden under the haunch) and its spike's point
TAIL_SLICES = 12
TAIL_SEAMS = tuple(round(TAIL_TOP + (TAIL_TIP - TAIL_TOP) * i / TAIL_SLICES) for i in range(TAIL_SLICES + 1))
TAIL_OVERLAP = 14                         # each slice reaches this far past its seams, so a bend shows no gap
TAIL_COLS = (400, 624)
DECAL_HEIGHT = 160
ORIGINAL_DECAL_WIDTH = 2048


def keyed(path):
    """The plate on a transparent ground, at full canvas size, the grey unmixed from its soft edge."""
    im = Image.open(path).convert("RGB")
    a = np.asarray(im).astype(float)
    corners = np.concatenate([a[:20, :20].reshape(-1, 3), a[:20, -20:].reshape(-1, 3),
                              a[-20:, :20].reshape(-1, 3), a[-20:, -20:].reshape(-1, 3)])
    bg = np.median(corners, axis=0)
    dist = np.sqrt(((a - bg) ** 2).sum(2))
    alpha = np.clip((dist - 12) / 26, 0, 1)
    k = np.maximum(alpha, 1e-3)[..., None]
    rgb = np.clip((a - bg * (1 - alpha[..., None])) / k, 0, 255)
    return np.dstack([rgb, alpha * 255])


def ramp(v, start, length):
    """0 before start, 1 from start + length on, smooth between."""
    t = np.clip((v - start) / length, 0, 1)
    return t * t * (3 - 2 * t)


def to_image(rgba):
    return Image.fromarray(np.clip(rgba, 0, 255).astype(np.uint8), "RGBA")


def shrink(img, scale=SCALE):
    w, h = max(1, round(img.width * scale)), max(1, round(img.height * scale))
    return img.convert("RGBa").resize((w, h), Image.LANCZOS).convert("RGBA")


def masked(plate, mask):
    return plate * np.dstack([np.ones(mask.shape + (3,)), mask])


def cut_tail(tail):
    """The straight tail cut into overlapping slices, root first."""
    h, w = tail.shape[:2]
    rows = np.arange(h)[:, None] * np.ones((1, w))
    slices = {}
    last = TAIL_SLICES - 1
    for i in range(TAIL_SLICES):
        top, bottom = TAIL_SEAMS[i], TAIL_SEAMS[i + 1]
        m = ramp(rows, top - TAIL_OVERLAP, TAIL_OVERLAP / 2) if i else np.ones((h, w))
        if i < last:
            m = m * (1 - ramp(rows, bottom + TAIL_OVERLAP / 2, TAIL_OVERLAP / 2))
        # symmetric about the slice's middle, so the composer centres it there; the tip's box runs off the canvas
        reach = TAIL_OVERLAP if i < last else h - bottom
        slices[f"tail-{i}"] = (masked(tail, m), (TAIL_COLS[0], top - reach, TAIL_COLS[1], bottom + reach))
    return slices


def parts():
    back, tail = (keyed(RAW / f"ridge-{n}.png") for n in ("body", "tail"))
    layers = {"back": (back, BACK_BOX),
              "head-open": (keyed(RAW / "ridge-head-spit.png"), HEAD_BOX),
              "head-shut": (keyed(RAW / "ridge-head.png"), HEAD_BOX)}
    layers.update(cut_tail(tail))
    return layers, assembled(back, tail, layers["head-shut"][0])


def assembled(back, tail, head):
    """The parts put together straight, for the preview: tail, back, closed head."""
    canvas = to_image(np.zeros_like(back))
    canvas.alpha_composite(to_image(tail))
    canvas.alpha_composite(to_image(back))
    canvas.alpha_composite(to_image(head))
    return np.asarray(canvas).astype(float)


def acid_items():
    """The acid sheet's four items, each keyed and cropped: glob (travelling up), splash, long splash, pool."""
    sheet = keyed(ACID)
    h, w = sheet.shape[:2]
    items = []
    for qx, qy in ((0, 0), (1, 0), (0, 1), (1, 1)):
        q = to_image(sheet[qy * h // 2:(qy + 1) * h // 2, qx * w // 2:(qx + 1) * w // 2])
        box = q.getchannel("A").point(lambda v: 255 if v > 40 else 0).getbbox()
        items.append(q.crop(box))
    items[0] = items[0].rotate(-90, expand=True, resample=Image.BICUBIC)   # tail right, flying left -> flying up
    return items


def append_decals(splats):
    """The splats appended to the decal strip after its original frames, each fitted to the strip's height."""
    strip = Image.open(DECALS).convert("RGBA")
    base = strip.crop((0, 0, ORIGINAL_DECAL_WIDTH, strip.height))
    frames = [shrink(s, (DECAL_HEIGHT - 8) / max(s.width, s.height)) for s in splats]
    gap = 8
    width = ORIGINAL_DECAL_WIDTH + sum(f.width + gap for f in frames)
    out = Image.new("RGBA", (width, DECAL_HEIGHT), (0, 0, 0, 0))
    out.alpha_composite(base, (0, 0))
    x = ORIGINAL_DECAL_WIDTH + gap
    for f in frames:
        out.alpha_composite(f, (x, (DECAL_HEIGHT - f.height) // 2))
        x += f.width + gap
    out.save(DECALS, optimize=True)
    return width


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    args = ap.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    for stale in OUT.glob("*.png"):
        stale.unlink()
    layers, whole = parts()
    for name, (rgba, box) in layers.items():
        shrink(to_image(rgba).crop(box)).save(OUT / f"{name}.png", optimize=True)
    glob, splash, splash_long, pool = acid_items()
    shrink(glob, 96 / max(glob.size)).save(OUT / "acid-glob.png", optimize=True)
    width = append_decals([splash, splash_long, pool])
    print("boxes", {k: v[1] for k, v in layers.items()}, "decal strip", width)
    if args.preview:
        sheet = Image.new("RGBA", whole.shape[1::-1], (60, 74, 54, 255))
        sheet.alpha_composite(to_image(whole))
        sheet.convert("RGB").save(args.preview)

if __name__ == "__main__":
    main()
