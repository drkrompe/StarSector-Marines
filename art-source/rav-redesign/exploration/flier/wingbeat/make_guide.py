"""Geometry guides for the lilac glider's wingbeat and fold-into-dive frames.

python art-source/rav-redesign/exploration/flier/wingbeat/make_guide.py

The body is the accepted glide drawing's own pixels (round-1 sheet a.png, keyed off its grey), so every frame carries
the same body. The wings are posed in 3D and drawn as flat geometry: a jointed bone arm from the shoulder to the
wrist, three finger spars fanning from the wrist ending in hooked claws, and a membrane in three panels between them
and back to the flank. Each panel is shaded by how it faces the light, so a raised or dropped wing reads as tilted.

The skeleton is measured off the glide drawing (sheet pixels, x from the body's centre line, y down the sheet). A
pose is five numbers: arm elevation about the spine axis through the shoulder, hand elevation about a parallel axis
through the wrist, arm sweep in the body plane (forward positive), hand sweep about the wrist, and finger fold (0 the
spars fanned as in the glide, 1 laid back together along the flank).

Writes guide-<sheet>.png, the frames in a grid of COLUMNS in FRAMES order, on the round-1 sheet's flat grey, every
frame centred in its own cell with the body's centre line on the cell's centre. CELL and SCALE are the export's
contract: export_harrier.py cuts each frame from its cell by these numbers, never by looking for gaps.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))
import cut_flier  # noqa: E402

SHEET = HERE.parent / "round-1" / "raw" / "a.png"
GREY = (180, 180, 180)
SS = 3                      # supersampling of the wing geometry
SCALE = 0.52                # guide pixels per round-1 sheet pixel
CELL = (680, 440)           # one frame's cell in the guide, guide pixels
BODY_TOP = 40               # sheet y drawn at the cell's top edge (the head starts near y 95)
CX = 552                    # the body's centre line on the round-1 sheet

# The right wing's skeleton on the round-1 sheet, x relative to CX. The left wing mirrors it.
S = (68, 245)               # shoulder, where the arm leaves the flank
W = (248, 185)              # wrist knuckle
ROOTS = [(248, 185), (251, 227), (215, 230)]      # where each finger spar leaves the wrist
KNUCKLES = [(448, 220), (461, 367), (298, 413)]   # where each spar ends and its claw begins
CLAWS = [(531, 277), (505, 423), (298, 483)]      # claw tips
EDGES = [(441, 313), (378, 395)]                  # the membrane's scalloped trailing edge between spars
INNER = (148, 323)          # the trailing edge between the last spar and the flank
B = (95, 333)               # where the membrane meets the flank (on the body: never moves)

# Fold targets: each spar's angle from the wrist when laid back along the flank (degrees, screen: 90 is straight back).
FOLDED = [100.0, 96.0, 92.0]

# name -> (arm elevation, hand elevation, arm sweep, hand sweep, finger fold)
FRAMES = {
    "beat-1": (34, -12, -6, -10, 0.25),    # upstroke begins: wing rising, hand trailing, fingers closing a little
    "beat-2": (62, -18, 0, -6, 0.40),      # mid upstroke
    "beat-3": (82, 0, 12, 8, 0.20),        # the top: wings raised high over the back, reaching forward
    "beat-4": (52, 22, 16, 10, 0.0),       # downstroke begins: arm driving down, the hand lagging above it
    "beat-5": (8, 14, 12, 6, -0.06),       # mid downstroke: level, fully spread and reaching forward
    "beat-6": (-38, -18, 4, 0, 0.0),       # the bottom: wings swept down below the body
    "beat-7": (-18, 12, -6, -8, 0.12),     # recovery: arm coming up, the hand still low
    "fold-1": (14, 6, 18, -22, 0.40),      # folding into the dive: wrist coming forward, spars swinging back
    "fold-2": (24, 10, 34, -48, 0.80),     # nearly folded: arm forward, spars laid along the flank
}
# One sheet, three rows of three, read left to right and top to bottom.
SHEETS = [("all", ["beat-1", "beat-2", "beat-3", "beat-4", "beat-5", "beat-6", "beat-7", "fold-1", "fold-2"])]
COLUMNS = 3

LIGHT = np.array([-0.45, -0.45, 0.77])
MEMBRANE = np.array([150, 28, 62], float)
BONE = (222, 204, 186)
ARM = (206, 186, 214)


def _rot(p, centre, deg):
    a = math.radians(deg)
    dx, dy = p[0] - centre[0], p[1] - centre[1]
    return (centre[0] + dx * math.cos(a) - dy * math.sin(a), centre[1] + dx * math.sin(a) + dy * math.cos(a))


def _fold(p, fold, lo, hi):
    """Rotates a hand point about the wrist toward its folded angle; lo/hi weight which spars' targets it follows."""
    ang = math.degrees(math.atan2(p[1] - W[1], p[0] - W[0]))
    target = FOLDED[lo] * 0.5 + FOLDED[hi] * 0.5
    r = math.hypot(p[0] - W[0], p[1] - W[1])
    a = math.radians(ang + (target - ang) * fold)
    return (W[0] + r * math.cos(a), W[1] + r * math.sin(a))


