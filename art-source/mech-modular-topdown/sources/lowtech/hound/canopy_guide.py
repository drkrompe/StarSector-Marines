"""Edit targets for the canopy pass on the accepted narrow-strider Hound (b-master.png, owner's pick): the body unchanged,
with a flat grey marker where a pilot's glass lookout goes, on the spine between the nose cheeks and the rack deck. The
dorsal rack covers the deck (rear pivot -0.30 H, 0.42 H long, so up to about +0.12 H); the canopy sits ahead of it,
over the gun's rear, which the chassis draws over anyway.

  canopy-guide-1.png   a faceted, framed canopy: narrow at the front, full width over the rear
  canopy-guide-2.png   a rounded bubble canopy

Run from anywhere: python art-source/mech-modular-topdown/sources/lowtech/hound/canopy_guide.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
MARK = (150, 150, 150, 255)
LINE = (44, 44, 44, 255)
CX = 627


def marked(shape) -> Image.Image:
    im = Image.open(HERE / "b-master.png").convert("RGBA")
    shape(ImageDraw.Draw(im))
    return im


def faceted(d: ImageDraw.ImageDraw) -> None:
    d.polygon([(CX - 45, 268), (CX + 45, 268), (CX + 95, 335), (CX + 95, 448), (CX - 95, 448), (CX - 95, 335)],
              fill=MARK, outline=LINE, width=6)


def bubble(d: ImageDraw.ImageDraw) -> None:
    d.ellipse((CX - 85, 268, CX + 85, 450), fill=MARK, outline=LINE, width=6)


if __name__ == "__main__":
    marked(faceted).save(HERE / "canopy-guide-1.png")
    marked(bubble).save(HERE / "canopy-guide-2.png")
    print("wrote canopy-guide-1.png, canopy-guide-2.png")
