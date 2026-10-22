"""A river settlement on C3's far bank, its freight road, and the relay mast above it.

python c3_settlement.py prep     # guides/c3-settlement-target.png (Codex edit target) and its mask
python c3_settlement.py check    # registration of assets/c3-settlement-v1.png against the target

Background touches for the Jangala the player will fight over later, not a tour (c3-layout.md,
"The settlement"): a freight checkpoint town on the far bank of the river, with a weigh-station
canopy across its road (Junction's Weigh Station, Wayline's checkpoint gate), warehouses and a
depot yard (Relief's service roads), a moored freight barge, trucks, and the laterite freight road
climbing out of the town into the hills (the Thunder Run corridor). The relay mast on the hill
above it (Relay Nine) is a moving element drawn by compose_c3.py, not paint: it stands above the
hill's crest line, where the plate belongs to a farther layer.

Geometry first (art-source/geometry-first-imagegen.md): the town is drawn into an upscaled crop
of the de-misted plate at its true scale (the bank is ~2 km out: about 1.2 plate px per metre,
seen 15 degrees down, so a 40 m warehouse is ~46 px wide and its roof ~6 px deep), and Codex
repaints only that drawing into the painting. apply() then registers the completion, matches
its broad colour to the plate outside the drawing, and pastes it back through the drawing's
feathered mask, so nothing outside the town can change.
"""
import argparse
import json
import math
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from c3_core import BASE

G = BASE / "guides"
A = BASE / "assets"
P = BASE / "build" / "plates"
CROP = (1750, 700, 2950, 1150)            # plate px (2x master): the far bank under the hills
GEN = (2048, 768)
K = GEN[0] / (CROP[2] - CROP[0])          # gen px per plate px (1.707)
SS = 2                                    # the guide is drawn at 2x and reduced

# The far bank's waterline, detected on the plate (first water row from above) and pulled up
# over the dark reflection band; plate px.
BANK_X = [2100, 2200, 2300, 2400, 2500, 2600, 2700, 2800, 2900, 2960]
BANK_Y = [1100, 1054, 1020, 970, 946, 890, 878, 850, 838, 822]

LATERITE = (148, 92, 62)                  # C4's red earth at the jungle edge
LATERITE_LIT = (176, 116, 80)
YARD = (128, 104, 84)
SHADOW = (24, 32, 22)


def bank(x):
    return float(np.interp(x, BANK_X, BANK_Y))


def gp(x, y):
    """Plate px -> guide canvas px (supersampled)."""
    return ((x - CROP[0]) * K * SS, (y - CROP[1]) * K * SS)


# Buildings: (x left, metres back from the bank road's line, width m, depth m, wall m, roof, wall)
ROOF_RUST, ROOF_GREY, ROOF_WHITE, ROOF_GREEN = (150, 86, 58), (150, 152, 150), (206, 206, 200), (96, 120, 100)
BUILDINGS = [
    (2318, 34, 44, 22, 8, ROOF_GREY, (120, 118, 112)),        # depot warehouse
    (2372, 30, 40, 20, 8, ROOF_RUST, (122, 104, 90)),         # depot warehouse
    (2425, 36, 26, 14, 6, ROOF_GREY, (126, 122, 114)),
    (2462, 22, 14, 10, 5, ROOF_WHITE, (150, 146, 136)),       # checkpoint office
    (2490, 34, 30, 16, 7, ROOF_RUST, (116, 98, 86)),
    (2532, 26, 12, 10, 4, ROOF_GREEN, (120, 120, 110)),       # houses up the bank
    (2552, 36, 12, 10, 4, ROOF_RUST, (128, 112, 98)),
    (2572, 28, 14, 10, 4, ROOF_GREY, (130, 126, 118)),
    (2596, 40, 12, 9, 4, ROOF_WHITE, (138, 134, 124)),
    (2286, 46, 16, 12, 5, ROOF_RUST, (118, 100, 88)),
]
PX_PER_M = 1.16                           # plate px per metre at ~2 km
DEPRESSION = math.radians(15.0)


