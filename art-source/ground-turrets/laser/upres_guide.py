"""Guides for the high-tech posts' laser heads: up-res'd vanilla laser turrets (`ground-turret-tiers.md`).

Writes, from the install, each vanilla turret sprite nose UP (the ground-turret heads' facing), enlarged with
nearest-neighbour onto a 1254x1254 transparent canvas. Not committed (they are the game's art); rebuilt from the
install:

  burstpd-upres-guide.png  burstpd_turret_base.png (32 px), the Burst PD Laser: the point posts' burst laser

The point posts' beam laser and the hardpoint's are the laser tank's own PD laser skins (art-source/vehicles/tank-laser),
not guides here.

Usage: python art-source/ground-turrets/laser/upres_guide.py
"""
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
WEAPONS = Path("C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core/graphics/weapons")
CANVAS = 1254
GUIDES = {"burstpd": ("burstpd_turret_base.png", 36)}


def main():
    for name, (sprite, scale) in GUIDES.items():
        im = Image.open(WEAPONS / sprite).convert("RGBA")
        big = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
        out = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
        out.alpha_composite(big, ((CANVAS - big.width) // 2, (CANVAS - big.height) // 2))
        out.save(HERE / f"{name}-upres-guide.png")


if __name__ == "__main__":
    main()
