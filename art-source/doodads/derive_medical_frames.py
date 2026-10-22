"""Derive the four ward-bed facings from the real-length ImageGen master.

The master is the bed's whole 3x1 placement frame at 320 px per cell, cut from
the generation against WardBedTemplate's guide, so the bed keeps its true
0.76 x 2.15 m inside the frame: nothing is trimmed. The sources are packed
with preserveCanvas by stitch_atlas.py.
"""

from pathlib import Path

from PIL import Image


HERE = Path(__file__).resolve().parent
MASTERS = HERE / "imagegen-masters"
SOURCES = HERE / "sources"


def save(image: Image.Image, name: str) -> None:
    destination = SOURCES / name
    image.save(destination, optimize=True)
    print(destination)


def main() -> None:
    west = Image.open(MASTERS / "medical-ward-bed.png").convert("RGBA")  # head at west edge
    south = west.transpose(Image.Transpose.ROTATE_90)  # head at south edge
    save(west, "medical-ward-bed-head-w.png")
    save(south, "medical-ward-bed-head-s.png")
    save(west.transpose(Image.Transpose.ROTATE_180), "medical-ward-bed-head-e.png")
    save(south.transpose(Image.Transpose.ROTATE_180), "medical-ward-bed-head-n.png")


if __name__ == "__main__":
    main()