def pose(params):
    """The right wing's points in 3D for one pose: a dict of name -> (x, y, z), x from the centre line."""
    ea, eh, sa, sh, fold = params
    pts = {"S": S, "W": W, "B": B, "INNER": INNER}
    for i in range(3):
        pts[f"R{i}"] = ROOTS[i]
        pts[f"K{i}"] = _fold(KNUCKLES[i], fold, i, i)
        pts[f"C{i}"] = _fold(CLAWS[i], fold, i, i)
    pts["E0"] = _fold(EDGES[0], fold, 0, 1)
    pts["E1"] = _fold(EDGES[1], fold, 1, 2)
    hand = [k for k in pts if k[0] in "RKCE"]
    # Hand sweep about the wrist, arm sweep (carrying the hand) about the shoulder; in-plane, forward is -y.
    for k in hand:
        pts[k] = _rot(pts[k], W, -sh)
    for k in hand + ["W", "INNER"]:
        pts[k] = _rot(pts[k], S, -sa)
    w = pts["W"]
    out = {}
    ch, sh_ = math.cos(math.radians(eh)), math.sin(math.radians(eh))
    ca, sa_ = math.cos(math.radians(ea)), math.sin(math.radians(ea))
    for k, (x, y) in pts.items():
        z = 0.0
        if k in hand:
            dx = x - w[0]
            x, z = w[0] + dx * ch, dx * sh_
        if k != "B":
            dx = x - S[0]
            weight = 0.5 if k == "INNER" else 1.0   # the inner trailing edge is half on the flank
            e = math.radians(ea * weight)
            x, z = S[0] + dx * math.cos(e) - z * math.sin(e), dx * math.sin(e) + z * math.cos(e)
        out[k] = (x, y, z)
    return out


def _shade(a, b, c):
    n = np.cross(np.subtract(b, a), np.subtract(c, a))
    n = n / (np.linalg.norm(n) + 1e-9)
    if n[2] < 0:
        n = -n
    lit = 0.45 + 0.55 * max(0.0, float(n @ LIGHT))
    return tuple(int(v) for v in np.clip(MEMBRANE * lit, 0, 255))


def draw_wing(d, p, ox, oy, k, mirror):
    """Draws one wing; (ox, oy) is the centre line's position in the drawing, k sheet->drawing pixels."""
    def xy(name):
        x, y, _ = p[name]
        return (ox + (-x if mirror else x) * k, oy + y * k)

    panels = [["W", "K0", "E0", "K1", "R1"], ["R1", "K1", "E1", "K2", "R2"], ["S", "W", "R2", "K2", "INNER", "B"]]
    # Draw the panel farthest from the camera first.
    panels.sort(key=lambda names: sum(p[n][2] for n in names) / len(names))
    for names in panels:
        col = _shade(p[names[0]], p[names[1]], p[names[3]])
        d.polygon([xy(n) for n in names], fill=col)
    for i in range(3):
        d.line([xy(f"R{i}"), xy(f"K{i}")], fill=BONE, width=int(11 * k))
        _claw(d, xy(f"K{i}"), xy(f"C{i}"), k)
    d.line([xy("S"), xy("W")], fill=ARM, width=int(26 * k))
    wx, wy = xy("W")
    r = 17 * k
    d.ellipse([wx - r, wy - r, wx + r, wy + r], fill=ARM)


