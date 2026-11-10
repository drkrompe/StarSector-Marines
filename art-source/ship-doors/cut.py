"""Cuts the accepted master into the shipped door sprites, mechanically from the guide's boxes.

The master came back at a different size than the guide; each box is scaled by the width ratio, cut whole (generated
alpha kept) and downsampled to SHIP_CELL px per cell. Run from the repository root:
python art-source/ship-doors/cut.py
"""
import json
import os
from PIL import Image

SHIP_CELL = 160
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(os.path.dirname(HERE)), "mod", "graphics", "doors")

spec = json.load(open(os.path.join(HERE, "pieces.json")))
master = Image.open(os.path.join(HERE, "master-v1.png")).convert("RGBA")
scale = master.width / spec["width"]
for p in spec["pieces"]:
    x, y, w, h = (round(v * scale) for v in (p["x"], p["y"], p["w"], p["h"]))
    size = (round(p["w"] / spec["cellPx"] * SHIP_CELL), round(p["h"] / spec["cellPx"] * SHIP_CELL))
    master.crop((x, y, x + w, y + h)).resize(size, Image.LANCZOS).save(os.path.join(OUT, "ship-" + p["id"] + ".png"))
    print("ship-" + p["id"], size)
