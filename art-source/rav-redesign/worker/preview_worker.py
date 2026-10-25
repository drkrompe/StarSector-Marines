"""Compose a Runner-skeleton bone set at the rig's standing pose, exactly as `RavRunnerComposer` draws it, so a
bone set's registration and size can be checked without the game.

  python preview_worker.py <out.png> [--set <label>=<bone dir> ...] [--ppc 32] [--zoom 4]

Draws the Runner (rav-runner/) beside each --set (default: the live rav-worker/ set) at true relative size: the worker at WORKER_SCALE of a Runner. Row 1 is the play zoom at 1:1, row 2 the same pixels
blown up `--zoom` times with nearest sampling, row 3 a crisp render at ppc * zoom.

The standing pose is `RavRunnerRig` in PLANT with no sway, bend or crouch: shoulders at (0, +SPINE/2, 0.40), hips at
(0, -SPINE/2, 0.38), each foot under its root, legs solved by the rig's own two-bone IK.
"""
import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).parent
ROOT = HERE.parent.parent.parent
GRAPHICS = ROOT / "mod" / "graphics" / "battle"

# RavRunnerComposer
TRUNK_HALF_PX, FORE_PX = 41.7, 17.4
FRONT_PX = [116, 120]
HIND_PX = [70, 84, 86]
ARM_PX = [118, 88, 45.8]
BODY_LAYER_PX_PER_CELL = (958 - 541) / 0.5
FORE_CELLS = (420 - 333) / BODY_LAYER_PX_PER_CELL
NECK_ROOT_CELLS = (541 - 420) / BODY_LAYER_PX_PER_CELL
LEG_WIDTH, ARM_WIDTH = 1.25, 1.1
LEG_ORDER = [2, 3, 0, 1]
WORKER_SCALE = 0.68

# RavRunnerRig
SPINE, SHOULDER_Z, HIP_Z = 0.50, 0.40, 0.38
FRONT_X, FRONT_A, FRONT_B = 0.20, 0.22, 0.22
HIND_X, HIND_A, HIND_B, HIND_C = 0.20, 0.19, 0.20, 0.12
ARM_X, ARM_UP, ARM_A, ARM_B, ARM_SPIKE = 0.12, 0.07, 0.30, 0.30, 0.18
G = ARM_A / 0.17
REST = [0.0, 0.15 * G, 0.10 * G, 0.0]


def ik2(ry, rz, ty, tz, a, b, bend):
    dy, dz = ty - ry, tz - rz
    d = max(abs(a - b) + 1e-4, min(a + b - 1e-4, math.hypot(dy, dz)))
    base = math.atan2(dz, dy)
    off = math.acos(max(-1, min(1, (a * a + d * d - b * b) / (2 * a * d))))
    j1 = (ry + a * math.cos(base + off), rz + a * math.sin(base + off))
    j2 = (ry + a * math.cos(base - off), rz + a * math.sin(base - off))
    j = j1 if bend * j1[0] >= bend * j2[0] else j2
    return j[0], j[1], ry + d * math.cos(base), rz + d * math.sin(base)


STRIDE = [(0.16, 0.0), (-0.12, 0.06), (-0.14, 0.0), (0.12, 0.05)]   # (foot along, foot up) per leg, mid-trot


