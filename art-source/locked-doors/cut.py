"""Cuts the accepted locked-door master into the shipped sprites (mod/graphics/doors/<id>.png).

Each piece is cut at its own opaque bounds found round its guide box (ImageGen moves pieces a few pixels), then
scaled to OUT_CELL px per cell at its guide size. Run from the repository root:
python art-source/locked-doors/cut.py
"""
import json
import os
from PIL import Image

OUT_CELL = 128
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
MASTER = os.path.join(HERE, "master-v1.png")
OUT = os.path.join(ROOT, "mod", "graphics", "doors")

master = Image.open(MASTER).convert("RGBA")
alpha = master.split()[3].point(lambda v: 255 if v > 128 else 0)
spec = json.load(open(os.path.join(HERE, "pieces.json")))
sx, sy = master.width / spec["width"], master.height / spec["height"]
os.makedirs(OUT, exist_ok=True)
for p in spec["pieces"]:
    x0, y0 = int((p["x"] - 40) * sx), int((p["y"] - 40) * sy)
    x1, y1 = int((p["x"] + p["w"] + 40) * sx), int((p["y"] + p["h"] + 40) * sy)
    bb = alpha.crop((x0, y0, x1, y1)).getbbox()
    piece = master.crop((x0 + bb[0], y0 + bb[1], x0 + bb[2], y0 + bb[3]))
    size = (round(p["w"] / spec["cellPx"] * OUT_CELL), round(p["h"] / spec["cellPx"] * OUT_CELL))
    piece.resize(size, Image.LANCZOS).save(os.path.join(OUT, p["id"] + ".png"))
    print(p["id"], size)
