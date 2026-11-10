"""The fallen hull's roof in battle: the hull-lineage card laid over the hull's plan.

  python art-source/fallen-hull/hull_roof.py

Writes mod/graphics/battle/fallen-hull/hull-roof.png, the whole roof in plan space (`HullPlan`): x runs along the hull
from the stern (left) to the bow (right), LENGTH cells; y across it, BREADTH cells, plan v = 0 at the bottom row. The
renderer (`HullRoof`) cuts each roofed cell of the hull's building out of it, turned to the hull's heading. No new
painting goes in:

  - the survey mothership's body from raw/hull-survey.png (the picked lineage), its guide box stretched onto the plan
    rectangle, the stern's torn edge facing the trench;
  - where the plan's outline reaches past the painting's silhouette (the bow's shoulders, the stern's tear), the
    painting's own edge carried outward, so no second material shows;
  - a light unsharp mask, so the card's 14 px a cell holds up stretched to the texture's 23.
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
BODY = (260, 296, 1480, 728)      # make_guide.hull: the body's box on the card, bow east
GLOW_MARKS = (896, 390, 986, 419)  # output pixels: the glyph-like marks in the forge's glow window
SHARPEN = 60                     # unsharp mask, percent, on the card stretched to 23 px a cell
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


def main():
    card = Image.open(HERE / "raw" / "hull-survey.png").convert("RGB").crop(BODY).resize((OUT_W, OUT_H),
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
    roof.convert("RGBA").save(OUT)
    print("wrote", OUT, (OUT_W, OUT_H))


if __name__ == "__main__":
    main()
