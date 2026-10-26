"""Exports the Bombard's parts from the picked master (raw/bombard5-withered.png, G1) into the sprites
RavBombardComposer draws, and its glob into the spore-glob projectile sprite.

  python export_bombard_rig.py [--preview <png>]

The master holds two frames side by side; the parts are cut from the right one, the release, which has the tail
laid straight out behind and the glob in flight above it. The wind-up (the left frame) is a pose of the same
parts: RavBombardRig curls the tail slices up over the back.
  leg-l0..3, leg-r0..3  each leg on its own, front to back: 0 the feeler claw beside the head, 1 to 3 the
                  walking legs. Each is the leg's own piece of the plate (they touch only at the shell's rim) with
                  the rim under its root, so a leg swung about its hip shows no gap at the body's edge. The script
                  prints each leg's box centre, hip and foot (claw tip) for RavBombardRig;
  body            the carapace, the split shell over the amber sac, the head and its short antennae;
  tail-0..5       the segmented tail from its root (hidden under the shell's rear) to the sling, in overlapping
                  slices, so it can curl over the back in a smooth curve;
  sling           the bony sling cup at the tail's end;
  glob            the glob in flight with its drip, travelling up, as projectile sprites do;
  shell-l, shell-r  the body cut in two along a ragged crack down its seam (CRACK, a fixed zigzag that wanders off
                  the middle), for the death: the shell cracks open along the split
                  over the sac and its halves fall apart.
A dead Bombard falls apart into these same parts, so it has no corpse sprite of its own.
Boxes and pivots are canvas pixels of the 1536 x 1024 master; RavBombardRig names the same ones. Sprites are
written at SCALE of the canvas into mod/graphics/battle/rav-bombard/.
"""
import argparse
from collections import deque
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
LEG_MIN_PX = 800                           # a piece of the plate smaller than this is no leg
LEG_ROOT = 20                              # px the leg's piece grows back under the rim at its root
# The crack the dead shell splits along: (row, column) points top to bottom, a ragged zigzag that wanders off the
# seam, so the halves come apart unevenly. Both halves' boxes take in its widest swing.
CRACK = [(150, 1074), (196, 1086), (232, 1066), (270, 1080), (318, 1090), (352, 1071), (398, 1084), (446, 1060),
         (486, 1078), (530, 1095), (566, 1072), (610, 1088), (652, 1066), (694, 1083), (730, 1076)]
CRACK_SWING = 20
SHELL_L_BOX = (BODY_BOX[0], BODY_BOX[1], CENTRE_COL + CRACK_SWING, BODY_BOX[3])
SHELL_R_BOX = (CENTRE_COL - CRACK_SWING, BODY_BOX[1], BODY_BOX[2], BODY_BOX[3])
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


def components(mask):
    """Each 4-connected piece of a boolean mask over LEG_MIN_PX, as an array of (row, col)."""
    h, w = mask.shape
    seen = np.zeros((h, w), bool)
    pieces = []
    for y0, x0 in zip(*np.nonzero(mask)):
        if seen[y0, x0]:
            continue
        seen[y0, x0] = True
        queue, pts = deque([(y0, x0)]), []
        while queue:
            y, x = queue.popleft()
            pts.append((y, x))
            for yy, xx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
                if 0 <= yy < h and 0 <= xx < w and mask[yy, xx] and not seen[yy, xx]:
                    seen[yy, xx] = True
                    queue.append((yy, xx))
        if len(pts) > LEG_MIN_PX:
            pieces.append(np.array(pts))
    return pieces


