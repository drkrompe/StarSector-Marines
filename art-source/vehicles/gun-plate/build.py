"""Build the gun plate sprite in mod/graphics/battle/vehicles/ from an ImageGen master beside this file.

  shield-master.png -> gun-plate.png   a flat turntable behind a curved gunner's shield (adopted)
  cupola-master.png                    candidate: a low armoured cupola with a squared mantlet

The master is reskinned from its guide (guide.py) with true alpha. It is cropped to a square about the master's centre,
which is the guide's centre and the turntable's pivot, so the plate turns on its middle in game; the square spans the
farthest opaque pixel from the centre. Facing north, 256 px. The runtime draws the whole square at
VehicleGun.plateCells.

Run from anywhere: python art-source/vehicles/gun-plate/build.py [master.png]
"""
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
OUT = REPO / "mod" / "graphics" / "battle" / "vehicles" / "gun-plate.png"
SIZE = 256


def build(master: Path) -> None:
    image = Image.open(master).convert("RGBA")
    left, top, right, bottom = image.getchannel("A").point(lambda v: 255 if v >= 24 else 0).getbbox()
    cx, cy = image.width / 2, image.height / 2
    half = int(max(cx - left, right - cx, cy - top, bottom - cy)) + 2
    square = image.crop((int(cx - half), int(cy - half), int(cx + half), int(cy + half)))
    square.resize((SIZE, SIZE), Image.Resampling.LANCZOS).save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    build(Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "shield-master.png")
