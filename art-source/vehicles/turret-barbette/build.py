"""Build the open Hegemony barbette's sprites from the accepted ImageGen masters beside this file.

  master.png    -> mod/graphics/battle/vehicles/turret-barbette-hegemony.png       (384 px square, front to the top)
  wreck-raw.png -> mod/graphics/battle/vehicles/turret-barbette-hegemony-wreck.png (the same square; an ImageGen edit
                   made on master.png with wreck.prompt.txt, registered to it pixel for pixel)

The square is centred on the open well's centre (the guide's well, 35 guide px aft of the wall's centre; guide.py),
not on the wall, so the well sits on the turret ring and what turns in it turns on the ring's centre. It is as large
as the wall's front face needs from there: 1010 guide px, the wall's flats 940. `VehicleGun.OPEN_HEAD_SHARE_OF_MOUNT`
is this square's side over the mount ring's diameter, so it carries that 1010 / 940.
Run: python art-source/vehicles/turret-barbette/build.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
VEHICLES = REPO / "mod" / "graphics" / "battle" / "vehicles"
SPRITES = (("master.png", "turret-barbette-hegemony.png"), ("wreck-raw.png", "turret-barbette-hegemony-wreck.png"))
GUIDE_SIZE = 1024
WELL_AFT = 35
HALF = 505
SIZE = 384


def build(source: str, output: str) -> None:
    image = Image.open(HERE / source).convert("RGBA")
    scale = image.width / GUIDE_SIZE
    cx, cy = image.width / 2, image.height / 2 + WELL_AFT * scale
    half = HALF * scale
    square = image.crop((round(cx - half), round(cy - half), round(cx + half), round(cy + half)))
    square.resize((SIZE, SIZE), Image.Resampling.LANCZOS).save(VEHICLES / output)
    print("wrote", output)


def main() -> None:
    for source, output in SPRITES:
        build(source, output)


if __name__ == "__main__":
    main()
