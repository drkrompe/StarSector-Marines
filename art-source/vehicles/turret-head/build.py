"""Build the shared turret head's sprite from the accepted ImageGen master beside this file.

  master-v2.png -> mod/graphics/battle/vehicles/turret-head-hegemony.png       (320 px square, gun slot to the top)
  wreck-raw.png -> mod/graphics/battle/vehicles/turret-head-hegemony-wreck.png (the same square; an ImageGen edit
                   made on master-v2.png with wreck.prompt.txt, registered to it pixel for pixel)

`master.png` was the first take: right silhouette and finish, but it copied the hulls' empty-ring hatch (arrows and
hazard ring) onto the roof, so it read as a second socket; `master-v2.png` asked for a plain cupola instead and is the
one used. The square is centred on the base circle's centre (the guide's centre, scaled to the master), not on the
art's bounds, so the head turns on the ring's centre; it is as large as the farthest opaque pixel from that centre
needs. `VehicleGun.HEAD_SHARE_OF_MOUNT` is this square's side over the base circle's diameter.
Run: python art-source/vehicles/turret-head/build.py
"""
import math
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
VEHICLES = REPO / "mod" / "graphics" / "battle" / "vehicles"
HEADS = (("master-v2.png", "turret-head-hegemony.png"), ("wreck-raw.png", "turret-head-hegemony-wreck.png"))
GUIDE_SIZE = 1024
GUIDE_BASE_RADIUS = 380
SIZE = 320


def build(source: str, output: str, half: int = 0) -> int:
    image = Image.open(HERE / source).convert("RGBA")
    scale = image.width / GUIDE_SIZE
    cx = cy = image.width / 2
    alpha = image.getchannel("A").point(lambda v: 255 if v >= 24 else 0)
    left, top, right, bottom = alpha.getbbox()
    half = half or math.ceil(max(cx - left, right - cx, cy - top, bottom - cy))
    square = image.crop((round(cx - half), round(cy - half), round(cx + half), round(cy + half)))
    square.resize((SIZE, SIZE), Image.Resampling.LANCZOS).save(VEHICLES / output)
    print("wrote", output, "side/base diameter =", round(2 * half / (2 * GUIDE_BASE_RADIUS * scale), 4))
    return half


def main() -> None:
    # The wreck is cut to the live head's square, so the swap does not move or rescale it.
    half = build(*HEADS[0])
    for source, output in HEADS[1:]:
        build(source, output, half)


if __name__ == "__main__":
    main()
