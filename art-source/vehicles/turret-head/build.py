"""Build the shared turret head's sprite from the accepted ImageGen master beside this file.

  master-v2.png -> mod/graphics/battle/vehicles/turret-head-hegemony.png (320 px square, gun slot to the top)

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
OUT = REPO / "mod" / "graphics" / "battle" / "vehicles" / "turret-head-hegemony.png"
GUIDE_SIZE = 1024
GUIDE_BASE_RADIUS = 380
SIZE = 320


def main() -> None:
    image = Image.open(HERE / "master-v2.png").convert("RGBA")
    scale = image.width / GUIDE_SIZE
    cx = cy = image.width / 2
    alpha = image.getchannel("A").point(lambda v: 255 if v >= 24 else 0)
    left, top, right, bottom = alpha.getbbox()
    half = math.ceil(max(cx - left, right - cx, cy - top, bottom - cy))
    square = image.crop((round(cx - half), round(cy - half), round(cx + half), round(cy + half)))
    square.resize((SIZE, SIZE), Image.Resampling.LANCZOS).save(OUT)
    print("wrote", OUT, "side/base diameter =", round(2 * half / (2 * GUIDE_BASE_RADIUS * scale), 4))


if __name__ == "__main__":
    main()