def spline(pts, n=8):
    """Catmull-Rom through the points: a road that bends rather than kinks."""
    p = np.array([pts[0]] + list(pts) + [pts[-1]], np.float64)
    out = []
    for i in range(1, len(p) - 2):
        for t in np.linspace(0, 1, n, endpoint=False):
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * (2 * p[i] + (-p[i - 1] + p[i + 1]) * t + (2 * p[i - 1] - 5 * p[i] + 4 * p[i + 1] - p[i + 2]) * t2
                                    + (-p[i - 1] + 3 * p[i] - 3 * p[i + 1] + p[i + 2]) * t3)))
    return out + [tuple(p[-2])]


def _poly(d, pts, fill):
    d.polygon([gp(*p) for p in pts], fill=fill)


def draw_guide(base):
    """The town drawn over the upscaled crop: returns (guide RGB, mask L) at GEN."""
    W2, H2 = GEN[0] * SS, GEN[1] * SS
    im = base.resize((W2, H2), Image.LANCZOS)
    mask = Image.new("L", (W2, H2), 0)
    d, m = ImageDraw.Draw(im), ImageDraw.Draw(mask)
    road_w = 5.0 * K * SS

    def line(pts, fill, width, mfill=255, mwidth=None):
        q = [gp(*p) for p in pts]
        d.line(q, fill=fill, width=round(width), joint="curve")
        m.line(q, fill=mfill, width=round(mwidth or width * 2.6), joint="curve")

    # --- the clearing on the bank: packed laterite, the yard, grass along its back edge
    xs = np.arange(2270, 2650, 6)
    back = [(x, bank(x) - 10 - 48 * math.sin(math.pi * (x - 2270) / 380) ** 0.6) for x in xs]
    front = [(x, bank(x) - 4) for x in xs[::-1]]
    _poly(d, back + front, YARD)
    _poly(m, back + front, 255)
    # --- the freight road: along the back of the town, upstream along the bank to the right,
    # and out of the town's left end, climbing into the hills in two switchbacks
    town_road = [(x, bank(x) - 22) for x in range(2270, 2660, 10)]
    bank_road = [(x, bank(x) - 16 - 14 * min(1.0, (x - 2650) / 80)) for x in range(2650, 2960, 10)]
    hill_road = spline([(2270, bank(2270) - 22), (2226, 1004), (2192, 978), (2178, 956), (2200, 936),
                        (2224, 918), (2210, 900), (2160, 884), (2096, 872), (2030, 852), (1960, 840),
                        (1890, 818), (1820, 806), (1750, 786)])
    for pts in (town_road, bank_road, hill_road):
        line(pts, (84, 98, 60), road_w * 2.0)          # the cleared verge, scrub
        line(pts, LATERITE, road_w)
    line(hill_road[:6], LATERITE_LIT, road_w * 0.45)   # the sunlit lip of the cut
    # --- the weigh station: a long white canopy across the bank road, a booth and a boom
    wx = 2668
    wy = bank(wx) - 16
    sx, sy = gp(wx - 12, wy - 9)
    ex, ey = gp(wx + 12, wy - 5)
    d.polygon([(sx, sy), (ex, ey - 6 * SS), (ex, ey), (sx, sy + 6 * SS)], fill=(222, 222, 214))
    d.rectangle([*gp(wx - 10, wy - 4), *gp(wx + 10, wy + 1)], fill=(70, 70, 66))
    d.line([gp(wx + 15, wy - 1), gp(wx + 26, wy - 3)], fill=(220, 60, 40), width=2 * SS)
    d.rectangle([*gp(wx - 18, wy - 8), *gp(wx - 14, wy - 1)], fill=(200, 196, 180))
    m.rectangle([*gp(wx - 30, wy - 22), *gp(wx + 34, wy + 8)], fill=255)
    # --- buildings, back to front: shadow (sun upper left), front wall, roof
    roof_dy = math.sin(DEPRESSION) * PX_PER_M
    wall_dy = math.cos(DEPRESSION) * PX_PER_M
    for x0, back_m, w_m, d_m, h_m, roof, wall in sorted(BUILDINGS, key=lambda b: -b[1]):
        yb = bank(x0) - 8 - back_m * roof_dy * 3.2          # foot of its front wall
        w = w_m * PX_PER_M
        rd, wh = d_m * roof_dy, h_m * wall_dy
        slope = (bank(x0 + w) - bank(x0)) / max(w, 1)
        foot = [(x0, yb), (x0 + w, yb + slope * w)]
        _poly(d, [foot[0], foot[1], (foot[1][0] + wh * 0.7, foot[1][1] + 1.5), (foot[0][0] + wh * 0.7, foot[0][1] + 1.5)], SHADOW)
        top = [(x, y - wh) for x, y in foot]
        _poly(d, [foot[0], foot[1], top[1], top[0]], wall)
        _poly(d, [top[0], top[1], (top[1][0] + rd * 0.4, top[1][1] - rd), (top[0][0] + rd * 0.4, top[0][1] - rd)], roof)
        for k in range(1, int(w // 5)):                     # corrugation ribs catch the light
            x = top[0][0] + k * 5
            d.line([gp(x, top[0][1] + slope * k * 5), gp(x + rd * 0.4, top[0][1] + slope * k * 5 - rd)],
                   fill=tuple(min(255, c + 18) for c in roof), width=SS)
    # --- the depot yard: container rows and three white tanks
    for i, col in enumerate([(178, 70, 52), (60, 104, 150), (196, 160, 60), (70, 130, 96), (178, 70, 52), (150, 150, 146)]):
        x = 2412 + (i % 3) * 9
        y = bank(x) - 9 - (i // 3) * 3.5
        d.rectangle([*gp(x, y - 3), *gp(x + 7, y)], fill=col)
    for i in range(3):
        x = 2296 + i * 9
        y = bank(x) - 12
        d.ellipse([*gp(x, y - 7), *gp(x + 7, y)], fill=(214, 214, 206))
        d.ellipse([*gp(x + 5, y - 1), *gp(x + 10, y + 1)], fill=SHADOW)
    # --- the barge moored along the bank below the depot, and its jetty
    bx = 2386
    hull = [(bx, bank(bx) + 20), (bx + 70, bank(bx + 70) + 20), (bx + 74, bank(bx + 74) + 15), (bx + 4, bank(bx) + 14)]
    _poly(d, hull, (58, 56, 52))
    _poly(m, [(x, y + dy) for (x, y), dy in zip(hull, (8, 8, -24, -24))], 255)
    for i in range(6):
        x = bx + 8 + i * 10
        d.rectangle([*gp(x, bank(x) + 12), *gp(x + 8, bank(x) + 16)],
                    fill=[(178, 70, 52), (60, 104, 150), (150, 150, 146), (196, 160, 60), (70, 130, 96), (178, 70, 52)][i])
    d.line([gp(bx + 34, bank(bx + 34) - 2), gp(bx + 36, bank(bx + 36) + 14)], fill=(110, 100, 86), width=2 * SS)
    # --- trucks: two on the bank road, one climbing the hill road
    for x, y in ((2760, bank(2760) - 17), (2860, bank(2860) - 17), (2196, 989)):
        d.rectangle([*gp(x, y - 2.5), *gp(x + 9, y + 0.5)], fill=(196, 194, 186))
        d.rectangle([*gp(x + 9, y - 2.5), *gp(x + 12, y + 0.5)], fill=(176, 70, 46))
    # --- the mast's foot: a small cleared pad with a hut on the hill above the town
    mx, my = MAST_FOOT
    d.ellipse([*gp(mx - 9, my - 3), *gp(mx + 9, my + 3)], fill=YARD)
    d.rectangle([*gp(mx + 2, my - 4), *gp(mx + 7, my - 1)], fill=(190, 188, 180))
    m.ellipse([*gp(mx - 20, my - 10), *gp(mx + 20, my + 10)], fill=255)
    im = im.resize(GEN, Image.LANCZOS)
    mask = mask.resize(GEN, Image.LANCZOS).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(6))
    return im, mask


MAST_FOOT = (2232, 862)                   # plate px: the relay mast stands here (compose_c3)


def crop_of(plate):
    return plate.crop(CROP)


def prep():
    plate = Image.open(P / "land-demist.png").convert("RGB")
    guide, mask = draw_guide(crop_of(plate))
    G.mkdir(exist_ok=True)
    guide.save(G / "c3-settlement-target.png")
    mask.save(G / "c3-settlement-mask.png")
    crop_of(plate).resize(GEN, Image.LANCZOS).save(G / "c3-settlement-before.png")
    print("wrote", G / "c3-settlement-target.png")


def register(gen, target, mask):
    """Integer shift of the completion against the edit target, measured outside the drawing."""
    from c3_plates import best_shift
    (dx, dy), score = best_shift(target, gen, mask < 0.05, 6)
    return dx, dy, score


def load(version="v1"):
    path = A / ("c3-settlement-%s.png" % version)
    if not path.exists():
        return None
    return Image.open(path).convert("RGB").resize(GEN, Image.LANCZOS)


def check(version="v1"):
    gen = load(version)
    if gen is None:
        raise SystemExit("no assets/c3-settlement-%s.png" % version)
    tgt = np.asarray(Image.open(G / "c3-settlement-target.png").convert("RGB"), np.float32) / 255
    mask = np.asarray(Image.open(G / "c3-settlement-mask.png"), np.float32) / 255
    g = np.asarray(gen, np.float32) / 255
    dx, dy, score = register(g, tgt, mask)
    out = np.abs(g - tgt)[mask < 0.05].mean()
    print("settlement %s: shift (%d, %d) score %.3f; mean |diff| outside the town %.3f" % (version, dx, dy, score, out))
    return dx, dy


def apply(plate_rgb, version="v1"):
    """The plate (float RGB, plate px) with the settlement pasted in, or unchanged when the
    completion does not exist."""
    gen = load(version)
    if gen is None:
        return plate_rgb
    from c3_plates import blur, shift
    if not (G / "c3-settlement-target.png").exists() or not (G / "c3-settlement-mask.png").exists():
        prep()                                                     # generated guides: a fresh checkout has none
    tgt = np.asarray(Image.open(G / "c3-settlement-target.png").convert("RGB"), np.float32) / 255
    mask = np.asarray(Image.open(G / "c3-settlement-mask.png"), np.float32) / 255
    g = np.asarray(gen, np.float32) / 255
    dx, dy, _ = register(g, tgt, mask)
    if abs(dx) > 3 or abs(dy) > 3:
        raise SystemExit("settlement completion drifts (%d, %d) gen px; rejected" % (dx, dy))
    g = shift(g, dx, dy)
    size = (CROP[2] - CROP[0], CROP[3] - CROP[1])
    g = np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize(size, Image.LANCZOS), np.float32) / 255
    mk = np.asarray(Image.fromarray((mask * 255).astype(np.uint8)).resize(size, Image.BILINEAR), np.float32) / 255
    out = plate_rgb.copy()
    reg = out[CROP[1]:CROP[3], CROP[0]:CROP[2]]
    # broad colour from the plate, detail from the completion (as c3_plates does for its tiles)
    outside = (mk < 0.05).astype(np.float32)
    lo_p = blur(reg * outside[..., None], 40) / np.maximum(blur(outside, 40), 1e-3)[..., None]
    lo_g = blur(g * outside[..., None], 40) / np.maximum(blur(outside, 40), 1e-3)[..., None]
    g = np.clip(g + (lo_p - lo_g), 0, 1)
    out[CROP[1]:CROP[3], CROP[0]:CROP[2]] = reg * (1 - mk[..., None]) + g * mk[..., None]
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("step", choices=("prep", "check"))
    ap.add_argument("--version", default="v1")
    a = ap.parse_args()
    if a.step == "prep":
        prep()
    else:
        check(a.version)


if __name__ == "__main__":
    sys.exit(main())
