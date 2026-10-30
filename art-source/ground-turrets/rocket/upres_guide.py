"""Guide for the Locust battery's head: the vanilla Locust turret, up-res'd (`tender-ship.md`, What a tender can build).

Writes, from the install, the vanilla Locust turret sprite nose UP (the ground-turret heads' facing), enlarged with
nearest-neighbour onto a 1254x1254 transparent canvas. Not committed (it is the game's art); rebuilt from the
install:

  locust-upres-guide.png  locust_turret.png (64 px), the Locust SRM launcher

Usage: python art-source/ground-turrets/rocket/upres_guide.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
WEAPONS = Path("C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core/graphics/weapons")
CANVAS = 1254
GUIDES = {"locust": ("locust_turret.png", 19)}


def main():
    for name, (sprite, scale) in GUIDES.items():
        im = Image.open(WEAPONS / sprite).convert("RGBA")
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        out = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
        out.alpha_composite(big, ((CANVAS - big.width) // 2, (CANVAS - big.height) // 2))
        out.save(HERE / f"{name}-upres-guide.png")


if __name__ == "__main__":
    main()
