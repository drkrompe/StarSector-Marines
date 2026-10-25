"""Exports the Bombard's parts from the picked master (raw/bombard5-withered.png, G1) into the sprites
RavBombardComposer draws, and its glob into the spore-glob projectile sprite.

  python export_bombard_rig.py [--preview <png>]

The master holds two frames side by side; the parts are cut from the right one, the release, which has the tail
laid straight out behind and the glob in flight above it. The wind-up (the left frame) is a pose of the same
parts: RavBombardRig curls the tail slices up over the back.
  legs-l, legs-r  the walking legs and withered claws of each side, with the shell's rim under them, so a leg
                  swung a few degrees about its hip shows no gap at the body's edge;
  body            the carapace, the split shell over the amber sac, the head and its short antennae;
  tail-0..5       the segmented tail from its root (hidden under the shell's rear) to the sling, in overlapping
                  slices, so it can curl over the back in a smooth curve;
  sling           the bony sling cup at the tail's end;
  glob            the glob in flight with its drip, travelling up, as projectile sprites do.
A dead Bombard bursts into these same parts (RavBurst), so it has no corpse sprite of its own.
Boxes and pivots are canvas pixels of the 1536 x 1024 master; RavBombardRig names the same ones. Sprites are
written at SCALE of the canvas into mod/graphics/battle/rav-bombard/.
"""
import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
sys.path.insert(0, str(HERE.parent / "spitter"))
from export_spitter_rig import keyed, ramp, to_image, shrink, masked  # noqa: E402

MASTER = HERE / "raw" / "bombard5-withered.png"
OUT = REPO / "mod" / "graphics" / "battle" / "rav-bombard"
GLOB_OUT = OUT / "glob.png"
SCALE = 1.0

# Canvas pixel geometry (the release frame, facing up).
CENTRE_COL = 1078
BODY_CENTRE = (1078, 445)
BODY_RADII = (142, 272)                    # the carapace ellipse
ANTENNAE = [(985, 158), (1175, 158), (1150, 300), (1006, 300)]
BODY_BOX = (930, 150, 1226, 730)
LEGS_BOTTOM = 712                          # the hind legs' last row; below it is the tail
LEGS_UNDER = 16                            # px of the shell's rim kept under the legs
LEGS_L_BOX = (800, 180, CENTRE_COL, 730)
LEGS_R_BOX = (CENTRE_COL, 180, 1356, 730)
TAIL_TOP, TAIL_SLING = 680, 860            # the tail's root (under the shell's rear) and the sling's hinge
TAIL_SLICES = 6
TAIL_SEAMS = tuple(round(TAIL_TOP + (TAIL_SLING - TAIL_TOP) * i / TAIL_SLICES) for i in range(TAIL_SLICES + 1))
TAIL_OVERLAP = 10
TAIL_HALF = 58                             # half the tail's width, px
SLING_BOX = (1003, TAIL_SLING - TAIL_OVERLAP, 1153, 1014)
GLOB_BOX = (1036, 8, 1120, 160)


def body_mask(h, w):
    yy, xx = np.mgrid[0:h, 0:w]
    cx, cy = BODY_CENTRE
    rx, ry = BODY_RADII
    ellipse = ((xx - cx) / rx) ** 2 + ((yy - cy) / ry) ** 2 <= 1.0
    poly = Image.new("L", (w, h), 0)
    from PIL import ImageDraw
    ImageDraw.Draw(poly).polygon(ANTENNAE, fill=255)
    m = Image.fromarray(((ellipse | (np.asarray(poly) > 0)) * 255).astype(np.uint8))
    return m


def feathered(mask_img, grow=0, feather=2):
    m = mask_img
    if grow > 0:
        m = m.filter(ImageFilter.MaxFilter(2 * grow + 1))
    elif grow < 0:
        m = m.filter(ImageFilter.MinFilter(2 * -grow + 1))
    return np.asarray(m.filter(ImageFilter.GaussianBlur(feather))).astype(float) / 255


def parts():
    plate = keyed(MASTER)
    h, w = plate.shape[:2]
    cols = np.arange(w)[None, :] * np.ones((h, 1))
    rows = np.arange(h)[:, None] * np.ones((1, w))
    frame = (cols >= w // 2).astype(float)              # the release frame only
    body = body_mask(h, w)
    in_body = feathered(body, 0, 1.5)
    under = feathered(body, -LEGS_UNDER, 2)            # the shell less its rim
    above_tail = 1 - ramp(rows, LEGS_BOTTOM - 4, 8)
    legs = frame * above_tail * (1 - under)
    left = legs * (1 - ramp(cols, CENTRE_COL - 2, 4))
    right = legs * ramp(cols, CENTRE_COL - 2, 4)
    layers = {
        "legs-l": (masked(plate, left), LEGS_L_BOX),
        "legs-r": (masked(plate, right), LEGS_R_BOX),
        "body": (masked(plate, frame * in_body), BODY_BOX),
    }
    width = 1 - ramp(np.abs(cols - CENTRE_COL), TAIL_HALF - 4, 8)
    for i in range(TAIL_SLICES):
        top, bottom = TAIL_SEAMS[i], TAIL_SEAMS[i + 1]
        m = ramp(rows, top - TAIL_OVERLAP, TAIL_OVERLAP / 2) if i else np.ones((h, w))
        m = m * (1 - ramp(rows, bottom + TAIL_OVERLAP / 2, TAIL_OVERLAP / 2))
        box = (CENTRE_COL - TAIL_HALF, top - TAIL_OVERLAP, CENTRE_COL + TAIL_HALF, bottom + TAIL_OVERLAP)
        layers[f"tail-{i}"] = (masked(plate, frame * width * m), box)
    sling = frame * ramp(rows, TAIL_SLING - TAIL_OVERLAP, TAIL_OVERLAP / 2)
    layers["sling"] = (masked(plate, sling), SLING_BOX)
    glob = frame * (1 - ramp(rows, GLOB_BOX[3] - 6, 6))
    layers["glob"] = (masked(plate, glob), GLOB_BOX)
    return layers, plate


def crop(rgba, box):
    return to_image(rgba).crop(box)


def preview(layers, path):
    """The parts assembled straight in draw order: legs, tail, sling, body."""
    canvas = Image.new("RGBA", (1536, 1024), (60, 60, 60, 255))
    for name in ("legs-l", "legs-r") + tuple(f"tail-{i}" for i in range(TAIL_SLICES)) + ("sling", "body"):
        rgba, box = layers[name]
        canvas.alpha_composite(crop(rgba, box), (box[0], box[1]))
    canvas.crop((760, 0, 1400, 1024)).save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    args = ap.parse_args()
    layers, _ = parts()
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (rgba, box) in layers.items():
        img = shrink(crop(rgba, box), SCALE)
        img.save(OUT / f"{name}.png", optimize=True)
        print(f"{name}: box {box} -> {img.width}x{img.height}")
    print(f"RavBombardRig: CENTRE_COL {CENTRE_COL}, BODY_ROW {(BODY_BOX[1] + BODY_BOX[3]) / 2}, "
          f"LEGS rows {(LEGS_L_BOX[1] + LEGS_L_BOX[3]) / 2}, TAIL_SEAMS {TAIL_SEAMS}, SLING centre row "
          f"{(SLING_BOX[1] + SLING_BOX[3]) / 2} col {(SLING_BOX[0] + SLING_BOX[2]) / 2}")
    if args.preview:
        preview(layers, args.preview)


if __name__ == "__main__":
    main()
