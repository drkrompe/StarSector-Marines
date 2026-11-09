"""Geometry guides for a low-tech Hegemony Hound chassis: the Hound drawn in the Hegemony armour line's language (the
Scorpion, the Onager, the APC: tan and olive plate, rivets, piping, orange hazard panels) instead of the shared sleek
outline every faction now repaints.

The runtime draws a chassis sprite square, its outline filling the full height, one hull width (H) on a side, north up
(build_assets.normalize). Around it the composer places, in hull widths from the centre with +y forward
(LayeredMechComposer, render_variants.py):

  nose gun      under the chassis, rear pivot (0, -0.02), about 0.54 H long, so its barrel runs up the nose
  dorsal pod    over the chassis, rear pivot (0, -0.30), 0.42 H long and 0.23 H wide: it covers the deck
  feet          under the chassis at (+-0.22, -0.05), stepping 0.16 H fore and aft
  thigh links   over the chassis, from the waist (0, 0) out to each foot

so a chassis needs a narrow nose the barrel shows beside or through, a flat dorsal deck the pod stands on, and hip
housings at the waist. These guides keep those places and change the body round them.

  guide-a.png   the same outline: the accepted Hegemony Hound master, turned north up, as the edit target
  guide-b.png   a rebodied narrow strider in the Hound's own proportions: a slim riveted spine (about 0.27 H), armoured
                skirts over the legs where today's leg pods are, cheek plates either side of an open gun channel, a
                flat deck, round hip housings, an engine block at the rear
  guide-c.png   a broad walker, a walking tank: about 0.6 H wide, a boxy armoured body with fenders over the hips, the
                same channel and deck

Each is 1254 px square (the size the chassis masters come back at), H = 1254 px.

Run from anywhere: python art-source/mech-modular-topdown/sources/lowtech/hound/guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
SOURCES = HERE.parent.parent
S = 1254
CX = S / 2
CY = S / 2

BODY = (128, 128, 124, 255)
DECK = (150, 150, 146, 255)
HIP = (104, 104, 100, 255)
ENGINE = (112, 112, 108, 255)
LINE = (44, 44, 44, 255)


def px(x: float, y: float) -> tuple[float, float]:
    """A point in hull widths (+y forward) as guide pixels."""
    return CX + x * S, CY - y * S


def poly(draw: ImageDraw.ImageDraw, points, fill) -> None:
    draw.polygon([px(x, y) for x, y in points], fill=fill, outline=LINE, width=6)


def rect(draw: ImageDraw.ImageDraw, x0: float, y0: float, x1: float, y1: float, fill, radius: float = 0.02) -> None:
    a, b = px(x0, y1), px(x1, y0)
    draw.rounded_rectangle((a[0], a[1], b[0], b[1]), radius=radius * S, fill=fill, outline=LINE, width=6)


def circle(draw: ImageDraw.ImageDraw, x: float, y: float, r: float, fill) -> None:
    cx, cy = px(x, y)
    draw.ellipse((cx - r * S, cy - r * S, cx + r * S, cy + r * S), fill=fill, outline=LINE, width=6)


def guide_a() -> Image.Image:
    master = Image.open(SOURCES / "faction-chassis" / "hegemony" / "hound.png").convert("RGBA")
    return master.transpose(Image.Transpose.FLIP_TOP_BOTTOM)


def narrow_strider() -> Image.Image:
    """The Hound's own proportions rebodied: a slim riveted spine with armoured skirts over the legs."""
    im = Image.new("RGBA", (S, S))
    d = ImageDraw.Draw(im)
    # Armoured skirts over each leg, beside the spine, where today's Hound carries its leg pods.
    for side in (-1, 1):
        poly(d, [(side * 0.13, -0.38), (side * 0.24, -0.34), (side * 0.255, -0.06), (side * 0.22, 0.02),
                 (side * 0.13, 0.02)], HIP)
    # Engine block at the rear, full height to the bottom edge.
    rect(d, -0.10, -0.497, 0.10, -0.34, ENGINE, 0.02)
    # The spine: one riveted box from the engine to the nose shoulders.
    poly(d, [(-0.13, -0.38), (0.13, -0.38), (0.14, 0.20), (0.10, 0.30), (-0.10, 0.30), (-0.14, 0.20)], BODY)
    # Cheek plates either side of the open gun channel, out to the nose.
    for side in (-1, 1):
        poly(d, [(side * 0.04, 0.26), (side * 0.11, 0.26), (side * 0.09, 0.44), (side * 0.04, 0.497)], BODY)
    # The flat dorsal deck the pod stands on.
    rect(d, -0.105, -0.32, 0.105, 0.13, DECK, 0.012)
    # Round hip housings at the waist, where the thigh links leave the body.
    for side in (-1, 1):
        circle(d, side * 0.15, 0.0, 0.055, HIP)
    return im


def strider(width: float, fenders: bool) -> Image.Image:
    """A blocked-out chassis `width` hull widths across at the shoulders."""
    im = Image.new("RGBA", (S, S))
    d = ImageDraw.Draw(im)
    w = width / 2
    # Engine block at the rear, full height to the bottom edge.
    rect(d, -w * 0.72, -0.497, w * 0.72, -0.30, ENGINE, 0.03)
    # The main armoured box, chamfered at the front shoulders and the rear corners.
    poly(d, [(-w * 0.80, -0.36), (w * 0.80, -0.36), (w, -0.26), (w, 0.16), (w * 0.70, 0.30),
             (-w * 0.70, 0.30), (-w, 0.16), (-w, -0.26)], BODY)
    if fenders:
        # Fenders over the hips, standing proud of the body's flanks.
        for side in (-1, 1):
            rect(d, min(side * w, side * (w + 0.06)), -0.14, max(side * w, side * (w + 0.06)), 0.10, HIP, 0.03)
    # Cheek plates either side of the open gun channel, out to the nose.
    for side in (-1, 1):
        inner, outer = 0.055, w * 0.62
        poly(d, [(side * inner, 0.28), (side * outer, 0.28), (side * outer * 0.78, 0.44), (side * inner, 0.497)], BODY)
    # The flat dorsal deck the pod stands on.
    rect(d, -0.125, -0.32, 0.125, 0.14, DECK, 0.015)
    # Round hip housings at the waist, where the thigh links leave the body.
    for side in (-1, 1):
        circle(d, side * min(0.17, w * 0.78), 0.0, 0.07, HIP)
    return im


def main() -> None:
    guide_a().save(HERE / "guide-a.png")
    narrow_strider().save(HERE / "guide-b.png")
    strider(0.60, True).save(HERE / "guide-c.png")
    print("wrote guide-a.png, guide-b.png, guide-c.png")


if __name__ == "__main__":
    main()