def standing_pose(feet=None):
    """The rig's points at rest (or with each foot moved by `feet`, a mid-stride check of the legs): legs [FL, FR, HL, HR] as point lists, arms as point lists, and the spine."""
    shY, hpY, shZ, hpZ = SPINE / 2, -SPINE / 2, SHOULDER_Z, HIP_Z
    legs = []
    for i in range(4):
        front = i < 2
        side = -1 if i % 2 == 0 else 1
        rx = side * (FRONT_X if front else HIND_X)
        ry, rz = (shY, shZ - 0.02) if front else (hpY, hpZ - 0.02)
        fx, fy, fz = rx, (SPINE / 2 if front else -SPINE / 2), 0.0
        if feet:
            fy, fz = fy + feet[i][0], feet[i][1]
        if front:
            j = ik2(ry, rz, fy, fz, FRONT_A, FRONT_B, -1)
            legs.append([(rx, ry, rz), ((rx + fx) / 2, j[0], j[1]), (fx, j[2], j[3])])
        else:
            j = ik2(ry, rz, fy - 0.03, fz + HIND_C, HIND_A, HIND_B, 1)
            legs.append([(rx, ry, rz), (rx, j[0], j[1]), (rx, j[2], j[3]), (fx, fy, fz)])
    arms = []
    for a in range(2):
        side = -1 if a == 0 else 1
        b = (side * ARM_X, shY - 0.02, shZ + ARM_UP)
        j = ik2(0, 0, REST[1], REST[2], ARM_A, ARM_B, -1)
        e = (b[0], b[1] + j[0], b[2] + j[1])
        w = (b[0], b[1] + j[2], b[2] + j[3])
        dx, dy, dz = w[0] - e[0], w[1] - e[1], w[2] - e[2]
        n = math.sqrt(dx * dx + dy * dy + dz * dz) or 1
        arms.append([b, e, w, (w[0] + ARM_SPIKE * dx / n, w[1] + ARM_SPIKE * dy / n, w[2] + ARM_SPIKE * dz / n)])
    return dict(legs=legs, arms=arms, shoulder=(0, shY, shZ), hip=(0, hpY, hpZ), mid=(0, 0, (shZ + hpZ) / 2),
                head=(0, shY + 0.25 + 0.0, shZ - 0.01))


def load(folder):
    def im(name):
        return Image.open(folder / f"{name}.png").convert("RGBA")
    art = dict(trunkFront=im("trunk-front"), trunkRear=im("trunk-rear"), fore=im("fore"),
               front=[[im(f"front-{i}-l"), im(f"front-{i}-r")] for i in range(2)],
               hind=[[im(f"hind-{i}-l"), im(f"hind-{i}-r")] for i in range(3)])
    if (folder / "arm-0-l.png").exists():
        art["arm"] = [[im(f"arm-{i}-l"), im(f"arm-{i}-r")] for i in range(3)]
    return art


def bone(canvas, sprite, bone_px, p0, p1, full, width_mul, lift, min_along):
    """`RavRunnerComposer.bone` in a y-up frame; canvas rows run down, so y is flipped on paste."""
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    length = math.hypot(dx, dy)
    if length > 1e-4:
        angle = math.degrees(math.atan2(-dx, dy))
        ux, uy = dx / length, dy / length
    else:
        angle, ux, uy = 0.0, 0.0, 1.0
    across = full / bone_px * width_mul
    along = max(min_along * full, length) / bone_px * width_mul
    to_centre = bone_px / 2 * along
    cx, cy = p0[0] + ux * to_centre, p0[1] + uy * to_centre
    w = max(1, round(sprite.width * across * lift))
    h = max(1, round(sprite.height * along))
    s = sprite.resize((w, h), Image.LANCZOS).rotate(angle, expand=True, resample=Image.BICUBIC)
    canvas.alpha_composite(s, (int(round(cx - s.width / 2)), int(round(canvas.height - cy - s.height / 2))))