def _claw(d, a, b, k):
    """A hooked claw from knuckle a to tip b: a tapered wedge, bowed outward."""
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    ln = math.hypot(dx, dy) + 1e-9
    nx, ny = -dy / ln, dx / ln
    pts = []
    for t in np.linspace(0, 1, 12):
        bow = math.sin(t * math.pi) * 0.18 * ln
        half = (1 - t) * 9 * k
        cx, cy = ax + dx * t + nx * bow, ay + dy * t + ny * bow
        pts.append((cx + nx * half, cy + ny * half))
    for t in np.linspace(1, 0, 12):
        bow = math.sin(t * math.pi) * 0.18 * ln
        half = (1 - t) * 9 * k
        cx, cy = ax + dx * t + nx * bow, ay + dy * t + ny * bow
        pts.append((cx - nx * half, cy - ny * half))
    d.polygon(pts, fill=BONE)


def body_mask(alpha):
    """The glide drawing's body without its wings, as a soft mask over the sheet crop of the left figure."""
    h, w = alpha.shape
    ys, xs = np.mgrid[0:h, 0:w]
    dx = np.abs(xs - CX)
    limit = np.where(ys < 230, 75, np.where(ys < 290, 66, np.where(ys < 370, 100, 175)))
    m = (dx < limit) & (alpha > 0)
    img = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2))
    return np.minimum(np.asarray(img).astype(float) / 255.0, alpha / 255.0)


def body_layer():
    """The keyed glide body as RGBA on the round-1 sheet's coordinates (left figure only)."""
    keyed = cut_flier.key(Image.open(SHEET))
    left = keyed.crop((0, 0, 1100, keyed.height))
    a = np.asarray(left).astype(float)
    m = body_mask(a[..., 3])
    out = a.copy()
    out[..., 3] = m * 255
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def wings_cell(name):
    """The posed wings alone on a transparent canvas of CELL, in the frame's cell coordinates."""
    cw, ch = CELL
    big = Image.new("RGBA", (cw * SS, ch * SS), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    k = SCALE * SS
    p = pose(FRAMES[name])
    for mirror in (False, True):
        draw_wing(d, p, cw * SS / 2, -BODY_TOP * k, k, mirror)
    return big.resize((cw, ch), Image.LANCZOS)


def to_cell(layer):
    """A layer in round-1 sheet coordinates (the left figure's crop) placed in a frame's cell."""
    cw, ch = CELL
    small = layer.resize((round(layer.width * SCALE), round(layer.height * SCALE)), Image.LANCZOS)
    canvas = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    canvas.alpha_composite(small, (round(cw / 2 - CX * SCALE), round(-BODY_TOP * SCALE)))
    return canvas


def frame(name, body_cell):
    """One guide frame: the posed wings under the glide body."""
    canvas = wings_cell(name)
    canvas.alpha_composite(body_cell)
    return canvas


def main():
    body = to_cell(body_layer())
    for sheet, names in SHEETS:
        cw, ch = CELL
        rows = -(-len(names) // COLUMNS)
        out = Image.new("RGB", (cw * COLUMNS, ch * rows), GREY)
        for i, name in enumerate(names):
            f = frame(name, body)
            print(name, "opaque box", f.getchannel("A").point(lambda v: 255 if v > 20 else 0).getbbox())
            out.paste(f, ((i % COLUMNS) * cw, (i // COLUMNS) * ch), f)
        path = HERE / f"guide-{sheet}.png"
        out.save(path)
        print(path.name, out.size)


if __name__ == "__main__":
    main()