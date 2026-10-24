"""Exports the Spitter's parts (the cobra hood in indigo-black, built as layers the way infantry are) into the sprites
RavSpitterComposer draws, and the acid sheet into its glob, its corpse's pool and its ground splats.

  python export_spitter_rig.py [--preview <png>]

Each part was generated on its own over a guide cut from the whole creature (raw/hero-open-v2.png, guides/part-*.png),
on the same 1024 x 1536 canvas, so every part's pixels share one frame:
  back       raw/part-back.png: the haunched back, chest and shoulders, with a neck stump, closed shoulder sockets
             and a rounded haunch; no head, arms or tail;
  head-open  raw/part-head-open.png: the head with the hood flared and jaws open;
  head-shut  raw/part-head-shut.png: the same head with the hood folded (generated over head-open);
  arm-l/-r   raw/part-arm.png: the whole left arm from its shoulder ball to its single talon, and its mirror;
  tail-0..11 raw/part-tail.png: the straight tail, cut into short slices from the root down, each overlapping the
             next, so it can coil under the haunch and slither in a smooth curve;
  dead       the parts assembled straight, dulled.
Boxes and pivots are canvas pixels; RavSpitterRig and RavSpitterComposer name the same ones. Sprites are written at
SCALE of the canvas into mod/graphics/battle/rav-spitter/.

The acid sheet (raw/acid.png) has four items in its quarters: the glob in flight (turned to travel up, as projectile
sprites do), two splashes and a pool. The glob ships as the acid weapon's projectile sprite and the pool as the bed
a dead Spitter lies in; the splashes and the pool are appended to the decal strip mod/graphics/decals/decals.png as
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
BACK_BOX = (240, 230, 784, 900)
HEAD_BOX = (220, 10, 804, 490)            # both hood states share it
ARM_BOX = (90, 330, 630, 875)             # the left arm; the right is its mirror
TAIL_TOP, TAIL_TIP = 480, 1503            # the tail's root (hidden under the haunch) and its spike's point
TAIL_SLICES = 12
TAIL_SEAMS = tuple(round(TAIL_TOP + (TAIL_TIP - TAIL_TOP) * i / TAIL_SLICES) for i in range(TAIL_SLICES + 1))
TAIL_OVERLAP = 14                         # each slice reaches this far past its seams, so a bend shows no gap
TAIL_COLS = (380, 644)
ARM_SCALE = 0.75                          # the arm was generated large; the composer draws it at this share
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


def mirror_box(box):
    x0, y0, x1, y1 = box
    return (2 * CENTRE_COL - x1, y0, 2 * CENTRE_COL - x0, y1)


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
    back, tail, arm = (keyed(RAW / f"part-{n}.png") for n in ("back", "tail", "arm"))
    layers = {"back": (back, BACK_BOX),
              "head-open": (keyed(RAW / "part-head-open.png"), HEAD_BOX),
              "head-shut": (keyed(RAW / "part-head-shut.png"), HEAD_BOX),
              "arm-l": (arm, ARM_BOX),
              "arm-r": (arm[:, ::-1], mirror_box(ARM_BOX))}
    layers.update(cut_tail(tail))
    return layers, assembled(back, tail, arm, layers["head-shut"][0])


def assembled(back, tail, arm, head):
    """The parts put together straight, as the dead body lies: tail, arms at their sockets, back, folded head."""
    canvas = to_image(np.zeros_like(back))
    canvas.alpha_composite(to_image(tail))
    a = shrink(to_image(arm), ARM_SCALE)
    # the arm's shoulder ball (536, 517) onto the back's left socket (320, 370), and its mirror
    x, y = round(320 - 536 * ARM_SCALE), round(370 - 517 * ARM_SCALE)
    canvas.alpha_composite(a, (x, y))
    canvas.alpha_composite(a.transpose(Image.FLIP_LEFT_RIGHT), (2 * CENTRE_COL - x - a.width, y))
    canvas.alpha_composite(to_image(back))
    canvas.alpha_composite(to_image(head))
    return np.asarray(canvas).astype(float)


def dead(plate):
    """The whole plate dulled: plates greyer and darker, the bone yellowed and dimmed."""
    rgb = plate[..., :3]
    grey = rgb.mean(2, keepdims=True)
    rgb = (rgb * 0.55 + grey * 0.45) * 0.62
    return np.dstack([rgb, plate[..., 3]])


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
    layers["dead"] = (dead(whole), (0, 0, whole.shape[1], whole.shape[0]))
    for name, (rgba, box) in layers.items():
        shrink(to_image(rgba).crop(box)).save(OUT / f"{name}.png", optimize=True)
    glob, splash, splash_long, pool = acid_items()
    shrink(glob, 96 / max(glob.size)).save(OUT / "acid-glob.png", optimize=True)
    shrink(pool, 256 / max(pool.size)).save(OUT / "acid-pool.png", optimize=True)
    width = append_decals([splash, splash_long, pool])
    print("boxes", {k: v[1] for k, v in layers.items()}, "decal strip", width)
    if args.preview:
        sheet = Image.new("RGBA", whole.shape[1::-1], (60, 74, 54, 255))
        sheet.alpha_composite(to_image(whole))
        sheet.convert("RGB").save(args.preview)

if __name__ == "__main__":
    main()
