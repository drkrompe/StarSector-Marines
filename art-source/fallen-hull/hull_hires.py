"""The hull's roof painting at a higher source resolution (owner, 2026-11-11, `hull-painting-res` (b)): the accepted
card (raw/hull-burnt.png, about 14 px a plan cell) re-rendered by ImageGen in SECTIONS overlapping portrait sections,
each the card's own crop as its edit target and geometry authority, then put back together on the plan.

  python art-source/fallen-hull/hull_hires.py prep       the section guides, build/fallen-hull-hires/section-<k>.png
  (each generated through tools/codex_imagegen.py into raw/hull-hires-<k>.png, prompts/hull-hires.prompt.txt)
  python art-source/fallen-hull/hull_hires.py assemble   build/fallen-hull-hires/hull-hires.png, the body at PX a
                                                         cell, plan space (regenerated, not tracked)

Assembly keeps each generation's fine detail and the card's own tone: a section's low frequencies (coarser than
KEEP_CELLS) are the card's, upscaled, so a generation that shifts a colour or nudges a panel cannot move what the
owner has seen, and neighbouring sections meet without a seam; overlaps are blended across their width. hull_roof.py
assembles it and reads it as its card when the generations are there.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SOURCE = HERE / "raw" / "hull-burnt.png"
BODY = (260, 296, 1480, 728)       # hull_roof.BODY: the body's box on the card, bow east
LENGTH, BREADTH = 88, 32           # HullPlan
SECTIONS = 5
SECTION_W = BREADTH * 2 / 3        # cells: a 2:3 portrait section the full breadth tall
GEN_W, GEN_H = 1024, 1536          # each generation's canvas
PX = 48                            # the assembled body's pixels a cell
KEEP_CELLS = 1.2                   # coarser than this, the card's own tone and shape
GREY = (96, 96, 96)                # the flat ground round the hull on a section guide
PREP = ROOT / "build" / "fallen-hull-hires"
OUT = PREP / "hull-hires.png"


def body():
    card = Image.open(SOURCE).convert("RGBA").crop(BODY)
    return card.resize((LENGTH * PX, BREADTH * PX), Image.LANCZOS)


def span(k):
    """Section k's left and right edges, cells."""
    stride = (LENGTH - SECTION_W) / (SECTIONS - 1)
    return k * stride, k * stride + SECTION_W


def prep():
    PREP.mkdir(parents=True, exist_ok=True)
    big = body()
    for k in range(SECTIONS):
        u0, u1 = span(k)
        crop = big.crop((round(u0 * PX), 0, round(u1 * PX), BREADTH * PX)).resize((GEN_W, GEN_H), Image.LANCZOS)
        flat = Image.new("RGBA", crop.size, GREY + (255,))
        flat.alpha_composite(crop)
        flat.convert("RGB").save(PREP / f"section-{k}.png")
        print("wrote", PREP / f"section-{k}.png", (u0, u1))


def assemble():
    big = body()
    w, h = big.size
    base = np.asarray(big.convert("RGB"), dtype=np.float32)
    acc = np.zeros((h, w, 3), dtype=np.float32)
    weight = np.zeros((h, w, 1), dtype=np.float32)
    blur = KEEP_CELLS * PX
    for k in range(SECTIONS):
        u0, u1 = span(k)
        x0, x1 = round(u0 * PX), round(u1 * PX)
        gen = Image.open(HERE / "raw" / f"hull-hires-{k}.png").convert("RGB").resize((x1 - x0, h), Image.LANCZOS)
        g = np.asarray(gen, dtype=np.float32)
        g_low = np.asarray(gen.filter(ImageFilter.GaussianBlur(blur)), dtype=np.float32)
        b = base[:, x0:x1]
        b_low = np.asarray(Image.fromarray(b.astype(np.uint8)).filter(ImageFilter.GaussianBlur(blur)),
                           dtype=np.float32)
        section = b_low + (g - g_low)
        # Blend weight: full in the middle, ramping to nothing across the overlaps at either side.
        xs = np.arange(x1 - x0, dtype=np.float32)
        ramp = (u1 - u0 - (span(1)[0] - span(0)[0])) * PX if SECTIONS > 1 else 1
        wx = np.clip(np.minimum(xs + 1, (x1 - x0) - xs) / max(1.0, ramp), 0, 1)
        if k == 0:
            wx[: len(wx) // 2] = 1
        if k == SECTIONS - 1:
            wx[len(wx) // 2:] = 1
        acc[:, x0:x1] += section * wx[None, :, None]
        weight[:, x0:x1] += wx[None, :, None]
    out = np.clip(acc / np.maximum(weight, 1e-3), 0, 255).astype(np.uint8)
    img = Image.fromarray(out, "RGB").convert("RGBA")
    img.putalpha(big.getchannel("A"))
    img.save(OUT)
    print("wrote", OUT, img.size)


if __name__ == "__main__":
    {"prep": prep, "assemble": assemble}[sys.argv[1]]()
