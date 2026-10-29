"""Build the modular turret's sprites in mod/graphics/battle/vehicles/ from the ImageGen masters beside this file.

  ring.png       -> gun-mount.png         the exposed turntable every fitted gun stands on (static, 256 px)
  collar.png     -> turret-collar.png     the toothed collar that turns with the turret (256 px, open centre)
  base-medium.png-> turret-base-medium.png  the round housing of a two-row laser, gun port to the top (192 px)
  base-light.png -> turret-base-light.png   the same for a one-row laser (160 px)

Each master is cleaned of its baked white plate (clean_imagegen_backgrounds), cropped to its visible bounds and fitted
square, facing north. Run from anywhere: python art-source/vehicles/turret-ring/build.py
"""
import sys
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
sys.path.insert(0, str(REPO / "art-source" / "mech-modular-topdown"))
from clean_imagegen_backgrounds import clean  # noqa: E402

OUT = REPO / "mod" / "graphics" / "battle" / "vehicles"
SPRITES = (("ring.png", "gun-mount.png", 256), ("collar.png", "turret-collar.png", 256),
           ("base-medium.png", "turret-base-medium.png", 192), ("base-light.png", "turret-base-light.png", 160))


def build(source: str, output: str, size: int) -> None:
    work = HERE / ("_work_" + source)
    Image.open(HERE / source).convert("RGBA").save(work)
    try:
        clean(work)
        image = Image.open(work).convert("RGBA")
    finally:
        work.unlink()
    box = image.getchannel("A").point(lambda v: 255 if v >= 24 else 0).getbbox()
    image = image.crop(box)
    side = max(image.size)
    square = Image.new("RGBA", (side, side))
    square.alpha_composite(image, ((side - image.width) // 2, (side - image.height) // 2))
    square.resize((size, size), Image.Resampling.LANCZOS).save(OUT / output)
    print("wrote", OUT / output)


if __name__ == "__main__":
    for source, output, size in SPRITES:
        build(source, output, size)
