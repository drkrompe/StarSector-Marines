"""Guide for an up-res of the vanilla red PD laser turret (pd_laser_turret_base.png).

Writes pd-upres-guide.png: the 24x24 vanilla sprite turned to face LEFT (our turret frames' facing),
enlarged 40x with nearest-neighbour onto a 1254x1254 transparent canvas. Not committed (it is the
game's art); rebuilt from the install.
"""
from pathlib import Path
from PIL import Image

HERE = Path(__file__).resolve().parent
VANILLA = Path("C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core/graphics/weapons/pd_laser_turret_base.png")
SCALE, CANVAS = 40, 1254

sprite = Image.open(VANILLA).convert("RGBA").rotate(90, expand=True)  # nose up -> nose left
big = sprite.resize((sprite.width * SCALE, sprite.height * SCALE), Image.NEAREST)
out = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
out.alpha_composite(big, ((CANVAS - big.width) // 2, (CANVAS - big.height) // 2))
out.save(HERE / "pd-upres-guide.png")
