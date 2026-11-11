"""A survey stake's drone pad (owner, 2026-11-11, `stake-composed` (a): the parts stay separate and build up in their
slots): the round plate the stake's repair drone lands on, in its DRONE slot (StakeFootprint), its deck the hull's own
roof plating so it reads as the same machine.

  graphics/battle/fallen-hull/stake-pad.png   the pad, 256 px across, drawn 2.4 cells across (StakeConstruction)

Run from the repository root: python art-source/fallen-hull/stake_parts.py
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[2]
ART = ROOT / "mod" / "graphics" / "battle" / "fallen-hull"
SIZE = 256


def pad():
    # The deck: the HULL roof's own plating (roofs.png, RoofStyle.HULL's row, its 2x2-cell field swatch).
    sheet = Image.open(ROOT / "mod" / "graphics" / "tilesets" / "roofs.png").convert("RGBA")
    y0 = 4 * 128
    deck = Image.new("RGBA", (256, 256))
    for i in range(2):
        for j in range(2):
            col = i + 2 * j
            deck.paste(sheet.crop((col * 128, y0, col * 128 + 128, y0 + 128)), (i * 128, (1 - j) * 128))
    deck = deck.resize((SIZE, SIZE), Image.LANCZOS)
    deck = Image.merge("RGBA", [c.point(lambda v: int(v * 0.8)) for c in deck.split()[:3]] + [deck.getchannel("A")])
    out = Image.new("RGBA", (SIZE, SIZE))
    disc = Image.new("L", (SIZE, SIZE), 0)
    ImageDraw.Draw(disc).ellipse([4, 4, SIZE - 5, SIZE - 5], fill=255)
    out.paste(deck, (0, 0), disc)
    d = ImageDraw.Draw(out)
    c = SIZE / 2
    # The rim: a dark steel band, an amber-and-black hazard ring inside it.
    d.ellipse([4, 4, SIZE - 5, SIZE - 5], outline=(38, 36, 34, 255), width=14)
    r0, r1 = c - 26, c - 16
    for k in range(32):
        a0, a1 = 2 * math.pi * k / 32, 2 * math.pi * (k + 1) / 32
        colour = (214, 160, 52, 255) if k % 2 == 0 else (30, 28, 26, 255)
        pts = [(c + r0 * math.cos(a0), c + r0 * math.sin(a0)), (c + r1 * math.cos(a0), c + r1 * math.sin(a0)),
               (c + r1 * math.cos(a1), c + r1 * math.sin(a1)), (c + r0 * math.cos(a1), c + r0 * math.sin(a1))]
        d.polygon(pts, fill=colour)
    # The landing mark: a cross of worn paint, and four deck lamps.
    for ang in (0, 90):
        rad = math.radians(ang)
        dx, dy = math.cos(rad), math.sin(rad)
        d.line([(c - dx * 52, c - dy * 52), (c + dx * 52, c + dy * 52)], fill=(190, 186, 170, 200), width=9)
    for k in range(4):
        a = math.radians(45 + 90 * k)
        x, y = c + math.cos(a) * (c - 40), c + math.sin(a) * (c - 40)
        d.ellipse([x - 7, y - 7, x + 7, y + 7], fill=(40, 40, 38, 255))
        d.ellipse([x - 4, y - 4, x + 4, y + 4], fill=(120, 220, 255, 255))
    out = out.filter(ImageFilter.SMOOTH)
    out.save(ART / "stake-pad.png")
    print("wrote", ART / "stake-pad.png")


if __name__ == "__main__":
    pad()
