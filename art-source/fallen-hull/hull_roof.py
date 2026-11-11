"""The fallen hull's roof in battle: the hull-lineage card laid over the hull's plan.

  python art-source/fallen-hull/hull_roof.py

Writes mod/graphics/battle/fallen-hull/hull-roof.png, the whole roof in plan space (`HullPlan`): x runs along the hull
from the stern (left) to the bow (right), LENGTH cells; y across it, BREADTH cells, plan v = 0 at the bottom row. The
renderer (`HullRoof`) cuts each roofed cell of the hull's building out of it, turned to the hull's heading. No new
painting goes in:

  - the survey mothership's body from raw/hull-burnt.png (the picked lineage, raw/hull-survey.png, repainted burnt and
    broken over itself, so it registers on the same guide), its guide box stretched onto the plan rectangle, the
    stern's torn edge facing the trench;
  - where the plan's outline reaches past the painting's silhouette (the bow's shoulders, the stern's tear), the
    painting's own edge carried outward, so no second material shows;
  - a light unsharp mask, so the card's 14 px a cell holds up stretched to the texture's 23;
  - the breakup torn into it (`HullSite`'s sections, the ground's numbers): a ragged transparent tear across each break
    between sections (BREAKS) and a ragged hole at each side rent (RENTS), scorched round, so a section's roof ends
    torn and the sheared walls and the open deck show through.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]

LENGTH, BREADTH = 88, 32          # HullPlan.LENGTH, BREADTH
OUT_W = 2048                      # one power-of-two texture wide
PX = OUT_W / LENGTH               # pixels a plan cell
OUT_H = round(BREADTH * PX)
SOURCE = "hull-burnt.png"          # the picked lineage repainted burnt and broken, over itself (generate.py)
BODY = (260, 296, 1480, 728)      # make_guide.hull: the body's box on the card, bow east
GLOW_MARKS = (896, 390, 986, 419)  # output pixels: the glyph-like marks in the forge's glow window
SHARPEN = 60                     # unsharp mask, percent, on the card stretched to 23 px a cell
# The breakup, in plan cells (the ground's sections: stern 0..23, mid 24..45, bow 46..87; side rents as u and v ranges).
BREAKS = (24, 46)
RENTS = ((14, 17, 0, 1), (8, 11, 30, 31), (34, 37, 30, 31))
TEAR = (0.35, 1.5)                # a break's tear, cells either side of it: least at its span's ends, most mid-hull
TEAR_SPAN = (6, 26)               # across, the keel's middle the ground tears open (HullPlan.TEAR_V0..TEAR_V1 + 1):
                                  # a broken end's corners stand and are roofed, so the painting keeps them
SCORCH = 2.2                      # cells of soot beyond a tear's edge
OUT = ROOT / "mod" / "graphics" / "battle" / "fallen-hull" / "hull-roof.png"


def extend(img, mask):
    """The card carried past its silhouette: each outside pixel takes the mean of the painting nearest it."""
    a = np.asarray(mask, dtype=np.float32)[..., None] / 255.0
    c = np.asarray(img, dtype=np.float32) * a
    out = c.copy()
    weight = a.copy()
    for radius in (2, 4, 8, 16, 32):
        cb = np.asarray(Image.fromarray(np.clip(c, 0, 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(radius)),
                        dtype=np.float32)
        ab = np.asarray(Image.fromarray((a[..., 0] * 255).astype(np.uint8)).filter(ImageFilter.BoxBlur(radius)),
                        dtype=np.float32)[..., None] / 255.0
        fill = cb / np.maximum(ab, 1e-3)
        take = (weight < 0.5) & (ab > 0.02)
        out = np.where(take, fill * (1 - weight) + out, out)
        weight = np.where(take, 1.0, weight)
    return Image.fromarray(np.clip(out, 0, 255).astype(np.uint8))


def tears(roof):
    """The breaks and rents torn out of the roof: alpha 0 inside, a soot gradient outside, a 2 px soft edge."""
    rgb = np.asarray(roof.convert("RGB"), dtype=np.float32)
    h, w = rgb.shape[:2]
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    u, v = xs / PX, BREADTH - ys / PX
    inside = np.full((h, w), np.inf, dtype=np.float32)    # signed distance to the nearest tear's edge, cells
    rnd = np.random.default_rng(11)
    for k, b in enumerate(BREAKS):
        span = (v - TEAR_SPAN[0]) / (TEAR_SPAN[1] - TEAR_SPAN[0])
        across = np.sin(np.pi * np.clip(span, 0, 1))        # 0 at the span's ends, 1 mid-hull
        ph = rnd.uniform(0, 6.3, 4)
        ragged = (0.45 * np.sin(v * 1.7 + ph[0]) + 0.3 * np.sin(v * 3.9 + ph[1]) + 0.2 * np.sin(v * 8.3 + ph[2])
                  + 0.12 * np.sin(v * 17.0 + ph[3]))
        depth = TEAR[0] + (TEAR[1] - TEAR[0]) * across + 0.6 * ragged
        torn = np.abs(u - b) - np.maximum(depth, 0.1)
        # Past the span's ends the tear closes: the distance out of it, so the soot fades round the standing corner.
        torn = np.maximum(torn, np.maximum(TEAR_SPAN[0] - v, v - TEAR_SPAN[1]))
        inside = np.minimum(inside, torn)
    for u0, u1, v0, v1 in RENTS:
        cu, cv = (u0 + u1 + 1) / 2, (v0 + v1 + 1) / 2
        ru, rv = (u1 - u0 + 1) / 2 + 0.3, (v1 - v0 + 1) / 2 + 0.6
        ang = np.arctan2((v - cv) / rv, (u - cu) / ru)
        ph = rnd.uniform(0, 6.3, 3)
        r = 1 + 0.18 * np.sin(3 * ang + ph[0]) + 0.1 * np.sin(7 * ang + ph[1]) + 0.06 * np.sin(13 * ang + ph[2])
        d = np.hypot((u - cu) / ru, (v - cv) / rv) / r - 1
        inside = np.minimum(inside, d * min(ru, rv))
    alpha = np.clip(inside * PX / 2, 0, 1)
    soot = np.clip(1 - inside / SCORCH, 0, 1) ** 1.6
    rgb = rgb * (1 - 0.85 * soot[..., None]) + np.array([24, 20, 17], dtype=np.float32) * 0.85 * soot[..., None]
    out = np.dstack([np.clip(rgb, 0, 255), alpha * 255]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


def main():
    card = Image.open(HERE / "raw" / SOURCE).convert("RGB").crop(BODY).resize((OUT_W, OUT_H),
                                                                                         Image.LANCZOS)
    guide = Image.open(HERE / "guides" / "hull-guide.png").convert("RGBA").crop(BODY)
    mask = guide.getchannel("A").resize((OUT_W, OUT_H), Image.LANCZOS).filter(ImageFilter.GaussianBlur(1.5))
    roof = Image.composite(card, extend(card, mask), mask)
    # The painter left letter-like marks in the forge's glow window; no text on the hull, so they melt into the glow.
    x0, y0, x1, y1 = GLOW_MARKS
    pad = 6
    box = (x0 - pad, y0 - pad, x1 + pad, y1 + pad)
    glow = roof.crop(box).filter(ImageFilter.GaussianBlur(3.5)).point(lambda c: min(255, round(c * 1.25)))
    feather = Image.new("L", glow.size, 0)
    feather.paste(255, (pad, pad, pad + x1 - x0, pad + y1 - y0))
    roof.paste(glow, box[:2], feather.filter(ImageFilter.GaussianBlur(pad / 2)))
    roof = roof.filter(ImageFilter.UnsharpMask(radius=2, percent=SHARPEN, threshold=2))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    tears(roof).save(OUT)
    print("wrote", OUT, (OUT_W, OUT_H))


if __name__ == "__main__":
    main()