def compose(canvas, art, cx, cy, ppc, feet=None):
    """Draw art standing facing up (north) with its rig centre at canvas (cx, cy), ppc screen px per cell."""
    pose = standing_pose(feet)
    y_up = canvas.height - cy

    def scr(p, add_y=0.0):
        return cx + p[0] * ppc, y_up + (p[1] + add_y) * ppc

    def d3(a, b):
        return math.dist(a, b)

    for leg in LEG_ORDER:
        front = leg < 2
        side = leg % 2
        pts = pose["legs"][leg]
        lift = 1 + 1.5 * pts[-1][2]
        for i in range(len(pts) - 1):
            spr = (art["front"] if front else art["hind"])[i][side]
            px = (FRONT_PX if front else HIND_PX)[i]
            bone(canvas, spr, px, scr(pts[i]), scr(pts[i + 1]), d3(pts[i], pts[i + 1]) * ppc, LEG_WIDTH,
                 lift if i == len(pts) - 2 else 1.0, 0.2)
    half = SPINE / 2 * ppc
    bone(canvas, art["trunkRear"], TRUNK_HALF_PX, scr(pose["mid"]), scr(pose["hip"]), half, 1, 1, 0.2)
    bone(canvas, art["trunkFront"], TRUNK_HALF_PX, scr(pose["mid"]), scr(pose["shoulder"]), half, 1, 1, 0.2)
    p0 = scr(pose["shoulder"], NECK_ROOT_CELLS)
    p1 = scr(pose["head"])
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    d = math.hypot(dx, dy)
    full = FORE_CELLS * ppc
    p1 = (p0[0] + dx / d * full, p0[1] + dy / d * full)
    bone(canvas, art["fore"], FORE_PX, p0, p1, full, 1, 1, 0.2)
    if "arm" in art:
        for a in range(2):
            pts = pose["arms"][a]
            for i in range(3):
                bone(canvas, art["arm"][i][a], ARM_PX[i], scr(pts[i]), scr(pts[i + 1]),
                     d3(pts[i], pts[i + 1]) * ppc, ARM_WIDTH, 1, 0.2)


def ground(w, h):
    img = Image.new("RGBA", (w, h), (74, 70, 62, 255))
    d = ImageDraw.Draw(img)
    for x in range(0, w, 16):
        for y in range(0, h, 16):
            if (x // 16 + y // 16) % 2:
                d.rectangle([x, y, x + 15, y + 15], fill=(80, 76, 67, 255))
    return img


def sheet(out, sets, ppc=32, zoom=4):
    """sets: [(label, art, scale)]. One column per set; rows: 1:1, nearest x zoom, crisp at ppc * zoom standing and
    mid-stride."""
    cell1 = int(ppc * 2.2)
    col = cell1 * zoom
    W, H = col * len(sets) + 20, 40 + cell1 + 30 + col + 30 + col + 10 + col + 20
    img = Image.new("RGBA", (W, H), (22, 24, 30, 255))
    d = ImageDraw.Draw(img)
    for k, (label, art, scale) in enumerate(sets):
        x0 = 10 + k * col
        d.text((x0 + 4, 8), f"{label}  ({scale:.2f}x)", fill=(230, 230, 230, 255))
        small = ground(cell1, cell1)
        compose(small, art, cell1 / 2, cell1 / 2 + 0.05 * ppc, ppc * scale)
        img.alpha_composite(small, (x0 + (col - cell1) // 2, 40))
        img.alpha_composite(small.resize((col, col), Image.NEAREST), (x0, 40 + cell1 + 30))
        big = ground(col, col)
        compose(big, art, col / 2, col / 2 + 0.05 * ppc * zoom, ppc * zoom * scale)
        img.alpha_composite(big, (x0, 40 + cell1 + 30 + col + 30))
        big = ground(col, col)
        compose(big, art, col / 2, col / 2 + 0.05 * ppc * zoom, ppc * zoom * scale, STRIDE)
        img.alpha_composite(big, (x0, 40 + cell1 + 30 + col + 30 + col + 10))
    d.text((10, 40 + cell1 + 8), f"row 1: {ppc} px per cell, 1:1   row 2: the same pixels x{zoom}   "
                                 f"rows 3-4: drawn at {ppc * zoom} px per cell, standing and mid-stride", fill=(170, 170, 170, 255))
    img.convert("RGB").save(out)
    print(out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--ppc", type=float, default=32)
    ap.add_argument("--zoom", type=int, default=4)
    ap.add_argument("--set", action="append", default=[], help="label=bone dir, drawn at WORKER_SCALE")
    args = ap.parse_args()
    sets = [("runner", load(GRAPHICS / "rav-runner"), 1.0)]
    for spec in args.set or [f"rav-worker={GRAPHICS / 'rav-worker'}"]:
        label, folder = spec.split("=", 1)
        sets.append((label, load(Path(folder)), WORKER_SCALE))
    sheet(args.out, sets, int(args.ppc), args.zoom)


if __name__ == "__main__":
    main()