def leg_layers(plate, frame, body):
    """Each leg on its own: the plate's piece of it outside the shell, grown back under the rim at its root."""
    h, w = plate.shape[:2]
    shell = np.asarray(body) > 0
    outside = (plate[..., 3] > 60) & ~shell & (frame > 0)
    outside[LEGS_BOTTOM:, :] = False
    outside[:GLOB_BOX[3] + 10, :] = False                 # the glob in flight above the head
    ring = shell & ~(np.asarray(body.filter(ImageFilter.MinFilter(2 * LEGS_UNDER + 1))) > 0)
    near_shell = np.asarray(body.filter(ImageFilter.MaxFilter(9))) > 0
    layers, report = {}, []
    pieces = components(outside)
    for side, on_side in (("l", lambda c: c < CENTRE_COL), ("r", lambda c: c >= CENTRE_COL)):
        legs = sorted((p for p in pieces if on_side(p[:, 1].mean())), key=lambda p: p[:, 0].min())
        for i, pts in enumerate(legs):
            own = np.zeros((h, w), bool)
            own[pts[:, 0], pts[:, 1]] = True
            grown = np.asarray(Image.fromarray((own * 255).astype(np.uint8))
                               .filter(ImageFilter.MaxFilter(2 * LEG_ROOT + 1))) > 0
            soft = np.asarray(Image.fromarray(((own | (grown & ring)) * 255).astype(np.uint8))
                              .filter(ImageFilter.MaxFilter(3)).filter(ImageFilter.GaussianBlur(1))) / 255.0
            ys, xs = np.nonzero(soft > 0.02)
            box = (int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1)
            # the hip: where the leg leaves the shell; the foot: its point farthest from the hip
            edge = pts[near_shell[pts[:, 0], pts[:, 1]]]
            hip = edge.mean(axis=0) if len(edge) else pts.mean(axis=0)
            far = pts[np.argmax(((pts - hip) ** 2).sum(axis=1))]
            name = f"leg-{side}{i}"
            layers[name] = (masked(plate, soft), box)
            report.append((name, (box[0] + box[2]) / 2, (box[1] + box[3]) / 2, hip[1], hip[0], far[1], far[0]))
    return layers, report


def parts():
    plate = keyed(MASTER)
    h, w = plate.shape[:2]
    cols = np.arange(w)[None, :] * np.ones((h, 1))
    rows = np.arange(h)[:, None] * np.ones((1, w))
    frame = (cols >= w // 2).astype(float)              # the release frame only
    body = body_mask(h, w)
    in_body = feathered(body, 0, 1.5)
    legs, report = leg_layers(plate, frame, body)
    crack = np.interp(rows, [r for r, _ in CRACK], [c for _, c in CRACK])
    layers = {
        "body": (masked(plate, frame * in_body), BODY_BOX),
        "shell-l": (masked(plate, frame * in_body * (1 - ramp(cols, crack - 1, 2))), SHELL_L_BOX),
        "shell-r": (masked(plate, frame * in_body * ramp(cols, crack - 1, 2)), SHELL_R_BOX),
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
    layers.update(legs)
    return layers, plate, report


def crop(rgba, box):
    return to_image(rgba).crop(box)


def preview(layers, path):
    """The parts assembled straight in draw order: legs, tail, sling, body."""
    canvas = Image.new("RGBA", (1536, 1024), (60, 60, 60, 255))
    for name in tuple(n for n in layers if n.startswith("leg-")) + tuple(f"tail-{i}" for i in range(TAIL_SLICES)) + ("sling", "body"):
        rgba, box = layers[name]
        canvas.alpha_composite(crop(rgba, box), (box[0], box[1]))
    canvas.crop((760, 0, 1400, 1024)).save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preview")
    args = ap.parse_args()
    layers, _, report = parts()
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (rgba, box) in layers.items():
        img = shrink(crop(rgba, box), SCALE)
        img.save(OUT / f"{name}.png", optimize=True)
        print(f"{name}: box {box} -> {img.width}x{img.height}")
    print(f"RavBombardRig: CENTRE_COL {CENTRE_COL}, BODY_ROW {(BODY_BOX[1] + BODY_BOX[3]) / 2}, "
          f"TAIL_SEAMS {TAIL_SEAMS}, SLING centre row "
          f"{(SLING_BOX[1] + SLING_BOX[3]) / 2} col {(SLING_BOX[0] + SLING_BOX[2]) / 2}")
    for name, bx, by, hx, hy, fx, fy in report:
        print(f"{name}: centre ({bx:.0f}, {by:.0f}) hip ({hx:.0f}, {hy:.0f}) foot ({fx:.0f}, {fy:.0f})")
    if args.preview:
        preview(layers, args.preview)


if __name__ == "__main__":
    main()
