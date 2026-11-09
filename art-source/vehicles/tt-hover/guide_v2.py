"""Geometry guides for Tri-Tachyon's skimmer line, light, medium and heavy (owner, from the high-tech frame concepts:
"Skimmers are interesting to explore as their vehicle line for light, medium, and heavy").

The outlines come from the skimmer concept sheet
(art-source/mech-modular-topdown/sources/hightech/frames/e-sheet.png): each machine is cut out of it, its painted
guns opened away (a hull is drawn bare, every weapon its own layer: mech-lines.md, law 4), turned to face LEFT and
scaled to its hull length. Each guide is pack.py's canvas, 1792x896 of two 896x896 panels at 256 px a cell: the live
hull on the left, the wreck on the right, one flat grey silhouette each, centred on its panel. A dark disc marks the
empty turret ring where a hull mounts a gun.

  skimmer-light-guide.png    the hover scout's new hull
  skimmer-medium-guide.png   the drone skimmer's new hull
  skimmer-heavy-guide.png    the heavy skimmer's hull
  skimmer-<w>-concept.png    the concept machine, gun and all, turned to face left: identity reference only

Run: python art-source/vehicles/tt-hover/guide_v2.py
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

HERE = Path(__file__).resolve().parent
CONCEPT = HERE.parents[1] / "mech-modular-topdown" / "sources" / "hightech" / "frames" / "e-sheet.png"
PX = 256
PANEL = 896
GREY = (150, 152, 156, 255)
RING = (60, 62, 66, 255)

# Hull length nose to stern, cells, and the turret ring (cells aft of the centre, ring radius), or None for none.
HULLS = {
    "light": dict(length=2.5, ring=(0.15, 0.30)),
    "medium": dict(length=2.2, ring=None),
    "heavy": dict(length=3.3, ring=(0.0, 0.42)),
}
# Thin painted barrels are opened away with a minimum filter this wide (sheet px) before the silhouette is taken.
OPEN_PX = {"light": 25, "medium": 25, "heavy": 45}


def machines():
    """The concept sheet's three machines, left to right, each cropped to its own alpha."""
    sheet = Image.open(CONCEPT).convert("RGBA")
    alpha = np.asarray(sheet.getchannel("A")) > 24
    cols = alpha.any(axis=0)
    spans, start = [], None
    for x, on in enumerate(cols):
        if on and start is None:
            start = x
        if not on and start is not None:
            if x - start > 40:
                spans.append((start, x))
            start = None
    if start is not None:
        spans.append((start, len(cols)))
    out = []
    for x0, x1 in spans[:3]:
        part = sheet.crop((x0, 0, x1, sheet.height))
        out.append(part.crop(part.getchannel("A").point(lambda v: 255 if v > 24 else 0).getbbox()))
    return dict(zip(("light", "medium", "heavy"), out))


def bare(machine, open_px):
    """The machine's alpha with thin barrels opened away: eroded and dilated by the same width."""
    a = machine.getchannel("A").point(lambda v: 255 if v > 24 else 0)
    size = open_px | 1
    return a.filter(ImageFilter.MinFilter(size)).filter(ImageFilter.MaxFilter(size))


def guide(weight, machine):
    hull = HULLS[weight]
    mask = bare(machine, OPEN_PX[weight])
    bbox = mask.getbbox()
    mask = mask.crop(bbox)
    # Face left: the concept's nose points up.
    mask = mask.rotate(90, expand=True)
    scale = hull["length"] * PX / mask.width
    mask = mask.resize((round(mask.width * scale), round(mask.height * scale)), Image.LANCZOS)
    mask = mask.point(lambda v: 255 if v > 127 else 0)
    canvas = Image.new("RGBA", (PANEL * 2, PANEL))
    for i in range(2):
        shape = Image.new("RGBA", mask.size, GREY)
        shape.putalpha(mask)
        cx = PANEL * i + PANEL // 2
        canvas.alpha_composite(shape, (cx - mask.width // 2, PANEL // 2 - mask.height // 2))
        if hull["ring"] is not None and i == 0:
            aft, r = hull["ring"]
            d = ImageDraw.Draw(canvas)
            rx = cx + round(aft * PX)
            d.ellipse((rx - r * PX, PANEL // 2 - r * PX, rx + r * PX, PANEL // 2 + r * PX), fill=RING)
    canvas.save(HERE / f"skimmer-{weight}-guide.png")
    concept = machine.crop(machine.getchannel("A").getbbox()).rotate(90, expand=True)
    concept.save(HERE / f"skimmer-{weight}-concept.png")
    return mask.size


def main():
    for weight, machine in machines().items():
        w, h = guide(weight, machine)
        print(f"{weight}: {w / PX:.2f} x {h / PX:.2f} cells")


if __name__ == "__main__":
    main()
