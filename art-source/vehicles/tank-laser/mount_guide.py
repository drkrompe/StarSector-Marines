"""Guide for an up-res of a vanilla ship turret mount: the Eagle's small energy slot WS010.

Starsector draws no separate mount sprites; a turret's mount is the ring painted into the hull.
This crops a disc of the Eagle hull around that slot, turns it to face LEFT, and enlarges it 30x
with nearest-neighbour onto a 1254x1254 transparent canvas: mount-upres-guide.png. Not committed
(it is the game's art); rebuilt from the install.
"""
import json
import re
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
CORE = Path("C:/Program Files (x86)/Fractal Softworks/Starsector/starsector-core")
SLOT, RADIUS, SCALE, CANVAS = "WS 010", 20, 30, 1254

text = re.sub(r"#.*", "", (CORE / "data/hulls/eagle.ship").read_text())
hull = json.loads(re.sub(r",(\s*[}\]])", r"\1", text))
img = Image.open(CORE / hull["spriteName"]).convert("RGBA")
slot = next(s for s in hull["weaponSlots"] if s["id"] == SLOT)
x, y = slot["locations"][:2]
px, py = hull["center"][0] - y, img.height - (hull["center"][1] + x)   # ship x forward, y left
crop = img.crop((round(px) - RADIUS, round(py) - RADIUS, round(px) + RADIUS, round(py) + RADIUS))
mask = Image.new("L", crop.size, 0)
ImageDraw.Draw(mask).ellipse((0, 0, crop.width - 1, crop.height - 1), fill=255)
crop.putalpha(Image.composite(crop.getchannel("A"), mask, mask))
crop = crop.rotate(90, expand=True)                                       # ship nose up -> left
big = crop.resize((crop.width * SCALE, crop.height * SCALE), Image.NEAREST)
out = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
out.alpha_composite(big, ((CANVAS - big.width) // 2, (CANVAS - big.height) // 2))
out.save(HERE / "mount-upres-guide.png")
print("slot", SLOT, "at", (px, py), "angle", slot.get("angle"))
