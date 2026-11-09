"""Geometry guides for the rest of the low-tech Hegemony mech family, after the owner's Hound (hound/canopy-1-master.png:
the narrow strider body B with the faceted framed canopy). Each keeps its chassis's mounts where the composer puts them
(LayeredMechComposer, render_variants.py; hull widths H from the centre, +y forward) and today's outline envelope, so
every weapon drawing fits unchanged:

  warden    long-range support, a broad wedge (about +-0.33 H at the rear quarter)
            heavy cannon under the chassis, rear pivot (0, -0.05): it runs up the nose channel
            two LRM racks under the chassis, rear pivots (+-0.40, -0.30): only their outer halves show past the flanks
            feet under at (+-0.22, -0.05), thigh links over from the waist
  bulwark   heavy anchor, a broad box (about +-0.42 H at the shoulders)
            an arm gun under each shoulder, rear pivots (+-0.37, -0.15), its barrel showing ahead of the shoulder
            two missile racks over the chassis, rear pivots (+-0.40, -0.30): they cover the shoulder pads
            feet under at (+-0.17, -0.28), no thigh links
  foot      the shared foot pad, today's foot.png outline at 8x, for a low-tech pad to match

Each chassis guide marks a faceted canopy on the spine, the Hound's shape (hound/canopy_guide.py), as a lighter grey.

Run from anywhere: python art-source/mech-modular-topdown/sources/lowtech/family_guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
REPOSITORY = HERE.parents[3]
S = 1254
CX = S / 2
CY = S / 2

BODY = (128, 128, 124, 255)
DECK = (150, 150, 146, 255)
HIP = (104, 104, 100, 255)
ENGINE = (112, 112, 108, 255)
GLASS = (176, 176, 176, 255)
LINE = (44, 44, 44, 255)


def px(x: float, y: float) -> tuple[float, float]:
    return CX + x * S, CY - y * S


def poly(d: ImageDraw.ImageDraw, points, fill) -> None:
    d.polygon([px(x, y) for x, y in points], fill=fill, outline=LINE, width=6)


def mirrored(d: ImageDraw.ImageDraw, half, fill) -> None:
    """A shape symmetric about the centre line, given its right half from front to back."""
    poly(d, half + [(-x, y) for x, y in reversed(half)], fill)


def rect(d: ImageDraw.ImageDraw, x0: float, y0: float, x1: float, y1: float, fill, radius: float = 0.02) -> None:
    a, b = px(x0, y1), px(x1, y0)
    d.rounded_rectangle((a[0], a[1], b[0], b[1]), radius=radius * S, fill=fill, outline=LINE, width=6)


def circle(d: ImageDraw.ImageDraw, x: float, y: float, r: float, fill) -> None:
    cx, cy = px(x, y)
    d.ellipse((cx - r * S, cy - r * S, cx + r * S, cy + r * S), fill=fill, outline=LINE, width=6)


def canopy(d: ImageDraw.ImageDraw, front: float, rear: float, half: float) -> None:
    """The Hound's faceted canopy outline: narrow at the front, full width behind its shoulder."""
    shoulder = front - (front - rear) * 0.37
    mirrored(d, [(half * 0.47, front), (half, shoulder), (half, rear)], GLASS)


def warden() -> Image.Image:
    im = Image.new("RGBA", (S, S))
    d = ImageDraw.Draw(im)
    # Engine block at the rear.
    rect(d, -0.16, -0.497, 0.16, -0.36, ENGINE, 0.03)
    # The wedge: narrow behind the nose, broadest over the rack roots, tapering to the engine.
    mirrored(d, [(0.11, 0.28), (0.20, 0.10), (0.30, -0.12), (0.335, -0.24), (0.30, -0.34), (0.20, -0.40)], BODY)
    # Cheek plates either side of the cannon channel, out to the nose.
    for side in (-1, 1):
        poly(d, [(side * 0.045, 0.26), (side * 0.11, 0.26), (side * 0.085, 0.45), (side * 0.045, 0.497)], BODY)
    # A raised spine deck down the middle, the sensor and ammunition housing.
    rect(d, -0.09, -0.34, 0.09, 0.10, DECK, 0.012)
    # Hip housings at the waist.
    for side in (-1, 1):
        circle(d, side * 0.16, 0.0, 0.055, HIP)
    # Armoured sponsons over each rack root, at the rear flanks.
    for side in (-1, 1):
        poly(d, [(side * 0.20, -0.06), (side * 0.31, -0.13), (side * 0.33, -0.26), (side * 0.20, -0.30)], HIP)
    canopy(d, 0.285, 0.13, 0.072)
    return im


def bulwark() -> Image.Image:
    im = Image.new("RGBA", (S, S))
    d = ImageDraw.Draw(im)
    # Engine block at the rear.
    rect(d, -0.22, -0.497, 0.22, -0.34, ENGINE, 0.03)
    # The body box: a narrow armoured head forward, broad shoulders, a squared rear.
    mirrored(d, [(0.10, 0.44), (0.18, 0.36), (0.20, 0.22), (0.30, 0.16), (0.36, 0.08), (0.36, -0.30),
                 (0.31, -0.40)], BODY)
    # Shoulder pads the racks stand on, out to the flanks, behind the arm guns' barrels.
    for side in (-1, 1):
        x0, x1 = sorted((side * 0.28, side * 0.42))
        rect(d, x0, -0.30, x1, 0.08, HIP, 0.02)
    # Head plate at the very front, where the head bolts to the body.
    rect(d, -0.07, 0.40, 0.07, 0.497, BODY, 0.015)
    # Spine deck behind the canopy.
    rect(d, -0.12, -0.32, 0.12, 0.06, DECK, 0.012)
    canopy(d, 0.39, 0.15, 0.09)
    return im


def foot() -> Image.Image:
    source = Image.open(REPOSITORY / "mod" / "graphics" / "battle" / "mech-modular-topdown" / "foot.png")
    alpha = source.getchannel("A").resize((source.width * 8, source.height * 8), Image.Resampling.LANCZOS)
    alpha = alpha.point(lambda v: 255 if v > 128 else 0)
    pad = Image.new("RGBA", alpha.size, BODY)
    pad.putalpha(alpha)
    canvas = Image.new("RGBA", (512, 512))
    canvas.alpha_composite(pad, ((512 - pad.width) // 2, (512 - pad.height) // 2))
    return canvas


def main() -> None:
    for name, image in (("warden", warden()), ("bulwark", bulwark()), ("foot", foot())):
        (HERE / name).mkdir(exist_ok=True)
        image.save(HERE / name / "guide.png")
    print("wrote warden/guide.png, bulwark/guide.png, foot/guide.png")


if __name__ == "__main__":
    main()
